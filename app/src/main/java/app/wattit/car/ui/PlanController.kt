package app.wattit.car.ui

import app.wattit.car.car.CarReading
import app.wattit.car.car.CarSource
import app.wattit.car.car.Speaker
import app.wattit.car.data.HttpResult
import app.wattit.car.data.PlanRequest
import app.wattit.car.domain.Departure
import app.wattit.car.domain.KST
import app.wattit.car.domain.PlanResults
import app.wattit.car.domain.PlanState
import app.wattit.car.domain.TariffState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/** Screen-wide inputs that are fixed for the first version (shown in the top chips). */
object PlanDefaults {
    const val SOC_TARGET = 80
    const val CHARGER_KW = 7.0
    const val MODE = "self_low"
}

data class ScreenState(
    val car: CarReading? = null,
    val departAt: ZonedDateTime? = null,
    val today: java.time.LocalDate? = null,
    val plan: PlanState = PlanState.Loading,
    val tariff: TariffState = TariffState.Loading,
)

/**
 * Reads the car, asks the server for the plan and the two days of hourly prices, and speaks the
 * server's sentence once, the first time a plan arrives.
 */
class PlanController(
    private val scope: CoroutineScope,
    private val car: CarSource,
    private val planCall: suspend (PlanRequest) -> HttpResult,
    private val timelineCall: suspend (day: String, mode: String) -> HttpResult,
    private val speaker: Speaker,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(KST) },
) {
    private val _state = MutableStateFlow(ScreenState())
    val state: StateFlow<ScreenState> = _state.asStateFlow()

    private var spoken = false
    private var planJob: Job? = null
    private var tariffJob: Job? = null

    fun start() {
        refreshPlan()
        refreshTariff()
    }

    fun refreshPlan() {
        planJob?.cancel()
        planJob = scope.launch {
            val now = clock().withZoneSameInstant(KST)
            val depart = Departure.next(now)
            _state.value = _state.value.copy(plan = PlanState.Loading, departAt = depart, today = now.toLocalDate())

            val reading = car.read()
            _state.value = _state.value.copy(car = reading)
            val soc = reading.socPercent
            val capacity = reading.capacityWh
            val plan = when {
                soc == null -> PlanState.VehicleUnavailable
                capacity == null || capacity <= 0 -> PlanState.CapacityUnknown
                else -> PlanResults.planState(
                    planCall(
                        PlanRequest(
                            socNow = soc,
                            batteryCapacityWh = capacity,
                            socTarget = PlanDefaults.SOC_TARGET,
                            chargerKw = PlanDefaults.CHARGER_KW,
                            plugInAt = Departure.iso(now),
                            departAt = Departure.iso(depart),
                            mode = PlanDefaults.MODE,
                        ),
                    ),
                    socNow = soc,
                    socTarget = PlanDefaults.SOC_TARGET,
                )
            }
            _state.value = _state.value.copy(plan = plan)
            if (plan is PlanState.Ready && !spoken) {
                spoken = true
                speaker.speak(plan.response.speech)
            }
        }
    }

    fun refreshTariff() {
        tariffJob?.cancel()
        tariffJob = scope.launch {
            _state.value = _state.value.copy(tariff = TariffState.Loading)
            val today = clock().withZoneSameInstant(KST).toLocalDate()
            val days = listOf(today, today.plusDays(1))
            val results = days.map { day -> async { timelineCall(day.toString(), PlanDefaults.MODE) } }.awaitAll()
            _state.value = _state.value.copy(tariff = PlanResults.tariffState(results))
        }
    }
}

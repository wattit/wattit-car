package app.wattit.car.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Request body for `POST /api/car/plan`. Every number shown on screen comes back from the server. */
@Serializable
data class PlanRequest(
    @SerialName("soc_now") val socNow: Int,
    @SerialName("battery_capacity_wh") val batteryCapacityWh: Int,
    @SerialName("soc_target") val socTarget: Int,
    @SerialName("charger_kw") val chargerKw: Double,
    @SerialName("plug_in_at") val plugInAt: String,
    @SerialName("depart_at") val departAt: String,
    val mode: String,
)

@Serializable
data class ChargePlan(
    val start: String,
    val end: String,
    @SerialName("energy_kwh") val energyKwh: Double,
    @SerialName("cost_won") val costWon: Int,
)

@Serializable
data class Reservation(
    @SerialName("start_at") val startAt: String,
    @SerialName("end_at") val endAt: String,
    @SerialName("start_hhmm") val startHhmm: String,
    @SerialName("end_hhmm") val endHhmm: String,
)

@Serializable
data class PlanResponse(
    @SerialName("needs_charging") val needsCharging: Boolean,
    val plan: ChargePlan? = null,
    @SerialName("savings_won") val savingsWon: Int? = null,
    val reservation: Reservation? = null,
    val speech: String,
)

@Serializable
data class HourPrice(
    val hour: Int,
    val band: String,
    @SerialName("price_per_kwh") val pricePerKwh: Double,
)

@Serializable
data class TimelineResponse(
    val day: String,
    val season: String,
    val hours: List<HourPrice>,
)

/** Error body: FastAPI sends either a string or a list of validation errors in `detail`. */
@Serializable
private data class StringDetail(val detail: String)

object ApiJson {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun encodePlanRequest(request: PlanRequest): String = json.encodeToString(PlanRequest.serializer(), request)

    fun parsePlan(body: String): PlanResponse = json.decodeFromString(PlanResponse.serializer(), body)

    fun parseTimeline(body: String): TimelineResponse = json.decodeFromString(TimelineResponse.serializer(), body)

    /** The server's Korean message when `detail` is a string, otherwise null. */
    fun stringDetail(body: String): String? =
        runCatching { json.decodeFromString(StringDetail.serializer(), body).detail }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
}

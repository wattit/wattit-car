package app.wattit.car.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import ai.pleos.playground.vehicle.Vehicle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** What the car reports. Null means the value could not be read. */
data class CarReading(
    val socPercent: Int?,
    val capacityWh: Int?,
    val model: String?,
)

interface CarSource {
    suspend fun read(): CarReading
    fun release()
}

/**
 * State of charge from Pleos Vehicle (`getEvBatteryStateOfCharge`, pleos.car.permission.CAR_ENERGY).
 * Battery capacity and model from the Android Automotive CarPropertyManager
 * (`INFO_EV_BATTERY_CAPACITY` in Wh, `INFO_MODEL`; android.car.permission.CAR_INFO), because the Pleos
 * capacity getter is @hide and deprecated in favor of that property.
 */
class PleosCarSource(context: Context) : CarSource {
    private val appContext = context.applicationContext
    private val vehicle = Vehicle(appContext).also { it.initialize() }
    @Volatile
    private var car: Car? = null

    override suspend fun read(): CarReading {
        val soc = readSoc()
        // CarPropertyManager calls block on IPC and must not run on the main thread.
        return withContext(Dispatchers.IO) {
            val properties = propertyManager()
            val capacity = properties?.let { manager ->
                runCatching { manager.getFloatProperty(VehiclePropertyIds.INFO_EV_BATTERY_CAPACITY, GLOBAL_AREA) }
                    .getOrNull()
                    ?.takeIf { it.isFinite() && it > 0f }
                    ?.toInt()
            }
            val model = properties?.let { manager ->
                runCatching {
                    manager.getProperty(String::class.java, VehiclePropertyIds.INFO_MODEL, GLOBAL_AREA)?.value
                }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
            }
            CarReading(soc, capacity, model)
        }
    }

    private suspend fun readSoc(): Int? = withTimeoutOrNull(SOC_TIMEOUT_MS) {
        suspendCancellableCoroutine { continuation ->
            try {
                vehicle.getEvBattery().getEvBatteryStateOfCharge(
                    onComplete = { value ->
                        if (continuation.isActive) continuation.resume(value?.takeIf { it in 0..100 })
                    },
                    onFailed = {
                        if (continuation.isActive) continuation.resume(null)
                    },
                )
            } catch (e: RuntimeException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun propertyManager(): CarPropertyManager? = runCatching {
        val connected = car ?: Car.createCar(appContext).also { car = it }
        connected?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
    }.getOrNull()

    override fun release() {
        runCatching { vehicle.release() }
        runCatching { car?.disconnect() }
        car = null
    }

    private companion object {
        const val GLOBAL_AREA = 0
        const val SOC_TIMEOUT_MS = 8_000L
    }
}

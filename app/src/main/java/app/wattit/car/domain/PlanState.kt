package app.wattit.car.domain

import app.wattit.car.data.ApiJson
import app.wattit.car.data.HttpResult
import app.wattit.car.data.PlanResponse
import app.wattit.car.data.TimelineResponse
import kotlinx.serialization.SerializationException

/** What the plan panel shows. Every number in [Ready] is the server's value as sent. */
sealed interface PlanState {
    data object Loading : PlanState

    /** The car did not give its state of charge. No default or last value is used. */
    data object VehicleUnavailable : PlanState

    /** The car reported battery capacity 0 or nothing: no request is sent. */
    data object CapacityUnknown : PlanState

    /** The server does not have the car plan API yet (404). */
    data object ServerNotReady : PlanState

    /** No answer from the server (offline, timeout). */
    data object Offline : PlanState

    /** The server refused the request and said why, in Korean. */
    data class Refused(val message: String) : PlanState

    /** Any other failure; [code] is the HTTP status when there was one. */
    data class Failed(val code: Int?) : PlanState

    data class Ready(val socNow: Int, val socTarget: Int, val response: PlanResponse) : PlanState
}

sealed interface TariffState {
    data object Loading : TariffState
    data object Failed : TariffState
    data class Ready(val days: List<TimelineResponse>) : TariffState
}

object PlanResults {

    fun planState(result: HttpResult, socNow: Int, socTarget: Int): PlanState = when (result) {
        is HttpResult.Ok -> try {
            PlanState.Ready(socNow, socTarget, ApiJson.parsePlan(result.body))
        } catch (e: SerializationException) {
            PlanState.Failed(null)
        } catch (e: IllegalArgumentException) {
            PlanState.Failed(null)
        }
        is HttpResult.Status -> when (result.code) {
            404 -> PlanState.ServerNotReady
            422 -> ApiJson.stringDetail(result.body)?.let { PlanState.Refused(it) } ?: PlanState.Failed(422)
            else -> PlanState.Failed(result.code)
        }
        is HttpResult.Failed -> PlanState.Offline
    }

    /** Both days must arrive; a chart with a missing day would show a false gap. */
    fun tariffState(results: List<HttpResult>): TariffState {
        val days = results.map { result ->
            if (result !is HttpResult.Ok) return TariffState.Failed
            runCatching { ApiJson.parseTimeline(result.body) }.getOrNull() ?: return TariffState.Failed
        }
        return if (days.isEmpty() || days.any { it.hours.size != 24 }) TariffState.Failed else TariffState.Ready(days)
    }
}

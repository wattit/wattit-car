package app.wattit.car.domain

import app.wattit.car.data.ApiJson
import app.wattit.car.data.HttpResult
import app.wattit.car.data.PlanRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class PlanResultsTest {

    // Shape from docs/car-api.md (POST /api/car/plan, example response) plus fields the app ignores.
    private val planBody = """
        {"needs_charging": true,
         "plan": {"start": "2026-10-07T22:00:00+09:00", "end": "2026-10-08T01:00:00+09:00",
                  "energy_kwh": 21.0, "cost_won": 2352, "discounted_kwh": 0.0},
         "baseline_now": {"start": "2026-10-07T18:00:00+09:00", "end": "2026-10-07T21:00:00+09:00",
                          "energy_kwh": 21.0, "cost_won": 2747, "discounted_kwh": 0.0},
         "savings_won": 395,
         "reservation": {"start_at": "2026-10-07T22:00:00+09:00", "end_at": "2026-10-08T01:00:00+09:00",
                         "start_hhmm": "22:00", "end_hhmm": "01:00"},
         "speech": "오늘 밤 10시부터 내일 새벽 1시까지 충전하면 가장 쌉니다.",
         "assumptions": ["충전 출력 일정"], "unverified_tariff_items": [], "saving_id": null}
    """.trimIndent()

    @Test
    fun okResponseKeepsTheServerNumbers() {
        val state = PlanResults.planState(HttpResult.Ok(planBody), socNow = 30, socTarget = 80)
        assertTrue(state is PlanState.Ready)
        val response = (state as PlanState.Ready).response
        assertEquals("22:00", response.reservation?.startHhmm)
        assertEquals("01:00", response.reservation?.endHhmm)
        assertEquals(395, response.savingsWon)
        assertEquals("오늘 밤 10시부터 내일 새벽 1시까지 충전하면 가장 쌉니다.", response.speech)
        assertEquals(30, state.socNow)
    }

    @Test
    fun noChargingNeededFromTheServerIsNotNeeded() {
        val body = """{"needs_charging": false, "plan": null, "baseline_now": null, "savings_won": null,
            "reservation": null, "speech": "잔량 79.5%가 목표 79% 이상이라 충전하지 않아도 됩니다.",
            "assumptions": [], "unverified_tariff_items": []}"""
        assertEquals(PlanState.NotNeeded(79, 80), PlanResults.planState(HttpResult.Ok(body), socNow = 79, socTarget = 80))
    }

    @Test
    fun aBatteryAtOrAboveTheTargetSkipsTheServer() {
        // The server accepts soc_now below 100 only; 100 would come back as a 422 validation list.
        assertTrue(PlanResults.alreadyCharged(100, 80))
        assertTrue(PlanResults.alreadyCharged(85, 80))
        assertTrue(PlanResults.alreadyCharged(80, 80))
        assertFalse(PlanResults.alreadyCharged(79, 80))
    }

    @Test
    fun missingCarApiIsServerNotReady() {
        assertEquals(PlanState.ServerNotReady, PlanResults.planState(HttpResult.Status(404, """{"detail":"Not Found"}"""), 30, 80))
    }

    @Test
    fun stringDetailOn422IsShownAsTheReason() {
        val state = PlanResults.planState(HttpResult.Status(422, """{"detail":"시간이 부족해요. 출발을 늦춰 주세요."}"""), 30, 80)
        assertEquals(PlanState.Refused("시간이 부족해요. 출발을 늦춰 주세요."), state)
    }

    @Test
    fun validationListOn422IsAGenericFailure() {
        val body = """{"detail":[{"type":"value_error","loc":["body","depart_at"],"msg":"x","input":"y"}]}"""
        assertEquals(PlanState.Failed(422), PlanResults.planState(HttpResult.Status(422, body), 30, 80))
        // What the server sends for soc_now=100 (exclusive maximum).
        val full = """{"detail":[{"type":"less_than","loc":["body","soc_now"],"msg":"Input should be less than 100",""" +
            """"input":100,"ctx":{"lt":100.0}}]}"""
        assertEquals(PlanState.Failed(422), PlanResults.planState(HttpResult.Status(422, full), 100, 80))
    }

    @Test
    fun reservationSpanIsPlacedFromTheFirstChartDay() {
        val span = TariffText.reservationSpan("2026-11-16", "2026-11-16T22:00:00+09:00", "2026-11-17T03:19:00+09:00")!!
        assertEquals(22f, span.start, 0.001f)
        assertEquals(27f + 19f / 60f, span.endInclusive, 0.001f)
        // Read in KST even when sent in UTC.
        val utc = TariffText.reservationSpan("2026-11-16", "2026-11-16T13:00:00Z", "2026-11-16T15:00:00Z")!!
        assertEquals(22f, utc.start, 0.001f)
        // Clipped at the chart's end, dropped when entirely outside it.
        val late = TariffText.reservationSpan("2026-11-16", "2026-11-17T23:00:00+09:00", "2026-11-18T04:00:00+09:00")!!
        assertEquals(48f, late.endInclusive, 0.001f)
        assertNull(TariffText.reservationSpan("2026-11-16", "2026-11-18T01:00:00+09:00", "2026-11-18T04:00:00+09:00"))
        assertNull(TariffText.reservationSpan("2026-11-16", "not a time", "2026-11-18T04:00:00+09:00"))
    }

    @Test
    fun serverErrorAndOfflineAndBrokenBodyAreDistinct() {
        assertEquals(PlanState.Failed(500), PlanResults.planState(HttpResult.Status(500, "Internal Server Error"), 30, 80))
        assertEquals(PlanState.Offline, PlanResults.planState(HttpResult.Failed(IOException("timeout")), 30, 80))
        assertEquals(PlanState.Failed(null), PlanResults.planState(HttpResult.Ok("<html>"), 30, 80))
        assertEquals(PlanState.Failed(null), PlanResults.planState(HttpResult.Ok("""{"needs_charging": true}"""), 30, 80))
    }

    @Test
    fun requestUsesTheServerFieldNames() {
        val json = ApiJson.encodePlanRequest(
            PlanRequest(32, 77400, 80, 7.0, "2026-11-15T15:30:00+09:00", "2026-11-16T08:00:00+09:00", "self_low"),
        )
        assertEquals(
            """{"soc_now":32,"battery_capacity_wh":77400,"soc_target":80,"charger_kw":7.0,""" +
                """"plug_in_at":"2026-11-15T15:30:00+09:00","depart_at":"2026-11-16T08:00:00+09:00","mode":"self_low"}""",
            json,
        )
    }

    private fun day(date: String, light: Set<Int>, season: String = "winter") =
        """{"day":"$date","mode":"self_low","season":"$season","is_holiday":false,"hours":[""" +
            (0 until 24).joinToString(",") { h ->
                val band = if (h in light) "light" else "middle"
                val price = if (h in light) 84 else 259
                """{"hour":$h,"band":"$band","discounted":false,"price_per_kwh":$price}"""
            } + "]}"

    @Test
    fun tariffNeedsBothDays() {
        val nights = (0..7).toSet() + setOf(22, 23)
        val ok = PlanResults.tariffState(listOf(HttpResult.Ok(day("2026-11-15", nights)), HttpResult.Ok(day("2026-11-16", nights))))
        assertTrue(ok is TariffState.Ready)
        assertEquals(48, (ok as TariffState.Ready).days.sumOf { it.hours.size })

        val oneFailed = PlanResults.tariffState(listOf(HttpResult.Ok(day("2026-11-15", nights)), HttpResult.Status(500, "")))
        assertEquals(TariffState.Failed, oneFailed)
        assertEquals(TariffState.Failed, PlanResults.tariffState(listOf(HttpResult.Ok("{}"))))
    }

    @Test
    fun lightWindowCrossesMidnightAndHandlesAllDay() {
        val nights = ApiJson.parseTimeline(day("2026-11-16", (0..7).toSet() + setOf(22, 23)))
        assertEquals("22:00–08:00", TariffText.lightWindow(nights))
        val allDay = ApiJson.parseTimeline(day("2026-11-15", (0..23).toSet()))
        assertEquals("종일", TariffText.lightWindow(allDay))
        val none = ApiJson.parseTimeline(day("2026-11-17", emptySet()))
        assertNull(TariffText.lightWindow(none))
    }

    @Test
    fun moneyAndPricesAreFormattedAsSent() {
        assertEquals("₩1,240", TariffText.wonAmount(1240))
        assertEquals("₩84", TariffText.won(84.0))
        assertEquals("₩136.8", TariffText.won(136.8))
        assertEquals("77.4kWh", TariffText.kwh(77400))
        assertEquals("겨울", TariffText.seasonName("winter"))
    }
}

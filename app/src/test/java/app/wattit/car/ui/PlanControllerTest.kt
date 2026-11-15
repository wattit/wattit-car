package app.wattit.car.ui

import app.wattit.car.car.CarReading
import app.wattit.car.car.CarSource
import app.wattit.car.car.Speaker
import app.wattit.car.data.HttpResult
import app.wattit.car.data.PlanRequest
import app.wattit.car.domain.PlanState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZonedDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class PlanControllerTest {

    private class FakeCar(var reading: CarReading) : CarSource {
        override suspend fun read() = reading
        override fun release() = Unit
    }

    private class FakeSpeaker : Speaker {
        val said = mutableListOf<String>()
        override fun speak(text: String) {
            said += text
        }
        override fun release() = Unit
    }

    private val body = """{"needs_charging": true, "savings_won": 1240,
        "reservation": {"start_at": "2026-11-15T23:00:00+09:00", "end_at": "2026-11-16T06:00:00+09:00",
                        "start_hhmm": "23:00", "end_hhmm": "06:00"},
        "speech": "오늘 밤 11시부터 내일 오전 6시까지 충전하면 가장 쌉니다."}"""

    private val now = ZonedDateTime.parse("2026-11-15T15:30:00+09:00[Asia/Seoul]")

    @Test
    fun missingSocShowsRetryAndSendsNothing() = runTest {
        val requests = mutableListOf<PlanRequest>()
        val controller = PlanController(
            scope = this,
            car = FakeCar(CarReading(socPercent = null, capacityWh = 77400, model = null)),
            planCall = { requests += it; HttpResult.Ok(body) },
            timelineCall = { _, _ -> HttpResult.Status(500, "") },
            speaker = FakeSpeaker(),
            clock = { now },
        )
        controller.refreshPlan()
        advanceUntilIdle()
        assertEquals(PlanState.VehicleUnavailable, controller.state.value.plan)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun atOrAboveTheTargetNoRequestIsSentAndNothingIsSpoken() = runTest {
        for (soc in listOf(100, 85, 80)) {
            val requests = mutableListOf<PlanRequest>()
            val speaker = FakeSpeaker()
            val controller = PlanController(
                scope = this,
                car = FakeCar(CarReading(socPercent = soc, capacityWh = 77400, model = null)),
                planCall = { requests += it; HttpResult.Status(422, "") },
                timelineCall = { _, _ -> HttpResult.Status(500, "") },
                speaker = speaker,
                clock = { now },
            )
            controller.refreshPlan()
            advanceUntilIdle()
            assertEquals(PlanState.NotNeeded(soc, 80), controller.state.value.plan)
            assertEquals(now, controller.state.value.now)
            assertTrue("soc $soc sent a request", requests.isEmpty())
            assertTrue(speaker.said.isEmpty())
        }
    }

    @Test
    fun belowTheTargetTheServerIsAsked() = runTest {
        val requests = mutableListOf<PlanRequest>()
        val controller = PlanController(
            scope = this,
            car = FakeCar(CarReading(socPercent = 79, capacityWh = 77400, model = null)),
            planCall = { requests += it; HttpResult.Ok(body) },
            timelineCall = { _, _ -> HttpResult.Status(500, "") },
            speaker = FakeSpeaker(),
            clock = { now },
        )
        controller.refreshPlan()
        advanceUntilIdle()
        assertEquals(79, requests.single().socNow)
    }

    @Test
    fun zeroCapacitySendsNothing() = runTest {
        val requests = mutableListOf<PlanRequest>()
        val controller = PlanController(
            scope = this,
            car = FakeCar(CarReading(socPercent = 40, capacityWh = null, model = null)),
            planCall = { requests += it; HttpResult.Ok(body) },
            timelineCall = { _, _ -> HttpResult.Status(500, "") },
            speaker = FakeSpeaker(),
            clock = { now },
        )
        controller.refreshPlan()
        advanceUntilIdle()
        assertEquals(PlanState.CapacityUnknown, controller.state.value.plan)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun readyPlanIsSpokenOnceAndSendsCarValues() = runTest {
        val requests = mutableListOf<PlanRequest>()
        val speaker = FakeSpeaker()
        val controller = PlanController(
            scope = this,
            car = FakeCar(CarReading(socPercent = 32, capacityWh = 77400, model = "IONIQ 5")),
            planCall = { requests += it; HttpResult.Ok(body) },
            timelineCall = { _, _ -> HttpResult.Status(500, "") },
            speaker = speaker,
            clock = { now },
        )
        controller.refreshPlan()
        advanceUntilIdle()
        controller.refreshPlan()
        advanceUntilIdle()

        assertTrue(controller.state.value.plan is PlanState.Ready)
        assertEquals(listOf("오늘 밤 11시부터 내일 오전 6시까지 충전하면 가장 쌉니다."), speaker.said)
        val sent = requests.first()
        assertEquals(32, sent.socNow)
        assertEquals(77400, sent.batteryCapacityWh)
        assertEquals(80, sent.socTarget)
        assertEquals("2026-11-15T15:30:00+09:00", sent.plugInAt)
        assertEquals("2026-11-16T08:00:00+09:00", sent.departAt)
    }

    @Test
    fun serverWithoutCarApiIsNotSpokenAndDoesNotCrash() = runTest {
        val speaker = FakeSpeaker()
        val controller = PlanController(
            scope = this,
            car = FakeCar(CarReading(socPercent = 32, capacityWh = 77400, model = null)),
            planCall = { HttpResult.Status(404, """{"detail":"Not Found"}""") },
            timelineCall = { _, _ -> HttpResult.Status(500, "") },
            speaker = speaker,
            clock = { now },
        )
        controller.start()
        advanceUntilIdle()
        assertEquals(PlanState.ServerNotReady, controller.state.value.plan)
        assertTrue(speaker.said.isEmpty())
    }
}

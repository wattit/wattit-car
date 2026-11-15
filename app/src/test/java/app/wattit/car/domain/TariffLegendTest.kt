package app.wattit.car.domain

import app.wattit.car.data.ApiJson
import app.wattit.car.data.TimelineResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.ZonedDateTime

/** Labels drawn from real /api/timeline answers (self_low) saved under src/test/resources. */
class TariffLegendTest {

    private fun day(date: String): TimelineResponse =
        ApiJson.parseTimeline(javaClass.classLoader!!.getResource("tl-$date.json")!!.readText())

    private fun at(text: String): ZonedDateTime = ZonedDateTime.parse("$text+09:00[Asia/Seoul]")

    // 2026-05-16 Sat: light 22-08 at 112.0, discount 11-14 at 70.6 (middle band), else 125.3.
    // 2026-05-17 Sun: light all day at 112.0, discount 11-14 at 63.9.
    // 2026-05-18 Mon: light 22-08 at 112.0, middle 125.3, peak 130.8, no discount.
    private val weekend = listOf(day("2026-05-16"), day("2026-05-17"))
    private val sundayMonday = listOf(day("2026-05-17"), day("2026-05-18"))
    private val winter = listOf(day("2026-11-16"), day("2026-11-17"))

    @Test
    fun cheapestOnASpringSaturdayMorningIsTheDiscountNotTheLightLoad() {
        val run = TariffText.cheapestAhead(weekend, at("2026-05-16T09:00:00"))!!
        assertEquals("11:00–14:00", run.label)
        assertEquals(70.6, run.price, 0.0)
    }

    @Test
    fun afterTheSaturdayDiscountTheCheapestIsSundaysDiscount() {
        val run = TariffText.cheapestAhead(weekend, at("2026-05-16T15:00:00"))!!
        assertEquals("내일 11:00–14:00", run.label)
        assertEquals(63.9, run.price, 0.0)
    }

    @Test
    fun aSundayMorningPicksSundaysDiscount() {
        val run = TariffText.cheapestAhead(sundayMonday, at("2026-05-17T09:30:00"))!!
        assertEquals("11:00–14:00", run.label)
        assertEquals(63.9, run.price, 0.0)
    }

    @Test
    fun aSundayAfternoonRunsInTheLightLoadUntilMondayMorning() {
        // Sunday 15:00 to Monday 15:00: 112.0 from now until Monday 08:00.
        val run = TariffText.cheapestAhead(sundayMonday, at("2026-05-17T15:00:00"))!!
        assertEquals("지금–08:00", run.label)
        assertEquals(112.0, run.price, 0.0)
    }

    @Test
    fun aWinterWeekdayAfternoonPicksTheNightLightLoad() {
        val run = TariffText.cheapestAhead(winter, at("2026-11-16T15:30:00"))!!
        assertEquals("22:00–08:00", run.label)
        assertEquals(136.8, run.price, 0.0)
    }

    @Test
    fun aFlatWindowIsAllDay() {
        val flat = ApiJson.parseTimeline(
            """{"day":"2026-11-15","season":"winter","hours":[""" +
                (0 until 24).joinToString(",") { """{"hour":$it,"band":"light","price_per_kwh":136.8}""" } + "]}",
        )
        val next = flat.copy(day = "2026-11-16")
        assertEquals("종일", TariffText.cheapestAhead(listOf(flat, next), at("2026-11-15T10:00:00"))!!.label)
        assertNull(TariffText.cheapestAhead(listOf(flat, next), at("2026-11-18T10:00:00")))
    }

    @Test
    fun lightLegendSaysLowestOnlyWhenItIs() {
        assertEquals("경부하 22:00–08:00 · ₩112/kWh", TariffText.lightLegend(weekend))
        assertEquals("경부하 종일 · ₩112/kWh", TariffText.lightLegend(sundayMonday))
        assertEquals("경부하 22:00–08:00 · 최저 ₩136.8/kWh", TariffText.lightLegend(winter))
    }

    @Test
    fun discountLegendsFollowTheServerFlag() {
        assertEquals(
            listOf("오늘 할인 11:00–14:00 · ₩70.6/kWh", "내일 할인 11:00–14:00 · ₩63.9/kWh"),
            TariffText.discountLegends(weekend),
        )
        assertEquals(listOf("오늘 할인 11:00–14:00 · ₩63.9/kWh"), TariffText.discountLegends(sundayMonday))
        assertEquals(emptyList<String>(), TariffText.discountLegends(winter))
        val sameBoth = listOf(day("2026-05-17"), day("2026-05-17").copy(day = "2026-05-18"))
        assertEquals(listOf("할인 11:00–14:00 · ₩63.9/kWh"), TariffText.discountLegends(sameBoth))
    }
}

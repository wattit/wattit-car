package app.wattit.car.domain

import app.wattit.car.data.HourPrice
import app.wattit.car.data.TimelineResponse
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

/** Labels for the tariff chart. Only describes the server's hourly prices; computes no fee. */
object TariffText {

    fun seasonName(season: String): String = when (season) {
        "summer" -> "여름"
        "winter" -> "겨울"
        "spring_fall" -> "봄·가을"
        else -> season
    }

    private fun format(pattern: String) = DecimalFormat(pattern, DecimalFormatSymbols(Locale.US))

    /** "₩84", "₩136.8": the server's price, without trailing ".0". */
    fun won(price: Double): String = "₩" + format("#,##0.#").format(price)

    fun wonAmount(amount: Int): String = "₩" + format("#,##0").format(amount)

    /** Battery capacity for the vehicle chip: 77400 Wh -> "77.4kWh". Display only. */
    fun kwh(wh: Int): String = format("#,##0.#").format(wh / 1000.0) + "kWh"

    /**
     * The light-load window of one day as "23:00–06:00", taking the longest run of light hours
     * around the clock (the night run crosses midnight). "종일" when every hour is light, null when none.
     */
    fun lightWindow(day: TimelineResponse): String? {
        val light = BooleanArray(24) { h -> day.hours.firstOrNull { it.hour == h }?.band == "light" }
        if (light.all { it }) return "종일"
        if (light.none { it }) return null
        var bestStart = -1
        var bestLength = 0
        for (start in 0 until 24) {
            if (!light[start] || light[(start + 23) % 24]) continue
            var length = 0
            while (length < 24 && light[(start + length) % 24]) length++
            if (length > bestLength) {
                bestStart = start
                bestLength = length
            }
        }
        val end = (bestStart + bestLength) % 24
        return "%02d:00–%02d:00".format(bestStart, end)
    }

    fun lowest(hours: List<HourPrice>): Double? = hours.minOfOrNull { it.pricePerKwh }

    fun highest(hours: List<HourPrice>): Double? = hours.maxOfOrNull { it.pricePerKwh }

    /**
     * Legend for the light-load band of the first chart day: "경부하 22:00–08:00 · ₩112/kWh".
     * The price is the light-load hours' own price (discount hours left out). "최저" is added only when
     * that price really is the lowest on the whole chart; a spring/fall weekend discount can be cheaper.
     */
    fun lightLegend(days: List<TimelineResponse>): String? {
        val first = days.firstOrNull() ?: return null
        val window = lightWindow(first) ?: return null
        val lightPrice = first.hours.sortedBy { it.hour }.firstOrNull { it.band == "light" && !it.discounted }?.pricePerKwh
            ?: return "경부하 $window"
        val chartLowest = days.flatMap { it.hours }.minOf { it.pricePerKwh }
        val lowest = if (lightPrice <= chartLowest) "최저 " else ""
        return "경부하 $window · $lowest${won(lightPrice)}/kWh"
    }

    /**
     * Legend entries for discount hours (spring/fall weekend and holiday daytime): "할인 11:00–14:00 · ₩70.6/kWh".
     * One entry when both chart days have the same window and price, else one per day marked 오늘/내일.
     */
    fun discountLegends(days: List<TimelineResponse>): List<String> {
        val perDay = days.take(2).map { day ->
            val hours = day.hours.sortedBy { it.hour }.filter { it.discounted }
            if (hours.isEmpty()) {
                null
            } else {
                val start = hours.first().hour
                val end = hours.last().hour + 1
                "%02d:00–%02d:00 · %s/kWh".format(start, end % 24, won(hours.minOf { it.pricePerKwh }))
            }
        }
        val present = perDay.filterNotNull()
        return when {
            present.isEmpty() -> emptyList()
            present.toSet().size == 1 && present.size == perDay.size -> listOf("할인 ${present.first()}")
            else -> perDay.mapIndexedNotNull { i, entry -> entry?.let { "${if (i == 0) "오늘" else "내일"} 할인 $it" } }
        }
    }

    /** The cheapest hours ahead, chosen from the server's hourly prices. [label] never contains the price. */
    data class CheapestRun(val label: String, val price: Double)

    /**
     * The cheapest run of hours in the next 24 hours from [now] on a chart that starts at the first day's
     * 00:00 KST: the lowest `price_per_kwh` in that window, then the first stretch of hours at that price
     * (followed past the window while the price holds). "지금–08:00" when it is under way, "내일 11:00–14:00"
     * when it starts on the second day, "종일" when the whole window has that price.
     */
    fun cheapestAhead(days: List<TimelineResponse>, now: ZonedDateTime): CheapestRun? {
        val first = days.firstOrNull() ?: return null
        val origin = runCatching { LocalDate.parse(first.day).atStartOfDay(KST) }.getOrNull() ?: return null
        val prices = days.flatMap { day -> day.hours.sortedBy { it.hour }.map { it.pricePerKwh } }
        val nowHour = Duration.between(origin, now.withZoneSameInstant(KST)).toHours().toInt()
        if (nowHour < 0 || nowHour >= prices.size) return null
        val windowEnd = minOf(nowHour + 24, prices.size)
        val window = nowHour until windowEnd
        val min = window.minOf { prices[it] }
        if (window.all { prices[it] == min }) return CheapestRun("종일", min)
        val start = window.first { prices[it] == min }
        var end = start
        while (end < prices.size && prices[end] == min) end++
        val endText = "%02d:00".format(end % 24)
        val label = when {
            start == nowHour -> "지금–$endText"
            start >= 24 -> "내일 %02d:00–$endText".format(start % 24)
            else -> "%02d:00–$endText".format(start)
        }
        return CheapestRun(label, min)
    }

    /**
     * Where the reservation sits on a chart that starts at [firstDay] 00:00 KST and runs [hours] hours,
     * in hours from the chart start, clipped to the chart. Null when it falls outside the chart or
     * the times cannot be read. Uses the server's `start_at`/`end_at` as sent.
     */
    fun reservationSpan(firstDay: String, startAt: String, endAt: String, hours: Int = 48): ClosedFloatingPointRange<Float>? {
        val origin = runCatching { LocalDate.parse(firstDay).atStartOfDay(KST) }.getOrNull() ?: return null
        val start = runCatching { ZonedDateTime.parse(startAt) }.getOrNull() ?: return null
        val end = runCatching { ZonedDateTime.parse(endAt) }.getOrNull() ?: return null
        fun offset(at: ZonedDateTime) = Duration.between(origin, at).toMinutes() / 60f
        val from = offset(start).coerceIn(0f, hours.toFloat())
        val to = offset(end).coerceIn(0f, hours.toFloat())
        return if (to > from) from..to else null
    }
}

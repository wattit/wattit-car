package app.wattit.car.domain

import app.wattit.car.data.HourPrice
import app.wattit.car.data.TimelineResponse
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
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
}

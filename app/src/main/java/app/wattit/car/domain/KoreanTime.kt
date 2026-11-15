package app.wattit.car.domain

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

val KST: ZoneId = ZoneId.of("Asia/Seoul")

/**
 * Korean spoken time, matching the server's speech rules so the screen and the voice agree:
 * 새벽 0-5, 오전 6-11, 낮 12, 오후 13-17, 저녁 18-20, 밤 21-23; 00:00 is the previous day's 밤 12시,
 * 00:01-00:59 has no period ("0시 29분").
 */
object KoreanTime {

    /** "23:00" -> "밤 11시", "05:30" -> "새벽 5시 30분", "00:00" -> "밤 12시". */
    fun spoken(hhmm: String): String {
        val time = LocalTime.parse(hhmm)
        return spoken(time.hour, time.minute)
    }

    fun spoken(hour: Int, minute: Int): String {
        val minutes = if (minute == 0) "" else " ${minute}분"
        if (hour == 0 && minute == 0) return "밤 12시"
        if (hour == 0) return "0시$minutes"
        val period = when (hour) {
            in 1..5 -> "새벽"
            in 6..11 -> "오전"
            12 -> "낮"
            in 13..17 -> "오후"
            in 18..20 -> "저녁"
            else -> "밤"
        }
        val twelve = if (hour > 12) hour - 12 else hour
        return "$period ${twelve}시$minutes"
    }

    /**
     * Day word for a time relative to [today] in KST: 오늘, 내일, 모레, else "11월 20일".
     * 00:00 belongs to the previous day, as in the server's speech.
     */
    fun dayWord(at: ZonedDateTime, today: LocalDate): String {
        val local = at.withZoneSameInstant(KST)
        val date = if (local.hour == 0 && local.minute == 0) local.toLocalDate().minusDays(1) else local.toLocalDate()
        return when (date.toEpochDay() - today.toEpochDay()) {
            0L -> "오늘"
            1L -> "내일"
            2L -> "모레"
            else -> "${date.monthValue}월 ${date.dayOfMonth}일"
        }
    }

    /** Topic particle for a day word: 오늘은, 내일은, 모레는, 11월 20일은. */
    fun withTopic(dayWord: String): String = dayWord + if (dayWord == "모레") "는" else "은"
}

object Departure {
    const val DEFAULT_HOUR = 8

    /** Next 08:00 KST strictly after [now]. */
    fun next(now: ZonedDateTime): ZonedDateTime {
        val local = now.withZoneSameInstant(KST)
        val todayAt = local.toLocalDate().atTime(DEFAULT_HOUR, 0).atZone(KST)
        return if (todayAt.isAfter(local)) todayAt else todayAt.plusDays(1)
    }

    fun iso(at: ZonedDateTime): String = at.withZoneSameInstant(KST).truncatedTo(java.time.temporal.ChronoUnit.SECONDS)
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}

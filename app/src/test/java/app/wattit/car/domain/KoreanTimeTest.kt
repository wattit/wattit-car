package app.wattit.car.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZonedDateTime

class KoreanTimeTest {

    @Test
    fun spokenHoursFollowTheServerPeriods() {
        assertEquals("밤 11시", KoreanTime.spoken("23:00"))
        assertEquals("밤 12시", KoreanTime.spoken("00:00"))
        assertEquals("0시 29분", KoreanTime.spoken("00:29"))
        assertEquals("새벽 3시 19분", KoreanTime.spoken("03:19"))
        assertEquals("오전 8시", KoreanTime.spoken("08:00"))
        assertEquals("낮 12시 30분", KoreanTime.spoken("12:30"))
        assertEquals("오후 3시", KoreanTime.spoken("15:00"))
        assertEquals("저녁 6시", KoreanTime.spoken("18:00"))
        assertEquals("밤 9시", KoreanTime.spoken("21:00"))
    }

    @Test
    fun dayWordIsRelativeToTodayAndMidnightBelongsToThePreviousDay() {
        val today = LocalDate.of(2026, 11, 15)
        assertEquals("오늘", KoreanTime.dayWord(ZonedDateTime.parse("2026-11-15T23:00:00+09:00"), today))
        assertEquals("오늘", KoreanTime.dayWord(ZonedDateTime.parse("2026-11-16T00:00:00+09:00"), today))
        assertEquals("내일", KoreanTime.dayWord(ZonedDateTime.parse("2026-11-16T00:29:00+09:00"), today))
        assertEquals("모레", KoreanTime.dayWord(ZonedDateTime.parse("2026-11-17T08:00:00+09:00"), today))
        assertEquals("11월 18일", KoreanTime.dayWord(ZonedDateTime.parse("2026-11-18T08:00:00+09:00"), today))
        // An instant sent in UTC is read in KST.
        assertEquals("내일", KoreanTime.dayWord(ZonedDateTime.parse("2026-11-15T23:00:00Z"), today))
    }

    @Test
    fun topicParticleMatchesTheDayWord() {
        assertEquals("오늘은", KoreanTime.withTopic("오늘"))
        assertEquals("내일은", KoreanTime.withTopic("내일"))
        assertEquals("모레는", KoreanTime.withTopic("모레"))
    }

    @Test
    fun departureIsTheNextEightOClockInKst() {
        assertEquals(
            ZonedDateTime.parse("2026-11-16T08:00:00+09:00[Asia/Seoul]"),
            Departure.next(ZonedDateTime.parse("2026-11-15T15:30:00+09:00[Asia/Seoul]")),
        )
        assertEquals(
            ZonedDateTime.parse("2026-11-16T08:00:00+09:00[Asia/Seoul]"),
            Departure.next(ZonedDateTime.parse("2026-11-16T07:59:00+09:00[Asia/Seoul]")),
        )
        assertEquals(
            ZonedDateTime.parse("2026-11-17T08:00:00+09:00[Asia/Seoul]"),
            Departure.next(ZonedDateTime.parse("2026-11-16T08:00:00+09:00[Asia/Seoul]")),
        )
        // A device clock in UTC still means 08:00 in Korea.
        assertEquals(
            "2026-11-16T08:00:00+09:00",
            Departure.iso(Departure.next(ZonedDateTime.parse("2026-11-15T06:30:00Z"))),
        )
    }
}

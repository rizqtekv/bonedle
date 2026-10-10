package app.bonedle.engine

import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class DailyScheduleTest {
    @Test
    fun epochDaysMatchKnownDates() {
        assertEquals(0L, CalendarDate(1970, 1, 1).epochDay)
        assertEquals(-1L, CalendarDate(1969, 12, 31).epochDay)
        assertEquals(11_016L, CalendarDate(2000, 2, 29).epochDay)
        assertEquals(20_759L, CalendarDate(2026, 11, 2).epochDay)
    }

    @Test
    fun epochDayRoundTripsAcrossCenturies() {
        var day = CalendarDate(1899, 12, 25).epochDay
        val end = CalendarDate(2101, 1, 5).epochDay
        var previous: CalendarDate? = null
        while (day <= end) {
            val date = CalendarDate.fromEpochDay(day)
            assertEquals(day, date.epochDay)
            if (previous != null) assertEquals(previous.plusDays(1), date)
            previous = date
            day += 1
        }
    }

    @Test
    fun leapYearsAndParsing() {
        assertEquals(29, CalendarDate.daysInMonth(2028, 2))
        assertEquals(28, CalendarDate.daysInMonth(2100, 2))
        assertEquals(29, CalendarDate.daysInMonth(2000, 2))
        assertEquals(CalendarDate(2027, 3, 23), CalendarDate.parse("2027-03-23"))
        assertEquals("2027-03-23", CalendarDate(2027, 3, 23).toIsoString())
        assertNull(CalendarDate.parse("2027-02-30"))
        assertNull(CalendarDate.parse("yesterday"))
        assertFailsWith<IllegalArgumentException> { CalendarDate(2027, 13, 1) }
    }

    @Test
    fun numbersCountFromLaunchDay() {
        assertEquals(1, DailySchedule.numberOn(DailySchedule.LAUNCH_DATE))
        assertEquals(142, DailySchedule.numberOn(CalendarDate(2027, 3, 23)))
        assertEquals(1, DailySchedule.numberOn(CalendarDate(2026, 1, 1)))
        assertEquals(CalendarDate(2027, 3, 23), DailySchedule.dateOf(142))
        for (n in 1..800) assertEquals(n, DailySchedule.numberOn(DailySchedule.dateOf(n)))
        assertFailsWith<IllegalArgumentException> { DailySchedule.dateOf(0) }
    }

    /** Pinned: changing any of these silently changes every player's daily board. */
    @Test
    fun seedsArePinned() {
        assertEquals(2083090342350878992L, DailySchedule.seedFor(1, Difficulty.EASY))
        assertEquals(6106246632845884829L, DailySchedule.seedFor(1, Difficulty.MEDIUM))
        assertEquals(4914931437954242658L, DailySchedule.seedFor(142, Difficulty.HARD))
    }

    @Test
    fun seedsDifferByDayAndDifficulty() {
        val seeds = HashSet<Long>()
        for (n in 1..365) for (d in Difficulty.DAILY) seeds.add(DailySchedule.seedFor(n, d))
        assertEquals(365 * 3, seeds.size)
        assertNotEquals(DailySchedule.seedFor(7, Difficulty.EASY), DailySchedule.seedFor(8, Difficulty.EASY))
    }
}

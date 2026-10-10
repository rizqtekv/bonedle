package app.bonedle.domain

import app.bonedle.notifications.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class GrowthTest {
    @Test
    fun theReferrerCarriesTheBoardPathBothWays() {
        val path = "d/142/medium?t=134&h=0"
        val referrer = Referrer.forPath(path)
        assertEquals("utm_source=bonedle&utm_medium=challenge&utm_content=d%2F142%2Fmedium%3Ft%3D134%26h%3D0", referrer)
        assertEquals(path, Referrer.boardPath(referrer))
        // A store that decodes the value once more still yields the whole path.
        assertEquals(path, Referrer.boardPath("utm_source=bonedle&utm_medium=challenge&utm_content=$path"))
        assertEquals(BoardLink(app.bonedle.data.game.GameId.Daily(142, app.bonedle.engine.model.Difficulty.MEDIUM), Challenge(134_000, 0)), Links.parsePath(requireNotNull(Referrer.boardPath(referrer))))
    }

    @Test
    fun otherReferrersAreIgnored() {
        assertNull(Referrer.boardPath("utm_source=google-play&utm_medium=organic"))
        assertNull(Referrer.boardPath("utm_source=bonedle&utm_medium=challenge"))
        assertNull(Referrer.boardPath("utm_source=bonedle&utm_content="))
        assertNull(Referrer.boardPath(""))
    }

    @Test
    fun headToHeadComparesWholeSeconds() {
        val friend = Challenge(timeMs = 134_000, hintsUsed = 0)
        assertEquals(Outcome.WON, HeadToHead(91_400, 0, friend).outcome)
        assertEquals(43_000, HeadToHead(91_400, 0, friend).marginMs)
        assertEquals(Outcome.LOST, HeadToHead(140_000, 2, friend).outcome)
        assertEquals(6_000, HeadToHead(140_000, 2, friend).marginMs)
        assertEquals(Outcome.TIE, HeadToHead(134_900, 0, friend).outcome)
        assertEquals(0, HeadToHead(134_900, 0, friend).marginMs)
    }

    @Test
    fun reviewIsAskedAfterTheThirdCleanSolveAtMostEverySixtyDays() {
        val day = 24L * 60 * 60 * 1000
        val now = 1_000 * day
        assertFalse(ReviewPolicy.shouldAsk(cleanSolves = 2, hintsThisSolve = 0, lastAskedAt = null, now = now, lostHeadToHead = false))
        assertTrue(ReviewPolicy.shouldAsk(3, 0, null, now, false))
        assertFalse("never right after using hints", ReviewPolicy.shouldAsk(5, 1, null, now, false))
        assertFalse("never right after a loss", ReviewPolicy.shouldAsk(5, 0, null, now, true))
        assertFalse(ReviewPolicy.shouldAsk(9, 0, now - 59 * day, now, false))
        assertTrue(ReviewPolicy.shouldAsk(9, 0, now - 60 * day, now, false))
    }

    @Test
    fun theReminderIsOfferedOnceFromTheThirdDaily() {
        assertFalse(ReminderOffer.shouldOffer(isDaily = true, dailiesCompleted = 2, alreadyOffered = false, reminderOn = false))
        assertTrue(ReminderOffer.shouldOffer(true, 3, false, false))
        assertFalse(ReminderOffer.shouldOffer(false, 10, false, false))
        assertFalse(ReminderOffer.shouldOffer(true, 10, true, false))
        assertFalse(ReminderOffer.shouldOffer(true, 10, false, true))
    }

    @Test
    fun theReminderFiresAtTheNextOccurrenceOfItsTime() {
        val zone = ZoneId.of("America/New_York")
        fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()
        // Before 9:00 → today; at or after → tomorrow.
        assertEquals(at(2026, 11, 2, 9, 0), ReminderScheduler.nextTrigger(at(2026, 11, 2, 8, 59), zone, 9 * 60))
        assertEquals(at(2026, 11, 3, 9, 0), ReminderScheduler.nextTrigger(at(2026, 11, 2, 9, 0), zone, 9 * 60))
        assertEquals(at(2026, 11, 3, 9, 0), ReminderScheduler.nextTrigger(at(2026, 11, 2, 22, 0), zone, 9 * 60))
        // The 2:30 a.m. reminder on the spring-forward night lands on the first valid minute.
        val gap = ReminderScheduler.nextTrigger(at(2027, 3, 13, 23, 0), zone, 2 * 60 + 30)
        assertEquals(at(2027, 3, 14, 3, 30), gap)
        // Across the autumn change the wall-clock time stays 9:00.
        assertEquals(at(2026, 11, 1, 9, 0), ReminderScheduler.nextTrigger(at(2026, 10, 31, 12, 0), zone, 9 * 60))
    }
}

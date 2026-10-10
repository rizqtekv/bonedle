package app.bonedle.ads

import app.bonedle.data.db.GameResultEntity
import app.bonedle.data.db.StreakFreezeEntity
import app.bonedle.data.game.GameId
import app.bonedle.data.game.Progress
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.daily.Streaks
import app.bonedle.engine.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdPolicyTest {
    private val minute = 60_000L

    @Test
    fun noAdsInTheFirstSessionWithoutConsentOrWithoutRealUnits() {
        assertTrue(AdPolicy.adsAllowed(adsEnabled = true, canRequestAds = true, firstSession = false))
        assertFalse(AdPolicy.adsAllowed(adsEnabled = true, canRequestAds = true, firstSession = true))
        assertFalse(AdPolicy.adsAllowed(adsEnabled = true, canRequestAds = false, firstSession = false))
        assertFalse(AdPolicy.adsAllowed(adsEnabled = false, canRequestAds = true, firstSession = false))
    }

    @Test
    fun interstitialsWaitForThreeSolvesAndThreeMinutes() {
        assertFalse(AdPolicy.interstitialDue(allowed = true, plus = false, solvedSinceLast = 2, msSinceLast = null))
        assertTrue(AdPolicy.interstitialDue(allowed = true, plus = false, solvedSinceLast = 3, msSinceLast = null))
        assertFalse(AdPolicy.interstitialDue(allowed = true, plus = false, solvedSinceLast = 5, msSinceLast = 3 * minute - 1))
        assertTrue(AdPolicy.interstitialDue(allowed = true, plus = false, solvedSinceLast = 5, msSinceLast = 3 * minute))
        assertFalse("never for Plus", AdPolicy.interstitialDue(allowed = true, plus = true, solvedSinceLast = 9, msSinceLast = null))
        assertFalse(AdPolicy.interstitialDue(allowed = false, plus = false, solvedSinceLast = 9, msSinceLast = null))
    }

    @Test
    fun theExtraHintAdIsOfferedOnUnlimitedBoardsOnly() {
        assertTrue(AdPolicy.extraHintOffered(allowed = true, plus = false, unlimitedBoard = true))
        assertFalse("never on a daily", AdPolicy.extraHintOffered(allowed = true, plus = false, unlimitedBoard = false))
        assertFalse(AdPolicy.extraHintOffered(allowed = true, plus = true, unlimitedBoard = true))
        assertFalse(AdPolicy.extraHintOffered(allowed = false, plus = false, unlimitedBoard = true))
    }

    @Test
    fun aRecentAdHoldsBackTheRatingPrompt() {
        val now = 1_000 * minute
        assertFalse(AdPolicy.adShownRecently(null, now))
        assertTrue(AdPolicy.adShownRecently(now - 9 * minute, now))
        assertFalse(AdPolicy.adShownRecently(now - 10 * minute, now))
    }

    private fun daily(n: Int) = GameResultEntity(
        gameKey = GameId.Daily(n, Difficulty.EASY).key, mode = "daily", difficulty = "easy", dailyNumber = n, seed = 0, timeMs = 1,
        hintsUsed = 0, undoCount = 0, source = "home", completedAt = n.toLong(), localDate = DailySchedule.dateOf(n).toIsoString(),
    )

    private fun freeze(day: Long, source: String) = StreakFreezeEntity(day, Streaks.weekStart(day), source, 0)

    @Test
    fun theStreakSaverComesOnlyAfterTheFreeFreeze() {
        // Week 2 (dailies #8-#14 run Monday to Sunday): Mon played, Tue missed, Wed played,
        // Thu missed, today is Fri.
        val results = listOf(daily(8), daily(10))
        val tuesday = DailySchedule.dateOf(9).epochDay
        val thursday = DailySchedule.dateOf(11).epochDay
        val today = DailySchedule.dateOf(12).epochDay
        // Without a freeze yet, the free one covers Thursday by itself: no ad needed.
        assertNull(Progress(results, emptyList(), emptyList()).rescueDay(today))
        // The free freeze went on Tuesday: the ad can save Thursday.
        val spent = listOf(freeze(tuesday, Progress.FREEZE_FREE))
        val progress = Progress(results, emptyList(), spent)
        assertEquals(thursday, progress.rescueDay(today))
        assertEquals(2, progress.streakIfFrozen(thursday, today))
        // One saver a week.
        val sunday = DailySchedule.dateOf(14).epochDay
        assertNull(Progress(results, emptyList(), spent + freeze(sunday, Progress.FREEZE_REWARD)).rescueDay(today))
    }
}

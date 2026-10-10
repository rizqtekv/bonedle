package app.bonedle.domain

import app.bonedle.data.db.GameResultEntity
import app.bonedle.data.db.SavedGameEntity
import app.bonedle.data.db.StreakFreezeEntity
import app.bonedle.data.game.GameId
import app.bonedle.data.game.Progress
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.daily.Streaks
import app.bonedle.engine.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StatsCalculatorTest {
    private val launch = DailySchedule.LAUNCH_DATE

    private fun daily(n: Int, d: Difficulty, timeMs: Long, onDay: Int = n, at: Long = n.toLong()) = GameResultEntity(
        gameKey = GameId.Daily(n, d).key, mode = "daily", difficulty = d.code, dailyNumber = n, seed = 0, timeMs = timeMs,
        hintsUsed = 0, undoCount = 0, source = "home", completedAt = at, localDate = DailySchedule.dateOf(onDay).toIsoString(),
    )

    private fun unlimited(seed: Long, d: Difficulty, timeMs: Long) = GameResultEntity(
        gameKey = GameId.Unlimited(seed, d).key, mode = "unlimited", difficulty = d.code, dailyNumber = null, seed = seed,
        timeMs = timeMs, hintsUsed = 1, undoCount = 0, source = "home", completedAt = 100 + seed, localDate = launch.toIsoString(),
    )

    private fun save(id: GameId) = SavedGameEntity(id.key, "", "", 1_000, 0, 0, "home", 0, 0)

    @Test
    fun emptyProgress() {
        val s = StatsCalculator.compute(Progress(emptyList(), emptyList(), emptyList()), launch)
        assertEquals(0, s.gamesPlayed)
        assertNull(s.solveRatePercent)
        assertEquals(0, s.currentStreak)
        assertNull(s.averageMs[Difficulty.EASY])
        assertEquals(Stats.HEATMAP_DAYS, s.heatmap.size)
        assertEquals(launch, s.heatmap.last().date)
    }

    @Test
    fun playedSolveRateStreaksAndAverages() {
        val results = listOf(
            daily(1, Difficulty.EASY, 60_000),
            daily(1, Difficulty.MEDIUM, 120_000),
            daily(2, Difficulty.EASY, 90_000),
            daily(3, Difficulty.EASY, 30_000),
            // A replay of the same board does not count twice.
            daily(3, Difficulty.EASY, 10_000, at = 99),
            unlimited(7, Difficulty.EXPERT, 600_000),
        )
        val saves = listOf(save(GameId.Daily(3, Difficulty.HARD)), save(GameId.Unlimited(8, Difficulty.HARD)))
        val today = DailySchedule.dateOf(3)
        val s = StatsCalculator.compute(Progress(results, saves, emptyList()), today)
        assertEquals(5, s.gamesPlayed)
        assertEquals(71, s.solveRatePercent) // 5 of 7, rounded
        assertEquals(3, s.currentStreak)
        assertEquals(3, s.bestStreak)
        assertEquals(60_000L, s.averageMs[Difficulty.EASY]) // (60 + 90 + 30) / 3
        assertEquals(120_000L, s.averageMs[Difficulty.MEDIUM])
        assertNull(s.averageMs[Difficulty.HARD])
        assertEquals(2, s.heatmap.first { it.date == DailySchedule.dateOf(1) }.dailies)
        assertEquals(1, s.heatmap.last().dailies)
    }

    @Test
    fun archiveBoardsDoNotFeedTheStreakAndFreezesBridgeIt() {
        // Day 4 is missed; daily #1 solved later from the archive (on day 5) counts for nothing.
        val results = listOf(daily(2, Difficulty.EASY, 1), daily(3, Difficulty.EASY, 1), daily(1, Difficulty.EASY, 1, onDay = 5), daily(5, Difficulty.EASY, 1))
        val day4 = DailySchedule.dateOf(4).epochDay
        val noFreeze = StatsCalculator.compute(Progress(results, emptyList(), emptyList()), DailySchedule.dateOf(5))
        assertEquals(1, noFreeze.currentStreak)
        val frozen = Progress(results, emptyList(), listOf(StreakFreezeEntity(day4, Streaks.weekStart(day4), Progress.FREEZE_FREE, 0)))
        val withFreeze = StatsCalculator.compute(frozen, DailySchedule.dateOf(5))
        assertEquals(3, withFreeze.currentStreak)
    }
}

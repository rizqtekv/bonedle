package app.bonedle.engine

import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.Streaks
import app.bonedle.engine.rules.PuzzleValidator
import app.bonedle.engine.solver.ExactSolver
import app.bonedle.engine.tutorial.TutorialBoards
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StreaksAndTutorialTest {
    private val d = CalendarDate(2026, 11, 2).epochDay // a Monday

    @Test
    fun currentStreakCountsBackFromTodayOrYesterday() {
        val played = setOf(d, d + 1, d + 2)
        assertEquals(3, Streaks.current(played, emptySet(), today = d + 2))
        // Today not played yet: the streak still stands until the day is over.
        assertEquals(3, Streaks.current(played, emptySet(), today = d + 3))
        assertEquals(0, Streaks.current(played, emptySet(), today = d + 4))
    }

    @Test
    fun frozenDaysBridgeWithoutCounting() {
        val played = setOf(d, d + 1, d + 3)
        assertEquals(1, Streaks.current(played, emptySet(), today = d + 3))
        assertEquals(3, Streaks.current(played, setOf(d + 2), today = d + 3))
        assertEquals(3, Streaks.best(played, setOf(d + 2)))
        assertEquals(2, Streaks.best(played, emptySet()))
    }

    @Test
    fun aFreeFreezeCoversOneMissedDayPerWeek() {
        val played = setOf(d, d + 1)
        // Missed d+2; on d+3 the free freeze covers it.
        assertEquals(d + 2, Streaks.freezeToApply(played, emptySet(), emptySet(), today = d + 3))
        // Already used this week.
        assertNull(Streaks.freezeToApply(played, emptySet(), setOf(Streaks.weekStart(d + 2)), today = d + 3))
        // Two days missed: the streak is gone, nothing to freeze.
        assertNull(Streaks.freezeToApply(played, emptySet(), emptySet(), today = d + 4))
        // Nothing missed.
        assertNull(Streaks.freezeToApply(played + (d + 2), emptySet(), emptySet(), today = d + 3))
    }

    @Test
    fun weeksStartOnMonday() {
        assertEquals(d, Streaks.weekStart(d))
        assertEquals(d, Streaks.weekStart(d + 6))
        assertEquals(d + 7, Streaks.weekStart(d + 7))
        assertEquals(d - 7, Streaks.weekStart(d - 1))
        assertEquals(4L, Streaks.weekStart(10L)) // 1970-01-05 was a Monday
    }

    @Test
    fun tutorialBoardsAreValidAndEachRuleMatters() {
        assertEquals(4, TutorialBoards.STEPS)
        for (step in 0 until TutorialBoards.STEPS) {
            val p = TutorialBoards.puzzle(step)
            assertEquals(emptyList(), PuzzleValidator.problems(p).filter { "colour" !in it }, "step $step")
            assertEquals(4, p.tray.size)
            if (p.regions.isNotEmpty()) {
                val with = ExactSolver.solve(p.board, p.regions, p.tray, maxSolutions = 10_000).solutionCount
                val without = ExactSolver.solve(p.board, emptyList(), p.tray, maxSolutions = 10_000).solutionCount
                assertTrue(with in 1 until without, "step $step: rule must remove options ($with of $without)")
            }
        }
    }
}

package app.bonedle.engine

import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Rule
import app.bonedle.engine.rules.RegionStatus.BROKEN
import app.bonedle.engine.rules.RegionStatus.OPEN
import app.bonedle.engine.rules.RegionStatus.SATISFIED
import app.bonedle.engine.rules.RuleCheck
import app.bonedle.engine.rules.RuleCheck.EMPTY
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RuleCheckTest {
    private fun s(rule: Rule, vararg v: Int) = RuleCheck.status(rule, v)

    @Test
    fun equal() {
        assertEquals(OPEN, s(Rule.EQUAL, EMPTY, EMPTY))
        assertEquals(OPEN, s(Rule.EQUAL, 3, EMPTY))
        assertEquals(BROKEN, s(Rule.EQUAL, 3, EMPTY, 4))
        assertEquals(SATISFIED, s(Rule.EQUAL, 2, 2, 2))
    }

    @Test
    fun allDifferent() {
        assertEquals(OPEN, s(Rule.ALL_DIFFERENT, 1, EMPTY))
        assertEquals(BROKEN, s(Rule.ALL_DIFFERENT, 1, 1, EMPTY))
        assertEquals(SATISFIED, s(Rule.ALL_DIFFERENT, 1, 0, 6))
    }

    @Test
    fun sumEquals() {
        assertEquals(OPEN, s(Rule.sumEq(8), 2, EMPTY))
        assertEquals(BROKEN, s(Rule.sumEq(8), 3, 6))
        assertEquals(BROKEN, s(Rule.sumEq(13), 0, EMPTY)) // at most 0 + 6
        assertEquals(BROKEN, s(Rule.sumEq(3), 4, EMPTY))
        assertEquals(SATISFIED, s(Rule.sumEq(8), 2, 6))
    }

    @Test
    fun sumBounds() {
        assertEquals(OPEN, s(Rule.sumLt(5), 4, EMPTY))
        assertEquals(BROKEN, s(Rule.sumLt(5), 5, EMPTY))
        assertEquals(SATISFIED, s(Rule.sumLt(5), 4, 0))
        assertEquals(OPEN, s(Rule.sumGt(10), 5, EMPTY))
        assertEquals(BROKEN, s(Rule.sumGt(10), 3, EMPTY))
        assertEquals(BROKEN, s(Rule.sumGt(10), 5, 5))
        assertEquals(SATISFIED, s(Rule.sumGt(10), 5, 6))
    }

    @Test
    fun solvedDetectionAcceptsOnlyComplete() {
        val g = GoldenBoards.ALL.first { it.name == "mixed-rules" }
        val puzzle = g.puzzle()
        assertTrue(RuleCheck.isSolved(puzzle, g.solution))
        assertFalse(RuleCheck.isSolved(puzzle, g.solution.drop(1)))
        // Swapping the halves of the 3-1 domino on the right breaks C.
        val broken = g.solution.map { if (it.valueA == 3 && it.valueB == 1) Placement(it.cellA, it.cellB, 1, 3) else it }
        assertFalse(RuleCheck.isSolved(puzzle, broken))
        // Non-adjacent placement.
        val bogus = g.solution.toMutableList().also { it[0] = Placement(0, 7, it[0].valueA, it[0].valueB) }
        assertFalse(RuleCheck.isSolved(puzzle, bogus))
    }
}

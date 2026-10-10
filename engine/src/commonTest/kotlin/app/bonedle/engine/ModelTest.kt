package app.bonedle.engine

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Direction
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.RuleType
import app.bonedle.engine.model.Technique
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelTest {
    @Test
    fun dominoIndexIsABijectionOverTheDoubleSixSet() {
        val seen = HashSet<Int>()
        for (a in 0..6) {
            for (b in a..6) {
                val d = Domino(a, b)
                assertTrue(seen.add(d.index))
                assertEquals(d, Domino.fromIndex(d.index))
                assertEquals(d.index, Domino.indexOf(b, a))
                assertEquals(a, Domino.lowOf(d.index))
                assertEquals(b, Domino.highOf(d.index))
            }
        }
        assertEquals((0 until Domino.TYPE_COUNT).toSet(), seen)
    }

    @Test
    fun dominoIsCanonical() {
        assertEquals(Domino(2, 5), Domino.of(5, 2))
        assertTrue(Domino(3, 3).isDouble)
        assertFailsWith<IllegalArgumentException> { Domino(5, 2) }
        assertFailsWith<IllegalArgumentException> { Domino(0, 7) }
        assertTrue(Domino(1, 6) > Domino(1, 5))
        assertTrue(Domino(2, 2) > Domino(1, 6))
    }

    @Test
    fun boardIndexesActiveCellsRowMajor() {
        val mask = booleanArrayOf(true, false, true, true, true, false)
        val board = Board(2, 3, mask)
        assertEquals(4, board.cellCount)
        assertEquals(0, board.cellAt(0, 0))
        assertEquals(-1, board.cellAt(0, 1))
        assertEquals(1, board.cellAt(0, 2))
        assertEquals(2, board.cellAt(1, 0))
        assertEquals(3, board.cellAt(1, 1))
        assertEquals(-1, board.cellAt(1, 2))
        assertEquals(-1, board.cellAt(5, 5))
        assertEquals(2, board.neighbor(0, Direction.SOUTH))
        assertEquals(-1, board.neighbor(0, Direction.EAST))
        assertEquals(3, board.neighbor(2, Direction.EAST))
        assertTrue(board.areAdjacent(2, 3))
        assertFalse(board.areAdjacent(0, 1))
        assertEquals(Direction.NORTH, board.directionTo(2, 0))
        assertFalse(board.isRectangle)
        assertTrue(Board.rectangle(3, 4).isRectangle)
        assertEquals(Board(2, 3, mask.copyOf()), board)
    }

    @Test
    fun boardRejectsOversizedShapes() {
        assertFailsWith<IllegalArgumentException> { Board.rectangle(9, 2) }
        assertFailsWith<IllegalArgumentException> { Board(2, 2, BooleanArray(3)) }
    }

    @Test
    fun rulesEvaluateFilledRegions() {
        assertTrue(Rule.EQUAL.isSatisfiedBy(intArrayOf(4, 4, 4)))
        assertFalse(Rule.EQUAL.isSatisfiedBy(intArrayOf(4, 4, 3)))
        assertTrue(Rule.ALL_DIFFERENT.isSatisfiedBy(intArrayOf(0, 6, 3)))
        assertFalse(Rule.ALL_DIFFERENT.isSatisfiedBy(intArrayOf(0, 6, 0)))
        assertTrue(Rule.sumEq(9).isSatisfiedBy(intArrayOf(3, 6)))
        assertTrue(Rule.sumLt(4).isSatisfiedBy(intArrayOf(1, 2)))
        assertFalse(Rule.sumLt(3).isSatisfiedBy(intArrayOf(1, 2)))
        assertTrue(Rule.sumGt(10).isSatisfiedBy(intArrayOf(5, 6)))
        assertFalse(Rule.sumGt(11).isSatisfiedBy(intArrayOf(5, 6)))
        assertFailsWith<IllegalArgumentException> { Rule(RuleType.EQUAL, 3) }
    }

    @Test
    fun placementHelpers() {
        val p = Placement(5, 2, 6, 1)
        assertEquals(Placement(2, 5, 1, 6), p.canonical())
        assertEquals(Domino(1, 6), p.domino)
        assertEquals(1, p.valueOn(2))
        assertEquals(5, p.partnerOf(2))
        assertTrue(p.covers(5))
    }

    @Test
    fun enumLookups() {
        assertEquals(Difficulty.HARD, Difficulty.fromCode("hard"))
        assertNull(Difficulty.fromCode("nightmare"))
        assertEquals(Technique.LOOKAHEAD, Technique.fromLevel(8))
        assertNull(Technique.fromLevel(9))
        assertEquals(RuleType.SUM_GT, RuleType.fromCode(4))
        assertEquals(listOf(Difficulty.EASY, Difficulty.MEDIUM, Difficulty.HARD), Difficulty.DAILY)
    }
}

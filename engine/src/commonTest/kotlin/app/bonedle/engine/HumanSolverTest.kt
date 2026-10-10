package app.bonedle.engine

import app.bonedle.engine.grader.HumanSolver
import app.bonedle.engine.grader.Knowledge
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Technique
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HumanSolverTest {
    private fun first(board: TextBoard) = assertNotNull(HumanSolver.nextDeduction(Knowledge.initial(board.puzzle())))

    @Test
    fun singleCellPinsALoneRegionCell() {
        val d = first(TextBoard("t1", listOf("A."), "A:3", "3-4"))
        assertEquals(Technique.SINGLE_CELL, d.technique)
        assertEquals(listOf(0), d.cells)
        assertEquals(3, d.determined.single().value)
    }

    @Test
    fun regionSumForcesBothSixes() {
        val d = first(TextBoard("t2", listOf("AA", ".."), "A:12", "6-6 0-1"))
        assertEquals(Technique.REGION_SUM, d.technique)
        assertEquals(0, d.region)
        assertEquals(setOf(6), d.determined.map { it.value }.toSet())
    }

    @Test
    fun equalRegionNeedsAValueWithEnoughHalves() {
        // Only 3 appears twice in the tray, so the equal pair must be 3s ("needs two 3s").
        val d = first(TextBoard("t3", listOf("AA", ".."), "A:=", "3-3 0-5"))
        assertEquals(Technique.EQUAL_PROPAGATION, d.technique)
        assertEquals(setOf(3), d.determined.map { it.value }.toSet())
    }

    @Test
    fun hintsWorkFromAPartlyFilledBoard() {
        val g = GoldenBoards.ALL.first { it.name == "two-by-five" }
        val known = listOf(g.solution.first())
        val k = Knowledge.initial(g.puzzle(), known)
        val report = HumanSolver.grade(g.puzzle())
        assertTrue(report.solved)
        val d = assertNotNull(HumanSolver.nextDeduction(k))
        // A hint never contradicts the solution.
        val values = g.puzzle().solutionValues()
        HumanSolver.apply(k, d)
        for (c in 0 until k.cellCount) assertTrue(k.candidates(c) and (1 shl values[c]) != 0)
    }

    @Test
    fun aWrongPlacementIsDetectedAsContradiction() {
        val g = GoldenBoards.ALL.first { it.name == "pin-and-pair" }
        // The region needs a 3 but the placement shows 4 there.
        val k = Knowledge.initial(g.puzzle(), listOf(Placement(0, 1, 4, 3)))
        repeat(10) {
            if (HumanSolver.isContradictory(k)) return
            val d = HumanSolver.nextDeduction(k) ?: return@repeat
            HumanSolver.apply(k, d)
        }
        assertTrue(HumanSolver.isContradictory(k))
    }

    @Test
    fun maxTechniqueLimitsTheSolver() {
        // A board that needs more than single-cell logic is not solved when capped at level 1.
        val g = GoldenBoards.ALL.first { it.name == "equal-block" }
        val capped = HumanSolver.grade(g.puzzle(), Technique.SINGLE_CELL)
        val full = HumanSolver.grade(g.puzzle())
        assertTrue(full.solved)
        if (full.hardest != Technique.SINGLE_CELL) assertTrue(!capped.solved)
    }
}

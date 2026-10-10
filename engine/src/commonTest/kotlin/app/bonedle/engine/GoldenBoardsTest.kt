package app.bonedle.engine

import app.bonedle.engine.grader.HumanSolver
import app.bonedle.engine.rules.RuleCheck
import app.bonedle.engine.solver.ExactSolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoldenBoardsTest {
    @Test
    fun thereAreTwentyGoldenBoards() {
        assertEquals(20, GoldenBoards.ALL.size)
        assertEquals(GoldenBoards.ALL.size, GoldenBoards.ALL.map { it.name }.toSet().size)
    }

    @Test
    fun handSolutionsAreValid() {
        for (g in GoldenBoards.ALL) {
            assertTrue(RuleCheck.isSolved(g.puzzle(), g.solution), "${g.name}: hand solution breaks a rule")
        }
    }

    @Test
    fun bruteForceAgreesThereIsExactlyOneSolution() {
        for (g in GoldenBoards.ALL) {
            val all = BruteForce.solutions(g.board, g.regions, g.dominoes)
            assertEquals(listOf(g.solution), all, "${g.name}: brute force")
        }
    }

    @Test
    fun exactSolverFindsTheHandSolutionAndProvesItUnique() {
        for (g in GoldenBoards.ALL) {
            val result = ExactSolver.solve(g.board, g.regions, g.dominoes, maxSolutions = 2)
            assertTrue(result.isUnique, "${g.name}: expected a unique solution, got ${result.solutionCount}")
            assertEquals(g.solution, result.solutions.single(), g.name)
        }
    }

    @Test
    fun humanSolverReachesTheHandSolution() {
        for (g in GoldenBoards.ALL) {
            val report = HumanSolver.grade(g.puzzle())
            assertTrue(report.solved, "${g.name}: human solver got stuck")
            assertEquals(g.solution, report.knowledge.establishedPlacements(), g.name)
        }
    }

    @Test
    fun countedBoardsHaveTheirKnownSolutionCount() {
        for ((g, expected) in GoldenBoards.COUNTED) {
            assertEquals(expected, BruteForce.solutions(g.board, g.regions, g.dominoes).size, "${g.name}: brute force")
            val result = ExactSolver.solve(g.board, g.regions, g.dominoes, maxSolutions = 10)
            assertEquals(expected, result.solutionCount, "${g.name}: exact solver")
        }
    }
}

package app.bonedle.engine

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Region
import app.bonedle.engine.model.Rule
import app.bonedle.engine.random.Xoshiro256StarStar
import app.bonedle.engine.solver.ExactSolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExactSolverTest {
    /**
     * Differential test: on random small boards with random regions, rules and trays (most with
     * zero or many solutions), the exact solver must find exactly the brute-force solution set.
     */
    @Test
    fun agreesWithBruteForceOnRandomBoards() {
        val rng = Xoshiro256StarStar(77L)
        val shapes = listOf(1 to 4, 2 to 2, 2 to 3, 1 to 6, 2 to 4, 3 to 2, 2 to 5)
        var withSolutions = 0
        var multiple = 0
        repeat(400) { round ->
            val (rows, cols) = shapes[rng.nextInt(shapes.size)]
            val mask = BooleanArray(rows * cols) { true }
            val board = Board(rows, cols, mask)
            val tray = List(board.cellCount / 2) {
                if (round % 5 == 0 && it > 0 && rng.chance(30)) Domino(2, 4) else Domino.of(rng.nextInt(7), rng.nextInt(7))
            }.sorted()
            val regions = randomRegions(board, rng)
            val expected = BruteForce.solutions(board, regions, tray, cap = CAP).toSet()
            val actual = ExactSolver.solve(board, regions, tray, maxSolutions = CAP)
            assertTrue(!actual.exhausted)
            if (expected.size == CAP) {
                // Both stopped at the cap, each with its own subset; only the count is comparable.
                assertEquals(CAP, actual.solutionCount, "round $round")
                return@repeat
            }
            assertEquals(expected, actual.solutions.toSet(), "round $round: $board $regions $tray")
            assertEquals(expected.size, actual.solutionCount)
            if (expected.isNotEmpty()) withSolutions++
            if (expected.size > 1) multiple++
        }
        // The sample must exercise both unique and ambiguous boards.
        assertTrue(withSolutions > 100, "only $withSolutions boards had solutions")
        assertTrue(multiple > 30, "only $multiple boards had several solutions")
    }

    @Test
    fun stopsAtTheRequestedSolutionCount() {
        val board = Board.rectangle(2, 4)
        val tray = listOf(Domino(0, 1), Domino(2, 3), Domino(4, 5), Domino(5, 6))
        val result = ExactSolver.solve(board, emptyList(), tray, maxSolutions = 2)
        assertEquals(2, result.solutionCount)
        assertTrue(!result.isUnique)
    }

    @Test
    fun reportsExhaustionAtTheNodeLimit() {
        val board = Board.rectangle(4, 4)
        val tray = (0 until 8).map { Domino.fromIndex(it * 3) }
        val result = ExactSolver.solve(board, emptyList(), tray, maxSolutions = 1_000_000, nodeLimit = 50)
        assertTrue(result.exhausted)
        assertTrue(!result.isUnique)
    }

    private companion object {
        const val CAP = 2_000
    }

    private fun randomRegions(board: Board, rng: Xoshiro256StarStar): List<Region> {
        val regionOf = IntArray(board.cellCount) { -1 }
        val regions = ArrayList<Region>()
        for (seed in 0 until board.cellCount) {
            if (regionOf[seed] >= 0 || rng.chance(30)) continue
            val cells = mutableListOf(seed)
            regionOf[seed] = regions.size
            val size = 1 + rng.nextInt(3)
            while (cells.size < size) {
                val frontier = cells.flatMap { c -> (0 until 4).map { board.neighbor(c, it) } }
                    .filter { it >= 0 && regionOf[it] < 0 }.distinct().sorted()
                if (frontier.isEmpty()) break
                val pick = frontier[rng.nextInt(frontier.size)]
                regionOf[pick] = regions.size
                cells.add(pick)
            }
            val k = cells.size
            val rule = when (rng.nextInt(5)) {
                0 -> Rule.EQUAL
                1 -> Rule.ALL_DIFFERENT
                2 -> Rule.sumEq(rng.nextInt(6 * k + 1))
                3 -> Rule.sumLt(1 + rng.nextInt(6 * k))
                else -> Rule.sumGt(rng.nextInt(6 * k))
            }
            regions.add(Region(cells.sorted(), rule, regions.size % Region.REGION_COLORS))
        }
        return regions
    }
}

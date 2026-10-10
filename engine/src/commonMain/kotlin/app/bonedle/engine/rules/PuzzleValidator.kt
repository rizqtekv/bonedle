package app.bonedle.engine.rules

import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Region

/**
 * Structural validation of a [Puzzle]: everything that must hold regardless of how it was made.
 * Used on decoded puzzles (saved games, the fallback bank) and by the test suite.
 */
object PuzzleValidator {
    /** Human-readable problems with [puzzle]; empty if it is well-formed and its solution is valid. */
    fun problems(puzzle: Puzzle): List<String> {
        val out = ArrayList<String>()
        val board = puzzle.board
        val n = board.cellCount
        if (n % 2 != 0) out.add("odd cell count $n")
        if (puzzle.tray.size * 2 != n) out.add("tray has ${puzzle.tray.size} dominoes for $n cells")
        if (puzzle.tray != puzzle.tray.sorted()) out.add("tray not sorted")
        val typeCounts = IntArray(Domino.TYPE_COUNT)
        for (d in puzzle.tray) typeCounts[d.index]++
        if (puzzle.difficulty != Difficulty.EXPERT && typeCounts.any { it > 1 }) {
            out.add("duplicate dominoes below Expert")
        }

        // Regions: in range, disjoint, connected, coloured apart from neighbours.
        val regionOf = IntArray(n) { -1 }
        for ((r, region) in puzzle.regions.withIndex()) {
            if (region.cells != region.cells.sorted()) out.add("region $r cells not sorted")
            for (c in region.cells) {
                if (c !in 0 until n) {
                    out.add("region $r has invalid cell $c")
                    continue
                }
                if (regionOf[c] >= 0) out.add("cell $c in regions ${regionOf[c]} and $r")
                regionOf[c] = r
            }
            if (!isConnected(puzzle, region)) out.add("region $r not connected")
        }
        if (out.isNotEmpty()) return out
        for (c in 0 until n) {
            val r = regionOf[c]
            if (r < 0) continue
            for (dir in 0 until 4) {
                val d = board.neighbor(c, dir)
                if (d < 0) continue
                val s = regionOf[d]
                if (s >= 0 && s != r && puzzle.regions[s].color == puzzle.regions[r].color) {
                    out.add("touching regions $r and $s share colour ${puzzle.regions[r].color}")
                }
            }
        }

        // Solution: canonical, covers the board with the tray, satisfies every rule.
        val canonical = puzzle.solution.map { it.canonical() }.sortedBy { it.cellA }
        if (canonical != puzzle.solution) out.add("solution not canonical")
        if (!RuleCheck.isSolved(puzzle, puzzle.solution)) out.add("stored solution is not valid")
        return out.distinct()
    }

    private fun isConnected(puzzle: Puzzle, region: Region): Boolean {
        val cells = region.cells
        val inRegion = CellSet(cells)
        val seen = ArrayList<Int>()
        seen.add(cells[0])
        var i = 0
        while (i < seen.size) {
            val c = seen[i++]
            for (dir in 0 until 4) {
                val d = puzzle.board.neighbor(c, dir)
                if (d >= 0 && inRegion.contains(d) && d !in seen) seen.add(d)
            }
        }
        return seen.size == cells.size
    }

    private class CellSet(cells: List<Int>) {
        private val bits = BooleanArray((cells.maxOrNull() ?: 0) + 1)

        init {
            for (c in cells) if (c >= 0) bits[c] = true
        }

        fun contains(c: Int): Boolean = c in bits.indices && bits[c]
    }
}

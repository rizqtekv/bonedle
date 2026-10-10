package app.bonedle.engine

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Grade
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Region
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.Technique

/**
 * A board written as text, for tests.
 *
 * - [layout]: one string per row; `#` is a hole, `.` a free cell, any letter a region id.
 * - [rules]: space-separated `X:rule` entries, where rule is `=`, `!=`, `n`, `<n` or `>n`.
 * - [tray]: space-separated dominoes, e.g. `0-6 3-3`.
 * - [values] / [pairs]: the expected solution; [pairs] marks each domino with a letter.
 */
class TextBoard(
    val name: String,
    val layout: List<String>,
    val rules: String,
    val tray: String,
    val values: List<String>? = null,
    val pairs: List<String>? = null,
) {
    val board: Board by lazy {
        val rows = layout.size
        val cols = layout[0].length
        Board(rows, cols, BooleanArray(rows * cols) { layout[it / cols][it % cols] != '#' })
    }

    val regions: List<Region> by lazy {
        val ruleOf = rules.split(' ').filter { it.isNotBlank() }.associate { entry ->
            val (id, text) = entry.split(':', limit = 2)
            id.single() to parseRule(text)
        }
        val ids = ruleOf.keys.sorted()
        ids.mapIndexed { i, id ->
            val cells = (0 until board.cellCount).filter { layout[board.row(it)][board.col(it)] == id }
            require(cells.isNotEmpty()) { "$name: region $id has no cells" }
            Region(cells, ruleOf.getValue(id), i % Region.REGION_COLORS)
        }.sortedBy { it.cells[0] }
    }

    val dominoes: List<Domino> by lazy {
        tray.split(' ').filter { it.isNotBlank() }.map {
            val (a, b) = it.split('-')
            Domino.of(a.toInt(), b.toInt())
        }.sorted()
    }

    /** The expected solution in canonical form; empty for boards given without one. */
    val solution: List<Placement> by lazy {
        val v = values ?: return@lazy emptyList()
        val p = requireNotNull(pairs) { "$name has values but no pairing" }
        val cellsByMark = HashMap<Char, MutableList<Int>>()
        for (c in 0 until board.cellCount) {
            cellsByMark.getOrPut(p[board.row(c)][board.col(c)]) { ArrayList() }.add(c)
        }
        cellsByMark.values.map { cells ->
            require(cells.size == 2) { "$name: pair mark covers ${cells.size} cells" }
            val (a, b) = cells
            Placement(a, b, v[board.row(a)][board.col(a)].digitToInt(), v[board.row(b)][board.col(b)].digitToInt())
        }.sortedBy { it.cellA }
    }

    fun puzzle(): Puzzle = Puzzle(
        seed = 0L,
        difficulty = Difficulty.EASY,
        board = board,
        regions = regions,
        tray = dominoes,
        solution = solution,
        grade = Grade(Technique.SINGLE_CELL, 0),
    )

    companion object {
        fun parseRule(text: String): Rule = when {
            text == "=" -> Rule.EQUAL
            text == "!=" -> Rule.ALL_DIFFERENT
            text.startsWith("<") -> Rule.sumLt(text.drop(1).toInt())
            text.startsWith(">") -> Rule.sumGt(text.drop(1).toInt())
            else -> Rule.sumEq(text.toInt())
        }
    }
}

/**
 * Exhaustive reference solver, deliberately simple and independent of the engine's solvers:
 * enumerate every domino tiling, then every assignment of tray dominoes (with orientation) to
 * its slots, and check every rule. Only for small boards.
 */
object BruteForce {
    /** Distinct solutions of the board, up to [cap]. */
    fun solutions(board: Board, regions: List<Region>, tray: List<Domino>, cap: Int = 64): List<List<Placement>> {
        val found = LinkedHashSet<List<Placement>>()
        for (tiling in tilings(board)) {
            assign(tiling, 0, tray.sorted().toMutableList(), IntArray(board.cellCount) { -1 }, regions, found, cap)
            if (found.size >= cap) break
        }
        return found.toList()
    }

    private fun tilings(board: Board): List<List<Pair<Int, Int>>> {
        val out = ArrayList<List<Pair<Int, Int>>>()
        val partner = IntArray(board.cellCount) { -1 }
        fun rec(acc: MutableList<Pair<Int, Int>>) {
            val c = (0 until board.cellCount).firstOrNull { partner[it] < 0 }
            if (c == null) {
                out.add(acc.toList())
                return
            }
            for (d in listOf(board.cellAt(board.row(c), board.col(c) + 1), board.cellAt(board.row(c) + 1, board.col(c)))) {
                if (d < 0 || partner[d] >= 0) continue
                partner[c] = d
                partner[d] = c
                acc.add(c to d)
                rec(acc)
                acc.removeAt(acc.lastIndex)
                partner[c] = -1
                partner[d] = -1
            }
        }
        rec(ArrayList())
        return out
    }

    private fun assign(
        slots: List<Pair<Int, Int>>,
        i: Int,
        remaining: MutableList<Domino>,
        values: IntArray,
        regions: List<Region>,
        found: MutableSet<List<Placement>>,
        cap: Int,
    ) {
        if (found.size >= cap) return
        if (i == slots.size) {
            val ok = regions.all { r -> r.rule.isSatisfiedBy(IntArray(r.cells.size) { values[r.cells[it]] }) }
            if (ok) {
                found.add(slots.map { (a, b) -> Placement(a, b, values[a], values[b]) }.sortedBy { it.cellA })
            }
            return
        }
        val (a, b) = slots[i]
        val tried = HashSet<Domino>()
        for (k in remaining.indices) {
            val d = remaining[k]
            if (!tried.add(d)) continue
            remaining.removeAt(k)
            for ((x, y) in listOf(d.low to d.high, d.high to d.low).distinct()) {
                values[a] = x
                values[b] = y
                assign(slots, i + 1, remaining, values, regions, found, cap)
            }
            values[a] = -1
            values[b] = -1
            remaining.add(k, d)
        }
    }
}

/** The hand-made boards used by the golden tests. Each comment is the hand proof of uniqueness. */
object GoldenBoards {
    val ALL: List<TextBoard> = listOf(
        // Only one tiling; the 3 pins the orientation.
        TextBoard("pin-and-pair", listOf("A."), "A:3", "3-4", listOf("34"), listOf("aa")),
        // The only domino is a double.
        TextBoard("double", listOf("AA"), "A:=", "2-2", listOf("22"), listOf("aa")),
        // Vertical tiling would need a double on the equal region; the 5 then fixes the top domino.
        TextBoard("equal-column", listOf("AB", "A."), "A:= B:5", "3-5 3-6", listOf("35", "36"), listOf("aa", "bb")),
        // Forced tiling; <1 needs the 0, >4 needs the 5.
        TextBoard("less-and-more", listOf("A..B"), "A:<1 B:>4", "0-6 1-5", listOf("0615"), listOf("aabb")),
        // Horizontal tiling would put 4 and 1 in the equal region; vertical puts the 4-4 under B.
        TextBoard("two-doubles", listOf("AB", "A."), "A:= B:4", "1-1 4-4", listOf("14", "14"), listOf("ab", "ab")),
        // >2 needs the 3, so the right domino is 1-3; != then flips the left one.
        TextBoard("all-different-pair", listOf(".AAB"), "A:!= B:>2", "1-2 1-3", listOf("1213"), listOf("aabb")),
        // The corner cell forces the D pair to be the double; 6 pins B, so the left column is 2-4.
        TextBoard(
            "notched", listOf("AB#", "..D", "##D"), "A:<3 B:6 D:=", "0-0 2-4 5-6",
            listOf("26#", "450", "##0"), listOf("ab#", "abc", "##c"),
        ),
        // A must be 6+0 (3+3 would starve B); the 2 can only pair with the 0.
        TextBoard(
            "sum-chain", listOf("ABB", "A.C"), "A:6 B:4 C:>2", "0-2 1-6 3-3",
            listOf("613", "023"), listOf("aab", "ccb"),
        ),
        // Identical dominoes are interchangeable, so this is one solution, not two.
        TextBoard("duplicate-dominoes", listOf("A..C"), "A:2 C:<3", "2-5 2-5", listOf("2552"), listOf("aabb")),
        // The equal region needs the double plus the 3 of 3-5.
        TextBoard("equal-run", listOf("AAA."), "A:=", "3-3 3-5", listOf("3335"), listOf("aabb")),
        // A is the 5-5; the 2 must meet its 6 to the right; >8 forces the 3 beside the 6.
        TextBoard(
            "mixed-rules", listOf("ABCC", "ABD."), "A:= B:2 C:>8 D:<5", "0-4 1-3 2-6 5-5",
            listOf("5263", "5041"), listOf("abbd", "accd"),
        ),
        // A ring has two tilings; the second would need a 1-5 domino.
        TextBoard(
            "ring", listOf("AA.", "D#B", ".C."), "A:= B:3 C:>5 D:5", "0-5 1-1 2-3 4-6",
            listOf("112", "5#3", "064"), listOf("aab", "d#b", "dcc"),
        ),
        // Every cell is pinned by a bound; the 0 cannot pair with the 6.
        TextBoard(
            "bounds", listOf("ADB", "CEF"), "A:<1 B:>5 C:>5 D:<2 E:<5 F:>4", "0-1 4-6 5-6",
            listOf("016", "645"), listOf("aab", "ccb"),
        ),
        // The 4 forces 3-4 on the left; != keeps the second 3 out of A.
        TextBoard("all-different-forces", listOf("BAA."), "B:4 A:!=", "2-3 3-4", listOf("4323"), listOf("aabb")),
        // B is the only 0 and pairs with the 5 below it; <2 pins the 1.
        TextBoard(
            "column-pairs", listOf("AB", ".C", "DD"), "A:<2 B:<1 C:>4 D:=", "0-5 1-4 3-3",
            listOf("10", "45", "33"), listOf("ab", "ab", "cc"),
        ),
        // 12 needs the 6-6; the 2 meets the 5; the 1 meets the 0; the equal column takes 4-4.
        TextBoard(
            "two-by-five", listOf("AC.EF", "AD.E."), "A:12 C:1 D:2 E:= F:>2", "0-1 0-3 2-5 4-4 6-6",
            listOf("61043", "62540"), listOf("abbde", "accde"),
        ),
        // The 3-3 must sit on B; <2 forbids the 6 meeting a 1, so the 2-6 stands upright.
        TextBoard(
            "upright", listOf("ABB", "CD."), "A:<3 B:= C:>5 D:<2", "1-5 2-6 3-3",
            listOf("233", "615"), listOf("abb", "acc"),
        ),
        // Two 2x2 blocks joined by one edge; = needs the 2-2, the 3 must be 0+3 split across rows.
        TextBoard(
            "s-shape", listOf("BA##", ".ACD", "##C."), "A:= B:>3 C:3 D:>5", "0-6 1-4 2-2 3-5",
            listOf("42##", "1206", "##35"), listOf("ab##", "abcc", "##dd"),
        ),
        // Four 5s fill the equal block; <2 decides which domino goes on top.
        TextBoard(
            "equal-block", listOf("AAB", "AA."), "A:= B:<2", "1-5 2-5 5-5",
            listOf("551", "552"), listOf("abb", "acc"),
        ),
        // 0 must start the line; >11 needs a 6 next to the first 6.
        TextBoard("greater-run", listOf("ABB.C."), "A:<1 B:>11 C:<2", "0-6 1-5 4-6", listOf("066415"), listOf("aabbcc")),
    )

    /** Boards with a known number of solutions other than one. */
    val COUNTED: List<Pair<TextBoard, Int>> = listOf(
        // Same values, two tilings, plus a vertical arrangement: three distinct placements.
        TextBoard("flippable-block", listOf("A.", "B."), "A:1 B:2", "1-2 1-2") to 3,
        // A sum region cannot orient a lone domino.
        TextBoard("unoriented", listOf("AA"), "A:7", "3-4") to 2,
        // Nothing sums to 5.
        TextBoard("infeasible", listOf("A."), "A:5", "3-4") to 0,
    )
}

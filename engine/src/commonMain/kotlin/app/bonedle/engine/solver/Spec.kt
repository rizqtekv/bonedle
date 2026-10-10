package app.bonedle.engine.solver

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Region
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.RuleType

/**
 * Flattened, array-based view of a puzzle definition (shape, regions, tray) shared by the solvers.
 * Built once per solve; never exposed outside the engine.
 */
internal class Spec(
    val board: Board,
    regionCells: List<IntArray>,
    val rules: List<Rule>,
    tray: List<Domino>,
) {
    val n: Int = board.cellCount
    val regionCount: Int = regionCells.size

    /** Neighbour of cell `c` in direction `d` at `c * 4 + d`, or -1. */
    val nbr = IntArray(n * 4) { board.neighbor(it / 4, it % 4) }
    val regionOf = IntArray(n) { -1 }
    val rCells: Array<IntArray> = Array(regionCount) { regionCells[it].copyOf() }
    val rType = IntArray(regionCount) { rules[it].type.ordinal }
    val rTarget = IntArray(regionCount) { rules[it].target }
    val rSize = IntArray(regionCount) { regionCells[it].size }
    val trayCounts = IntArray(Domino.TYPE_COUNT)
    val dominoCount: Int = tray.size

    init {
        require(tray.size * 2 == n) { "tray must cover the board exactly: ${tray.size} dominoes for $n cells" }
        for ((r, cells) in regionCells.withIndex()) {
            for (c in cells) {
                require(c in 0 until n) { "region cell out of range: $c" }
                require(regionOf[c] == -1) { "cell $c is in two regions" }
                regionOf[c] = r
            }
        }
        for (d in tray) trayCounts[d.index]++
    }

    companion object {
        val EQUAL = RuleType.EQUAL.ordinal
        val ALL_DIFFERENT = RuleType.ALL_DIFFERENT.ordinal
        val SUM_EQ = RuleType.SUM_EQ.ordinal
        val SUM_LT = RuleType.SUM_LT.ordinal
        val SUM_GT = RuleType.SUM_GT.ordinal

        fun of(board: Board, regions: List<Region>, tray: List<Domino>): Spec =
            Spec(board, regions.map { it.cells.toIntArray() }, regions.map { it.rule }, tray)

        /** True if a region of `rule` type whose cells sum to `sum` satisfies the sum rule. */
        fun sumOk(type: Int, target: Int, sum: Int): Boolean = when (type) {
            SUM_EQ -> sum == target
            SUM_LT -> sum < target
            SUM_GT -> sum > target
            else -> true
        }

        /**
         * True if a sum region with `known` already fixed and the rest able to reach anything in
         * `[min, max]` can still satisfy its rule.
         */
        fun sumFeasible(type: Int, target: Int, known: Int, min: Int, max: Int): Boolean = when (type) {
            SUM_EQ -> known + min <= target && target <= known + max
            SUM_LT -> known + min < target
            SUM_GT -> known + max > target
            else -> true
        }
    }
}

/** Bit helpers for 7-value candidate masks. */
internal object Bits {
    fun isSingle(mask: Int): Boolean = mask != 0 && (mask and (mask - 1)) == 0

    fun lowest(mask: Int): Int = mask.countTrailingZeroBits()

    fun highest(mask: Int): Int = 31 - mask.countLeadingZeroBits()

    fun count(mask: Int): Int = mask.countOneBits()
}

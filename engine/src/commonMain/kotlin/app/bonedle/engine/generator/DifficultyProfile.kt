package app.bonedle.engine.generator

import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.RuleType

/** A rectangular board option: [rows] × [cols], picked with relative [weight]. */
internal class RectShape(val rows: Int, val cols: Int, val weight: Int)

/**
 * Every tunable of the generator for one difficulty. Values are integers so generation never
 * depends on floating-point behaviour.
 */
internal class DifficultyProfile(
    val rectangles: List<RectShape>,
    /** Chance (percent) of an irregular board instead of a rectangle. */
    val irregularPercent: Int,
    /** Cell counts an irregular board may have. */
    val irregularCells: IntArray,
    /** Irregular bounding box: columns × rows ranges. */
    val irregularCols: IntRange,
    val irregularRows: IntRange,
    /** Relative weight of each region size; index = size. */
    val regionSizeWeights: IntArray,
    /** Relative weight of each [RuleType], indexed by [RuleType.ordinal]. */
    val ruleWeights: IntArray,
    /** Largest gap between a solution's sum and a `<` / `>` target. */
    val maxSlack: Int,
    /** Chance (percent) that a domino duplicates an earlier one (Expert only). */
    val duplicatePercent: Int,
    /** Inclusive band of the hardest technique level required. */
    val minLevel: Int,
    val maxLevel: Int,
    /** Exact-sum regions offered for relaxation to `<` / `>` after the board is unique. */
    val loosenAttempts: Int,
    /** Region merges tried after the board is unique, so regions are not left fragmented. */
    val mergeAttempts: Int,
    /** Hill-climbing steps that cluster equal and close values (see the generator). */
    val clusterIterations: Int,
) {
    val maxRegionSize: Int get() = regionSizeWeights.lastIndex

    companion object {
        /** Percent of cells covered by regions: 55–80 %. */
        const val MIN_COVERAGE_PERCENT = 55
        const val MAX_COVERAGE_PERCENT = 80

        private val EASY = DifficultyProfile(
            rectangles = listOf(
                RectShape(2, 4, 2), RectShape(2, 5, 3), RectShape(3, 4, 3), RectShape(4, 3, 2),
                RectShape(2, 6, 2),
            ),
            irregularPercent = 0,
            irregularCells = intArrayOf(),
            irregularCols = 0..0,
            irregularRows = 0..0,
            regionSizeWeights = intArrayOf(0, 3, 5, 4, 2),
            ruleWeights = weights(equal = 4, allDifferent = 1, sumEq = 5, sumLt = 1, sumGt = 1),
            maxSlack = 0,
            duplicatePercent = 0,
            minLevel = 1,
            maxLevel = 3,
            loosenAttempts = 3,
            mergeAttempts = 8,
            clusterIterations = 60,
        )

        private val MEDIUM = DifficultyProfile(
            rectangles = listOf(
                RectShape(4, 4, 3), RectShape(3, 6, 2), RectShape(6, 3, 2), RectShape(4, 5, 2),
                RectShape(5, 4, 2),
            ),
            irregularPercent = 0,
            irregularCells = intArrayOf(),
            irregularCols = 0..0,
            irregularRows = 0..0,
            regionSizeWeights = intArrayOf(0, 2, 5, 5, 3, 1),
            ruleWeights = weights(equal = 3, allDifferent = 3, sumEq = 4, sumLt = 2, sumGt = 2),
            maxSlack = 1,
            duplicatePercent = 0,
            minLevel = 4,
            maxLevel = 5,
            loosenAttempts = 5,
            mergeAttempts = 12,
            clusterIterations = 100,
        )

        private val HARD = DifficultyProfile(
            rectangles = listOf(RectShape(4, 6, 2), RectShape(6, 4, 2), RectShape(7, 4, 1)),
            irregularPercent = 50,
            irregularCells = intArrayOf(24, 26, 28),
            irregularCols = 5..6,
            irregularRows = 5..7,
            regionSizeWeights = intArrayOf(0, 1, 4, 5, 4, 2, 1),
            ruleWeights = weights(equal = 2, allDifferent = 3, sumEq = 3, sumLt = 3, sumGt = 3),
            maxSlack = 2,
            duplicatePercent = 0,
            minLevel = 6,
            maxLevel = 7,
            loosenAttempts = 8,
            mergeAttempts = 16,
            clusterIterations = 140,
        )

        private val EXPERT = DifficultyProfile(
            // 36 cells is out of reach: a unique board leaves at most ~7 cells free (one free half
            // per anchored value), which would need more than the 80 % coverage cap.
            rectangles = listOf(RectShape(5, 6, 2), RectShape(6, 5, 2)),
            irregularPercent = 70,
            irregularCells = intArrayOf(30, 32, 34),
            irregularCols = 6..6,
            irregularRows = 6..8,
            regionSizeWeights = intArrayOf(0, 1, 3, 5, 5, 3, 2),
            ruleWeights = weights(equal = 1, allDifferent = 3, sumEq = 2, sumLt = 4, sumGt = 4),
            maxSlack = 3,
            duplicatePercent = 12,
            minLevel = 7,
            maxLevel = 8,
            loosenAttempts = 10,
            mergeAttempts = 18,
            clusterIterations = 160,
        )

        fun of(difficulty: Difficulty): DifficultyProfile = when (difficulty) {
            Difficulty.EASY -> EASY
            Difficulty.MEDIUM -> MEDIUM
            Difficulty.HARD -> HARD
            Difficulty.EXPERT -> EXPERT
        }

        private fun weights(equal: Int, allDifferent: Int, sumEq: Int, sumLt: Int, sumGt: Int): IntArray {
            val out = IntArray(RuleType.entries.size)
            out[RuleType.EQUAL.ordinal] = equal
            out[RuleType.ALL_DIFFERENT.ordinal] = allDifferent
            out[RuleType.SUM_EQ.ordinal] = sumEq
            out[RuleType.SUM_LT.ordinal] = sumLt
            out[RuleType.SUM_GT.ordinal] = sumGt
            return out
        }
    }
}

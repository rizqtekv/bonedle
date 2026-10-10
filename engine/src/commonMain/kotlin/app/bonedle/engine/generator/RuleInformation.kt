package app.bonedle.engine.generator

import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.RuleType

/**
 * How much a rule says about its cells, ignoring the tray: the fraction of the `7^k` value tuples
 * of a `k`-cell region that satisfy it. A unique board needs roughly 2.4 bits per regioned cell,
 * and a one-cell exact sum carries log2(7) ≈ 2.8, so multi-cell rules are only worth keeping when
 * they are nearly as sharp. All arithmetic is integer so results match on every platform.
 */
internal object RuleInformation {
    private const val MAX_CELLS = 6
    private const val MAX_SUM = Domino.MAX_VALUE * MAX_CELLS

    /** `tuples[k][s]`: number of `k`-tuples of values 0–6 summing to `s`. */
    private val tuples: Array<LongArray> = Array(MAX_CELLS + 1) { LongArray(MAX_SUM + 1) }.also { t ->
        t[0][0] = 1
        for (k in 1..MAX_CELLS) {
            for (s in 0..MAX_SUM) {
                var total = 0L
                for (v in 0..Domino.MAX_VALUE) if (s - v >= 0) total += t[k - 1][s - v]
                t[k][s] = total
            }
        }
    }

    /** Number of value tuples for a [k]-cell region that satisfy [rule]. */
    fun satisfyingTuples(rule: Rule, k: Int): Long {
        require(k in 1..MAX_CELLS) { "region size out of range: $k" }
        val row = tuples[k]
        val top = Domino.MAX_VALUE * k
        return when (rule.type) {
            RuleType.EQUAL -> Domino.VALUE_COUNT.toLong()
            RuleType.ALL_DIFFERENT -> {
                var count = 1L
                for (i in 0 until k) count *= (Domino.VALUE_COUNT - i).coerceAtLeast(0)
                count
            }
            RuleType.SUM_EQ -> if (rule.target in 0..top) row[rule.target] else 0L
            RuleType.SUM_LT -> (0 until minOf(rule.target, top + 1)).sumOf { row[it] }
            RuleType.SUM_GT -> ((rule.target + 1)..top).sumOf { row[it] }
        }
    }

    /**
     * True if [rule] on [k] cells carries at least 1.5 bits per cell:
     * `count ≤ 7^k / 2^(1.5k)`, checked exactly as `count² · 8^k ≤ 49^k`.
     */
    fun isInformative(rule: Rule, k: Int): Boolean {
        val count = satisfyingTuples(rule, k)
        if (count == 0L) return false
        var lhs = count * count
        var rhs = 1L
        repeat(k) {
            lhs *= 8
            rhs *= 49
        }
        return lhs <= rhs
    }
}

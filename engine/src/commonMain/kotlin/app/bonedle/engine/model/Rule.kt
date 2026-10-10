package app.bonedle.engine.model

/** The kinds of constraint a region can carry. [code] is the stable serialised identifier. */
enum class RuleType(val code: Int) {
    /** Every cell in the region shows the same value. */
    EQUAL(0),

    /** No two cells in the region show the same value. */
    ALL_DIFFERENT(1),

    /** The region's values sum to exactly [Rule.target]. */
    SUM_EQ(2),

    /** The region's values sum to strictly less than [Rule.target]. */
    SUM_LT(3),

    /** The region's values sum to strictly more than [Rule.target]. */
    SUM_GT(4),
    ;

    /** True for the three sum-based rules. */
    val isSum: Boolean get() = this == SUM_EQ || this == SUM_LT || this == SUM_GT

    companion object {
        /** Inverse of [code], or null for an unknown code. */
        fun fromCode(code: Int): RuleType? = entries.firstOrNull { it.code == code }
    }
}

/** A region rule. [target] is meaningful only for sum rules and is 0 otherwise. */
data class Rule(val type: RuleType, val target: Int = 0) {
    init {
        require(type.isSum || target == 0) { "$type takes no target" }
        require(target >= 0) { "target must be non-negative" }
    }

    /** True if a fully filled region showing [values] satisfies this rule. */
    fun isSatisfiedBy(values: IntArray): Boolean = when (type) {
        RuleType.EQUAL -> values.all { it == values[0] }
        RuleType.ALL_DIFFERENT -> {
            var seen = 0
            var ok = true
            for (v in values) {
                if (seen and (1 shl v) != 0) ok = false
                seen = seen or (1 shl v)
            }
            ok
        }
        RuleType.SUM_EQ -> values.sum() == target
        RuleType.SUM_LT -> values.sum() < target
        RuleType.SUM_GT -> values.sum() > target
    }

    override fun toString(): String = when (type) {
        RuleType.EQUAL -> "="
        RuleType.ALL_DIFFERENT -> "!="
        RuleType.SUM_EQ -> "$target"
        RuleType.SUM_LT -> "<$target"
        RuleType.SUM_GT -> ">$target"
    }

    companion object {
        /** All cells equal. */
        val EQUAL = Rule(RuleType.EQUAL)

        /** All cells different. */
        val ALL_DIFFERENT = Rule(RuleType.ALL_DIFFERENT)

        /** Sum equals [n]. */
        fun sumEq(n: Int) = Rule(RuleType.SUM_EQ, n)

        /** Sum below [n]. */
        fun sumLt(n: Int) = Rule(RuleType.SUM_LT, n)

        /** Sum above [n]. */
        fun sumGt(n: Int) = Rule(RuleType.SUM_GT, n)
    }
}

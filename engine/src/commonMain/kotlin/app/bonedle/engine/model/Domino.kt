package app.bonedle.engine.model

/**
 * An unordered domino with two halves valued 0–6. Stored canonically with [low] ≤ [high].
 */
data class Domino(val low: Int, val high: Int) : Comparable<Domino> {
    init {
        require(low in 0..MAX_VALUE && high in low..MAX_VALUE) { "invalid domino $low-$high" }
    }

    /** True when both halves carry the same value. */
    val isDouble: Boolean get() = low == high

    /** Dense index in `0 until TYPE_COUNT`, unique per domino type. */
    val index: Int get() = indexOf(low, high)

    override fun compareTo(other: Domino): Int = if (low != other.low) low - other.low else high - other.high

    override fun toString(): String = "$low-$high"

    companion object {
        /** Highest pip value on a half. */
        const val MAX_VALUE = 6

        /** Number of distinct values a half can take. */
        const val VALUE_COUNT = MAX_VALUE + 1

        /** Number of distinct domino types in a double-six set. */
        const val TYPE_COUNT = 28

        /** Bit mask with one bit per possible value. */
        const val ALL_VALUES_MASK = (1 shl VALUE_COUNT) - 1

        private val LOW_BY_INDEX = IntArray(TYPE_COUNT)
        private val HIGH_BY_INDEX = IntArray(TYPE_COUNT)

        init {
            var i = 0
            for (a in 0..MAX_VALUE) {
                for (b in a..MAX_VALUE) {
                    LOW_BY_INDEX[i] = a
                    HIGH_BY_INDEX[i] = b
                    i++
                }
            }
        }

        /** The domino carrying values [a] and [b] in either order. */
        fun of(a: Int, b: Int): Domino = if (a <= b) Domino(a, b) else Domino(b, a)

        /** Dense type index for the unordered pair ([a], [b]). */
        fun indexOf(a: Int, b: Int): Int {
            val lo = if (a <= b) a else b
            val hi = if (a <= b) b else a
            return lo * VALUE_COUNT - lo * (lo - 1) / 2 + (hi - lo)
        }

        /** Lower value of the domino type [index]. */
        fun lowOf(index: Int): Int = LOW_BY_INDEX[index]

        /** Higher value of the domino type [index]. */
        fun highOf(index: Int): Int = HIGH_BY_INDEX[index]

        /** The domino with dense type index [index]. */
        fun fromIndex(index: Int): Domino = Domino(LOW_BY_INDEX[index], HIGH_BY_INDEX[index])
    }
}

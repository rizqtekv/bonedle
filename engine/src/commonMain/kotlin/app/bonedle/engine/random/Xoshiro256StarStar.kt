package app.bonedle.engine.random

/**
 * xoshiro256** 1.0 (Blackman, Vigna 2018), seeded through [SplitMix64] as its authors recommend.
 *
 * This is the only source of randomness used by puzzle generation. Never substitute
 * `java.util.Random` or `kotlin.random.Random.Default`: the same seed must produce the same
 * board on every device and on the JS build.
 */
class Xoshiro256StarStar(seed: Long) {
    private var s0: Long
    private var s1: Long
    private var s2: Long
    private var s3: Long

    init {
        val sm = SplitMix64(seed)
        s0 = sm.nextLong()
        s1 = sm.nextLong()
        s2 = sm.nextLong()
        s3 = sm.nextLong()
    }

    /** Returns the next raw 64-bit output. */
    fun nextLong(): Long {
        val result = (s1 * 5).rotateLeft(7) * 9
        val t = s1 shl 17
        s2 = s2 xor s0
        s3 = s3 xor s1
        s1 = s1 xor s2
        s0 = s0 xor s3
        s2 = s2 xor t
        s3 = s3.rotateLeft(45)
        return result
    }

    /** Returns a uniformly distributed int in `[0, bound)`. [bound] must be positive. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive: $bound" }
        // Rejection sampling on 31 high bits (same scheme as java.util.Random#nextInt(int)),
        // implemented here so the sequence is fully specified by this file.
        var r = (nextLong() ushr 33).toInt()
        val m = bound - 1
        if (bound and m == 0) return ((bound.toLong() * r.toLong()) shr 31).toInt()
        var u = r
        while (true) {
            r = u % bound
            if (u - r + m >= 0) return r
            u = (nextLong() ushr 33).toInt()
        }
    }

    /** Returns a uniformly distributed int in `[from, untilInclusive]`. */
    fun nextIntInclusive(from: Int, untilInclusive: Int): Int = from + nextInt(untilInclusive - from + 1)

    /** Returns true with probability [percent]/100. */
    fun chance(percent: Int): Boolean = nextInt(100) < percent

    /** Picks an index with probability proportional to [weights]; at least one weight must be positive. */
    fun weightedIndex(weights: IntArray): Int {
        var total = 0
        for (w in weights) total += w
        require(total > 0) { "weights must have a positive sum" }
        var roll = nextInt(total)
        for (i in weights.indices) {
            roll -= weights[i]
            if (roll < 0) return i
        }
        return weights.lastIndex
    }

    /** Fisher–Yates shuffle of [array] in place. */
    fun shuffle(array: IntArray) {
        for (i in array.lastIndex downTo 1) {
            val j = nextInt(i + 1)
            val tmp = array[i]
            array[i] = array[j]
            array[j] = tmp
        }
    }
}

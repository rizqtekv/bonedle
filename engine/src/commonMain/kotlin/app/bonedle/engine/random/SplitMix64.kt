package app.bonedle.engine.random

/**
 * SplitMix64 (Steele, Lea, Flood 2014), used to expand a single 64-bit seed into well-mixed
 * state words. The arithmetic is pure 64-bit two's-complement, so outputs are identical on
 * the JVM, Android and Kotlin/JS.
 */
class SplitMix64(seed: Long) {
    private var state: Long = seed

    /** Returns the next 64-bit output and advances the state. */
    fun nextLong(): Long {
        state += GOLDEN_GAMMA
        return mix(state)
    }

    companion object {
        private const val GOLDEN_GAMMA: Long = -0x61c8864680b583ebL

        /** The SplitMix64 finaliser applied to [z]; a bijective 64-bit mix. */
        fun mix(z0: Long): Long {
            var z = z0
            z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
            z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
            return z xor (z ushr 31)
        }
    }
}

package app.bonedle.engine.random

/** Platform-independent hashing used to derive seeds from text. */
object Hashing {
    private const val FNV_OFFSET: Long = -0x340d631b7bdddcdbL
    private const val FNV_PRIME: Long = 0x100000001b3L

    /** 64-bit FNV-1a over the UTF-8 bytes of [text]. Stable across platforms and releases. */
    fun fnv1a64(text: String): Long {
        var hash = FNV_OFFSET
        for (b in text.encodeToByteArray()) {
            hash = hash xor (b.toLong() and 0xff)
            hash *= FNV_PRIME
        }
        return hash
    }
}

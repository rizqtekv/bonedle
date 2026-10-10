package app.bonedle.engine

import app.bonedle.engine.random.Hashing
import app.bonedle.engine.random.SplitMix64
import app.bonedle.engine.random.Xoshiro256StarStar
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Reference vectors come from an independent Python implementation of the published algorithms
 * (and, for SplitMix64 seed 1234567 and FNV-1a, match the published test vectors). They pin the
 * exact sequences so every platform generates the same boards.
 */
class RandomTest {
    @Test
    fun splitMix64MatchesReference() {
        val sm = SplitMix64(1234567L)
        assertContentEquals(
            longArrayOf(
                6457827717110365317L, 3203168211198807973L, -8629252141511181193L,
                4593380528125082431L, -2037821214251327795L,
            ),
            LongArray(5) { sm.nextLong() },
        )
        assertEquals(0L, SplitMix64.mix(0L))
        assertEquals(6238072747940578789L, SplitMix64.mix(1L))
        assertEquals(-5417735806833148549L, SplitMix64.mix(-1L))
    }

    @Test
    fun xoshiroMatchesReference() {
        val expected = mapOf(
            0L to longArrayOf(
                -7355399402456485196L, -4652746763540216534L, 1900383378846508768L,
                7684712102626143532L, -4925340083591827879L, -4640532413560118L,
            ),
            42L to longArrayOf(
                1546998764402558742L, 6990951692964543102L, -5902157311460992607L,
                -1389169964527427423L, -151191095644234140L, -4247557243643801032L,
            ),
            -7L to longArrayOf(
                -935278008730389822L, -2984799092062921764L, 8317729841091847865L,
                7641945841512210337L, 8307406274391941071L, 4081416671559498334L,
            ),
        )
        for ((seed, outputs) in expected) {
            val x = Xoshiro256StarStar(seed)
            assertContentEquals(outputs, LongArray(outputs.size) { x.nextLong() }, "seed $seed")
        }
    }

    @Test
    fun nextIntMatchesReference() {
        val x = Xoshiro256StarStar(2026L)
        val bounds = intArrayOf(7, 100, 1 shl 20, 3, 28, 64)
        assertContentEquals(intArrayOf(4, 2, 851977, 0, 12, 50), IntArray(bounds.size) { x.nextInt(bounds[it]) })
    }

    @Test
    fun nextIntStaysInRangeAndCoversIt() {
        val x = Xoshiro256StarStar(99L)
        for (bound in intArrayOf(1, 2, 3, 7, 10, 28)) {
            val seen = IntArray(bound)
            repeat(2_000) {
                val v = x.nextInt(bound)
                assertTrue(v in 0 until bound)
                seen[v]++
            }
            assertTrue(seen.all { it > 0 }, "bound $bound left a value unused")
        }
        assertFailsWith<IllegalArgumentException> { x.nextInt(0) }
    }

    @Test
    fun weightedIndexRespectsZeroWeights() {
        val x = Xoshiro256StarStar(5L)
        repeat(500) { assertTrue(x.weightedIndex(intArrayOf(0, 3, 0, 1)) in setOf(1, 3)) }
    }

    @Test
    fun shuffleIsAPermutation() {
        val x = Xoshiro256StarStar(11L)
        val a = IntArray(20) { it }
        x.shuffle(a)
        assertEquals((0 until 20).toList(), a.sorted())
    }

    @Test
    fun fnv1aMatchesPublishedVectors() {
        assertEquals(-3750763034362895579L, Hashing.fnv1a64("")) // 0xcbf29ce484222325
        assertEquals(-5808556873153909620L, Hashing.fnv1a64("a")) // 0xaf63dc4c8601ec8c
        assertEquals(-8821353812377114648L, Hashing.fnv1a64("foobar")) // 0x85944171f73967e8
    }
}

package app.bonedle.engine

import app.bonedle.engine.codec.PuzzleCodec
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Quick generator checks that run on every platform; the large sample lives in PropertyTest. */
class GeneratorTest {
    @Test
    fun smallSampleSatisfiesEveryInvariant() {
        for (d in Difficulty.entries) {
            for (seed in 1L..6L) {
                val p = PuzzleGenerator.generate(seed * 7919, d)
                assertEquals(emptyList(), PuzzleChecks.problems(p), "$d seed ${seed * 7919}")
            }
        }
    }

    @Test
    fun sameSeedGivesByteIdenticalBoards() {
        for (d in Difficulty.entries) {
            val a = PuzzleCodec.encode(PuzzleGenerator.generate(424242L, d))
            val b = PuzzleCodec.encode(PuzzleGenerator.generate(424242L, d))
            assertContentEquals(a, b, d.name)
        }
    }

    @Test
    fun seedAndDifficultyBothMatter() {
        val easy = PuzzleGenerator.generate(5L, Difficulty.EASY)
        assertNotEquals(easy, PuzzleGenerator.generate(6L, Difficulty.EASY))
        assertNotEquals(easy.board, PuzzleGenerator.generate(5L, Difficulty.HARD).board)
    }

    /**
     * Pinned output: guards against accidental changes to generation, which would change every
     * shared link and daily board. Update deliberately (and bump the codec/salt) if generation
     * must change.
     */
    @Test
    fun generationIsPinned() {
        val p = PuzzleGenerator.generate(20261102L, Difficulty.MEDIUM)
        assertEquals(PINNED_MEDIUM, PuzzleCodec.encodeToString(p))
    }

    @Test
    fun statsAreReported() {
        val g = PuzzleGenerator.generateDetailed(31L, Difficulty.HARD)
        assertTrue(g.stats.attempts >= 1)
        assertTrue(g.stats.solverCalls >= 1)
        assertEquals(g.puzzle.grade.steps, g.techniqueCounts.sum())
    }

    companion object {
        const val PINNED_MEDIUM =
            "Qk4BAAAAAAE1KO4BBQT___AKAAECBhMVMzQ1VQ4CAQUBAAICAQEBBAMAAQICAwMBAwMCAgEEAgAEAQYCAwUBBwIFAQIIDAIFAgEJAAADAgsPAgYAAQ0CAwQBDgIBBAERAgUCARMABBABBSACA0MGCgAHCzMICVUMDQYOEjEPEzUQEVEFACo"
    }
}

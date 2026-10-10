package app.bonedle.engine

import app.bonedle.engine.codec.PuzzleCodec
import app.bonedle.engine.codec.PuzzleFormatException
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PuzzleCodecTest {
    @Test
    fun roundTripsGeneratedPuzzles() {
        for (d in Difficulty.entries) {
            for (seed in listOf(1L, -99L, Long.MAX_VALUE)) {
                val p = PuzzleGenerator.generate(seed, d)
                val bytes = PuzzleCodec.encode(p)
                assertEquals(p, PuzzleCodec.decode(bytes))
                assertContentEquals(bytes, PuzzleCodec.encode(PuzzleCodec.decode(bytes)))
                assertEquals(p, PuzzleCodec.decodeFromString(PuzzleCodec.encodeToString(p)))
            }
        }
    }

    @Test
    fun roundTripsIrregularGoldenBoards() {
        for (g in GoldenBoards.ALL) {
            val p = g.puzzle()
            assertEquals(p, PuzzleCodec.decode(PuzzleCodec.encode(p)), g.name)
        }
    }

    @Test
    fun textIsUrlSafe() {
        val text = PuzzleCodec.encodeToString(PuzzleGenerator.generate(3L, Difficulty.HARD))
        assertEquals(text, text.filter { it.isLetterOrDigit() || it == '-' || it == '_' })
    }

    @Test
    fun rejectsCorruptInput() {
        val good = PuzzleCodec.encode(PuzzleGenerator.generate(8L, Difficulty.EASY))
        assertFailsWith<PuzzleFormatException> { PuzzleCodec.decode(byteArrayOf()) }
        assertFailsWith<PuzzleFormatException> { PuzzleCodec.decode(good.copyOf(good.size - 1)) }
        assertFailsWith<PuzzleFormatException> { PuzzleCodec.decode(good + byteArrayOf(0)) }
        assertFailsWith<PuzzleFormatException> { PuzzleCodec.decode(good.copyOf().also { it[0] = 'X'.code.toByte() }) }
        assertFailsWith<PuzzleFormatException> { PuzzleCodec.decode(good.copyOf().also { it[2] = 9 }) }
        assertFailsWith<PuzzleFormatException> { PuzzleCodec.decodeFromString("not base64!") }
    }
}

package app.bonedle.engine

import app.bonedle.engine.codec.PuzzleBank
import app.bonedle.engine.codec.PuzzleFormatException
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PuzzleBankTest {
    @Test
    fun roundTrips() {
        val puzzles = (0 until 3).map { PuzzleGenerator.generate(PuzzleBank.seedFor(it, Difficulty.MEDIUM), Difficulty.MEDIUM) }
        val text = PuzzleBank.encode(puzzles)
        assertEquals(3, text.lines().count { it.isNotBlank() })
        assertEquals(puzzles, PuzzleBank.decode(text))
        assertEquals(puzzles, PuzzleBank.decode(text.replace("\n", "\r\n") + "\n\n"))
    }

    @Test
    fun seedsAreStableAndDistinct() {
        val seeds = Difficulty.entries.flatMap { d -> (0 until PuzzleBank.SIZE).map { PuzzleBank.seedFor(it, d) } }
        assertEquals(seeds.size, seeds.toSet().size)
        assertEquals(PuzzleBank.seedFor(0, Difficulty.EASY), PuzzleBank.seedFor(0, Difficulty.EASY))
    }

    @Test
    fun rejectsGarbage() {
        assertFailsWith<PuzzleFormatException> { PuzzleBank.decode("hello\n") }
        assertFailsWith<IllegalArgumentException> { PuzzleBank.seedFor(PuzzleBank.SIZE, Difficulty.EASY) }
    }
}

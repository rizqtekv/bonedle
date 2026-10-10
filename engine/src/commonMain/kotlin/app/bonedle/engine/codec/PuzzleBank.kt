package app.bonedle.engine.codec

import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.random.Hashing
import app.bonedle.engine.random.SplitMix64

/**
 * The bundled fallback bank: pre-generated, pre-validated Unlimited boards used when on-device
 * generation would be too slow. Bank boards come from fixed seeds ([seedFor]), so a bank board
 * is shared and replayed exactly like any other Unlimited board.
 *
 * File format: one [PuzzleCodec.encodeToString] puzzle per line, UTF-8.
 */
object PuzzleBank {
    /** Boards per difficulty. */
    const val SIZE = 200

    /** Seed of bank entry [index] at [difficulty]. */
    fun seedFor(index: Int, difficulty: Difficulty): Long {
        require(index in 0 until SIZE) { "bank index out of range: $index" }
        return SplitMix64.mix(Hashing.fnv1a64("bank|${difficulty.code}|$index"))
    }

    /** Asset file name for [difficulty]'s bank. */
    fun fileName(difficulty: Difficulty): String = "bank-${difficulty.code}.txt"

    /** Serialises [puzzles] in bank format. */
    fun encode(puzzles: List<Puzzle>): String = puzzles.joinToString(separator = "\n", postfix = "\n") {
        PuzzleCodec.encodeToString(it)
    }

    /** Parses bank text; throws [PuzzleFormatException] on any malformed line. */
    fun decode(text: String): List<Puzzle> =
        text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.map { PuzzleCodec.decodeFromString(it) }.toList()
}

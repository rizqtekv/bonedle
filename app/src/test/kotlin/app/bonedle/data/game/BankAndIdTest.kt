package app.bonedle.data.game

import app.bonedle.engine.codec.PuzzleBank
import app.bonedle.engine.codec.PuzzleCodec
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class BankAndIdTest {
    /**
     * The bundled bank must be exactly what the engine generates for the bank seeds; if the
     * generator changes, regenerate it with `./gradlew :engine:generateBank` and copy it over.
     */
    @Test
    fun bundledBankMatchesTheGenerator() {
        for (d in Difficulty.entries) {
            val file = File("src/main/assets/bank/${PuzzleBank.fileName(d)}")
            val puzzles = PuzzleBank.decode(file.readText())
            assertEquals(PuzzleBank.SIZE, puzzles.size)
            puzzles.forEachIndexed { i, p ->
                assertEquals("$d #$i seed", PuzzleBank.seedFor(i, d), p.seed)
                assertEquals(d, p.difficulty)
            }
            for (i in listOf(0, 57, PuzzleBank.SIZE - 1)) {
                val regenerated = PuzzleGenerator.generate(PuzzleBank.seedFor(i, d), d)
                assertEquals("$d #$i", PuzzleCodec.encodeToString(regenerated), PuzzleCodec.encodeToString(puzzles[i]))
            }
        }
    }

    @Test
    fun gameKeysRoundTrip() {
        val ids = listOf(GameId.Daily(142, Difficulty.MEDIUM), GameId.Unlimited(-9_000_000_000_000L, Difficulty.EXPERT), GameId.Unlimited(0, Difficulty.EASY))
        for (id in ids) assertEquals(id, GameId.parse(id.key))
        assertNull(GameId.parse("d:0:easy"))
        assertNull(GameId.parse("x:1:easy"))
        assertNull(GameId.parse("d:1:legendary"))
        assertNull(GameId.parse("garbage"))
    }
}

package app.bonedle.data.game

import android.content.Context
import app.bonedle.di.ApplicationScope
import app.bonedle.di.DefaultDispatcher
import app.bonedle.engine.codec.PuzzleBank
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.tutorial.TutorialBoards
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** The bundled fallback bank (assets/bank), loaded lazily per difficulty. */
@Singleton
class PuzzleBankSource @Inject constructor(@ApplicationContext private val context: Context) {
    private val cache = ConcurrentHashMap<Difficulty, List<Puzzle>>()

    fun puzzles(difficulty: Difficulty): List<Puzzle> = cache.getOrPut(difficulty) {
        context.assets.open("$ASSET_DIR/${PuzzleBank.fileName(difficulty)}").bufferedReader().use {
            PuzzleBank.decode(it.readText())
        }
    }

    companion object {
        const val ASSET_DIR = "bank"
    }
}

/**
 * Supplies boards. Dailies are always generated (they must be identical for every player).
 * New Unlimited boards are generated within the spec's time budget; on a slow device the
 * generation keeps running in the background for next time and a bank board is used now.
 */
@Singleton
class PuzzleRepository @Inject constructor(
    private val bank: PuzzleBankSource,
    @DefaultDispatcher private val dispatcher: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val random = SecureRandom()
    private val ready = ConcurrentHashMap<Difficulty, Pair<GameId.Unlimited, Puzzle>>()

    /** The board for [id]. Generation is CPU work and runs off the main thread. */
    suspend fun puzzle(id: GameId): Puzzle = withContext(dispatcher) {
        when (id) {
            is GameId.Daily -> DailySchedule.puzzle(id.number, id.difficulty)
            is GameId.Unlimited -> PuzzleGenerator.generate(id.seed, id.difficulty)
            is GameId.Tutorial -> TutorialBoards.puzzle(id.step)
        }
    }

    /** Generates the next Unlimited board at [difficulty] in the background, so "Next puzzle" is instant. */
    fun prefetch(difficulty: Difficulty) {
        if (ready.containsKey(difficulty)) return
        appScope.launch(dispatcher) {
            val id = GameId.Unlimited(random.nextLong(), difficulty)
            ready.putIfAbsent(difficulty, id to PuzzleGenerator.generate(id.seed, difficulty))
        }
    }

    /** A brand-new Unlimited board at [difficulty]. */
    suspend fun newUnlimited(difficulty: Difficulty): Pair<GameId.Unlimited, Puzzle> {
        ready.remove(difficulty)?.let { return it }
        val id = GameId.Unlimited(random.nextLong(), difficulty)
        // Not a child of the caller: if it overruns the budget it finishes in the background.
        val generation = appScope.async(dispatcher) { id to PuzzleGenerator.generate(id.seed, difficulty) }
        val generated = withTimeoutOrNull(budgetMs(difficulty)) { generation.await() }
        if (generated != null) return generated
        // Keep the late result for the next request at this difficulty.
        appScope.launch { runCatching { generation.await() }.getOrNull()?.let { ready.putIfAbsent(difficulty, it) } }
        return withContext(dispatcher) {
            val index = random.nextInt(PuzzleBank.SIZE)
            val puzzles = bank.puzzles(difficulty)
            val puzzle = puzzles[index % puzzles.size]
            GameId.Unlimited(puzzle.seed, difficulty) to puzzle
        }
    }

    private fun budgetMs(difficulty: Difficulty): Long = when (difficulty) {
        Difficulty.EASY, Difficulty.MEDIUM -> 150L
        Difficulty.HARD -> 400L
        Difficulty.EXPERT -> 1_500L
    }
}

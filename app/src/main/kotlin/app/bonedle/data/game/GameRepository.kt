package app.bonedle.data.game

import app.bonedle.data.Clock
import app.bonedle.data.db.GameDao
import app.bonedle.data.db.GameResultEntity
import app.bonedle.data.db.SavedGameEntity
import app.bonedle.di.IoDispatcher
import app.bonedle.engine.codec.PuzzleCodec
import app.bonedle.engine.game.Game
import app.bonedle.engine.game.GameCodec
import app.bonedle.engine.model.Puzzle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** An unfinished board as saved. */
data class SavedGame(
    val id: GameId,
    val game: Game,
    val elapsedMs: Long,
    val hintsUsed: Int,
    val undoCount: Int,
    val source: GameSource,
    val startedAt: Long,
) {
    val puzzle: Puzzle get() = game.puzzle
}

/** A finished board. */
data class GameResult(
    val id: GameId,
    val timeMs: Long,
    val hintsUsed: Int,
    val undoCount: Int,
    val completedAt: Long,
    val localDate: String,
)

/** In-progress saves and finished results. */
@Singleton
class GameRepository @Inject constructor(
    private val dao: GameDao,
    private val clock: Clock,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    /** The save for [id], or null. A save that no longer decodes is dropped. */
    suspend fun load(id: GameId): SavedGame? = withContext(io) {
        val e = dao.save(id.key) ?: return@withContext null
        val restored = runCatching {
            val puzzle = PuzzleCodec.decodeFromString(e.puzzle)
            SavedGame(
                id = id,
                game = GameCodec.decode(puzzle, e.board),
                elapsedMs = e.elapsedMs,
                hintsUsed = e.hintsUsed,
                undoCount = e.undoCount,
                source = GameSource.fromCode(e.source),
                startedAt = e.startedAt,
            )
        }.getOrNull()
        if (restored == null) dao.deleteSave(id.key)
        restored
    }

    suspend fun save(save: SavedGame) = withContext(io) {
        dao.upsertSave(
            SavedGameEntity(
                gameKey = save.id.key,
                puzzle = PuzzleCodec.encodeToString(save.puzzle),
                board = GameCodec.encode(save.game),
                elapsedMs = save.elapsedMs,
                hintsUsed = save.hintsUsed,
                undoCount = save.undoCount,
                source = save.source.code,
                startedAt = save.startedAt,
                updatedAt = clock.nowMillis(),
            ),
        )
    }

    /** Records a finished board and removes its save, as one step. */
    suspend fun complete(save: SavedGame): GameResult = withContext(io) {
        val now = clock.nowMillis()
        val today = clock.today().toIsoString()
        val id = save.id
        dao.insertResult(
            GameResultEntity(
                gameKey = id.key,
                mode = if (id is GameId.Daily) MODE_DAILY else MODE_UNLIMITED,
                difficulty = id.difficulty.code,
                dailyNumber = (id as? GameId.Daily)?.number,
                seed = save.puzzle.seed,
                timeMs = save.elapsedMs,
                hintsUsed = save.hintsUsed,
                undoCount = save.undoCount,
                source = save.source.code,
                completedAt = now,
                localDate = today,
            ),
        )
        dao.deleteSave(id.key)
        GameResult(id, save.elapsedMs, save.hintsUsed, save.undoCount, now, today)
    }

    /** The first completion of [id], or null if it was never solved. */
    suspend fun result(id: GameId): GameResult? = withContext(io) {
        dao.firstResult(id.key)?.let { GameResult(id, it.timeMs, it.hintsUsed, it.undoCount, it.completedAt, it.localDate) }
    }

    companion object {
        const val MODE_DAILY = "daily"
        const val MODE_UNLIMITED = "unlimited"
    }
}

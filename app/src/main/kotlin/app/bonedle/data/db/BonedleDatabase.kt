package app.bonedle.data.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * An unfinished board, saved after every move so it survives process death.
 * [puzzle] is `PuzzleCodec` text; [board] is `GameCodec` text.
 */
@Entity(tableName = "saved_games")
data class SavedGameEntity(
    @PrimaryKey val gameKey: String,
    val puzzle: String,
    val board: String,
    val elapsedMs: Long,
    val hintsUsed: Int,
    val undoCount: Int,
    val source: String,
    val startedAt: Long,
    val updatedAt: Long,
    /** Hints earned on this board by watching a rewarded ad, on top of the free ones. */
    @ColumnInfo(defaultValue = "0") val extraHints: Int = 0,
)

/** A finished board: the source for stats, streaks, the archive and the share card. */
@Entity(
    tableName = "results",
    indices = [Index("gameKey"), Index("localDate"), Index("mode", "difficulty")],
)
data class GameResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameKey: String,
    /** "daily" or "unlimited". */
    val mode: String,
    val difficulty: String,
    val dailyNumber: Int?,
    val seed: Long,
    val timeMs: Long,
    val hintsUsed: Int,
    val undoCount: Int,
    val source: String,
    val completedAt: Long,
    /** Player's local date at completion, ISO-8601; drives streaks and the calendar heatmap. */
    val localDate: String,
)

/** A missed day covered by a streak freeze. [weekStart] is the Monday of that day's ISO week. */
@Entity(tableName = "streak_freezes")
data class StreakFreezeEntity(
    @PrimaryKey val epochDay: Long,
    val weekStart: Long,
    /** "free" (weekly) or "reward" (rewarded ad). */
    val source: String,
    val createdAt: Long,
)

/**
 * A friend's result on a board, from the challenge link that opened it (spec 8.3). The latest
 * link for a board wins.
 */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey val gameKey: String,
    val opponentMs: Long,
    val opponentHints: Int,
    val receivedAt: Long,
)

@Dao
interface GameDao {
    @Upsert
    suspend fun upsertSave(save: SavedGameEntity)

    @Query("SELECT * FROM saved_games WHERE gameKey = :key")
    suspend fun save(key: String): SavedGameEntity?

    @Query("DELETE FROM saved_games WHERE gameKey = :key")
    suspend fun deleteSave(key: String)

    @Insert
    suspend fun insertResult(result: GameResultEntity): Long

    @Query("SELECT * FROM results WHERE gameKey = :key ORDER BY completedAt LIMIT 1")
    suspend fun firstResult(key: String): GameResultEntity?

    @Query("SELECT * FROM results ORDER BY completedAt")
    fun results(): Flow<List<GameResultEntity>>

    @Query("SELECT * FROM saved_games ORDER BY updatedAt DESC")
    fun saves(): Flow<List<SavedGameEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertFreeze(freeze: StreakFreezeEntity)

    @Query("SELECT * FROM streak_freezes")
    fun freezes(): Flow<List<StreakFreezeEntity>>

    @Query("SELECT * FROM streak_freezes")
    suspend fun allFreezes(): List<StreakFreezeEntity>

    @Query("SELECT * FROM results")
    suspend fun allResults(): List<GameResultEntity>

    /** Unlimited boards solved after [since] (epoch ms), for the interstitial pacing. */
    @Query("SELECT COUNT(*) FROM results WHERE mode = 'unlimited' AND completedAt > :since")
    suspend fun unlimitedSolvedSince(since: Long): Int

    @Upsert
    suspend fun upsertChallenge(challenge: ChallengeEntity)

    @Query("SELECT * FROM challenges WHERE gameKey = :key")
    suspend fun challenge(key: String): ChallengeEntity?
}

@Database(
    entities = [SavedGameEntity::class, GameResultEntity::class, StreakFreezeEntity::class, ChallengeEntity::class],
    version = 4,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class BonedleDatabase : RoomDatabase() {
    abstract fun games(): GameDao

    companion object {
        const val NAME = "bonedle.db"
    }
}

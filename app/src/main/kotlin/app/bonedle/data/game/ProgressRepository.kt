package app.bonedle.data.game

import app.bonedle.data.Clock
import app.bonedle.data.db.GameDao
import app.bonedle.data.db.GameResultEntity
import app.bonedle.data.db.SavedGameEntity
import app.bonedle.data.db.StreakFreezeEntity
import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.daily.Streaks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** Where a board stands for the player. */
sealed interface BoardStatus {
    data object New : BoardStatus

    data class InProgress(val elapsedMs: Long) : BoardStatus

    data class Solved(val timeMs: Long, val hintsUsed: Int) : BoardStatus
}

/** Current and best daily streak. */
data class StreakInfo(val current: Int, val best: Int, val freezeUsedThisWeek: Boolean)

/** Everything the player has done, as one snapshot the screens derive from. */
data class Progress(
    val results: List<GameResultEntity>,
    val saves: List<SavedGameEntity>,
    val freezes: List<StreakFreezeEntity>,
) {
    private val firstResult: Map<String, GameResultEntity> = results.groupBy { it.gameKey }.mapValues { (_, r) -> r.minBy { it.completedAt } }
    private val saveByKey: Map<String, SavedGameEntity> = saves.associateBy { it.gameKey }

    fun status(key: String): BoardStatus {
        firstResult[key]?.let { return BoardStatus.Solved(it.timeMs, it.hintsUsed) }
        saveByKey[key]?.let { return BoardStatus.InProgress(it.elapsedMs) }
        return BoardStatus.New
    }

    /** The most recently touched unfinished Unlimited board, optionally at one difficulty. */
    fun latestUnlimitedSave(difficultyCode: String): GameId.Unlimited? =
        saves.asSequence()
            .mapNotNull { GameId.parse(it.gameKey) as? GameId.Unlimited }
            .firstOrNull { it.difficulty.code == difficultyCode }

    /** Epoch days on which the player solved that day's daily. */
    val playedDays: Set<Long> by lazy {
        results.asSequence()
            .filter { it.mode == GameRepository.MODE_DAILY && it.dailyNumber != null }
            .mapNotNull { r ->
                val date = CalendarDate.parse(r.localDate) ?: return@mapNotNull null
                date.epochDay.takeIf { DailySchedule.numberOn(date) == r.dailyNumber }
            }
            .toSet()
    }

    val frozenDays: Set<Long> get() = freezes.map { it.epochDay }.toSet()

    fun streak(today: Long): StreakInfo = StreakInfo(
        current = Streaks.current(playedDays, frozenDays, today),
        best = Streaks.best(playedDays, frozenDays),
        freezeUsedThisWeek = freezes.any { it.source == FREEZE_FREE && it.weekStart == Streaks.weekStart(today) },
    )

    companion object {
        const val FREEZE_FREE = "free"
        const val FREEZE_REWARD = "reward"
    }
}

/** Live view of the player's progress, plus streak upkeep. */
@Singleton
class ProgressRepository @Inject constructor(
    private val dao: GameDao,
    private val clock: Clock,
) {
    val progress: Flow<Progress> = combine(dao.results(), dao.saves(), dao.freezes()) { r, s, f -> Progress(r, s, f) }

    /**
     * Spends the weekly free streak freeze on yesterday if yesterday was missed and the streak
     * was alive before it. Returns the frozen day, or null. Call when the app comes to the
     * foreground.
     */
    suspend fun reconcileStreak(): Long? {
        val progress = Progress(dao.allResults(), emptyList(), dao.allFreezes())
        val today = clock.today().epochDay
        val freeWeeks = progress.freezes.filter { it.source == Progress.FREEZE_FREE }.map { it.weekStart }.toSet()
        val day = Streaks.freezeToApply(progress.playedDays, progress.frozenDays, freeWeeks, today) ?: return null
        dao.insertFreeze(StreakFreezeEntity(day, Streaks.weekStart(day), Progress.FREEZE_FREE, clock.nowMillis()))
        return day
    }
}

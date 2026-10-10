package app.bonedle.domain

import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameRepository
import app.bonedle.data.game.Progress
import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty

/** One day of the completion heatmap. */
data class HeatDay(val date: CalendarDate, val dailies: Int)

/** Everything the Stats screen shows. */
data class Stats(
    val gamesPlayed: Int,
    /** Solved boards as a share of boards started; null before anything was started. */
    val solveRatePercent: Int?,
    val currentStreak: Int,
    val bestStreak: Int,
    val freezeUsedThisWeek: Boolean,
    /** Mean solve time per difficulty, null where nothing was solved. */
    val averageMs: Map<Difficulty, Long?>,
    /** The last [HEATMAP_DAYS] days, oldest first, counting that day's dailies solved on that day. */
    val heatmap: List<HeatDay>,
) {
    companion object {
        const val HEATMAP_DAYS = 30
    }
}

/** Derives [Stats] from the player's [Progress]. Pure, so it is unit tested directly. */
object StatsCalculator {
    fun compute(progress: Progress, today: CalendarDate): Stats {
        val results = progress.results
        val solvedKeys = results.map { it.gameKey }.toSet()
        // Tutorial boards are never saved, so every save is a real board left unfinished.
        val started = solvedKeys.size + progress.saves.count { it.gameKey !in solvedKeys && GameId.parse(it.gameKey) !is GameId.Tutorial }
        val firstPerKey = results.groupBy { it.gameKey }.values.map { r -> r.minBy { it.completedAt } }
        val averages = Difficulty.entries.associateWith { d ->
            firstPerKey.filter { it.difficulty == d.code }.map { it.timeMs }.takeIf { it.isNotEmpty() }?.average()?.toLong()
        }
        val streak = progress.streak(today.epochDay)
        val dailiesByDay = firstPerKey
            .filter { it.mode == GameRepository.MODE_DAILY }
            .groupBy { it.localDate }
            .mapValues { (date, rs) ->
                val day = CalendarDate.parse(date)
                rs.count { r -> day != null && DailySchedule.numberOn(day) == r.dailyNumber }
            }
        val heatmap = (Stats.HEATMAP_DAYS - 1 downTo 0).map { back ->
            val date = today.plusDays(-back.toLong())
            HeatDay(date, dailiesByDay[date.toIsoString()] ?: 0)
        }
        return Stats(
            gamesPlayed = firstPerKey.size,
            solveRatePercent = if (started == 0) null else (firstPerKey.size * 100 + started / 2) / started,
            currentStreak = streak.current,
            bestStreak = streak.best,
            freezeUsedThisWeek = streak.freezeUsedThisWeek,
            averageMs = averages,
            heatmap = heatmap,
        )
    }
}

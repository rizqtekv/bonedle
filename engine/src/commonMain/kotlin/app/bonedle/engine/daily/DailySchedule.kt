package app.bonedle.engine.daily

import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.random.Hashing
import app.bonedle.engine.random.SplitMix64

/**
 * The global daily schedule. Bonedle #1 is [LAUNCH_DATE]; board #n belongs to the UTC calendar
 * date `LAUNCH_DATE + (n - 1)` and is identical for every player. The app decides which number is
 * "today" from the player's local date, so the daily rolls over at local midnight.
 */
object DailySchedule {
    /** Launch day, Bonedle #1. Change before release if the launch date moves. */
    val LAUNCH_DATE: CalendarDate = CalendarDate(2026, 11, 2)

    /** Salt mixed into daily seeds. Changing it changes every daily board. */
    const val APP_SALT: String = "bonedle.daily.v1.3f9a61c2"

    /** First valid daily number. */
    const val FIRST_NUMBER: Int = 1

    /**
     * The daily number for a player whose local calendar date is [localDate]. Dates before
     * launch map to [FIRST_NUMBER].
     */
    fun numberOn(localDate: CalendarDate): Int {
        val n = localDate.epochDay - LAUNCH_DATE.epochDay + 1
        return if (n < FIRST_NUMBER) FIRST_NUMBER else n.toInt()
    }

    /** The calendar date daily [number] belongs to. */
    fun dateOf(number: Int): CalendarDate {
        require(number >= FIRST_NUMBER) { "daily numbers start at $FIRST_NUMBER: $number" }
        return LAUNCH_DATE.plusDays(number - 1L)
    }

    /** Seed of daily [number] at [difficulty]: SplitMix64(FNV-1a(date | difficulty | salt)). */
    fun seedFor(number: Int, difficulty: Difficulty): Long =
        SplitMix64.mix(Hashing.fnv1a64("${dateOf(number).toIsoString()}|${difficulty.code}|$APP_SALT"))

    /** Generates daily [number] at [difficulty]. Call off the main thread. */
    fun puzzle(number: Int, difficulty: Difficulty): Puzzle = PuzzleGenerator.generate(seedFor(number, difficulty), difficulty)
}

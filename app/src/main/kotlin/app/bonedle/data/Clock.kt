package app.bonedle.data

import app.bonedle.engine.daily.CalendarDate
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Time source, injectable so tests control it. */
interface Clock {
    /** Wall-clock time for timestamps. */
    fun nowMillis(): Long

    /** Monotonic time for measuring play time (unaffected by clock changes). */
    fun elapsedRealtimeMillis(): Long

    /** The player's local calendar date (the daily rolls over at local midnight). */
    fun today(): CalendarDate
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun nowMillis(): Long = System.currentTimeMillis()

    override fun elapsedRealtimeMillis(): Long = android.os.SystemClock.elapsedRealtime()

    override fun today(): CalendarDate {
        val d = LocalDate.now(ZoneId.systemDefault())
        return CalendarDate(d.year, d.monthValue, d.dayOfMonth)
    }
}

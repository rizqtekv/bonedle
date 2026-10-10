package app.bonedle.engine.daily

/**
 * A proleptic Gregorian calendar date with no time zone. Implemented here (rather than using
 * `java.time`) so the engine stays platform-independent.
 */
data class CalendarDate(val year: Int, val month: Int, val day: Int) : Comparable<CalendarDate> {
    init {
        require(month in 1..12) { "month out of range: $month" }
        require(day in 1..daysInMonth(year, month)) { "day out of range: $year-$month-$day" }
    }

    /** Days since 1970-01-01 (negative before). */
    val epochDay: Long
        get() {
            // Howard Hinnant's days_from_civil.
            val y = if (month <= 2) year - 1L else year.toLong()
            val era = (if (y >= 0) y else y - 399) / 400
            val yoe = y - era * 400
            val mp = (month + 9) % 12
            val doy = (153 * mp + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            return era * 146_097 + doe - 719_468
        }

    /** The date [days] later (or earlier, if negative). */
    fun plusDays(days: Long): CalendarDate = fromEpochDay(epochDay + days)

    /** ISO-8601 form, e.g. `2026-11-02`. */
    fun toIsoString(): String = buildString {
        append(year.toString().padStart(4, '0'))
        append('-')
        append(month.toString().padStart(2, '0'))
        append('-')
        append(day.toString().padStart(2, '0'))
    }

    override fun compareTo(other: CalendarDate): Int = epochDay.compareTo(other.epochDay)

    override fun toString(): String = toIsoString()

    companion object {
        /** The date [epochDay] days after 1970-01-01. */
        fun fromEpochDay(epochDay: Long): CalendarDate {
            // Howard Hinnant's civil_from_days.
            val z = epochDay + 719_468
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
            val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
            val y = (yoe + era * 400 + if (m <= 2) 1 else 0).toInt()
            return CalendarDate(y, m, d)
        }

        /** Parses `YYYY-MM-DD`; returns null if malformed. */
        fun parse(iso: String): CalendarDate? {
            val parts = iso.split('-')
            if (parts.size != 3) return null
            val y = parts[0].toIntOrNull() ?: return null
            val m = parts[1].toIntOrNull() ?: return null
            val d = parts[2].toIntOrNull() ?: return null
            if (m !in 1..12 || d !in 1..daysInMonth(y, m)) return null
            return CalendarDate(y, m, d)
        }

        private fun isLeap(year: Int): Boolean = (year % 4 == 0 && year % 100 != 0) || year % 400 == 0

        /** Number of days in [month] of [year]. */
        fun daysInMonth(year: Int, month: Int): Int = when (month) {
            2 -> if (isLeap(year)) 29 else 28
            4, 6, 9, 11 -> 30
            else -> 31
        }
    }
}

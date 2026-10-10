package app.bonedle.ui.format

import android.content.Context
import android.text.format.DateFormat
import androidx.annotation.StringRes
import app.bonedle.R
import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.model.Difficulty
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `m:ss`, or `h:mm:ss` from one hour. Digits only, so it reads the same in every locale. */
fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(Locale.ROOT, h, m, s) else "%d:%02d".format(Locale.ROOT, m, s)
}

/** Display name of [difficulty]. */
@StringRes
fun difficultyLabel(difficulty: Difficulty): Int = when (difficulty) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
    Difficulty.EXPERT -> R.string.difficulty_expert
}

/** [date] in the device locale with the given skeleton, e.g. "EEEEMMMMd" → "Saturday, October 10". */
fun formatDate(date: CalendarDate, skeleton: String, locale: Locale = Locale.getDefault()): String {
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    val local = LocalDate.of(date.year, date.month, date.day)
    return DateTimeFormatter.ofPattern(pattern, locale).format(local)
}

/** Long weekday date for headers. */
fun formatLongDate(date: CalendarDate): String = formatDate(date, "EEEEMMMMd")

/** Short date for lists: "Fri, Oct 9". */
fun formatShortDate(date: CalendarDate): String = formatDate(date, "EEEMMMd")

/** [minuteOfDay] as a clock time in the device's 12- or 24-hour style: "9:00 AM", "09:00". */
fun formatTimeOfDay(context: Context, minuteOfDay: Int, locale: Locale = Locale.getDefault()): String {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale).format(LocalTime.of(minuteOfDay / 60, minuteOfDay % 60))
}

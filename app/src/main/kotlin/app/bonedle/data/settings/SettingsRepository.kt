package app.bonedle.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.bonedle.engine.model.Difficulty
import app.bonedle.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Player preferences. Defaults are the first-launch experience. */
data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val sound: Boolean = true,
    val haptics: Boolean = true,
    val hideTimer: Boolean = false,
    /** Refuse placements that break a rule instead of flagging them. */
    val strictMode: Boolean = false,
    /** The tutorial was finished or skipped. */
    val onboarded: Boolean = false,
    /** Last difficulty picked on the Unlimited card. */
    val unlimitedDifficulty: Difficulty = Difficulty.MEDIUM,
    /** The optional daily reminder (spec 8.5); off until the player turns it on. */
    val reminderEnabled: Boolean = false,
    /** Reminder time as minutes after local midnight. */
    val reminderMinutes: Int = DEFAULT_REMINDER_MINUTES,
) {
    companion object {
        /** 9:00 in the morning, when the day's boards are fresh. */
        const val DEFAULT_REMINDER_MINUTES = 9 * 60
    }
}

/** Settings persisted in Preferences DataStore. */
@Singleton
class SettingsRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            theme = p[THEME]?.let { name -> ThemeMode.entries.firstOrNull { it.name == name } } ?: ThemeMode.SYSTEM,
            sound = p[SOUND] ?: true,
            haptics = p[HAPTICS] ?: true,
            hideTimer = p[HIDE_TIMER] ?: false,
            strictMode = p[STRICT] ?: false,
            onboarded = p[ONBOARDED] ?: false,
            unlimitedDifficulty = p[UNLIMITED]?.let(Difficulty::fromCode) ?: Difficulty.MEDIUM,
            reminderEnabled = p[REMINDER_ON] ?: false,
            reminderMinutes = p[REMINDER_MINUTES]?.takeIf { it in 0 until MINUTES_PER_DAY } ?: Settings.DEFAULT_REMINDER_MINUTES,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[THEME] = mode.name }

    suspend fun setSound(on: Boolean) = store.edit { it[SOUND] = on }

    suspend fun setHaptics(on: Boolean) = store.edit { it[HAPTICS] = on }

    suspend fun setHideTimer(hide: Boolean) = store.edit { it[HIDE_TIMER] = hide }

    suspend fun setStrictMode(on: Boolean) = store.edit { it[STRICT] = on }

    suspend fun setOnboarded() = store.edit { it[ONBOARDED] = true }

    suspend fun setUnlimitedDifficulty(d: Difficulty) = store.edit { it[UNLIMITED] = d.code }

    /** Use [app.bonedle.notifications.ReminderController], which also arms the alarm. */
    suspend fun setReminder(enabled: Boolean, minutes: Int) = store.edit {
        it[REMINDER_ON] = enabled
        it[REMINDER_MINUTES] = minutes.coerceIn(0, MINUTES_PER_DAY - 1)
    }

    private companion object {
        val THEME = stringPreferencesKey("theme")
        val SOUND = booleanPreferencesKey("sound")
        val HAPTICS = booleanPreferencesKey("haptics")
        val HIDE_TIMER = booleanPreferencesKey("hide_timer")
        val STRICT = booleanPreferencesKey("strict_mode")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val UNLIMITED = stringPreferencesKey("unlimited_difficulty")
        val REMINDER_ON = booleanPreferencesKey("reminder_enabled")
        val REMINDER_MINUTES = intPreferencesKey("reminder_minutes")
        const val MINUTES_PER_DAY = 24 * 60
    }
}

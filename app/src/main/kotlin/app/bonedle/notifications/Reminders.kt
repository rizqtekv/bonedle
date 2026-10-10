package app.bonedle.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.toArgb
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.bonedle.MainActivity
import app.bonedle.R
import app.bonedle.data.Clock
import app.bonedle.data.db.GameDao
import app.bonedle.data.game.Progress
import app.bonedle.data.settings.SettingsRepository
import app.bonedle.di.ApplicationScope
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.ui.theme.DarkColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Arms the one daily reminder (spec 8.5) with an inexact alarm in a short window, which needs
 * no exact-alarm permission. Each alarm re-arms the next one.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clock: Clock,
) {
    private val alarms: AlarmManager get() = context.getSystemService(AlarmManager::class.java)

    fun schedule(minuteOfDay: Int) {
        val at = nextTrigger(clock.nowMillis(), ZoneId.systemDefault(), minuteOfDay)
        alarms.setWindow(AlarmManager.RTC_WAKEUP, at, WINDOW_MS, alarmIntent())
    }

    fun cancel() = alarms.cancel(alarmIntent())

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        const val ACTION_REMIND = "app.bonedle.action.DAILY_REMINDER"

        /** The reminder may arrive up to this long after its time, so the system can batch it. */
        const val WINDOW_MS = 10 * 60_000L

        /**
         * The next time the clock in [zone] reads [minuteOfDay] strictly after [nowMillis]. Across
         * a daylight-saving gap the reminder moves to the first valid minute after it.
         */
        fun nextTrigger(nowMillis: Long, zone: ZoneId, minuteOfDay: Int): Long {
            val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
            val time = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
            var next = ZonedDateTime.of(now.toLocalDate(), time, zone)
            if (!next.isAfter(now)) next = ZonedDateTime.of(now.toLocalDate().plusDays(1), time, zone)
            return next.toInstant().toEpochMilli()
        }
    }
}

/** Turns the reminder on and off, keeping the setting and the alarm in step. */
@Singleton
class ReminderController @Inject constructor(
    private val settings: SettingsRepository,
    private val scheduler: ReminderScheduler,
) {
    suspend fun enable(minuteOfDay: Int) {
        settings.setReminder(true, minuteOfDay)
        scheduler.schedule(minuteOfDay)
    }

    suspend fun disable() {
        val minutes = settings.settings.first().reminderMinutes
        settings.setReminder(false, minutes)
        scheduler.cancel()
    }

    suspend fun isEnabled(): Boolean = settings.settings.first().reminderEnabled

    /** Re-arms after boot, an app update, a clock change, or an alarm that just fired. */
    suspend fun rearm() {
        val s = settings.settings.first()
        if (s.reminderEnabled) scheduler.schedule(s.reminderMinutes) else scheduler.cancel()
    }
}

/** What a reminder says: today's number and, if the player has one, the streak at stake. */
data class ReminderContent(val dailyNumber: Int, val streak: Int)

/** Posts the reminder, unless today's daily is already solved. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: GameDao,
    private val clock: Clock,
) {
    /** The reminder for today, or null when today's daily is already solved. */
    suspend fun contentForToday(): ReminderContent? {
        val progress = Progress(dao.allResults(), emptyList(), dao.allFreezes())
        val today = clock.today()
        if (today.epochDay in progress.playedDays) return null
        return ReminderContent(DailySchedule.numberOn(today), progress.streak(today.epochDay).current)
    }

    suspend fun remindIfDue() {
        val content = contentForToday() ?: return
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val res = context.resources
        val text = if (content.streak > 0) {
            res.getQuantityString(R.plurals.reminder_body_streak, content.streak, content.streak)
        } else {
            res.getString(R.string.reminder_body)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(DarkColors.accent.toArgb())
            .setContentTitle(res.getString(R.string.reminder_title, content.dailyNumber))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "daily_reminder"
        private const val NOTIFICATION_ID = 1

        /** The reminder channel; created at startup so it shows in system settings early. */
        fun createChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.reminder_channel_description) }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        /** True when a notification would actually appear (permission granted, channel on). */
        fun canPost(context: Context): Boolean {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
            val channel = manager.getNotificationChannel(CHANNEL_ID) ?: return true
            return channel.importance != NotificationManager.IMPORTANCE_NONE
        }

        /** Android 13+ needs the runtime permission before the first notification. */
        fun needsPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
}

/** The reminder's alarm, plus the system events after which it must be armed again. */
class ReminderReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun controller(): ReminderController

        fun notifier(): ReminderNotifier

        @ApplicationScope
        fun scope(): CoroutineScope
    }

    override fun onReceive(context: Context, intent: Intent) {
        val deps = EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
        val result = goAsync()
        deps.scope().launch {
            try {
                if (intent.action == ReminderScheduler.ACTION_REMIND && deps.controller().isEnabled()) deps.notifier().remindIfDue()
                deps.controller().rearm()
            } finally {
                result.finish()
            }
        }
    }
}

package app.bonedle

import android.app.Application
import app.bonedle.analytics.FirebaseSetup
import app.bonedle.notifications.ReminderNotifier
import dagger.hilt.android.HiltAndroidApp

/** Hilt's dependency-graph root. */
@HiltAndroidApp
class BonedleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseSetup.ensureInitialized(this)
        ReminderNotifier.createChannel(this)
    }
}

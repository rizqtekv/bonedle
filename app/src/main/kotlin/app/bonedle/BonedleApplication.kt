package app.bonedle

import android.app.Application
import androidx.lifecycle.ProcessLifecycleOwner
import app.bonedle.ads.SessionTracker
import app.bonedle.analytics.FirebaseSetup
import app.bonedle.notifications.ReminderNotifier
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/** Hilt's dependency-graph root. */
@HiltAndroidApp
class BonedleApplication : Application() {
    @Inject lateinit var sessions: SessionTracker

    override fun onCreate() {
        super.onCreate()
        FirebaseSetup.ensureInitialized(this)
        ReminderNotifier.createChannel(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(sessions)
    }
}

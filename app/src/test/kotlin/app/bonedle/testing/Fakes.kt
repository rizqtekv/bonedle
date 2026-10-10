package app.bonedle.testing

import android.app.Activity
import app.bonedle.ads.AdPlacement
import app.bonedle.ads.Ads
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.AnalyticsEvent
import app.bonedle.data.Clock
import app.bonedle.engine.daily.CalendarDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Analytics that keeps every event, for assertions. */
class RecordingAnalytics : Analytics {
    val events = mutableListOf<AnalyticsEvent>()
    val userProperties = mutableMapOf<String, String>()

    override fun log(event: AnalyticsEvent) {
        events += event
    }

    override fun setUserProperty(name: String, value: String) {
        userProperties[name] = value
    }

    fun named(name: String): List<AnalyticsEvent> = events.filter { it.name == name }
}

/** A clock the test moves by hand. */
class TestClock(var today: CalendarDate = CalendarDate(2026, 11, 2), var now: Long = 1_800_000_000_000L) : Clock {
    var elapsed = 0L

    override fun nowMillis(): Long = now + elapsed

    override fun elapsedRealtimeMillis(): Long = elapsed

    override fun today(): CalendarDate = today
}

/** Every rewarded ad loaded and ready, for screenshots of ad offers. Showing one does nothing. */
object ReadyAds : Ads {
    override val readyRewarded: StateFlow<Set<AdPlacement>> = MutableStateFlow(AdPlacement.entries.toSet())

    override fun showRewarded(activity: Activity, placement: AdPlacement, onReward: () -> Unit, onClosed: () -> Unit) = onClosed()

    override fun continueAfterUnlimited(activity: Activity, onContinue: () -> Unit) = onContinue()
}


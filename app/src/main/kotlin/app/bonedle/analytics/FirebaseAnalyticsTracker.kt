package app.bonedle.analytics

import android.content.Context
import android.os.Bundle
import android.util.Log
import app.bonedle.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Starts Firebase from the keys baked into the build (`-Pbonedle.firebase.*`). A build without
 * keys has no Firebase at all: nothing is initialised and nothing is sent.
 */
object FirebaseSetup {
    val configured: Boolean
        get() = BuildConfig.FIREBASE_APP_ID.isNotEmpty() && BuildConfig.FIREBASE_API_KEY.isNotEmpty() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotEmpty()

    /** Initialises the default app once; true when Firebase is available. */
    @Synchronized
    fun ensureInitialized(context: Context): Boolean {
        if (!configured) return false
        if (FirebaseApp.getApps(context).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .apply { if (BuildConfig.FIREBASE_SENDER_ID.isNotEmpty()) setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID) }
                .build()
            FirebaseApp.initializeApp(context, options)
        }
        return true
    }
}

/** [Analytics] on Firebase Analytics; debug builds without keys print events to Logcat instead. */
@Singleton
class FirebaseAnalyticsTracker @Inject constructor(@ApplicationContext context: Context) : Analytics {
    private val firebase: FirebaseAnalytics? =
        if (FirebaseSetup.ensureInitialized(context)) FirebaseAnalytics.getInstance(context) else null

    override fun log(event: AnalyticsEvent) {
        val target = firebase
        if (target == null) {
            if (BuildConfig.DEBUG) Log.d(TAG, "${event.name} ${event.params}")
            return
        }
        val bundle = Bundle()
        for ((key, value) in event.params) {
            when (value) {
                is Long -> bundle.putLong(key, value)
                is Int -> bundle.putLong(key, value.toLong())
                else -> bundle.putString(key, value.toString())
            }
        }
        target.logEvent(event.name, bundle)
    }

    override fun setUserProperty(name: String, value: String) {
        firebase?.setUserProperty(name, value) ?: run { if (BuildConfig.DEBUG) Log.d(TAG, "user property $name=$value") }
    }

    private companion object {
        const val TAG = "BonedleAnalytics"
    }
}

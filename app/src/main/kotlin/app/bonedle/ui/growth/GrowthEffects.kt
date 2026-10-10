package app.bonedle.ui.growth

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import app.bonedle.notifications.ReminderNotifier
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.delay

/**
 * A function that asks for the Android 13+ notification permission and reports whether
 * notifications may be posted. Earlier versions need no prompt and report true at once. Only
 * ever called from a button the player pressed to turn the reminder on (spec 8.5).
 */
@Composable
fun rememberNotificationPermission(onResult: (granted: Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val callback by rememberUpdatedState(onResult)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { callback(it) }
    return remember(launcher, context) {
        {
            if (ReminderNotifier.needsPermission(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                callback(true)
            }
        }
    }
}

/** Opens Bonedle's notification settings, for a player who blocked notifications earlier. */
fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", context.packageName, null))
    }
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Lets the success emblem play before Play's rating card slides over the screen. */
private const val REVIEW_DELAY_MS = 1_200L

/**
 * Requests the Play In-App Review flow when [ask] turns true (spec 8.6); Play decides whether
 * the card actually appears. [onRequested] runs once the flow was launched, to start the
 * 60-day cooldown.
 */
@Composable
fun InAppReview(ask: Boolean, onRequested: () -> Unit) {
    val activity = LocalActivity.current ?: return
    val requested by rememberUpdatedState(onRequested)
    LaunchedEffect(ask) {
        if (!ask) return@LaunchedEffect
        delay(REVIEW_DELAY_MS)
        val manager = ReviewManagerFactory.create(activity)
        manager.requestReviewFlow().addOnCompleteListener { task ->
            if (task.isSuccessful && !activity.isFinishing) {
                manager.launchReviewFlow(activity, task.result)
                requested()
            }
        }
    }
}

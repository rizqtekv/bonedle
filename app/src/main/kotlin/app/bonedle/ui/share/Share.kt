package app.bonedle.ui.share

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.content.IntentCompat
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.analytics.ShareSurface
import app.bonedle.data.game.GameId
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

private const val EXTRA_SURFACE = "app.bonedle.extra.SHARE_SURFACE"

/**
 * Opens the Android Sharesheet with [text] and, when given, the card [image] (spec 8.1). Apps
 * that take both (WhatsApp, Messages) get the picture with the text as its caption; Instagram
 * Stories takes the picture. The Sharesheet reports the app picked to [ShareResultReceiver].
 */
fun shareResult(
    context: Context,
    analytics: Analytics,
    surface: ShareSurface,
    id: GameId?,
    text: String,
    chooserTitle: String,
    image: Uri? = null,
) {
    analytics.log(Events.shareOpen(surface, id))
    val send = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_TITLE, chooserTitle)
        if (image != null) {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, image)
            // The ClipData carries the read grant to the chosen app (and the Sharesheet preview).
            clipData = ClipData.newRawUri("", image)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    // Mutable so the system can add the chosen component to the callback.
    val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
    val callback = PendingIntent.getBroadcast(
        context,
        surface.ordinal,
        Intent(context, ShareResultReceiver::class.java).putExtra(EXTRA_SURFACE, surface.code),
        PendingIntent.FLAG_UPDATE_CURRENT or mutable,
    )
    val chooser = Intent.createChooser(send, chooserTitle, callback.intentSender)
    context.startActivity(chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Puts [text] on the clipboard ("Copy text", spec 8.1). */
fun copyText(context: Context, analytics: Analytics, id: GameId?, label: String, text: String) {
    analytics.log(Events.shareOpen(ShareSurface.COPY, id))
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(label, text))
}

/** Receives the app the player picked in the Sharesheet and logs `share_complete`. */
class ShareResultReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun analytics(): Analytics
    }

    override fun onReceive(context: Context, intent: Intent) {
        val chosen = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_CHOSEN_COMPONENT, ComponentName::class.java) ?: return
        val surface = intent.getStringExtra(EXTRA_SURFACE) ?: return
        EntryPointAccessors.fromApplication(context.applicationContext, Dependencies::class.java)
            .analytics()
            .log(Events.shareComplete(surface, chosen.packageName))
    }
}

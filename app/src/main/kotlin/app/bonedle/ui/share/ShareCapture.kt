package app.bonedle.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.bonedle.BuildConfig
import app.bonedle.engine.model.Puzzle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The exported share image, in pixels (spec 8.1). */
const val SHARE_IMAGE_WIDTH = 1080
const val SHARE_IMAGE_HEIGHT = 1350

/** The card is laid out at this size and drawn at 3 px per dp, giving exactly 1080 × 1350. */
private val CARD_WIDTH = 360.dp
private val CARD_HEIGHT = 450.dp

/** The share card's last off-screen recording, ready to save as an image. */
@Stable
class ShareCardCapture internal constructor(internal val layer: GraphicsLayer) {
    internal var recorded = false

    /**
     * Saves the card as a PNG in the app's share cache (replacing earlier ones) and returns a
     * content URI other apps can read, or null if nothing was drawn yet or saving failed.
     */
    suspend fun export(context: Context, name: String): Uri? {
        if (!recorded) return null
        val bitmap = runCatching { layer.toImageBitmap().asAndroidBitmap() }.getOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                // Hardware bitmaps cannot be encoded directly.
                val software = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
                    bitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    bitmap
                }
                val dir = File(context.cacheDir, SHARE_DIR).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                val file = File(dir, "$name.png")
                file.outputStream().use { software.compress(Bitmap.CompressFormat.PNG, 100, it) }
                FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.share", file)
            }.getOrNull()
        }
    }

    private companion object {
        /** Matches res/xml/share_paths.xml. */
        const val SHARE_DIR = "share"
    }
}

@Composable
fun rememberShareCardCapture(): ShareCardCapture {
    val layer = rememberGraphicsLayer()
    return remember(layer) { ShareCardCapture(layer) }
}

/**
 * Draws the share card at 1080 × 1350 into [capture] without showing it: the card is recorded
 * into a graphics layer that is never drawn on screen. Hidden from accessibility services too
 * (the visible preview already describes it).
 */
@Composable
fun ShareCardOffscreen(capture: ShareCardCapture, puzzle: Puzzle, content: ShareCardContent, modifier: Modifier = Modifier) {
    val exportDensity = Density(density = SHARE_IMAGE_WIDTH / CARD_WIDTH.value, fontScale = 1f)
    Box(
        modifier
            .size(1.dp)
            .clearAndSetSemantics { }
            .drawWithContent {
                capture.layer.record(size = IntSize(SHARE_IMAGE_WIDTH, SHARE_IMAGE_HEIGHT)) { this@drawWithContent.drawContent() }
                capture.recorded = true
            },
    ) {
        CompositionLocalProvider(LocalDensity provides exportDensity) {
            ShareCard(
                puzzle = puzzle,
                content = content,
                modifier = Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(CARD_WIDTH, CARD_HEIGHT),
                rounded = false,
            )
        }
    }
}

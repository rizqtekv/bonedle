package app.bonedle.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asAndroidBitmap

private const val NOISE_SIZE = 96

/** Monochrome grain tile, generated once; drawn at 2 % over glass to prevent gradient banding. */
private val noiseTile: ImageBitmap by lazy {
    val bitmap = ImageBitmap(NOISE_SIZE, NOISE_SIZE)
    val pixels = IntArray(NOISE_SIZE * NOISE_SIZE)
    var state = 0x2545F491L
    for (i in pixels.indices) {
        // xorshift: deterministic grain, identical on every run and in screenshots.
        state = state xor (state shl 13)
        state = state xor (state ushr 7)
        state = state xor (state shl 17)
        val v = ((state ushr 24) and 0xFF).toInt()
        pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
    }
    bitmap.asAndroidBitmap().setPixels(pixels, 0, NOISE_SIZE, 0, 0, NOISE_SIZE, NOISE_SIZE)
    bitmap
}

/** A brush that tiles the grain texture. */
@Composable
fun rememberNoiseBrush(): ShaderBrush = remember {
    ShaderBrush(ImageShader(noiseTile, TileMode.Repeated, TileMode.Repeated))
}

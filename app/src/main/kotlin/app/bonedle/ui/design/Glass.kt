package app.bonedle.ui.design

import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bonedle.ui.theme.BonedleTheme

/**
 * The content glass chrome floats over. Mark the scrolling/main content with
 * [glassBackdropSource]; every [GlassSurface] inside the same [ProvideGlassBackdrop] then blurs
 * whatever part of it lies underneath.
 *
 * The recorded layer holds references to the content's render nodes, so the blur follows
 * scrolling and animation without re-recording anything.
 */
@Stable
class GlassBackdrop internal constructor(internal val layer: GraphicsLayer) {
    internal var origin by mutableStateOf(Offset.Zero)
}

/** Creates a [GlassBackdrop] tied to this composition. */
@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/** The backdrop glass chrome blurs; null means glass uses its opaque fallback. */
val LocalGlassBackdrop = staticCompositionLocalOf<GlassBackdrop?> { null }

/** Makes [backdrop] available to the glass surfaces in [content]. */
@Composable
fun ProvideGlassBackdrop(backdrop: GlassBackdrop, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalGlassBackdrop provides backdrop, content = content)
}

/**
 * Records this content into [backdrop] (and draws it as usual). Glass must not be inside it.
 *
 * The recording starts with an opaque [background]: glass draws the blurred copy over the live
 * content, so any transparent pixel in the copy would let the sharp original show through.
 */
fun Modifier.glassBackdropSource(backdrop: GlassBackdrop, background: Color): Modifier = this
    .onGloballyPositioned { backdrop.origin = it.positionInRoot() }
    .drawWithContent {
        backdrop.layer.record {
            drawRect(background)
            this@drawWithContent.drawContent()
        }
        drawLayer(backdrop.layer)
    }

/** Glass recipe (spec 4.4). */
object GlassSpec {
    val blurRadius: Dp = 24.dp
    const val TINT_ALPHA = 0.55f

    /** Tint for glass that carries reading text (sheets, dialogs, the hint card). */
    const val DENSE_TINT_ALPHA = 0.86f
    const val FALLBACK_ALPHA = 0.92f
    const val HAIRLINE_ALPHA = 0.10f
    const val HIGHLIGHT_ALPHA = 0.06f
    const val NOISE_ALPHA = 0.02f
    val shadowBlur: Dp = 24.dp
    const val SHADOW_ALPHA = 0.35f

    /** Real backdrop blur needs `RenderEffect` (API 31+). */
    val blurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
}

/**
 * Frosted glass for chrome only: bars, the in-game toolbar, sheets, dialogs and the share-card
 * frame. Never use it for the board, dominoes, rule chips or text-heavy surfaces.
 *
 * API 31+: surface.1 at 55 % ([tintAlpha]; denser for surfaces that carry reading text) over a
 * 24dp blur of the [backdrop]. Below 31 (or without a
 * backdrop): surface.1 at 92 %, no blur. Both add a 1dp hairline, a 1dp top highlight, 2 % noise
 * and a soft ambient shadow.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    shadow: Boolean = true,
    tintAlpha: Float = GlassSpec.TINT_ALPHA,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = BonedleTheme.colors
    val blurSource = backdrop?.takeIf { GlassSpec.blurSupported }
    val blurLayer = rememberGraphicsLayer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    val noise = rememberNoiseBrush()
    // A white hairline disappears on paper; light glass uses a faint ink line instead.
    val hairline = if (colors.isDark) Color.White.copy(alpha = GlassSpec.HAIRLINE_ALPHA) else colors.textPrimary.copy(alpha = 0.08f)
    val highlight = Color.White.copy(alpha = if (colors.isDark) GlassSpec.HIGHLIGHT_ALPHA else 0.6f)
    Box(
        modifier = modifier
            .then(if (shadow) Modifier.ambientShadow(shape) else Modifier)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .clip(shape)
            .drawBehind {
                if (blurSource != null) {
                    val offset = origin - blurSource.origin
                    val radius = GlassSpec.blurRadius.toPx()
                    blurLayer.renderEffect = BlurEffect(radius, radius, TileMode.Clamp)
                    blurLayer.record { translate(-offset.x, -offset.y) { drawLayer(blurSource.layer) } }
                    drawLayer(blurLayer)
                    drawRect(colors.surface1.copy(alpha = tintAlpha))
                } else {
                    drawRect(colors.surface1.copy(alpha = maxOf(tintAlpha, GlassSpec.FALLBACK_ALPHA)))
                }
                drawRect(noise, alpha = GlassSpec.NOISE_ALPHA)
                val line = 1.dp.toPx()
                drawRect(highlight, size = size.copy(height = line))
            }
            .border(1.dp, hairline, shape),
        content = content,
    )
}

/**
 * Soft shadow with no vertical offset (spec: ambient, 24dp blur, black at 35 %), drawn only
 * outside [shape] so translucent glass stays translucent. Platform shadow layers on arbitrary
 * paths need API 28; earlier devices get an elevation shadow.
 */
fun Modifier.ambientShadow(shape: Shape, blur: Dp = GlassSpec.shadowBlur, alpha: Float = GlassSpec.SHADOW_ALPHA): Modifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        drawBehind { drawAmbientShadow(shape, blur, alpha) }
    } else {
        shadow(elevation = 8.dp, shape = shape, clip = false)
    }

private fun DrawScope.drawAmbientShadow(shape: Shape, blur: Dp, alpha: Float) {
    val outline = shape.createOutline(size, layoutDirection, this)
    val path = Path().apply { addOutline(outline) }
    val shadowColor = Color.Black.copy(alpha = alpha).toArgb()
    clipPath(path, clipOp = ClipOp.Difference) {
        drawIntoCanvas { canvas ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.BLACK
                setShadowLayer(blur.toPx() / 2f, 0f, 0f, shadowColor)
            }
            when (outline) {
                is Outline.Rectangle -> canvas.nativeCanvas.drawRect(
                    outline.rect.left, outline.rect.top, outline.rect.right, outline.rect.bottom, paint,
                )
                else -> canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
            }
        }
    }
}


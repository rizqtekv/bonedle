package app.bonedle.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bonedle.ui.theme.BonedleColors
import app.bonedle.ui.theme.BonedleTheme

/**
 * The six region textures (spec 4.1): every palette slot has its own pattern so regions stay
 * distinguishable without colour. Slot order matches the region colours.
 */
enum class RegionTexture { Dots, Diagonal, CrossHatch, Horizontal, Zigzag, Grid;

    companion object {
        /** Texture for palette slot [slot]. */
        fun forSlot(slot: Int): RegionTexture = entries[slot % entries.size]
    }
}

/**
 * Draws [texture] over the whole draw area in [ink] (alpha is applied by the caller's colour).
 * Clip to the region shape before calling. Pattern phase is anchored to the canvas origin, so
 * neighbouring cells of one region line up seamlessly.
 */
fun DrawScope.drawRegionTexture(texture: RegionTexture, ink: Color, scale: Float = 1f) {
    clipRect { drawTexture(texture, ink, scale) }
}

private fun DrawScope.drawTexture(texture: RegionTexture, ink: Color, scale: Float) {
    val unit = 6.dp.toPx() * scale
    val stroke = 1.dp.toPx() * scale
    val w = size.width
    val h = size.height
    when (texture) {
        RegionTexture.Dots -> {
            var y = unit / 2
            while (y < h + unit) {
                var x = unit / 2
                while (x < w + unit) {
                    drawCircle(ink, radius = stroke * 1.1f, center = Offset(x, y))
                    x += unit
                }
                y += unit
            }
        }
        RegionTexture.Diagonal -> diagonals(ink, unit * 1.2f, stroke, rising = true)
        RegionTexture.CrossHatch -> {
            diagonals(ink, unit * 1.5f, stroke, rising = true)
            diagonals(ink, unit * 1.5f, stroke, rising = false)
        }
        RegionTexture.Horizontal -> {
            var y = unit / 2
            while (y < h) {
                drawLine(ink, Offset(0f, y), Offset(w, y), stroke)
                y += unit
            }
        }
        RegionTexture.Zigzag -> {
            val rise = unit * 0.5f
            var y = unit
            while (y < h + unit) {
                val path = Path().apply {
                    moveTo(0f, y)
                    var x = 0f
                    var up = true
                    while (x < w) {
                        x += unit / 2
                        lineTo(x, if (up) y - rise else y)
                        up = !up
                    }
                }
                drawPath(path, ink, style = Stroke(stroke))
                y += unit * 1.2f
            }
        }
        RegionTexture.Grid -> {
            val step = unit * 1.2f
            var x = step / 2
            while (x < w) {
                drawLine(ink, Offset(x, 0f), Offset(x, h), stroke)
                x += step
            }
            var y = step / 2
            while (y < h) {
                drawLine(ink, Offset(0f, y), Offset(w, y), stroke)
                y += step
            }
        }
    }
}

private fun DrawScope.diagonals(ink: Color, step: Float, stroke: Float, rising: Boolean) {
    val w = size.width
    val h = size.height
    var start = -h
    while (start < w) {
        if (rising) {
            drawLine(ink, Offset(start, h), Offset(start + h, 0f), stroke)
        } else {
            drawLine(ink, Offset(start, 0f), Offset(start + h, h), stroke)
        }
        start += step
    }
}

/** A region sample: 24 % fill, texture and 3dp outline, as it appears on the board. */
@Composable
fun RegionSwatch(slot: Int, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    val colors = BonedleTheme.colors
    Canvas(modifier.size(size)) {
        val radius = CornerRadius(8.dp.toPx())
        drawRoundRect(colors.cellFree, cornerRadius = radius)
        drawRoundRect(colors.regionFill(slot), cornerRadius = radius)
        val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, this@Canvas.size.width, this@Canvas.size.height, radius)) }
        clipPath(shape) {
            drawRegionTexture(RegionTexture.forSlot(slot), colors.textPrimary.copy(alpha = BonedleColors.TEXTURE_ALPHA))
        }
        val outline = 3.dp.toPx()
        drawRoundRect(
            colors.region(slot),
            topLeft = Offset(outline / 2, outline / 2),
            size = androidx.compose.ui.geometry.Size(this.size.width - outline, this.size.height - outline),
            cornerRadius = radius,
            style = Stroke(outline),
        )
    }
}

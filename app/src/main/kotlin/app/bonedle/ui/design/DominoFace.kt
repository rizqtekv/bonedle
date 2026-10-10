package app.bonedle.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.bonedle.R
import app.bonedle.ui.theme.BonedleColors
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Radii

/** How a domino lies: halves side by side, or stacked. */
enum class DominoOrientation { Horizontal, Vertical }

/** Share of the cell height taken by the bottom bevel. */
private const val BEVEL_FRACTION = 0.07f
private const val PIP_RADIUS_FRACTION = 0.085f
private const val DIVIDER_FRACTION = 0.03f
private const val DIVIDER_INSET_FRACTION = 0.16f

/** Pip centres in a unit half (x, y in 0..1), for values 0–6. */
private val PIP_LAYOUT: Array<FloatArray> = arrayOf(
    floatArrayOf(),
    floatArrayOf(0.5f, 0.5f),
    floatArrayOf(0.27f, 0.27f, 0.73f, 0.73f),
    floatArrayOf(0.27f, 0.27f, 0.5f, 0.5f, 0.73f, 0.73f),
    floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.27f, 0.73f, 0.73f, 0.73f),
    floatArrayOf(0.27f, 0.27f, 0.73f, 0.27f, 0.5f, 0.5f, 0.27f, 0.73f, 0.73f, 0.73f),
    floatArrayOf(0.27f, 0.25f, 0.27f, 0.5f, 0.27f, 0.75f, 0.73f, 0.25f, 0.73f, 0.5f, 0.73f, 0.75f),
)

/**
 * Draws an ivory domino face at [topLeft] covering two cells of [cell] px: bevel, face, centre
 * line and spots. [first] is the left (horizontal) or top (vertical) half.
 */
fun DrawScope.drawDomino(
    topLeft: Offset,
    cell: Float,
    orientation: DominoOrientation,
    first: Int,
    second: Int,
    colors: BonedleColors,
) {
    val horizontal = orientation == DominoOrientation.Horizontal
    val w = if (horizontal) cell * 2 else cell
    val h = if (horizontal) cell else cell * 2
    val bevel = cell * BEVEL_FRACTION
    val radius = CornerRadius(cell * Radii.DOMINO_FRACTION)
    drawRoundRect(colors.tileEdge, topLeft = topLeft, size = Size(w, h), cornerRadius = radius)
    drawRoundRect(colors.tile, topLeft = topLeft, size = Size(w, h - bevel), cornerRadius = radius)
    val faceH = h - bevel
    val inset = cell * DIVIDER_INSET_FRACTION
    val stroke = cell * DIVIDER_FRACTION
    if (horizontal) {
        drawLine(colors.tileDivider, Offset(topLeft.x + cell, topLeft.y + inset), Offset(topLeft.x + cell, topLeft.y + faceH - inset), stroke)
    } else {
        val mid = topLeft.y + faceH / 2
        drawLine(colors.tileDivider, Offset(topLeft.x + inset, mid), Offset(topLeft.x + w - inset, mid), stroke)
    }
    val halfH = if (horizontal) faceH else faceH / 2
    drawPips(first, Offset(topLeft.x, topLeft.y), cell, halfH, colors)
    val secondOrigin = if (horizontal) Offset(topLeft.x + cell, topLeft.y) else Offset(topLeft.x, topLeft.y + halfH)
    drawPips(second, secondOrigin, cell, halfH, colors)
}

private fun DrawScope.drawPips(value: Int, origin: Offset, w: Float, h: Float, colors: BonedleColors) {
    val layout = PIP_LAYOUT[value]
    val r = minOf(w, h) * PIP_RADIUS_FRACTION
    var i = 0
    while (i < layout.size) {
        drawCircle(colors.pip, radius = r, center = Offset(origin.x + layout[i] * w, origin.y + layout[i + 1] * h))
        i += 2
    }
}

/** A standalone domino (tray, tutorial, share card), announced as "Domino 6 and 2". */
@Composable
fun DominoFace(
    first: Int,
    second: Int,
    modifier: Modifier = Modifier,
    cellSize: Dp = 40.dp,
    orientation: DominoOrientation = DominoOrientation.Horizontal,
) {
    val colors = BonedleTheme.colors
    val description = stringResource(R.string.domino_description, first, second)
    val width = if (orientation == DominoOrientation.Horizontal) cellSize * 2 else cellSize
    val height = if (orientation == DominoOrientation.Horizontal) cellSize else cellSize * 2
    Canvas(
        modifier
            .size(width, height)
            .semantics { contentDescription = description },
    ) {
        drawDomino(Offset.Zero, cellSize.toPx(), orientation, first, second, colors)
    }
}

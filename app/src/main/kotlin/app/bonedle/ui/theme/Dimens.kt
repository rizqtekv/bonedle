package app.bonedle.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/** Spacing on the 4dp grid (spec 4.3). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp

    /** Horizontal screen padding. */
    val screen = 20.dp

    /** Padding inside cards. */
    val card = 16.dp

    /** Gap between sections (spec: 14–20dp). */
    val section = 16.dp
}

/** Corner radii (spec 4.3). */
object Radii {
    val button = 14.dp
    val card = 18.dp
    val sheet = 28.dp
    val board = 18.dp

    /** Domino corner radius as a fraction of one cell. */
    const val DOMINO_FRACTION = 0.16f
}

/** Shapes built from [Radii]. */
object Shapes {
    val chip = CircleShape
    val button = RoundedCornerShape(Radii.button)
    val card = RoundedCornerShape(Radii.card)
    val sheet = RoundedCornerShape(topStart = Radii.sheet, topEnd = Radii.sheet)
    val board = RoundedCornerShape(Radii.board)
}

/** Sizes with accessibility minimums (spec 4.3, 4.7). */
object Sizes {
    /** Minimum touch target. */
    val touchTarget = 48.dp
    val primaryButtonHeight = 52.dp
    val icon = 24.dp
    val iconSmall = 18.dp

    /** Stroke width of the custom icon set. */
    const val ICON_STROKE = 1.9f

    /** Region outline drawn on edges facing another region or the board edge. */
    val regionOutline = 3.dp

    /** Outline of a region whose rule is broken (dashed). */
    val regionOutlineWarning = 2.dp
    val hairline = 1.dp
}

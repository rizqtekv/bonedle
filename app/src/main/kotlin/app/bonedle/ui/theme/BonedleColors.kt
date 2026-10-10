package app.bonedle.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

/**
 * Colour tokens (spec 4.1). Names mirror the spec: `bgBase` is `bg.base`, and so on.
 *
 * Every text/background pair meets WCAG AA (≥ 4.5:1); `ColorContrastTest` verifies this for both
 * themes. [accent] is a fill colour; text drawn in accent uses [accentText], which also passes on
 * light backgrounds.
 *
 * @property regions the six region colours, indexed by a region's palette slot.
 * @property onRegion text and icons drawn on a solid region colour (rule chips).
 */
@Immutable
data class BonedleColors(
    val isDark: Boolean,
    val bgBase: Color,
    val bgDeep: Color,
    val surface1: Color,
    val surface2: Color,
    val boardBase: Color,
    val cellFree: Color,
    val cellBorder: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentText: Color,
    val success: Color,
    val tile: Color,
    val tileEdge: Color,
    val tileDivider: Color,
    val pip: Color,
    val regions: List<Color>,
    val onRegion: Color,
    val scrim: Color,
) {
    init {
        require(regions.size == REGION_COUNT) { "expected $REGION_COUNT region colours" }
    }

    /** Region colour for palette slot [slot]. */
    fun region(slot: Int): Color = regions[slot]

    /** Region fill: the region colour at 24 % over the cell. */
    fun regionFill(slot: Int): Color = regions[slot].copy(alpha = REGION_FILL_ALPHA)

    companion object {
        /** Region palette slots; matches the engine's `Region.REGION_COLORS`. */
        const val REGION_COUNT = 6

        const val REGION_FILL_ALPHA = 0.24f

        /** Internal cell grid lines: white at 7 %. */
        val CellGridLine = Color.White.copy(alpha = 0.07f)

        /** Texture ink for region patterns, applied at 8 %. */
        const val TEXTURE_ALPHA = 0.08f
    }
}

/** The primary theme. Values are exactly those of spec 4.1. */
val DarkColors = BonedleColors(
    isDark = true,
    bgBase = Color(0xFF101826),
    bgDeep = Color(0xFF0B111C),
    surface1 = Color(0xFF182234),
    surface2 = Color(0xFF1F2B40),
    boardBase = Color(0xFF0E1522),
    cellFree = Color(0xFF1B2638),
    cellBorder = Color(0xFF2A3850),
    textPrimary = Color(0xFFF4EFE6),
    textSecondary = Color(0xFFA9B3C4),
    accent = Color(0xFFF4B942),
    onAccent = Color(0xFF1A1405),
    accentText = Color(0xFFF4B942),
    success = Color(0xFF8FDCA8),
    tile = Color(0xFFF3EBDD),
    tileEdge = Color(0xFFC9BCA4),
    tileDivider = Color(0xFFD6CAB3),
    pip = Color(0xFF1B2230),
    regions = listOf(
        Color(0xFF35C2B0), // teal
        Color(0xFFF08A4B), // orange
        Color(0xFF9B7BF2), // violet
        Color(0xFFF2668B), // pink
        Color(0xFF4FA8F5), // sky
        Color(0xFFA6D84B), // lime
    ),
    onRegion = Color(0xFF101826),
    scrim = Color(0xB30B111C),
)

/**
 * Light theme. Spec 4.1 fixes bg.base, surface.1, text.primary, accent and on.accent; the rest are
 * derived to keep the same roles at AA contrast on warm paper. Region colours are deepened so
 * white chip text and the 3dp outlines stay legible on light cells.
 */
val LightColors = BonedleColors(
    isDark = false,
    bgBase = Color(0xFFF6F2EA),
    bgDeep = Color(0xFFECE6DA),
    surface1 = Color(0xFFFFFFFF),
    surface2 = Color(0xFFEFE9DE),
    boardBase = Color(0xFFDCD3C3),
    cellFree = Color(0xFFEDE7DC),
    cellBorder = Color(0xFFCFC4B1),
    textPrimary = Color(0xFF141413),
    textSecondary = Color(0xFF56606F),
    accent = Color(0xFFF4B942),
    onAccent = Color(0xFF1A1405),
    accentText = Color(0xFF8A5800),
    success = Color(0xFF1F6B43),
    tile = Color(0xFFFFFCF5),
    tileEdge = Color(0xFFC4B69C),
    tileDivider = Color(0xFFD9CDB7),
    pip = Color(0xFF1B2230),
    regions = listOf(
        Color(0xFF137A6E),
        Color(0xFFB44A15),
        Color(0xFF6247CC),
        Color(0xFFB42E57),
        Color(0xFF1F67AF),
        Color(0xFF4F7610),
    ),
    onRegion = Color(0xFFFFFFFF),
    scrim = Color(0x99141413),
)

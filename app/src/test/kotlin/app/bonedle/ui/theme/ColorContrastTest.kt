package app.bonedle.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import app.bonedle.ui.design.GlassSpec
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/**
 * Spec 4.7 release blocker: every text/background pair the design system uses meets WCAG AA
 * (4.5:1), and non-text essentials (region outlines, spots) meet 3:1, in both themes.
 */
class ColorContrastTest {
    private fun channel(c: Float): Double = if (c <= 0.04045f) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)

    private fun luminance(color: Color): Double =
        0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)

    private fun contrast(a: Color, b: Color): Double {
        val la = luminance(a)
        val lb = luminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private val failures = ArrayList<String>()

    private fun check(theme: String, what: String, fg: Color, bg: Color, minimum: Double) {
        val ratio = contrast(fg, bg)
        if (ratio < minimum) failures += "%s: %s is %.2f:1 (needs %.1f)".format(theme, what, ratio, minimum)
    }

    @Test
    fun allTokenPairsMeetWcag() {
        for ((theme, c) in listOf("dark" to DarkColors, "light" to LightColors)) {
            val glass = c.surface1.copy(alpha = GlassSpec.TINT_ALPHA).compositeOver(c.bgBase)
            val glassFallback = c.surface1.copy(alpha = GlassSpec.FALLBACK_ALPHA).compositeOver(c.bgBase)
            val surfaces = mapOf(
                "bg.base" to c.bgBase, "bg.deep" to c.bgDeep, "surface.1" to c.surface1, "surface.2" to c.surface2,
                "cell.free" to c.cellFree, "glass" to glass, "glass fallback" to glassFallback,
            )
            for ((name, bg) in surfaces) {
                check(theme, "text.primary on $name", c.textPrimary, bg, AA)
                check(theme, "text.secondary on $name", c.textSecondary, bg, AA)
                check(theme, "accent text on $name", c.accentText, bg, AA)
                check(theme, "success on $name", c.success, bg, AA)
            }
            check(theme, "on.accent on accent", c.onAccent, c.accent, AA)
            check(theme, "accent on on.accent (selected segment on hero card)", c.accent, c.onAccent, AA)
            check(theme, "on.accent on hero track", c.onAccent, c.onAccent.copy(alpha = 0.12f).compositeOver(c.accent), AA)
            check(theme, "success pill", c.success, c.success.copy(alpha = 0.18f).compositeOver(c.surface1), AA)
            for (slot in 0 until BonedleColors.REGION_COUNT) {
                val region = c.region(slot)
                val fill = c.regionFill(slot).compositeOver(c.cellFree)
                check(theme, "chip text on region $slot", c.onRegion, region, AA)
                check(theme, "text on region $slot fill", c.textPrimary, fill, AA)
                check(theme, "region $slot outline vs its fill", region, fill, NON_TEXT)
            }
            check(theme, "spots on tile", c.pip, c.tile, NON_TEXT)
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    private companion object {
        const val AA = 4.5
        const val NON_TEXT = 3.0
    }
}

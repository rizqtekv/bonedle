package app.bonedle.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import app.bonedle.R

/** Type scale (spec 4.2). Sizes are in sp, so every style follows the system font scale. */
@Immutable
data class BonedleTypography(
    val display: TextStyle,
    val title: TextStyle,
    val heading: TextStyle,
    val body: TextStyle,
    val bodyStrong: TextStyle,
    val label: TextStyle,
    val caption: TextStyle,
    /** Timer and other changing numbers: tabular figures so digits do not jitter. */
    val timer: TextStyle,
)

private fun variable(res: Int, weight: Int) = Font(
    resId = res,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Sora, bundled as a variable font (display styles). */
val Sora = FontFamily(
    variable(R.font.sora_variable, 400),
    variable(R.font.sora_variable, 600),
    variable(R.font.sora_variable, 700),
)

/** DM Sans, bundled as a variable font (body styles). */
val DmSans = FontFamily(
    variable(R.font.dm_sans_variable, 400),
    variable(R.font.dm_sans_variable, 500),
    variable(R.font.dm_sans_variable, 700),
)

private val TightLines = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(family: FontFamily, size: Int, line: Int, weight: Int, tracking: TextUnit, features: String? = null) =
    TextStyle(
        fontFamily = family,
        fontSize = size.sp,
        lineHeight = line.sp,
        fontWeight = FontWeight(weight),
        letterSpacing = tracking,
        fontFeatureSettings = features,
        lineHeightStyle = TightLines,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )

/** The single type scale used by both themes. */
val Typography = BonedleTypography(
    display = style(Sora, 32, 38, 700, (-0.5).sp),
    title = style(Sora, 26, 32, 700, (-0.4).sp),
    heading = style(Sora, 20, 26, 700, (-0.2).sp),
    body = style(DmSans, 16, 22, 400, 0.sp),
    bodyStrong = style(DmSans, 16, 22, 700, 0.sp),
    label = style(DmSans, 14, 18, 700, 0.sp),
    caption = style(DmSans, 13, 18, 400, 0.sp),
    timer = style(DmSans, 14, 18, 700, 0.sp, features = "tnum"),
)

/**
 * This style with its growth under the system font scale limited to [maxScale]. Only for short
 * labels in fixed-width chrome (bar labels, badges), where unlimited growth would truncate them;
 * body text always scales fully.
 */
@Composable
@ReadOnlyComposable
fun TextStyle.withScaleCap(maxScale: Float): TextStyle {
    val scale = LocalDensity.current.fontScale
    if (scale <= maxScale) return this
    val factor = maxScale / scale
    return copy(fontSize = fontSize * factor, lineHeight = lineHeight * factor)
}

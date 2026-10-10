package app.bonedle.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/** Theme preference from Settings; [SYSTEM] follows the device. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

val LocalBonedleColors = staticCompositionLocalOf { DarkColors }
val LocalBonedleTypography = staticCompositionLocalOf { Typography }

/**
 * Root theme. Provides Bonedle tokens through [BonedleTheme] accessors and maps them onto a
 * Material 3 scheme so stock components (ripples, text fields, sheets) inherit the palette.
 *
 * @param reducedMotion overrides detection of the system "remove animations" setting.
 */
@Composable
fun BonedleTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    reducedMotion: Boolean? = null,
    content: @Composable () -> Unit,
) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val reduced = reducedMotion ?: remember(context) { animationsDisabled(context) }
    val motion = remember(reduced) { BonedleMotion(reduced) }
    CompositionLocalProvider(
        LocalBonedleColors provides colors,
        LocalBonedleTypography provides Typography,
        LocalBonedleMotion provides motion,
    ) {
        MaterialTheme(colorScheme = colors.toMaterial(), content = content)
    }
}

/** Token accessors, e.g. `BonedleTheme.colors.accent`. */
object BonedleTheme {
    val colors: BonedleColors
        @Composable @ReadOnlyComposable
        get() = LocalBonedleColors.current

    val typography: BonedleTypography
        @Composable @ReadOnlyComposable
        get() = LocalBonedleTypography.current

    val motion: BonedleMotion
        @Composable @ReadOnlyComposable
        get() = LocalBonedleMotion.current
}

private fun animationsDisabled(context: android.content.Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

private fun BonedleColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        secondary = surface2,
        onSecondary = textPrimary,
        background = bgBase,
        onBackground = textPrimary,
        surface = surface1,
        onSurface = textPrimary,
        surfaceVariant = surface2,
        onSurfaceVariant = textSecondary,
        surfaceContainer = surface1,
        surfaceContainerHigh = surface2,
        outline = cellBorder,
        outlineVariant = cellBorder,
        scrim = scrim,
    )
}

package app.bonedle.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import app.bonedle.ui.theme.Spacing

/** From this font scale, side-by-side tiles and buttons stack instead (spec 4.7, 200 % text). */
const val STACK_FONT_SCALE = 1.5f

/**
 * Equal-width items side by side, stacked full-width at large font scales so labels and
 * numbers never break mid-word. Each item applies the modifier it is given.
 */
@Composable
fun AdaptiveRow(modifier: Modifier = Modifier, content: @Composable (itemModifier: Modifier) -> Unit) {
    if (LocalDensity.current.fontScale >= STACK_FONT_SCALE) {
        Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) { content(Modifier.fillMaxWidth()) }
    } else {
        Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) { content(Modifier.weight(1f)) }
    }
}

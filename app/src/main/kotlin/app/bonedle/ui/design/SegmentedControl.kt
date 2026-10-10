package app.bonedle.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bonedle.ui.theme.BonedleMotion
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing

/** Where a [SegmentedControl] sits, which decides its colours. */
enum class SegmentedStyle { OnSurface, OnAccent }

private val TrackShape = RoundedCornerShape(28.dp)
private val SegmentShape = RoundedCornerShape(24.dp)

/**
 * Single-choice segmented control (difficulty picker). Each segment is a 48dp-tall radio item,
 * announced by TalkBack as selected / not selected.
 *
 * Labels never break mid-word: when they cannot all fit on one line (large font scales, long
 * translations) the segments reflow into rows of two, or one per row if even two do not fit.
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    style: SegmentedStyle = SegmentedStyle.OnSurface,
) {
    val colors = BonedleTheme.colors
    val labelStyle = BonedleTheme.typography.label
    val track = when (style) {
        SegmentedStyle.OnSurface -> colors.surface2
        SegmentedStyle.OnAccent -> colors.onAccent.copy(alpha = 0.12f)
    }
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(modifier) {
        // Segments share a row equally, so the widest label decides what fits.
        val trackPadding = with(density) { (Spacing.xs * 2).roundToPx() }
        val padding = with(density) { (Spacing.sm * 2).roundToPx() }
        val widest = options.maxOf { measurer.measure(it, labelStyle, maxLines = 1).size.width } + padding
        fun fits(count: Int) = widest * count + trackPadding <= constraints.maxWidth
        val perRow = when {
            fits(options.size) -> options.size
            fits(2) -> 2
            else -> 1
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TrackShape)
                .background(track)
                .padding(Spacing.xs)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            options.indices.chunked(perRow).forEach { row ->
                Row(Modifier.fillMaxWidth()) {
                    row.forEach { index ->
                        val selected = index == selectedIndex
                        val (fill, text) = when (style) {
                            SegmentedStyle.OnSurface -> if (selected) colors.accent to colors.onAccent else Color.Transparent to colors.textSecondary
                            SegmentedStyle.OnAccent -> if (selected) colors.onAccent to colors.accent else Color.Transparent to colors.onAccent
                        }
                        val animatedFill by animateColorAsState(fill, BonedleTheme.motion.enter(BonedleMotion.MICRO), label = "segment")
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = Sizes.touchTarget)
                                .clip(SegmentShape)
                                .background(animatedFill)
                                .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) })
                                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = options[index], style = labelStyle, color = text, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

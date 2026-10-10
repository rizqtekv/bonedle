package app.bonedle.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Shapes
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing

/** Card fill: neutral (surface.1) or the accent hero. */
enum class CardStyle { Neutral, Accent }

/**
 * Card (spec 4.3): 18dp radius, 16dp padding. Opaque by design: cards carry text, so they are
 * never glass. With [onClick] the whole card is one button.
 */
@Composable
fun BonedleCard(
    modifier: Modifier = Modifier,
    style: CardStyle = CardStyle.Neutral,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BonedleTheme.colors
    val (container, onContainer) = when (style) {
        CardStyle.Neutral -> colors.surface1 to colors.textPrimary
        CardStyle.Accent -> colors.accent to colors.onAccent
    }
    CompositionLocalProvider(LocalContentColor provides onContainer) {
        Column(
            modifier = modifier
                .clip(Shapes.card)
                .background(container)
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick)
                    } else {
                        Modifier
                    },
                )
                .padding(Spacing.card),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            content = content,
        )
    }
}

/** Visual role of a [Pill]. */
enum class PillStyle { Neutral, Accent, Success }

/** Rounded status pill, e.g. the streak counter. Not interactive. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null, style: PillStyle = PillStyle.Neutral) {
    val colors = BonedleTheme.colors
    val (container, content) = when (style) {
        PillStyle.Neutral -> colors.surface2 to colors.textPrimary
        PillStyle.Accent -> colors.accent to colors.onAccent
        PillStyle.Success -> colors.success.copy(alpha = 0.18f) to colors.success
    }
    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(Shapes.chip)
            .background(container)
            .padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Sizes.iconSmall))
            Spacer(Modifier.width(Spacing.xs))
        }
        Text(text, style = BonedleTheme.typography.label, color = content)
    }
}

/** Section heading used above groups of cards. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = BonedleTheme.colors.textSecondary) {
    Text(
        text = text,
        style = BonedleTheme.typography.label,
        color = color,
        modifier = modifier.padding(vertical = Spacing.xs),
    )
}

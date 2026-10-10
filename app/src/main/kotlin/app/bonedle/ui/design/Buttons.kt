package app.bonedle.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.bonedle.ui.theme.BonedleMotion
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Shapes
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing
import app.bonedle.ui.theme.withScaleCap

/** Visual role of a [BonedleButton]. */
enum class ButtonStyle {
    /** Accent fill: the one main action on a screen. */
    Primary,

    /** Raised neutral fill. */
    Secondary,

    /** Dark fill for use on accent surfaces (the Unlimited hero card). */
    OnAccent,

    /** No fill; accent-coloured text. */
    Text,
}

private const val PRESSED_SCALE = 0.98f
private const val DISABLED_ALPHA = 0.4f

/** A badge overlaps its icon; it may not outgrow it. */
private const val BADGE_MAX_SCALE = 1.3f

/**
 * Button with a 52dp minimum height (spec 4.3) that grows with the font scale instead of
 * clipping. Pressing scales it slightly; disabled buttons fade and ignore taps.
 */
@Composable
fun BonedleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ButtonStyle = ButtonStyle.Primary,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val colors = BonedleTheme.colors
    val (container, content) = when (style) {
        ButtonStyle.Primary -> colors.accent to colors.onAccent
        ButtonStyle.Secondary -> colors.surface2 to colors.textPrimary
        ButtonStyle.OnAccent -> colors.onAccent to colors.accent
        ButtonStyle.Text -> Color.Transparent to colors.accentText
    }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) PRESSED_SCALE else 1f,
        animationSpec = BonedleTheme.motion.enter(BonedleMotion.MICRO),
        label = "press",
    )
    Row(
        modifier = modifier
            .heightIn(min = if (style == ButtonStyle.Text) Sizes.touchTarget else Sizes.primaryButtonHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else DISABLED_ALPHA
            }
            .clip(Shapes.button)
            .background(container)
            .clickable(
                interactionSource = interaction,
                indication = ripple(color = content),
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = Spacing.xl, vertical = Spacing.md),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.sm))
        }
        Text(
            text = text,
            style = BonedleTheme.typography.bodyStrong,
            color = content,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Icon-only button with a 48dp touch target. [contentDescription] is required: it is the only
 * label TalkBack has.
 */
@Composable
fun BonedleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = BonedleTheme.colors.textPrimary,
    enabled: Boolean = true,
    badge: String? = null,
) {
    Box(
        modifier = modifier
            .sizeIn(minWidth = Sizes.touchTarget, minHeight = Sizes.touchTarget)
            .clip(Shapes.chip)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = null,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 24.dp, color = tint),
                onClick = onClick,
            )
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(Sizes.icon))
        if (badge != null) CountBadge(badge, Modifier.align(Alignment.TopEnd).padding(top = 6.dp, end = 4.dp))
    }
}

/** Small accent count badge (free hints left, for example). */
@Composable
fun CountBadge(text: String, modifier: Modifier = Modifier) {
    val colors = BonedleTheme.colors
    Box(
        modifier = modifier
            .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
            .clip(Shapes.chip)
            .background(colors.accent)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = BonedleTheme.typography.caption
                .copy(fontWeight = BonedleTheme.typography.label.fontWeight)
                .withScaleCap(BADGE_MAX_SCALE),
            color = colors.onAccent,
        )
    }
}

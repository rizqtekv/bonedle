package app.bonedle.ui.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.bonedle.ui.theme.BonedleMotion
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Shapes
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing
import app.bonedle.ui.theme.withScaleCap

/** Bar labels sit in fixed-width cells; past 1.5x they would truncate. */
private const val BAR_LABEL_MAX_SCALE = 1.5f

/** One destination or action in a glass bar. */
@Immutable
data class BarItem(
    val icon: ImageVector,
    val label: String,
    val badge: String? = null,
    val enabled: Boolean = true,
)

/**
 * Bottom navigation on glass (Home, Stats, Archive). Floats over content; pads itself for the
 * system navigation bar.
 */
@Composable
fun GlassNavigationBar(
    items: List<BarItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier.fillMaxWidth(), shape = RectangleShape) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                .selectableGroup(),
        ) {
            items.forEachIndexed { index, item ->
                val selected = index == selectedIndex
                val tint by animateColorAsState(
                    if (selected) BonedleTheme.colors.accentText else BonedleTheme.colors.textSecondary,
                    BonedleTheme.motion.enter(BonedleMotion.MICRO),
                    label = "navTint",
                )
                BarCell(
                    item = item,
                    tint = tint,
                    modifier = Modifier
                        .weight(1f)
                        .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) }),
                )
            }
        }
    }
}

/**
 * The in-game toolbar on glass (Undo, Rotate, Hint, Restart). A rounded pill floating above the
 * tray; items can carry a badge (free hints left) and be disabled.
 */
@Composable
fun GlassToolbar(
    items: List<BarItem>,
    onClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(modifier = modifier, shape = Shapes.card) {
        Row(
            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEachIndexed { index, item ->
                BarCell(
                    item = item,
                    tint = BonedleTheme.colors.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .clip(Shapes.button)
                        .clickable(enabled = item.enabled, role = Role.Button, onClick = { onClick(index) })
                        .graphicsLayer { alpha = if (item.enabled) 1f else 0.4f },
                )
            }
        }
    }
}

@Composable
private fun BarCell(item: BarItem, tint: androidx.compose.ui.graphics.Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 56.dp)
            .padding(vertical = Spacing.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            Icon(item.icon, contentDescription = null, tint = tint, modifier = Modifier.size(Sizes.icon))
            if (item.badge != null) {
                CountBadge(item.badge, Modifier.align(Alignment.TopEnd).padding(start = 18.dp).offsetBadge())
            }
        }
        Text(
            text = item.label,
            style = BonedleTheme.typography.caption.withScaleCap(BAR_LABEL_MAX_SCALE),
            color = tint,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun Modifier.offsetBadge(): Modifier = graphicsLayer {
    translationX = 10.dp.toPx()
    translationY = -6.dp.toPx()
}

/**
 * Top app bar that turns to glass once content scrolls beneath it ([scrolled]); flat over the
 * background otherwise. Pads itself for the status bar.
 */
@Composable
fun GlassTopBar(
    scrolled: Boolean,
    modifier: Modifier = Modifier,
    navigation: @Composable (BoxScope.() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable BoxScope.() -> Unit,
) {
    val glassAlpha by animateFloatAsState(if (scrolled) 1f else 0f, BonedleTheme.motion.enter(BonedleMotion.MICRO), label = "topBar")
    Box(modifier.fillMaxWidth()) {
        if (glassAlpha > 0f) {
            GlassSurface(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = glassAlpha },
                shadow = false,
                content = {},
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .heightIn(min = 56.dp)
                .padding(horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(Sizes.touchTarget), contentAlignment = Alignment.Center) { navigation?.invoke(this) }
            Box(Modifier.weight(1f).padding(horizontal = Spacing.xs), contentAlignment = Alignment.CenterStart, content = title)
            Row(verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

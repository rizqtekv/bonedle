package app.bonedle.gallery

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.bonedle.R
import app.bonedle.engine.model.Rule
import app.bonedle.ui.design.BarItem
import app.bonedle.ui.design.BonedleButton
import app.bonedle.ui.design.BonedleCard
import app.bonedle.ui.design.BonedleIconButton
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.ButtonStyle
import app.bonedle.ui.design.CardStyle
import app.bonedle.ui.design.DominoFace
import app.bonedle.ui.design.DominoOrientation
import app.bonedle.ui.design.GlassNavigationBar
import app.bonedle.ui.design.GlassSurface
import app.bonedle.ui.design.GlassToolbar
import app.bonedle.ui.design.Pill
import app.bonedle.ui.design.PillStyle
import app.bonedle.ui.design.ProvideGlassBackdrop
import app.bonedle.ui.design.RegionSwatch
import app.bonedle.ui.design.RuleChip
import app.bonedle.ui.design.RuleChipState
import app.bonedle.ui.design.SectionHeader
import app.bonedle.ui.design.SegmentedControl
import app.bonedle.ui.design.SegmentedStyle
import app.bonedle.ui.design.drawDomino
import app.bonedle.ui.design.glassBackdropSource
import app.bonedle.ui.design.regionColorName
import app.bonedle.ui.design.rememberGlassBackdrop
import app.bonedle.ui.theme.BonedleColors
import app.bonedle.ui.theme.BonedleMotion
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Shapes
import app.bonedle.ui.theme.Spacing
import app.bonedle.ui.theme.ThemeMode
import kotlinx.coroutines.launch

/** Every gallery section, in order; screenshot tests render them one by one. */
val GallerySections: List<Pair<String, @Composable () -> Unit>> = listOf(
    "colors" to { ColorsSection() },
    "regions" to { RegionsSection() },
    "type" to { TypeSection() },
    "buttons" to { ButtonsSection() },
    "chips" to { ChipsSection() },
    "controls" to { ControlsSection() },
    "cards" to { CardsSection() },
    "glass" to { GlassSection() },
    "dominoes" to { DominoesSection() },
    "icons" to { IconsSection() },
    "motion" to { MotionSection() },
)

/** The whole gallery, scrollable, with a theme switch on top. */
@Composable
fun DesignGallery(mode: ThemeMode, onModeChange: (ThemeMode) -> Unit) {
    val colors = BonedleTheme.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bgBase)
            .verticalScroll(rememberScrollState())
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl),
    ) {
        Text(
            stringResource(R.string.gallery_title),
            style = BonedleTheme.typography.display,
            color = colors.textPrimary,
            modifier = Modifier.padding(horizontal = Spacing.screen),
        )
        SegmentedControl(
            options = listOf(
                stringResource(R.string.gallery_theme_system),
                stringResource(R.string.gallery_theme_light),
                stringResource(R.string.gallery_theme_dark),
            ),
            selectedIndex = mode.ordinal,
            onSelect = { onModeChange(ThemeMode.entries[it]) },
            modifier = Modifier.padding(horizontal = Spacing.screen).fillMaxWidth(),
        )
        GallerySections.forEach { (_, section) -> section() }
    }
}

/** Frame used by every section: themed background, screen padding, heading. */
@Composable
fun GalleryFrame(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(BonedleTheme.colors.bgBase)
            .padding(horizontal = Spacing.screen, vertical = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Text(title, style = BonedleTheme.typography.heading, color = BonedleTheme.colors.textPrimary)
        content()
    }
}

private fun hex(color: Color): String = "#%06X".format(color.toArgb() and 0xFFFFFF)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ColorsSection() {
    val c = BonedleTheme.colors
    val tokens = listOf(
        "bg.base" to c.bgBase, "bg.deep" to c.bgDeep, "surface.1" to c.surface1, "surface.2" to c.surface2,
        "board.base" to c.boardBase, "cell.free" to c.cellFree, "text.primary" to c.textPrimary,
        "text.secondary" to c.textSecondary, "accent" to c.accent, "on.accent" to c.onAccent,
        "accent.text" to c.accentText, "success" to c.success, "tile" to c.tile, "tile.edge" to c.tileEdge,
        "tile.divider" to c.tileDivider, "pip" to c.pip,
    )
    GalleryFrame(stringResource(R.string.gallery_section_colors)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            tokens.forEach { (name, color) -> Swatch(name, color) }
        }
    }
}

@Composable
private fun Swatch(name: String, color: Color) {
    val c = BonedleTheme.colors
    Column(
        Modifier
            .width(104.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(c.surface1),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .background(color),
        )
        Column(Modifier.padding(Spacing.sm)) {
            Text(name, style = BonedleTheme.typography.label, color = c.textPrimary)
            Text(hex(color), style = BonedleTheme.typography.caption, color = c.textSecondary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RegionsSection() {
    val c = BonedleTheme.colors
    GalleryFrame(stringResource(R.string.gallery_section_regions)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            repeat(BonedleColors.REGION_COUNT) { slot ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RegionSwatch(slot, size = 64.dp)
                    Spacer(Modifier.height(Spacing.xs))
                    Text(regionColorName(slot), style = BonedleTheme.typography.caption, color = c.textPrimary)
                    Text(hex(c.region(slot)), style = BonedleTheme.typography.caption, color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
fun TypeSection() {
    val t = BonedleTheme.typography
    val c = BonedleTheme.colors
    GalleryFrame(stringResource(R.string.gallery_section_type)) {
        Text(stringResource(R.string.gallery_sample_display), style = t.display, color = c.textPrimary)
        Text(stringResource(R.string.gallery_sample_title), style = t.title, color = c.textPrimary)
        Text(stringResource(R.string.gallery_sample_heading), style = t.heading, color = c.textPrimary)
        Text(stringResource(R.string.gallery_sample_body), style = t.body, color = c.textPrimary)
        Text(stringResource(R.string.gallery_sample_body), style = t.bodyStrong, color = c.textPrimary)
        Text(stringResource(R.string.gallery_how_to_play), style = t.label, color = c.textSecondary)
        Text(stringResource(R.string.gallery_sample_caption), style = t.caption, color = c.textSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BonedleIcons.Timer, null, tint = c.textSecondary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.xs))
            Text(stringResource(R.string.gallery_sample_timer), style = t.timer, color = c.textSecondary)
        }
    }
}

@Composable
fun ButtonsSection() {
    GalleryFrame(stringResource(R.string.gallery_section_buttons)) {
        BonedleButton(stringResource(R.string.gallery_share), onClick = {}, icon = BonedleIcons.Share, modifier = Modifier.fillMaxWidth())
        BonedleButton(stringResource(R.string.gallery_next), onClick = {}, style = ButtonStyle.Secondary, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BonedleButton(stringResource(R.string.gallery_how_to_play), onClick = {}, style = ButtonStyle.Text)
            BonedleButton(stringResource(R.string.gallery_disabled), onClick = {}, enabled = false)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipsSection() {
    val rules = listOf(Rule.EQUAL, Rule.ALL_DIFFERENT, Rule.sumEq(12), Rule.sumLt(5), Rule.sumGt(7), Rule.sumEq(3))
    GalleryFrame(stringResource(R.string.gallery_section_chips)) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Pill(stringResource(R.string.gallery_streak), icon = BonedleIcons.Flame, style = PillStyle.Accent)
            Pill(stringResource(R.string.gallery_solved), icon = BonedleIcons.Check, style = PillStyle.Success)
            Pill(stringResource(R.string.gallery_new))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rules.forEachIndexed { slot, rule -> RuleChip(rule, slot) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            rules.forEachIndexed { slot, rule ->
                RuleChip(rule, slot, state = if (slot % 2 == 0) RuleChipState.Satisfied else RuleChipState.Broken)
            }
        }
    }
}

@Composable
fun ControlsSection() {
    var selected by remember { mutableIntStateOf(1) }
    val levels = listOf(
        stringResource(R.string.gallery_easy),
        stringResource(R.string.gallery_medium),
        stringResource(R.string.gallery_hard),
        stringResource(R.string.gallery_expert),
    )
    GalleryFrame(stringResource(R.string.gallery_section_controls)) {
        SegmentedControl(levels, selected, { selected = it }, Modifier.fillMaxWidth())
        Row {
            BonedleIconButton(BonedleIcons.Back, stringResource(R.string.action_back), onClick = {})
            BonedleIconButton(BonedleIcons.Settings, stringResource(R.string.gallery_settings), onClick = {})
            BonedleIconButton(BonedleIcons.Hint, stringResource(R.string.gallery_hint), onClick = {}, badge = "2")
            BonedleIconButton(BonedleIcons.Undo, stringResource(R.string.gallery_undo), onClick = {}, enabled = false)
        }
    }
}

@Composable
fun CardsSection() {
    var selected by remember { mutableIntStateOf(0) }
    val c = BonedleTheme.colors
    GalleryFrame(stringResource(R.string.gallery_section_cards)) {
        BonedleCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.gallery_sample_title), style = BonedleTheme.typography.heading, color = c.textPrimary)
            listOf(R.string.gallery_easy, R.string.gallery_medium, R.string.gallery_hard).forEachIndexed { i, label ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(label), style = BonedleTheme.typography.bodyStrong, color = c.textPrimary, modifier = Modifier.weight(1f))
                    if (i == 0) {
                        Pill(stringResource(R.string.gallery_sample_timer), icon = BonedleIcons.Check, style = PillStyle.Success)
                    } else {
                        Pill(stringResource(R.string.gallery_play), icon = BonedleIcons.Play)
                    }
                }
            }
        }
        BonedleCard(Modifier.fillMaxWidth(), style = CardStyle.Accent) {
            Text(stringResource(R.string.gallery_sample_heading), style = BonedleTheme.typography.title, color = c.onAccent)
            SegmentedControl(
                options = listOf(
                    stringResource(R.string.gallery_easy),
                    stringResource(R.string.gallery_medium),
                    stringResource(R.string.gallery_hard),
                    stringResource(R.string.gallery_expert),
                ),
                selectedIndex = selected,
                onSelect = { selected = it },
                style = SegmentedStyle.OnAccent,
                modifier = Modifier.fillMaxWidth(),
            )
            BonedleButton(
                stringResource(R.string.gallery_play_unlimited),
                onClick = {},
                style = ButtonStyle.OnAccent,
                icon = BonedleIcons.Play,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Busy content under glass so the blur (or its fallback) is visible. */
@Composable
private fun GlassBackdropContent(modifier: Modifier = Modifier) {
    val c = BonedleTheme.colors
    Canvas(modifier) {
        val cell = 44.dp.toPx()
        drawRect(c.boardBase)
        var row = 0
        var y = 0f
        while (y < size.height) {
            var x = -(row % 2) * cell
            var col = 0
            while (x < size.width) {
                drawRect(c.regionFill((row + col) % 6), Offset(x, y), androidx.compose.ui.geometry.Size(cell * 2, cell))
                drawDomino(Offset(x + 4f, y + 4f), cell - 8f, DominoOrientation.Horizontal, (row + col) % 7, (row * 3 + col) % 7, c)
                x += cell * 2
                col++
            }
            y += cell
            row++
        }
    }
}

@Composable
fun GlassSection() {
    val backdrop = rememberGlassBackdrop()
    GalleryFrame(stringResource(R.string.gallery_section_glass)) {
        Text(stringResource(R.string.gallery_glass_note), style = BonedleTheme.typography.caption, color = BonedleTheme.colors.textSecondary)
        Box(
            Modifier
                .fillMaxWidth()
                .height(300.dp)
                .clip(Shapes.card),
        ) {
            GlassBackdropContent(
                Modifier
                    .fillMaxSize()
                    .glassBackdropSource(backdrop, BonedleTheme.colors.bgBase),
            )
            ProvideGlassBackdrop(backdrop) {
                GlassToolbar(
                    items = listOf(
                        BarItem(BonedleIcons.Undo, stringResource(R.string.gallery_undo)),
                        BarItem(BonedleIcons.Rotate, stringResource(R.string.gallery_rotate)),
                        BarItem(BonedleIcons.Hint, stringResource(R.string.gallery_hint), badge = "2"),
                        BarItem(BonedleIcons.Restart, stringResource(R.string.gallery_restart), enabled = false),
                    ),
                    onClick = {},
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = Spacing.lg)
                        .fillMaxWidth(),
                )
                GlassNavigationBar(
                    items = listOf(
                        BarItem(BonedleIcons.Home, stringResource(R.string.gallery_home)),
                        BarItem(BonedleIcons.Stats, stringResource(R.string.gallery_stats)),
                        BarItem(BonedleIcons.Archive, stringResource(R.string.gallery_archive)),
                    ),
                    selectedIndex = 0,
                    onSelect = {},
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
                GlassSurface(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(Spacing.lg)
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = Shapes.button,
                ) {
                    Text(
                        stringResource(R.string.gallery_sample_display),
                        style = BonedleTheme.typography.heading,
                        color = BonedleTheme.colors.textPrimary,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DominoesSection() {
    GalleryFrame(stringResource(R.string.gallery_section_dominoes)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            for (v in 0..6) DominoFace(v, 6 - v, cellSize = 36.dp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            DominoFace(6, 6, orientation = DominoOrientation.Vertical, cellSize = 40.dp)
            DominoFace(5, 4, orientation = DominoOrientation.Vertical, cellSize = 40.dp)
            DominoFace(3, 0, orientation = DominoOrientation.Vertical, cellSize = 40.dp)
            DominoFace(2, 1, orientation = DominoOrientation.Vertical, cellSize = 56.dp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun IconsSection() {
    val c = BonedleTheme.colors
    GalleryFrame(stringResource(R.string.gallery_section_icons)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            BonedleIcons.all.forEach { icon ->
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface1),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = icon.name, tint = c.textPrimary, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

@Composable
fun MotionSection() {
    val motion = BonedleTheme.motion
    val scope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    val pulse = remember { Animatable(1f) }
    val c = BonedleTheme.colors
    GalleryFrame(stringResource(R.string.gallery_section_motion)) {
        Text(
            stringResource(
                R.string.gallery_reduced_motion,
                stringResource(if (motion.reduced) R.string.gallery_on else R.string.gallery_off),
            ),
            style = BonedleTheme.typography.caption,
            color = c.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxl), verticalAlignment = Alignment.CenterVertically) {
            DominoFace(
                6,
                2,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = scale.value * pulse.value
                        scaleY = scale.value * pulse.value
                    }
                    .offset(x = shake.value.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BonedleButton(stringResource(R.string.gallery_pick_up), style = ButtonStyle.Secondary, onClick = {
                scope.launch {
                    scale.animateTo(BonedleMotion.PICK_UP_SCALE, motion.pickUp())
                    scale.animateTo(1f, motion.snapIntoPlace())
                }
            })
            BonedleButton(stringResource(R.string.gallery_shake), style = ButtonStyle.Secondary, onClick = {
                scope.launch {
                    shake.snapTo(0f)
                    shake.animateTo(0f, motion.shake())
                }
            })
            BonedleButton(stringResource(R.string.gallery_pulse), style = ButtonStyle.Secondary, onClick = {
                scope.launch {
                    pulse.snapTo(1f)
                    pulse.animateTo(1f, motion.pulse())
                }
            })
        }
    }
}

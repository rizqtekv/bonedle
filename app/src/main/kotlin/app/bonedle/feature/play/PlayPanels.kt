package app.bonedle.feature.play

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.bonedle.R
import app.bonedle.engine.game.Hint
import app.bonedle.engine.grader.Deduction
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.RuleType
import app.bonedle.engine.model.Technique
import app.bonedle.engine.tutorial.TutorialBoards
import app.bonedle.ui.design.AdaptiveRow
import app.bonedle.ui.design.BonedleButton
import app.bonedle.ui.design.BonedleIconButton
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.ButtonStyle
import app.bonedle.ui.design.GlassSpec
import app.bonedle.ui.design.GlassSurface
import app.bonedle.ui.design.RuleChip
import app.bonedle.ui.design.regionColorName
import app.bonedle.ui.design.toChipState
import app.bonedle.ui.theme.BonedleMotion
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Shapes
import app.bonedle.ui.theme.Spacing

/** Cells a hint highlights at levels 1–2. */
fun hintHighlight(hint: HintUi?, puzzle: Puzzle): Set<Int> = when (val h = hint?.hint) {
    is Hint.WrongDomino -> setOf(h.placement.cellA, h.placement.cellB)
    is Hint.Step -> {
        val d = h.deduction
        if (d == null) {
            setOf(h.reveal.placement.cellA, h.reveal.placement.cellB)
        } else {
            val region = if (d.region >= 0) puzzle.regions[d.region].cells else emptyList()
            (d.cells + region).toSet()
        }
    }
    else -> emptySet()
}

/** Level-2 wording for a deduction (spec 6: "name the deduction"). */
@Composable
@ReadOnlyComposable
fun deductionText(d: Deduction, puzzle: Puzzle): String {
    val main = when (d.technique) {
        Technique.SINGLE_CELL ->
            if (d.determined.isEmpty() && d.pairsFixed.isNotEmpty()) {
                stringResource(R.string.deduction_single_pair)
            } else {
                stringResource(R.string.deduction_single_cell)
            }
        Technique.REGION_SUM -> stringResource(R.string.deduction_region_sum)
        Technique.EQUAL_PROPAGATION -> {
            val values = d.determined.map { it.value }.distinct()
            if (values.size == 1) stringResource(R.string.deduction_equal, values[0]) else stringResource(R.string.deduction_equal_generic)
        }
        Technique.ALL_DIFFERENT -> stringResource(R.string.deduction_all_different)
        Technique.DOMINO_ADJACENCY -> stringResource(R.string.deduction_adjacency)
        Technique.TRAY_COUNTING -> stringResource(R.string.deduction_tray)
        Technique.TWO_REGION -> stringResource(R.string.deduction_two_region)
        Technique.LOOKAHEAD -> stringResource(R.string.deduction_lookahead)
    }
    val first = d.determined.firstOrNull() ?: return main
    if (d.technique == Technique.EQUAL_PROPAGATION) return main
    val then = stringResource(R.string.deduction_then, puzzle.board.row(first.cell) + 1, puzzle.board.col(first.cell) + 1, first.value)
    return "$main $then"
}

/** A glass card floating above the toolbar with the current hint. */
@Composable
fun HintPanel(state: PlayUiState, onIntent: (PlayIntent) -> Unit, modifier: Modifier = Modifier) {
    val game = state.game ?: return
    val hint = state.hint
    val visible = hint != null || state.outOfHints
    val motion = BonedleTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(motion.enter()) + slideInVertically(motion.enter()) { it / 3 },
        exit = fadeOut(motion.exit()) + slideOutVertically(motion.exit()) { it / 3 },
    ) {
        val title = stringResource(R.string.hint_title)
        GlassSurface(Modifier.fillMaxWidth().semantics { paneTitle = title }, shape = Shapes.card, tintAlpha = GlassSpec.DENSE_TINT_ALPHA) {
            Column(Modifier.padding(Spacing.card), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = BonedleTheme.typography.heading, color = BonedleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                    BonedleIconButton(BonedleIcons.Close, stringResource(R.string.action_close), onClick = { onIntent(PlayIntent.DismissHint) })
                }
                val text = when {
                    state.outOfHints && hint == null -> stringResource(R.string.hint_none_left)
                    hint?.hint is Hint.WrongDomino -> stringResource(R.string.hint_wrong)
                    hint?.hint is Hint.Step -> {
                        val d = hint.hint.deduction
                        when {
                            d == null -> stringResource(R.string.hint_fallback)
                            hint.level >= 2 -> deductionText(d, game.puzzle)
                            else -> stringResource(R.string.hint_look)
                        }
                    }
                    else -> ""
                }
                Text(
                    text,
                    style = BonedleTheme.typography.body,
                    color = BonedleTheme.colors.textPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (hint != null) {
                    AdaptiveRow { item ->
                        val step = hint.hint as? Hint.Step
                        if (step?.deduction != null && hint.level < 2) {
                            BonedleButton(stringResource(R.string.hint_explain), onClick = { onIntent(PlayIntent.Hint) }, style = ButtonStyle.Secondary, modifier = item)
                        }
                        val reveal = if (hint.hint is Hint.WrongDomino) R.string.hint_take_back else R.string.hint_show
                        BonedleButton(stringResource(reveal), onClick = { onIntent(PlayIntent.RevealHint) }, modifier = item)
                    }
                }
            }
        }
    }
}

/** Plain-language description of [rule] for the legend. */
@Composable
@ReadOnlyComposable
fun legendText(rule: Rule): String = when (rule.type) {
    RuleType.EQUAL -> stringResource(R.string.legend_rule_equal)
    RuleType.ALL_DIFFERENT -> stringResource(R.string.legend_rule_all_different)
    RuleType.SUM_EQ -> stringResource(R.string.legend_rule_sum_eq, rule.target)
    RuleType.SUM_LT -> stringResource(R.string.legend_rule_sum_lt, rule.target)
    RuleType.SUM_GT -> stringResource(R.string.legend_rule_sum_gt, rule.target)
}

/** A scrim plus a glass sheet rising from the bottom. Tapping the scrim dismisses. */
@Composable
fun GlassSheet(visible: Boolean, title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val motion = BonedleTheme.motion
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(motion.enter()), exit = fadeOut(motion.exit())) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(BonedleTheme.colors.scrim)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
            )
        }
        AnimatedVisibility(
            visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(motion.enter(BonedleMotion.SCREEN)) { it },
            exit = slideOutVertically(motion.exit()) { it },
        ) {
            GlassSurface(Modifier.fillMaxWidth().semantics { paneTitle = title }, shape = Shapes.sheet, tintAlpha = GlassSpec.DENSE_TINT_ALPHA) {
                Column(
                    Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(title, style = BonedleTheme.typography.title, color = BonedleTheme.colors.textPrimary, modifier = Modifier.weight(1f))
                        BonedleIconButton(BonedleIcons.Close, stringResource(R.string.action_close), onClick = onDismiss)
                    }
                    content()
                }
            }
        }
    }
}

/** Every region's rule, in plain words (opened from any rule chip). */
@Composable
fun RuleLegendSheet(state: PlayUiState, onIntent: (PlayIntent) -> Unit) {
    val game = state.game ?: return
    val puzzle = game.puzzle
    GlassSheet(state.legendOpen, stringResource(R.string.legend_title), onDismiss = { onIntent(PlayIntent.CloseLegend) }) {
        Text(stringResource(R.string.legend_goal), style = BonedleTheme.typography.body, color = BonedleTheme.colors.textSecondary)
        Column(
            Modifier
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            val order = state.legendFocus?.let { f -> listOf(f) + puzzle.regions.indices.filter { it != f } } ?: puzzle.regions.indices.toList()
            for (r in order) {
                val region = puzzle.regions[r]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RuleChip(region.rule, region.color, state = game.regionStatuses[r].toChipState())
                    Spacer(Modifier.width(Spacing.md))
                    Column(Modifier.weight(1f)) {
                        Text(regionColorName(region.color).replaceFirstChar { it.uppercase() }, style = BonedleTheme.typography.label, color = BonedleTheme.colors.textPrimary)
                        Text(legendText(region.rule), style = BonedleTheme.typography.body, color = BonedleTheme.colors.textSecondary)
                    }
                }
            }
        }
        Text(stringResource(R.string.legend_free), style = BonedleTheme.typography.caption, color = BonedleTheme.colors.textSecondary)
    }
}

/** Glass confirmation before wiping the board. */
@Composable
fun RestartDialog(visible: Boolean, onIntent: (PlayIntent) -> Unit) {
    GlassSheet(visible, stringResource(R.string.restart_title), onDismiss = { onIntent(PlayIntent.CancelRestart) }) {
        Text(stringResource(R.string.restart_body), style = BonedleTheme.typography.body, color = BonedleTheme.colors.textSecondary)
        AdaptiveRow { item ->
            BonedleButton(stringResource(R.string.action_cancel), onClick = { onIntent(PlayIntent.CancelRestart) }, style = ButtonStyle.Secondary, modifier = item)
            BonedleButton(stringResource(R.string.restart_confirm), onClick = { onIntent(PlayIntent.ConfirmRestart) }, icon = BonedleIcons.Restart, modifier = item)
        }
    }
}

/** After each tutorial step: a word of praise and the way on (the last step opens today's board). */
@Composable
fun BoxScope.TutorialStepPanel(state: PlayUiState, visible: Boolean, onNext: () -> Unit) {
    val step = state.tutorialStep ?: return
    val last = step == TutorialBoards.STEPS - 1
    val motion = BonedleTheme.motion
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter),
        enter = slideInVertically(motion.enter(BonedleMotion.SCREEN)) { it },
        exit = slideOutVertically(motion.exit()) { it },
    ) {
        val title = stringResource(if (last) R.string.tutorial_all_done else R.string.tutorial_step_done)
        GlassSurface(Modifier.fillMaxWidth().semantics { paneTitle = title }, shape = Shapes.sheet, tintAlpha = GlassSpec.DENSE_TINT_ALPHA) {
            Column(
                Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.lg),
            ) {
                Text(title, style = BonedleTheme.typography.title, color = BonedleTheme.colors.success)
                BonedleButton(
                    stringResource(if (last) R.string.tutorial_finish else R.string.tutorial_next),
                    onClick = onNext,
                    icon = if (last) BonedleIcons.Play else BonedleIcons.ChevronRight,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

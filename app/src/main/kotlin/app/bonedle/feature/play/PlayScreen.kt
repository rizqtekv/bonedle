package app.bonedle.feature.play

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bonedle.R
import app.bonedle.ads.AdPlacement
import app.bonedle.ads.AdPolicy
import app.bonedle.ads.LocalAds
import app.bonedle.data.game.GameId
import app.bonedle.engine.tutorial.TutorialBoards
import app.bonedle.ui.design.BarItem
import app.bonedle.ui.design.BonedleButton
import app.bonedle.ui.design.BonedleIconButton
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.ButtonStyle
import app.bonedle.ui.design.DominoOrientation
import app.bonedle.ui.design.GlassToolbar
import app.bonedle.ui.design.GlassTopBar
import app.bonedle.ui.design.Pill
import app.bonedle.ui.design.ProvideGlassBackdrop
import app.bonedle.ui.design.drawDomino
import app.bonedle.ui.design.glassBackdropSource
import app.bonedle.ui.design.rememberGlassBackdrop
import app.bonedle.ui.design.rememberHaptics
import app.bonedle.ui.format.difficultyLabel
import app.bonedle.ui.format.formatDuration
import app.bonedle.ui.sound.LocalSoundEffects
import app.bonedle.ui.sound.Sound
import app.bonedle.ui.sound.SoundEffects
import app.bonedle.ui.theme.BonedleMotion
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Radii
import app.bonedle.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** Largest cell on the board; boards smaller than the space available stay this size. */
private val MaxCell = 64.dp
private val BoardPadding = 8.dp

/** Height reserved under the tray for the floating glass toolbar. */
private val ToolbarSpace = 88.dp

/** Play screen wired to its ViewModel and lifecycle. */
@Composable
fun PlayDestination(
    onBack: () -> Unit,
    onSolved: (GameId) -> Unit,
    onTutorialNext: (step: Int) -> Unit,
    onSkipTutorial: () -> Unit,
    onPlus: () -> Unit,
    viewModel: PlayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { viewModel.onPause() }
    }
    PlayScreen(
        state = state,
        effects = viewModel.effects,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onSolved = { state.id?.let(onSolved) },
        onTutorialNext = { state.tutorialStep?.let(onTutorialNext) },
        onSkipTutorial = onSkipTutorial,
        onPlus = onPlus,
    )
}

/**
 * Stateless Play screen: top bar, the board scaled to the largest size that fits, the tray, the
 * glass toolbar, and the hint, legend and restart overlays. When the board is solved the accent
 * sweep plays and [onSolved] hands over to the Solved screen; tutorial steps show their own
 * panel instead.
 */
@Composable
fun PlayScreen(
    state: PlayUiState,
    onIntent: (PlayIntent) -> Unit,
    onBack: () -> Unit,
    onSolved: () -> Unit,
    effects: Flow<PlayEffect> = emptyFlow(),
    onTutorialNext: () -> Unit = {},
    onSkipTutorial: () -> Unit = {},
    onPlus: (() -> Unit)? = null,
    sounds: SoundEffects? = LocalSoundEffects.current,
    motion: BoardMotion = remember { BoardMotion() },
    drag: DragState = remember { DragState() },
) {
    val colors = BonedleTheme.colors
    val motionSpec = BonedleTheme.motion
    val haptics = rememberHaptics(state.haptics)
    val soundOn by rememberUpdatedState(state.sound)
    val solvedNow by rememberUpdatedState(onSolved)
    val isTutorial = state.tutorialStep != null
    var showTutorialDone by remember { mutableStateOf(state.tutorialDone) }
    fun play(sound: Sound) {
        if (soundOn) sounds?.play(sound)
    }

    LaunchedEffect(effects) {
        effects.collect { effect ->
            when (effect) {
                is PlayEffect.Rejected -> {
                    haptics.error()
                    play(Sound.Error)
                    motion.shakeTray = effect.trayIndex
                    launch {
                        motion.shake.snapTo(0f)
                        motion.shake.animateTo(0f, motionSpec.shake())
                    }
                }
                is PlayEffect.Snapped -> {
                    haptics.tick()
                    play(Sound.Snap)
                    motion.snapTray = effect.trayIndex
                    launch {
                        motion.snapScale.snapTo(BonedleMotion.PICK_UP_SCALE)
                        motion.snapScale.animateTo(1f, motionSpec.snapIntoPlace())
                    }
                }
                is PlayEffect.Satisfied -> {
                    play(Sound.RegionDone)
                    motion.pulseRegions = effect.regions.toSet()
                    launch {
                        motion.pulse.snapTo(1f)
                        motion.pulse.animateTo(1f, motionSpec.pulse())
                    }
                }
                PlayEffect.Solved -> {
                    haptics.success()
                    play(Sound.Solved)
                    launch {
                        motion.sweep.snapTo(0f)
                        motion.sweep.animateTo(1f, motionSpec.solvedSweep())
                        motion.sweep.snapTo(0f)
                        if (isTutorial) showTutorialDone = true else solvedNow()
                    }
                }
            }
        }
    }
    LaunchedEffect(state.completion) {
        // A board opened already solved goes straight to its result.
        if (state.completion != null && motion.sweep.value == 0f) {
            delay(BonedleMotion.MICRO.toLong())
            if (motion.sweep.value == 0f) solvedNow()
        }
    }
    BackHandler(enabled = state.legendOpen || state.confirmRestart || state.hint != null) {
        when {
            state.legendOpen -> onIntent(PlayIntent.CloseLegend)
            state.confirmRestart -> onIntent(PlayIntent.CancelRestart)
            else -> onIntent(PlayIntent.DismissHint)
        }
    }

    val backdrop = rememberGlassBackdrop()
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .glassBackdropSource(backdrop, colors.bgBase),
        ) {
            PlayTopBar(state, onBack, onSkipTutorial)
            state.tutorialStep?.let { LessonBanner(it) }
            val game = state.game
            when {
                state.loading -> Message(stringResource(R.string.play_loading), Modifier.weight(1f))
                state.failed || game == null -> {
                    Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(stringResource(R.string.play_failed), style = BonedleTheme.typography.body, color = colors.textPrimary)
                        Spacer(Modifier.height(Spacing.lg))
                        BonedleButton(stringResource(R.string.action_retry), onClick = { onIntent(PlayIntent.Retry) })
                    }
                }
                else -> {
                    val interactive = !state.solved
                    BoxWithConstraints(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
                        contentAlignment = Alignment.Center,
                    ) {
                        val density = LocalDensity.current
                        val board = game.puzzle.board
                        val geometry = remember(board, maxWidth, maxHeight, density) {
                            with(density) {
                                val pad = BoardPadding.toPx()
                                val cell = minOf((maxWidth.toPx() - pad * 2) / board.cols, (maxHeight.toPx() - pad * 2) / board.rows, MaxCell.toPx())
                                BoardGeometry(board, cell, pad)
                            }
                        }
                        BoardView(
                            game = game,
                            geometry = geometry,
                            drag = drag,
                            motion = motion,
                            highlight = hintHighlight(state.hint, game.puzzle),
                            interactive = interactive,
                            onTapCell = { onIntent(PlayIntent.TapCell(it)) },
                            onDrop = onIntent,
                            onChipTap = { onIntent(PlayIntent.OpenLegend(it)) },
                            onReturnToTray = { onIntent(PlayIntent.ReturnToTray(it)) },
                        )
                    }
                    TrayView(
                        game = game,
                        poses = state.poses,
                        selected = state.selected,
                        drag = drag,
                        motion = motion,
                        interactive = interactive,
                        onSelect = { onIntent(PlayIntent.SelectTray(it)) },
                        onDrop = onIntent,
                        modifier = Modifier.padding(horizontal = Spacing.screen),
                    )
                    Spacer(
                        Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .height(ToolbarSpace),
                    )
                }
            }
        }
        ProvideGlassBackdrop(backdrop) {
            if (state.game != null && !state.solved) {
                Column(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        .padding(horizontal = Spacing.lg, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    HintPanel(state, onIntent, onWatchAd = rememberExtraHintAd(state, onIntent), onPlus = onPlus)
                    PlayToolbar(state, onIntent)
                }
            }
            RuleLegendSheet(state, onIntent)
            RestartDialog(state.confirmRestart, onIntent)
            if (isTutorial) TutorialStepPanel(state, showTutorialDone, onTutorialNext)
        }
        DragGhost(drag)
    }
}

@Composable
private fun PlayTopBar(state: PlayUiState, onBack: () -> Unit, onSkipTutorial: () -> Unit) {
    val colors = BonedleTheme.colors
    val id = state.id
    GlassTopBar(
        scrolled = false,
        navigation = { BonedleIconButton(BonedleIcons.Back, stringResource(R.string.action_back), onClick = onBack) },
        actions = {
            if (state.tutorialStep != null) {
                BonedleButton(stringResource(R.string.tutorial_skip), onClick = onSkipTutorial, style = ButtonStyle.Text)
            } else if (!state.hideTimer && state.game != null) {
                val time = formatDuration(state.elapsedMs)
                val spoken = stringResource(R.string.play_timer_description, time)
                Pill(time, icon = BonedleIcons.Timer, modifier = Modifier.semantics { contentDescription = spoken })
                Spacer(Modifier.width(Spacing.sm))
            }
        },
    ) {
        val step = state.tutorialStep
        if (step != null) {
            Column {
                Text(stringResource(R.string.tutorial_title), style = BonedleTheme.typography.heading, color = colors.textPrimary, modifier = Modifier.semantics { heading() })
                Text(stringResource(R.string.tutorial_progress, step + 1, TutorialBoards.STEPS), style = BonedleTheme.typography.caption, color = colors.textSecondary)
            }
        } else if (id != null) {
            Column {
                val mode = stringResource(if (id is GameId.Daily) R.string.mode_daily else R.string.mode_unlimited)
                Text(
                    stringResource(R.string.play_title, mode, stringResource(difficultyLabel(id.difficulty))),
                    style = BonedleTheme.typography.heading,
                    color = colors.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                if (id is GameId.Daily) {
                    Text(stringResource(R.string.play_daily_number, id.number), style = BonedleTheme.typography.caption, color = colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun PlayToolbar(state: PlayUiState, onIntent: (PlayIntent) -> Unit) {
    val actions = buildList {
        add(BarItem(BonedleIcons.Undo, stringResource(R.string.toolbar_undo), enabled = state.canUndo) to PlayIntent.Undo)
        add(BarItem(BonedleIcons.Rotate, stringResource(R.string.toolbar_rotate), enabled = state.selected != null || state.lastPlaced != null) to PlayIntent.Rotate)
        // Tutorial boards teach placement only; hints start on real boards.
        if (state.tutorialStep == null) add(BarItem(BonedleIcons.Hint, stringResource(R.string.toolbar_hint), badge = if (state.plus) stringResource(R.string.hints_unlimited_badge) else state.freeHintsLeft.toString()) to PlayIntent.Hint)
        add(BarItem(BonedleIcons.Restart, stringResource(R.string.toolbar_restart), enabled = state.game?.placements?.isNotEmpty() == true) to PlayIntent.RequestRestart)
    }
    GlassToolbar(
        items = actions.map { it.first },
        onClick = { index -> onIntent(actions[index].second) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun Message(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, style = BonedleTheme.typography.body, color = BonedleTheme.colors.textSecondary)
    }
}

/** The domino under the finger while dragging: lifted (scaled up, shadowed) above the board. */
@Composable
private fun DragGhost(drag: DragState) {
    val colors = BonedleTheme.colors
    if (!drag.active) return
    Canvas(Modifier.fillMaxSize()) {
        val cell = drag.geometry?.cell ?: return@Canvas
        val topLeft = drag.pointer - drag.grab
        val w = if (drag.horizontal) cell * 2 else cell
        val h = if (drag.horizontal) cell else cell * 2
        val inset = cell * 0.05f
        val center = topLeft + Offset(w / 2, h / 2)
        scale(BonedleMotion.PICK_UP_SCALE, pivot = center) {
            drawRoundRect(
                Color.Black.copy(alpha = 0.28f),
                topLeft = topLeft + Offset(inset, inset + 6.dp.toPx()),
                size = Size(w - inset * 2, h - inset * 2),
                cornerRadius = CornerRadius(cell * Radii.DOMINO_FRACTION),
            )
            drawDomino(
                topLeft + Offset(inset, inset),
                cell - inset * 2,
                if (drag.horizontal) DominoOrientation.Horizontal else DominoOrientation.Vertical,
                drag.lead,
                drag.trail,
                colors,
            )
        }
    }
}

/** The one-sentence lesson above a tutorial board. */
@Composable
private fun LessonBanner(step: Int) {
    val text = stringResource(
        when (TutorialBoards.lessons[step]) {
            TutorialBoards.Lesson.COVER -> R.string.tutorial_cover
            TutorialBoards.Lesson.EQUAL -> R.string.tutorial_equal
            TutorialBoards.Lesson.SUM -> R.string.tutorial_sum
            TutorialBoards.Lesson.ALL_DIFFERENT -> R.string.tutorial_all_different
        },
    )
    Text(
        text,
        style = BonedleTheme.typography.bodyStrong,
        color = BonedleTheme.colors.textPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The rewarded "one more hint" action, or null when it is not offered: Unlimited boards only
 * (never a daily), never with Plus, and only when an ad is loaded and allowed (spec 9).
 */
@Composable
private fun rememberExtraHintAd(state: PlayUiState, onIntent: (PlayIntent) -> Unit): (() -> Unit)? {
    val ads = LocalAds.current
    val activity = LocalActivity.current
    val ready by ads.readyRewarded.collectAsStateWithLifecycle()
    val offered = AdPolicy.extraHintOffered(AdPlacement.EXTRA_HINT in ready, state.plus, state.id is GameId.Unlimited)
    if (!offered || activity == null) return null
    return { ads.showRewarded(activity, AdPlacement.EXTRA_HINT, onReward = { onIntent(PlayIntent.GrantExtraHint) }) }
}


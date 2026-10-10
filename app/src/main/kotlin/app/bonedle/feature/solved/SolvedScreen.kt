package app.bonedle.feature.solved

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bonedle.R
import app.bonedle.ads.LocalAds
import app.bonedle.analytics.LocalAnalytics
import app.bonedle.analytics.ShareSurface
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameResult
import app.bonedle.domain.Challenge
import app.bonedle.domain.HeadToHead
import app.bonedle.domain.Links
import app.bonedle.domain.Outcome
import app.bonedle.engine.model.Difficulty
import app.bonedle.ui.design.AdaptiveRow
import app.bonedle.ui.design.BonedleButton
import app.bonedle.ui.design.BonedleCard
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.ButtonStyle
import app.bonedle.ui.design.StatTile
import app.bonedle.ui.format.difficultyLabel
import app.bonedle.ui.format.formatDuration
import app.bonedle.ui.format.formatTimeOfDay
import app.bonedle.ui.growth.InAppReview
import app.bonedle.ui.growth.rememberNotificationPermission
import app.bonedle.ui.share.ShareCard
import app.bonedle.ui.share.ShareCardContent
import app.bonedle.ui.share.ShareCardOffscreen
import app.bonedle.ui.share.copyText
import app.bonedle.ui.share.rememberShareCardCapture
import app.bonedle.ui.share.shareResult
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** What the Solved screen can do; a plain holder so the screen stays stateless. */
data class SolvedActions(
    val onHome: () -> Unit,
    val onNext: (Difficulty) -> Unit,
    /** The reminder offer was accepted; the argument is the notification permission answer. */
    val onAcceptReminder: (granted: Boolean) -> Unit = {},
    val onDismissReminder: () -> Unit = {},
    val onReviewRequested: () -> Unit = {},
)

@Composable
fun SolvedDestination(onHome: () -> Unit, onNext: (Difficulty) -> Unit, viewModel: SolvedViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SolvedScreen(
        state,
        SolvedActions(onHome, onNext, viewModel::acceptReminder, viewModel::dismissReminder, viewModel::reviewRequested),
    )
}

/** "no hints" / "1 hint" / "3 hints". */
@Composable
@ReadOnlyComposable
private fun hintsText(hints: Int): String =
    if (hints == 0) stringResource(R.string.share_no_hints) else pluralStringResource(R.plurals.share_hints, hints, hints)

/** "2:14 · no hints · 12-day streak" (spec 8.1). */
@Composable
@ReadOnlyComposable
private fun statsLine(result: GameResult, streak: Int, withStreak: Boolean): String {
    val parts = mutableListOf(formatDuration(result.timeMs), hintsText(result.hintsUsed))
    if (withStreak && streak > 0) parts += stringResource(R.string.share_streak, streak)
    return parts.joinToString(" · ")
}

/** What a share sends: the text (ending in the challenge link) and the card's content. */
private class ShareData(val text: String, val card: ShareCardContent)

/**
 * The share text, `Bonedle #142 Medium · 2:14 · no hints · 12-day streak` plus a link that
 * carries this result (so the friend gets a head-to-head), and the card, which prints the
 * short link. Null for boards without a link (tutorial).
 */
@Composable
@ReadOnlyComposable
private fun shareData(id: GameId, result: GameResult, streak: Int, subtitle: String, difficulty: String): ShareData? {
    val cardLink = Links.forGame(id) ?: return null
    val challengeLink = Links.forGame(id, Challenge(result.timeMs, result.hintsUsed)) ?: return null
    val line = statsLine(result, streak, withStreak = id is GameId.Daily)
    val headline = when (id) {
        is GameId.Daily -> stringResource(R.string.share_daily, id.number, difficulty)
        else -> stringResource(R.string.share_unlimited, difficulty)
    }
    val header = "$headline · $line"
    return ShareData(
        text = "$header\n${Links.display(challengeLink)}",
        card = ShareCardContent(
            title = subtitle,
            stats = line,
            link = Links.display(cardLink),
            description = stringResource(R.string.solved_card_description, header),
        ),
    )
}

/** How long "Copied" replaces "Copy text". */
private const val COPIED_MS = 2_000L

/**
 * Solved (spec 5.4): emblem, time / hints / streak, the share card and the way on. A board
 * opened from a friend's challenge link shows the head-to-head instead of the tiles and offers
 * "Send it back" (spec 8.3).
 */
@Composable
fun SolvedScreen(state: SolvedUiState, actions: SolvedActions) {
    val colors = BonedleTheme.colors
    val context = LocalContext.current
    val analytics = LocalAnalytics.current
    val scope = rememberCoroutineScope()
    val capture = rememberShareCardCapture()
    val id = state.id
    val result = state.result
    val headToHead = state.headToHead
    val difficulty = id?.difficulty?.let { stringResource(difficultyLabel(it)) }.orEmpty()
    val daily = id as? GameId.Daily
    val subtitle = when (id) {
        is GameId.Daily -> stringResource(R.string.solved_subtitle_daily, id.number, difficulty)
        is GameId.Unlimited -> stringResource(R.string.solved_subtitle_unlimited, difficulty)
        else -> ""
    }
    val title = when (headToHead?.outcome) {
        Outcome.WON -> R.string.solved_title_won
        Outcome.LOST -> R.string.solved_title_lost
        Outcome.TIE -> R.string.solved_title_tie
        null -> R.string.solved_title
    }
    val share = if (id != null && result != null) shareData(id, result, state.streak, subtitle, difficulty) else null
    val puzzle = state.puzzle
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bgBase),
    ) {
        // Drawn only into the share image: it takes no room and is never visible.
        if (share != null && puzzle != null) ShareCardOffscreen(capture, puzzle, share.card)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = Spacing.screen, vertical = Spacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            Emblem()
            Text(
                stringResource(title),
                style = BonedleTheme.typography.display,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(subtitle, style = BonedleTheme.typography.body, color = colors.textSecondary)
            if (result != null && id != null) {
                if (headToHead != null) {
                    HeadToHeadCard(headToHead)
                } else {
                    AdaptiveRow { item ->
                        StatTile(stringResource(R.string.solved_time), formatDuration(result.timeMs), item, icon = BonedleIcons.Timer)
                        StatTile(stringResource(R.string.solved_hints), result.hintsUsed.toString(), item, icon = BonedleIcons.Hint)
                        if (daily != null) {
                            StatTile(stringResource(R.string.solved_streak), state.streak.toString(), item, icon = BonedleIcons.Flame)
                        }
                    }
                }
                if (state.offerReminder) {
                    ReminderOfferCard(state.reminderMinutes, actions.onAcceptReminder, actions.onDismissReminder)
                }
                if (share != null) {
                    if (puzzle != null) {
                        // A preview of the image that is shared, so it keeps the image's text size
                        // at every font scale; TalkBack reads its full description instead.
                        val density = LocalDensity.current
                        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1f)) {
                            ShareCard(puzzle = puzzle, content = share.card, modifier = Modifier.widthIn(max = 340.dp))
                        }
                    }
                    val chooser = stringResource(R.string.solved_share_chooser)
                    val surface = if (headToHead != null) ShareSurface.HEAD_TO_HEAD else ShareSurface.SOLVED
                    BonedleButton(
                        stringResource(if (headToHead != null) R.string.solved_send_back else R.string.solved_share),
                        onClick = {
                            scope.launch {
                                val image = if (puzzle != null) capture.export(context, "bonedle-${Links.boardId(id).replace('/', '-')}") else null
                                shareResult(context, analytics, surface, id, share.text, chooser, image)
                            }
                        },
                        icon = BonedleIcons.Share,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    CopyTextButton(id, share.text)
                }
            }
            if (id is GameId.Unlimited) {
                val ads = LocalAds.current
                val activity = LocalActivity.current
                BonedleButton(
                    stringResource(R.string.solved_next),
                    onClick = {
                        // Between Unlimited boards is the one place an interstitial may appear (spec 9).
                        if (activity != null) ads.continueAfterUnlimited(activity) { actions.onNext(id.difficulty) } else actions.onNext(id.difficulty)
                    },
                    style = ButtonStyle.Secondary,
                    icon = BonedleIcons.Play,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            BonedleButton(
                stringResource(R.string.solved_home),
                onClick = actions.onHome,
                style = if (id is GameId.Unlimited) ButtonStyle.Text else ButtonStyle.Secondary,
                icon = BonedleIcons.Home,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    InAppReview(ask = state.askReview, onRequested = actions.onReviewRequested)
}

/** "Copy text": the share text on the clipboard, confirmed in place for a moment. */
@Composable
private fun CopyTextButton(id: GameId, text: String) {
    val context = LocalContext.current
    val analytics = LocalAnalytics.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MS)
            copied = false
        }
    }
    val label = stringResource(R.string.solved_copy_label)
    BonedleButton(
        stringResource(if (copied) R.string.solved_copied else R.string.solved_copy),
        onClick = {
            copyText(context, analytics, id, label, text)
            copied = true
        },
        style = ButtonStyle.Text,
        icon = if (copied) BonedleIcons.Check else BonedleIcons.Copy,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** Both times side by side and who was faster (spec 8.3). */
@Composable
private fun HeadToHeadCard(h2h: HeadToHead) {
    val colors = BonedleTheme.colors
    val margin = formatDuration(h2h.marginMs)
    val verdict = when (h2h.outcome) {
        Outcome.WON -> stringResource(R.string.h2h_won_by, margin)
        Outcome.LOST -> stringResource(R.string.h2h_lost_by, margin)
        Outcome.TIE -> stringResource(R.string.h2h_tie)
    }
    val mine = formatDuration(h2h.mineMs)
    val theirs = formatDuration(h2h.theirs.timeMs)
    val mineHints = hintsText(h2h.mineHints)
    val theirHints = hintsText(h2h.theirs.hintsUsed)
    val description = stringResource(R.string.h2h_description, mine, mineHints, theirs, theirHints, verdict)
    BonedleCard(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = description }) {
        Text(stringResource(R.string.h2h_title), style = BonedleTheme.typography.caption, color = colors.textSecondary)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Side(stringResource(R.string.h2h_you), mine, mineHints, winner = h2h.outcome == Outcome.WON, Modifier.weight(1f))
            Spacer(Modifier.width(Spacing.sm))
            Side(stringResource(R.string.h2h_friend), theirs, theirHints, winner = h2h.outcome == Outcome.LOST, Modifier.weight(1f))
        }
        Text(
            verdict,
            style = BonedleTheme.typography.bodyStrong,
            color = if (h2h.outcome == Outcome.WON) colors.success else colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Side(label: String, time: String, hints: String, winner: Boolean, modifier: Modifier = Modifier) {
    val colors = BonedleTheme.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (winner) {
                Icon(BonedleIcons.Crown, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.width(Spacing.xs))
            }
            Text(label, style = BonedleTheme.typography.label, color = colors.textSecondary)
        }
        Text(
            time,
            style = BonedleTheme.typography.display.copy(fontFeatureSettings = "tnum"),
            color = if (winner) colors.accentText else colors.textPrimary,
        )
        Text(hints, style = BonedleTheme.typography.caption, color = colors.textSecondary)
    }
}

/** The one-time reminder offer; accepting asks for the notification permission first. */
@Composable
private fun ReminderOfferCard(minutes: Int, onAccept: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val colors = BonedleTheme.colors
    val context = LocalContext.current
    val requestPermission = rememberNotificationPermission(onAccept)
    BonedleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BonedleIcons.Bell, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.reminder_offer_title), style = BonedleTheme.typography.heading, color = colors.textPrimary)
        }
        Text(
            stringResource(R.string.reminder_offer_body, formatTimeOfDay(context, minutes)),
            style = BonedleTheme.typography.body,
            color = colors.textSecondary,
        )
        AdaptiveRow { item ->
            BonedleButton(stringResource(R.string.reminder_offer_decline), onClick = onDismiss, style = ButtonStyle.Secondary, modifier = item)
            BonedleButton(stringResource(R.string.reminder_offer_accept), onClick = requestPermission, modifier = item)
        }
    }
}

/** The success emblem: a check in a success-coloured disc that springs in. */
@Composable
private fun Emblem() {
    val colors = BonedleTheme.colors
    val motion = BonedleTheme.motion
    val scale = remember { Animatable(if (motion.reduced) 1f else 0.6f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, motion.snapIntoPlace()) }
    Box(
        Modifier
            .size(88.dp)
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
            }
            .clip(CircleShape)
            .background(colors.success.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(BonedleIcons.Check, contentDescription = null, tint = colors.success, modifier = Modifier.size(44.dp))
    }
}

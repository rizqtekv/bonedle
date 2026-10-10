package app.bonedle.feature.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.bonedle.R
import app.bonedle.ads.AdPlacement
import app.bonedle.ads.LocalAds
import app.bonedle.analytics.LocalAnalytics
import app.bonedle.analytics.ShareSurface
import app.bonedle.data.game.BoardStatus
import app.bonedle.data.game.GameId
import app.bonedle.domain.Links
import app.bonedle.engine.model.Difficulty
import app.bonedle.navigation.Tab
import app.bonedle.navigation.TabBarClearance
import app.bonedle.navigation.TabScaffold
import app.bonedle.ui.design.BonedleButton
import app.bonedle.ui.design.BonedleCard
import app.bonedle.ui.design.BonedleIconButton
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.BonedleLogo
import app.bonedle.ui.design.ButtonStyle
import app.bonedle.ui.design.CardStyle
import app.bonedle.ui.design.Pill
import app.bonedle.ui.design.PillStyle
import app.bonedle.ui.design.SegmentedControl
import app.bonedle.ui.design.SegmentedStyle
import app.bonedle.ui.format.difficultyLabel
import app.bonedle.ui.format.formatDuration
import app.bonedle.ui.format.formatLongDate
import app.bonedle.ui.share.shareResult
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing

/** What Home asks the app to do. */
data class HomeActions(
    val openDaily: (GameId.Daily, BoardStatus) -> Unit,
    val playUnlimited: (Difficulty, GameId.Unlimited?) -> Unit,
    val selectUnlimited: (Difficulty) -> Unit,
    val openSettings: () -> Unit,
    val howToPlay: () -> Unit,
    val onTab: (Tab) -> Unit,
    /** The streak saver ad was watched for this missed day. */
    val saveStreak: (Long) -> Unit = {},
)

@Composable
fun HomeDestination(
    onOpenDaily: (GameId.Daily, BoardStatus) -> Unit,
    onPlayUnlimited: (Difficulty, GameId.Unlimited?) -> Unit,
    onSettings: () -> Unit,
    onHowToPlay: () -> Unit,
    onTab: (Tab) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) {
        viewModel.onResume()
        onPauseOrDispose { }
    }
    HomeScreen(state, HomeActions(onOpenDaily, onPlayUnlimited, viewModel::selectUnlimited, onSettings, onHowToPlay, onTab, viewModel::saveStreak))
}

/** Home (spec 5.2). Stateless, for previews and screenshots. */
@Composable
fun HomeScreen(state: HomeUiState, actions: HomeActions) {
    val colors = BonedleTheme.colors
    TabScaffold(selected = Tab.Home, onTab = actions.onTab) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.sm, bottom = TabBarClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            Header(state, actions)
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(formatLongDate(state.today), style = BonedleTheme.typography.heading, color = colors.textPrimary)
                Text(stringResource(R.string.play_daily_number, state.number), style = BonedleTheme.typography.caption, color = colors.textSecondary)
            }
            state.rescue?.let { StreakSaverCard(it, actions.saveStreak) }
            TodayCard(state, actions)
            UnlimitedCard(state, actions)
            ChallengeCard(state)
            BonedleButton(
                stringResource(R.string.home_how_to_play),
                onClick = actions.howToPlay,
                style = ButtonStyle.Text,
                icon = BonedleIcons.Help,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

/**
 * Yesterday was missed and the free freeze is spent: a rewarded ad can save the streak (spec
 * 8.4). Shown only while such an ad is ready, so the button always works.
 */
@Composable
private fun StreakSaverCard(rescue: StreakRescue, onSaved: (Long) -> Unit) {
    val ads = LocalAds.current
    val activity = LocalActivity.current
    val ready by ads.readyRewarded.collectAsStateWithLifecycle()
    if (AdPlacement.STREAK_SAVER !in ready || activity == null || rescue.streak <= 0) return
    val colors = BonedleTheme.colors
    BonedleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BonedleIcons.Snowflake, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.sm))
            Text(
                pluralStringResource(R.plurals.streak_saver_title, rescue.streak, rescue.streak),
                style = BonedleTheme.typography.heading,
                color = colors.textPrimary,
            )
        }
        Text(stringResource(R.string.streak_saver_body), style = BonedleTheme.typography.body, color = colors.textSecondary)
        BonedleButton(
            stringResource(R.string.streak_saver_action),
            onClick = { ads.showRewarded(activity, AdPlacement.STREAK_SAVER, onReward = { onSaved(rescue.day) }) },
            icon = BonedleIcons.Video,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Header(state: HomeUiState, actions: HomeActions) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        BonedleLogo(Modifier.weight(1f), markSize = 28.dp)
        val streak = stringResource(R.string.home_streak_description, state.streak)
        Pill(
            state.streak.toString(),
            icon = BonedleIcons.Flame,
            style = if (state.streak > 0) PillStyle.Accent else PillStyle.Neutral,
            modifier = Modifier.clearAndSetSemantics { contentDescription = streak },
        )
        Spacer(Modifier.width(Spacing.xs))
        BonedleIconButton(BonedleIcons.Settings, stringResource(R.string.nav_settings), onClick = actions.openSettings)
    }
}

@Composable
private fun TodayCard(state: HomeUiState, actions: HomeActions) {
    val colors = BonedleTheme.colors
    BonedleCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.home_today_title), style = BonedleTheme.typography.heading, color = colors.textPrimary)
        state.dailies.forEach { row ->
            val label = stringResource(difficultyLabel(row.id.difficulty))
            val statusText = when (val s = row.status) {
                BoardStatus.New -> stringResource(R.string.home_play)
                is BoardStatus.InProgress -> stringResource(R.string.home_continue, formatDuration(s.elapsedMs))
                is BoardStatus.Solved -> formatDuration(s.timeMs)
            }
            val spoken = when (val s = row.status) {
                is BoardStatus.Solved -> stringResource(R.string.home_row_description, label, stringResource(R.string.home_solved_in, formatDuration(s.timeMs)))
                else -> stringResource(R.string.home_row_description, label, statusText)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = Sizes.touchTarget)
                    .clickable(role = Role.Button) { actions.openDaily(row.id, row.status) }
                    .clearAndSetSemantics {
                        contentDescription = spoken
                        role = Role.Button
                        onClick { actions.openDaily(row.id, row.status); true }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = BonedleTheme.typography.bodyStrong, color = colors.textPrimary, modifier = Modifier.weight(1f))
                when (row.status) {
                    is BoardStatus.Solved -> Pill(statusText, icon = BonedleIcons.Check, style = PillStyle.Success)
                    is BoardStatus.InProgress -> Pill(statusText, icon = BonedleIcons.Play, style = PillStyle.Accent)
                    BoardStatus.New -> Pill(statusText, icon = BonedleIcons.Play)
                }
            }
        }
    }
}

@Composable
private fun UnlimitedCard(state: HomeUiState, actions: HomeActions) {
    val colors = BonedleTheme.colors
    BonedleCard(Modifier.fillMaxWidth(), style = CardStyle.Accent) {
        Text(stringResource(R.string.home_unlimited_title), style = BonedleTheme.typography.title, color = colors.onAccent)
        Text(stringResource(R.string.home_unlimited_body), style = BonedleTheme.typography.body, color = colors.onAccent)
        SegmentedControl(
            options = Difficulty.entries.map { stringResource(difficultyLabel(it)) },
            selectedIndex = state.unlimitedDifficulty.ordinal,
            onSelect = { actions.selectUnlimited(Difficulty.entries[it]) },
            style = SegmentedStyle.OnAccent,
            modifier = Modifier.fillMaxWidth(),
        )
        val resume = state.unlimitedInProgress
        BonedleButton(
            stringResource(if (resume != null) R.string.home_continue_unlimited else R.string.home_play_unlimited),
            onClick = { actions.playUnlimited(state.unlimitedDifficulty, resume) },
            style = ButtonStyle.OnAccent,
            icon = BonedleIcons.Play,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ChallengeCard(state: HomeUiState) {
    val colors = BonedleTheme.colors
    val context = LocalContext.current
    val analytics = LocalAnalytics.current
    val daily = state.dailies.firstOrNull { it.id.difficulty == Difficulty.MEDIUM }?.id ?: return
    val difficulty = stringResource(difficultyLabel(daily.difficulty))
    val message = stringResource(R.string.share_challenge, daily.number, difficulty)
    val chooser = stringResource(R.string.home_challenge_title)
    val link = Links.forGame(daily) ?: return
    BonedleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(BonedleIcons.Friends, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(Sizes.icon))
            Spacer(Modifier.width(Spacing.sm))
            Text(stringResource(R.string.home_challenge_title), style = BonedleTheme.typography.heading, color = colors.textPrimary)
        }
        Text(stringResource(R.string.home_challenge_body), style = BonedleTheme.typography.body, color = colors.textSecondary)
        BonedleButton(
            stringResource(R.string.home_challenge_action),
            onClick = { shareResult(context, analytics, ShareSurface.CHALLENGE, daily, "$message\n${Links.display(link)}", chooser) },
            style = ButtonStyle.Secondary,
            icon = BonedleIcons.Share,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

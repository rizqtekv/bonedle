package app.bonedle.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.bonedle.R
import app.bonedle.data.Clock
import app.bonedle.data.game.ProgressRepository
import app.bonedle.domain.HeatDay
import app.bonedle.domain.Stats
import app.bonedle.domain.StatsCalculator
import app.bonedle.engine.model.Difficulty
import app.bonedle.navigation.Tab
import app.bonedle.navigation.TabBarClearance
import app.bonedle.navigation.TabScaffold
import app.bonedle.ui.design.AdaptiveRow
import app.bonedle.ui.design.BonedleCard
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.ScreenTitle
import app.bonedle.ui.design.StatTile
import app.bonedle.ui.format.difficultyLabel
import app.bonedle.ui.format.formatDate
import app.bonedle.ui.format.formatDuration
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(progress: ProgressRepository, clock: Clock) : ViewModel() {
    val stats: StateFlow<Stats?> = progress.progress
        .map { StatsCalculator.compute(it, clock.today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}

@Composable
fun StatsDestination(onTab: (Tab) -> Unit, viewModel: StatsViewModel = hiltViewModel()) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    StatsScreen(stats, onTab)
}

/** Stats (spec 5.5). */
@Composable
fun StatsScreen(stats: Stats?, onTab: (Tab) -> Unit) {
    TabScaffold(selected = Tab.Stats, onTab = onTab) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = Spacing.screen)
                .padding(top = Spacing.lg, bottom = TabBarClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.section),
        ) {
            ScreenTitle(stringResource(R.string.stats_title))
            if (stats == null) return@Column
            AdaptiveRow { item ->
                StatTile(stringResource(R.string.stats_played), stats.gamesPlayed.toString(), item)
                val rate = stats.solveRatePercent?.let { stringResource(R.string.stats_solve_rate_value, it) } ?: stringResource(R.string.stats_no_time)
                StatTile(stringResource(R.string.stats_solve_rate), rate, item)
            }
            AdaptiveRow { item ->
                StatTile(stringResource(R.string.stats_current_streak), stats.currentStreak.toString(), item, icon = BonedleIcons.Flame)
                StatTile(stringResource(R.string.stats_best_streak), stats.bestStreak.toString(), item)
            }
            Text(
                stringResource(if (stats.freezeUsedThisWeek) R.string.stats_freeze_used else R.string.stats_freeze_ready),
                style = BonedleTheme.typography.caption,
                color = BonedleTheme.colors.textSecondary,
            )
            AverageCard(stats)
            Heatmap(stats.heatmap)
        }
    }
}

@Composable
private fun AverageCard(stats: Stats) {
    val colors = BonedleTheme.colors
    BonedleCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.stats_average_title), style = BonedleTheme.typography.heading, color = colors.textPrimary)
        Difficulty.entries.forEach { d ->
            val ms = stats.averageMs[d]
            val label = stringResource(difficultyLabel(d))
            val value = ms?.let(::formatDuration) ?: stringResource(R.string.stats_no_time)
            val spoken = ms?.let(::formatDuration) ?: stringResource(R.string.stats_no_time_description)
            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp)
                    .clearAndSetSemantics { contentDescription = "$label, $spoken" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = BonedleTheme.typography.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
                Text(value, style = BonedleTheme.typography.timer, color = colors.textPrimary)
            }
        }
    }
}

/** The last 30 days as a calendar: one square per day, filled by how many dailies were solved. */
@Composable
private fun Heatmap(days: List<HeatDay>) {
    val colors = BonedleTheme.colors
    BonedleCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.stats_calendar_title), style = BonedleTheme.typography.heading, color = colors.textPrimary)
        // Weeks run Monday to Sunday; pad the first week so columns line up by weekday.
        val firstWeekday = (days.first().date.epochDay + 3).mod(7L).toInt() // Monday = 0
        val cells: List<HeatDay?> = List(firstWeekday) { null } + days
        cells.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                for (i in 0 until 7) {
                    val day = week.getOrNull(i)
                    Box(Modifier.weight(1f).aspectRatio(1f)) {
                        if (day != null) HeatCell(day)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.stats_calendar_less), style = BonedleTheme.typography.caption, color = colors.textSecondary)
            for (n in 0..3) Box(Modifier.size(14.dp).clip(RoundedCornerShape(3.dp)).background(heatColor(n)))
            Text(stringResource(R.string.stats_calendar_more), style = BonedleTheme.typography.caption, color = colors.textSecondary)
        }
    }
}

@Composable
private fun HeatCell(day: HeatDay) {
    val spoken = stringResource(R.string.stats_calendar_day, formatDate(day.date, "MMMd"), day.dailies)
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(5.dp))
            .background(heatColor(day.dailies))
            .semantics { contentDescription = spoken },
    )
}

@Composable
private fun heatColor(count: Int): Color {
    val colors = BonedleTheme.colors
    return when (count) {
        0 -> colors.surface2
        1 -> colors.accent.copy(alpha = 0.4f)
        2 -> colors.accent.copy(alpha = 0.7f)
        else -> colors.accent
    }
}

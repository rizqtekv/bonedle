package app.bonedle.feature.archive

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.bonedle.R
import app.bonedle.data.Clock
import app.bonedle.data.game.BoardStatus
import app.bonedle.data.game.GameId
import app.bonedle.data.game.ProgressRepository
import app.bonedle.data.settings.EntitlementRepository
import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import app.bonedle.navigation.Tab
import app.bonedle.navigation.TabBarClearance
import app.bonedle.navigation.TabScaffold
import app.bonedle.ui.design.BonedleCard
import app.bonedle.ui.design.BonedleIcons
import app.bonedle.ui.design.ScreenTitle
import app.bonedle.ui.format.difficultyLabel
import app.bonedle.ui.format.formatDuration
import app.bonedle.ui.format.formatShortDate
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.Shapes
import app.bonedle.ui.theme.Sizes
import app.bonedle.ui.theme.Spacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** One past daily in the archive. */
@Immutable
data class ArchiveEntry(val number: Int, val date: CalendarDate, val boards: List<Pair<GameId.Daily, BoardStatus>>, val locked: Boolean)

@Immutable
data class ArchiveUiState(val entries: List<ArchiveEntry> = emptyList(), val loading: Boolean = true)

@HiltViewModel
class ArchiveViewModel @Inject constructor(progress: ProgressRepository, entitlements: EntitlementRepository, clock: Clock) : ViewModel() {
    val state: StateFlow<ArchiveUiState> = combine(progress.progress, entitlements.plus) { p, plus ->
        val today = DailySchedule.numberOn(clock.today())
        val entries = (today - 1 downTo DailySchedule.FIRST_NUMBER).map { n ->
            ArchiveEntry(
                number = n,
                date = DailySchedule.dateOf(n),
                boards = Difficulty.DAILY.map { d -> GameId.Daily(n, d).let { it to p.status(it.key) } },
                locked = !plus && today - n > FREE_DAYS,
            )
        }
        ArchiveUiState(entries, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArchiveUiState())

    companion object {
        /** Days back that are free; older boards come with Bonedle Plus (spec 5.6). */
        const val FREE_DAYS = 30
    }
}

@Composable
fun ArchiveDestination(onOpen: (GameId.Daily, BoardStatus) -> Unit, onTab: (Tab) -> Unit, onLocked: () -> Unit, viewModel: ArchiveViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ArchiveScreen(state, onOpen, onTab, onLocked)
}

/** Archive (spec 5.6): every past daily, newest first. A locked board opens Bonedle Plus ([onLocked]). */
@Composable
fun ArchiveScreen(state: ArchiveUiState, onOpen: (GameId.Daily, BoardStatus) -> Unit, onTab: (Tab) -> Unit, onLocked: () -> Unit = {}) {
    TabScaffold(selected = Tab.Archive, onTab = onTab) {
        LazyColumn(
            Modifier.windowInsetsPadding(WindowInsets.statusBars),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, top = Spacing.lg, bottom = TabBarClearance),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { ScreenTitle(stringResource(R.string.archive_title), subtitle = stringResource(R.string.archive_subtitle)) }
            if (!state.loading && state.entries.isEmpty()) {
                item {
                    Text(stringResource(R.string.archive_empty), style = BonedleTheme.typography.body, color = BonedleTheme.colors.textSecondary, modifier = Modifier.padding(top = Spacing.lg))
                }
            }
            items(state.entries, key = { it.number }) { entry -> EntryCard(entry, onOpen, onLocked) }
        }
    }
}

@Composable
private fun EntryCard(entry: ArchiveEntry, onOpen: (GameId.Daily, BoardStatus) -> Unit, onLocked: () -> Unit) {
    val colors = BonedleTheme.colors
    BonedleCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.archive_entry_title, entry.number, formatShortDate(entry.date)),
                style = BonedleTheme.typography.bodyStrong,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f),
            )
            if (entry.locked) {
                val lockText = stringResource(R.string.archive_locked)
                Icon(BonedleIcons.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(Sizes.iconSmall))
                Spacer(Modifier.width(Spacing.xs))
                Text(lockText, style = BonedleTheme.typography.caption, color = colors.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            entry.boards.forEach { (id, status) ->
                BoardChip(id, status, entry.locked, Modifier.weight(1f)) { if (entry.locked) onLocked() else onOpen(id, status) }
            }
        }
    }
}

@Composable
private fun BoardChip(id: GameId.Daily, status: BoardStatus, locked: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = BonedleTheme.colors
    val label = stringResource(difficultyLabel(id.difficulty))
    val statusText = when (status) {
        BoardStatus.New -> stringResource(R.string.status_new)
        is BoardStatus.InProgress -> stringResource(R.string.status_in_progress)
        is BoardStatus.Solved -> stringResource(R.string.status_solved, formatDuration(status.timeMs))
    }
    val spoken = if (locked) stringResource(R.string.archive_locked_description) else stringResource(R.string.archive_chip_description, label, statusText)
    // A locked board leads to Bonedle Plus; TalkBack says so ("double-tap to see Bonedle Plus").
    val plusLabel = stringResource(R.string.archive_open_plus)
    val (fill, border) = when (status) {
        is BoardStatus.Solved -> colors.success.copy(alpha = 0.18f) to Color.Transparent
        is BoardStatus.InProgress -> colors.accent.copy(alpha = 0.14f) to colors.accent
        BoardStatus.New -> colors.surface2 to Color.Transparent
    }
    Column(
        modifier
            .heightIn(min = Sizes.touchTarget)
            .clip(Shapes.button)
            .background(fill)
            .border(1.dp, border, Shapes.button)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics {
                contentDescription = "$label, $spoken"
                role = Role.Button
                onClick(label = if (locked) plusLabel else null) {
                    onClick()
                    true
                }
            }
            .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(label, style = BonedleTheme.typography.label, color = if (locked) colors.textSecondary else colors.textPrimary)
        Box(Modifier.heightIn(min = 18.dp), contentAlignment = Alignment.Center) {
            when (status) {
                is BoardStatus.Solved -> Text(formatDuration(status.timeMs), style = BonedleTheme.typography.timer, color = colors.success)
                is BoardStatus.InProgress -> Icon(BonedleIcons.Play, contentDescription = null, tint = colors.accentText, modifier = Modifier.size(16.dp))
                BoardStatus.New -> if (locked) Icon(BonedleIcons.Lock, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(14.dp))
            }
        }
    }
}

package app.bonedle.feature.home

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.data.Clock
import app.bonedle.data.game.BoardStatus
import app.bonedle.data.game.GameId
import app.bonedle.data.game.Progress
import app.bonedle.data.game.ProgressRepository
import app.bonedle.data.game.PuzzleRepository
import app.bonedle.data.settings.SettingsRepository
import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One of today's daily rows. */
@Immutable
data class DailyRow(val id: GameId.Daily, val status: BoardStatus)

@Immutable
data class HomeUiState(
    val loading: Boolean = true,
    val today: CalendarDate = CalendarDate(2026, 1, 1),
    val number: Int = 1,
    val dailies: List<DailyRow> = emptyList(),
    val streak: Int = 0,
    val unlimitedDifficulty: Difficulty = Difficulty.MEDIUM,
    /** An unfinished Unlimited board at [unlimitedDifficulty], resumed by the Play button. */
    val unlimitedInProgress: GameId.Unlimited? = null,
)

/** Home: today's three dailies, the Unlimited card and the streak, all live from the database. */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val progress: ProgressRepository,
    private val settings: SettingsRepository,
    private val puzzles: PuzzleRepository,
    private val clock: Clock,
    private val analytics: Analytics,
) : ViewModel() {
    /** Re-read on every resume, so the daily rolls over at local midnight. */
    private val today = MutableStateFlow(clock.today())

    val state: StateFlow<HomeUiState> = combine(progress.progress, settings.settings, today) { p, s, day ->
        val number = DailySchedule.numberOn(day)
        val unlimited = p.latestUnlimitedSave(s.unlimitedDifficulty.code)
        HomeUiState(
            loading = false,
            today = day,
            number = number,
            dailies = Difficulty.DAILY.map { d -> GameId.Daily(number, d).let { DailyRow(it, p.status(it.key)) } },
            streak = p.streak(day.epochDay).current,
            unlimitedDifficulty = s.unlimitedDifficulty,
            unlimitedInProgress = unlimited,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Home came to the foreground: roll the date over and spend a streak freeze if one is due. */
    fun onResume() {
        today.value = clock.today()
        viewModelScope.launch {
            if (progress.reconcileStreak() != null) analytics.log(Events.streakFreezeUsed(Progress.FREEZE_FREE))
        }
    }

    fun selectUnlimited(difficulty: Difficulty) {
        viewModelScope.launch { settings.setUnlimitedDifficulty(difficulty) }
        // Start generating now, so pressing Play is instant.
        if (state.value.unlimitedInProgress?.difficulty != difficulty) puzzles.prefetch(difficulty)
    }
}

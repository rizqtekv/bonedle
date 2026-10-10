package app.bonedle.feature.solved

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.bonedle.ads.AdHistory
import app.bonedle.ads.AdPolicy
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.data.Clock
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameRepository
import app.bonedle.data.game.GameResult
import app.bonedle.data.game.Progress
import app.bonedle.data.game.ProgressRepository
import app.bonedle.data.game.PuzzleRepository
import app.bonedle.data.links.LinkRepository
import app.bonedle.data.settings.GrowthPreferences
import app.bonedle.data.settings.Settings
import app.bonedle.data.settings.SettingsRepository
import app.bonedle.domain.Challenge
import app.bonedle.domain.HeadToHead
import app.bonedle.domain.Outcome
import app.bonedle.domain.ReminderOffer
import app.bonedle.domain.ReviewPolicy
import app.bonedle.engine.model.Puzzle
import app.bonedle.navigation.SolvedRoute
import app.bonedle.notifications.ReminderController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SolvedUiState(
    val id: GameId? = null,
    val result: GameResult? = null,
    /** For the share card's silhouette; arrives a moment after the result for Unlimited boards. */
    val puzzle: Puzzle? = null,
    val streak: Int = 0,
    /** The friend's result, when a challenge link brought this board. */
    val challenge: Challenge? = null,
    /** Offer the daily reminder (after the third completed daily, once). */
    val offerReminder: Boolean = false,
    val reminderMinutes: Int = Settings.DEFAULT_REMINDER_MINUTES,
    /** Request the Play rating flow. */
    val askReview: Boolean = false,
) {
    val headToHead: HeadToHead?
        get() {
            val mine = result ?: return null
            return challenge?.let { HeadToHead(mine.timeMs, mine.hintsUsed, it) }
        }
}

/**
 * The result of one finished board. Straight after a solve ([SolvedRoute.fresh]) it may offer
 * the daily reminder or ask for a rating, never both on one screen; revisiting an old result
 * asks nothing.
 */
@HiltViewModel
class SolvedViewModel @Inject constructor(
    savedState: SavedStateHandle,
    games: GameRepository,
    private val progress: ProgressRepository,
    puzzles: PuzzleRepository,
    links: LinkRepository,
    private val settings: SettingsRepository,
    private val growth: GrowthPreferences,
    private val reminders: ReminderController,
    private val analytics: Analytics,
    private val adHistory: AdHistory,
    private val clock: Clock,
) : ViewModel() {
    private val route: SolvedRoute = savedState.toRoute()
    private val _state = MutableStateFlow(SolvedUiState(id = GameId.parse(route.gameKey)))
    val state: StateFlow<SolvedUiState> = _state.asStateFlow()

    init {
        val id = _state.value.id
        if (id != null) {
            viewModelScope.launch {
                val result = games.result(id)
                val challenge = links.challenge(id)
                _state.update { it.copy(result = result, challenge = challenge) }
                if (route.fresh && result != null) decidePrompts(id, result, challenge)
            }
            viewModelScope.launch { _state.update { it.copy(puzzle = puzzles.puzzle(id)) } }
            viewModelScope.launch {
                progress.progress.collect { p -> _state.update { it.copy(streak = p.streak(clock.today().epochDay).current) } }
            }
        }
    }

    private suspend fun decidePrompts(id: GameId, result: GameResult, challenge: Challenge?) {
        val p = progress.progress.first()
        val s = settings.settings.first()
        val offer = ReminderOffer.shouldOffer(
            isDaily = id is GameId.Daily,
            dailiesCompleted = dailiesCompleted(p),
            alreadyOffered = growth.reminderOffered.first(),
            reminderOn = s.reminderEnabled,
        )
        if (offer) {
            // One chance: Settings keeps the switch for later.
            growth.setReminderOffered()
            _state.update { it.copy(offerReminder = true, reminderMinutes = s.reminderMinutes) }
            return
        }
        val lost = challenge?.let { HeadToHead(result.timeMs, result.hintsUsed, it).outcome == Outcome.LOST } ?: false
        val now = clock.nowMillis()
        val afterAd = AdPolicy.adShownRecently(adHistory.lastShownAt.value, now)
        if (ReviewPolicy.shouldAsk(cleanSolves(p), result.hintsUsed, growth.reviewAskedAt(), now, lost, afterAd)) {
            _state.update { it.copy(askReview = true) }
        }
    }

    /** The player accepted the reminder offer; [granted] is the notification permission answer. */
    fun acceptReminder(granted: Boolean) {
        _state.update { it.copy(offerReminder = false) }
        if (granted) viewModelScope.launch { reminders.enable(_state.value.reminderMinutes) }
    }

    fun dismissReminder() = _state.update { it.copy(offerReminder = false) }

    /** The rating flow was launched: start the cooldown. */
    fun reviewRequested() {
        _state.update { it.copy(askReview = false) }
        viewModelScope.launch {
            growth.setReviewAskedAt(clock.nowMillis())
            analytics.log(Events.reviewPromptShown())
        }
    }

    private companion object {
        fun dailiesCompleted(p: Progress): Int =
            p.results.asSequence().filter { it.mode == GameRepository.MODE_DAILY }.map { it.gameKey }.distinct().count()

        /** Boards whose first solve used no hints. */
        fun cleanSolves(p: Progress): Int =
            p.results.groupBy { it.gameKey }.values.count { r -> r.minBy { it.completedAt }.hintsUsed == 0 }
    }
}

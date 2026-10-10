package app.bonedle.feature.play

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.data.Clock
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameRepository
import app.bonedle.data.game.GameSource
import app.bonedle.data.game.PuzzleRepository
import app.bonedle.data.game.SavedGame
import app.bonedle.data.settings.EntitlementRepository
import app.bonedle.data.settings.GrowthPreferences
import app.bonedle.data.settings.SettingsRepository
import app.bonedle.di.ApplicationScope
import app.bonedle.di.DefaultDispatcher
import app.bonedle.domain.Links
import app.bonedle.engine.game.Game
import app.bonedle.engine.game.Hint
import app.bonedle.engine.game.HintEngine
import app.bonedle.engine.game.MoveResult
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Direction
import app.bonedle.engine.model.Placement
import app.bonedle.engine.rules.RegionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Play screen logic. Rules live in the engine's [Game]; this class sequences intents, keeps
 * active play time (paused whenever the screen is not resumed), autosaves after every change
 * and records the result when the board is solved.
 */
@HiltViewModel
class PlayViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    private val puzzles: PuzzleRepository,
    private val games: GameRepository,
    settings: SettingsRepository,
    entitlements: EntitlementRepository,
    private val clock: Clock,
    @DefaultDispatcher private val compute: CoroutineDispatcher,
    @ApplicationScope private val appScope: CoroutineScope,
    private val analytics: Analytics,
    private val growth: GrowthPreferences,
) : ViewModel() {
    private val route: PlayRoute = savedState.toRoute()
    private val source = GameSource.fromCode(route.source)

    private val _state = MutableStateFlow(PlayUiState())
    val state: StateFlow<PlayUiState> = _state.asStateFlow()

    private val _effects = Channel<PlayEffect>(Channel.BUFFERED)
    val effects: Flow<PlayEffect> = _effects.receiveAsFlow()

    private var baseElapsed = 0L
    private var runningSince: Long? = null
    private var resumed = false
    private var undoCount = 0
    private var startedAt = 0L
    private var ticker: Job? = null
    private var pendingSave: Job? = null

    /** The board loaded was started now, not resumed from a save (`puzzle_start`). */
    private var startedNow = false

    /** Set the moment the board is solved; the recorded result arrives a little later. */
    private var finished = false

    init {
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(hideTimer = s.hideTimer, strict = s.strictMode, haptics = s.haptics, sound = s.sound) } }
        }
        viewModelScope.launch { entitlements.plus.collect { plus -> _state.update { it.copy(plus = plus) } } }
        load()
    }

    fun onIntent(intent: PlayIntent) {
        val s = _state.value
        if (intent == PlayIntent.Retry) {
            load()
            return
        }
        val game = s.game ?: return
        if (s.solved) return
        when (intent) {
            is PlayIntent.SelectTray -> select(intent.trayIndex)
            is PlayIntent.TapCell -> tapCell(game, intent.cell)
            PlayIntent.Rotate -> rotate(game)
            PlayIntent.Undo -> when (val r = game.undo()) {
                is MoveResult.Accepted -> {
                    undoCount++
                    commit(r.game, placed = null)
                }
                is MoveResult.Rejected -> reject(null)
            }
            PlayIntent.Hint -> hint(game)
            PlayIntent.RevealHint -> s.hint?.let { reveal(game, it.hint) }
            PlayIntent.DismissHint -> _state.update { it.copy(hint = null, outOfHints = false) }
            PlayIntent.GrantExtraHint -> {
                _state.update { it.copy(extraHints = it.extraHints + 1, outOfHints = false) }
                scheduleSave()
                hint(game)
            }
            PlayIntent.RequestRestart -> _state.update { it.copy(confirmRestart = true) }
            PlayIntent.CancelRestart -> _state.update { it.copy(confirmRestart = false) }
            PlayIntent.ConfirmRestart -> {
                _state.update { it.copy(confirmRestart = false, selected = null, anchors = emptyMap(), lastPlaced = null, poses = defaultPoses(game)) }
                commit(game.restart(), placed = null)
            }
            is PlayIntent.OpenLegend -> _state.update { it.copy(legendOpen = true, legendFocus = intent.region) }
            PlayIntent.CloseLegend -> _state.update { it.copy(legendOpen = false, legendFocus = null) }
            is PlayIntent.Drop -> drop(game, intent)
            is PlayIntent.ReturnToTray -> (game.remove(intent.trayIndex) as? MoveResult.Accepted)?.let { commit(it.game, placed = null) }
            PlayIntent.Retry -> Unit
        }
    }

    /** Screen became visible and interactive: the clock runs. */
    fun onResume() {
        resumed = true
        startClock()
    }

    /** Screen left the foreground: the clock stops and the board is saved now. */
    fun onPause() {
        resumed = false
        stopClock()
        saveNow()
    }

    override fun onCleared() {
        stopClock()
        saveNow()
        val s = _state.value
        val id = s.id
        val game = s.game
        if (id != null && game != null && id !is GameId.Tutorial && !s.solved && !finished) {
            analytics.log(Events.puzzleAbandon(id, source, baseElapsed, game.tray.size - game.unplaced.size))
        }
    }

    // ------------------------------------------------------------------ loading

    private fun load() {
        _state.update { it.copy(loading = true, failed = false) }
        viewModelScope.launch {
            val loaded = runCatching { resolve() }.getOrNull()
            if (loaded == null) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val (id, game, completion) = loaded
            savedState[KEY_RESOLVED] = id.key
            _state.update {
                it.copy(
                    loading = false,
                    id = id,
                    game = game,
                    poses = defaultPoses(game),
                    elapsedMs = baseElapsed,
                    completion = completion,
                )
            }
            if (completion == null) startClock()
            if (startedNow) analytics.log(Events.puzzleStart(id, source))
            if (id is GameId.Unlimited) puzzles.prefetch(id.difficulty)
        }
    }

    private data class Loaded(val id: GameId, val game: Game, val completion: Completion?)

    private suspend fun resolve(): Loaded {
        val key = savedState.get<String>(KEY_RESOLVED) ?: route.gameKey
        if (key == null) {
            val difficulty = Difficulty.fromCode(route.difficulty) ?: Difficulty.EASY
            val (id, puzzle) = puzzles.newUnlimited(difficulty)
            startedAt = clock.nowMillis()
            startedNow = true
            return Loaded(id, Game.start(puzzle), null)
        }
        val id = requireNotNull(GameId.parse(key)) { "bad game key $key" }
        if (id is GameId.Tutorial) return Loaded(id, Game.start(puzzles.puzzle(id)), null)
        games.load(id)?.let { save ->
            baseElapsed = save.elapsedMs
            undoCount = save.undoCount
            startedAt = save.startedAt
            _state.update { it.copy(hintsUsed = save.hintsUsed, extraHints = save.extraHints) }
            return Loaded(id, save.game, null)
        }
        val puzzle = puzzles.puzzle(id)
        val done = games.result(id)
        if (done != null) {
            // Already solved: show the finished board.
            var g = Game.start(puzzle)
            for (p in puzzle.solution) {
                val t = g.unplaced.first { g.tray[it] == p.domino }
                g = (g.place(t, p) as MoveResult.Accepted).game
            }
            baseElapsed = done.timeMs
            return Loaded(id, g, Completion(done))
        }
        startedAt = clock.nowMillis()
        startedNow = true
        return Loaded(id, Game.start(puzzle), null)
    }

    private fun defaultPoses(game: Game): Map<Int, Pose> =
        game.tray.indices.associateWith { t -> Pose(Direction.EAST, game.tray[t].low, game.tray[t].high) }

    // ------------------------------------------------------------------ moves

    private fun select(trayIndex: Int) {
        _state.update { it.copy(selected = if (it.selected == trayIndex) null else trayIndex) }
    }

    private fun tapCell(game: Game, cell: Int) {
        val s = _state.value
        val owner = game.ownerOf(cell)
        val selected = s.selected
        if (selected != null) {
            if (owner >= 0 && owner != selected) return reject(selected)
            val pose = s.poses.getValue(selected)
            // First half on the tapped cell; if the pose does not fit, turn until one does.
            for (step in 0 until 4) {
                val dir = (pose.dir + step) and 3
                val r = game.placeAt(selected, cell, dir, pose.firstValue, s.strict)
                if (r is MoveResult.Accepted) {
                    _state.update {
                        it.copy(
                            selected = null,
                            poses = it.poses + (selected to pose.copy(dir = dir)),
                            anchors = it.anchors + (selected to cell),
                        )
                    }
                    commit(r.game, placed = selected)
                    return
                }
            }
            return reject(selected)
        }
        if (owner >= 0) rotatePlaced(game, owner, cell)
    }

    private fun rotate(game: Game) {
        val s = _state.value
        val selected = s.selected
        if (selected != null) {
            _state.update { it.copy(poses = it.poses + (selected to it.poses.getValue(selected).rotated())) }
            return
        }
        val last = s.lastPlaced ?: return reject(null)
        val placement = game.placementOf(last) ?: return reject(null)
        rotatePlaced(game, last, s.anchors[last]?.takeIf { placement.covers(it) } ?: placement.cellA)
    }

    private fun rotatePlaced(game: Game, trayIndex: Int, anchor: Int) {
        when (val r = game.rotate(trayIndex, anchor, _state.value.strict)) {
            is MoveResult.Accepted -> {
                _state.update { it.copy(anchors = it.anchors + (trayIndex to anchor)) }
                commit(r.game, placed = trayIndex)
            }
            is MoveResult.Rejected -> reject(trayIndex)
        }
    }

    private fun drop(game: Game, d: PlayIntent.Drop) {
        val target = d.target
        if (target == null) {
            if (d.overBoard) return reject(d.trayIndex)
            if (d.fromBoard) (game.remove(d.trayIndex) as? MoveResult.Accepted)?.let { commit(it.game, placed = null) }
            return
        }
        when (val r = game.place(d.trayIndex, target, _state.value.strict)) {
            is MoveResult.Accepted -> {
                _state.update { it.copy(selected = null, anchors = it.anchors + (d.trayIndex to target.cellA)) }
                commit(r.game, placed = d.trayIndex)
            }
            is MoveResult.Rejected -> reject(d.trayIndex)
        }
    }

    private fun reject(trayIndex: Int?) {
        _effects.trySend(PlayEffect.Rejected(trayIndex))
    }

    /** Adopts [next] as the board: effects, hint invalidation, autosave, completion. */
    private fun commit(next: Game, placed: Int?) {
        val before = _state.value.game
        if (next === before) return
        val newlySatisfied = next.regionStatuses.indices.filter {
            next.regionStatuses[it] == RegionStatus.SATISFIED && before?.regionStatuses?.get(it) != RegionStatus.SATISFIED
        }
        _state.update {
            it.copy(
                game = next,
                hint = null,
                outOfHints = false,
                lastPlaced = placed ?: it.lastPlaced?.takeIf { t -> next.placementOf(t) != null },
            )
        }
        if (placed != null) _effects.trySend(PlayEffect.Snapped(placed))
        if (newlySatisfied.isNotEmpty()) _effects.trySend(PlayEffect.Satisfied(newlySatisfied))
        when {
            !next.isSolved -> scheduleSave()
            _state.value.tutorialStep != null -> {
                stopClock()
                _state.value.tutorialStep?.let { analytics.log(Events.tutorialStep(it + 1, route.source)) }
                _state.update { it.copy(tutorialDone = true, selected = null) }
                _effects.trySend(PlayEffect.Solved)
            }
            else -> complete(next)
        }
    }

    // ------------------------------------------------------------------ hints

    private fun hint(game: Game) {
        val s = _state.value
        val open = s.hint
        if (open != null) {
            val step = open.hint as? Hint.Step
            if (open.level < 2 && step?.deduction != null) {
                _state.value.id?.let { analytics.log(Events.hintUsed(it, level = 2)) }
                _state.update { it.copy(hint = open.copy(level = 2)) }
            } else {
                reveal(game, open.hint)
            }
            return
        }
        if (!s.hintAvailable) {
            _state.update { it.copy(outOfHints = true) }
            return
        }
        viewModelScope.launch {
            val next = withContext(compute) { HintEngine.next(game) }
            if (_state.value.game !== game) return@launch
            if (next == Hint.Solved) return@launch
            _state.value.id?.let { analytics.log(Events.hintUsed(it, level = 1)) }
            _state.update { it.copy(hint = HintUi(next, level = 1), hintsUsed = it.hintsUsed + 1, selected = null) }
            scheduleSave()
        }
    }

    private fun reveal(game: Game, hint: Hint) {
        val result = when (hint) {
            is Hint.WrongDomino -> game.remove(hint.trayIndex)
            is Hint.Step -> game.place(hint.reveal.trayIndex, hint.reveal.placement)
            Hint.Solved -> return
        }
        val placed = (hint as? Hint.Step)?.reveal?.trayIndex
        if (result is MoveResult.Accepted) _state.value.id?.let { analytics.log(Events.hintUsed(it, level = 3)) }
        (result as? MoveResult.Accepted)?.let {
            if (placed != null) _state.update { s -> s.copy(anchors = s.anchors + (placed to hint.reveal.placement.cellA)) }
            commit(it.game, placed = placed)
        }
    }

    // ------------------------------------------------------------------ clock and saving

    private fun startClock() {
        val s = _state.value
        if (!resumed || s.game == null || s.solved || runningSince != null) return
        runningSince = clock.elapsedRealtimeMillis()
        ticker = viewModelScope.launch {
            var sinceSave = 0
            while (isActive) {
                delay(TICK_MS)
                _state.update { it.copy(elapsedMs = elapsedNow()) }
                if (++sinceSave * TICK_MS >= PERIODIC_SAVE_MS) {
                    sinceSave = 0
                    scheduleSave()
                }
            }
        }
    }

    private fun stopClock() {
        val since = runningSince ?: return
        baseElapsed += clock.elapsedRealtimeMillis() - since
        runningSince = null
        ticker?.cancel()
        ticker = null
        _state.update { it.copy(elapsedMs = baseElapsed) }
    }

    private fun elapsedNow(): Long = baseElapsed + (runningSince?.let { clock.elapsedRealtimeMillis() - it } ?: 0L)

    private fun snapshot(): SavedGame? {
        val s = _state.value
        val id = s.id ?: return null
        val game = s.game ?: return null
        if (s.solved || id is GameId.Tutorial) return null
        return SavedGame(id, game, elapsedNow(), s.hintsUsed, undoCount, source, startedAt, s.extraHints)
    }

    private fun scheduleSave() {
        pendingSave?.cancel()
        pendingSave = viewModelScope.launch {
            delay(SAVE_DEBOUNCE_MS)
            snapshot()?.let { games.save(it) }
        }
    }

    /** Saves on the application scope so leaving the screen cannot cut the write short. */
    private fun saveNow() {
        pendingSave?.cancel()
        val snap = snapshot() ?: return
        appScope.launch { games.save(snap) }
    }

    private fun complete(game: Game) {
        finished = true
        stopClock()
        val s = _state.value
        val id = s.id ?: return
        val save = SavedGame(id, game, baseElapsed, s.hintsUsed, undoCount, source, startedAt, s.extraHints)
        pendingSave?.cancel()
        _effects.trySend(PlayEffect.Solved)
        analytics.log(Events.puzzleComplete(id, source, save.elapsedMs, save.hintsUsed, save.undoCount))
        appScope.launch {
            val result = games.complete(save)
            _state.update { it.copy(completion = Completion(result), selected = null, hint = null) }
            val boardId = Links.boardId(id)
            if (source == GameSource.LINK && growth.consumeReferredBoard(boardId)) {
                analytics.log(Events.referredPuzzleComplete(boardId, save.elapsedMs))
            }
        }
    }

    private companion object {
        const val KEY_RESOLVED = "resolvedGameKey"
        const val TICK_MS = 250L
        const val PERIODIC_SAVE_MS = 5_000L
        const val SAVE_DEBOUNCE_MS = 300L
    }
}

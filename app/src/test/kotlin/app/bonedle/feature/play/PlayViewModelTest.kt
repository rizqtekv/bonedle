package app.bonedle.feature.play

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.bonedle.data.Clock
import app.bonedle.data.db.BonedleDatabase
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameRepository
import app.bonedle.data.game.PuzzleBankSource
import app.bonedle.data.game.PuzzleRepository
import app.bonedle.data.settings.GrowthPreferences
import app.bonedle.data.settings.SettingsRepository
import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.game.Hint
import app.bonedle.engine.game.MoveResult
import app.bonedle.engine.rules.RegionStatus
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Direction
import app.bonedle.testing.RecordingAnalytics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class PlayViewModelTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: BonedleDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var growth: GrowthPreferences
    private val clock = FakeClock()
    private val analytics = RecordingAnalytics()

    /** Daily #1 Easy: generated deterministically, so every run plays the same board. */
    private val dailyId = GameId.Daily(1, Difficulty.EASY)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BonedleDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryCoroutineContext(dispatcher)
            .build()
        val store = PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + SupervisorJob())) { tmp.newFile("settings.preferences_pb") }
        settings = SettingsRepository(store)
        growth = GrowthPreferences(store)
    }

    @After
    fun tearDown() {
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(
        key: String? = dailyId.key,
        difficulty: String = "easy",
        handle: SavedStateHandle? = null,
        source: String = "home",
    ): Pair<PlayViewModel, SavedStateHandle> {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val h = handle ?: SavedStateHandle(mapOf("gameKey" to key, "difficulty" to difficulty, "source" to source))
        val vm = PlayViewModel(
            savedState = h,
            puzzles = PuzzleRepository(PuzzleBankSource(context), dispatcher, backgroundScope),
            games = GameRepository(db.games(), clock, dispatcher),
            settings = settings,
            clock = clock,
            compute = dispatcher,
            appScope = backgroundScope,
            analytics = analytics,
            growth = growth,
        )
        advanceUntilIdle()
        return vm to h
    }

    /**
     * Plays the solution through select, rotate and tap, the path a TalkBack user takes. A tray
     * domino is held by its low value, so the anchor is whichever cell shows the low half.
     */
    private fun PlayViewModel.playSolution(count: Int = Int.MAX_VALUE, scope: TestScope) {
        val solution = requireNotNull(state.value.game).puzzle.solution
        for (p in solution.take(count)) {
            val g = requireNotNull(state.value.game)
            val t = g.unplaced.first { g.tray[it] == p.domino }
            val lowOnA = p.valueA == g.tray[t].low
            val anchor = if (lowOnA) p.cellA else p.cellB
            val other = if (lowOnA) p.cellB else p.cellA
            val dir = g.puzzle.board.directionTo(anchor, other)
            onIntent(PlayIntent.SelectTray(t))
            while (state.value.poses[t]?.dir != dir) onIntent(PlayIntent.Rotate)
            onIntent(PlayIntent.TapCell(anchor))
            scope.runCurrent()
            check(state.value.game?.placementOf(t) == p) { "failed to place $p" }
        }
    }

    @Test
    fun solvingRecordsTheResultAndClearsTheSave() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        assertFalse(vm.state.value.loading)
        vm.playSolution(scope = this)
        advanceUntilIdle()
        assertTrue(requireNotNull(vm.state.value.game).isSolved)
        assertNotNull(vm.state.value.completion)
        assertNull(db.games().save(dailyId.key))
        assertNotNull(db.games().firstResult(dailyId.key))
    }

    @Test
    fun reopeningASolvedDailyShowsItSolved() = runTest(dispatcher) {
        val (first, _) = viewModel()
        first.playSolution(scope = this)
        advanceUntilIdle()
        val (again, _) = viewModel()
        assertNotNull(again.state.value.completion)
        assertTrue(requireNotNull(again.state.value.game).isSolved)
    }

    @Test
    fun boardAndHistorySurviveProcessDeath() = runTest(dispatcher) {
        val (vm, handle) = viewModel()
        vm.playSolution(count = 2, scope = this)
        vm.onIntent(PlayIntent.Undo)
        vm.onPause()
        advanceUntilIdle()
        val before = requireNotNull(vm.state.value.game)
        val (restored, _) = viewModel(handle = handle)
        val after = requireNotNull(restored.state.value.game)
        assertEquals(before.placements, after.placements)
        assertEquals(before.history, after.history)
        assertEquals(1, db.games().save(dailyId.key)?.undoCount)
    }

    @Test
    fun newUnlimitedBoardKeepsItsSeedAcrossProcessDeath() = runTest(dispatcher) {
        val (vm, handle) = viewModel(key = null, difficulty = "medium")
        val id = vm.state.value.id as GameId.Unlimited
        val (restored, _) = viewModel(handle = handle)
        assertEquals(id, restored.state.value.id)
        assertEquals(vm.state.value.game?.puzzle, restored.state.value.game?.puzzle)
    }

    @Test
    fun theClockCountsOnlyActiveTime() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.onResume()
        clock.elapsed += 5_000
        advanceTimeBy(5_000)
        vm.onPause()
        clock.elapsed += 60_000 // backgrounded: must not count
        advanceTimeBy(60_000)
        vm.onResume()
        clock.elapsed += 2_000
        advanceTimeBy(2_000)
        vm.onPause()
        runCurrent()
        assertEquals(7_000, vm.state.value.elapsedMs)
    }

    @Test
    fun hintsEscalateRevealAndRunOut() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.onIntent(PlayIntent.Hint)
        advanceUntilIdle()
        val first = requireNotNull(vm.state.value.hint)
        assertEquals(1, first.level)
        assertEquals(1, vm.state.value.hintsUsed)
        val placedBefore = requireNotNull(vm.state.value.game).placements.size
        vm.onIntent(PlayIntent.Hint) // level 2 (explanation) or straight to the reveal
        vm.onIntent(PlayIntent.RevealHint)
        advanceUntilIdle()
        assertNull(vm.state.value.hint)
        assertEquals(placedBefore + 1, vm.state.value.game?.placements?.size)
        vm.onIntent(PlayIntent.Hint)
        advanceUntilIdle()
        vm.onIntent(PlayIntent.DismissHint)
        vm.onIntent(PlayIntent.Hint)
        advanceUntilIdle()
        assertTrue(vm.state.value.outOfHints)
        assertEquals(PlayUiState.FREE_HINTS, vm.state.value.hintsUsed)
    }

    @Test
    fun aWrongDominoIsTheFirstThingAHintPointsAt() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        val g = requireNotNull(vm.state.value.game)
        val wrong = g.puzzle.solution.first()
        val t = g.unplaced.first { g.tray[it] != wrong.domino }
        vm.onIntent(PlayIntent.SelectTray(t))
        vm.onIntent(PlayIntent.TapCell(wrong.cellA))
        runCurrent()
        vm.onIntent(PlayIntent.Hint)
        advanceUntilIdle()
        assertTrue(vm.state.value.hint?.hint is Hint.WrongDomino)
    }

    @Test
    fun strictModeRefusesBrokenPlacements() = runTest(dispatcher) {
        settings.setStrictMode(true)
        val (vm, _) = viewModel()
        assertTrue(vm.state.value.strict)
        val g = requireNotNull(vm.state.value.game)
        val (t, breaking) = g.tray.indices.asSequence().flatMap { t ->
            (0 until g.puzzle.board.cellCount).asSequence().flatMap { c -> (0 until 4).asSequence().map { dir -> Triple(t, c, dir) } }
        }.mapNotNull { (t, c, dir) ->
            val r = g.placeAt(t, c, dir, g.tray[t].high) as? MoveResult.Accepted ?: return@mapNotNull null
            if (r.game.regionStatuses.any { it == RegionStatus.BROKEN }) t to requireNotNull(r.game.placementOf(t)) else null
        }.first()
        vm.onIntent(PlayIntent.Drop(t, breaking, overBoard = true, fromBoard = false))
        runCurrent()
        assertNull(vm.state.value.game?.placementOf(t))
    }

    @Test
    fun restartNeedsConfirmation() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.playSolution(count = 1, scope = this)
        vm.onIntent(PlayIntent.RequestRestart)
        assertTrue(vm.state.value.confirmRestart)
        assertEquals(1, vm.state.value.game?.placements?.size)
        vm.onIntent(PlayIntent.ConfirmRestart)
        assertEquals(0, vm.state.value.game?.placements?.size)
        assertFalse(vm.state.value.confirmRestart)
    }

    @Test
    fun selectingTwiceDeselectsAndRotateTurnsTheHeldDomino() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.onIntent(PlayIntent.SelectTray(0))
        assertEquals(0, vm.state.value.selected)
        assertEquals(Direction.EAST, vm.state.value.poses[0]?.dir)
        vm.onIntent(PlayIntent.Rotate)
        assertEquals(Direction.SOUTH, vm.state.value.poses[0]?.dir)
        vm.onIntent(PlayIntent.SelectTray(0))
        assertNull(vm.state.value.selected)
    }

    @Test
    fun tutorialBoardsAreNeverSavedAndFinishLocally() = runTest(dispatcher) {
        val (vm, _) = viewModel(key = GameId.Tutorial(1).key)
        assertEquals(1, vm.state.value.tutorialStep)
        vm.playSolution(scope = this)
        vm.onPause()
        advanceUntilIdle()
        assertTrue(vm.state.value.tutorialDone)
        assertNull(vm.state.value.completion)
        assertNull(db.games().save(GameId.Tutorial(1).key))
        assertNull(db.games().firstResult(GameId.Tutorial(1).key))
    }

    @Test
    fun analyticsFollowAPlaythrough() = runTest(dispatcher) {
        val (vm, handle) = viewModel()
        assertEquals(1, analytics.named("puzzle_start").size)
        val start = analytics.named("puzzle_start").single().params
        assertEquals("daily", start["mode"])
        assertEquals("easy", start["difficulty"])
        assertEquals("home", start["source"])
        assertEquals("d/1/easy", start["board_id"])
        vm.onIntent(PlayIntent.Hint)
        advanceUntilIdle()
        assertEquals(listOf(1L), analytics.named("hint_used").map { it.params["level"] })
        vm.onPause()
        advanceUntilIdle()
        // Resuming the save is not a new start.
        val (resumed, _) = viewModel(handle = handle)
        assertEquals(1, analytics.named("puzzle_start").size)
        resumed.onIntent(PlayIntent.DismissHint)
        resumed.playSolution(scope = this)
        advanceUntilIdle()
        val complete = analytics.named("puzzle_complete").single().params
        assertEquals(1L, complete["hints"])
        assertEquals("home", complete["source"])
        assertTrue(analytics.named("referred_puzzle_complete").isEmpty())
    }

    @Test
    fun leavingAnUnfinishedBoardLogsAnAbandonButSolvingDoesNot() = runTest(dispatcher) {
        val (vm, _) = viewModel()
        vm.playSolution(count = 2, scope = this)
        clear(vm)
        val abandon = analytics.named("puzzle_abandon").single().params
        assertEquals(2L, abandon["placed"])
        val (solved, _) = viewModel(key = GameId.Daily(1, Difficulty.MEDIUM).key, difficulty = "medium")
        solved.playSolution(scope = this)
        clear(solved)
        advanceUntilIdle()
        assertEquals(1, analytics.named("puzzle_abandon").size)
    }

    @Test
    fun theBoardAReferredPlayerWasInvitedToLogsOnce() = runTest(dispatcher) {
        growth.setReferredBoard("d/1/easy")
        val (vm, _) = viewModel(source = "link")
        assertEquals("link", analytics.named("puzzle_start").single().params["source"])
        vm.playSolution(scope = this)
        advanceUntilIdle()
        assertEquals("d/1/easy", analytics.named("referred_puzzle_complete").single().params["board_id"])
        assertFalse(growth.consumeReferredBoard("d/1/easy"))
    }

    @Test
    fun tutorialStepsAreLoggedButNeverStartPuzzles() = runTest(dispatcher) {
        val (vm, _) = viewModel(key = GameId.Tutorial(2).key)
        vm.playSolution(scope = this)
        advanceUntilIdle()
        assertTrue(analytics.named("puzzle_start").isEmpty())
        assertEquals(3L, analytics.named("tutorial_step").single().params["step"])
    }

    /** Clears [vm] the way leaving the screen does. */
    private fun clear(vm: PlayViewModel) {
        val store = ViewModelStore()
        ViewModelProvider.create(
            store,
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T = vm as T
            },
        )[PlayViewModel::class.java]
        store.clear()
    }

    private class FakeClock : Clock {
        var elapsed = 0L

        override fun nowMillis(): Long = 1_800_000_000_000L + elapsed

        override fun elapsedRealtimeMillis(): Long = elapsed

        override fun today(): CalendarDate = CalendarDate(2026, 11, 2)
    }
}

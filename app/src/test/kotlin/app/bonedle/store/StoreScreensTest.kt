package app.bonedle.store

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import app.bonedle.data.db.GameResultEntity
import app.bonedle.data.db.SavedGameEntity
import app.bonedle.data.db.StreakFreezeEntity
import app.bonedle.data.game.BoardStatus
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameResult
import app.bonedle.data.game.Progress
import app.bonedle.domain.Challenge
import app.bonedle.domain.StatsCalculator
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.daily.Streaks
import app.bonedle.engine.game.Game
import app.bonedle.engine.game.HintEngine
import app.bonedle.engine.game.MoveResult
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Direction
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.tutorial.TutorialBoards
import app.bonedle.feature.home.DailyRow
import app.bonedle.feature.home.HomeActions
import app.bonedle.feature.home.HomeScreen
import app.bonedle.feature.home.HomeUiState
import app.bonedle.feature.play.HintUi
import app.bonedle.feature.play.PlayScreen
import app.bonedle.feature.play.PlayUiState
import app.bonedle.feature.play.Pose
import app.bonedle.feature.solved.SolvedActions
import app.bonedle.feature.solved.SolvedScreen
import app.bonedle.feature.solved.SolvedUiState
import app.bonedle.feature.stats.StatsScreen
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Real app screens in staged states for the Play Store listing (spec 12), captured at phone
 * resolution into art/store/screens. art/store/compose.py frames them at 1080 x 1920 with a
 * benefit headline each. Every state here is one a player can reach.
 */
object StoreScenes {
    /** A player 40 days in, on daily #40. */
    private const val NUMBER = 40
    private val today = DailySchedule.dateOf(NUMBER)

    private fun Game.placed(p: Placement): Game {
        val t = unplaced.first { tray[it] == p.domino }
        return (place(t, p) as MoveResult.Accepted).game
    }

    private fun midGame(puzzle: Puzzle, fraction: Double): Game {
        var g = Game.start(puzzle)
        for (p in puzzle.solution.take((puzzle.solution.size * fraction).toInt())) g = g.placed(p)
        return g
    }

    private fun play(id: GameId, game: Game, elapsedMs: Long, hintsUsed: Int = 0) = PlayUiState(
        loading = false,
        id = id,
        game = game,
        poses = game.tray.indices.associateWith { Pose(Direction.EAST, game.tray[it].low, game.tray[it].high) },
        elapsedMs = elapsedMs,
        hintsUsed = hintsUsed,
    )

    private fun result(n: Int, d: Difficulty, timeMs: Long, hints: Int = 0) = GameResultEntity(
        gameKey = GameId.Daily(n, d).key, mode = "daily", difficulty = d.code, dailyNumber = n, seed = 0, timeMs = timeMs,
        hintsUsed = hints, undoCount = 0, source = "home", completedAt = n * 10L + d.ordinal,
        localDate = DailySchedule.dateOf(n).toIsoString(),
    )

    /** Most days played, a few skipped, one bridged by a freeze; a 23-day streak to today. */
    private val progress: Progress by lazy {
        val skipped = setOf(3, 9, 12, 17)
        val results = (1..NUMBER).filter { it !in skipped }.flatMap { n ->
            buildList {
                add(result(n, Difficulty.EASY, 48_000L + (n * 7_919L) % 40_000))
                if (n % 3 != 1) add(result(n, Difficulty.MEDIUM, 120_000L + (n * 5_003L) % 90_000, hints = if (n % 7 == 0) 1 else 0))
                if (n % 4 == 0 && n != NUMBER) add(result(n, Difficulty.HARD, 300_000L + (n * 3_001L) % 160_000))
            }
        }
        val freezeDay = DailySchedule.dateOf(17).epochDay
        val saves = listOf(SavedGameEntity(GameId.Daily(NUMBER, Difficulty.MEDIUM).key, "", "", 74_000, 0, 0, "home", 0, 0))
        Progress(results, saves, listOf(StreakFreezeEntity(freezeDay, Streaks.weekStart(freezeDay), Progress.FREEZE_FREE, 0)))
    }

    private val dailyMedium by lazy { DailySchedule.puzzle(NUMBER, Difficulty.MEDIUM) }
    private val dailyHard by lazy { DailySchedule.puzzle(NUMBER, Difficulty.HARD) }
    private val expert by lazy { PuzzleGenerator.generate(20_261_102L, Difficulty.EXPERT) }

    private val noHome = HomeActions({ _, _ -> }, { _, _ -> }, {}, {}, {}, {})

    /** (file name, theme, screen). */
    val all: List<Triple<String, ThemeMode, @Composable () -> Unit>> = listOf(
        Triple("01-play", ThemeMode.DARK) {
            val id = GameId.Daily(NUMBER, Difficulty.MEDIUM)
            PlayScreen(play(id, midGame(dailyMedium, 0.45), 74_000), onIntent = {}, onBack = {}, onSolved = {})
        },
        Triple("02-home", ThemeMode.DARK) {
            HomeScreen(
                HomeUiState(
                    loading = false,
                    today = today,
                    number = NUMBER,
                    dailies = Difficulty.DAILY.map { d -> GameId.Daily(NUMBER, d).let { DailyRow(it, progress.status(it.key)) } },
                    streak = progress.streak(today.epochDay).current,
                    unlimitedDifficulty = Difficulty.HARD,
                ),
                noHome,
            )
        },
        Triple("03-rules", ThemeMode.DARK) {
            val id = GameId.Daily(NUMBER, Difficulty.HARD)
            PlayScreen(play(id, midGame(dailyHard, 0.2), 95_000).copy(legendOpen = true, legendFocus = 0), onIntent = {}, onBack = {}, onSolved = {})
        },
        Triple("04-hints", ThemeMode.DARK) {
            val id = GameId.Daily(NUMBER, Difficulty.MEDIUM)
            val g = midGame(dailyMedium, 0.3)
            PlayScreen(play(id, g, 101_000, hintsUsed = 1).copy(hint = HintUi(HintEngine.next(g), level = 2)), onIntent = {}, onBack = {}, onSolved = {})
        },
        Triple("05-unlimited", ThemeMode.LIGHT) {
            val id = GameId.Unlimited(expert.seed, Difficulty.EXPERT)
            PlayScreen(play(id, midGame(expert, 0.35), 412_000), onIntent = {}, onBack = {}, onSolved = {})
        },
        Triple("06-head-to-head", ThemeMode.DARK) {
            val id = GameId.Daily(NUMBER, Difficulty.HARD)
            SolvedScreen(
                SolvedUiState(
                    id,
                    GameResult(id, 263_000, 0, 3, 0, today.toIsoString()),
                    dailyHard,
                    streak = progress.streak(today.epochDay).current,
                    challenge = Challenge(timeMs = 318_000, hintsUsed = 1),
                ),
                SolvedActions(onHome = {}, onNext = {}),
            )
        },
        Triple("07-stats", ThemeMode.DARK) { StatsScreen(StatsCalculator.compute(progress, today), onTab = {}) },
        Triple("08-learn", ThemeMode.LIGHT) {
            val t = TutorialBoards.puzzle(2)
            PlayScreen(play(GameId.Tutorial(2), midGame(t, 0.5), 0), onIntent = {}, onBack = {}, onSolved = {})
        },
    )
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class StoreScreensTest(private val scene: Int) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun capture() {
        val (name, mode, content) = StoreScenes.all[scene]
        compose.setContent { BonedleTheme(mode = mode, reducedMotion = true) { content() } }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../art/store/screens/$name.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun scenes(): List<Array<Any>> = StoreScenes.all.indices.map { arrayOf(it) }
    }
}

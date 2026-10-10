package app.bonedle.feature

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.bonedle.data.db.GameResultEntity
import app.bonedle.data.db.SavedGameEntity
import app.bonedle.data.game.BoardStatus
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameResult
import app.bonedle.data.game.Progress
import app.bonedle.data.settings.Settings
import app.bonedle.domain.StatsCalculator
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import app.bonedle.feature.archive.ArchiveEntry
import app.bonedle.feature.archive.ArchiveScreen
import app.bonedle.feature.archive.ArchiveUiState
import app.bonedle.feature.home.DailyRow
import app.bonedle.feature.home.HomeActions
import app.bonedle.feature.home.HomeScreen
import app.bonedle.feature.home.HomeUiState
import app.bonedle.feature.settings.SettingsActions
import app.bonedle.feature.settings.SettingsScreen
import app.bonedle.domain.Challenge
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

/** Deterministic states for every shell screen: a player 18 days in. */
object ShellScenes {
    private const val NUMBER = 18
    private val today = DailySchedule.dateOf(NUMBER)

    private fun daily(n: Int, d: Difficulty, timeMs: Long) = GameResultEntity(
        gameKey = GameId.Daily(n, d).key, mode = "daily", difficulty = d.code, dailyNumber = n, seed = 0, timeMs = timeMs,
        hintsUsed = if (n % 4 == 0) 1 else 0, undoCount = 0, source = "home", completedAt = n * 10L + d.ordinal,
        localDate = DailySchedule.dateOf(n).toIsoString(),
    )

    private val progress: Progress by lazy {
        val results = (6..NUMBER).flatMap { n ->
            buildList {
                add(daily(n, Difficulty.EASY, 50_000L + n * 1_000))
                if (n % 2 == 0) add(daily(n, Difficulty.MEDIUM, 140_000L + n * 3_000))
                if (n % 5 == 0 && n != NUMBER) add(daily(n, Difficulty.HARD, 320_000))
            }
        }
        val saves = listOf(SavedGameEntity(GameId.Daily(NUMBER, Difficulty.HARD).key, "", "", 95_000, 0, 0, "home", 0, 0))
        Progress(results, saves, emptyList())
    }

    private val home = HomeActions({ _, _ -> }, { _, _ -> }, {}, {}, {}, {})
    private val solvedActions = SolvedActions(onHome = {}, onNext = {})

    val all: List<Pair<String, @Composable () -> Unit>> = listOf(
        "home" to {
            HomeScreen(
                HomeUiState(
                    loading = false,
                    today = today,
                    number = NUMBER,
                    dailies = Difficulty.DAILY.map { d -> GameId.Daily(NUMBER, d).let { DailyRow(it, progress.status(it.key)) } },
                    streak = progress.streak(today.epochDay).current,
                    unlimitedDifficulty = Difficulty.HARD,
                ),
                home,
            )
        },
        "solved" to {
            val id = GameId.Daily(NUMBER, Difficulty.MEDIUM)
            SolvedScreen(
                SolvedUiState(id, GameResult(id, 134_000, 0, 2, 0, today.toIsoString()), DailySchedule.puzzle(NUMBER, Difficulty.MEDIUM), streak = 13),
                solvedActions,
            )
        },
        "solved-h2h" to {
            val id = GameId.Daily(NUMBER, Difficulty.HARD)
            SolvedScreen(
                SolvedUiState(
                    id,
                    GameResult(id, 251_000, 1, 4, 0, today.toIsoString()),
                    DailySchedule.puzzle(NUMBER, Difficulty.HARD),
                    streak = 13,
                    challenge = Challenge(timeMs = 298_000, hintsUsed = 0),
                ),
                solvedActions,
            )
        },
        "solved-reminder" to {
            val id = GameId.Daily(NUMBER, Difficulty.EASY)
            SolvedScreen(
                SolvedUiState(
                    id,
                    GameResult(id, 68_000, 0, 0, 0, today.toIsoString()),
                    DailySchedule.puzzle(NUMBER, Difficulty.EASY),
                    streak = 13,
                    offerReminder = true,
                ),
                solvedActions,
            )
        },
        "stats" to { StatsScreen(StatsCalculator.compute(progress, today), onTab = {}) },
        "archive" to {
            ArchiveScreen(
                ArchiveUiState(
                    (NUMBER - 1 downTo 1).map { n ->
                        ArchiveEntry(
                            number = n,
                            date = DailySchedule.dateOf(n),
                            boards = Difficulty.DAILY.map { d -> GameId.Daily(n, d).let { it to (if (n == 9 && d == Difficulty.HARD) BoardStatus.InProgress(40_000) else progress.status(it.key)) } },
                            locked = n < 3,
                        )
                    },
                    loading = false,
                ),
                onOpen = { _, _ -> },
                onTab = {},
            )
        },
        "settings" to { SettingsScreen(Settings(hideTimer = true), SettingsActions({}, {}, {}, {}, {}, {}, {}, {})) },
        "settings-reminder" to {
            SettingsScreen(Settings(reminderEnabled = true, reminderMinutes = 19 * 60 + 30), SettingsActions({}, {}, {}, {}, {}, {}, {}, {}))
        },
    )
}

abstract class ShellScreenshots(private val scene: Int, private val mode: ThemeMode, private val fontScale: Float, private val variant: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun scene() {
        val (name, content) = ShellScenes.all[scene]
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BonedleTheme(mode = mode, reducedMotion = true) { content() }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$variant/shell-$name.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun scenes(): List<Array<Any>> = ShellScenes.all.indices.map { arrayOf(it) }
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class ShellDarkScreenshotTest(scene: Int) : ShellScreenshots(scene, ThemeMode.DARK, 1f, "dark")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class ShellLightScreenshotTest(scene: Int) : ShellScreenshots(scene, ThemeMode.LIGHT, 1f, "light")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class ShellLargeFontScreenshotTest(scene: Int) : ShellScreenshots(scene, ThemeMode.DARK, 2f, "font200")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "w393dp-h852dp-xxhdpi")
class ShellApi29ScreenshotTest(scene: Int) : ShellScreenshots(scene, ThemeMode.DARK, 1f, "api29")

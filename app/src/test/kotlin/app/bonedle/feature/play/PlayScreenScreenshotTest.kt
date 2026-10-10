package app.bonedle.feature.play

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.Density
import app.bonedle.ads.LocalAds
import app.bonedle.data.game.GameId
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.game.Game
import app.bonedle.engine.game.HintEngine
import app.bonedle.engine.game.MoveResult
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Direction
import app.bonedle.engine.model.Placement
import app.bonedle.engine.tutorial.TutorialBoards
import app.bonedle.testing.ReadyAds
import app.bonedle.ui.theme.BonedleTheme
import app.bonedle.ui.theme.ThemeMode
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Play-screen scenes rendered for screenshots: deterministic states of daily #1 Medium. */
object PlayScenes {
    private val id = GameId.Daily(1, Difficulty.MEDIUM)
    private val puzzle by lazy { DailySchedule.puzzle(1, Difficulty.MEDIUM) }

    private fun Game.place(p: Placement): Game {
        val t = unplaced.first { tray[it] == p.domino }
        return (place(t, p) as MoveResult.Accepted).game
    }

    /** A third of the solution placed, plus one domino flipped so a region shows as broken. */
    private val inProgress: Game by lazy {
        var g = Game.start(puzzle)
        val sol = puzzle.solution
        for (p in sol.take(sol.size / 3)) g = g.place(p)
        val flip = sol[sol.size / 3]
        g.place(Placement(flip.cellA, flip.cellB, flip.valueB, flip.valueA)).takeIf { it.regionStatuses.any { s -> s.name == "BROKEN" } } ?: g
    }

    private fun base(game: Game) = PlayUiState(
        loading = false,
        id = id,
        game = game,
        poses = game.tray.indices.associateWith { Pose(Direction.EAST, game.tray[it].low, game.tray[it].high) },
        elapsedMs = 134_000,
        hintsUsed = 1,
    )

    val all: List<Pair<String, () -> PlayUiState>> = listOf(
        "playing" to {
            val s = base(inProgress)
            val selected = inProgress.unplaced.first()
            s.copy(selected = selected, poses = s.poses + (selected to s.poses.getValue(selected).rotated()), lastPlaced = 0)
        },
        "hint" to {
            var g = Game.start(puzzle)
            for (p in puzzle.solution.take(2)) g = g.place(p)
            base(g).copy(hint = HintUi(HintEngine.next(g), level = 2))
        },
        "legend" to { base(inProgress).copy(legendOpen = true, legendFocus = 0) },
        "out-of-hints" to {
            val unlimited = GameId.Unlimited(puzzle.seed, Difficulty.MEDIUM)
            base(Game.start(puzzle)).copy(id = unlimited, hintsUsed = 2, outOfHints = true)
        },
        "tutorial" to {
            val t = TutorialBoards.puzzle(1)
            var g = Game.start(t)
            g = g.place(t.solution.first())
            base(g).copy(id = GameId.Tutorial(1))
        },
    )
}

abstract class PlayScreenshots(private val scene: Int, private val mode: ThemeMode, private val fontScale: Float, private val variant: String) {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun scene() {
        val (name, state) = PlayScenes.all[scene]
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                BonedleTheme(mode = mode, reducedMotion = true) {
                    CompositionLocalProvider(LocalAds provides ReadyAds) {
                        PlayScreen(state = state(), onIntent = {}, onBack = {}, onSolved = {}, onPlus = {})
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("src/test/screenshots/$variant/play-$name.png")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun scenes(): List<Array<Any>> = PlayScenes.all.indices.map { arrayOf(it) }
    }
}

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class PlayDarkScreenshotTest(scene: Int) : PlayScreenshots(scene, ThemeMode.DARK, 1f, "dark")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class PlayLightScreenshotTest(scene: Int) : PlayScreenshots(scene, ThemeMode.LIGHT, 1f, "light")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w393dp-h852dp-xxhdpi")
class PlayLargeFontScreenshotTest(scene: Int) : PlayScreenshots(scene, ThemeMode.DARK, 2f, "font200")

@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "w393dp-h852dp-xxhdpi")
class PlayApi29ScreenshotTest(scene: Int) : PlayScreenshots(scene, ThemeMode.DARK, 1f, "api29")

package app.bonedle.feature.play

import androidx.compose.runtime.Immutable
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameResult
import app.bonedle.engine.game.Game
import app.bonedle.engine.game.Hint
import app.bonedle.engine.model.Direction
import app.bonedle.engine.model.Placement
import kotlinx.serialization.Serializable

/**
 * Navigation route for the Play screen. [gameKey] null means "a new Unlimited board at
 * [difficulty]"; once generated, its key is kept in the saved state so process death restores
 * the same board.
 */
@Serializable
data class PlayRoute(val gameKey: String? = null, val difficulty: String = "easy", val source: String = "home")

/**
 * How a domino not yet on the board is held: the direction from its first half to its second,
 * and the first half's value. Tapping a cell puts the first half there.
 */
@Immutable
data class Pose(val dir: Int, val firstValue: Int, val secondValue: Int) {
    val isHorizontal: Boolean get() = dir == Direction.EAST || dir == Direction.WEST

    /** Value in the left (horizontal) or top (vertical) half, as drawn. */
    val leadingValue: Int get() = if (dir == Direction.EAST || dir == Direction.SOUTH) firstValue else secondValue

    /** Value in the right or bottom half, as drawn. */
    val trailingValue: Int get() = if (dir == Direction.EAST || dir == Direction.SOUTH) secondValue else firstValue

    /** A quarter turn clockwise. */
    fun rotated(): Pose = copy(dir = (dir + 1) and 3)
}

/** What the player can do on the Play screen. */
sealed interface PlayIntent {
    data class SelectTray(val trayIndex: Int) : PlayIntent

    /** A tap on a cell: places the selected domino there, or rotates the domino on it. */
    data class TapCell(val cell: Int) : PlayIntent

    /** Rotates the selected tray domino, or the last domino placed. */
    data object Rotate : PlayIntent

    data object Undo : PlayIntent

    /** Starts a hint, or escalates the open one by a level. */
    data object Hint : PlayIntent

    /** Goes straight to the hint's last level (place / remove the domino). */
    data object RevealHint : PlayIntent

    data object DismissHint : PlayIntent

    /** The player watched the rewarded ad: one more hint on this board, shown at once. */
    data object GrantExtraHint : PlayIntent

    data object RequestRestart : PlayIntent

    data object ConfirmRestart : PlayIntent

    data object CancelRestart : PlayIntent

    /** Opens the rule legend, scrolled to [region] if given. */
    data class OpenLegend(val region: Int?) : PlayIntent

    data object CloseLegend : PlayIntent

    /**
     * End of a drag. [target] is where the domino snapped (cells and values, first half on
     * [Placement.cellA]), or null; [overBoard] tells a miss on the board (a shake) from a drop
     * elsewhere (back to the tray).
     */
    data class Drop(val trayIndex: Int, val target: Placement?, val overBoard: Boolean, val fromBoard: Boolean) : PlayIntent

    /** Sends a placed domino back to the tray (TalkBack action). */
    data class ReturnToTray(val trayIndex: Int) : PlayIntent

    data object Retry : PlayIntent
}

/** An open hint at [level] 1 (highlight) or 2 (explanation). Level 3 acts and closes it. */
@Immutable
data class HintUi(val hint: Hint, val level: Int)

/** Shown once the board is solved. */
@Immutable
data class Completion(val result: GameResult)

/** Immutable state of the Play screen. */
@Immutable
data class PlayUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val id: GameId? = null,
    val game: Game? = null,
    /** Pose of every tray domino, by tray index. */
    val poses: Map<Int, Pose> = emptyMap(),
    val selected: Int? = null,
    /** For each placed domino, the half it turns around when tapped. */
    val anchors: Map<Int, Int> = emptyMap(),
    val lastPlaced: Int? = null,
    val elapsedMs: Long = 0,
    val hideTimer: Boolean = false,
    val haptics: Boolean = true,
    val sound: Boolean = true,
    val strict: Boolean = false,
    val hintsUsed: Int = 0,
    /** Hints earned with rewarded ads on this board. */
    val extraHints: Int = 0,
    /** Bonedle Plus: unlimited hints. */
    val plus: Boolean = false,
    val hint: HintUi? = null,
    /** The player asked for a hint with none left. */
    val outOfHints: Boolean = false,
    val legendOpen: Boolean = false,
    val legendFocus: Int? = null,
    val confirmRestart: Boolean = false,
    val completion: Completion? = null,
    /** A tutorial step was completed (tutorial boards are never recorded). */
    val tutorialDone: Boolean = false,
) {
    /** The tutorial step on screen, or null for a real board. */
    val tutorialStep: Int? get() = (id as? GameId.Tutorial)?.step

    /** True once the board is solved, whether recorded or a tutorial step. */
    val solved: Boolean get() = completion != null || tutorialDone

    val freeHintsLeft: Int get() = (FREE_HINTS + extraHints - hintsUsed).coerceAtLeast(0)

    /** A hint can be asked for now (Plus never runs out). */
    val hintAvailable: Boolean get() = plus || freeHintsLeft > 0

    val canUndo: Boolean get() = game?.canUndo == true && !solved

    companion object {
        /** Free hints per puzzle (spec 6). */
        const val FREE_HINTS = 2
    }
}

/** One-shot feedback the screen plays once. */
sealed interface PlayEffect {
    /** A move was refused: shake the domino ([trayIndex]) and play the error haptic. */
    data class Rejected(val trayIndex: Int?) : PlayEffect

    /** A domino settled on the board: spring it in and play a light haptic. */
    data class Snapped(val trayIndex: Int) : PlayEffect

    /** These regions just became satisfied: pulse their outlines. */
    data class Satisfied(val regions: List<Int>) : PlayEffect

    /** The board was solved. */
    data object Solved : PlayEffect
}

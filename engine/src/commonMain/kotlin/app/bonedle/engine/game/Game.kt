package app.bonedle.engine.game

import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.rules.RegionStatus
import app.bonedle.engine.rules.RuleCheck

/** Why a move was refused. */
enum class MoveError {
    /** The two cells are not neighbours, or not on the board. */
    NOT_ADJACENT,

    /** Another domino already covers one of the cells. */
    OCCUPIED,

    /** The values do not match the tray domino being placed. */
    WRONG_DOMINO,

    /** Strict mode: the placement would break a region's rule. */
    BREAKS_RULE,

    /** The domino is not on the board. */
    NOT_PLACED,

    /** No neighbour is free to rotate into. */
    NO_ROOM,

    /** The undo history is empty. */
    NOTHING_TO_UNDO,
}

/** Outcome of a move: the new game, or why it was refused (the game is unchanged). */
sealed interface MoveResult {
    data class Accepted(val game: Game) : MoveResult

    data class Rejected(val error: MoveError) : MoveResult
}

/** One change to a tray domino's position; null means "in the tray". Undo reverses it. */
data class Move(val trayIndex: Int, val before: Placement?, val after: Placement?)

/**
 * The player's board: where each tray domino lies (by tray index) and the full undo history.
 * Immutable; every move returns a new [Game]. All rules of play live here so the UI only
 * renders state and forwards intents.
 *
 * Placements are kept in canonical form ([Placement.cellA] < [Placement.cellB]).
 */
class Game private constructor(
    val puzzle: Puzzle,
    private val slots: List<Placement?>,
    /** Moves in order, oldest first; [undo] pops the last. */
    val history: List<Move>,
) {
    private val owner: IntArray = IntArray(puzzle.board.cellCount) { -1 }.also { owner ->
        slots.forEachIndexed { t, p ->
            if (p != null) {
                owner[p.cellA] = t
                owner[p.cellB] = t
            }
        }
    }

    /** Value showing on every cell, or [RuleCheck.EMPTY]. */
    val cellValues: IntArray = RuleCheck.cellValues(puzzle.board, slots.filterNotNull())

    /** Dominoes in the tray, in display order; the index is the domino's identity. */
    val tray: List<Domino> get() = puzzle.tray

    /** Where tray domino [trayIndex] lies, or null if it is still in the tray. */
    fun placementOf(trayIndex: Int): Placement? = slots[trayIndex]

    /** Tray index of the domino covering [cell], or -1. */
    fun ownerOf(cell: Int): Int = owner[cell]

    /** Every domino on the board. */
    val placements: List<Placement> get() = slots.filterNotNull()

    /** Tray indices of the dominoes not yet on the board. */
    val unplaced: List<Int> get() = slots.indices.filter { slots[it] == null }

    /** State of every region, in [Puzzle.regions] order. */
    val regionStatuses: List<RegionStatus> = puzzle.regions.map { RuleCheck.status(it, cellValues) }

    /** True when every domino is on the board. */
    val isComplete: Boolean get() = slots.all { it != null }

    /** True when the board is a valid solution (any valid solution counts). */
    val isSolved: Boolean get() = isComplete && RuleCheck.isSolved(puzzle, placements)

    val canUndo: Boolean get() = history.isNotEmpty()

    /** Moves currently in the history (undone moves are gone). */
    val moveCount: Int get() = history.size

    /**
     * Puts tray domino [trayIndex] at [placement] (taking it off the board first if it was
     * already placed, i.e. a move). In [strict] mode a placement that breaks a rule is refused.
     */
    fun place(trayIndex: Int, placement: Placement, strict: Boolean = false): MoveResult {
        val p = placement.canonical()
        val board = puzzle.board
        if (p.cellA !in 0 until board.cellCount || p.cellB !in 0 until board.cellCount || !board.areAdjacent(p.cellA, p.cellB)) {
            return MoveResult.Rejected(MoveError.NOT_ADJACENT)
        }
        if (p.domino != tray[trayIndex]) return MoveResult.Rejected(MoveError.WRONG_DOMINO)
        for (c in intArrayOf(p.cellA, p.cellB)) {
            val o = owner[c]
            if (o >= 0 && o != trayIndex) return MoveResult.Rejected(MoveError.OCCUPIED)
        }
        if (slots[trayIndex] == p) return MoveResult.Accepted(this)
        val next = apply(Move(trayIndex, slots[trayIndex], p))
        if (strict && next.breaksRuleAt(p)) return MoveResult.Rejected(MoveError.BREAKS_RULE)
        return MoveResult.Accepted(next)
    }

    /**
     * Places tray domino [trayIndex] with [firstValue] on [anchor] and the other half on the
     * neighbour in direction [dir] (see `Direction`).
     */
    fun placeAt(trayIndex: Int, anchor: Int, dir: Int, firstValue: Int, strict: Boolean = false): MoveResult {
        val d = tray[trayIndex]
        if (firstValue != d.low && firstValue != d.high) return MoveResult.Rejected(MoveError.WRONG_DOMINO)
        val other = puzzle.board.neighbor(anchor, dir)
        if (other < 0) return MoveResult.Rejected(MoveError.NOT_ADJACENT)
        val second = if (firstValue == d.low) d.high else d.low
        return place(trayIndex, Placement(anchor, other, firstValue, second), strict)
    }

    /** Returns tray domino [trayIndex] to the tray. */
    fun remove(trayIndex: Int): MoveResult {
        if (slots[trayIndex] == null) return MoveResult.Rejected(MoveError.NOT_PLACED)
        return MoveResult.Accepted(apply(Move(trayIndex, slots[trayIndex], null)))
    }

    /**
     * Turns placed domino [trayIndex] a quarter turn clockwise around its half on [anchor],
     * skipping directions that are off the board or covered; the anchor half keeps its value.
     */
    fun rotate(trayIndex: Int, anchor: Int, strict: Boolean = false): MoveResult {
        val current = slots[trayIndex] ?: return MoveResult.Rejected(MoveError.NOT_PLACED)
        if (!current.covers(anchor)) return MoveResult.Rejected(MoveError.NOT_PLACED)
        val board = puzzle.board
        val other = current.partnerOf(anchor)
        val startDir = board.directionTo(anchor, other)
        val anchorValue = current.valueOn(anchor)
        val otherValue = current.valueOn(other)
        var lastRejection = MoveError.NO_ROOM
        for (step in 1..3) {
            val dir = (startDir + step) and 3
            val target = board.neighbor(anchor, dir)
            if (target < 0) continue
            val o = owner[target]
            if (o >= 0 && o != trayIndex) continue
            when (val r = place(trayIndex, Placement(anchor, target, anchorValue, otherValue), strict)) {
                is MoveResult.Accepted -> return r
                is MoveResult.Rejected -> lastRejection = r.error
            }
        }
        return MoveResult.Rejected(lastRejection)
    }

    /** Reverses the last move. */
    fun undo(): MoveResult {
        val last = history.lastOrNull() ?: return MoveResult.Rejected(MoveError.NOTHING_TO_UNDO)
        val restored = slots.toMutableList().also { it[last.trayIndex] = last.before }
        return MoveResult.Accepted(Game(puzzle, restored, history.dropLast(1)))
    }

    /** An empty board with no history. */
    fun restart(): Game = start(puzzle)

    /** True if any region touching [p] is broken. */
    private fun breaksRuleAt(p: Placement): Boolean {
        val regionOf = puzzle.regionOfCells()
        for (c in intArrayOf(p.cellA, p.cellB)) {
            val r = regionOf[c]
            if (r >= 0 && regionStatuses[r] == RegionStatus.BROKEN) return true
        }
        return false
    }

    private fun apply(move: Move): Game {
        val next = slots.toMutableList().also { it[move.trayIndex] = move.after }
        return Game(puzzle, next, history + move)
    }

    companion object {
        /** A fresh game: every domino in the tray. */
        fun start(puzzle: Puzzle): Game = Game(puzzle, List(puzzle.tray.size) { null }, emptyList())

        /**
         * Rebuilds a saved game, replaying [history] from an empty board to validate it; the
         * replay must end at [slots]. Throws [IllegalArgumentException] on any inconsistency.
         */
        fun restore(puzzle: Puzzle, slots: List<Placement?>, history: List<Move>): Game {
            require(slots.size == puzzle.tray.size) { "slot count mismatch" }
            var game = start(puzzle)
            for (move in history) {
                require(move.trayIndex in slots.indices) { "bad tray index ${move.trayIndex}" }
                require(game.slots[move.trayIndex] == move.before) { "history does not replay" }
                val after = move.after
                game = if (after == null) {
                    (game.remove(move.trayIndex) as? MoveResult.Accepted)?.game
                } else {
                    (game.place(move.trayIndex, after) as? MoveResult.Accepted)?.game
                } ?: throw IllegalArgumentException("illegal move in history: $move")
            }
            val board = game.slots.map { it?.canonical() }
            require(board == slots.map { it?.canonical() }) { "history does not end at the saved board" }
            return Game(puzzle, board, history)
        }
    }
}

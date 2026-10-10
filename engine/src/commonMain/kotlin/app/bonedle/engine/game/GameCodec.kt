package app.bonedle.engine.game

import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Puzzle

/** Thrown when saved game text cannot be restored. */
class GameFormatException(message: String, cause: Throwable? = null) : IllegalArgumentException(message, cause)

/**
 * Text form of a [Game]'s board and undo history, for saves. The puzzle itself is stored
 * separately (see `PuzzleCodec`).
 *
 * ```
 * g1|<slot>;<slot>;...|<move>;<move>;...
 * slot  = "-" | cellA,cellB,valueA,valueB
 * move  = trayIndex/slot/slot          (before / after)
 * ```
 */
object GameCodec {
    private const val VERSION = "g1"

    /** Encodes [game]'s board and history. */
    fun encode(game: Game): String = buildString {
        append(VERSION)
        append('|')
        game.tray.indices.joinTo(this, ";") { slot(game.placementOf(it)) }
        append('|')
        game.history.joinTo(this, ";") { "${it.trayIndex}/${slot(it.before)}/${slot(it.after)}" }
    }

    /** Restores a game of [puzzle] from [text], validating every move. */
    fun decode(puzzle: Puzzle, text: String): Game {
        try {
            val parts = text.split('|')
            if (parts.size != 3 || parts[0] != VERSION) throw GameFormatException("unsupported save")
            val slots = if (puzzle.tray.isEmpty()) emptyList() else parts[1].split(';').map(::parseSlot)
            val history = if (parts[2].isEmpty()) {
                emptyList()
            } else {
                parts[2].split(';').map { m ->
                    val f = m.split('/')
                    if (f.size != 3) throw GameFormatException("bad move: $m")
                    Move(f[0].toInt(), parseSlot(f[1]), parseSlot(f[2]))
                }
            }
            return Game.restore(puzzle, slots, history)
        } catch (e: GameFormatException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw GameFormatException(e.message ?: "invalid save", e)
        }
    }

    private fun slot(p: Placement?): String = if (p == null) "-" else "${p.cellA},${p.cellB},${p.valueA},${p.valueB}"

    private fun parseSlot(s: String): Placement? {
        if (s == "-") return null
        val f = s.split(',').map { it.toInt() }
        if (f.size != 4) throw GameFormatException("bad slot: $s")
        return Placement(f[0], f[1], f[2], f[3])
    }
}

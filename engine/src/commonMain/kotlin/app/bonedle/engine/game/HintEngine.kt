package app.bonedle.engine.game

import app.bonedle.engine.grader.Deduction
import app.bonedle.engine.grader.HumanSolver
import app.bonedle.engine.grader.Knowledge
import app.bonedle.engine.model.Placement

/** A domino the hint offers to place, and which tray domino to use. */
data class Reveal(val trayIndex: Int, val placement: Placement)

/**
 * What a hint has to say about the current board. The UI escalates through three levels:
 * 1. highlight [Step.deduction]'s region or cells, 2. name the deduction, 3. place [Step.reveal].
 */
sealed interface Hint {
    /** A domino on the board that is not part of the solution; level 3 returns it to the tray. */
    data class WrongDomino(val trayIndex: Int, val placement: Placement) : Hint

    /**
     * The next logical step from the player's correct dominoes. [deduction] is null only if the
     * human-style solver finds no step from here, in which case the hint goes straight to a reveal.
     */
    data class Step(val deduction: Deduction?, val reveal: Reveal) : Hint

    /** Nothing left to hint: the board is solved. */
    data object Solved : Hint
}

/** Produces hints from the human-style solver's next step (spec 6, 7.5). */
object HintEngine {
    /** The hint for [game]'s current board. */
    fun next(game: Game): Hint {
        if (game.isSolved) return Hint.Solved
        val solution = game.puzzle.solution.toSet()
        // A wrong domino would mislead every deduction, so it is pointed out first.
        for (t in game.tray.indices) {
            val p = game.placementOf(t) ?: continue
            if (p.canonical() !in solution) return Hint.WrongDomino(t, p)
        }
        val correct = game.placements
        val knowledge = Knowledge.initial(game.puzzle, correct)
        val deduction = HumanSolver.nextDeduction(knowledge)
        val reveal = reveal(game, deduction) ?: return Hint.Solved
        return Hint.Step(deduction, reveal)
    }

    /** A solution domino not yet on the board, preferring one the deduction is about. */
    private fun reveal(game: Game, deduction: Deduction?): Reveal? {
        val placed = game.placements.map { it.canonical() }.toSet()
        val missing = game.puzzle.solution.filter { it !in placed }
        if (missing.isEmpty()) return null
        val focus = deduction?.let { d -> d.cells + d.determined.map { it.cell } }.orEmpty()
        val chosen = missing.firstOrNull { p -> focus.any { p.covers(it) } } ?: missing.first()
        val trayIndex = game.unplaced.first { game.tray[it] == chosen.domino }
        return Reveal(trayIndex, chosen)
    }
}

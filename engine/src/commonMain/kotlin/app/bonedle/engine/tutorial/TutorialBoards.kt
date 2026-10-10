package app.bonedle.engine.tutorial

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Grade
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Region
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.Technique
import app.bonedle.engine.solver.ExactSolver

/**
 * The first-launch tutorial: four 2×4 boards of four dominoes, each introducing one idea
 * (spec 5.1): cover the board, then `=`, then a sum, then `≠`. Any valid covering completes a
 * step, so these boards need not be unique, but each rule rules out arrangements the step
 * before would have allowed.
 *
 * Cells are numbered row by row: `0 1 2 3` on top, `4 5 6 7` below.
 */
object TutorialBoards {
    /** What each step teaches, in order. */
    enum class Lesson { COVER, EQUAL, SUM, ALL_DIFFERENT }

    val lessons: List<Lesson> = Lesson.entries

    val STEPS: Int get() = lessons.size

    private val board = Board.rectangle(2, 4)

    /** The board for tutorial [step] (0-based). */
    fun puzzle(step: Int): Puzzle = puzzles[step]

    private val puzzles: List<Puzzle> by lazy {
        listOf(
            build(emptyList(), listOf(Domino(1, 2), Domino(3, 4), Domino(5, 6), Domino(6, 6))),
            // The only matching pair is two 4s, from two different dominoes.
            build(listOf(Region(listOf(1, 2), Rule.EQUAL, 0)), listOf(Domino(0, 5), Domino(1, 3), Domino(2, 4), Domino(4, 6))),
            // 10 on two stacked cells: only the 4-6 standing upright makes it.
            build(listOf(Region(listOf(0, 4), Rule.sumEq(10), 1)), listOf(Domino(0, 3), Domino(1, 2), Domino(3, 5), Domino(4, 6))),
            // The top row may not repeat a value, so the 1-1 cannot lie across it.
            build(listOf(Region(listOf(0, 1, 2, 3), Rule.ALL_DIFFERENT, 4)), listOf(Domino(1, 1), Domino(1, 3), Domino(2, 4), Domino(3, 5))),
        )
    }

    private fun build(regions: List<Region>, tray: List<Domino>): Puzzle {
        val solution = ExactSolver.solve(board, regions, tray, maxSolutions = 1).solutions.single()
        return Puzzle(
            seed = 0L,
            difficulty = Difficulty.EASY,
            board = board,
            regions = regions,
            tray = tray.sorted(),
            solution = solution,
            grade = Grade(Technique.SINGLE_CELL, 0),
        )
    }
}

package app.bonedle.engine

import app.bonedle.engine.codec.PuzzleCodec
import app.bonedle.engine.grader.HumanSolver
import app.bonedle.engine.grader.Knowledge
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Technique
import app.bonedle.engine.rules.PuzzleValidator
import app.bonedle.engine.solver.ExactSolver

/** Every invariant a generated puzzle must satisfy, shared by the generator and property tests. */
object PuzzleChecks {
    /** Inclusive cell-count range per difficulty. */
    fun cellRange(d: Difficulty): IntRange = when (d) {
        Difficulty.EASY -> 8..12
        Difficulty.MEDIUM -> 16..20
        Difficulty.HARD -> 24..28
        Difficulty.EXPERT -> 30..34
    }

    /** Inclusive band of the hardest technique level per difficulty. */
    fun levelBand(d: Difficulty): IntRange = when (d) {
        Difficulty.EASY -> 1..3
        Difficulty.MEDIUM -> 4..5
        Difficulty.HARD -> 6..7
        Difficulty.EXPERT -> 7..8
    }

    fun maxRegionSize(d: Difficulty): Int = when (d) {
        Difficulty.EASY -> 4
        Difficulty.MEDIUM -> 5
        Difficulty.HARD, Difficulty.EXPERT -> 6
    }

    /** Problems with [p] as a generated puzzle of its difficulty; empty when all invariants hold. */
    fun problems(p: Puzzle): List<String> {
        val out = ArrayList<String>()
        out += PuzzleValidator.problems(p)
        val n = p.board.cellCount
        val d = p.difficulty
        if (n !in cellRange(d)) out += "cell count $n outside ${cellRange(d)}"
        if (d <= Difficulty.MEDIUM && !p.board.isRectangle) out += "irregular board below Hard"
        if (p.board.cols > 6 || p.board.rows > 8) out += "board ${p.board.rows}x${p.board.cols} too large for a phone"
        val covered = p.regions.sumOf { it.cells.size }
        if (covered * 100 < n * 55 || covered * 100 > n * 80) out += "coverage $covered/$n outside 55-80%"
        val biggest = p.regions.maxOf { it.cells.size }
        if (biggest > maxRegionSize(d)) out += "region of $biggest cells exceeds ${maxRegionSize(d)}"
        if (p.grade.hardest.level !in levelBand(d)) out += "grade ${p.grade} outside band ${levelBand(d)}"

        val exact = ExactSolver.solve(p.board, p.regions, p.tray, maxSolutions = 2)
        if (!exact.isUnique) out += "not unique (count=${exact.solutionCount}, exhausted=${exact.exhausted})"
        else if (exact.solutions.single() != p.solution) out += "exact solver found a different solution"

        out += humanSolveProblems(p)
        return out
    }

    /**
     * Replays the human-style solve step by step. Every step must keep the true solution possible
     * (soundness), the solve must finish on the stored solution, and the grade must reproduce.
     */
    fun humanSolveProblems(p: Puzzle): List<String> {
        val out = ArrayList<String>()
        val k = Knowledge.initial(p)
        val values = p.solutionValues()
        val partner = IntArray(p.board.cellCount)
        for (pl in p.solution) {
            partner[pl.cellA] = pl.cellB
            partner[pl.cellB] = pl.cellA
        }
        var steps = 0
        var hardest = 0
        while (!k.isSolved && steps < HumanSolver.STEP_LIMIT) {
            val ded = HumanSolver.nextDeduction(k) ?: break
            HumanSolver.apply(k, ded)
            steps++
            hardest = maxOf(hardest, ded.technique.level)
            for (c in 0 until p.board.cellCount) {
                if (k.candidates(c) and (1 shl values[c]) == 0) {
                    out += "step $steps (${ded.technique}) removed the true value of cell $c"
                    return out
                }
                if (partner[c] !in k.possiblePartners(c)) {
                    out += "step $steps (${ded.technique}) removed the true partner of cell $c"
                    return out
                }
            }
        }
        if (!k.isSolved) out += "human solver stuck after $steps steps"
        else if (k.establishedPlacements() != p.solution) out += "human solve ended on a different solution"
        if (Technique.fromLevel(hardest) != p.grade.hardest || steps != p.grade.steps) {
            out += "grade does not reproduce: stored ${p.grade}, replay level $hardest in $steps steps"
        }
        return out
    }

    /** True if regenerating [p] from its seed gives byte-identical output. */
    fun reproduces(p: Puzzle, regenerate: (Long, Difficulty) -> Puzzle): Boolean =
        PuzzleCodec.encode(p).contentEquals(PuzzleCodec.encode(regenerate(p.seed, p.difficulty)))
}

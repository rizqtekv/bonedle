package app.bonedle.engine.rules

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Region
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.RuleType

/** State of one region on a player's board. */
enum class RegionStatus {
    /** Not yet decided: some cells are empty and the rule can still be met. */
    OPEN,

    /** Every cell is filled and the rule holds. */
    SATISFIED,

    /** The rule can no longer hold, whatever goes in the empty cells. */
    BROKEN,
}

/** Rule evaluation for boards as the player sees them (cells may be empty). */
object RuleCheck {
    /** Marker for an empty cell in value arrays. */
    const val EMPTY = -1

    /** Status of a region with [rule] whose cells currently show [values] ([EMPTY] = empty). */
    fun status(rule: Rule, values: IntArray): RegionStatus {
        var sum = 0
        var empty = 0
        var seen = 0
        var duplicate = false
        var first = EMPTY
        var mixed = false
        for (v in values) {
            if (v == EMPTY) {
                empty++
                continue
            }
            sum += v
            if (seen and (1 shl v) != 0) duplicate = true
            seen = seen or (1 shl v)
            if (first == EMPTY) first = v else if (v != first) mixed = true
        }
        val complete = empty == 0
        val broken = when (rule.type) {
            RuleType.EQUAL -> mixed
            RuleType.ALL_DIFFERENT -> duplicate
            RuleType.SUM_EQ -> sum > rule.target || sum + empty * Domino.MAX_VALUE < rule.target
            RuleType.SUM_LT -> sum >= rule.target
            RuleType.SUM_GT -> sum + empty * Domino.MAX_VALUE <= rule.target
        }
        return when {
            broken -> RegionStatus.BROKEN
            complete -> RegionStatus.SATISFIED
            else -> RegionStatus.OPEN
        }
    }

    /** Status of [region] given per-cell [cellValues] for the whole board. */
    fun status(region: Region, cellValues: IntArray): RegionStatus =
        status(region.rule, IntArray(region.cells.size) { cellValues[region.cells[it]] })

    /** Per-cell values shown by [placements] on [board] ([EMPTY] where uncovered). */
    fun cellValues(board: Board, placements: List<Placement>): IntArray {
        val out = IntArray(board.cellCount) { EMPTY }
        for (p in placements) {
            out[p.cellA] = p.valueA
            out[p.cellB] = p.valueB
        }
        return out
    }

    /**
     * True if [placements] solve [puzzle]: every cell covered exactly once by adjacent halves,
     * exactly the tray's dominoes used, and every region satisfied. Any valid solution counts,
     * not only the stored one.
     */
    fun isSolved(puzzle: Puzzle, placements: List<Placement>): Boolean {
        val board = puzzle.board
        if (placements.size != puzzle.tray.size) return false
        val covered = BooleanArray(board.cellCount)
        val counts = IntArray(Domino.TYPE_COUNT)
        for (p in placements) {
            if (p.cellA !in 0 until board.cellCount || p.cellB !in 0 until board.cellCount) return false
            if (!board.areAdjacent(p.cellA, p.cellB)) return false
            if (covered[p.cellA] || covered[p.cellB]) return false
            covered[p.cellA] = true
            covered[p.cellB] = true
            counts[p.domino.index]++
        }
        for (d in puzzle.tray) counts[d.index]--
        if (counts.any { it != 0 }) return false
        val values = cellValues(board, placements)
        return puzzle.regions.all { status(it, values) == RegionStatus.SATISFIED }
    }
}

package app.bonedle.engine.model

/**
 * A coloured group of orthogonally connected cells sharing one [rule].
 *
 * @property cells sorted cell indices.
 * @property color palette slot in `0 until REGION_COLORS`; neighbouring regions never share a slot,
 *   and each slot has its own texture so regions stay distinguishable without colour.
 */
data class Region(val cells: List<Int>, val rule: Rule, val color: Int) {
    init {
        require(cells.isNotEmpty()) { "region must contain cells" }
        require(color in 0 until REGION_COLORS) { "color out of range: $color" }
    }

    companion object {
        /** Number of region colour/texture slots in the design system. */
        const val REGION_COLORS = 6
    }
}

/**
 * One domino lying on the board: [valueA] on [cellA] and [valueB] on [cellB].
 * Canonical form has [cellA] < [cellB].
 */
data class Placement(val cellA: Int, val cellB: Int, val valueA: Int, val valueB: Int) {
    /** The domino type this placement uses. */
    val domino: Domino get() = Domino.of(valueA, valueB)

    /** This placement with cells ordered so that `cellA < cellB`. */
    fun canonical(): Placement = if (cellA < cellB) this else Placement(cellB, cellA, valueB, valueA)

    /** The value shown on [cell], which must be one of this placement's cells. */
    fun valueOn(cell: Int): Int = if (cell == cellA) valueA else valueB

    /** The other cell covered by this placement. */
    fun partnerOf(cell: Int): Int = if (cell == cellA) cellB else cellA

    /** True if this placement covers [cell]. */
    fun covers(cell: Int): Boolean = cell == cellA || cell == cellB
}

/** The named deduction techniques of the human-style solver, from easiest to hardest. */
enum class Technique(val level: Int) {
    /** A cell has only one legal value or placement given what is already known. */
    SINGLE_CELL(1),

    /** A sum region's open cells admit only some values. */
    REGION_SUM(2),

    /** Values propagate across an equal region. */
    EQUAL_PROPAGATION(3),

    /** Values are eliminated within an all-different region. */
    ALL_DIFFERENT(4),

    /** A cell can only pair with some neighbours, or only some values have a fitting domino. */
    DOMINO_ADJACENCY(5),

    /** Counting the remaining dominoes forces a value or a placement. */
    TRAY_COUNTING(6),

    /** Linked regions constrain each other through the dominoes that join them. */
    TWO_REGION(7),

    /** Trying a value or pairing leads to a contradiction. */
    LOOKAHEAD(8),
    ;

    companion object {
        /** The technique with the given [level], or null. */
        fun fromLevel(level: Int): Technique? = entries.firstOrNull { it.level == level }
    }
}

/**
 * Human-style difficulty of a puzzle.
 *
 * @property hardest highest technique the human-style solver needed.
 * @property steps number of deductions in the solve path.
 */
data class Grade(val hardest: Technique, val steps: Int)

/**
 * A complete, validated puzzle.
 *
 * @property seed generator seed that reproduces this puzzle exactly (0 for hand-made boards).
 * @property tray dominoes handed to the player, sorted.
 * @property solution the unique solution in canonical form, sorted by [Placement.cellA].
 */
data class Puzzle(
    val seed: Long,
    val difficulty: Difficulty,
    val board: Board,
    val regions: List<Region>,
    val tray: List<Domino>,
    val solution: List<Placement>,
    val grade: Grade,
) {
    /** Region index of every cell, or -1 for free cells. */
    fun regionOfCells(): IntArray {
        val out = IntArray(board.cellCount) { -1 }
        regions.forEachIndexed { r, region -> region.cells.forEach { out[it] = r } }
        return out
    }

    /** The value on every cell in the solution. */
    fun solutionValues(): IntArray {
        val out = IntArray(board.cellCount)
        for (p in solution) {
            out[p.cellA] = p.valueA
            out[p.cellB] = p.valueB
        }
        return out
    }
}

package app.bonedle.engine.model

/**
 * The playable shape: a [rows] × [cols] bounding box in which some positions hold a cell.
 * Shapes may be irregular (holes, non-rectangular outlines).
 *
 * Cells are identified by a dense index in row-major order of the active positions, so cell `0`
 * is the top-most, left-most active position.
 */
class Board(val rows: Int, val cols: Int, private val active: BooleanArray) {
    init {
        require(rows in 1..MAX_SIZE && cols in 1..MAX_SIZE) { "board must fit in ${MAX_SIZE}x$MAX_SIZE" }
        require(active.size == rows * cols) { "mask size mismatch" }
    }

    private val indexAt = IntArray(rows * cols) { -1 }

    /** Number of active cells. */
    val cellCount: Int

    private val rowOf: IntArray
    private val colOf: IntArray
    private val neighbors: IntArray

    init {
        var count = 0
        for (p in active.indices) if (active[p]) indexAt[p] = count++
        cellCount = count
        rowOf = IntArray(count)
        colOf = IntArray(count)
        for (p in active.indices) {
            val i = indexAt[p]
            if (i >= 0) {
                rowOf[i] = p / cols
                colOf[i] = p % cols
            }
        }
        neighbors = IntArray(count * 4) { -1 }
        for (i in 0 until count) {
            for (dir in 0 until 4) {
                neighbors[i * 4 + dir] = cellAt(rowOf[i] + DR[dir], colOf[i] + DC[dir])
            }
        }
    }

    /** Row (0-based) of [cell]. */
    fun row(cell: Int): Int = rowOf[cell]

    /** Column (0-based) of [cell]. */
    fun col(cell: Int): Int = colOf[cell]

    /** Index of the cell at ([row], [col]), or -1 if the position is outside the board or a hole. */
    fun cellAt(row: Int, col: Int): Int =
        if (row !in 0 until rows || col !in 0 until cols) -1 else indexAt[row * cols + col]

    /** True if ([row], [col]) holds a cell. */
    fun isActive(row: Int, col: Int): Boolean = cellAt(row, col) >= 0

    /** Neighbour of [cell] in direction [dir] (see [Direction]), or -1. */
    fun neighbor(cell: Int, dir: Int): Int = neighbors[cell * 4 + dir]

    /** True if cells [a] and [b] share an edge. */
    fun areAdjacent(a: Int, b: Int): Boolean = directionTo(a, b) >= 0

    /** Direction from [a] to its orthogonal neighbour [b], or -1 when not adjacent. */
    fun directionTo(a: Int, b: Int): Int {
        for (dir in 0 until 4) if (neighbors[a * 4 + dir] == b) return dir
        return -1
    }

    /** True when every position of the bounding box holds a cell. */
    val isRectangle: Boolean get() = cellCount == rows * cols

    /** Copy of the activity mask in row-major order. */
    fun activeMask(): BooleanArray = active.copyOf()

    override fun equals(other: Any?): Boolean =
        other is Board && other.rows == rows && other.cols == cols && other.active.contentEquals(active)

    override fun hashCode(): Int = (rows * 31 + cols) * 31 + active.contentHashCode()

    override fun toString(): String = buildString {
        for (r in 0 until rows) {
            for (c in 0 until cols) append(if (isActive(r, c)) '#' else '.')
            if (r < rows - 1) append('/')
        }
    }

    companion object {
        /** Largest supported side length. */
        const val MAX_SIZE = 8

        private val DR = intArrayOf(-1, 0, 1, 0)
        private val DC = intArrayOf(0, 1, 0, -1)

        /** A full [rows] × [cols] rectangle. */
        fun rectangle(rows: Int, cols: Int): Board = Board(rows, cols, BooleanArray(rows * cols) { true })
    }
}

/** Orthogonal directions, in the order used by [Board.neighbor]. */
object Direction {
    /** Up. */
    const val NORTH = 0

    /** Right. */
    const val EAST = 1

    /** Down. */
    const val SOUTH = 2

    /** Left. */
    const val WEST = 3

    /** The direction pointing back the way [dir] came. */
    fun opposite(dir: Int): Int = (dir + 2) and 3
}

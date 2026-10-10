package app.bonedle.feature.play

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Placement
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Pixel layout of a board: cells are [cell] px squares starting [padding] px inside the board's
 * frame. All coordinates are local to the board composable.
 */
@Immutable
class BoardGeometry(val board: Board, val cell: Float, val padding: Float) {
    val size: Size get() = Size(board.cols * cell + padding * 2, board.rows * cell + padding * 2)

    fun cellRect(c: Int): Rect {
        val left = padding + board.col(c) * cell
        val top = padding + board.row(c) * cell
        return Rect(left, top, left + cell, top + cell)
    }

    /** Bounding box of the two cells of [p]. */
    fun placementRect(p: Placement): Rect {
        val a = cellRect(p.cellA)
        val b = cellRect(p.cellB)
        return Rect(minOf(a.left, b.left), minOf(a.top, b.top), maxOf(a.right, b.right), maxOf(a.bottom, b.bottom))
    }

    /** The cell under [point], or -1. */
    fun cellAt(point: Offset): Int {
        val col = floor((point.x - padding) / cell).toInt()
        val row = floor((point.y - padding) / cell).toInt()
        return board.cellAt(row, col)
    }

    /** True if [point] lies within the board frame. */
    fun contains(point: Offset): Boolean = point.x in 0f..size.width && point.y in 0f..size.height

    /**
     * Where a dragged domino whose top-left corner is at [topLeft] would land: the cell pair it
     * overlaps by at least [SNAP_OVERLAP], with [lead] on the left/top half. Null if none.
     */
    fun snap(topLeft: Offset, horizontal: Boolean, lead: Int, trail: Int): Placement? {
        val col = ((topLeft.x - padding) / cell).roundToInt()
        val row = ((topLeft.y - padding) / cell).roundToInt()
        val a = board.cellAt(row, col)
        val b = if (horizontal) board.cellAt(row, col + 1) else board.cellAt(row + 1, col)
        if (a < 0 || b < 0) return null
        val w = if (horizontal) cell * 2 else cell
        val h = if (horizontal) cell else cell * 2
        val ghost = Rect(topLeft, Size(w, h))
        val target = cellRect(a).let { Rect(it.left, it.top, it.left + w, it.top + h) }
        val overlap = ghost.intersect(target)
        if (overlap.width <= 0f || overlap.height <= 0f) return null
        if (overlap.width * overlap.height < SNAP_OVERLAP * w * h) return null
        return Placement(a, b, lead, trail)
    }

    companion object {
        /** Share of the domino that must cover the target pair to snap (spec 6). */
        const val SNAP_OVERLAP = 0.6f
    }
}

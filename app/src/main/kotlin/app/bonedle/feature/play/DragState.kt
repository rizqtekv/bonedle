package app.bonedle.feature.play

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import app.bonedle.engine.model.Placement

/**
 * A domino being dragged, shared by the tray, the board and the floating ghost. Positions are
 * in root (window) pixels; the ghost's top-left is [pointer] − [grab].
 */
@Stable
class DragState {
    var trayIndex by mutableStateOf<Int?>(null)
        private set
    var fromBoard by mutableStateOf(false)
        private set
    var horizontal by mutableStateOf(true)
        private set
    var lead by mutableStateOf(0)
        private set
    var trail by mutableStateOf(0)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    var grab by mutableStateOf(Offset.Zero)
        private set

    /** Where the board is in the window, and its geometry; set by the board. */
    var boardOrigin by mutableStateOf(Offset.Zero)
    var geometry by mutableStateOf<BoardGeometry?>(null)

    val active: Boolean get() = trayIndex != null

    fun start(trayIndex: Int, fromBoard: Boolean, horizontal: Boolean, lead: Int, trail: Int, pointer: Offset, grab: Offset) {
        this.trayIndex = trayIndex
        this.fromBoard = fromBoard
        this.horizontal = horizontal
        this.lead = lead
        this.trail = trail
        this.pointer = pointer
        this.grab = grab
    }

    fun end() {
        trayIndex = null
    }

    /** Ghost top-left in board coordinates. */
    private val ghostOnBoard: Offset get() = pointer - grab - boardOrigin

    /** Where the ghost would snap now, or null. */
    val target: Placement?
        get() = if (!active) null else geometry?.snap(ghostOnBoard, horizontal, lead, trail)

    /** True if the ghost's centre is over the board frame. */
    val overBoard: Boolean
        get() {
            val g = geometry ?: return false
            val cell = g.cell
            val center = ghostOnBoard + Offset(if (horizontal) cell else cell / 2, if (horizontal) cell / 2 else cell)
            return g.contains(center)
        }
}

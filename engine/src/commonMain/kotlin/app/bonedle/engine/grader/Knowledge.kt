package app.bonedle.engine.grader

import app.bonedle.engine.model.Direction
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.solver.Bits
import app.bonedle.engine.solver.Spec

/**
 * Everything a logical player has established about a board: for every cell, the values it may
 * still show and the neighbours it may still be paired with.
 *
 * Partner candidates are stored as a 4-bit direction mask per cell and are always symmetric:
 * if `a` may pair with `b`, then `b` may pair with `a`.
 */
class Knowledge internal constructor(
    internal val spec: Spec,
    internal val values: IntArray,
    internal val links: IntArray,
) {
    /** Number of cells on the board. */
    val cellCount: Int get() = spec.n

    /** Bit mask of values [cell] may still show (bit `v` set ⇒ value `v` possible). */
    fun candidates(cell: Int): Int = values[cell]

    /** The value of [cell] if it is known, otherwise -1. */
    fun knownValue(cell: Int): Int = if (Bits.isSingle(values[cell])) Bits.lowest(values[cell]) else -1

    /** The cell [cell] is known to be paired with, or -1 if that is not yet established. */
    fun knownPartner(cell: Int): Int = fixedPartner(cell)

    /** Neighbours [cell] may still be paired with. */
    fun possiblePartners(cell: Int): List<Int> {
        val out = ArrayList<Int>(4)
        for (dir in 0 until 4) if (links[cell] and (1 shl dir) != 0) out.add(spec.nbr[cell * 4 + dir])
        return out
    }

    /** True once every value and every pairing is established. */
    val isSolved: Boolean
        get() {
            for (c in 0 until spec.n) if (!Bits.isSingle(values[c]) || fixedPartner(c) < 0) return false
            return true
        }

    /** The placements that are fully established (both cells paired and both values known). */
    fun establishedPlacements(): List<Placement> {
        val out = ArrayList<Placement>()
        for (c in 0 until spec.n) {
            val d = fixedPartner(c)
            if (d > c && Bits.isSingle(values[c]) && Bits.isSingle(values[d])) {
                out.add(Placement(c, d, Bits.lowest(values[c]), Bits.lowest(values[d])))
            }
        }
        return out
    }

    /** An independent copy. */
    fun copy(): Knowledge = Knowledge(spec, values.copyOf(), links.copyOf())

    internal fun fixedPartner(c: Int): Int {
        val l = links[c]
        if (!Bits.isSingle(l)) return -1
        val dir = Bits.lowest(l)
        val d = spec.nbr[c * 4 + dir]
        return if (links[d] == 1 shl Direction.opposite(dir)) d else -1
    }

    internal fun removeLink(c: Int, dir: Int) {
        val d = spec.nbr[c * 4 + dir]
        links[c] = links[c] and (1 shl dir).inv()
        if (d >= 0) links[d] = links[d] and (1 shl Direction.opposite(dir)).inv()
    }

    /** Restricts [c] to pair only with [d], removing every other link of both cells. */
    internal fun fixPair(c: Int, d: Int) {
        for (dir in 0 until 4) {
            val e = spec.nbr[c * 4 + dir]
            if (e >= 0 && e != d && links[c] and (1 shl dir) != 0) removeLink(c, dir)
            val f = spec.nbr[d * 4 + dir]
            if (f >= 0 && f != c && links[d] and (1 shl dir) != 0) removeLink(d, dir)
        }
    }

    companion object {
        /**
         * The starting knowledge for [puzzle], optionally with [known] placements treated as
         * established (used for hints on a partly filled board).
         */
        fun initial(puzzle: Puzzle, known: List<Placement> = emptyList()): Knowledge {
            val k = initial(Spec.of(puzzle.board, puzzle.regions, puzzle.tray))
            for (p in known) {
                k.values[p.cellA] = 1 shl p.valueA
                k.values[p.cellB] = 1 shl p.valueB
                k.fixPair(p.cellA, p.cellB)
            }
            return k
        }

        internal fun initial(spec: Spec): Knowledge {
            var trayValues = 0
            for (t in 0 until Domino.TYPE_COUNT) {
                if (spec.trayCounts[t] > 0) {
                    trayValues = trayValues or (1 shl Domino.lowOf(t)) or (1 shl Domino.highOf(t))
                }
            }
            val values = IntArray(spec.n) { trayValues }
            val links = IntArray(spec.n) { c ->
                var mask = 0
                for (dir in 0 until 4) if (spec.nbr[c * 4 + dir] >= 0) mask = mask or (1 shl dir)
                mask
            }
            return Knowledge(spec, values, links)
        }
    }
}

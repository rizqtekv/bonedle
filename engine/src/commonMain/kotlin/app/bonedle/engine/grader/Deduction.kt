package app.bonedle.engine.grader

import app.bonedle.engine.model.Technique
import app.bonedle.engine.solver.Bits

/** A cell together with the value it has been shown to hold. */
data class CellValue(val cell: Int, val value: Int)

/** Two cells shown to be covered by the same domino. */
data class CellPair(val cellA: Int, val cellB: Int)

/**
 * One logical step of the human-style solver.
 *
 * @property technique the technique that justifies the step.
 * @property region the region the step is about, or -1 when it is not about a single region.
 * @property cells the cells to highlight when this step is used as a hint.
 * @property determined cells whose value this step establishes.
 * @property pairsFixed cell pairs this step establishes as one domino.
 */
class Deduction internal constructor(
    val technique: Technique,
    val region: Int,
    val cells: List<Int>,
    val determined: List<CellValue>,
    val pairsFixed: List<CellPair>,
    internal val valueRemovals: IntArray,
    internal val linkRemovals: IntArray,
) {
    override fun toString(): String =
        "$technique(region=$region, cells=$cells, determined=$determined, pairs=$pairsFixed)"
}

/** Accumulates the eliminations of one deduction against a [Knowledge] without mutating it. */
internal class DeductionBuilder(
    private val k: Knowledge,
    private val technique: Technique,
    private val region: Int = -1,
) {
    private val n = k.spec.n
    private val valueRemovals = IntArray(n)
    private val linkRemovals = IntArray(n)
    private val focus = ArrayList<Int>()
    private var productive = false

    fun focus(c: Int): DeductionBuilder {
        if (c !in focus) focus.add(c)
        return this
    }

    /** Eliminates the values in [mask] from [c]. */
    fun removeValues(c: Int, mask: Int): DeductionBuilder {
        val effective = mask and k.values[c] and valueRemovals[c].inv()
        if (effective != 0) {
            valueRemovals[c] = valueRemovals[c] or effective
            productive = true
        }
        return this
    }

    /** Restricts [c] to the values in [mask]. */
    fun keepValues(c: Int, mask: Int): DeductionBuilder = removeValues(c, mask.inv())

    /** Eliminates the possibility that [c] pairs with its neighbour in direction [dir]. */
    fun removeLink(c: Int, dir: Int): DeductionBuilder {
        if (k.links[c] and (1 shl dir) == 0 || linkRemovals[c] and (1 shl dir) != 0) return this
        val d = k.spec.nbr[c * 4 + dir]
        linkRemovals[c] = linkRemovals[c] or (1 shl dir)
        linkRemovals[d] = linkRemovals[d] or (1 shl ((dir + 2) and 3))
        productive = true
        return this
    }

    /** Establishes that [c] and [d] form one domino. */
    fun pair(c: Int, d: Int): DeductionBuilder {
        for (dir in 0 until 4) {
            val e = k.spec.nbr[c * 4 + dir]
            if (e >= 0 && e != d) removeLink(c, dir)
            val f = k.spec.nbr[d * 4 + dir]
            if (f >= 0 && f != c) removeLink(d, dir)
        }
        return this
    }

    val isProductive: Boolean get() = productive

    fun build(): Deduction? {
        if (!productive) return null
        val determined = ArrayList<CellValue>()
        for (c in 0 until n) {
            val after = k.values[c] and valueRemovals[c].inv()
            if (valueRemovals[c] != 0 && Bits.isSingle(after)) determined.add(CellValue(c, Bits.lowest(after)))
        }
        val pairs = ArrayList<CellPair>()
        for (c in 0 until n) {
            val after = k.links[c] and linkRemovals[c].inv()
            if (!Bits.isSingle(after)) continue
            val dir = Bits.lowest(after)
            val d = k.spec.nbr[c * 4 + dir]
            if (d < c) continue
            val dAfter = k.links[d] and linkRemovals[d].inv()
            val wasFixed = k.fixedPartner(c) == d
            if (!wasFixed && dAfter == 1 shl ((dir + 2) and 3)) pairs.add(CellPair(c, d))
        }
        val cells = if (focus.isEmpty()) {
            (0 until n).filter { valueRemovals[it] != 0 || linkRemovals[it] != 0 }
        } else {
            focus.toList()
        }
        return Deduction(technique, region, cells, determined, pairs, valueRemovals, linkRemovals)
    }
}

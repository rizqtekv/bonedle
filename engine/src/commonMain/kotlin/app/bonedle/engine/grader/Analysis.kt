package app.bonedle.engine.grader

import app.bonedle.engine.model.Domino
import app.bonedle.engine.solver.Bits
import app.bonedle.engine.solver.Spec

/**
 * Derived facts about a [Knowledge] snapshot shared by every technique within one solver step:
 * established placements, the dominoes still available, half-value counts and per-region bounds.
 */
internal class Analysis(val k: Knowledge) {
    val spec: Spec = k.spec
    val n: Int = spec.n
    val values: IntArray = k.values
    val links: IntArray = k.links

    /** Mutually established partner of each cell, or -1. */
    val partner = IntArray(n) { k.fixedPartner(it) }

    /** True for cells covered by an established placement (pair and both values known). */
    val placed = BooleanArray(n)

    /** Copies of each domino type not yet accounted for by established placements. */
    val avail: IntArray = spec.trayCounts.copyOf()

    /** True if established placements use a domino more often than the tray holds it. */
    var overused = false
        private set

    /** Halves of each value among the available dominoes. */
    val openHalves = IntArray(Domino.VALUE_COUNT)

    /** Halves of each value not yet claimed by an open cell already known to show that value. */
    val spare = IntArray(Domino.VALUE_COUNT)

    val rKnownSum = IntArray(spec.regionCount)
    val rUnknown = IntArray(spec.regionCount)
    val rMin = IntArray(spec.regionCount)
    val rMax = IntArray(spec.regionCount)

    init {
        for (c in 0 until n) {
            val d = partner[c]
            if (d > c && Bits.isSingle(values[c]) && Bits.isSingle(values[d])) {
                placed[c] = true
                placed[d] = true
                val t = Domino.indexOf(Bits.lowest(values[c]), Bits.lowest(values[d]))
                if (--avail[t] < 0) overused = true
            }
        }
        for (t in 0 until Domino.TYPE_COUNT) {
            val count = avail[t]
            if (count > 0) {
                openHalves[Domino.lowOf(t)] += count
                openHalves[Domino.highOf(t)] += count
            }
        }
        openHalves.copyInto(spare)
        for (c in 0 until n) {
            if (!placed[c] && Bits.isSingle(values[c])) spare[Bits.lowest(values[c])]--
        }
        for (r in 0 until spec.regionCount) {
            for (c in spec.rCells[r]) {
                val m = values[c]
                if (Bits.isSingle(m)) {
                    rKnownSum[r] += Bits.lowest(m)
                } else {
                    rUnknown[r]++
                    if (m != 0) {
                        rMin[r] += Bits.lowest(m)
                        rMax[r] += Bits.highest(m)
                    }
                }
            }
        }
    }

    fun isKnown(c: Int): Boolean = Bits.isSingle(values[c])

    /**
     * True if [c] showing [a] and [d] showing [b] is compatible with the rule of a region that
     * contains both cells (always true when they are in different regions).
     */
    fun jointOk(c: Int, a: Int, d: Int, b: Int): Boolean {
        val r = spec.regionOf[c]
        if (r < 0 || r != spec.regionOf[d]) return true
        return when (spec.rType[r]) {
            Spec.EQUAL -> a == b
            Spec.ALL_DIFFERENT -> a != b
            else -> {
                var known = rKnownSum[r]
                var min = rMin[r]
                var max = rMax[r]
                val mc = values[c]
                if (Bits.isSingle(mc)) {
                    known -= Bits.lowest(mc)
                } else {
                    min -= Bits.lowest(mc)
                    max -= Bits.highest(mc)
                }
                val md = values[d]
                if (Bits.isSingle(md)) {
                    known -= Bits.lowest(md)
                } else {
                    min -= Bits.lowest(md)
                    max -= Bits.highest(md)
                }
                Spec.sumFeasible(spec.rType[r], spec.rTarget[r], known + a + b, min, max)
            }
        }
    }

    /** True if the cell pair ([c], [d]) can hold some available domino consistent with all knowledge. */
    fun linkSupport(c: Int, d: Int, out: IntArray): Boolean {
        var supC = 0
        var supD = 0
        var bitsA = values[c]
        while (bitsA != 0) {
            val a = Bits.lowest(bitsA)
            bitsA = bitsA and (bitsA - 1)
            var bitsB = values[d]
            while (bitsB != 0) {
                val b = Bits.lowest(bitsB)
                bitsB = bitsB and (bitsB - 1)
                if (avail[Domino.indexOf(a, b)] > 0 && jointOk(c, a, d, b)) {
                    supC = supC or (1 shl a)
                    supD = supD or (1 shl b)
                }
            }
        }
        out[0] = supC
        out[1] = supD
        return supC != 0
    }

    /**
     * True if the state is provably inconsistent. Only cheap, local checks; techniques expose
     * deeper contradictions by emptying a candidate set.
     */
    fun hasContradiction(): Boolean {
        if (overused) return true
        for (c in 0 until n) {
            if (values[c] == 0) return true
            if (!placed[c] && links[c] == 0) return true
        }
        for (v in 0 until Domino.VALUE_COUNT) {
            if (spare[v] < 0) return true
            if (spare[v] > 0) {
                var cells = 0
                val bit = 1 shl v
                for (c in 0 until n) if (!placed[c] && !isKnown(c) && values[c] and bit != 0) cells++
                if (cells < spare[v]) return true
            }
        }
        for (r in 0 until spec.regionCount) if (!regionConsistent(r)) return true
        // Every available domino still needs at least one place to go.
        for (t in 0 until Domino.TYPE_COUNT) {
            if (avail[t] > 0 && !hasLocation(t)) return true
        }
        return false
    }

    private fun regionConsistent(r: Int): Boolean {
        val cells = spec.rCells[r]
        return when (spec.rType[r]) {
            Spec.EQUAL -> {
                var inter = Domino.ALL_VALUES_MASK
                for (c in cells) inter = inter and values[c]
                inter != 0
            }
            Spec.ALL_DIFFERENT -> {
                var union = 0
                var seen = 0
                for (c in cells) {
                    union = union or values[c]
                    if (isKnown(c)) {
                        if (seen and values[c] != 0) return false
                        seen = seen or values[c]
                    }
                }
                Bits.count(union) >= cells.size
            }
            else -> Spec.sumFeasible(spec.rType[r], spec.rTarget[r], rKnownSum[r], rMin[r], rMax[r])
        }
    }

    fun hasLocation(t: Int): Boolean {
        val lo = Domino.lowOf(t)
        val hi = Domino.highOf(t)
        for (c in 0 until n) {
            if (placed[c]) continue
            for (dir in 0 until 4) {
                if (links[c] and (1 shl dir) == 0) continue
                val d = spec.nbr[c * 4 + dir]
                if (d < c || placed[d]) continue
                if (fits(c, lo, d, hi) || (lo != hi && fits(c, hi, d, lo))) return true
            }
        }
        return false
    }

    fun fits(c: Int, a: Int, d: Int, b: Int): Boolean =
        values[c] and (1 shl a) != 0 && values[d] and (1 shl b) != 0 && jointOk(c, a, d, b)
}

package app.bonedle.engine.grader

import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Technique
import app.bonedle.engine.solver.Bits
import app.bonedle.engine.solver.Spec

/**
 * Outcome of solving a board with human-style techniques only.
 *
 * @property solved true if the techniques reached the full solution without guessing.
 * @property hardest the hardest technique used, or null if no step was needed.
 * @property steps number of deductions applied.
 * @property techniqueCounts number of steps per technique, indexed by [Technique.level].
 * @property knowledge the final knowledge state.
 */
class GradeReport internal constructor(
    val solved: Boolean,
    val hardest: Technique?,
    val steps: Int,
    val techniqueCounts: IntArray,
    val knowledge: Knowledge,
)

/**
 * Human-style solver and difficulty grader.
 *
 * Each step applies the easiest technique that makes progress, so the step list doubles as a
 * hint sequence. Every technique is sound: it only removes candidates that cannot be part of any
 * solution. Techniques, in order:
 *
 * 1. [Technique.SINGLE_CELL] — last open cell of a region, a cell with a single possible partner,
 *    a known value next to a domino that fits only one way, values gone from the tray.
 * 2. [Technique.REGION_SUM] — exact sum arithmetic over a region's open cells.
 * 3. [Technique.EQUAL_PROPAGATION] — intersecting an equal region's candidates; enough halves of
 *    the value must remain.
 * 4. [Technique.ALL_DIFFERENT] — known values and naked subsets inside an all-different region.
 * 5. [Technique.DOMINO_ADJACENCY] — a neighbour (or a value) is dropped when no available domino
 *    fits it.
 * 6. [Technique.TRAY_COUNTING] — counting halves of a value against the cells that can show it;
 *    a domino with only one possible spot.
 * 7. [Technique.TWO_REGION] — joint enumeration of one or two regions linked by established
 *    dominoes, respecting the tray.
 * 8. [Technique.LOOKAHEAD] — a value or pairing that leads to a contradiction under techniques
 *    1–6 is eliminated.
 */
object HumanSolver {
    /** Upper bound on steps for a single solve; far above any generated board. */
    const val STEP_LIMIT = 2_000

    private const val CLUSTER_NODE_LIMIT = 20_000
    private const val LOOKAHEAD_MAX_LEVEL = 6
    private const val LOOKAHEAD_STEP_LIMIT = 120
    private const val LOOKAHEAD_HYPOTHESIS_LIMIT = 240

    /** Grades [puzzle] using techniques up to [maxTechnique]. */
    fun grade(puzzle: Puzzle, maxTechnique: Technique = Technique.LOOKAHEAD): GradeReport =
        solve(Knowledge.initial(puzzle), maxTechnique.level)

    /**
     * The next deduction available from [knowledge] using techniques up to [maxTechnique], or
     * null if none applies or the state is contradictory.
     */
    fun nextDeduction(knowledge: Knowledge, maxTechnique: Technique = Technique.LOOKAHEAD): Deduction? {
        val a = Analysis(knowledge)
        if (a.hasContradiction()) return null
        return next(knowledge, a, maxTechnique.level)
    }

    /** True if [knowledge] is provably inconsistent with its puzzle. */
    fun isContradictory(knowledge: Knowledge): Boolean = Analysis(knowledge).hasContradiction()

    /** Applies [deduction] to [knowledge] in place. */
    fun apply(knowledge: Knowledge, deduction: Deduction) {
        for (c in 0 until knowledge.spec.n) {
            knowledge.values[c] = knowledge.values[c] and deduction.valueRemovals[c].inv()
            knowledge.links[c] = knowledge.links[c] and deduction.linkRemovals[c].inv()
        }
    }

    internal fun solve(k: Knowledge, maxLevel: Int, stepLimit: Int = STEP_LIMIT): GradeReport {
        val counts = IntArray(Technique.entries.size + 1)
        var steps = 0
        var hardest = 0
        while (steps < stepLimit) {
            val a = Analysis(k)
            if (a.hasContradiction()) break
            if (k.isSolved) return report(true, hardest, steps, counts, k)
            val d = next(k, a, maxLevel) ?: break
            apply(k, d)
            steps++
            counts[d.technique.level]++
            if (d.technique.level > hardest) hardest = d.technique.level
        }
        return report(false, hardest, steps, counts, k)
    }

    private fun report(solved: Boolean, hardest: Int, steps: Int, counts: IntArray, k: Knowledge) =
        GradeReport(solved, Technique.fromLevel(hardest), steps, counts, k)

    internal fun next(k: Knowledge, a: Analysis, maxLevel: Int): Deduction? {
        for (level in 1..maxLevel) {
            val d = when (level) {
                1 -> singleCell(k, a)
                2 -> regionSum(k, a)
                3 -> equalPropagation(k, a)
                4 -> allDifferent(k, a)
                5 -> dominoAdjacency(k, a)
                6 -> trayCounting(k, a)
                7 -> twoRegion(k, a)
                else -> lookahead(k)
            }
            if (d != null) return d
        }
        return null
    }

    // ---------------------------------------------------------------- 1. single-cell forcing

    private fun singleCell(k: Knowledge, a: Analysis): Deduction? {
        val spec = a.spec
        // Last open cell of a region: the rule pins its value.
        for (r in 0 until spec.regionCount) {
            if (a.rUnknown[r] != 1) continue
            val cells = spec.rCells[r]
            var u = -1
            for (c in cells) if (!a.isKnown(c)) u = c
            var allowed = 0
            var bits = a.values[u]
            while (bits != 0) {
                val v = Bits.lowest(bits)
                bits = bits and (bits - 1)
                if (ruleHoldsWith(a, r, u, v)) allowed = allowed or (1 shl v)
            }
            val b = DeductionBuilder(k, Technique.SINGLE_CELL, r).focus(u).keepValues(u, allowed)
            if (b.isProductive) return b.build()
        }
        // An established domino with one known half: only some available dominoes fit the other half.
        for (c in 0 until a.n) {
            val d = a.partner[c]
            if (d < 0 || a.placed[c] || !a.isKnown(c) || a.isKnown(d)) continue
            val vc = Bits.lowest(a.values[c])
            var allowed = 0
            var bits = a.values[d]
            while (bits != 0) {
                val w = Bits.lowest(bits)
                bits = bits and (bits - 1)
                if (a.avail[Domino.indexOf(vc, w)] > 0 && a.jointOk(c, vc, d, w)) allowed = allowed or (1 shl w)
            }
            val b = DeductionBuilder(k, Technique.SINGLE_CELL).focus(c).focus(d).keepValues(d, allowed)
            if (b.isProductive) return b.build()
        }
        // Two known neighbours whose domino is no longer in the tray cannot be paired.
        for (c in 0 until a.n) {
            if (a.placed[c] || !a.isKnown(c)) continue
            for (dir in 0 until 4) {
                if (a.links[c] and (1 shl dir) == 0) continue
                val d = spec.nbr[c * 4 + dir]
                if (d < c || a.partner[c] == d || !a.isKnown(d)) continue
                val vc = Bits.lowest(a.values[c])
                val vd = Bits.lowest(a.values[d])
                if (a.avail[Domino.indexOf(vc, vd)] == 0 || !a.jointOk(c, vc, d, vd)) {
                    return DeductionBuilder(k, Technique.SINGLE_CELL).focus(c).focus(d).removeLink(c, dir).build()
                }
            }
        }
        // A cell with one possible partner: they form a domino, so the partner's other links go.
        for (c in 0 until a.n) {
            val l = a.links[c]
            if (!Bits.isSingle(l)) continue
            val d = spec.nbr[c * 4 + Bits.lowest(l)]
            if (a.partner[c] == d) continue
            val b = DeductionBuilder(k, Technique.SINGLE_CELL).focus(c).focus(d).pair(c, d)
            if (b.isProductive) return b.build()
        }
        // A value with no halves left in the tray.
        for (v in 0 until Domino.VALUE_COUNT) {
            if (a.openHalves[v] > 0) continue
            val b = DeductionBuilder(k, Technique.SINGLE_CELL)
            for (c in 0 until a.n) if (!a.placed[c] && !a.isKnown(c)) b.removeValues(c, 1 shl v)
            if (b.isProductive) return b.build()
        }
        return null
    }

    /** True if region [r] satisfies its rule with [u] showing [v] and every other cell known. */
    private fun ruleHoldsWith(a: Analysis, r: Int, u: Int, v: Int): Boolean {
        val cells = a.spec.rCells[r]
        val type = a.spec.rType[r]
        var sum = 0
        var mask = 0
        var duplicate = false
        var distinct = 0
        for (c in cells) {
            val value = if (c == u) v else Bits.lowest(a.values[c])
            sum += value
            if (mask and (1 shl value) != 0) duplicate = true else distinct++
            mask = mask or (1 shl value)
        }
        return when (type) {
            Spec.EQUAL -> distinct == 1
            Spec.ALL_DIFFERENT -> !duplicate
            else -> Spec.sumOk(type, a.spec.rTarget[r], sum)
        }
    }

    // ---------------------------------------------------------------- 2. region sum forcing

    private fun regionSum(k: Knowledge, a: Analysis): Deduction? {
        val spec = a.spec
        for (r in 0 until spec.regionCount) {
            val type = spec.rType[r]
            if (type != Spec.SUM_EQ && type != Spec.SUM_LT && type != Spec.SUM_GT) continue
            if (a.rUnknown[r] == 0) continue
            val open = spec.rCells[r].filter { !a.isKnown(it) }
            val m = open.size
            val target = spec.rTarget[r] - a.rKnownSum[r]
            val b = DeductionBuilder(k, Technique.REGION_SUM, r)
            open.forEach { b.focus(it) }
            if (a.rMax[r] > 62) {
                // Too large for the bitset; fall back to bounds.
                for (c in open) {
                    val m0 = a.values[c]
                    val minOthers = a.rMin[r] - Bits.lowest(m0)
                    val maxOthers = a.rMax[r] - Bits.highest(m0)
                    var allowed = 0
                    for (v in 0 until Domino.VALUE_COUNT) {
                        if (m0 and (1 shl v) == 0) continue
                        if (Spec.sumFeasible(type, target, v, minOthers, maxOthers)) allowed = allowed or (1 shl v)
                    }
                    b.keepValues(c, allowed)
                }
            } else {
                // prefix[i]: achievable sums of open[0 until i]; suffix[i]: of open[i until m].
                val prefix = LongArray(m + 1)
                val suffix = LongArray(m + 1)
                prefix[0] = 1L
                for (i in 0 until m) prefix[i + 1] = shiftOr(prefix[i], a.values[open[i]])
                suffix[m] = 1L
                for (i in m - 1 downTo 0) suffix[i] = shiftOr(suffix[i + 1], a.values[open[i]])
                for (i in 0 until m) {
                    val others = convolve(prefix[i], suffix[i + 1])
                    var allowed = 0
                    var bits = a.values[open[i]]
                    while (bits != 0) {
                        val v = Bits.lowest(bits)
                        bits = bits and (bits - 1)
                        if (sumReachable(type, target - v, others)) allowed = allowed or (1 shl v)
                    }
                    b.keepValues(open[i], allowed)
                }
            }
            if (b.isProductive) return b.build()
        }
        return null
    }

    private fun shiftOr(sums: Long, mask: Int): Long {
        var out = 0L
        var bits = mask
        while (bits != 0) {
            val v = Bits.lowest(bits)
            bits = bits and (bits - 1)
            out = out or (sums shl v)
        }
        return out
    }

    private fun convolve(x: Long, y: Long): Long {
        var out = 0L
        var bits = x
        while (bits != 0L) {
            val s = bits.countTrailingZeroBits()
            bits = bits and (bits - 1)
            out = out or (y shl s)
        }
        return out
    }

    /** True if some sum `s` in the bitset [others] makes `s` satisfy the rule against [need]. */
    private fun sumReachable(type: Int, need: Int, others: Long): Boolean {
        if (others == 0L) return false
        return when (type) {
            Spec.SUM_EQ -> need in 0..62 && (others ushr need) and 1L == 1L
            Spec.SUM_LT -> others.countTrailingZeroBits() < need
            else -> (63 - others.countLeadingZeroBits()) > need
        }
    }

    // ---------------------------------------------------------------- 3. equal propagation

    private fun equalPropagation(k: Knowledge, a: Analysis): Deduction? {
        val spec = a.spec
        for (r in 0 until spec.regionCount) {
            if (spec.rType[r] != Spec.EQUAL || a.rUnknown[r] == 0) continue
            val cells = spec.rCells[r]
            var inter = Domino.ALL_VALUES_MASK
            for (c in cells) inter = inter and a.values[c]
            // Every open cell of the region needs its own half of the shared value.
            val need = a.rUnknown[r]
            var allowed = 0
            var bits = inter
            while (bits != 0) {
                val v = Bits.lowest(bits)
                bits = bits and (bits - 1)
                if (a.spare[v] >= need) allowed = allowed or (1 shl v)
            }
            val b = DeductionBuilder(k, Technique.EQUAL_PROPAGATION, r)
            for (c in cells) {
                if (a.isKnown(c)) continue
                b.focus(c)
                b.keepValues(c, allowed)
            }
            if (b.isProductive) return b.build()
        }
        return null
    }

    // ---------------------------------------------------------------- 4. all-different elimination

    private fun allDifferent(k: Knowledge, a: Analysis): Deduction? {
        val spec = a.spec
        for (r in 0 until spec.regionCount) {
            if (spec.rType[r] != Spec.ALL_DIFFERENT || a.rUnknown[r] == 0) continue
            val cells = spec.rCells[r]
            var known = 0
            for (c in cells) if (a.isKnown(c)) known = known or a.values[c]
            val open = cells.filter { !a.isKnown(it) }
            val b = DeductionBuilder(k, Technique.ALL_DIFFERENT, r)
            for (c in open) {
                b.focus(c)
                b.removeValues(c, known)
            }
            if (b.isProductive) return b.build()
            // Naked subsets: s open cells whose candidates span exactly s values claim those values.
            val m = open.size
            if (m >= 3) {
                for (subset in 1 until (1 shl m) - 1) {
                    val size = Bits.count(subset)
                    if (size < 2 || size > m - 1) continue
                    var union = 0
                    for (i in 0 until m) if (subset and (1 shl i) != 0) union = union or a.values[open[i]]
                    if (Bits.count(union) != size) continue
                    val nb = DeductionBuilder(k, Technique.ALL_DIFFERENT, r)
                    for (i in 0 until m) {
                        if (subset and (1 shl i) != 0) nb.focus(open[i]) else nb.removeValues(open[i], union)
                    }
                    if (nb.isProductive) return nb.build()
                }
            }
        }
        return null
    }

    // ---------------------------------------------------------------- 5. domino-adjacency forcing

    private fun dominoAdjacency(k: Knowledge, a: Analysis): Deduction? {
        val spec = a.spec
        val sup = IntArray(2)
        for (c in 0 until a.n) {
            if (a.placed[c]) continue
            val b = DeductionBuilder(k, Technique.DOMINO_ADJACENCY).focus(c)
            var supported = 0
            for (dir in 0 until 4) {
                if (a.links[c] and (1 shl dir) == 0) continue
                val d = spec.nbr[c * 4 + dir]
                if (a.linkSupport(c, d, sup)) {
                    supported = supported or sup[0]
                } else {
                    b.removeLink(c, dir)
                    b.focus(d)
                }
            }
            b.keepValues(c, supported)
            if (b.isProductive) return b.build()
        }
        return null
    }

    // ---------------------------------------------------------------- 6. tray counting

    private fun trayCounting(k: Knowledge, a: Analysis): Deduction? {
        val spec = a.spec
        // Halves of a value against the open cells that can still show it.
        for (v in 0 until Domino.VALUE_COUNT) {
            val bit = 1 shl v
            val cand = ArrayList<Int>()
            for (c in 0 until a.n) if (!a.placed[c] && !a.isKnown(c) && a.values[c] and bit != 0) cand.add(c)
            if (cand.isEmpty()) continue
            val b = DeductionBuilder(k, Technique.TRAY_COUNTING)
            if (a.spare[v] == 0) {
                for (c in cand) b.focus(c).removeValues(c, bit)
            } else if (a.spare[v] == cand.size) {
                for (c in cand) b.focus(c).keepValues(c, bit)
            }
            if (b.isProductive) return b.build()
        }
        // A domino with a single copy left and all its possible spots sharing a cell.
        for (t in 0 until Domino.TYPE_COUNT) {
            if (a.avail[t] != 1) continue
            val lo = Domino.lowOf(t)
            val hi = Domino.highOf(t)
            var count = 0
            var common = -1L
            var firstC = -1
            var firstD = -1
            var firstA = -1
            var firstB = -1
            for (c in 0 until a.n) {
                if (a.placed[c]) continue
                for (dir in 0 until 4) {
                    if (a.links[c] and (1 shl dir) == 0) continue
                    val d = spec.nbr[c * 4 + dir]
                    if (d < c || a.placed[d]) continue
                    for (o in 0 until (if (lo == hi) 1 else 2)) {
                        val va = if (o == 0) lo else hi
                        val vb = if (o == 0) hi else lo
                        if (!a.fits(c, va, d, vb)) continue
                        count++
                        common = common and ((1L shl c) or (1L shl d))
                        if (count == 1) {
                            firstC = c
                            firstD = d
                            firstA = va
                            firstB = vb
                        }
                    }
                }
            }
            if (count == 0) continue
            if (count == 1) {
                val b = DeductionBuilder(k, Technique.TRAY_COUNTING)
                    .focus(firstC).focus(firstD)
                    .keepValues(firstC, 1 shl firstA)
                    .keepValues(firstD, 1 shl firstB)
                    .pair(firstC, firstD)
                if (b.isProductive) return b.build()
                continue
            }
            var bits = common
            while (bits != 0L) {
                val x = bits.countTrailingZeroBits()
                bits = bits and (bits - 1)
                val d = restrictCommonCell(k, a, t, x)
                if (d != null) return d
            }
        }
        return null
    }

    /** Every spot for the last copy of domino [t] covers [x]: restrict [x]'s partners and values. */
    private fun restrictCommonCell(k: Knowledge, a: Analysis, t: Int, x: Int): Deduction? {
        val lo = Domino.lowOf(t)
        val hi = Domino.highOf(t)
        var valuesOk = 0
        var dirsOk = 0
        for (dir in 0 until 4) {
            if (a.links[x] and (1 shl dir) == 0) continue
            val d = a.spec.nbr[x * 4 + dir]
            if (a.placed[d]) continue
            if (a.fits(x, lo, d, hi)) {
                valuesOk = valuesOk or (1 shl lo)
                dirsOk = dirsOk or (1 shl dir)
            }
            if (lo != hi && a.fits(x, hi, d, lo)) {
                valuesOk = valuesOk or (1 shl hi)
                dirsOk = dirsOk or (1 shl dir)
            }
        }
        val b = DeductionBuilder(k, Technique.TRAY_COUNTING).focus(x).keepValues(x, valuesOk)
        for (dir in 0 until 4) if (dirsOk and (1 shl dir) == 0) b.removeLink(x, dir)
        return if (b.isProductive) b.build() else null
    }

    // ---------------------------------------------------------------- 7. two-region interaction

    private fun twoRegion(k: Knowledge, a: Analysis): Deduction? {
        val spec = a.spec
        for (r in 0 until spec.regionCount) {
            if (a.rUnknown[r] == 0) continue
            val d = cluster(k, a, intArrayOf(r))
            if (d != null) return d
        }
        for (r in 0 until spec.regionCount) {
            if (a.rUnknown[r] == 0) continue
            for (s in r + 1 until spec.regionCount) {
                if (a.rUnknown[s] == 0 || !linkedByDomino(a, r, s)) continue
                val d = cluster(k, a, intArrayOf(r, s))
                if (d != null) return d
            }
        }
        return null
    }

    private fun linkedByDomino(a: Analysis, r: Int, s: Int): Boolean {
        for (c in a.spec.rCells[r]) {
            val d = a.partner[c]
            if (d >= 0 && !a.placed[c] && a.spec.regionOf[d] == s) return true
        }
        return false
    }

    /**
     * Enumerates every assignment of the open cells of [regions] consistent with their rules, the
     * spare half-values, and the dominoes needed by established pairs; drops unsupported values.
     */
    private fun cluster(k: Knowledge, a: Analysis, regions: IntArray): Deduction? {
        val spec = a.spec
        val cells = ArrayList<Int>()
        for (r in regions) for (c in spec.rCells[r]) if (!a.isKnown(c)) cells.add(c)
        val m = cells.size
        if (m == 0) return null
        val pos = IntArray(a.n) { -1 }
        for (i in 0 until m) pos[cells[i]] = i
        val e = ClusterEnumerator(a, regions, cells.toIntArray(), pos)
        if (!e.run()) return null
        val b = DeductionBuilder(k, Technique.TWO_REGION, regions[0])
        for (i in 0 until m) {
            b.focus(cells[i])
            b.keepValues(cells[i], e.supported[i])
        }
        return if (b.isProductive) b.build() else null
    }

    private class ClusterEnumerator(
        val a: Analysis,
        val regions: IntArray,
        val cells: IntArray,
        val pos: IntArray,
    ) {
        val spec = a.spec
        val m = cells.size
        val assigned = IntArray(m) { -1 }
        val supported = IntArray(m)
        val halvesUsed = IntArray(Domino.VALUE_COUNT)
        val tileUsed = IntArray(Domino.TYPE_COUNT)
        var nodes = 0
        var fullySupported = false

        /** Returns false if the enumeration was abandoned (node budget) or proved nothing. */
        fun run(): Boolean {
            dfs(0)
            if (nodes > CLUSTER_NODE_LIMIT) return false
            return !fullySupported
        }

        private fun dfs(i: Int) {
            if (fullySupported || nodes > CLUSTER_NODE_LIMIT) return
            nodes++
            if (i == m) {
                for (r in regions) if (!regionComplete(r)) return
                var all = true
                for (j in 0 until m) {
                    supported[j] = supported[j] or (1 shl assigned[j])
                    if (supported[j] != a.values[cells[j]]) all = false
                }
                if (all) fullySupported = true
                return
            }
            val c = cells[i]
            var bits = a.values[c]
            while (bits != 0) {
                val v = Bits.lowest(bits)
                bits = bits and (bits - 1)
                if (halvesUsed[v] >= a.spare[v]) continue
                val tile = pairTile(c, v)
                if (tile == -2) continue
                if (tile >= 0 && tileUsed[tile] >= a.avail[tile]) continue
                assigned[i] = v
                halvesUsed[v]++
                if (tile >= 0) tileUsed[tile]++
                if (regionPartialOk(spec.regionOf[c])) dfs(i + 1)
                if (tile >= 0) tileUsed[tile]--
                halvesUsed[v]--
                assigned[i] = -1
            }
        }

        /**
         * Domino type completed by putting [v] on [c] when its established partner is already
         * determined (known or assigned earlier): the type index, -1 if no domino is completed, or
         * -2 if no available domino can fit.
         */
        private fun pairTile(c: Int, v: Int): Int {
            val d = a.partner[c]
            if (d < 0) return -1
            val pd = pos[d]
            val w = when {
                pd >= 0 && assigned[pd] >= 0 -> assigned[pd]
                pd < 0 && a.isKnown(d) -> Bits.lowest(a.values[d])
                pd >= 0 -> return -1
                else -> {
                    // Partner open and outside the cluster: some fitting domino must remain.
                    var bits = a.values[d]
                    while (bits != 0) {
                        val x = Bits.lowest(bits)
                        bits = bits and (bits - 1)
                        if (a.avail[Domino.indexOf(v, x)] > 0) return -1
                    }
                    return -2
                }
            }
            val t = Domino.indexOf(v, w)
            return if (a.avail[t] > 0) t else -2
        }

        private fun valueOf(c: Int): Int {
            val p = pos[c]
            return if (p >= 0) assigned[p] else Bits.lowest(a.values[c])
        }

        private fun regionPartialOk(r: Int): Boolean {
            if (r < 0) return true
            val type = spec.rType[r]
            var sum = 0
            var min = 0
            var max = 0
            var seen = 0
            var shared = -1
            for (c in spec.rCells[r]) {
                val v = valueOf(c)
                if (v < 0) {
                    min += Bits.lowest(a.values[c])
                    max += Bits.highest(a.values[c])
                    continue
                }
                sum += v
                when (type) {
                    Spec.EQUAL -> {
                        if (shared >= 0 && shared != v) return false
                        shared = v
                    }
                    Spec.ALL_DIFFERENT -> {
                        if (seen and (1 shl v) != 0) return false
                        seen = seen or (1 shl v)
                    }
                }
            }
            return Spec.sumFeasible(type, spec.rTarget[r], sum, min, max)
        }

        private fun regionComplete(r: Int): Boolean = regionPartialOk(r)
    }

    // ---------------------------------------------------------------- 8. limited lookahead

    private fun lookahead(k: Knowledge): Deduction? {
        val spec = k.spec
        val order = (0 until spec.n)
            .filter { Bits.count(k.values[it]) >= 2 }
            .sortedWith(compareBy({ Bits.count(k.values[it]) }, { it }))
        var hypotheses = 0
        for (c in order) {
            var bits = k.values[c]
            while (bits != 0) {
                val v = Bits.lowest(bits)
                bits = bits and (bits - 1)
                if (++hypotheses > LOOKAHEAD_HYPOTHESIS_LIMIT) return null
                val trial = k.copy()
                trial.values[c] = 1 shl v
                if (refutes(trial)) {
                    return DeductionBuilder(k, Technique.LOOKAHEAD).focus(c).removeValues(c, 1 shl v).build()
                }
            }
        }
        val pairOrder = (0 until spec.n)
            .filter { Bits.count(k.links[it]) >= 2 }
            .sortedWith(compareBy({ Bits.count(k.links[it]) }, { it }))
        for (c in pairOrder) {
            for (dir in 0 until 4) {
                if (k.links[c] and (1 shl dir) == 0) continue
                if (++hypotheses > LOOKAHEAD_HYPOTHESIS_LIMIT) return null
                val d = spec.nbr[c * 4 + dir]
                val trial = k.copy()
                trial.fixPair(c, d)
                if (refutes(trial)) {
                    return DeductionBuilder(k, Technique.LOOKAHEAD).focus(c).focus(d).removeLink(c, dir).build()
                }
            }
        }
        return null
    }

    /** True if techniques 1–6 drive [trial] into a contradiction. */
    private fun refutes(trial: Knowledge): Boolean {
        var steps = 0
        while (steps < LOOKAHEAD_STEP_LIMIT) {
            val a = Analysis(trial)
            if (a.hasContradiction()) return true
            if (trial.isSolved) return false
            val d = next(trial, a, LOOKAHEAD_MAX_LEVEL) ?: return false
            apply(trial, d)
            steps++
        }
        return false
    }
}

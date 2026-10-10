package app.bonedle.engine.solver

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Region

/**
 * Result of an exact solve.
 *
 * @property solutionCount number of solutions found, capped at the requested maximum.
 * @property solutions the solutions found, each in canonical form sorted by [Placement.cellA].
 * @property nodes search nodes explored.
 * @property exhausted true if the node limit was hit before the search finished; the count is then
 *   only a lower bound.
 */
class SolveResult(
    val solutionCount: Int,
    val solutions: List<List<Placement>>,
    val nodes: Long,
    val exhausted: Boolean,
) {
    /** True if the search completed and found exactly one solution. */
    val isUnique: Boolean get() = !exhausted && solutionCount == 1
}

/**
 * Exact backtracking solver.
 *
 * Search branches on the open cell with the fewest legal domino placements. After every placement
 * it propagates: per-cell value domains under each region's partial-sum bounds (computed from the
 * half-values still in the tray), equality and all-different pruning, a pigeonhole check that
 * every remaining half-value still has enough cells able to show it, and a check that every
 * remaining domino still has somewhere to go.
 *
 * Two placements are the same solution only if they put the same values on the same cell pairs.
 */
object ExactSolver {
    /** Default node budget; generous for every supported board size. */
    const val DEFAULT_NODE_LIMIT: Long = 2_000_000L

    /**
     * Solves the puzzle defined by [board], [regions] and [tray], stopping after [maxSolutions]
     * solutions or [nodeLimit] nodes.
     */
    fun solve(
        board: Board,
        regions: List<Region>,
        tray: List<Domino>,
        maxSolutions: Int = 2,
        nodeLimit: Long = DEFAULT_NODE_LIMIT,
    ): SolveResult = solve(Spec.of(board, regions, tray), maxSolutions, nodeLimit)

    internal fun solve(spec: Spec, maxSolutions: Int = 2, nodeLimit: Long = DEFAULT_NODE_LIMIT): SolveResult {
        require(maxSolutions >= 1) { "maxSolutions must be at least 1" }
        val search = Search(spec, maxSolutions, nodeLimit)
        search.run()
        return SolveResult(search.solutions.size, search.solutions, search.nodes, search.aborted)
    }

    private val LOW = IntArray(Domino.TYPE_COUNT) { Domino.fromIndex(it).low }
    private val HIGH = IntArray(Domino.TYPE_COUNT) { Domino.fromIndex(it).high }

    private class Search(val spec: Spec, val maxSolutions: Int, val nodeLimit: Long) {
        val n = spec.n
        val nbr = spec.nbr
        val regionOf = spec.regionOf
        val value = IntArray(n) { -1 }
        val partner = IntArray(n) { -1 }
        val tray = spec.trayCounts.copyOf()
        val half = IntArray(Domino.VALUE_COUNT)
        val rSum = IntArray(spec.regionCount)
        val rFilled = IntArray(spec.regionCount)
        val rDistinct = IntArray(spec.regionCount)
        val rCount = IntArray(spec.regionCount * Domino.VALUE_COUNT)
        val maxDepth = n / 2 + 2
        val domains = Array(maxDepth) { IntArray(n) }
        val solutions = ArrayList<List<Placement>>()
        var nodes = 0L
        var aborted = false

        init {
            for (t in 0 until Domino.TYPE_COUNT) {
                val c = tray[t]
                if (c > 0) {
                    half[LOW[t]] += c
                    half[HIGH[t]] += c
                }
            }
        }

        fun run() {
            if (!allRegionsFeasible()) return
            recurse(0)
        }

        private fun allRegionsFeasible(): Boolean {
            for (r in 0 until spec.regionCount) if (!regionFeasible(r)) return false
            return true
        }

        private fun put(c: Int, v: Int) {
            value[c] = v
            half[v]--
            val r = regionOf[c]
            if (r >= 0) {
                rSum[r] += v
                rFilled[r]++
                if (rCount[r * Domino.VALUE_COUNT + v]++ == 0) rDistinct[r]++
            }
        }

        private fun take(c: Int) {
            val v = value[c]
            value[c] = -1
            half[v]++
            val r = regionOf[c]
            if (r >= 0) {
                rSum[r] -= v
                rFilled[r]--
                if (--rCount[r * Domino.VALUE_COUNT + v] == 0) rDistinct[r]--
            }
        }

        private fun minOfRemaining(k: Int): Int {
            var need = k
            var acc = 0
            var v = 0
            while (need > 0 && v < Domino.VALUE_COUNT) {
                val take = if (half[v] < need) half[v] else need
                acc += take * v
                need -= take
                v++
            }
            return if (need > 0) Int.MAX_VALUE / 4 else acc
        }

        private fun maxOfRemaining(k: Int): Int {
            var need = k
            var acc = 0
            var v = Domino.MAX_VALUE
            while (need > 0 && v >= 0) {
                val take = if (half[v] < need) half[v] else need
                acc += take * v
                need -= take
                v--
            }
            return if (need > 0) -1 else acc
        }

        fun regionFeasible(r: Int): Boolean {
            val k = spec.rSize[r] - rFilled[r]
            val type = spec.rType[r]
            when (type) {
                Spec.EQUAL -> {
                    if (rDistinct[r] > 1) return false
                    if (k == 0) return true
                    if (rDistinct[r] == 1) {
                        val base = r * Domino.VALUE_COUNT
                        for (v in 0 until Domino.VALUE_COUNT) if (rCount[base + v] > 0) return half[v] >= k
                        return false
                    }
                    for (v in 0 until Domino.VALUE_COUNT) if (half[v] >= k) return true
                    return false
                }
                Spec.ALL_DIFFERENT -> {
                    if (rDistinct[r] != rFilled[r]) return false
                    if (k == 0) return true
                    val base = r * Domino.VALUE_COUNT
                    var free = 0
                    for (v in 0 until Domino.VALUE_COUNT) if (rCount[base + v] == 0 && half[v] > 0) free++
                    return free >= k
                }
                else -> {
                    if (k == 0) return Spec.sumOk(type, spec.rTarget[r], rSum[r])
                    val min = minOfRemaining(k)
                    val max = maxOfRemaining(k)
                    if (max < 0) return false
                    return Spec.sumFeasible(type, spec.rTarget[r], rSum[r], min, max)
                }
            }
        }

        /**
         * Fills [dom] with each open cell's legal values, starting from the parent node's domains
         * [parent] (domains only shrink with depth); returns false on a dead end.
         */
        private fun computeDomains(dom: IntArray, parent: IntArray?, types: IntArray, typeCount: Int): Boolean {
            var available = 0
            for (v in 0 until Domino.VALUE_COUNT) if (half[v] > 0) available = available or (1 shl v)
            for (r in 0 until spec.regionCount) regionAllowed[r] = allowedValues(r, available)
            for (c in 0 until n) {
                if (value[c] >= 0) {
                    dom[c] = 0
                    continue
                }
                val start = if (parent == null) available else available and parent[c]
                val r = regionOf[c]
                val mask = if (r < 0) start else start and regionAllowed[r]
                if (mask == 0) return false
                dom[c] = mask
            }
            repeat(2) { if (!propagateRegions(dom)) return false }
            // Tile support: a value survives only if some open neighbour can complete a domino with it.
            for (v in 0 until Domino.VALUE_COUNT) partnersOf[v] = 0
            for (i in 0 until typeCount) {
                val t = types[i]
                val lo = LOW[t]
                val hi = HIGH[t]
                partnersOf[lo] = partnersOf[lo] or (1 shl hi)
                partnersOf[hi] = partnersOf[hi] or (1 shl lo)
            }
            repeat(2) {
                for (c in 0 until n) {
                    val dc = dom[c]
                    if (dc == 0) continue
                    var near = 0
                    for (dir in 0 until 4) {
                        val d = nbr[c * 4 + dir]
                        if (d >= 0 && value[d] < 0) near = near or dom[d]
                    }
                    var kept = 0
                    var bits = dc
                    while (bits != 0) {
                        val v = Bits.lowest(bits)
                        bits = bits and (bits - 1)
                        if (partnersOf[v] and near != 0) kept = kept or (1 shl v)
                    }
                    if (kept == 0) return false
                    dom[c] = kept
                }
            }
            // Pigeonhole: every remaining half of value v needs its own cell able to show v.
            for (v in 0 until Domino.VALUE_COUNT) {
                if (half[v] == 0) continue
                var cells = 0
                val bit = 1 shl v
                for (c in 0 until n) if (dom[c] and bit != 0) cells++
                if (cells < half[v]) return false
            }
            // Every remaining domino type needs some adjacent open pair able to show it.
            for (v in 0 until Domino.VALUE_COUNT) pairMask[v] = 0
            for (c in 0 until n) {
                val dc = dom[c]
                if (dc == 0) continue
                for (dir in 1..2) {
                    val d = nbr[c * 4 + dir]
                    if (d < 0 || value[d] >= 0) continue
                    val dd = dom[d]
                    var bits = dc
                    while (bits != 0) {
                        val a = Bits.lowest(bits)
                        bits = bits and (bits - 1)
                        pairMask[a] = pairMask[a] or dd
                    }
                    bits = dd
                    while (bits != 0) {
                        val b = Bits.lowest(bits)
                        bits = bits and (bits - 1)
                        pairMask[b] = pairMask[b] or dc
                    }
                }
            }
            for (i in 0 until typeCount) {
                val t = types[i]
                if (pairMask[LOW[t]] and (1 shl HIGH[t]) == 0) return false
            }
            return true
        }

        /**
         * Narrows open-cell domains region by region: sum bounds against the other cells' domain
         * extremes, equal-region intersection, and all-different singleton removal.
         */
        private fun propagateRegions(dom: IntArray): Boolean {
            for (r in 0 until spec.regionCount) {
                if (rFilled[r] == spec.rSize[r]) continue
                val cells = spec.rCells[r]
                when (val type = spec.rType[r]) {
                    Spec.EQUAL -> {
                        var inter = Domino.ALL_VALUES_MASK
                        for (c in cells) inter = inter and (if (value[c] >= 0) 1 shl value[c] else dom[c])
                        if (inter == 0) return false
                        for (c in cells) if (value[c] < 0) dom[c] = inter
                    }
                    Spec.ALL_DIFFERENT -> {
                        var fixed = 0
                        for (c in cells) if (value[c] < 0 && Bits.isSingle(dom[c])) {
                            if (fixed and dom[c] != 0) return false
                            fixed = fixed or dom[c]
                        }
                        if (fixed != 0) {
                            for (c in cells) {
                                if (value[c] >= 0 || Bits.isSingle(dom[c])) continue
                                dom[c] = dom[c] and fixed.inv()
                                if (dom[c] == 0) return false
                            }
                        }
                    }
                    else -> {
                        var min = rSum[r]
                        var max = rSum[r]
                        for (c in cells) if (value[c] < 0) {
                            min += Bits.lowest(dom[c])
                            max += Bits.highest(dom[c])
                        }
                        if (!Spec.sumFeasible(type, spec.rTarget[r], 0, min, max)) return false
                        val target = spec.rTarget[r]
                        for (c in cells) {
                            if (value[c] >= 0) continue
                            val d = dom[c]
                            val minOthers = min - Bits.lowest(d)
                            val maxOthers = max - Bits.highest(d)
                            var kept = 0
                            var bits = d
                            while (bits != 0) {
                                val v = Bits.lowest(bits)
                                bits = bits and (bits - 1)
                                if (Spec.sumFeasible(type, target, v, minOthers, maxOthers)) kept = kept or (1 shl v)
                            }
                            if (kept == 0) return false
                            if (kept != d) {
                                dom[c] = kept
                                min = minOthers + Bits.lowest(kept)
                                max = maxOthers + Bits.highest(kept)
                            }
                        }
                    }
                }
            }
            return true
        }

        private val regionAllowed = IntArray(spec.regionCount)

        /**
         * Values any one open cell of region [r] could show, given the region's filled cells and
         * the remaining half-values. The same for every open cell of the region, so it is computed
         * once per node. Bounds use the current halves (not excluding the tested value), which can
         * only loosen them, so the check stays sound.
         */
        private fun allowedValues(r: Int, available: Int): Int {
            val k = spec.rSize[r] - rFilled[r]
            if (k == 0) return 0
            val base = r * Domino.VALUE_COUNT
            return when (val type = spec.rType[r]) {
                Spec.EQUAL -> when (rDistinct[r]) {
                    0 -> {
                        var mask = 0
                        for (v in 0 until Domino.VALUE_COUNT) if (half[v] >= k) mask = mask or (1 shl v)
                        mask
                    }
                    1 -> {
                        var mask = 0
                        for (v in 0 until Domino.VALUE_COUNT) if (rCount[base + v] > 0 && half[v] >= k) mask = 1 shl v
                        mask
                    }
                    else -> 0
                }
                Spec.ALL_DIFFERENT -> {
                    if (rDistinct[r] != rFilled[r]) return 0
                    var mask = 0
                    var free = 0
                    for (v in 0 until Domino.VALUE_COUNT) {
                        if (rCount[base + v] == 0 && half[v] > 0) {
                            mask = mask or (1 shl v)
                            free++
                        }
                    }
                    if (free >= k) mask else 0
                }
                else -> {
                    val minRest = minOfRemaining(k - 1)
                    val maxRest = maxOfRemaining(k - 1)
                    if (maxRest < 0) return 0
                    var mask = 0
                    var bits = available
                    while (bits != 0) {
                        val v = Bits.lowest(bits)
                        bits = bits and (bits - 1)
                        if (Spec.sumFeasible(type, spec.rTarget[r], rSum[r] + v, minRest, maxRest)) mask = mask or (1 shl v)
                    }
                    mask
                }
            }
        }

        private val partnersOf = IntArray(Domino.VALUE_COUNT)
        private val pairMask = IntArray(Domino.VALUE_COUNT)

        /** Per-region sum of the open cells' smallest / largest domain values, set each node. */
        private val rDomMin = IntArray(spec.regionCount)
        private val rDomMax = IntArray(spec.regionCount)

        private fun computeRegionBounds(dom: IntArray) {
            for (r in 0 until spec.regionCount) {
                var min = 0
                var max = 0
                for (c in spec.rCells[r]) {
                    if (value[c] >= 0) continue
                    min += Bits.lowest(dom[c])
                    max += Bits.highest(dom[c])
                }
                rDomMin[r] = min
                rDomMax[r] = max
            }
        }

        /**
         * True if putting [a] on [c] and [b] on [d] can satisfy a region shared by both, judged
         * against the other open cells' domain bounds. O(1); requires [computeRegionBounds].
         */
        private fun jointOk(c: Int, d: Int, a: Int, b: Int, dom: IntArray): Boolean {
            val r = regionOf[c]
            if (r < 0 || r != regionOf[d]) return true
            return when (val type = spec.rType[r]) {
                Spec.EQUAL -> a == b
                Spec.ALL_DIFFERENT -> a != b
                else -> {
                    val dc = dom[c]
                    val dd = dom[d]
                    val min = rDomMin[r] - Bits.lowest(dc) - Bits.lowest(dd)
                    val max = rDomMax[r] - Bits.highest(dc) - Bits.highest(dd)
                    Spec.sumFeasible(type, spec.rTarget[r], rSum[r] + a + b, min, max)
                }
            }
        }

        /** Counts legal placements covering open cell [c], stopping once [stopAt] is reached. */
        private fun countCandidates(c: Int, dom: IntArray, types: IntArray, typeCount: Int, stopAt: Int): Int {
            var count = 0
            val dc = dom[c]
            for (dir in 0 until 4) {
                val d = nbr[c * 4 + dir]
                if (d < 0 || value[d] >= 0) continue
                val dd = dom[d]
                for (i in 0 until typeCount) {
                    val t = types[i]
                    val lo = LOW[t]
                    val hi = HIGH[t]
                    if (dc and (1 shl lo) != 0 && dd and (1 shl hi) != 0 && jointOk(c, d, lo, hi, dom)) count++
                    if (lo != hi && dc and (1 shl hi) != 0 && dd and (1 shl lo) != 0 && jointOk(c, d, hi, lo, dom)) count++
                    if (count >= stopAt) return count
                }
            }
            return count
        }

        private val typeLists = Array(maxDepth) { IntArray(Domino.TYPE_COUNT) }

        private fun recurse(depth: Int) {
            if (aborted || solutions.size >= maxSolutions) return
            nodes++
            if (nodes > nodeLimit) {
                aborted = true
                return
            }
            var firstOpen = -1
            for (c in 0 until n) {
                if (value[c] < 0) {
                    firstOpen = c
                    break
                }
            }
            if (firstOpen < 0) {
                solutions.add(snapshot())
                return
            }
            val types = typeLists[depth]
            var typeCount = 0
            for (t in 0 until Domino.TYPE_COUNT) if (tray[t] > 0) types[typeCount++] = t
            val dom = domains[depth]
            if (!computeDomains(dom, if (depth == 0) null else domains[depth - 1], types, typeCount)) return
            computeRegionBounds(dom)

            // Branch on the open cell with the fewest legal placements.
            var best = -1
            var bestCount = Int.MAX_VALUE
            for (c in 0 until n) {
                if (value[c] >= 0) continue
                val count = countCandidates(c, dom, types, typeCount, bestCount)
                if (count == 0) return
                if (count < bestCount) {
                    bestCount = count
                    best = c
                }
            }
            branch(best, dom, types, typeCount, depth)
        }

        private fun branch(c: Int, dom: IntArray, types: IntArray, typeCount: Int, depth: Int) {
            val dc = dom[c]
            for (dir in 0 until 4) {
                val d = nbr[c * 4 + dir]
                if (d < 0 || value[d] >= 0) continue
                val dd = dom[d]
                for (i in 0 until typeCount) {
                    val t = types[i]
                    val lo = LOW[t]
                    val hi = HIGH[t]
                    if (dc and (1 shl lo) != 0 && dd and (1 shl hi) != 0) tryPlace(c, d, lo, hi, t, depth)
                    if (lo != hi && dc and (1 shl hi) != 0 && dd and (1 shl lo) != 0) tryPlace(c, d, hi, lo, t, depth)
                    if (aborted || solutions.size >= maxSolutions) return
                }
            }
        }

        private fun tryPlace(c: Int, d: Int, a: Int, b: Int, t: Int, depth: Int) {
            put(c, a)
            put(d, b)
            tray[t]--
            partner[c] = d
            partner[d] = c
            val rc = regionOf[c]
            val rd = regionOf[d]
            if ((rc < 0 || regionFeasible(rc)) && (rd < 0 || rd == rc || regionFeasible(rd))) recurse(depth + 1)
            partner[c] = -1
            partner[d] = -1
            tray[t]++
            take(d)
            take(c)
        }

        private fun snapshot(): List<Placement> {
            val out = ArrayList<Placement>(n / 2)
            for (c in 0 until n) {
                val d = partner[c]
                if (d > c) out.add(Placement(c, d, value[c], value[d]))
            }
            return out
        }
    }
}

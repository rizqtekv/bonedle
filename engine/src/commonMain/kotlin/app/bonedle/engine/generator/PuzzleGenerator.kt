package app.bonedle.engine.generator

import app.bonedle.engine.grader.GradeReport
import app.bonedle.engine.grader.HumanSolver
import app.bonedle.engine.grader.Knowledge
import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Direction
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Grade
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Region
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.RuleType
import app.bonedle.engine.model.Technique
import app.bonedle.engine.random.SplitMix64
import app.bonedle.engine.random.Xoshiro256StarStar
import app.bonedle.engine.solver.Bits
import app.bonedle.engine.solver.ExactSolver
import app.bonedle.engine.solver.SolveResult
import app.bonedle.engine.solver.Spec
import kotlin.time.TimeSource

/**
 * Counters describing how a puzzle was produced.
 *
 * @property attempts full generation attempts (1 = first try succeeded).
 * @property repairs uniqueness repairs applied in the successful attempt and the failed ones.
 * @property strengthenings constraints added because the grader got stuck.
 * @property loosenings exact sums relaxed to `<` / `>`.
 * @property merges region merges kept after the board became unique.
 * @property solverCalls exact-solver invocations.
 * @property solverNodes total exact-solver nodes.
 */
class GenerationStats internal constructor(
    val attempts: Int,
    val repairs: Int,
    val strengthenings: Int,
    val loosenings: Int,
    val merges: Int,
    val solverCalls: Int,
    val solverNodes: Long,
    /** Discarded attempts by reason. */
    val failures: Map<PuzzleGenerator.FailReason, Int>,
    /** Wall time spent in the exact solver, for reporting only (never used for decisions). */
    val solverMillis: Long,
    /** Wall time spent in the grader, for reporting only (never used for decisions). */
    val graderMillis: Long,
)

/** A generated puzzle together with how it was made and graded. */
class GeneratedPuzzle internal constructor(
    val puzzle: Puzzle,
    val stats: GenerationStats,
    /** Steps per technique in the grading solve, indexed by [Technique.level]. */
    val techniqueCounts: IntArray,
)

/**
 * Deterministic puzzle generator.
 *
 * The same `(seed, difficulty)` always yields the same puzzle on every platform: all randomness
 * comes from [Xoshiro256StarStar], all collections are iterated in index order, no decision
 * depends on time, and all arithmetic is integer. Pipeline per attempt:
 *
 * 1. **Shape** — a rectangle, or (Hard/Expert) an irregular shape grown one domino at a time.
 * 2. **Tiling** — random tiling, most constrained cell first.
 * 3. **Values** — distinct dominoes (duplicates allowed at Expert), hill-climbed so touching
 *    halves cluster; 2x2 blocks that could be rotated without changing any value are re-dealt.
 * 4. **Regions** — free cells chosen first under the uniqueness invariants (see `planRegions`),
 *    then rule-aware growth (equal cells for `=`, low cells for `<`, high cells for `>`).
 * 5. **Rules** — weighted by difficulty, tight targets.
 * 6. **Uniqueness** — exact solver, repairing against each alternative solution (≤ 40 repairs).
 * 7. **Grading** — the human-style solver; stuck boards are strengthened, then regions are
 *    merged and exact sums relaxed to `<` / `>` while the board stays unique and in band.
 *
 * A failed attempt continues with the same random stream, so the result is still determined by
 * the seed. Merges and loosening are accepted on the grader's word (a sound solver that reaches
 * the full solution proves uniqueness); the exact solver confirms every finished board.
 */
object PuzzleGenerator {
    /** Attempts before giving up; never reached in practice (see the property tests). */
    const val MAX_ATTEMPTS = 5_000

    /** Uniqueness repairs per attempt before restarting. */
    const val MAX_REPAIRS = 40

    /** Constraints added for a stuck grader before the attempt is abandoned. */
    private const val MAX_STRENGTHENINGS = 16

    /** Finished boards prove unique in a few hundred nodes; bigger searches mean a loose board. */
    private const val SOLVER_NODE_LIMIT = 10_000L

    /** Re-deals allowed while removing rotatable 2x2 blocks. */
    private const val FLIP_REROLLS = 60

    /** Techniques cheap enough to try before the exact solver when checking a candidate change. */
    private const val QUICK_LEVEL = 6

    /** Values a `<` region grows through, and the floor for `>` regions. */
    private const val LOW_MAX = 2
    private const val HIGH_MIN = 4

    /** One free cell per anchored value at most; see planRegions. */
    private const val MAX_FREE_CELLS = Domino.VALUE_COUNT

    /** Generates the puzzle for [seed] at [difficulty]. */
    fun generate(seed: Long, difficulty: Difficulty): Puzzle = generateDetailed(seed, difficulty).puzzle

    /** Generates the puzzle for [seed] at [difficulty], with generation statistics. */
    fun generateDetailed(seed: Long, difficulty: Difficulty): GeneratedPuzzle {
        val rng = Xoshiro256StarStar(seed xor SplitMix64.mix(difficulty.ordinal + 1L))
        val profile = DifficultyProfile.of(difficulty)
        val counters = Counters()
        for (attempt in 1..MAX_ATTEMPTS) {
            counters.attempts = attempt
            val result = Attempt(seed, difficulty, profile, rng, counters).run()
            if (result != null) {
                val stats = GenerationStats(
                    counters.attempts, counters.repairs, counters.strengthenings, counters.loosenings, counters.merges,
                    counters.solverCalls, counters.solverNodes,
                    FailReason.entries.associateWith { counters.failures[it.ordinal] }.filterValues { it > 0 },
                    counters.solverNanos / 1_000_000, counters.graderNanos / 1_000_000,
                )
                return GeneratedPuzzle(result.first, stats, result.second)
            }
        }
        throw IllegalStateException("No $difficulty puzzle for seed $seed after $MAX_ATTEMPTS attempts")
    }

    /** Why a generation attempt was discarded (reported in [GenerationStats.failures]). */
    enum class FailReason {
        /** Irregular shape growth ran out of room. */
        SHAPE,

        /** No domino tiling found within budget. */
        TILING,

        /** Not enough cells could be left free under the free-cell invariants. */
        ANCHOR,

        /** A region could not be given a rule. */
        FILL,

        /** Region coverage fell outside 55–80 %. */
        COVERAGE,

        /** The exact solver could not decide uniqueness within its node budget. */
        SOLVER_LIMIT,

        /** Two solutions with identical values but different tilings; no rule separates them. */
        TILING_AMBIGUOUS,

        /** No constraint change could rule out an alternative solution. */
        NO_REPAIR,

        /** Still not unique after [MAX_REPAIRS] repairs. */
        REPAIR_LIMIT,

        /** The human-style solver stayed stuck within the band's techniques. */
        GRADER_STUCK,

        /** Solvable with techniques easier than the band requires. */
        TOO_EASY,

        /** The exact solver did not confirm the grader's uniqueness proof. */
        UNCONFIRMED,

        /** Regions could not be coloured apart. */
        COLORING,
    }

    private class Counters {
        val failures = IntArray(FailReason.entries.size)
        var attempts = 0
        var repairs = 0
        var strengthenings = 0
        var loosenings = 0
        var merges = 0
        var solverCalls = 0
        var solverNodes = 0L
        var solverNanos = 0L
        var graderNanos = 0L
    }

    private class Draft(var cells: IntArray, var rule: Rule) {
        fun copy() = Draft(cells.copyOf(), rule)
    }

    private class Attempt(
        val seed: Long,
        val difficulty: Difficulty,
        val profile: DifficultyProfile,
        val rng: Xoshiro256StarStar,
        val counters: Counters,
    ) {
        lateinit var board: Board
        lateinit var values: IntArray
        lateinit var tray: List<Domino>
        lateinit var solution: List<Placement>
        var n = 0
        var drafts = ArrayList<Draft>()
        lateinit var regionOf: IntArray
        lateinit var partnerOf: IntArray
        var minCovered = 0
        var maxCovered = 0

        private fun fail(reason: FailReason): Pair<Puzzle, IntArray>? {
            counters.failures[reason.ordinal]++
            return null
        }

        private fun failed(reason: FailReason): Boolean {
            counters.failures[reason.ordinal]++
            return false
        }

        fun run(): Pair<Puzzle, IntArray>? {
            board = ShapeFactory.create(profile, rng) ?: return fail(FailReason.SHAPE)
            n = board.cellCount
            val partner = Tiler.randomTiling(board, rng) ?: return fail(FailReason.TILING)
            partnerOf = partner
            if (!assignValues(partner)) return fail(FailReason.TILING_AMBIGUOUS)
            regionOf = IntArray(n) { -1 }
            minCovered = (n * DifficultyProfile.MIN_COVERAGE_PERCENT + 99) / 100
            maxCovered = n * DifficultyProfile.MAX_COVERAGE_PERCENT / 100
            planRegions()?.let { return fail(it) }
            if (!makeUnique()) return null

            var report = grade()
            var strengthenings = 0
            while (!report.solved) {
                if (strengthenings >= MAX_STRENGTHENINGS || !strengthen(report.knowledge)) {
                    return fail(FailReason.GRADER_STUCK)
                }
                strengthenings++
                counters.strengthenings++
                report = grade()
            }
            report = merge(report)
            report = loosen(report)
            if (level(report) < profile.minLevel) return fail(FailReason.TOO_EASY)
            // Cross-check the grader's uniqueness proof with the exact solver.
            if (!solveExact().isUnique) return fail(FailReason.UNCONFIRMED)
            return assemble(report) ?: fail(FailReason.COLORING)
        }

        // ------------------------------------------------------------ values and tray

        private fun assignValues(partner: IntArray): Boolean {
            val k = n / 2
            val pool = IntArray(Domino.TYPE_COUNT) { it }
            rng.shuffle(pool)
            var next = 0
            val types = IntArray(k)
            val used = IntArray(Domino.TYPE_COUNT)
            for (i in 0 until k) {
                var t = -1
                if (profile.duplicatePercent > 0 && i > 0 && rng.chance(profile.duplicatePercent)) {
                    val candidate = types[rng.nextInt(i)]
                    if (used[candidate] < 2) t = candidate
                }
                if (t < 0) t = pool[next++]
                types[i] = t
                used[t]++
            }
            // Dominoes indexed by their lower cell, in cell order.
            val first = IntArray(k)
            var i = 0
            for (c in 0 until n) if (partner[c] > c) first[i++] = c
            values = IntArray(n)
            for (j in 0 until k) orient(first[j], partner[first[j]], types[j])
            clusterValues(first, types, partner)
            // Two parallel dominoes in a 2x2 block with a matching diagonal can be rotated a quarter
            // turn without changing any value or the tray; no rule can rule that out, so re-deal.
            var guard = 0
            while (true) {
                val c = flippableBlock(partner)
                if (c < 0) break
                if (++guard > FLIP_REROLLS) return false
                val j = first.indexOf(if (partner[c] > c) c else partner[c])
                val other = rng.nextInt(k)
                val tmp = types[j]
                types[j] = types[other]
                types[other] = tmp
                orient(first[j], partner[first[j]], types[j])
                orient(first[other], partner[first[other]], types[other])
            }
            val placements = ArrayList<Placement>(k)
            for (j in 0 until k) {
                val c = first[j]
                val d = partner[c]
                placements.add(Placement(c, d, values[c], values[d]))
            }
            solution = placements
            tray = placements.map { it.domino }.sorted()
            return true
        }

        /**
         * Hill-climbs domino orientations and positions so that touching halves of different
         * dominoes tend to be equal or close. Clustered values let equal, low-sum and high-sum
         * regions span several cells and still pin the solution; with scattered values only
         * one-cell regions carry enough information for a unique board.
         */
        private fun clusterValues(first: IntArray, types: IntArray, partner: IntArray) {
            val k = first.size
            var score = clusterScore(partner)
            repeat(profile.clusterIterations) {
                val savedValues = values.copyOf()
                val savedTypes = types.copyOf()
                val j = rng.nextInt(k)
                val other = rng.nextInt(k)
                if (other != j && rng.chance(60)) {
                    val tmp = types[j]
                    types[j] = types[other]
                    types[other] = tmp
                    orient(first[j], partner[first[j]], types[j])
                    orient(first[other], partner[first[other]], types[other])
                } else {
                    val c = first[j]
                    val d = partner[c]
                    val tmp = values[c]
                    values[c] = values[d]
                    values[d] = tmp
                }
                val next = clusterScore(partner)
                if (next >= score) {
                    score = next
                } else {
                    savedValues.copyInto(values)
                    savedTypes.copyInto(types)
                }
            }
        }

        private fun clusterScore(partner: IntArray): Int {
            var score = 0
            for (c in 0 until n) {
                for (dir in 1..2) {
                    val d = board.neighbor(c, dir)
                    if (d < 0 || partner[c] == d) continue
                    val diff = values[c] - values[d]
                    if (diff == 0) score += 3 else if (diff == 1 || diff == -1) score += 1
                }
            }
            return score
        }

        private fun orient(c: Int, d: Int, t: Int) {
            val lo = Domino.lowOf(t)
            val hi = Domino.highOf(t)
            if (rng.chance(50)) {
                values[c] = lo
                values[d] = hi
            } else {
                values[c] = hi
                values[d] = lo
            }
        }

        /** Top-left cell of a 2x2 block of two parallel dominoes that can be rotated, or -1. */
        private fun flippableBlock(partner: IntArray): Int {
            for (c in 0 until n) {
                val right = board.neighbor(c, Direction.EAST)
                val below = board.neighbor(c, Direction.SOUTH)
                if (right < 0 || below < 0) continue
                val diag = board.neighbor(right, Direction.SOUTH)
                if (diag < 0) continue
                val parallel = (partner[c] == right && partner[below] == diag) ||
                    (partner[c] == below && partner[right] == diag)
                if (parallel && (values[right] == values[below] || values[c] == values[diag])) return c
            }
            return -1
        }

        // ------------------------------------------------------------ regions and rules

        private fun covered(): Int = regionOf.count { it >= 0 }

        /** Re-sorts regions into canonical order (by first cell) and rebuilds the cell index. */
        private fun reindex() {
            drafts.sortBy { it.cells[0] }
            regionOf.fill(-1)
            drafts.forEachIndexed { r, d -> d.cells.forEach { regionOf[it] = r } }
        }

        private fun addDraft(cells: IntArray, rule: Rule) {
            cells.sort()
            drafts.add(Draft(cells, rule))
            reindex()
        }

        /**
         * Plans the initial regions.
         *
         * Free cells are chosen first. A domino lying wholly on free cells could be flipped (or
         * swapped with another free domino) without breaking any rule, and two dominoes whose free
         * halves sit next to regioned halves of equal value can trade free halves using the same
         * tiles. So each free cell is the only free half of its domino, the regioned partners of
         * free cells all show different values (hence at most seven free cells), and free cells
         * never touch each other. Every other cell is then partitioned into regions.
         */
        private fun planRegions(): FailReason? {
            val maxFree = minOf(MAX_FREE_CELLS, n - minCovered)
            val minFree = n - maxCovered
            if (minFree > maxFree) return FailReason.COVERAGE
            val wanted = rng.nextIntInclusive(minFree, maxFree)
            val free = BooleanArray(n)
            var anchorValues = 0
            var freeCount = 0
            val order = IntArray(n) { it }
            rng.shuffle(order)
            for (f in order) {
                if (freeCount == wanted) break
                val p = partnerOf[f]
                if (free[p] || anchorValues and (1 shl values[p]) != 0) continue
                var touching = false
                for (dir in 0 until 4) {
                    val d = board.neighbor(f, dir)
                    if (d >= 0 && free[d]) touching = true
                }
                if (touching) continue
                free[f] = true
                anchorValues = anchorValues or (1 shl values[p])
                freeCount++
            }
            if (freeCount < minFree) return FailReason.ANCHOR
            rng.shuffle(order)
            for (next in order) {
                if (free[next] || regionOf[next] >= 0) continue
                var type = RuleType.entries[rng.weightedIndex(initialWeights)]
                var seedCell = next
                val seedOk: ((Int) -> Boolean)? = when (type) {
                    // An equal region needs a seed with an equal-valued open neighbour.
                    RuleType.EQUAL -> { c -> hasEqualNeighbor(c, free) }
                    RuleType.SUM_LT -> { c -> values[c] <= LOW_MAX }
                    RuleType.SUM_GT -> { c -> values[c] >= HIGH_MIN }
                    else -> null
                }
                if (seedOk != null) {
                    val seeds = (0 until n).filter { c -> !free[c] && regionOf[c] < 0 && seedOk(c) }
                    if (seeds.isEmpty()) type = RuleType.SUM_EQ else seedCell = seeds[rng.nextInt(seeds.size)]
                }
                val size = rng.weightedIndex(profile.regionSizeWeights)
                val cells = grow(seedCell, size, type, free)
                if (cells.size < 2 && (type == RuleType.EQUAL || type == RuleType.ALL_DIFFERENT)) {
                    type = RuleType.SUM_EQ
                }
                val rule = makeRule(type, cells) ?: pickRule(ruleOptions(cells), null) ?: return FailReason.FILL
                addDraft(cells, rule)
            }
            // Seeds taken out of order can leave unassigned cells behind; give each its own region.
            for (c in 0 until n) {
                if (free[c] || regionOf[c] >= 0) continue
                val cells = grow(c, rng.weightedIndex(profile.regionSizeWeights), RuleType.SUM_EQ, free)
                addDraft(cells, makeRule(RuleType.SUM_EQ, cells) ?: return FailReason.FILL)
            }
            return if (covered() in minCovered..maxCovered) null else FailReason.COVERAGE
        }

        private fun hasEqualNeighbor(c: Int, free: BooleanArray): Boolean {
            for (dir in 0 until 4) {
                val d = board.neighbor(c, dir)
                if (d >= 0 && !free[d] && regionOf[d] < 0 && values[d] == values[c]) return true
            }
            return false
        }

        /** Grows a region from [seedCell] through unassigned, non-[blocked] cells compatible with [type]. */
        private fun grow(seedCell: Int, size: Int, type: RuleType, blocked: BooleanArray? = null): IntArray {
            val cells = ArrayList<Int>()
            cells.add(seedCell)
            var seen = 1 shl values[seedCell]
            val frontier = ArrayList<Int>()
            while (cells.size < size) {
                frontier.clear()
                for (c in cells) {
                    for (dir in 0 until 4) {
                        val d = board.neighbor(c, dir)
                        if (d < 0 || regionOf[d] >= 0 || d in cells || d in frontier) continue
                        if (blocked != null && blocked[d]) continue
                        val ok = when (type) {
                            RuleType.EQUAL -> values[d] == values[seedCell]
                            RuleType.ALL_DIFFERENT -> seen and (1 shl values[d]) == 0
                            RuleType.SUM_LT -> values[d] <= LOW_MAX
                            RuleType.SUM_GT -> values[d] >= HIGH_MIN
                            RuleType.SUM_EQ -> true
                        }
                        if (ok) frontier.add(d)
                    }
                }
                if (frontier.isEmpty()) break
                frontier.sort()
                val pick = frontier[rng.nextInt(frontier.size)]
                cells.add(pick)
                seen = seen or (1 shl values[pick])
            }
            return cells.toIntArray()
        }

        private fun slack(): Int = rng.nextIntInclusive(0, profile.maxSlack)

        /** A rule of [type] true for the solution on [cells], or null if [type] cannot hold. */
        private fun makeRule(type: RuleType, cells: IntArray): Rule? {
            val k = cells.size
            var sum = 0
            var mask = 0
            var distinct = true
            for (c in cells) {
                val v = values[c]
                sum += v
                if (mask and (1 shl v) != 0) distinct = false
                mask = mask or (1 shl v)
            }
            val max = Domino.MAX_VALUE * k
            return when (type) {
                RuleType.EQUAL -> if (k >= 2 && Bits.isSingle(mask)) Rule.EQUAL else null
                RuleType.ALL_DIFFERENT -> if (k >= 2 && distinct) Rule.ALL_DIFFERENT else null
                RuleType.SUM_EQ -> Rule.sumEq(sum)
                // Tight targets while planning and repairing; [loosen] adds slack later.
                RuleType.SUM_LT -> if (sum < max) Rule.sumLt(sum + 1) else null
                RuleType.SUM_GT -> if (sum > 0) Rule.sumGt(sum - 1) else null
            }
        }

        private fun ruleOptions(cells: IntArray): List<Rule> =
            RuleType.entries.mapNotNull { makeRule(it, cells) }

        /** Rule weights used while planning and repairing (`<` / `>` with tight targets). */
        private val initialWeights = profile.ruleWeights

        /**
         * Weighted pick among [options] by [initialWeights], keeping only rules [alt] violates when
         * [alt] is given. Zero-weight rules are used only when nothing else qualifies.
         */
        private fun pickRule(options: List<Rule>, alt: IntArray?, cells: IntArray? = null): Rule? {
            val filtered = if (alt == null || cells == null) {
                options
            } else {
                val altValues = IntArray(cells.size) { alt[cells[it]] }
                options.filter { !it.isSatisfiedBy(altValues) }
            }
            if (filtered.isEmpty()) return null
            // While repairing, sharper rules end the search for a unique board sooner.
            val sharp = if (alt == null || cells == null) {
                filtered
            } else {
                filtered.filter { !it.type.isSum || RuleInformation.isInformative(it, cells.size) }.ifEmpty { filtered }
            }
            val preferred = sharp.filter { initialWeights[it.type.ordinal] > 0 }
            val pool = preferred.ifEmpty { sharp }
            val weights = IntArray(pool.size) { maxOf(1, initialWeights[pool[it].type.ordinal]) }
            return pool[rng.weightedIndex(weights)]
        }

        // ------------------------------------------------------------ uniqueness

        private fun spec(): Spec = Spec(board, drafts.map { it.cells }, drafts.map { it.rule }, tray)

        private fun solveExact(): SolveResult {
            val mark = TimeSource.Monotonic.markNow()
            val result = ExactSolver.solve(spec(), 2, SOLVER_NODE_LIMIT)
            counters.solverNanos += mark.elapsedNow().inWholeNanoseconds
            counters.solverCalls++
            counters.solverNodes += result.nodes
            return result
        }

        private fun makeUnique(): Boolean {
            repeat(MAX_REPAIRS) {
                val res = solveExact()
                if (res.exhausted) {
                    // Too loose to search cheaply: tighten somewhere and look again.
                    if (!strengthenCells(IntArray(n) { it })) return failed(FailReason.SOLVER_LIMIT)
                    counters.repairs++
                    return@repeat
                }
                if (res.solutionCount == 0) return failed(FailReason.SOLVER_LIMIT)
                if (res.solutionCount == 1) return true
                val alt = res.solutions.firstOrNull { it != solution } ?: return failed(FailReason.SOLVER_LIMIT)
                val altValues = IntArray(n)
                for (p in alt) {
                    altValues[p.cellA] = p.valueA
                    altValues[p.cellB] = p.valueB
                }
                val diff = (0 until n).filter { altValues[it] != values[it] }.toIntArray()
                // Same values, different tiling: no value rule can tell them apart.
                if (diff.isEmpty()) return failed(FailReason.TILING_AMBIGUOUS)
                if (!repairOnce(diff, altValues)) return failed(FailReason.NO_REPAIR)
                counters.repairs++
            }
            return failed(FailReason.REPAIR_LIMIT)
        }

        /** Adds or changes one constraint so the alternative solution [alt] is ruled out. */
        private fun repairOnce(diff: IntArray, alt: IntArray): Boolean {
            rng.shuffle(diff)
            // Equal and all-different regions make the most interesting rules; change them last.
            val ordered = diff.sortedBy { x ->
                val r = regionOf[x]
                if (r >= 0 && (drafts[r].rule.type == RuleType.EQUAL || drafts[r].rule.type == RuleType.ALL_DIFFERENT)) 1 else 0
            }
            for (x in ordered) {
                val r = regionOf[x]
                if (r < 0) {
                    if (addRegionAround(x, alt)) return true
                    continue
                }
                val splitFirst = rng.chance(40)
                if (splitFirst && split(r, x, alt)) return true
                if (retighten(r, alt)) return true
                if (!splitFirst && split(r, x, alt)) return true
            }
            // Only free cells differ and coverage is at the cap: trade a free cell for another.
            for (x in diff) if (regionOf[x] < 0 && tradeFreeCell(x, alt)) return true
            return false
        }

        /**
         * Puts free cell [x] in a new one-cell region that [alt] violates and frees another cell
         * instead, keeping coverage constant and the free-cell invariants of [planRegions].
         */
        private fun tradeFreeCell(x: Int, alt: IntArray): Boolean {
            val rule = pickRule(ruleOptions(intArrayOf(x)), alt, intArrayOf(x)) ?: return false
            var anchors = 0
            for (f in 0 until n) {
                if (f != x && regionOf[f] < 0) anchors = anchors or (1 shl values[partnerOf[f]])
            }
            val candidates = ArrayList<Int>()
            for (y in 0 until n) {
                val r = regionOf[y]
                if (r < 0) continue
                val p = partnerOf[y]
                if (p == x || regionOf[p] < 0 || anchors and (1 shl values[p]) != 0) continue
                var touchesFree = false
                for (dir in 0 until 4) {
                    val d = board.neighbor(y, dir)
                    if (d >= 0 && (d == x || regionOf[d] < 0)) touchesFree = true
                }
                if (touchesFree) continue
                val rest = drafts[r].cells.filter { it != y }.toIntArray()
                if (rest.isNotEmpty() && !connected(rest)) continue
                candidates.add(y)
            }
            if (candidates.isEmpty()) return false
            val y = candidates[rng.nextInt(candidates.size)]
            val r = regionOf[y]
            val rest = drafts[r].cells.filter { it != y }.toIntArray()
            if (rest.isEmpty()) {
                // y was a one-cell region: the region goes away.
                drafts.removeAt(r)
            } else {
                val restRule = makeRule(drafts[r].rule.type, rest) ?: pickRule(ruleOptions(rest), null) ?: return false
                drafts[r] = Draft(rest, restRule)
            }
            addDraft(intArrayOf(x), rule)
            return true
        }

        /** New region on free cell [x] (optionally with a free neighbour) that [alt] violates if given. */
        private fun addRegionAround(x: Int, alt: IntArray?): Boolean {
            val covered = covered()
            if (covered + 1 > maxCovered) return false
            val shapes = ArrayList<IntArray>()
            if (covered + 2 <= maxCovered && rng.chance(50)) {
                val neighbors = ArrayList<Int>()
                for (dir in 0 until 4) {
                    val d = board.neighbor(x, dir)
                    if (d >= 0 && regionOf[d] < 0) neighbors.add(d)
                }
                if (neighbors.isNotEmpty()) shapes.add(intArrayOf(x, neighbors[rng.nextInt(neighbors.size)]))
            }
            shapes.add(intArrayOf(x))
            for (cells in shapes) {
                val rule = pickRule(ruleOptions(cells), alt, cells) ?: continue
                addDraft(cells, rule)
                return true
            }
            return false
        }

        private fun retighten(r: Int, alt: IntArray): Boolean {
            val draft = drafts[r]
            val options = ruleOptions(draft.cells).filter { it != draft.rule }
            val rule = pickRule(options, alt, draft.cells) ?: return false
            draft.rule = rule
            return true
        }

        /** Splits region [r] into a part grown from [x] and the rest; [alt] must break one of them. */
        private fun split(r: Int, x: Int, alt: IntArray?, sumOnly: Boolean = false): Boolean {
            val cells = drafts[r].cells
            if (cells.size < 2) return false
            repeat(6) {
                val size = rng.nextIntInclusive(1, cells.size - 1)
                val part = growWithin(x, size, cells)
                val rest = cells.filter { it !in part }.toIntArray()
                if (rest.isEmpty() || !connected(rest)) return@repeat
                val partOptions = if (sumOnly) listOf(Rule.sumEq(part.sumOf { values[it] })) else ruleOptions(part)
                val restOptions = if (sumOnly) listOf(Rule.sumEq(rest.sumOf { values[it] })) else ruleOptions(rest)
                var partRule: Rule?
                var restRule: Rule?
                if (alt == null) {
                    partRule = pickRule(partOptions, null)
                    restRule = pickRule(restOptions, null)
                } else {
                    partRule = pickRule(partOptions, alt, part)
                    restRule = if (partRule != null) pickRule(restOptions, null) else pickRule(restOptions, alt, rest)
                    if (partRule == null) partRule = pickRule(partOptions, null)
                }
                if (partRule == null || restRule == null) return@repeat
                part.sort()
                drafts[r] = Draft(part, partRule)
                drafts.add(Draft(rest, restRule))
                reindex()
                return true
            }
            return false
        }

        private fun growWithin(start: Int, size: Int, allowed: IntArray): IntArray {
            val out = ArrayList<Int>()
            out.add(start)
            val frontier = ArrayList<Int>()
            while (out.size < size) {
                frontier.clear()
                for (c in out) {
                    for (dir in 0 until 4) {
                        val d = board.neighbor(c, dir)
                        if (d >= 0 && d in allowed && d !in out && d !in frontier) frontier.add(d)
                    }
                }
                if (frontier.isEmpty()) break
                frontier.sort()
                out.add(frontier[rng.nextInt(frontier.size)])
            }
            return out.toIntArray()
        }

        private fun connected(cells: IntArray): Boolean {
            val seen = ArrayList<Int>()
            seen.add(cells[0])
            var i = 0
            while (i < seen.size) {
                val c = seen[i++]
                for (dir in 0 until 4) {
                    val d = board.neighbor(c, dir)
                    if (d >= 0 && d in cells && d !in seen) seen.add(d)
                }
            }
            return seen.size == cells.size
        }

        // ------------------------------------------------------------ grading and band fitting

        private fun grade(): GradeReport = timedGrade(profile.maxLevel)

        private fun timedGrade(maxLevel: Int): GradeReport {
            val mark = TimeSource.Monotonic.markNow()
            val report = HumanSolver.solve(Knowledge.initial(spec()), maxLevel)
            counters.graderNanos += mark.elapsedNow().inWholeNanoseconds
            return report
        }

        private fun level(report: GradeReport): Int = report.hardest?.level ?: 0

        /**
         * Grade of the current board if it is unique and solvable within the band, else null.
         * Cheapest proof first: the grader is sound, so if techniques 1–6 reach the full solution
         * the board is unique (and the full grade would take the identical path). Otherwise the
         * exact solver rejects ambiguous boards quickly before the expensive full grade.
         */
        private fun checkCandidate(): GradeReport? {
            if (profile.maxLevel > QUICK_LEVEL) {
                val quick = timedGrade(QUICK_LEVEL)
                if (quick.solved) return quick
                if (!solveExact().isUnique) return null
            }
            val full = grade()
            return if (full.solved) full else null
        }

        /**
         * Adds a constraint near a cell the grader could not resolve. Every change here only
         * removes solutions (new region, `<`/`>` → `=`, splitting a sum), so uniqueness holds.
         */
        private fun strengthen(k: Knowledge): Boolean =
            strengthenCells((0 until n).filter { k.knownValue(it) < 0 }.toIntArray())

        /** Restrictive change (see [strengthen]) at one of [stuck], tried in random order. */
        private fun strengthenCells(stuck: IntArray): Boolean {
            if (stuck.isEmpty()) return false
            rng.shuffle(stuck)
            for (x in stuck) {
                val r = regionOf[x]
                if (r < 0) {
                    if (addRegionAround(x, null)) return true
                    continue
                }
                val draft = drafts[r]
                when (draft.rule.type) {
                    RuleType.SUM_LT, RuleType.SUM_GT -> {
                        draft.rule = Rule.sumEq(draft.cells.sumOf { values[it] })
                        return true
                    }
                    RuleType.SUM_EQ -> if (split(r, x, null, sumOnly = true)) return true
                    else -> Unit
                }
            }
            return false
        }

        /**
         * Grows regions back after repairs: touching regions are merged (within the size cap)
         * under a rule drawn from [DifficultyProfile.ruleWeights]. A merge is kept only if the
         * board stays unique and solvable within the band.
         */
        private fun merge(start: GradeReport): GradeReport {
            var best = start
            repeat(profile.mergeAttempts) {
                val pairs = ArrayList<Long>()
                for (c in 0 until n) {
                    val r = regionOf[c]
                    if (r < 0) continue
                    for (dir in 1..2) {
                        val d = board.neighbor(c, dir)
                        if (d < 0) continue
                        val s = regionOf[d]
                        if (s < 0 || s == r) continue
                        if (drafts[r].cells.size + drafts[s].cells.size > profile.maxRegionSize) continue
                        val key = minOf(r, s).toLong() shl 32 or maxOf(r, s).toLong()
                        if (key !in pairs) pairs.add(key)
                    }
                }
                if (pairs.isEmpty()) return best
                val key = pairs[rng.nextInt(pairs.size)]
                val a = (key ushr 32).toInt()
                val b = (key and 0xFFFFFFFFL).toInt()
                val union = (drafts[a].cells + drafts[b].cells).sortedArray()
                val options = ruleOptions(union).filter { !it.type.isSum || RuleInformation.isInformative(it, union.size) }
                if (options.isEmpty()) return@repeat
                val weights = IntArray(options.size) { profile.ruleWeights[options[it].type.ordinal] }
                if (weights.all { it == 0 }) return@repeat
                val rule = options[rng.weightedIndex(weights)]
                val saved = ArrayList(drafts.map { it.copy() })
                drafts[a] = Draft(union, rule)
                drafts.removeAt(b)
                reindex()
                val report = checkCandidate()
                if (report == null) {
                    drafts = saved
                    reindex()
                    return@repeat
                }
                counters.merges++
                best = report
            }
            return best
        }

        /**
         * Reintroduces `<` / `>` rules in the proportions of [DifficultyProfile.ruleWeights]: each
         * exact-sum region, in random order, may be relaxed (or a `<` / `>` widened). A change is
         * kept only if the board stays unique and solvable within the band.
         */
        private fun loosen(start: GradeReport): GradeReport {
            var best = start
            val order = IntArray(drafts.size) { it }
            rng.shuffle(order)
            var tries = 0
            for (r in order) {
                if (tries >= profile.loosenAttempts) break
                val draft = drafts[r]
                val previous = draft.rule
                draft.rule = loosened(draft) ?: continue
                tries++
                val report = checkCandidate()
                if (report == null) {
                    draft.rule = previous
                    continue
                }
                counters.loosenings++
                best = report
            }
            return best
        }

        /** A weaker rule for [draft] still true for the solution, or null to leave it as is. */
        private fun loosened(draft: Draft): Rule? {
            val k = draft.cells.size
            val sum = draft.cells.sumOf { values[it] }
            val max = Domino.MAX_VALUE * k
            val w = profile.ruleWeights
            return when (draft.rule.type) {
                RuleType.SUM_EQ -> {
                    val pick = rng.weightedIndex(
                        intArrayOf(
                            w[RuleType.SUM_EQ.ordinal],
                            if (sum < max) w[RuleType.SUM_LT.ordinal] else 0,
                            if (sum > 0) w[RuleType.SUM_GT.ordinal] else 0,
                        ),
                    )
                    when (pick) {
                        1 -> Rule.sumLt(minOf(max, sum + 1 + slack()))
                        2 -> Rule.sumGt(maxOf(0, sum - 1 - slack()))
                        else -> null
                    }
                }
                RuleType.SUM_LT -> {
                    val t = draft.rule.target + 1
                    if (t <= max && t - sum - 1 <= profile.maxSlack) Rule.sumLt(t) else null
                }
                RuleType.SUM_GT -> {
                    val t = draft.rule.target - 1
                    if (t >= 0 && sum - t - 1 <= profile.maxSlack) Rule.sumGt(t) else null
                }
                RuleType.EQUAL, RuleType.ALL_DIFFERENT -> null
            }
        }

        // ------------------------------------------------------------ assembly

        private fun assemble(report: GradeReport): Pair<Puzzle, IntArray>? {
            val sorted = drafts.sortedBy { it.cells[0] }
            val colors = RegionColoring.assign(board, sorted.map { it.cells }, rng) ?: return null
            val regions = sorted.mapIndexed { i, d -> Region(d.cells.toList(), d.rule, colors[i]) }
            val grade = Grade(report.hardest ?: Technique.SINGLE_CELL, report.steps)
            val puzzle = Puzzle(seed, difficulty, board, regions, tray, solution, grade)
            return puzzle to report.techniqueCounts
        }
    }
}

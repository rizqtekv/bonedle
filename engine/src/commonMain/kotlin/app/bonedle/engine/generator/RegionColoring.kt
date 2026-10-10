package app.bonedle.engine.generator

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Region
import app.bonedle.engine.random.Xoshiro256StarStar

/**
 * Assigns each region one of [Region.REGION_COLORS] palette slots so that touching regions never
 * share a slot. Uses smallest-last (degeneracy) ordering, which needs at most six colours on any
 * planar adjacency graph, and balances slot usage so boards look varied.
 */
internal object RegionColoring {
    fun assign(board: Board, regionCells: List<IntArray>, rng: Xoshiro256StarStar): IntArray? {
        val count = regionCells.size
        val regionOf = IntArray(board.cellCount) { -1 }
        regionCells.forEachIndexed { r, cells -> cells.forEach { regionOf[it] = r } }
        val adj = Array(count) { BooleanArray(count) }
        for (c in 0 until board.cellCount) {
            val r = regionOf[c]
            if (r < 0) continue
            for (dir in 0 until 4) {
                val d = board.neighbor(c, dir)
                if (d < 0) continue
                val s = regionOf[d]
                if (s >= 0 && s != r) {
                    adj[r][s] = true
                    adj[s][r] = true
                }
            }
        }
        // Smallest-last ordering.
        val removed = BooleanArray(count)
        val degree = IntArray(count) { r -> adj[r].count { it } }
        val stack = IntArray(count)
        for (i in 0 until count) {
            var pick = -1
            for (r in 0 until count) if (!removed[r] && (pick < 0 || degree[r] < degree[pick])) pick = r
            removed[pick] = true
            stack[i] = pick
            for (s in 0 until count) if (adj[pick][s] && !removed[s]) degree[s]--
        }
        val color = IntArray(count) { -1 }
        val usage = IntArray(Region.REGION_COLORS)
        for (i in count - 1 downTo 0) {
            val r = stack[i]
            var blocked = 0
            for (s in 0 until count) if (adj[r][s] && color[s] >= 0) blocked = blocked or (1 shl color[s])
            var bestUse = Int.MAX_VALUE
            var ties = 0
            for (k in 0 until Region.REGION_COLORS) {
                if (blocked and (1 shl k) != 0) continue
                if (usage[k] < bestUse) {
                    bestUse = usage[k]
                    ties = 1
                } else if (usage[k] == bestUse) {
                    ties++
                }
            }
            if (ties == 0) return null
            var pickIndex = rng.nextInt(ties)
            for (k in 0 until Region.REGION_COLORS) {
                if (blocked and (1 shl k) != 0 || usage[k] != bestUse) continue
                if (pickIndex-- == 0) {
                    color[r] = k
                    usage[k]++
                    break
                }
            }
        }
        return color
    }
}

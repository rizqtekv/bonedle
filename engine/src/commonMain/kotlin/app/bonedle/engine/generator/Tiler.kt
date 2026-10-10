package app.bonedle.engine.generator

import app.bonedle.engine.model.Board
import app.bonedle.engine.random.Xoshiro256StarStar

/** Random domino tilings by randomised backtracking. */
internal object Tiler {
    private const val NODE_LIMIT = 20_000

    /**
     * Returns a random tiling of [board] as a partner array (`partner[c]` is the cell sharing a
     * domino with `c`), or null if none was found within the node budget.
     *
     * Always extends the open cell with the fewest open neighbours ("most constrained first"),
     * breaking ties by a random cell order, and tries its neighbours in random order.
     */
    fun randomTiling(board: Board, rng: Xoshiro256StarStar): IntArray? {
        val n = board.cellCount
        if (n % 2 != 0) return null
        val order = IntArray(n) { it }
        rng.shuffle(order)
        val partner = IntArray(n) { -1 }
        val dirOrders = Array(n) { IntArray(4) { it }.also(rng::shuffle) }
        val nodes = IntArray(1)
        return if (extend(board, order, partner, dirOrders, nodes)) partner else null
    }

    private fun extend(
        board: Board,
        order: IntArray,
        partner: IntArray,
        dirOrders: Array<IntArray>,
        nodes: IntArray,
    ): Boolean {
        if (++nodes[0] > NODE_LIMIT) return false
        var best = -1
        var bestFree = 5
        for (c in order) {
            if (partner[c] >= 0) continue
            var free = 0
            for (dir in 0 until 4) {
                val d = board.neighbor(c, dir)
                if (d >= 0 && partner[d] < 0) free++
            }
            if (free < bestFree) {
                bestFree = free
                best = c
                if (free <= 1) break
            }
        }
        if (best < 0) return true
        if (bestFree == 0) return false
        for (dir in dirOrders[best]) {
            val d = board.neighbor(best, dir)
            if (d < 0 || partner[d] >= 0) continue
            partner[best] = d
            partner[d] = best
            if (extend(board, order, partner, dirOrders, nodes)) return true
            partner[best] = -1
            partner[d] = -1
        }
        return false
    }
}

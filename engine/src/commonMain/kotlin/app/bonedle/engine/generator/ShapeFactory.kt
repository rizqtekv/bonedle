package app.bonedle.engine.generator

import app.bonedle.engine.model.Board
import app.bonedle.engine.random.Xoshiro256StarStar

/** Builds board shapes. Every shape returned admits at least one domino tiling. */
internal object ShapeFactory {
    fun create(profile: DifficultyProfile, rng: Xoshiro256StarStar): Board? {
        if (profile.irregularPercent > 0 && rng.chance(profile.irregularPercent)) {
            val cells = profile.irregularCells[rng.nextInt(profile.irregularCells.size)]
            val cols = rng.nextIntInclusive(profile.irregularCols.first, profile.irregularCols.last)
            // The box needs some slack around the shape, or growth cannot leave notches.
            val minRows = (cells * 10 / 9 + cols) / cols
            val firstRow = maxOf(profile.irregularRows.first, minRows)
            if (firstRow > profile.irregularRows.last) return null
            val rows = rng.nextIntInclusive(firstRow, profile.irregularRows.last)
            return irregular(rows, cols, cells, rng)
        }
        val weights = IntArray(profile.rectangles.size) { profile.rectangles[it].weight }
        val rect = profile.rectangles[rng.weightedIndex(weights)]
        return Board.rectangle(rect.rows, rect.cols)
    }

    /**
     * Grows a compact polyomino of [cells] cells inside a [rows] × [cols] box one domino at a time,
     * so the result is tileable by construction. Additions touching more of the shape are
     * favoured, which keeps outlines compact while still producing notches and holes.
     */
    fun irregular(rows: Int, cols: Int, cells: Int, rng: Xoshiro256StarStar): Board? {
        if (cells > rows * cols * 9 / 10 || cells % 2 != 0) return null
        val inShape = BooleanArray(rows * cols)
        val startR = rows / 2 - 1 + rng.nextInt(2)
        val startC = cols / 2 - 1 + rng.nextInt(2)
        val start = startR * cols + startC
        val horizontal = rng.chance(50)
        val second = if (horizontal) start + 1 else start + cols
        inShape[start] = true
        inShape[second] = true
        var size = 2
        val pairA = IntArray(rows * cols * 2)
        val pairB = IntArray(rows * cols * 2)
        val weights = IntArray(rows * cols * 2)
        while (size < cells) {
            var count = 0
            for (p in 0 until rows * cols) {
                if (inShape[p]) continue
                val pr = p / cols
                val pc = p % cols
                // Right and down partners only, so each pair is listed once.
                for (q in intArrayOf(if (pc + 1 < cols) p + 1 else -1, if (pr + 1 < rows) p + cols else -1)) {
                    if (q < 0 || inShape[q]) continue
                    val touch = touching(inShape, rows, cols, p, q) + touching(inShape, rows, cols, q, p)
                    if (touch == 0) continue
                    pairA[count] = p
                    pairB[count] = q
                    weights[count] = 1 + touch * touch * touch
                    count++
                }
            }
            if (count == 0) return null
            val pick = rng.weightedIndex(weights.copyOf(count))
            inShape[pairA[pick]] = true
            inShape[pairB[pick]] = true
            size += 2
        }
        return crop(inShape, rows, cols)
    }

    /** Neighbours of [p] inside the shape, not counting [other]. */
    private fun touching(inShape: BooleanArray, rows: Int, cols: Int, p: Int, other: Int): Int {
        val r = p / cols
        val c = p % cols
        var n = 0
        if (r > 0 && p - cols != other && inShape[p - cols]) n++
        if (r + 1 < rows && p + cols != other && inShape[p + cols]) n++
        if (c > 0 && p - 1 != other && inShape[p - 1]) n++
        if (c + 1 < cols && p + 1 != other && inShape[p + 1]) n++
        return n
    }

    private fun crop(inShape: BooleanArray, rows: Int, cols: Int): Board {
        var minR = rows
        var maxR = -1
        var minC = cols
        var maxC = -1
        for (p in inShape.indices) {
            if (!inShape[p]) continue
            val r = p / cols
            val c = p % cols
            if (r < minR) minR = r
            if (r > maxR) maxR = r
            if (c < minC) minC = c
            if (c > maxC) maxC = c
        }
        val h = maxR - minR + 1
        val w = maxC - minC + 1
        val mask = BooleanArray(h * w) { inShape[(minR + it / w) * cols + minC + it % w] }
        return Board(h, w, mask)
    }
}

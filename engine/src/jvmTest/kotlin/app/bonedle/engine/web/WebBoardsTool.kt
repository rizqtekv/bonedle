package app.bonedle.engine.web

import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.RuleType
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * Spoiler-free SVG silhouettes of boards for the challenge web page (spec 8.2, v1: "a page with
 * the board image"): regions, outlines and rule chips, no dominoes, in the app's dark palette
 * (mirrors `app.bonedle.ui.theme.DarkColors`). Same layout rules as the in-app board: a
 * region's chip straddles the top edge of its top-left cell.
 */
object BoardSvg {
    private const val CELL = 64
    private const val PAD = 20
    private const val OUTLINE = 3.5
    private const val CHIP_HEIGHT = 26
    private const val RADIUS = 14

    private const val BOARD_BASE = "#0E1522"
    private const val CELL_FREE = "#1B2638"
    private const val CELL_BORDER = "#2A3850"
    private const val ON_REGION = "#101826"
    private const val FILL_OPACITY = "0.24"
    private val REGIONS = listOf("#35C2B0", "#F08A4B", "#9B7BF2", "#F2668B", "#4FA8F5", "#A6D84B")

    /** The chip text, as in the app: `=`, `≠`, `12`, `<5`, `>7`. */
    fun symbol(type: RuleType, target: Int): String = when (type) {
        RuleType.EQUAL -> "="
        RuleType.ALL_DIFFERENT -> "≠"
        RuleType.SUM_EQ -> target.toString()
        RuleType.SUM_LT -> "<$target"
        RuleType.SUM_GT -> ">$target"
    }

    fun render(puzzle: Puzzle, title: String): String {
        val board = puzzle.board
        val width = board.cols * CELL + PAD * 2
        val height = board.rows * CELL + PAD * 2
        val regionOf = IntArray(board.cellCount) { -1 }
        puzzle.regions.forEachIndexed { r, region -> region.cells.forEach { regionOf[it] = r } }
        val sb = StringBuilder()
        sb.append("""<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 $width $height" width="$width" height="$height" role="img" aria-label="${escape(title)}">""")
        sb.append("<title>${escape(title)}</title>")
        sb.append("""<rect width="$width" height="$height" rx="$RADIUS" fill="$BOARD_BASE"/>""")
        for (cell in 0 until board.cellCount) {
            val x = PAD + board.col(cell) * CELL
            val y = PAD + board.row(cell) * CELL
            sb.append("""<rect x="$x" y="$y" width="$CELL" height="$CELL" fill="$CELL_FREE" stroke="$CELL_BORDER" stroke-width="1"/>""")
            val r = regionOf[cell]
            if (r >= 0) {
                sb.append("""<rect x="$x" y="$y" width="$CELL" height="$CELL" fill="${REGIONS[puzzle.regions[r].color]}" fill-opacity="$FILL_OPACITY"/>""")
            }
        }
        // Outlines on every region edge that faces another region, a free cell or the board edge,
        // clipped to the region's own cells (as in the app) so line ends never spill over.
        sb.append("<defs>")
        puzzle.regions.forEachIndexed { r, region ->
            sb.append("""<clipPath id="r$r">""")
            for (cell in region.cells) {
                sb.append("""<rect x="${PAD + board.col(cell) * CELL}" y="${PAD + board.row(cell) * CELL}" width="$CELL" height="$CELL"/>""")
            }
            sb.append("</clipPath>")
        }
        sb.append("</defs>")
        for (cell in 0 until board.cellCount) {
            val r = regionOf[cell]
            if (r < 0) continue
            val color = REGIONS[puzzle.regions[r].color]
            val x = PAD + board.col(cell) * CELL
            val y = PAD + board.row(cell) * CELL
            for (dir in 0 until 4) {
                val n = board.neighbor(cell, dir)
                if (n >= 0 && regionOf[n] == r) continue
                val inset = OUTLINE / 2
                val (x1, y1, x2, y2) = when (dir) {
                    0 -> listOf(x.toDouble(), y + inset, (x + CELL).toDouble(), y + inset)
                    1 -> listOf(x + CELL - inset, y.toDouble(), x + CELL - inset, (y + CELL).toDouble())
                    2 -> listOf(x.toDouble(), y + CELL - inset, (x + CELL).toDouble(), y + CELL - inset)
                    else -> listOf(x + inset, y.toDouble(), x + inset, (y + CELL).toDouble())
                }
                sb.append("""<line x1="${num(x1)}" y1="${num(y1)}" x2="${num(x2)}" y2="${num(y2)}" stroke="$color" stroke-width="$OUTLINE" stroke-linecap="square" clip-path="url(#r$r)"/>""")
            }
        }
        for (region in puzzle.regions) {
            val anchor = region.cells.minWith(compareBy({ board.row(it) }, { board.col(it) }))
            val text = symbol(region.rule.type, region.rule.target)
            val chipWidth = maxOf(CHIP_HEIGHT, 14 + text.length * 11)
            val x = PAD + board.col(anchor) * CELL - 4
            val y = PAD + board.row(anchor) * CELL - CHIP_HEIGHT / 2
            sb.append("""<rect x="$x" y="$y" width="$chipWidth" height="$CHIP_HEIGHT" rx="${CHIP_HEIGHT / 2}" fill="${REGIONS[region.color]}"/>""")
            sb.append(
                """<text x="${x + chipWidth / 2}" y="${y + CHIP_HEIGHT / 2}" fill="$ON_REGION" font-family="Sora, DM Sans, sans-serif" font-size="15" font-weight="700" text-anchor="middle" dominant-baseline="central">${escape(text)}</text>""",
            )
        }
        sb.append("</svg>\n")
        return sb.toString()
    }

    private fun num(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else String.format(Locale.ROOT, "%.2f", v)

    private fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
}

/**
 * Writes `d/<number>-<difficulty>.svg` for the daily boards up to and including tomorrow (UTC),
 * so the web page never shows a board before its day. Run it from the deploy job:
 * `./gradlew :engine:generateWebBoards [-Pbonedle.webDir=<dir>] [-Pbonedle.webThrough=YYYY-MM-DD]`.
 */
fun main(args: Array<String>) {
    val out = File(args.getOrNull(0) ?: "build/web-boards", "d")
    val through = args.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { CalendarDate.parse(it) }
        ?: LocalDate.now(ZoneOffset.UTC).plusDays(1).let { CalendarDate(it.year, it.monthValue, it.dayOfMonth) }
    val last = DailySchedule.numberOn(through)
    if (through.epochDay < DailySchedule.LAUNCH_DATE.epochDay) {
        println("Nothing to write before launch (${DailySchedule.LAUNCH_DATE}).")
        return
    }
    out.mkdirs()
    val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    try {
        val jobs: List<Future<Unit>> = (DailySchedule.FIRST_NUMBER..last).flatMap { n ->
            Difficulty.DAILY.map { d ->
                pool.submit<Unit> {
                    val file = File(out, "$n-${d.code}.svg")
                    if (!file.exists()) {
                        val title = "Bonedle #$n · ${d.code.replaceFirstChar { it.uppercase() }}"
                        file.writeText(BoardSvg.render(DailySchedule.puzzle(n, d), title))
                    }
                }
            }
        }
        jobs.forEach { it.get() }
    } finally {
        pool.shutdown()
    }
    println("${out.path}: dailies #${DailySchedule.FIRST_NUMBER}-#$last")
}

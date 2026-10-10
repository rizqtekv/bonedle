package app.bonedle.engine.web

import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.RuleType
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BoardSvgTest {
    @Test
    fun rendersWellFormedSilhouetteWithOneChipPerRegion() {
        val puzzle = DailySchedule.puzzle(1, Difficulty.MEDIUM)
        val svg = BoardSvg.render(puzzle, "Bonedle #1 · Medium")
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(svg.byteInputStream())
        assertEquals("svg", doc.documentElement.tagName)
        assertEquals(puzzle.regions.size, doc.getElementsByTagName("text").length)
        // Background, a rect per cell, a fill and an outline clip per region cell, a chip per region.
        val regionCells = puzzle.regions.sumOf { it.cells.size }
        assertEquals(1 + puzzle.board.cellCount + regionCells * 2 + puzzle.regions.size, doc.getElementsByTagName("rect").length)
        assertEquals(puzzle.regions.size, doc.getElementsByTagName("clipPath").length)
        // Spoiler-free: no domino spots or values beyond the rule targets.
        assertFalse(svg.contains("<circle"))
    }

    @Test
    fun symbolsMatchTheApp() {
        assertEquals("=", BoardSvg.symbol(RuleType.EQUAL, 0))
        assertEquals("≠", BoardSvg.symbol(RuleType.ALL_DIFFERENT, 0))
        assertEquals("12", BoardSvg.symbol(RuleType.SUM_EQ, 12))
        assertEquals("<5", BoardSvg.symbol(RuleType.SUM_LT, 5))
        assertTrue(BoardSvg.render(DailySchedule.puzzle(2, Difficulty.EASY), "a < b").contains("a &lt; b"))
    }
}

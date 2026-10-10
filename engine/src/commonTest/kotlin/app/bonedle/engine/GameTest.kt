package app.bonedle.engine

import app.bonedle.engine.game.Game
import app.bonedle.engine.game.GameCodec
import app.bonedle.engine.game.GameFormatException
import app.bonedle.engine.game.Hint
import app.bonedle.engine.game.HintEngine
import app.bonedle.engine.game.MoveError
import app.bonedle.engine.game.MoveResult
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Direction
import app.bonedle.engine.model.Placement
import app.bonedle.engine.rules.RegionStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GameTest {
    private fun Game.ok(r: MoveResult): Game = assertIs<MoveResult.Accepted>(r, "expected acceptance").game

    private fun rejected(r: MoveResult): MoveError = assertIs<MoveResult.Rejected>(r).error

    /** "less-and-more": 1x4, A:<1 on cell 0, B:>4 on cell 3, tray 0-6, 1-5; solution 0 6 1 5. */
    private val line = GoldenBoards.ALL.first { it.name == "less-and-more" }.puzzle()

    @Test
    fun placingTheSolutionSolves() {
        var g = Game.start(line)
        // Tray is sorted: 0-6 is index 0, 1-5 is index 1.
        g = g.ok(g.place(0, Placement(0, 1, 0, 6)))
        assertFalse(g.isSolved)
        g = g.ok(g.place(1, Placement(2, 3, 1, 5)))
        assertTrue(g.isComplete)
        assertTrue(g.isSolved)
        assertEquals(listOf(RegionStatus.SATISFIED, RegionStatus.SATISFIED), g.regionStatuses)
    }

    @Test
    fun illegalPlacementsAreRejected() {
        var g = Game.start(line)
        assertEquals(MoveError.NOT_ADJACENT, rejected(g.place(0, Placement(0, 2, 0, 6))))
        assertEquals(MoveError.WRONG_DOMINO, rejected(g.place(0, Placement(0, 1, 1, 5))))
        g = g.ok(g.place(0, Placement(0, 1, 0, 6)))
        assertEquals(MoveError.OCCUPIED, rejected(g.place(1, Placement(1, 2, 1, 5))))
        assertEquals(MoveError.NOT_PLACED, rejected(g.remove(1)))
    }

    @Test
    fun brokenRulesShowAndStrictModeRefusesThem() {
        val g = Game.start(line)
        // 6 on cell 0 breaks "<1".
        val loose = g.ok(g.place(0, Placement(0, 1, 6, 0)))
        assertEquals(RegionStatus.BROKEN, loose.regionStatuses[0])
        assertEquals(MoveError.BREAKS_RULE, rejected(g.place(0, Placement(0, 1, 6, 0), strict = true)))
    }

    @Test
    fun movingAndRemovingADomino() {
        var g = Game.start(line)
        g = g.ok(g.place(1, Placement(0, 1, 1, 5)))
        g = g.ok(g.place(1, Placement(2, 3, 1, 5)))
        assertEquals(-1, g.ownerOf(0))
        assertEquals(1, g.ownerOf(3))
        g = g.ok(g.remove(1))
        assertTrue(g.placements.isEmpty())
        assertEquals(3, g.history.size)
    }

    @Test
    fun undoWalksBackEveryMove() {
        var g = Game.start(line)
        g = g.ok(g.place(0, Placement(0, 1, 0, 6)))
        g = g.ok(g.place(1, Placement(2, 3, 5, 1)))
        g = g.ok(g.remove(0))
        g = g.ok(g.undo())
        assertEquals(Placement(0, 1, 0, 6), g.placementOf(0))
        g = g.ok(g.undo())
        assertNull(g.placementOf(1))
        g = g.ok(g.undo())
        assertTrue(g.placements.isEmpty())
        assertEquals(MoveError.NOTHING_TO_UNDO, rejected(g.undo()))
    }

    @Test
    fun rotationTurnsAroundTheAnchorAndSkipsBlockedCells() {
        val square = GoldenBoards.ALL.first { it.name == "two-doubles" }.puzzle() // 2x2, tray 1-1, 4-4
        var g = Game.start(square)
        g = g.ok(g.placeAt(0, anchor = 0, dir = Direction.EAST, firstValue = 1))
        assertEquals(Placement(0, 1, 1, 1), g.placementOf(0))
        g = g.ok(g.rotate(0, anchor = 0)) // east -> south
        assertEquals(Placement(0, 2, 1, 1), g.placementOf(0))
        g = g.ok(g.place(1, Placement(1, 3, 4, 4)))
        // From south, west and north are off the board and east is covered: no room.
        assertEquals(MoveError.NO_ROOM, rejected(g.rotate(0, anchor = 0)))
    }

    @Test
    fun restartClearsBoardAndHistory() {
        var g = Game.start(line)
        g = g.ok(g.place(0, Placement(0, 1, 0, 6)))
        val fresh = g.restart()
        assertTrue(fresh.placements.isEmpty())
        assertFalse(fresh.canUndo)
    }

    @Test
    fun savesRoundTripWithHistory() {
        val p = PuzzleGenerator.generate(77L, Difficulty.MEDIUM)
        var g = Game.start(p)
        for ((i, s) in p.solution.take(4).withIndex()) {
            val t = g.unplaced.first { g.tray[it] == s.domino }
            g = g.ok(g.place(t, if (i % 2 == 0) s else Placement(s.cellB, s.cellA, s.valueB, s.valueA)))
        }
        g = g.ok(g.undo())
        val restored = GameCodec.decode(p, GameCodec.encode(g))
        assertEquals(g.placements, restored.placements)
        assertEquals(g.history, restored.history)
        assertEquals(GameCodec.encode(g), GameCodec.encode(restored))
    }

    @Test
    fun corruptSavesAreRejected() {
        assertFailsWith<GameFormatException> { GameCodec.decode(line, "nope") }
        assertFailsWith<GameFormatException> { GameCodec.decode(line, "g1|0,1,0,6;-|") } // board without history
        assertFailsWith<GameFormatException> { GameCodec.decode(line, "g1|-;-|0/-/0,2,0,6") } // illegal move
    }

    @Test
    fun hintsPointOutWrongDominoesFirst() {
        var g = Game.start(line)
        g = g.ok(g.place(0, Placement(0, 1, 6, 0)))
        val hint = assertIs<Hint.WrongDomino>(HintEngine.next(g))
        assertEquals(0, hint.trayIndex)
    }

    @Test
    fun followingRevealsAloneSolvesEveryDifficulty() {
        for (d in Difficulty.entries) {
            val p = PuzzleGenerator.generate(4242L, d)
            var g = Game.start(p)
            repeat(p.tray.size) {
                val step = assertIs<Hint.Step>(HintEngine.next(g), "$d")
                g = g.ok(g.place(step.reveal.trayIndex, step.reveal.placement))
            }
            assertTrue(g.isSolved, "$d")
            assertEquals(Hint.Solved, HintEngine.next(g))
        }
    }

    @Test
    fun hintDeductionsComeFromTheSolver() {
        val p = PuzzleGenerator.generate(99L, Difficulty.HARD)
        val step = assertIs<Hint.Step>(HintEngine.next(Game.start(p)))
        val d = step.deduction
        assertTrue(d != null && d.cells.isNotEmpty())
    }
}

package app.bonedle.engine.codec

import app.bonedle.engine.model.Board
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Domino
import app.bonedle.engine.model.Grade
import app.bonedle.engine.model.Placement
import app.bonedle.engine.model.Puzzle
import app.bonedle.engine.model.Region
import app.bonedle.engine.model.Rule
import app.bonedle.engine.model.RuleType
import app.bonedle.engine.model.Technique
import kotlin.io.encoding.Base64

/** Thrown when bytes or text cannot be decoded into a valid puzzle. */
class PuzzleFormatException(message: String) : IllegalArgumentException(message)

/**
 * Canonical binary encoding of a [Puzzle]. Encoding is deterministic, so equal puzzles always
 * produce byte-identical output, which is what the reproducibility tests compare.
 *
 * Layout (version 1, big-endian):
 * ```
 * 'B' 'N' version
 * seed:i64  difficulty:u8  rows:u8  cols:u8  mask:ceil(rows*cols/8) bytes (row-major, MSB first)
 * trayCount:u8  { low<<4 | high }*
 * regionCount:u8  { ruleType:u8 target:u8 color:u8 cellCount:u8 cell:u8* }*
 * { cellA:u8 cellB:u8 valueA<<4|valueB }*   (one per tray domino)
 * hardest:u8  steps:u16
 * ```
 */
object PuzzleCodec {
    private const val MAGIC_0: Byte = 'B'.code.toByte()
    private const val MAGIC_1: Byte = 'N'.code.toByte()
    private const val VERSION: Int = 1

    private val base64 = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** Encodes [puzzle] to its canonical bytes. */
    fun encode(puzzle: Puzzle): ByteArray {
        val out = ByteWriter()
        out.u8(MAGIC_0.toInt())
        out.u8(MAGIC_1.toInt())
        out.u8(VERSION)
        out.i64(puzzle.seed)
        out.u8(puzzle.difficulty.ordinal)
        val board = puzzle.board
        out.u8(board.rows)
        out.u8(board.cols)
        val mask = board.activeMask()
        var acc = 0
        var bits = 0
        for (active in mask) {
            acc = (acc shl 1) or (if (active) 1 else 0)
            if (++bits == 8) {
                out.u8(acc)
                acc = 0
                bits = 0
            }
        }
        if (bits > 0) out.u8(acc shl (8 - bits))
        out.u8(puzzle.tray.size)
        for (d in puzzle.tray) out.u8((d.low shl 4) or d.high)
        out.u8(puzzle.regions.size)
        for (r in puzzle.regions) {
            out.u8(r.rule.type.code)
            out.u8(r.rule.target)
            out.u8(r.color)
            out.u8(r.cells.size)
            for (c in r.cells) out.u8(c)
        }
        for (p in puzzle.solution) {
            out.u8(p.cellA)
            out.u8(p.cellB)
            out.u8((p.valueA shl 4) or p.valueB)
        }
        out.u8(puzzle.grade.hardest.level)
        out.u16(puzzle.grade.steps)
        return out.toByteArray()
    }

    /** Decodes canonical bytes produced by [encode]. */
    fun decode(bytes: ByteArray): Puzzle {
        val r = ByteReader(bytes)
        try {
            if (r.u8() != MAGIC_0.toInt() || r.u8() != MAGIC_1.toInt()) throw PuzzleFormatException("bad magic")
            val version = r.u8()
            if (version != VERSION) throw PuzzleFormatException("unsupported version $version")
            val seed = r.i64()
            val difficulty = Difficulty.entries.getOrNull(r.u8()) ?: throw PuzzleFormatException("bad difficulty")
            val rows = r.u8()
            val cols = r.u8()
            if (rows !in 1..Board.MAX_SIZE || cols !in 1..Board.MAX_SIZE) throw PuzzleFormatException("bad size")
            val mask = BooleanArray(rows * cols)
            var i = 0
            while (i < mask.size) {
                val byte = r.u8()
                for (b in 7 downTo 0) {
                    if (i < mask.size) mask[i++] = (byte shr b) and 1 == 1
                }
            }
            val board = Board(rows, cols, mask)
            val tray = List(r.u8()) {
                val v = r.u8()
                Domino(v shr 4, v and 0xF)
            }
            val regions = List(r.u8()) {
                val type = RuleType.fromCode(r.u8()) ?: throw PuzzleFormatException("bad rule")
                val target = r.u8()
                val color = r.u8()
                val cells = List(r.u8()) { r.u8() }
                Region(cells, Rule(type, target), color)
            }
            val solution = List(tray.size) {
                val a = r.u8()
                val b = r.u8()
                val v = r.u8()
                Placement(a, b, v shr 4, v and 0xF)
            }
            val hardest = Technique.fromLevel(r.u8()) ?: throw PuzzleFormatException("bad technique")
            val steps = r.u16()
            if (!r.atEnd) throw PuzzleFormatException("trailing bytes")
            return Puzzle(seed, difficulty, board, regions, tray, solution, Grade(hardest, steps))
        } catch (e: PuzzleFormatException) {
            throw e
        } catch (e: IllegalArgumentException) {
            throw PuzzleFormatException(e.message ?: "invalid puzzle")
        } catch (e: IndexOutOfBoundsException) {
            throw PuzzleFormatException("truncated data")
        }
    }

    /** [encode] as unpadded base64url text, safe for storage and URLs. */
    fun encodeToString(puzzle: Puzzle): String = base64.encode(encode(puzzle))

    /** Inverse of [encodeToString]. */
    fun decodeFromString(text: String): Puzzle {
        val bytes = try {
            base64.decode(text)
        } catch (e: IllegalArgumentException) {
            throw PuzzleFormatException("not base64url: ${e.message}")
        }
        return decode(bytes)
    }

    private class ByteWriter {
        private var buf = ByteArray(256)
        private var size = 0

        fun u8(v: Int) {
            require(v in 0..0xFF) { "byte out of range: $v" }
            if (size == buf.size) buf = buf.copyOf(buf.size * 2)
            buf[size++] = v.toByte()
        }

        fun u16(v: Int) {
            require(v in 0..0xFFFF) { "u16 out of range: $v" }
            u8(v ushr 8)
            u8(v and 0xFF)
        }

        fun i64(v: Long) {
            for (shift in 56 downTo 0 step 8) u8(((v ushr shift) and 0xFF).toInt())
        }

        fun toByteArray(): ByteArray = buf.copyOf(size)
    }

    private class ByteReader(private val bytes: ByteArray) {
        private var pos = 0

        val atEnd: Boolean get() = pos == bytes.size

        fun u8(): Int = bytes[pos++].toInt() and 0xFF

        fun u16(): Int = (u8() shl 8) or u8()

        fun i64(): Long {
            var v = 0L
            repeat(8) { v = (v shl 8) or u8().toLong() }
            return v
        }
    }
}

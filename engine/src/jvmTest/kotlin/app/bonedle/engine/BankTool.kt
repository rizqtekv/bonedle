package app.bonedle.engine

import app.bonedle.engine.codec.PuzzleBank
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.Puzzle
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.system.exitProcess

/**
 * Writes the fallback bank: [PuzzleBank.SIZE] fully validated boards per difficulty into the
 * directory given as the first argument. Run with `./gradlew :engine:generateBank`.
 */
fun main(args: Array<String>) {
    val out = File(args.firstOrNull() ?: "build/bank")
    out.mkdirs()
    val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())
    try {
        for (d in Difficulty.entries) {
            val jobs: List<Future<Puzzle>> = (0 until PuzzleBank.SIZE).map { i ->
                pool.submit<Puzzle> { PuzzleGenerator.generate(PuzzleBank.seedFor(i, d), d) }
            }
            val puzzles = jobs.map { it.get() }
            for (p in puzzles) {
                val problems = PuzzleChecks.problems(p)
                if (problems.isNotEmpty()) {
                    System.err.println("$d seed ${p.seed}: $problems")
                    exitProcess(1)
                }
            }
            val file = File(out, PuzzleBank.fileName(d))
            file.writeText(PuzzleBank.encode(puzzles))
            println("${file.path}: ${puzzles.size} boards, ${file.length()} bytes")
        }
    } finally {
        pool.shutdown()
    }
}

package app.bonedle.engine

import app.bonedle.engine.codec.PuzzleCodec
import app.bonedle.engine.generator.GeneratedPuzzle
import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import app.bonedle.engine.model.RuleType
import app.bonedle.engine.model.Technique
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

/**
 * Spec 7.6 property suite: boards from fixed seeds per difficulty must have exactly one solution,
 * satisfy every rule, grade inside the difficulty band, replay soundly with the human solver, and
 * reproduce byte-identically from their seed.
 *
 * `-Pbonedle.propertyCount=N` sets boards per difficulty (300 under `jvmTest`, 10,000 under
 * `propertyTest`). Writes the generation statistics report to `build/reports/engine/`.
 */
class PropertyTest {
    private val count = (System.getProperty("bonedle.propertyCount") ?: "300").toInt()
    private val reportDir = File(System.getProperty("bonedle.reportDir") ?: "build/reports/engine")

    private class Sample(val generated: GeneratedPuzzle, val nanos: Long)

    @Test
    fun generatedBoardsSatisfyAllInvariants() {
        val threads = Runtime.getRuntime().availableProcessors().coerceAtMost(16)
        val report = StringBuilder()
        report.appendLine("# Bonedle generation statistics")
        report.appendLine()
        report.appendLine("$count boards per difficulty, seeds `difficulty.ordinal * 1_000_000_007 + i`, $threads threads, JVM ${System.getProperty("java.version")}.")
        report.appendLine("Times are single-board wall time on this machine (each board is generated on one thread).")
        val failures = ConcurrentLinkedQueue<String>()
        for (d in Difficulty.entries) {
            val samples = arrayOfNulls<Sample>(count)
            val done = AtomicInteger()
            val pool = Executors.newFixedThreadPool(threads)
            for (i in 0 until count) {
                pool.execute {
                    val seed = d.ordinal * 1_000_000_007L + i
                    try {
                        val t0 = System.nanoTime()
                        val g = PuzzleGenerator.generateDetailed(seed, d)
                        val nanos = System.nanoTime() - t0
                        samples[i] = Sample(g, nanos)
                        val problems = PuzzleChecks.problems(g.puzzle)
                        if (problems.isNotEmpty()) failures.add("$d seed $seed: $problems")
                        if (!PuzzleChecks.reproduces(g.puzzle, PuzzleGenerator::generate)) {
                            failures.add("$d seed $seed: regeneration is not byte-identical")
                        }
                        val bytes = PuzzleCodec.encode(g.puzzle)
                        if (PuzzleCodec.decode(bytes) != g.puzzle) failures.add("$d seed $seed: codec round trip")
                    } catch (e: Throwable) {
                        failures.add("$d seed $seed: ${e::class.simpleName}: ${e.message}")
                    }
                    val n = done.incrementAndGet()
                    if (n % 1000 == 0) println("  $d: $n/$count")
                }
            }
            pool.shutdown()
            pool.awaitTermination(6, TimeUnit.HOURS)
            appendStats(report, d, samples.filterNotNull())
            println("$d done: ${samples.count { it != null }} boards, ${failures.size} failures so far")
        }
        reportDir.mkdirs()
        val file = File(reportDir, "generation-stats-$count.md")
        file.writeText(report.toString())
        println(report)
        println("Report written to ${file.absolutePath}")
        if (failures.isNotEmpty()) fail("${failures.size} failures:\n" + failures.take(30).joinToString("\n"))
        assertEquals(0, failures.size)
    }

    private fun appendStats(out: StringBuilder, d: Difficulty, samples: List<Sample>) {
        if (samples.isEmpty()) return
        val ms = samples.map { it.nanos / 1e6 }.sorted()
        fun pct(p: Double) = ms[((ms.size - 1) * p).toInt()]
        val puzzles = samples.map { it.generated.puzzle }
        out.appendLine()
        out.appendLine("## $d")
        out.appendLine()
        out.appendLine("| metric | value |")
        out.appendLine("|---|---|")
        out.appendLine("| boards | ${samples.size} |")
        out.appendLine("| time mean / p50 / p95 / p99 / max (ms) | %.1f / %.1f / %.1f / %.1f / %.1f |".format(ms.average(), pct(0.5), pct(0.95), pct(0.99), ms.last()))
        out.appendLine("| attempts per board (mean / max) | %.2f / %d |".format(samples.map { it.generated.stats.attempts }.average(), samples.maxOf { it.generated.stats.attempts }))
        out.appendLine("| exact-solver calls per board (mean) | %.1f |".format(samples.map { it.generated.stats.solverCalls }.average()))
        out.appendLine("| solve steps (mean / min / max) | %.1f / %d / %d |".format(puzzles.map { it.grade.steps }.average(), puzzles.minOf { it.grade.steps }, puzzles.maxOf { it.grade.steps }))
        out.appendLine("| cells (min-max) | ${puzzles.minOf { it.board.cellCount }}-${puzzles.maxOf { it.board.cellCount }} |")
        out.appendLine("| irregular boards | %.1f%% |".format(100.0 * puzzles.count { !it.board.isRectangle } / puzzles.size))
        out.appendLine("| region coverage (mean) | %.1f%% |".format(puzzles.map { 100.0 * it.regions.sumOf { r -> r.cells.size } / it.board.cellCount }.average()))
        out.appendLine("| regions per board (mean) | %.1f |".format(puzzles.map { it.regions.size }.average()))
        val sizes = puzzles.flatMap { p -> p.regions.map { it.cells.size } }
        out.appendLine("| region size (mean / max) | %.2f / %d |".format(sizes.average(), sizes.max()))
        out.appendLine("| single-cell regions | %.1f%% |".format(100.0 * sizes.count { it == 1 } / sizes.size))
        out.appendLine("| merges kept per board (mean) | %.1f |".format(samples.map { it.generated.stats.merges }.average()))
        out.appendLine("| boards with duplicate dominoes | %.1f%% |".format(100.0 * puzzles.count { it.tray.toSet().size < it.tray.size } / puzzles.size))
        out.appendLine()
        out.appendLine("Hardest technique required:")
        out.appendLine()
        out.appendLine("| technique | boards |")
        out.appendLine("|---|---|")
        for (t in Technique.entries) {
            val c = puzzles.count { it.grade.hardest == t }
            if (c > 0) out.appendLine("| ${t.level}. $t | $c (%.1f%%) |".format(100.0 * c / puzzles.size))
        }
        out.appendLine()
        out.appendLine("Steps by technique (share of all solve steps):")
        out.appendLine()
        val totals = IntArray(Technique.entries.size + 1)
        for (s in samples) for (i in totals.indices) totals[i] += s.generated.techniqueCounts[i]
        val all = totals.sum().toDouble()
        out.appendLine("| technique | share |")
        out.appendLine("|---|---|")
        for (t in Technique.entries) out.appendLine("| ${t.level}. $t | %.1f%% |".format(100.0 * totals[t.level] / all))
        out.appendLine()
        out.appendLine("Rule mix:")
        out.appendLine()
        val rules = puzzles.flatMap { p -> p.regions.map { it.rule.type } }
        out.appendLine("| rule | share |")
        out.appendLine("|---|---|")
        for (r in RuleType.entries) out.appendLine("| $r | %.1f%% |".format(100.0 * rules.count { it == r } / rules.size))
        val reasons = samples.flatMap { it.generated.stats.failures.entries }.groupBy({ it.key }, { it.value })
        if (reasons.isNotEmpty()) {
            out.appendLine()
            out.appendLine("Discarded attempts by reason (total): " + reasons.entries.sortedByDescending { it.value.sum() }.joinToString { "${it.key}=${it.value.sum()}" })
        }
    }
}

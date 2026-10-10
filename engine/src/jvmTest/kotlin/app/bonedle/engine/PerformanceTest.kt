package app.bonedle.engine

import app.bonedle.engine.generator.PuzzleGenerator
import app.bonedle.engine.model.Difficulty
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Spec 7.3 budgets: mean generation time per board on a 2021 mid-range phone — Easy/Medium
 * ≤ 150 ms, Hard ≤ 400 ms, Expert ≤ 1.5 s.
 *
 * Assumption: a 2021 mid-range Android device runs this single-threaded code about
 * [DEVICE_SLOWDOWN]× slower than a desktop JVM, so the desktop thresholds are the budgets divided
 * by that factor. Measured single-threaded after a warm-up so JIT compilation is excluded, as it
 * is on device after the Baseline Profile is applied. Time is the generating thread's CPU time,
 * so other load on the machine does not distort it (Windows reports it in ~15.6 ms ticks, so
 * single-board values and p95 are quantised; the mean over many boards is still accurate).
 * Runs in its own task (`performanceTest`),
 * never alongside the multi-threaded suites. On-device numbers are re-checked in the hardening
 * phase with a macrobenchmark.
 */
class PerformanceTest {
    private val cpu = ManagementFactory.getThreadMXBean()

    @Test
    fun meanGenerationTimeIsWithinBudget() {
        val budgetsMs = mapOf(
            Difficulty.EASY to 150.0,
            Difficulty.MEDIUM to 150.0,
            Difficulty.HARD to 400.0,
            Difficulty.EXPERT to 1500.0,
        )
        // Warm-up.
        for (d in Difficulty.entries) for (s in 0L until 15L) PuzzleGenerator.generate(-1_000L - s, d)
        val lines = ArrayList<String>()
        val over = ArrayList<String>()
        for ((d, budget) in budgetsMs) {
            val times = (0L until SAMPLES).map { s ->
                val t0 = cpu.currentThreadCpuTime
                PuzzleGenerator.generate(9_000_000L + s, d)
                (cpu.currentThreadCpuTime - t0) / 1e6
            }.sorted()
            val mean = times.average()
            val p95 = times[(times.size * 95) / 100]
            val limit = budget / DEVICE_SLOWDOWN
            lines += "%-6s mean %7.1f ms  p95 %7.1f ms CPU  desktop limit %6.1f ms (device budget %.0f ms)".format(d, mean, p95, limit, budget)
            if (mean > limit) over += "$d mean %.1f ms > %.1f ms".format(mean, limit)
        }
        lines.forEach(::println)
        assertTrue(over.isEmpty(), "over budget: $over")
    }

    private companion object {
        const val SAMPLES = 120L
        const val DEVICE_SLOWDOWN = 3.0
    }
}

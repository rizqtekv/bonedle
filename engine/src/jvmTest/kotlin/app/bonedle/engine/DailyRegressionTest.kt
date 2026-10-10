package app.bonedle.engine

import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.fail

/** Spec 7.6 regression: every daily board for the first 730 days must generate and validate. */
class DailyRegressionTest {
    @Test
    fun firstTwoYearsOfDailiesAreValid() {
        val failures = ConcurrentLinkedQueue<String>()
        val pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors().coerceAtMost(16))
        for (number in 1..DAYS) {
            for (d in Difficulty.DAILY) {
                pool.execute {
                    try {
                        val p = DailySchedule.puzzle(number, d)
                        if (p.seed != DailySchedule.seedFor(number, d)) failures.add("#$number $d: wrong seed")
                        val problems = PuzzleChecks.problems(p)
                        if (problems.isNotEmpty()) failures.add("#$number $d (${DailySchedule.dateOf(number)}): $problems")
                    } catch (e: Throwable) {
                        failures.add("#$number $d: ${e::class.simpleName}: ${e.message}")
                    }
                }
            }
        }
        pool.shutdown()
        pool.awaitTermination(2, TimeUnit.HOURS)
        if (failures.isNotEmpty()) fail("${failures.size} daily failures:\n" + failures.take(30).joinToString("\n"))
        println("Validated ${DAYS * Difficulty.DAILY.size} daily boards (#1-#$DAYS, ${DailySchedule.LAUNCH_DATE} to ${DailySchedule.dateOf(DAYS)}).")
    }

    private companion object {
        const val DAYS = 730
    }
}

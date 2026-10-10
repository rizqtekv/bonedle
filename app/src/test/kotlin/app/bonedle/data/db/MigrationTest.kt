package app.bonedle.data.db

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.driver.AndroidSQLiteDriver
import androidx.sqlite.execSQL
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Every schema upgrade must keep the player's saves and results. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        instrumentation = InstrumentationRegistry.getInstrumentation(),
        file = ApplicationProvider.getApplicationContext<android.content.Context>().getDatabasePath("migration-test.db"),
        driver = AndroidSQLiteDriver(),
        databaseClass = BonedleDatabase::class,
    )

    @Test
    fun v1ToV2KeepsResultsAndSavesAndAddsFreezes() {
        helper.createDatabase(1).apply {
            execSQL(
                "INSERT INTO results (gameKey, mode, difficulty, dailyNumber, seed, timeMs, hintsUsed, undoCount, source, completedAt, localDate) " +
                    "VALUES ('d:1:easy', 'daily', 'easy', 1, 5, 61000, 0, 2, 'home', 1, '2026-11-02')",
            )
            execSQL(
                "INSERT INTO saved_games (gameKey, puzzle, board, elapsedMs, hintsUsed, undoCount, source, startedAt, updatedAt) " +
                    "VALUES ('d:1:hard', 'p', 'b', 1000, 1, 0, 'home', 1, 2)",
            )
            close()
        }
        helper.runMigrationsAndValidate(2, emptyList()).apply {
            prepare("SELECT timeMs FROM results WHERE gameKey = 'd:1:easy'").use {
                it.step()
                assertEquals(61_000L, it.getLong(0))
            }
            prepare("SELECT COUNT(*) FROM saved_games").use {
                it.step()
                assertEquals(1L, it.getLong(0))
            }
            execSQL("INSERT INTO streak_freezes (epochDay, weekStart, source, createdAt) VALUES (20760, 20759, 'free', 0)")
            close()
        }
    }

    @Test
    fun v2ToV3AddsChallengesAndKeepsEverything() {
        helper.createDatabase(2).apply {
            execSQL("INSERT INTO streak_freezes (epochDay, weekStart, source, createdAt) VALUES (20760, 20759, 'free', 0)")
            execSQL(
                "INSERT INTO results (gameKey, mode, difficulty, dailyNumber, seed, timeMs, hintsUsed, undoCount, source, completedAt, localDate) " +
                    "VALUES ('d:2:easy', 'daily', 'easy', 2, 5, 45000, 0, 0, 'link', 1, '2026-11-03')",
            )
            close()
        }
        helper.runMigrationsAndValidate(3, emptyList()).apply {
            prepare("SELECT COUNT(*) FROM streak_freezes").use {
                it.step()
                assertEquals(1L, it.getLong(0))
            }
            prepare("SELECT timeMs FROM results WHERE gameKey = 'd:2:easy'").use {
                it.step()
                assertEquals(45_000L, it.getLong(0))
            }
            execSQL("INSERT INTO challenges (gameKey, opponentMs, opponentHints, receivedAt) VALUES ('d:2:easy', 134000, 1, 0)")
            close()
        }
    }

    @Test
    fun v3ToV4AddsEarnedHintsToSaves() {
        helper.createDatabase(3).apply {
            execSQL(
                "INSERT INTO saved_games (gameKey, puzzle, board, elapsedMs, hintsUsed, undoCount, source, startedAt, updatedAt) " +
                    "VALUES ('u:7:hard', 'p', 'b', 1000, 2, 0, 'home', 1, 2)",
            )
            close()
        }
        helper.runMigrationsAndValidate(4, emptyList()).apply {
            prepare("SELECT hintsUsed, extraHints FROM saved_games WHERE gameKey = 'u:7:hard'").use {
                it.step()
                assertEquals(2L, it.getLong(0))
                assertEquals(0L, it.getLong(1))
            }
            close()
        }
    }
}

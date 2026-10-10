package app.bonedle.data.links

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.bonedle.data.db.BonedleDatabase
import app.bonedle.data.db.GameResultEntity
import app.bonedle.data.game.GameId
import app.bonedle.data.settings.GrowthPreferences
import app.bonedle.domain.BoardLink
import app.bonedle.domain.Challenge
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import app.bonedle.notifications.ReminderNotifier
import app.bonedle.testing.RecordingAnalytics
import app.bonedle.testing.TestClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [36])
class LinkRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var db: BonedleDatabase
    private lateinit var growth: GrowthPreferences
    private val analytics = RecordingAnalytics()
    private val clock = TestClock(today = DailySchedule.dateOf(10))
    private lateinit var links: LinkRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, BonedleDatabase::class.java).allowMainThreadQueries().setQueryCoroutineContext(dispatcher).build()
        growth = GrowthPreferences(PreferenceDataStoreFactory.create(scope = CoroutineScope(dispatcher + SupervisorJob())) { tmp.newFile("growth.preferences_pb") })
        links = LinkRepository(growth, db.games(), clock, analytics)
    }

    @After
    fun tearDown() = db.close()

    private fun solved(id: GameId.Daily) = GameResultEntity(
        gameKey = id.key, mode = "daily", difficulty = id.difficulty.code, dailyNumber = id.number, seed = 0, timeMs = 60_000,
        hintsUsed = 0, undoCount = 0, source = "home", completedAt = 1, localDate = DailySchedule.dateOf(id.number).toIsoString(),
    )

    @Test
    fun aChallengeLinkWaitsWithTheFriendsResultUntilOpened() = runTest(dispatcher) {
        val id = GameId.Daily(9, Difficulty.HARD)
        val link = BoardLink(id, Challenge(134_000, 1))
        assertTrue(links.accept(link, installed = true))
        assertEquals(PendingLink(link, solved = false), links.pending.first())
        assertEquals(Challenge(134_000, 1), links.challenge(id))
        val open = analytics.named("link_open").single().params
        assertEquals("d/9/hard", open["board_id"])
        assertEquals("true", open["installed"])
        links.opened(link)
        assertNull(links.pending.first())
        // The friend's time stays for the head-to-head.
        assertEquals(Challenge(134_000, 1), links.challenge(id))
    }

    @Test
    fun anAlreadySolvedBoardOpensItsResult() = runTest(dispatcher) {
        val id = GameId.Daily(8, Difficulty.EASY)
        db.games().insertResult(solved(id))
        links.accept(BoardLink(id), installed = true)
        assertTrue(requireNotNull(links.pending.first()).solved)
    }

    @Test
    fun aFutureDailyIsRefused() = runTest(dispatcher) {
        assertFalse(links.accept(BoardLink(GameId.Daily(12, Difficulty.EASY)), installed = true))
        assertNull(links.pending.first())
        assertTrue(analytics.events.isEmpty())
    }

    @Test
    fun aNewerLinkReplacesTheWaitingOne() = runTest(dispatcher) {
        val first = BoardLink(GameId.Daily(3, Difficulty.EASY))
        val second = BoardLink(GameId.Unlimited(77, Difficulty.EXPERT), Challenge(600_000, 0))
        links.accept(first, installed = true)
        links.accept(second, installed = false)
        // Opening the first one late must not clear the second.
        links.opened(first)
        assertEquals(second, links.pending.first()?.link)
    }

    @Test
    fun theReminderStaysQuietOnceTodaysDailyIsSolved() = runTest(dispatcher) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notifier = ReminderNotifier(context, db.games(), clock)
        db.games().insertResult(solved(GameId.Daily(9, Difficulty.EASY)))
        // Day 10, unsolved, with yesterday played: remind, and mention the streak.
        assertEquals(10, notifier.contentForToday()?.dailyNumber)
        assertEquals(1, notifier.contentForToday()?.streak)
        db.games().insertResult(solved(GameId.Daily(10, Difficulty.MEDIUM)))
        assertNull(notifier.contentForToday())
    }
}

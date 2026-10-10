package app.bonedle.domain

import app.bonedle.data.game.GameId
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LinksTest {
    private val domain = Links.SITE

    @Test
    fun dailyAndUnlimitedLinksRoundTrip() {
        val daily = GameId.Daily(142, Difficulty.MEDIUM)
        assertEquals("https://$domain/d/142/medium", Links.forGame(daily))
        assertEquals(BoardLink(daily), Links.parse("https://$domain/d/142/medium"))
        // Negative seeds are written unsigned, so they survive the trip.
        for (seed in listOf(0L, 1L, -1L, Long.MIN_VALUE, Long.MAX_VALUE, 0x5DEECE66DL)) {
            val id = GameId.Unlimited(seed, Difficulty.EXPERT)
            assertEquals(BoardLink(id), Links.parse(requireNotNull(Links.forGame(id))))
        }
        assertNull(Links.forGame(GameId.Tutorial(0)))
    }

    @Test
    fun aChallengeLinkCarriesTheSendersResult() {
        val id = GameId.Daily(7, Difficulty.HARD)
        val url = requireNotNull(Links.forGame(id, Challenge(timeMs = 134_900, hintsUsed = 1)))
        assertEquals("https://$domain/d/7/hard?t=134&h=1", url)
        assertEquals(BoardLink(id, Challenge(134_000, 1)), Links.parse(url))
        assertEquals("d/7/hard?t=134&h=1", Links.relative(BoardLink(id, Challenge(134_000, 1))))
        assertEquals(BoardLink(id, Challenge(134_000, 1)), Links.parsePath("d/7/hard?t=134&h=1"))
    }

    @Test
    fun theShortDailyFormOpensEasy() {
        assertEquals(BoardLink(GameId.Daily(142, Difficulty.EASY)), Links.parse("https://$domain/d/142"))
        assertEquals(BoardLink(GameId.Daily(3, Difficulty.HARD)), Links.parse("https://www.$domain/d/3/HARD/#top"))
    }

    @Test
    fun malformedLinksAreRejected() {
        val bad = listOf(
            "https://example.com/d/1/easy",
            "https://${Links.HOST}/elsewhere/d/1/easy",
            "ftp://$domain/d/1/easy",
            "https://$domain/",
            "https://$domain/d/0/easy",
            "https://$domain/d/x/easy",
            "https://$domain/d/1/expert", // no Expert daily
            "https://$domain/d/1/easy/extra",
            "https://$domain/u/zzzzzzzzzzzzzzzzz/hard", // seed overflow
            "https://$domain/u/abc", // no difficulty
            "https://$domain/u/abc/legendary",
            "https://$domain/privacy",
        )
        for (url in bad) assertNull(url, Links.parse(url))
    }

    @Test
    fun aBrokenChallengeDropsOnlyTheChallenge() {
        val board = GameId.Daily(5, Difficulty.EASY)
        assertEquals(BoardLink(board), Links.parsePath("d/5/easy?t=abc"))
        assertEquals(BoardLink(board), Links.parsePath("d/5/easy?t=0&h=0"))
        assertEquals(BoardLink(board), Links.parsePath("d/5/easy?t=10&h=-1"))
        assertEquals(BoardLink(board, Challenge(10_000, 0)), Links.parsePath("d/5/easy?utm_source=x&t=10"))
    }

    @Test
    fun futureDailiesStayHiddenExceptTomorrow() {
        val today = DailySchedule.dateOf(10)
        assertTrue(Links.isPlayable(BoardLink(GameId.Daily(10, Difficulty.EASY)), today))
        assertTrue(Links.isPlayable(BoardLink(GameId.Daily(11, Difficulty.EASY)), today))
        assertFalse(Links.isPlayable(BoardLink(GameId.Daily(12, Difficulty.EASY)), today))
        assertTrue(Links.isPlayable(BoardLink(GameId.Unlimited(9, Difficulty.HARD)), today))
    }

    @Test
    fun boardIdIsThePublicPath() {
        assertEquals("d/142/medium", Links.boardId(GameId.Daily(142, Difficulty.MEDIUM)))
        assertEquals("u/z/easy", Links.boardId(GameId.Unlimited(35, Difficulty.EASY)))
        assertEquals("t:0:easy", Links.boardId(GameId.Tutorial(0)))
    }
}

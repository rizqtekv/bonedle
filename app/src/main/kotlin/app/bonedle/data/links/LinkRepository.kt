package app.bonedle.data.links

import app.bonedle.analytics.Analytics
import app.bonedle.analytics.Events
import app.bonedle.data.Clock
import app.bonedle.data.db.ChallengeEntity
import app.bonedle.data.db.GameDao
import app.bonedle.data.game.GameId
import app.bonedle.data.settings.GrowthPreferences
import app.bonedle.domain.BoardLink
import app.bonedle.domain.Challenge
import app.bonedle.domain.Links
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** A board link waiting for navigation. [solved] boards open their result instead of the board. */
data class PendingLink(val link: BoardLink, val solved: Boolean)

/**
 * Challenge links in (spec 8.2): App Links and the install referrer both end here. A link is
 * checked, its sender's result is kept for the head-to-head, and the board waits as
 * [pending] until navigation opens it: at once for a returning player, after the tutorial for a
 * new one. The pending board is persisted, so it survives the tutorial and process death.
 */
@Singleton
class LinkRepository @Inject constructor(
    private val growth: GrowthPreferences,
    private val dao: GameDao,
    private val clock: Clock,
    private val analytics: Analytics,
) {
    val pending: Flow<PendingLink?> = growth.pendingLink.map { path ->
        val link = path?.let(Links::parsePath) ?: return@map null
        PendingLink(link, solved = dao.firstResult(link.id.key) != null)
    }

    /**
     * Takes in [link]. [installed] is false when it came through the install referrer (the
     * friend did not have the app). Returns false for a board that cannot be played yet.
     */
    suspend fun accept(link: BoardLink, installed: Boolean): Boolean {
        if (!Links.isPlayable(link, clock.today())) return false
        analytics.log(Events.linkOpen(link.boardId, installed))
        link.challenge?.let {
            dao.upsertChallenge(ChallengeEntity(link.id.key, it.timeMs, it.hintsUsed, clock.nowMillis()))
        }
        growth.setPendingLink(Links.relative(link))
        return true
    }

    /** Navigation opened [link]. */
    suspend fun opened(link: BoardLink) = growth.clearPendingLink(Links.relative(link))

    /** The friend's result on [id], if a challenge link brought it. */
    suspend fun challenge(id: GameId): Challenge? = dao.challenge(id.key)?.let { Challenge(it.opponentMs, it.opponentHints) }
}

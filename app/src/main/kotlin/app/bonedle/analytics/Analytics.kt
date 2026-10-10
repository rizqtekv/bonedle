package app.bonedle.analytics

import androidx.compose.runtime.staticCompositionLocalOf
import app.bonedle.data.game.GameId
import app.bonedle.data.game.GameSource
import app.bonedle.domain.Links

/**
 * One analytics event (spec 10). Parameter values are [String] or [Long] only, and never
 * carry personal data: boards are named by their public link path, apps by package name.
 */
data class AnalyticsEvent(val name: String, val params: Map<String, Any> = emptyMap())

/** Where analytics events go. */
interface Analytics {
    fun log(event: AnalyticsEvent)

    /** A user property used to segment the funnels, e.g. `referred = true`. */
    fun setUserProperty(name: String, value: String)
}

/** Drops everything: previews, screenshot tests, and builds without Firebase keys. */
object NoAnalytics : Analytics {
    override fun log(event: AnalyticsEvent) = Unit

    override fun setUserProperty(name: String, value: String) = Unit
}

/** Analytics for composables that log directly (sharing). */
val LocalAnalytics = staticCompositionLocalOf<Analytics> { NoAnalytics }

/** Where a share started (`share_open.surface`). */
enum class ShareSurface(val code: String) {
    /** The Solved screen's "Share result". */
    SOLVED("solved"),

    /** "Send it back" after a head-to-head. */
    HEAD_TO_HEAD("head_to_head"),

    /** Home's "Challenge a friend" card. */
    CHALLENGE("challenge"),

    /** "Copy text" on the Solved screen. */
    COPY("copy"),
}

/**
 * The event catalogue. Together these give the spec's metrics: retention comes from Firebase's
 * own `first_open` / engagement; puzzles per DAU from `puzzle_complete`; share rate per
 * completion from `share_complete` / `puzzle_complete`; link-open-to-completion from
 * `link_open` against `puzzle_complete` with `source = link`; and the referral factor from
 * `referred_install` and the `referred` user property (referred players active on day 7).
 */
object Events {
    const val USER_REFERRED = "referred"

    fun tutorialStep(step: Int, source: String) = AnalyticsEvent(
        "tutorial_step",
        mapOf("step" to step.toLong(), "source" to source),
    )

    fun tutorialComplete(skipped: Boolean) = AnalyticsEvent("tutorial_complete", mapOf("skipped" to skipped.toString()))

    fun puzzleStart(id: GameId, source: GameSource) = AnalyticsEvent(
        "puzzle_start",
        board(id) + ("source" to source.code),
    )

    fun puzzleComplete(id: GameId, source: GameSource, timeMs: Long, hints: Int, undoCount: Int) = AnalyticsEvent(
        "puzzle_complete",
        board(id) + mapOf(
            "source" to source.code,
            "time_s" to timeMs / 1000,
            "hints" to hints.toLong(),
            "undo_count" to undoCount.toLong(),
        ),
    )

    fun puzzleAbandon(id: GameId, source: GameSource, timeMs: Long, placed: Int) = AnalyticsEvent(
        "puzzle_abandon",
        board(id) + mapOf("source" to source.code, "time_s" to timeMs / 1000, "placed" to placed.toLong()),
    )

    /** [level] 1 highlights the region, 2 names the deduction, 3 places the domino. */
    fun hintUsed(id: GameId, level: Int) = AnalyticsEvent("hint_used", board(id) + ("level" to level.toLong()))

    fun shareOpen(surface: ShareSurface, id: GameId?) = AnalyticsEvent(
        "share_open",
        (id?.let(::board) ?: emptyMap()) + ("surface" to surface.code),
    )

    /** [channel] is the package name of the app the player picked in the Sharesheet. */
    fun shareComplete(surface: String, channel: String) = AnalyticsEvent(
        "share_complete",
        mapOf("surface" to surface, "channel" to channel),
    )

    /** [installed] false means the link reached the app through the Play install referrer. */
    fun linkOpen(boardId: String, installed: Boolean) = AnalyticsEvent(
        "link_open",
        mapOf("board_id" to boardId, "installed" to installed.toString()),
    )

    fun referredInstall(boardId: String) = AnalyticsEvent("referred_install", mapOf("board_id" to boardId))

    /** A referred player finished the board they were invited to. */
    fun referredPuzzleComplete(boardId: String, timeMs: Long) = AnalyticsEvent(
        "referred_puzzle_complete",
        mapOf("board_id" to boardId, "time_s" to timeMs / 1000),
    )

    fun streakFreezeUsed(source: String) = AnalyticsEvent("streak_freeze_used", mapOf("source" to source))

    fun reviewPromptShown() = AnalyticsEvent("review_prompt_shown")

    private fun board(id: GameId): Map<String, Any> = mapOf(
        "mode" to mode(id),
        "difficulty" to id.difficulty.code,
        "board_id" to Links.boardId(id),
    )

    private fun mode(id: GameId): String = when (id) {
        is GameId.Daily -> "daily"
        is GameId.Unlimited -> "unlimited"
        is GameId.Tutorial -> "tutorial"
    }
}

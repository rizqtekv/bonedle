package app.bonedle.domain

import app.bonedle.BuildConfig
import app.bonedle.data.game.GameId
import app.bonedle.engine.daily.CalendarDate
import app.bonedle.engine.daily.DailySchedule
import app.bonedle.engine.model.Difficulty

/** A sender's result carried by a challenge link (`?t=<seconds>&h=<hints>`). */
data class Challenge(val timeMs: Long, val hintsUsed: Int)

/** A board reached through a link, with the sender's result when the link carries one. */
data class BoardLink(val id: GameId, val challenge: Challenge? = null) {
    /** The board's public path, also its analytics `board_id`: `d/142/medium`. */
    val boardId: String get() = Links.boardId(id)
}

/**
 * Public links to a board (spec 8.2): `https://<site>/d/<number>/<difficulty>` for dailies and
 * `https://<site>/u/<seed>/<difficulty>` for Unlimited boards, the seed written as unsigned
 * base 36 to keep links short. Links shared from a result add the sender's time and hints, so
 * the friend who opens it gets a head-to-head. Verified App Links open them in the app; the
 * website hands the same path to the app through the Play install referrer.
 */
object Links {
    /** The website's host, from `bonedle.siteUrl` (the manifest's App Link host too). */
    val HOST: String = BuildConfig.LINK_HOST

    /** The site's path on [HOST], `/bonedle` for a GitHub Pages project site, or empty. */
    val PATH: String = BuildConfig.LINK_PATH

    /** Host and path, as links are shown: `rizqtekv.github.io/bonedle`. */
    val SITE: String = HOST + PATH

    val PRIVACY_POLICY: String = "https://$SITE/privacy.html"

    /** Longest time a link may carry: 99:59:59. */
    private const val MAX_SECONDS = 99L * 3600 + 59 * 60 + 59
    private const val MAX_HINTS = 99

    /** The link that opens [id] (with the sender's [challenge], if any), or null for tutorial boards. */
    fun forGame(id: GameId, challenge: Challenge? = null): String? =
        relative(id, challenge)?.let { "https://$SITE/$it" }

    /** [link] as a path and query without the domain: `d/142/medium?t=134&h=0`. */
    fun relative(link: BoardLink): String = requireNotNull(relative(link.id, link.challenge)) { "tutorial boards have no link" }

    private fun relative(id: GameId, challenge: Challenge?): String? {
        val path = path(id) ?: return null
        val query = challenge?.let { "?t=${(it.timeMs / 1000).coerceIn(1, MAX_SECONDS)}&h=${it.hintsUsed.coerceIn(0, MAX_HINTS)}" }.orEmpty()
        return path + query
    }

    private fun path(id: GameId): String? = when (id) {
        is GameId.Daily -> "d/${id.number}/${id.difficulty.code}"
        is GameId.Unlimited -> "u/${java.lang.Long.toUnsignedString(id.seed, 36)}/${id.difficulty.code}"
        is GameId.Tutorial -> null
    }

    /** A stable, public name for the board in analytics: its link path, e.g. `u/3k9x0p/hard`. */
    fun boardId(id: GameId): String = path(id) ?: id.key

    /** [url] without its scheme, as shown in share text: `rizqtekv.github.io/bonedle/d/142/medium`. */
    fun display(url: String): String = url.removePrefix("https://")

    /**
     * The board an App Link points to, or null if [url] is not a board link on [SITE]
     * (`www.` is accepted on the host too).
     */
    fun parse(url: String): BoardLink? {
        val rest = when {
            url.startsWith("https://") -> url.removePrefix("https://")
            url.startsWith("http://") -> url.removePrefix("http://")
            else -> return null
        }
        val slash = rest.indexOf('/')
        if (slash <= 0) return null
        val host = rest.substring(0, slash).substringBefore(':').lowercase()
        if (host != HOST && host != "www.$HOST") return null
        val path = rest.substring(slash)
        if (!path.startsWith("$PATH/")) return null
        return parsePath(path.removePrefix("$PATH/"))
    }

    /**
     * Parses a path and query such as `d/142/medium?t=134&h=0` (no domain). A daily without a
     * difficulty (`d/142`, the short form in share text) opens Easy. Unknown query keys and a
     * fragment are ignored; a malformed time or hint count drops the challenge, not the board.
     */
    fun parsePath(pathAndQuery: String): BoardLink? {
        val withoutFragment = pathAndQuery.substringBefore('#')
        val path = withoutFragment.substringBefore('?').trim('/')
        val query = withoutFragment.substringAfter('?', missingDelimiterValue = "")
        val parts = path.split('/')
        val id: GameId = when (parts.firstOrNull()) {
            "d" -> {
                if (parts.size !in 2..3) return null
                val number = parts[1].toIntOrNull()?.takeIf { it >= DailySchedule.FIRST_NUMBER } ?: return null
                val difficulty = if (parts.size == 3) Difficulty.fromCode(parts[2].lowercase()) ?: return null else Difficulty.EASY
                if (difficulty !in Difficulty.DAILY) return null
                GameId.Daily(number, difficulty)
            }
            "u" -> {
                if (parts.size != 3) return null
                val seed = runCatching { java.lang.Long.parseUnsignedLong(parts[1].lowercase(), 36) }.getOrNull() ?: return null
                val difficulty = Difficulty.fromCode(parts[2].lowercase()) ?: return null
                GameId.Unlimited(seed, difficulty)
            }
            else -> return null
        }
        return BoardLink(id, challenge(query))
    }

    private fun challenge(query: String): Challenge? {
        if (query.isEmpty()) return null
        val params = query.split('&').mapNotNull { p ->
            val eq = p.indexOf('=')
            if (eq <= 0) null else p.substring(0, eq) to p.substring(eq + 1)
        }.toMap()
        val seconds = params["t"]?.toLongOrNull()?.takeIf { it in 1..MAX_SECONDS } ?: return null
        val hints = params["h"]?.let { h -> h.toIntOrNull()?.takeIf { it in 0..MAX_HINTS } ?: return null } ?: 0
        return Challenge(seconds * 1000, hints)
    }

    /**
     * Whether [link] can be played on [today]. Future dailies stay hidden, except tomorrow's:
     * a friend a time zone ahead may already be on it.
     */
    fun isPlayable(link: BoardLink, today: CalendarDate): Boolean = when (val id = link.id) {
        is GameId.Daily -> id.number <= DailySchedule.numberOn(today) + 1
        is GameId.Unlimited -> true
        is GameId.Tutorial -> false
    }
}

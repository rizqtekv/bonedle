package app.bonedle.domain

import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The Play install referrer that hands a challenge link to a fresh install (spec 8.2). The
 * website's Play Store button passes
 * `referrer=utm_source=bonedle&utm_medium=challenge&utm_content=<encoded board path>`, and the
 * first launch reads it back to open that board. `utm_content` is always last, so a board path
 * whose own query uses `&` survives even if a store decodes the value once more.
 */
object Referrer {
    const val SOURCE = "bonedle"
    private const val CONTENT = "utm_content="

    /** The referrer string for [boardPath] (`d/142/medium?t=134&h=0`), as the website builds it. */
    fun forPath(boardPath: String): String =
        "utm_source=$SOURCE&utm_medium=challenge&$CONTENT${URLEncoder.encode(boardPath, Charsets.UTF_8.name())}"

    /** The board path in [referrer], or null when the install did not come from a Bonedle link. */
    fun boardPath(referrer: String): String? {
        val source = referrer.split('&').firstOrNull { it.startsWith("utm_source=") }?.substringAfter('=')
        if (source != SOURCE) return null
        val start = referrer.indexOf(CONTENT)
        if (start < 0) return null
        val raw = referrer.substring(start + CONTENT.length)
        val path = if ('%' in raw) runCatching { URLDecoder.decode(raw, Charsets.UTF_8.name()) }.getOrNull() else raw
        return path?.takeIf { it.isNotBlank() }
    }
}

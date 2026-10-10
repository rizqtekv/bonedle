package app.bonedle.domain

/** Who won a head-to-head, from the player's side. */
enum class Outcome { WON, LOST, TIE }

/**
 * The player's time against a friend's on the same board (spec 8.3). Times compare in whole
 * seconds, the precision a link carries.
 */
data class HeadToHead(val mineMs: Long, val mineHints: Int, val theirs: Challenge) {
    private val mineSeconds: Long get() = mineMs / 1000
    private val theirSeconds: Long get() = theirs.timeMs / 1000

    val outcome: Outcome
        get() = when {
            mineSeconds < theirSeconds -> Outcome.WON
            mineSeconds > theirSeconds -> Outcome.LOST
            else -> Outcome.TIE
        }

    /** How far apart the two times are. */
    val marginMs: Long get() = kotlin.math.abs(mineSeconds - theirSeconds) * 1000
}

/**
 * When to ask for a Play rating (spec 8.6): right after a board solved without hints, once the
 * player has solved at least [MIN_CLEAN_SOLVES] that way, at most once every [COOLDOWN_DAYS]
 * days, and never after a loss (a head-to-head the player just lost).
 */
object ReviewPolicy {
    const val MIN_CLEAN_SOLVES = 3
    const val COOLDOWN_DAYS = 60
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /**
     * @param cleanSolves boards solved without hints so far, this one included.
     * @param hintsThisSolve hints used on the board just solved.
     * @param lastAskedAt when the prompt was last requested, or null if never.
     */
    fun shouldAsk(cleanSolves: Int, hintsThisSolve: Int, lastAskedAt: Long?, now: Long, lostHeadToHead: Boolean): Boolean =
        hintsThisSolve == 0 &&
            cleanSolves >= MIN_CLEAN_SOLVES &&
            !lostHeadToHead &&
            (lastAskedAt == null || now - lastAskedAt >= COOLDOWN_DAYS * DAY_MS)
}

/**
 * When to offer the daily reminder (spec 8.5): on the Solved screen of a daily, once the player
 * has completed [MIN_DAILIES] dailies, only once, and never if it is already on. The Android 13+
 * notification permission is requested only when the player accepts this offer (or turns the
 * reminder on in Settings).
 */
object ReminderOffer {
    const val MIN_DAILIES = 3

    fun shouldOffer(isDaily: Boolean, dailiesCompleted: Int, alreadyOffered: Boolean, reminderOn: Boolean): Boolean =
        isDaily && dailiesCompleted >= MIN_DAILIES && !alreadyOffered && !reminderOn
}

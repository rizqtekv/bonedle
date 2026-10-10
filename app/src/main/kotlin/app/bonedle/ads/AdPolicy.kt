package app.bonedle.ads

/** Where an ad appears. Every placement but [BETWEEN_UNLIMITED] is opt-in (rewarded). */
enum class AdPlacement(val code: String) {
    /** Rewarded: one more hint on an Unlimited board once the free hints are used. */
    EXTRA_HINT("extra_hint"),

    /** Rewarded: saves the streak over a missed day once the weekly free freeze is spent. */
    STREAK_SAVER("streak_saver"),

    /** Interstitial: between Unlimited puzzles. */
    BETWEEN_UNLIMITED("between_unlimited"),
}

/**
 * The ad rules of spec 2 and 9, in one place. No ad ever appears in the first session, before
 * consent where it is required, on a daily board, or (interstitials) for Bonedle Plus owners.
 */
object AdPolicy {
    /** At most one interstitial per this many solved Unlimited boards... */
    const val INTERSTITIAL_EVERY_SOLVES = 3

    /** ...and never two within this long. */
    const val INTERSTITIAL_MIN_GAP_MS = 3 * 60_000L

    /** "Right after an ad", for the rating prompt (spec 8.6). */
    const val RECENT_AD_MS = 10 * 60_000L

    /** Ads may be loaded and shown at all. */
    fun adsAllowed(adsEnabled: Boolean, canRequestAds: Boolean, firstSession: Boolean): Boolean =
        adsEnabled && canRequestAds && !firstSession

    /**
     * Whether "Next puzzle" after an Unlimited board shows the interstitial first.
     *
     * @param solvedSinceLast Unlimited boards solved since the last interstitial (or ever).
     * @param msSinceLast time since the last interstitial, or null if there was none.
     */
    fun interstitialDue(allowed: Boolean, plus: Boolean, solvedSinceLast: Int, msSinceLast: Long?): Boolean =
        allowed && !plus && solvedSinceLast >= INTERSTITIAL_EVERY_SOLVES &&
            (msSinceLast == null || msSinceLast >= INTERSTITIAL_MIN_GAP_MS)

    /** The rewarded extra hint is offered on Unlimited boards only (no ads on dailies, spec 2). */
    fun extraHintOffered(allowed: Boolean, plus: Boolean, unlimitedBoard: Boolean): Boolean =
        allowed && !plus && unlimitedBoard

    /** Whether an ad was shown recently enough that a rating prompt must wait. */
    fun adShownRecently(lastShownAt: Long?, now: Long): Boolean =
        lastShownAt != null && now - lastShownAt < RECENT_AD_MS
}

package app.bonedle.engine.daily

/**
 * Daily-streak arithmetic on epoch days (see [CalendarDate.epochDay]).
 *
 * A day is *played* when the player solved that day's daily board (any difficulty) on that day.
 * A *frozen* day is a missed day covered by a streak freeze: it keeps the streak alive but does
 * not add to it. One free freeze is available per ISO week (Monday to Sunday) of the missed day.
 */
object Streaks {
    /**
     * Current streak on [today]: played days in the unbroken run of played-or-frozen days that
     * ends today, or yesterday when today's daily is not solved yet (the streak is not lost
     * until the day is over).
     */
    fun current(played: Set<Long>, frozen: Set<Long>, today: Long): Int {
        var day = if (today in played) today else today - 1
        var count = 0
        while (day in played || day in frozen) {
            if (day in played) count++
            day--
        }
        return count
    }

    /** Longest run of played days ever, bridging frozen days. */
    fun best(played: Set<Long>, frozen: Set<Long>): Int {
        var best = 0
        for (start in played) {
            // Only count runs from their first day.
            if (start - 1 in played || start - 1 in frozen) continue
            var day = start
            var count = 0
            while (day in played || day in frozen) {
                if (day in played) count++
                day++
            }
            if (count > best) best = count
        }
        return best
    }

    /**
     * The day to freeze on [today], or null. A free freeze covers yesterday when yesterday was
     * missed, the streak was alive the day before, and no free freeze was used in yesterday's
     * week ([freeFreezeWeeks] holds week starts, see [weekStart]).
     */
    fun freezeToApply(played: Set<Long>, frozen: Set<Long>, freeFreezeWeeks: Set<Long>, today: Long): Long? {
        val missed = today - 1
        if (missed in played || missed in frozen) return null
        val before = missed - 1
        if (before !in played && before !in frozen) return null
        if (weekStart(missed) in freeFreezeWeeks) return null
        return missed
    }

    /** Rewarded "streak saver" freezes allowed per ISO week, on top of the free one. */
    const val REWARD_FREEZES_PER_WEEK: Int = 1

    /**
     * The day a rewarded streak saver can rescue on [today], or null: yesterday, when it was
     * missed and the streak was alive the day before, and yesterday's week has fewer than
     * [REWARD_FREEZES_PER_WEEK] rewarded freezes ([rewardFreezeWeeks] holds the week start of
     * each one). Apply the free freeze ([freezeToApply]) first; this is for when it is spent.
     */
    fun rescueDay(played: Set<Long>, frozen: Set<Long>, rewardFreezeWeeks: List<Long>, today: Long): Long? {
        val missed = today - 1
        if (missed in played || missed in frozen) return null
        val before = missed - 1
        if (before !in played && before !in frozen) return null
        if (rewardFreezeWeeks.count { it == weekStart(missed) } >= REWARD_FREEZES_PER_WEEK) return null
        return missed
    }

    /** Epoch day of the Monday starting [day]'s ISO week. 1970-01-01 was a Thursday. */
    fun weekStart(day: Long): Long = day - (day + 3).mod(7L)
}

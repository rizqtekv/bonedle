package app.bonedle.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** One-off flags of the growth features: links, the install referrer, review and reminder prompts. */
@Singleton
class GrowthPreferences @Inject constructor(private val store: DataStore<Preferences>) {
    /** A board link waiting to be opened (relative form, `d/142/medium?t=134&h=0`). */
    val pendingLink: Flow<String?> = store.data.map { it[PENDING_LINK] }

    suspend fun setPendingLink(path: String) = store.edit { it[PENDING_LINK] = path }

    /** Clears the pending link if it is still [path] (a newer link is kept). */
    suspend fun clearPendingLink(path: String) = store.edit { if (it[PENDING_LINK] == path) it.remove(PENDING_LINK) }

    /** The install referrer was read (it is only meaningful on the first launch after install). */
    suspend fun referrerChecked(): Boolean = store.data.first()[REFERRER_CHECKED] ?: false

    suspend fun setReferrerChecked() = store.edit { it[REFERRER_CHECKED] = true }

    /** The board a referred install was invited to, until the player finishes it. */
    suspend fun setReferredBoard(boardId: String) = store.edit { it[REFERRED_BOARD] = boardId }

    /** True (once) if [boardId] is the board this install was referred to; the mark is then cleared. */
    suspend fun consumeReferredBoard(boardId: String): Boolean {
        var matched = false
        store.edit {
            if (it[REFERRED_BOARD] == boardId) {
                it.remove(REFERRED_BOARD)
                matched = true
            }
        }
        return matched
    }

    /** When the Play rating prompt was last requested, or null. */
    suspend fun reviewAskedAt(): Long? = store.data.first()[REVIEW_ASKED_AT]

    suspend fun setReviewAskedAt(at: Long) = store.edit { it[REVIEW_ASKED_AT] = at }

    /** Whether the Solved screen already offered the daily reminder. */
    val reminderOffered: Flow<Boolean> = store.data.map { it[REMINDER_OFFERED] ?: false }

    suspend fun setReminderOffered() = store.edit { it[REMINDER_OFFERED] = true }

    private companion object {
        val PENDING_LINK = stringPreferencesKey("pending_link")
        val REFERRER_CHECKED = booleanPreferencesKey("referrer_checked")
        val REFERRED_BOARD = stringPreferencesKey("referred_board")
        val REVIEW_ASKED_AT = longPreferencesKey("review_asked_at")
        val REMINDER_OFFERED = booleanPreferencesKey("reminder_offered")
    }
}

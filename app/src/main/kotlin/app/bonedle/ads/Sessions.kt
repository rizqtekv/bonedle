package app.bonedle.ads

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import app.bonedle.data.Clock
import app.bonedle.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Counts sessions, for "never in the first session" (spec 2, 9). A session is the app in the
 * foreground, with breaks shorter than [SESSION_GAP_MS] (the same rule Firebase Analytics uses).
 * Registered on the process lifecycle by [app.bonedle.BonedleApplication].
 */
@Singleton
class SessionTracker @Inject constructor(
    private val store: DataStore<Preferences>,
    private val clock: Clock,
    @ApplicationScope private val scope: CoroutineScope,
) : DefaultLifecycleObserver {
    private val mutex = Mutex()
    private val _session = MutableStateFlow(0)

    /** The current session's number from 1; 0 until the first foreground is recorded. */
    val session: StateFlow<Int> = _session.asStateFlow()

    /** True until a second session starts (and while unknown, the safe answer). */
    val firstSession: Flow<Boolean> = session.map { it <= 1 }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            mutex.withLock {
                val now = clock.nowMillis()
                var number = 0
                store.edit { p ->
                    val count = p[COUNT] ?: 0
                    val last = p[LAST_ACTIVE]
                    number = if (count == 0 || last == null || now - last >= SESSION_GAP_MS) count + 1 else count
                    p[COUNT] = number
                    p[LAST_ACTIVE] = now
                }
                _session.value = number
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        scope.launch { mutex.withLock { store.edit { it[LAST_ACTIVE] = clock.nowMillis() } } }
    }

    companion object {
        const val SESSION_GAP_MS = 30 * 60_000L
        private val COUNT = intPreferencesKey("session_count")
        private val LAST_ACTIVE = longPreferencesKey("session_last_active")
    }
}

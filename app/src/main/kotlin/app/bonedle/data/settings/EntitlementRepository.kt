package app.bonedle.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What the player has unlocked. Bonedle Plus (one-time purchase: no ads, full archive,
 * unlimited free hints) is granted by Play Billing, which also restores it after a reinstall;
 * this cache lets the app honour it offline and before billing connects.
 */
@Singleton
class EntitlementRepository @Inject constructor(private val store: DataStore<Preferences>) {
    val plus: Flow<Boolean> = store.data.map { it[PLUS] ?: false }

    /** Called by billing whenever the purchase state is confirmed. */
    suspend fun setPlus(owned: Boolean) = store.edit { it[PLUS] = owned }

    private companion object {
        val PLUS = booleanPreferencesKey("plus_owned")
    }
}

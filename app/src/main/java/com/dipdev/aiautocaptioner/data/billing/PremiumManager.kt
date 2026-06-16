package com.dipdev.aiautocaptioner.data.billing

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PremiumManager @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val PREMIUM_UNLOCKED_KEY = booleanPreferencesKey("premium_unlocked_one_time")

    val isPremiumFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[PREMIUM_UNLOCKED_KEY] ?: false
    }.distinctUntilChanged()

    suspend fun unlockPremium() {
        dataStore.edit { prefs ->
            prefs[PREMIUM_UNLOCKED_KEY] = true
        }
    }

    suspend fun resetPremiumForTesting() {
        dataStore.edit { prefs ->
            prefs[PREMIUM_UNLOCKED_KEY] = false
        }
    }
}

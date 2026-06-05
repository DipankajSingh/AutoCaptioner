package com.dipdev.aiautocaptioner.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTheme(val displayName: String) {
    EMERALD("Emerald Gradient"),
    FLAT_GREEN("Flat Green"),
    PURPLE("Purple Neon"),
    BLUE("Ocean Blue")
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val THEME_KEY = stringPreferencesKey("primary_color_theme")
    private val GLASSMORPHISM_KEY = booleanPreferencesKey("glassmorphism_enabled")

    val themeFlow: Flow<AppTheme> = dataStore.data.map { prefs ->
        val themeName = prefs[THEME_KEY] ?: AppTheme.EMERALD.name
        try {
            AppTheme.valueOf(themeName)
        } catch (e: Exception) {
            AppTheme.EMERALD
        }
    }.distinctUntilChanged()

    val glassmorphismFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GLASSMORPHISM_KEY] ?: true
    }.distinctUntilChanged()

    suspend fun setTheme(theme: AppTheme) {
        dataStore.edit { prefs ->
            prefs[THEME_KEY] = theme.name
        }
    }

    suspend fun setGlassmorphismEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[GLASSMORPHISM_KEY] = enabled
        }
    }
}

package com.dipdev.aiautocaptioner.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTheme(val displayName: String) {
    DEEP_SPACE("Deep Space"),
    TRUE_BLACK("True Black"),
    MATTE_DARK("Matte Dark")
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val THEME_KEY = stringPreferencesKey("primary_color_theme")
    private val GLASSMORPHISM_KEY = booleanPreferencesKey("glassmorphism_enabled")
    private val SHOW_TIMELINE_THUMBNAILS_KEY = booleanPreferencesKey("show_timeline_thumbnails")
    
    private val EXPORT_RESOLUTION_KEY = intPreferencesKey("export_resolution")
    private val EXPORT_FPS_KEY = intPreferencesKey("export_fps")
    private val EXPORT_QUALITY_KEY = intPreferencesKey("export_quality")

    val themeFlow: Flow<AppTheme> = dataStore.data.map { prefs ->
        val themeName = prefs[THEME_KEY] ?: AppTheme.DEEP_SPACE.name
        try {
            AppTheme.valueOf(themeName)
        } catch (_: Exception) {
            AppTheme.DEEP_SPACE
        }
    }.distinctUntilChanged()

    val glassmorphismFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GLASSMORPHISM_KEY] ?: true
    }.distinctUntilChanged()

    val showTimelineThumbnailsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_TIMELINE_THUMBNAILS_KEY] ?: false
    }.distinctUntilChanged()

    val exportResolutionFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[EXPORT_RESOLUTION_KEY] ?: -1
    }

    val exportFpsFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[EXPORT_FPS_KEY] ?: -1
    }

    val exportQualityFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[EXPORT_QUALITY_KEY] ?: 1
    }



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

    suspend fun setShowTimelineThumbnails(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_TIMELINE_THUMBNAILS_KEY] = enabled
        }
    }

    suspend fun saveExportSettings(resolution: Int, fps: Int, quality: Int) {
        dataStore.edit { prefs ->
            prefs[EXPORT_RESOLUTION_KEY] = resolution
            prefs[EXPORT_FPS_KEY] = fps
            prefs[EXPORT_QUALITY_KEY] = quality
        }
    }
}

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
    TRUE_BLACK("True Black")
}

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val THEME_KEY = stringPreferencesKey("primary_color_theme")
    private val GLASSMORPHISM_KEY = booleanPreferencesKey("glassmorphism_enabled")
    private val SHOW_TIMELINE_THUMBNAILS_KEY = booleanPreferencesKey("show_timeline_thumbnails")
    private val TELEMETRY_ENABLED_KEY = booleanPreferencesKey("telemetry_enabled")
    
    private val EXPORT_RESOLUTION_KEY = intPreferencesKey("export_resolution")
    private val EXPORT_FPS_KEY = intPreferencesKey("export_fps")
    private val EXPORT_QUALITY_KEY = intPreferencesKey("export_quality")

    // Last-used transcription language settings
    private val LAST_LANGUAGE_KEY = stringPreferencesKey("last_transcription_language")
    private val LAST_TRANSLATE_KEY = booleanPreferencesKey("last_translate_to_english")

    private val LAST_RECORDING_MODE_KEY = stringPreferencesKey("last_recording_mode")
    private val HAS_SEEN_RECORDER_ONBOARDING_KEY = booleanPreferencesKey("has_seen_recorder_onboarding")

    val themeFlow: Flow<AppTheme> = dataStore.data.map { prefs ->
        val themeName = prefs[THEME_KEY] ?: AppTheme.TRUE_BLACK.name
        try {
            AppTheme.valueOf(themeName)
        } catch (_: Exception) {
            AppTheme.TRUE_BLACK
        }
    }.distinctUntilChanged()

    val glassmorphismFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[GLASSMORPHISM_KEY] ?: true
    }.distinctUntilChanged()

    val showTimelineThumbnailsFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[SHOW_TIMELINE_THUMBNAILS_KEY] ?: true
    }.distinctUntilChanged()

    val telemetryEnabledFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[TELEMETRY_ENABLED_KEY] ?: true // Default to true
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

    val lastRecordingModeFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_RECORDING_MODE_KEY] ?: "CAMERA"
    }

    val hasSeenRecorderOnboardingFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[HAS_SEEN_RECORDER_ONBOARDING_KEY] ?: false
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

    suspend fun setShowTimelineThumbnails(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[SHOW_TIMELINE_THUMBNAILS_KEY] = enabled
        }
    }

    suspend fun setTelemetryEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[TELEMETRY_ENABLED_KEY] = enabled
        }
    }

    suspend fun saveExportSettings(resolution: Int, fps: Int, quality: Int) {
        dataStore.edit { prefs ->
            prefs[EXPORT_RESOLUTION_KEY] = resolution
            prefs[EXPORT_FPS_KEY] = fps
            prefs[EXPORT_QUALITY_KEY] = quality
        }
    }

    val lastLanguageFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_LANGUAGE_KEY] ?: "en"
    }.distinctUntilChanged()

    val lastTranslateFlow: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[LAST_TRANSLATE_KEY] ?: false
    }.distinctUntilChanged()

    suspend fun saveLastLanguageSettings(language: String, translateToEnglish: Boolean) {
        dataStore.edit { prefs ->
            prefs[LAST_LANGUAGE_KEY] = language
            prefs[LAST_TRANSLATE_KEY] = translateToEnglish
        }
    }

    suspend fun setLastRecordingMode(mode: String) {
        dataStore.edit { prefs ->
            prefs[LAST_RECORDING_MODE_KEY] = mode
        }
    }

    suspend fun setHasSeenRecorderOnboarding() {
        dataStore.edit { prefs ->
            prefs[HAS_SEEN_RECORDER_ONBOARDING_KEY] = true
        }
    }
}

package com.dipdev.aiautocaptioner.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.dipdev.aiautocaptioner.engine.effects.CreatorFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
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
    private val LAST_ASPECT_RATIO_KEY = stringPreferencesKey("last_aspect_ratio")
    private val LAST_RECORDING_QUALITY_KEY = stringPreferencesKey("last_recording_quality")

    // Preview rendering FPS — lower values reduce battery usage and heat
    private val PREVIEW_FPS_KEY = intPreferencesKey("preview_fps")

    // Creator camera studio settings
    private val SELECTED_CREATOR_FILTER_KEY = stringPreferencesKey("selected_creator_filter_name")
    private val SKIN_SMOOTHNESS_INTENSITY_KEY = floatPreferencesKey("skin_smoothness_intensity_float")

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

    val lastAspectRatioFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_ASPECT_RATIO_KEY] ?: "PORTRAIT_9_16"
    }.distinctUntilChanged()

    val lastRecordingQualityFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[LAST_RECORDING_QUALITY_KEY] ?: "MEDIUM"
    }.distinctUntilChanged()

    /** Preview FPS: 30 (default, saves battery) or 60 (smooth but more power). */
    val previewFpsFlow: Flow<Int> = dataStore.data.map { prefs ->
        prefs[PREVIEW_FPS_KEY] ?: 30
    }.distinctUntilChanged()

    suspend fun setPreviewFps(fps: Int) {
        dataStore.edit { prefs ->
            prefs[PREVIEW_FPS_KEY] = fps
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

    suspend fun setLastAspectRatio(aspectRatio: String) {
        dataStore.edit { prefs ->
            prefs[LAST_ASPECT_RATIO_KEY] = aspectRatio
        }
    }

    suspend fun setLastRecordingQuality(quality: String) {
        dataStore.edit { prefs ->
            prefs[LAST_RECORDING_QUALITY_KEY] = quality
        }
    }

    val selectedCreatorFilterFlow: Flow<CreatorFilter> = dataStore.data.map { prefs ->
        CreatorFilter.fromName(prefs[SELECTED_CREATOR_FILTER_KEY])
    }.distinctUntilChanged()

    val skinSmoothnessIntensityFlow: Flow<Float> = dataStore.data.map { prefs ->
        prefs[SKIN_SMOOTHNESS_INTENSITY_KEY] ?: 0.35f
    }.distinctUntilChanged()

    suspend fun setCreatorFilter(filter: CreatorFilter) {
        dataStore.edit { prefs ->
            prefs[SELECTED_CREATOR_FILTER_KEY] = filter.name
        }
    }

    suspend fun setSkinSmoothnessIntensity(intensity: Float) {
        dataStore.edit { prefs ->
            prefs[SKIN_SMOOTHNESS_INTENSITY_KEY] = intensity
        }
    }
}

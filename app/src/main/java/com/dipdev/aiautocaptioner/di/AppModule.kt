package com.dipdev.aiautocaptioner.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.core.logging.FirebaseCrashReporter
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine
import com.dipdev.aiautocaptioner.ui.recorder.camera.CameraEngine
import com.dipdev.aiautocaptioner.ui.recorder.camera.Camera2Engine
import com.dipdev.aiautocaptioner.ui.recorder.recording.FacelessRecorder
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences>
        by preferencesDataStore(name = "autocaptioner_prefs")

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun bindCrashReporter(
        reporter: FirebaseCrashReporter
    ): CrashReporter

    @Binds
    abstract fun bindCameraEngine(
        engine: Camera2Engine
    ): CameraEngine

    companion object {
        @Provides
        fun provideDataStore(
            @ApplicationContext context: Context
        ): DataStore<Preferences> = context.dataStore

        @Provides
        @Singleton
        fun provideWhisperEngine(
            @ApplicationContext context: Context
        ): WhisperEngine = WhisperEngine(context)

        @Provides
        fun provideCamera2Engine(
            @ApplicationContext context: Context
        ): Camera2Engine = Camera2Engine(context)

        @Provides
        fun provideFacelessRecorder(
            crashReporter: CrashReporter
        ): FacelessRecorder = FacelessRecorder(crashReporter)
    }
}
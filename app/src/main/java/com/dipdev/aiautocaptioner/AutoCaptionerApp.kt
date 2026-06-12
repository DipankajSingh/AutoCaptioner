package com.dipdev.aiautocaptioner

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.perf.performance
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AiAutoCaptioner : Application() {

    override fun onCreate() {
        super.onCreate()
        initFirebase()
    }

    private fun initFirebase() {

        // ── Crashlytics ────────────────────────────────────────────────────
        // Enable crash collection. Set to false for debug builds to avoid
        // polluting the dashboard with development crashes.
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        // ── Analytics ─────────────────────────────────────────────────────
        // Collect anonymized usage data. Disabled in debug builds.
        Firebase.analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)

        // ── Performance Monitoring ─────────────────────────────────────────
        // Collects startup time, screen render performance, network traces.
        Firebase.performance.isPerformanceCollectionEnabled = !BuildConfig.DEBUG

        // ── Remote Config ─────────────────────────────────────────────────
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            // In debug builds, fetch frequently so you can test config changes fast.
            // In release builds, Firebase uses its own fetch throttle (~12 hours).
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

        // Fetch and activate in the background. New values take effect on next launch.
        remoteConfig.fetchAndActivate()
    }
}
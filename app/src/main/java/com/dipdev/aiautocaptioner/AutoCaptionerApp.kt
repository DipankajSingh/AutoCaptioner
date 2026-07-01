package com.dipdev.aiautocaptioner

import android.app.Application
import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.crashlytics.crashlytics
import com.google.firebase.perf.performance
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.HiltAndroidApp
import com.revenuecat.purchases.LogLevel
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.dipdev.aiautocaptioner.data.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class AiAutoCaptioner : Application() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        initFirebase()
    }

    private fun initRevenueCat() {
        val apiKey = Firebase.remoteConfig.getString("revenuecat_api_key")
        if (apiKey != "disabled" && apiKey.isNotBlank()) {
            if (BuildConfig.DEBUG) {
                Purchases.logLevel = LogLevel.DEBUG
            }
            Purchases.configure(
                PurchasesConfiguration.Builder(this, apiKey)
                    .build()
            )
        }
    }

    private fun initFirebase() {

        // ── Crashlytics ────────────────────────────────────────────────────
        // Initially set based on debug flag, then observed from settings
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = !BuildConfig.DEBUG

        // ── Analytics ─────────────────────────────────────────────────────
        // Collect anonymized usage data. Disabled in debug builds.
        Firebase.analytics.setAnalyticsCollectionEnabled(!BuildConfig.DEBUG)

        // ── Performance Monitoring ─────────────────────────────────────────
        // Collects startup time, screen render performance, network traces.
        Firebase.performance.isPerformanceCollectionEnabled = !BuildConfig.DEBUG

        // Observe user settings to override default behavior
        applicationScope.launch {
            settingsRepository.telemetryEnabledFlow.collect { isEnabled ->
                // Do not enable telemetry in debug builds even if user toggled it
                val shouldEnable = isEnabled && !BuildConfig.DEBUG
                Firebase.crashlytics.isCrashlyticsCollectionEnabled = shouldEnable
                Firebase.analytics.setAnalyticsCollectionEnabled(shouldEnable)
                Firebase.performance.isPerformanceCollectionEnabled = shouldEnable
            }
        }

        // ── Remote Config ─────────────────────────────────────────────────
        val remoteConfig = Firebase.remoteConfig
        val configSettings = remoteConfigSettings {
            // In debug builds, fetch frequently so you can test config changes fast.
            // In release builds, Firebase uses its own fetch throttle (~12 hours).
            minimumFetchIntervalInSeconds = if (BuildConfig.DEBUG) 0 else 3600
        }
        remoteConfig.setConfigSettingsAsync(configSettings)
        remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults).addOnCompleteListener {
            initRevenueCat()
        }

        // Fetch and activate in the background. New values take effect on next launch.
        remoteConfig.fetchAndActivate()
    }
}
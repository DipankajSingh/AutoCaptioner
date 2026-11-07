package com.dipdev.aiautocaptioner.core.logging

import com.dipdev.aiautocaptioner.BuildConfig
import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    override fun recordException(e: Throwable) {
        if (BuildConfig.DEBUG) {
            throw RuntimeException("Crashlytics caught an exception in DEBUG mode", e)
        } else {
            FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    override fun setKey(key: String, value: String) {
        try {
            FirebaseCrashlytics.getInstance().setCustomKey(key, value)
        } catch (_: Exception) {}
    }

    override fun logNonFatal(message: String) {
        if (!BuildConfig.DEBUG) {
            try {
                FirebaseCrashlytics.getInstance().recordException(Exception("NonFatal: $message"))
            } catch (_: Exception) {}
        }
    }
}

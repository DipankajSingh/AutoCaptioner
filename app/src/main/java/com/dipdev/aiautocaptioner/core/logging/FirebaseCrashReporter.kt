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
}

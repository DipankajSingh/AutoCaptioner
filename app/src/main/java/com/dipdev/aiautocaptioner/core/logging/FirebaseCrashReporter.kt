package com.dipdev.aiautocaptioner.core.logging

import com.google.firebase.crashlytics.FirebaseCrashlytics
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseCrashReporter @Inject constructor() : CrashReporter {
    override fun recordException(e: Throwable) {
        FirebaseCrashlytics.getInstance().recordException(e)
    }
}

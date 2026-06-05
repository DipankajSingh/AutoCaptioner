package com.dipdev.aiautocaptioner.core.logging

interface CrashReporter {
    fun recordException(e: Throwable)
}

package com.dipdev.aiautocaptioner.core.logging

interface CrashReporter {
    fun recordException(e: Throwable)
    fun setKey(key: String, value: String)
    fun logNonFatal(message: String)
}

package com.dipdev.aiautocaptioner.core.extensions

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

fun <T> Flow<T>.stateInDefault(scope: CoroutineScope, initialValue: T): StateFlow<T> {
    return this.stateIn(
        scope = scope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = initialValue
    )
}

package com.dipdev.aiautocaptioner.data.model

import java.util.UUID

data class Clip(
    val id: String = UUID.randomUUID().toString(),
    val startTrimMs: Long,
    val endTrimMs: Long
)

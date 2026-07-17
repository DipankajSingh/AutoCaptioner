package com.dipdev.aiautocaptioner.engine

import androidx.media3.effect.StaticOverlaySettings

fun main() {
    val builder = StaticOverlaySettings.Builder()
    for (method in builder.javaClass.methods) {
        println(method.name)
    }
}

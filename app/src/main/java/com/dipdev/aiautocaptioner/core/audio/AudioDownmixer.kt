package com.dipdev.aiautocaptioner.core.audio

/**
 * Utility for downmixing multi-channel PCM audio to mono.
 * Implements E4.1 correlation-aware downmixing for stereo audio:
 * - If stereo channels have non-negative correlation (in-phase or independent), arithmetic mean is used to improve SNR.
 * - If stereo channels are negatively correlated (antiphase), the louder channel is selected to avoid phase cancellation.
 * - Multi-channel (> 2 channels) falls back to arithmetic mean across channels.
 */
object AudioDownmixer {

    fun downmix(interleavedSamples: FloatArray, channelCount: Int): FloatArray {
        if (channelCount <= 1 || interleavedSamples.isEmpty()) return interleavedSamples

        if (channelCount == 2) {
            val halfLen = interleavedSamples.size / 2
            var crossCorr = 0.0
            var energyL = 0.0
            var energyR = 0.0

            for (i in 0 until halfLen) {
                val l = interleavedSamples[i * 2].toDouble()
                val r = interleavedSamples[i * 2 + 1].toDouble()
                crossCorr += l * r
                energyL += l * l
                energyR += r * r
            }

            return if (crossCorr >= 0.0) {
                // In-phase or uncorrelated: arithmetic mean improves SNR
                FloatArray(halfLen) { i ->
                    (interleavedSamples[i * 2] + interleavedSamples[i * 2 + 1]) / 2f
                }
            } else {
                // Antiphase / negative correlation: pick louder channel to prevent destructive interference
                if (energyL >= energyR) {
                    FloatArray(halfLen) { i -> interleavedSamples[i * 2] }
                } else {
                    FloatArray(halfLen) { i -> interleavedSamples[i * 2 + 1] }
                }
            }
        }

        // Multichannel (> 2 channels): simple mean across all channels
        val frameCount = interleavedSamples.size / channelCount
        return FloatArray(frameCount) { i ->
            var sum = 0f
            val base = i * channelCount
            for (ch in 0 until channelCount) {
                sum += interleavedSamples[base + ch]
            }
            sum / channelCount
        }
    }
}

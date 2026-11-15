package com.dipdev.aiautocaptioner.core.audio

/**
 * Audio resampler interface for converting audio streams to 16 kHz mono PCM.
 */
interface AudioResampler {
    /**
     * Feeds a chunk of mono samples at source sample rate.
     * Returns resampled 16 kHz mono samples.
     */
    fun process(input: FloatArray): FloatArray

    /**
     * Flushes any remaining samples at the end of the audio stream.
     */
    fun flush(): FloatArray

    companion object {
        fun create(fromRate: Int, toRate: Int = 16000, useResamplerV2: Boolean = false): AudioResampler {
            if (fromRate == toRate) {
                return PassthroughResampler()
            }
            return if (useResamplerV2) {
                BandLimitedResampler(fromRate, toRate)
            } else {
                StreamingLinearResampler(fromRate, toRate)
            }
        }
    }
}

/**
 * Passthrough resampler for audio already at target sample rate.
 */
class PassthroughResampler : AudioResampler {
    override fun process(input: FloatArray): FloatArray = input
    override fun flush(): FloatArray = FloatArray(0)
}

/**
 * Streaming linear interpolation resampler.
 * Retains sub-sample position and boundary samples across chunk calls
 * to prevent phase distortion and boundary clicks.
 */
class StreamingLinearResampler(
    private val fromRate: Int,
    private val toRate: Int = 16000
) : AudioResampler {

    private val ratio: Double = fromRate.toDouble() / toRate.toDouble()
    private var buffer = FloatArray(0)
    private var nextPos: Double = 0.0
    private var totalInputSamples: Long = 0L
    private var totalOutputSamples: Long = 0L

    override fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        totalInputSamples += input.size

        val working = if (buffer.isEmpty()) {
            input
        } else {
            FloatArray(buffer.size + input.size).apply {
                buffer.copyInto(this, 0)
                input.copyInto(this, buffer.size)
            }
        }

        var pos = nextPos
        val outList = ArrayList<Float>()

        while (pos + 1.0 < working.size) {
            val i0 = pos.toInt()
            val frac = (pos - i0).toFloat()
            val s0 = working[i0]
            val s1 = working[i0 + 1]
            outList.add(s0 + (s1 - s0) * frac)
            pos += ratio
        }

        val k = pos.toInt()
        if (k < working.size) {
            buffer = working.copyOfRange(k, working.size)
            nextPos = pos - k
        } else {
            buffer = FloatArray(0)
            nextPos = pos - working.size
        }

        totalOutputSamples += outList.size
        val result = FloatArray(outList.size)
        for (i in outList.indices) {
            result[i] = outList[i]
        }
        return result
    }

    override fun flush(): FloatArray {
        val expectedTotal = (totalInputSamples / ratio).toInt()
        val remaining = (expectedTotal - totalOutputSamples).toInt().coerceAtLeast(0)
        if (remaining == 0) return FloatArray(0)

        val flushSamples = FloatArray(remaining)
        val lastVal = if (buffer.isNotEmpty()) buffer.last() else 0f
        for (i in 0 until remaining) {
            flushSamples[i] = lastVal
        }
        totalOutputSamples += remaining
        buffer = FloatArray(0)
        return flushSamples
    }
}

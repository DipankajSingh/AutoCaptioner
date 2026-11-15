package com.dipdev.aiautocaptioner.core.audio

import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Band-limited polyphase sinc resampler using a Kaiser window.
 * 
 * Replaces linear interpolation to eliminate high-frequency aliasing
 * when downsampling (e.g. 48 kHz or 44.1 kHz to 16 kHz).
 *
 * Characteristics:
 * - Cutoff: ~7.6 kHz for 16 kHz output
 * - Stopband attenuation: >= 80 dB (Kaiser beta = 8.6)
 * - Number of polyphase phases: 64 with linear inter-phase interpolation
 * - Filter half-length: 16 taps (total 33 taps per phase)
 * - Zero phase delay streaming architecture with carryover buffer
 */
class BandLimitedResampler(
    private val fromRate: Int,
    private val toRate: Int = 16000,
    private val halfLength: Int = 16,
    private val numPhases: Int = 64
) : AudioResampler {

    private val ratio: Double = fromRate.toDouble() / toRate.toDouble()
    private val filterTaps = 2 * halfLength + 1
    private val polyphaseTable: Array<FloatArray>

    // Buffer starts with halfLength zeros for causal boundary at t=0
    private var buffer = FloatArray(halfLength)
    private var bufferStartGlobalSample: Long = -halfLength.toLong()
    private var totalInputSamples: Long = 0L
    private var totalOutputSamples: Long = 0L

    init {
        val nyquist = toRate / 2.0
        val targetCutoff = if (fromRate > toRate) {
            (nyquist - 400.0).coerceAtLeast(1000.0)
        } else {
            (fromRate / 2.0 - 400.0).coerceAtLeast(1000.0)
        }
        val normalizedCutoff = targetCutoff / fromRate.toDouble()
        val beta = 8.6 // ~80 dB stopband attenuation

        polyphaseTable = Array(numPhases) { phase ->
            val phaseFraction = phase.toDouble() / numPhases.toDouble()
            val taps = FloatArray(filterTaps)
            var sum = 0.0

            for (j in 0 until filterTaps) {
                val n = j - halfLength
                val t = n.toDouble() - phaseFraction

                val sincVal = sinc(2.0 * normalizedCutoff * t)
                val windowVal = kaiserWindow(t, halfLength.toDouble(), beta)
                val h = 2.0 * normalizedCutoff * sincVal * windowVal
                taps[j] = h.toFloat()
                sum += h
            }

            if (sum != 0.0) {
                val invSum = (1.0 / sum).toFloat()
                for (j in 0 until filterTaps) {
                    taps[j] *= invSum
                }
            }
            taps
        }
    }

    override fun process(input: FloatArray): FloatArray {
        if (input.isEmpty()) return FloatArray(0)
        totalInputSamples += input.size

        val working = FloatArray(buffer.size + input.size).apply {
            buffer.copyInto(this, 0)
            input.copyInto(this, buffer.size)
        }

        val outList = ArrayList<Float>()
        var outIdx = totalOutputSamples

        while (true) {
            // Absolute continuous position in input stream
            val globalPos = outIdx * ratio
            val localPos = globalPos - bufferStartGlobalSample

            if (localPos.toInt() + halfLength >= working.size) {
                break
            }

            val i0 = localPos.toInt()
            val startIdx = i0 - halfLength
            val frac = localPos - i0
            val phaseFloat = frac * numPhases
            val phase = phaseFloat.toInt().coerceIn(0, numPhases - 1)
            val phaseFrac = (phaseFloat - phase).toFloat()
            val taps0 = polyphaseTable[phase]
            val taps1 = if (phase + 1 < numPhases) polyphaseTable[phase + 1] else polyphaseTable[phase]

            var acc0 = 0f
            var acc1 = 0f
            for (j in 0 until filterTaps) {
                val sample = working[startIdx + j]
                acc0 += sample * taps0[j]
                acc1 += sample * taps1[j]
            }
            outList.add(acc0 + (acc1 - acc0) * phaseFrac)
            outIdx++
        }

        // Keep necessary trailing samples for next chunk
        val nextGlobalPos = outIdx * ratio
        val nextLocalPos = nextGlobalPos - bufferStartGlobalSample
        val keepStart = (nextLocalPos.toInt() - halfLength).coerceAtLeast(0)

        if (keepStart < working.size) {
            buffer = working.copyOfRange(keepStart, working.size)
            bufferStartGlobalSample += keepStart
        } else {
            buffer = FloatArray(0)
            bufferStartGlobalSample += working.size
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

        val padSize = 2 * halfLength + 2
        val padded = FloatArray(buffer.size + padSize) { i ->
            if (i < buffer.size) buffer[i] else 0f
        }

        val outList = ArrayList<Float>()
        var outIdx = totalOutputSamples

        while (outList.size < remaining && (outIdx * ratio - bufferStartGlobalSample).toInt() + halfLength < padded.size) {
            val localPos = outIdx * ratio - bufferStartGlobalSample
            val i0 = localPos.toInt()
            val startIdx = (i0 - halfLength).coerceAtLeast(0)
            val frac = localPos - i0
            val phaseFloat = frac * numPhases
            val phase = phaseFloat.toInt().coerceIn(0, numPhases - 1)
            val phaseFrac = (phaseFloat - phase).toFloat()
            val taps0 = polyphaseTable[phase]
            val taps1 = if (phase + 1 < numPhases) polyphaseTable[phase + 1] else polyphaseTable[phase]

            var acc0 = 0f
            var acc1 = 0f
            for (j in 0 until filterTaps) {
                val idx = startIdx + j
                val sample = if (idx < padded.size) padded[idx] else 0f
                acc0 += sample * taps0[j]
                acc1 += sample * taps1[j]
            }
            outList.add(acc0 + (acc1 - acc0) * phaseFrac)
            outIdx++
        }

        while (outList.size < remaining) {
            outList.add(0f)
        }

        totalOutputSamples += outList.size
        buffer = FloatArray(0)
        val result = FloatArray(outList.size)
        for (i in outList.indices) {
            result[i] = outList[i]
        }
        return result
    }

    companion object {
        private fun sinc(x: Double): Double {
            val piX = PI * x
            if (kotlin.math.abs(piX) < 1e-9) return 1.0
            return sin(piX) / piX
        }

        private fun kaiserWindow(t: Double, halfLen: Double, beta: Double): Double {
            val ratio = t / halfLen
            val valSq = 1.0 - ratio * ratio
            if (valSq <= 0.0) return 0.0
            return besselI0(beta * sqrt(valSq)) / besselI0(beta)
        }

        private fun besselI0(x: Double): Double {
            var sum = 1.0
            var term = 1.0
            var k = 1
            val xHalfSq = (x * 0.5) * (x * 0.5)
            while (term > 1e-12 * sum) {
                term *= xHalfSq / (k * k)
                sum += term
                k++
            }
            return sum
        }
    }
}

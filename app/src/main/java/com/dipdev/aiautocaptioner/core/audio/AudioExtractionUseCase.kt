package com.dipdev.aiautocaptioner.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject

class AudioExtractionUseCase @Inject constructor() {

    @Suppress("DEPRECATION")
    suspend fun extractAudioFloatArray(videoPath: String): FloatArray {
        return withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null

            try {
                extractor.setDataSource(videoPath)
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("audio/") == true) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }
                if (audioTrackIndex == -1 || audioFormat == null) {
                    throw IllegalStateException("No audio track found in $videoPath")
                }
                extractor.selectTrack(audioTrackIndex)

                val mime = audioFormat?.getString(MediaFormat.KEY_MIME)!!
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(audioFormat, null, null, 0)
                codec.start()

                val info = MediaCodec.BufferInfo()
                var isEOS = false
                val pcmChunks = mutableListOf<ShortArray>()

                while (true) {
                    if (!isEOS) {
                        val inputBufferId = codec.dequeueInputBuffer(10000)
                        if (inputBufferId >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferId)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inputBufferId, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                isEOS = true
                            } else {
                                codec.queueInputBuffer(inputBufferId, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outputBufferId = codec.dequeueOutputBuffer(info, 10000)
                    if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        audioFormat = codec.outputFormat
                    } else if (outputBufferId >= 0) {
                        if (info.size > 0) {
                            val outputBuffer = codec.getOutputBuffer(outputBufferId)!!
                            val pcmBytes = ByteArray(info.size)
                            outputBuffer.get(pcmBytes)
                            outputBuffer.clear()
                            val shortBuffer = ByteBuffer.wrap(pcmBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                            val chunk = ShortArray(shortBuffer.remaining())
                            shortBuffer.get(chunk)
                            pcmChunks.add(chunk)
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }

                val sampleRate = audioFormat?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 16000
                val channelCount = audioFormat?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 1

                val totalSamples = pcmChunks.sumOf { it.size }
                val pcmShorts = ShortArray(totalSamples)
                var offset = 0
                for (chunk in pcmChunks) {
                    System.arraycopy(chunk, 0, pcmShorts, offset, chunk.size)
                    offset += chunk.size
                }

                val monoShorts: ShortArray = if (channelCount > 1) {
                    ShortArray(totalSamples / channelCount) { i ->
                        var sum = 0
                        for (ch in 0 until channelCount) sum += pcmShorts[i * channelCount + ch]
                        (sum / channelCount).toShort()
                    }
                } else {
                    pcmShorts
                }

                val finalShorts = if (sampleRate != 16000) resampleTo16kLinear(monoShorts, sampleRate)
                                  else monoShorts

                FloatArray(finalShorts.size) { i -> finalShorts[i] / 32768.0f }
            } finally {
                try { codec?.stop() } catch (_: Exception) {}
                try { codec?.release() } catch (_: Exception) {}
                extractor.release()
            }
        }
    }

    private fun resampleTo16kLinear(input: ShortArray, fromRate: Int): ShortArray {
        if (fromRate == 16000) return input
        if (input.size < 2) return input
        val ratio = fromRate.toDouble() / 16000.0
        val outputSize = (input.size / ratio).toInt()
        return ShortArray(outputSize) { i ->
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt().coerceIn(0, input.size - 2)
            val fraction = (srcPos - srcIndex).toFloat()
            val s0 = input[srcIndex].toFloat()
            val s1 = input[srcIndex + 1].toFloat()
            (s0 + (s1 - s0) * fraction).toInt().toShort()
        }
    }
}

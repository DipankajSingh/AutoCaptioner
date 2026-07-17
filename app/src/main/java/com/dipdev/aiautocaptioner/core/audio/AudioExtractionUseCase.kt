package com.dipdev.aiautocaptioner.core.audio

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import javax.inject.Inject
import androidx.core.net.toUri

class AudioExtractionUseCase @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {

    @Suppress("DEPRECATION")
    suspend fun extractAudioFloatArray(videoPath: String): FloatArray {
        return withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null

            try {
                if (videoPath.startsWith("content://") || videoPath.startsWith("file://")) {
                    extractor.setDataSource(context, videoPath.toUri(), null)
                } else {
                    extractor.setDataSource(videoPath)
                }
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

                val mime = audioFormat.getString(MediaFormat.KEY_MIME)
                    ?: throw IllegalStateException("Audio MIME type is null")
                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(audioFormat, null, null, 0)
                codec.start()

                val info = MediaCodec.BufferInfo()
                var isEOS = false
                
                val floatArrays = ArrayList<FloatArray>()
                var totalFloatsWritten = 0
                
                // Hoisted buffers
                var pcmBytes = ByteArray(0)

                while (true) {
                    kotlinx.coroutines.yield()
                    if (!isEOS) {
                        val inputBufferId = codec.dequeueInputBuffer(10000)
                        if (inputBufferId >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputBufferId)
                                ?: throw IllegalStateException("Input buffer is null")
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
                            val outputBuffer = codec.getOutputBuffer(outputBufferId)
                                ?: throw IllegalStateException("Output buffer is null")
                            if (pcmBytes.size < info.size) {
                                pcmBytes = ByteArray(info.size)
                            }
                            outputBuffer.get(pcmBytes, 0, info.size)
                            outputBuffer.clear()
                            
                            val pcmEncoding = if (audioFormat?.containsKey(MediaFormat.KEY_PCM_ENCODING) == true) {
                                audioFormat?.getInteger(MediaFormat.KEY_PCM_ENCODING) ?: android.media.AudioFormat.ENCODING_PCM_16BIT
                            } else {
                                android.media.AudioFormat.ENCODING_PCM_16BIT
                            }

                            val byteBuffer = ByteBuffer.wrap(pcmBytes, 0, info.size).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            val floatArray = when (pcmEncoding) {
                                android.media.AudioFormat.ENCODING_PCM_FLOAT -> {
                                    val fb = byteBuffer.asFloatBuffer()
                                    FloatArray(fb.remaining()).also { fb.get(it) }
                                }
                                android.media.AudioFormat.ENCODING_PCM_8BIT -> {
                                    FloatArray(info.size) { i -> (pcmBytes[i].toInt() - 128) / 128.0f }
                                }
                                else -> {
                                    val sb = byteBuffer.asShortBuffer()
                                    FloatArray(sb.remaining()) { i -> sb.get(i) / 32768.0f }
                                }
                            }

                            val channelCount = audioFormat?.getInteger(MediaFormat.KEY_CHANNEL_COUNT) ?: 1
                            val monoFloats = if (channelCount > 1) {
                                FloatArray(floatArray.size / channelCount) { i ->
                                    var sum = 0f
                                    for (ch in 0 until channelCount) sum += floatArray[i * channelCount + ch]
                                    sum / channelCount
                                }
                            } else {
                                floatArray
                            }
                            
                            floatArrays.add(monoFloats)
                            totalFloatsWritten += monoFloats.size
                        }
                        codec.releaseOutputBuffer(outputBufferId, false)
                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }

                val sampleRate = audioFormat?.getInteger(MediaFormat.KEY_SAMPLE_RATE) ?: 16000
                var finalFloats = FloatArray(totalFloatsWritten)
                var offset = 0
                for (arr in floatArrays) {
                    System.arraycopy(arr, 0, finalFloats, offset, arr.size)
                    offset += arr.size
                }

                if (sampleRate != 16000) {
                    finalFloats = resampleTo16kLinear(finalFloats, sampleRate)
                }

                finalFloats
            } finally {
                try { codec?.stop() } catch (_: Exception) {}
                try { codec?.release() } catch (_: Exception) {}
                extractor.release()
            }
        }
    }

    private fun resampleTo16kLinear(input: FloatArray, fromRate: Int): FloatArray {
        if (fromRate == 16000) return input
        if (input.size < 2) return input
        val ratio = fromRate.toDouble() / 16000.0
        val outputSize = (input.size / ratio).toInt()
        return FloatArray(outputSize) { i ->
            val srcPos = i * ratio
            val srcIndex = srcPos.toInt().coerceIn(0, input.size - 2)
            val fraction = (srcPos - srcIndex).toFloat()
            val s0 = input[srcIndex]
            val s1 = input[srcIndex + 1]
            s0 + (s1 - s0) * fraction
        }
    }
}

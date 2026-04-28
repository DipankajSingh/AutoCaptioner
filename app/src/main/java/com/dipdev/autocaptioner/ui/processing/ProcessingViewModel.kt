package com.dipdev.autocaptioner.ui.processing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.autocaptioner.core.whisper.WhisperEngine
import com.dipdev.autocaptioner.data.db.entity.ProjectStatus
import com.dipdev.autocaptioner.data.repository.CaptionRepository
import com.dipdev.autocaptioner.data.repository.ModelRepository
import com.dipdev.autocaptioner.data.repository.ProjectRepository
import com.dipdev.autocaptioner.data.repository.TranscriptionSegment
import com.dipdev.autocaptioner.data.repository.TranscriptionWord
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import com.dipdev.autocaptioner.core.whisper.WhisperEngine.WordTimestamp
import kotlinx.coroutines.flow.first

import kotlinx.coroutines.Job

sealed class ProcessingStep {
    data object Idle : ProcessingStep()           // before anything starts
    data object Ready : ProcessingStep()          // waiting for user to tap Start
    data object ExtractingAudio : ProcessingStep()
    data class Transcribing(val progress: Float = 0f) : ProcessingStep()
    data object Saving : ProcessingStep()
    data object Done : ProcessingStep()
    data object Cancelled : ProcessingStep()
    data class Error(val message: String) : ProcessingStep()
}

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository,
    private val modelRepository: ModelRepository,
    private val whisperEngine: WhisperEngine
) : ViewModel() {

    private val _step = MutableStateFlow<ProcessingStep>(ProcessingStep.Idle)
    val step: StateFlow<ProcessingStep> = _step.asStateFlow()

    private var activeJob: Job? = null

    /** Move to the Ready state so the screen shows a Start button */
    fun prepareForProject(projectId: String) {
        // If already transcribed, jump straight to Done so we don't redo work
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            if (project?.status == ProjectStatus.TRANSCRIBED || project?.status == ProjectStatus.EXPORTED) {
                _step.value = ProcessingStep.Done
            } else if (_step.value == ProcessingStep.Idle) {
                _step.value = ProcessingStep.Ready
            }
        }
    }

    fun cancel() {
        activeJob?.cancel()
        activeJob = null
        _step.value = ProcessingStep.Cancelled
    }

    fun startProcessing(projectId: String) {
        if (activeJob?.isActive == true) return  // already running
        _step.value = ProcessingStep.ExtractingAudio
        activeJob = viewModelScope.launch {
            try {
                val project = projectRepository.getProjectById(projectId) ?: run {
                    _step.value = ProcessingStep.Error("Project not found")
                    return@launch
                }

                // Skip if already transcribed
                if (project.status == ProjectStatus.TRANSCRIBED ||
                    project.status == ProjectStatus.EXPORTED) {
                    _step.value = ProcessingStep.Done
                    return@launch
                }

                // Step 1 — Extract audio
                _step.value = ProcessingStep.ExtractingAudio
                projectRepository.updateStatus(projectId, ProjectStatus.EXTRACTING_AUDIO)

                val audioFile = File(context.filesDir, "projects/$projectId/audio.wav")
                extractAudio(project.workingVideoPath, audioFile)
                projectRepository.updateAudioPath(projectId, audioFile.absolutePath)


                // Step 2 — Load model if needed
                val activeModelFile = modelRepository.getActiveModel().first()?.let { model ->
                    modelRepository.getModelFile(model.id)
                }

                if (activeModelFile == null || !activeModelFile.exists()) {
                    _step.value = ProcessingStep.Error("No model downloaded")
                    return@launch
                }

                if (!whisperEngine.isReady()) {
                    val success = whisperEngine.initialize(activeModelFile)
                    if (!success) {
                        _step.value = ProcessingStep.Error("Failed to load model")
                        return@launch
                    }
                }
                // Step 3 — Transcribe in 30-second chunks for real progress reporting
                _step.value = ProcessingStep.Transcribing(0f)
                projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBING)

                val pcmSamples = readWavAsPcm(audioFile)

                // Whisper operates at 16kHz, so 30 seconds = 480,000 samples
                val chunkSizeSamples = 16_000 * 30
                val totalChunks = (pcmSamples.size + chunkSizeSamples - 1) / chunkSizeSamples
                val allWords = mutableListOf<com.dipdev.autocaptioner.core.whisper.WhisperEngine.WordTimestamp>()

                for (chunkIndex in 0 until totalChunks) {
                    val chunkStart = chunkIndex * chunkSizeSamples
                    val chunkEnd = minOf(chunkStart + chunkSizeSamples, pcmSamples.size)
                    val chunkSamples = pcmSamples.copyOfRange(chunkStart, chunkEnd)

                    // Time offset for this chunk in milliseconds (to adjust word timestamps)
                    val timeOffsetMs = (chunkStart / 16_000L) * 1000L

                    val chunkWords = whisperEngine.transcribeWithWordTimestamps(
                        samples = chunkSamples,
                        language = "en"
                    )

                    // Offset the timestamps to reflect position in the full audio
                    allWords.addAll(chunkWords.map { word ->
                        word.copy(
                            startTimeMs = word.startTimeMs + timeOffsetMs,
                            endTimeMs = word.endTimeMs + timeOffsetMs
                        )
                    })

                    val progress = (chunkIndex + 1).toFloat() / totalChunks.toFloat()
                    _step.value = ProcessingStep.Transcribing(progress)
                }

                val wordTimestamps = allWords

                if (wordTimestamps.isEmpty()) {
                    _step.value = ProcessingStep.Error("No words transcribed")
                    return@launch
                }

                // Step 4 — Clean and group words into segments
                _step.value = ProcessingStep.Saving
                val mergedTimestamps = mergeContractions(wordTimestamps)
                // Strip trailing punctuation from stored word text so the DB data is
                // always clean — no commas or full stops attached to words.
                val cleanedTimestamps = mergedTimestamps.map { w ->
                    w.copy(word = w.word.trim().trimEnd(',', '.', '!', '?', ';', ':'))
                }.filter { it.word.isNotBlank() }
                val segments = groupWordsIntoSegments(cleanedTimestamps)

                captionRepository.saveTranscription(projectId, segments)
                projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBED)

                _step.value = ProcessingStep.Done

            } catch (e: kotlinx.coroutines.CancellationException) {
                // User cancelled — already set to Cancelled in cancel()
                android.util.Log.i("Processing", "Transcription cancelled")
            } catch (e: Exception) {
                android.util.Log.e("Processing", "Error: ${e.message}", e)
                _step.value = ProcessingStep.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Extract audio track from video using MediaExtractor + MediaMuxer
    // Output: 16kHz mono WAV file ready for Whisper
    private suspend fun extractAudio(videoPath: String, outputFile: File) {
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            extractor.setDataSource(videoPath)

            // Find the audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex == -1) throw Exception("No audio track found in video")

            extractor.selectTrack(audioTrackIndex)

            // Write raw audio to temp file first
            val tempFile = File(outputFile.parent, "audio_raw.tmp")
            val muxer = MediaMuxer(
                tempFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val muxerTrackIndex = muxer.addTrack(audioFormat!!)
            muxer.start()

            val buffer = ByteBuffer.allocate(1024 * 1024)
            val bufferInfo = android.media.MediaCodec.BufferInfo()

            while (true) {
                bufferInfo.offset = 0
                bufferInfo.size = extractor.readSampleData(buffer, 0)
                if (bufferInfo.size < 0) break

                bufferInfo.presentationTimeUs = extractor.sampleTime
                bufferInfo.flags = when {
                    extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0 ->
                        android.media.MediaCodec.BUFFER_FLAG_KEY_FRAME
                    else -> 0
                }
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }

            muxer.stop()
            muxer.release()
            extractor.release()

            // Convert to 16kHz mono WAV using MediaCodec decoder
            // For now use the extracted audio directly
            // TODO: resample to 16kHz if needed
            tempFile.renameTo(outputFile)
        }
    }

    // Read WAV/AAC file and return float PCM samples for Whisper
    // Key design: we avoid boxing (mutableListOf<Short>) which inflates RAM by 14x.
    // Instead we do a two-pass decode: first pass counts samples, second fills a
    // pre-allocated primitive ShortArray.
    private suspend fun readWavAsPcm(audioFile: File): FloatArray {
        return withContext(Dispatchers.IO) {

            fun buildExtractorAndCodec(): Triple<MediaExtractor, android.media.MediaCodec, MediaFormat> {
                val extractor = MediaExtractor()
                extractor.setDataSource(audioFile.absolutePath)
                var audioTrackIndex = -1
                var audioFormat: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        audioFormat = format
                        break
                    }
                }
                if (audioTrackIndex == -1 || audioFormat == null) {
                    extractor.release()
                    throw Exception("No audio track found in extracted file")
                }
                extractor.selectTrack(audioTrackIndex)
                val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
                val codec = android.media.MediaCodec.createDecoderByType(mime)
                codec.configure(audioFormat, null, null, 0)
                codec.start()
                return Triple(extractor, codec, audioFormat)
            }

            // --- PASS 1: count total Short samples without storing them ---
            var totalSamples = 0
            run {
                val (extractor, codec, _) = buildExtractorAndCodec()
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val idx = codec.dequeueInputBuffer(10_000L)
                        if (idx >= 0) {
                            val inputBuffer = codec.getInputBuffer(idx)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(idx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(idx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIdx >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outIdx)!!
                        totalSamples += outputBuffer.remaining() / 2
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                codec.stop(); codec.release(); extractor.release()
            }

            // --- PASS 2: fill pre-allocated primitive ShortArray ---
            val rawSamples = ShortArray(totalSamples)
            var writePos = 0
            var audioFormat: MediaFormat
            run {
                val (extractor, codec, fmt) = buildExtractorAndCodec()
                audioFormat = fmt
                val bufferInfo = android.media.MediaCodec.BufferInfo()
                var inputDone = false
                var outputDone = false
                while (!outputDone) {
                    if (!inputDone) {
                        val idx = codec.dequeueInputBuffer(10_000L)
                        if (idx >= 0) {
                            val inputBuffer = codec.getInputBuffer(idx)!!
                            val sampleSize = extractor.readSampleData(inputBuffer, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(idx, 0, 0, 0, android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(idx, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }
                    val outIdx = codec.dequeueOutputBuffer(bufferInfo, 10_000L)
                    if (outIdx >= 0) {
                        val outputBuffer = codec.getOutputBuffer(outIdx)!!
                        outputBuffer.order(ByteOrder.LITTLE_ENDIAN)
                        while (outputBuffer.remaining() >= 2 && writePos < totalSamples) {
                            rawSamples[writePos++] = outputBuffer.short
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
                codec.stop(); codec.release(); extractor.release()
            }

            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            // Mix down to mono in-place without creating an intermediate boxed list
            val monoSamples = if (channelCount > 1) {
                ShortArray(writePos / channelCount) { i ->
                    var sum = 0
                    for (ch in 0 until channelCount) sum += rawSamples[i * channelCount + ch]
                    (sum / channelCount).toShort()
                }
            } else {
                rawSamples.copyOf(writePos)
            }

            // Resample to 16 kHz if the source is at a higher rate
            val finalSamples = if (sampleRate != 16000) resampleTo16k(monoSamples, sampleRate) else monoSamples

            // Normalise to float [-1.0, 1.0] — Whisper's expected input range
            FloatArray(finalSamples.size) { i -> finalSamples[i] / 32768.0f }
        }
    }
    // Simple linear interpolation resampler
    private fun resampleTo16k(input: ShortArray, fromRate: Int): ShortArray {
        if (fromRate == 16000) return input
        val ratio = fromRate.toDouble() / 16000.0
        val outputSize = (input.size / ratio).toInt()
        return ShortArray(outputSize) { i ->
            val srcIndex = (i * ratio).toInt().coerceIn(0, input.size - 1)
            input[srcIndex]
        }
    }

    private fun groupWordsIntoSegments(words: List<WordTimestamp>): List<TranscriptionSegment> {
        if (words.isEmpty()) return emptyList()
        val segments = mutableListOf<TranscriptionSegment>()
        var currentWords = mutableListOf<WordTimestamp>()

        for (i in words.indices) {
            val word = words[i]
            currentWords.add(word)
            val isLastWord = i == words.size - 1
            val nextWord = if (!isLastWord) words[i + 1] else null
            val gapToNext = if (nextWord != null) nextWord.startTimeMs - word.endTimeMs else Long.MAX_VALUE
            val shouldSplit = gapToNext > 1000 || currentWords.size >= 8 || isLastWord
            if (shouldSplit && currentWords.isNotEmpty()) {
                segments.add(
                    TranscriptionSegment(
                        startTimeMs = currentWords.first().startTimeMs,
                        endTimeMs = currentWords.last().endTimeMs,
                        words = currentWords.map { w ->
                            TranscriptionWord(
                                word = w.word,
                                startTimeMs = w.startTimeMs,
                                endTimeMs = w.endTimeMs,
                                confidence = w.confidence
                            )
                        }
                    )
                )
                currentWords = mutableListOf()
            }
        }
        return segments
    }

    /**
     * Whisper often tokenizes contractions as two separate words:
     *   "it" + "'s"  →  "it's"
     *   "don" + "'t" →  "don't"
     *   "I" + "'m"   →  "I'm"
     *
     * Merges any word starting with an apostrophe into the previous word,
     * preserving the base word's start time and the suffix's end time.
     */
    private fun mergeContractions(
        words: List<com.dipdev.autocaptioner.core.whisper.WhisperEngine.WordTimestamp>
    ): List<com.dipdev.autocaptioner.core.whisper.WhisperEngine.WordTimestamp> {
        if (words.isEmpty()) return words
        val result = mutableListOf<com.dipdev.autocaptioner.core.whisper.WhisperEngine.WordTimestamp>()
        for (word in words) {
            val trimmed = word.word.trim()
            if (trimmed.startsWith("'") && result.isNotEmpty()) {
                val prev = result.removeLast()
                result.add(prev.copy(word = prev.word.trimEnd() + trimmed, endTimeMs = word.endTimeMs))
            } else {
                result.add(word)
            }
        }
        return result
    }
}
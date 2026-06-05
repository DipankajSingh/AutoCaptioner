package com.dipdev.aiautocaptioner.ui.processing

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.TranscriptionSegment
import com.dipdev.aiautocaptioner.data.repository.TranscriptionWord
import dagger.hilt.android.lifecycle.HiltViewModel
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import dagger.hilt.android.qualifiers.ApplicationContext
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine.WordTimestamp

sealed class ProcessingStep {
    data object Idle : ProcessingStep()
    data object Ready : ProcessingStep()
    data object ExtractingAudio : ProcessingStep()
    data object LoadingModel : ProcessingStep()
    data class Transcribing(val progress: Float = 0f) : ProcessingStep()
    data object Saving : ProcessingStep()
    data object Done : ProcessingStep()
    data object Cancelling : ProcessingStep()
    data object Cancelled : ProcessingStep()
    data class Error(val message: String) : ProcessingStep()
}

@HiltViewModel
class ProcessingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: ProjectRepository,
    private val captionRepository: CaptionRepository,
    private val modelRepository: ModelRepository,
    private val whisperEngine: WhisperEngine,
    private val crashReporter: CrashReporter
) : ViewModel() {

    private val _step = MutableStateFlow<ProcessingStep>(ProcessingStep.Idle)
    val step: StateFlow<ProcessingStep> = _step.asStateFlow()

    // The language currently selected in the UI — bound to the picker
    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _workingVideoPath = MutableStateFlow<String?>(null)
    val workingVideoPath: StateFlow<String?> = _workingVideoPath.asStateFlow()

    /** The currently active Whisper model — used by the UI to check isMultilingual. */
    val activeModel: StateFlow<WhisperModel?> = modelRepository.getActiveModel()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private var activeJob: Job? = null
    @Volatile private var isCancelled = false

    /** Move to the Ready state so the screen shows a Start button */
    fun prepareForProject(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _workingVideoPath.value = project?.workingVideoPath
            // Restore the language saved from last transcription
            _selectedLanguage.value = project?.transcriptionLanguage ?: "en"

            if (project?.status == ProjectStatus.TRANSCRIBED || project?.status == ProjectStatus.EXPORTED) {
                _step.value = ProcessingStep.Done
            } else if (_step.value == ProcessingStep.Idle) {
                _step.value = ProcessingStep.Ready
            }
        }
    }

    fun selectLanguage(language: String) {
        _selectedLanguage.value = language
    }

    fun cancel() {
        isCancelled = true          // Set BEFORE cancelling the job to close the race window
        _step.value = ProcessingStep.Cancelling
        activeJob?.cancel()
        activeJob = null
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.NonCancellable) {
            whisperEngine.release()
            _step.value = ProcessingStep.Cancelled
        }
    }

    fun startProcessing(projectId: String) {
        if (activeJob?.isActive == true) return
        isCancelled = false         // Reset flag for each fresh processing run
        _step.value = ProcessingStep.ExtractingAudio
        val language = _selectedLanguage.value

        activeJob = viewModelScope.launch {
            try {
                val project = projectRepository.getProjectById(projectId) ?: run {
                    _step.value = ProcessingStep.Error("Project not found")
                    return@launch
                }

                if (project.status == ProjectStatus.TRANSCRIBED ||
                    project.status == ProjectStatus.EXPORTED) {
                    _step.value = ProcessingStep.Done
                    return@launch
                }

                // Step 1 — Extract compressed audio track to a proper .m4a file
                _step.value = ProcessingStep.ExtractingAudio
                projectRepository.updateStatus(projectId, ProjectStatus.EXTRACTING_AUDIO)

                // Named .m4a because MediaMuxer writes MPEG-4 container (not raw WAV)
                val audioFile = File(context.filesDir, "projects/$projectId/audio.m4a")
                extractAudio(project.workingVideoPath, audioFile)
                projectRepository.updateAudioPath(projectId, audioFile.absolutePath)

                // Step 2 — Persist selected language before transcription
                projectRepository.updateProject(
                    project.copy(transcriptionLanguage = language)
                )

                // Step 3 — Load model if needed
                _step.value = ProcessingStep.LoadingModel
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

                // Step 4 — Transcribe using full audio with JNI progress callback
                _step.value = ProcessingStep.Transcribing(0f)
                projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBING)

                val pcmSamples = readAudioAsPcm(audioFile)

                val allWords = whisperEngine.transcribeWithWordTimestamps(
                    samples = pcmSamples,
                    language = language,
                    onProgress = { percent ->
                        _step.value = ProcessingStep.Transcribing(percent / 100f)
                    }
                )

                // Guard: if cancel() fired while JNI was running, discard the result
                if (isCancelled) return@launch

                if (allWords.isEmpty()) {
                    _step.value = ProcessingStep.Error("No words transcribed")
                    whisperEngine.release()
                    return@launch
                }

                // Step 5 — Clean and group words into segments
                _step.value = ProcessingStep.Saving
                val mergedTimestamps = mergeContractions(allWords)
                val cleanedTimestamps = mergedTimestamps.map { w ->
                    w.copy(word = w.word.trim().trimEnd(',', '.', '!', '?', ';', ':'))
                }.filter { it.word.isNotBlank() }
                val segments = groupWordsIntoSegments(cleanedTimestamps)

                captionRepository.saveTranscription(projectId, segments)
                projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBED)

                whisperEngine.release()
                _step.value = ProcessingStep.Done

            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.i("Processing", "Transcription cancelled")
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Processing", "Error: ${e.message}", e)
                crashReporter.recordException(e)
                whisperEngine.release()
                _step.value = ProcessingStep.Error(e.message ?: "Unknown error")
            }
        }
    }

    // Extract the compressed audio track from the video to a standalone M4A file.
    // The output is still compressed (AAC); we decode it to PCM in readAudioAsPcm().
    private suspend fun extractAudio(videoPath: String, outputFile: File) {
        withContext(Dispatchers.IO) {
            val extractor = MediaExtractor()
            extractor.setDataSource(context, android.net.Uri.parse(videoPath), null)

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

            val tempFile = File(outputFile.parent, "${outputFile.name}.tmp")
            val muxer = MediaMuxer(
                tempFile.absolutePath,
                MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
            )

            val muxerTrackIndex = muxer.addTrack(audioFormat!!)
            var muxerStarted = false
            var samplesWritten = 0

            try {
                muxer.start()
                muxerStarted = true

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
                    samplesWritten++
                    extractor.advance()
                }

                if (samplesWritten > 0) {
                    muxer.stop()
                } else {
                    // Calling stop() on a muxer with zero samples causes MPEG4Writer to crash.
                    // Release without stop and surface a meaningful error instead.
                    muxer.release()
                    throw Exception("Audio track contained no samples")
                }
                muxer.release()
            } catch (e: Exception) {
                // Ensure the muxer is properly torn down on any unexpected failure.
                crashReporter.recordException(e)
                if (muxerStarted && samplesWritten > 0) {
                    try { muxer.stop() } catch (_: Exception) {}
                }
                muxer.release()
                throw e
            } finally {
                extractor.release()
            }

            if (!tempFile.renameTo(outputFile)) {
                throw IOException("Failed to rename audio temp file")
            }
        }
    }

    // Decode the compressed audio file to float32 PCM samples at 16kHz mono.
    // Single-pass implementation: accumulates decoded shorts into a dynamic buffer,
    // then converts to FloatArray — no wasted double-decode.
    private suspend fun readAudioAsPcm(audioFile: File): FloatArray {
        return withContext(Dispatchers.IO) {

            val extractor = MediaExtractor()
            var codec: android.media.MediaCodec? = null
            try {
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
                    throw Exception("No audio track found in extracted file")
                }

                extractor.selectTrack(audioTrackIndex)
                val mime = audioFormat.getString(MediaFormat.KEY_MIME)!!
                codec = android.media.MediaCodec.createDecoderByType(mime)
                codec.configure(audioFormat, null, null, 0)
                codec.start()

                val pcmChunks = ArrayList<ShortArray>()
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
                                codec.queueInputBuffer(idx, 0, 0, 0,
                                    android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM)
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
                        val chunk = ShortArray(outputBuffer.remaining() / 2)
                        outputBuffer.asShortBuffer().get(chunk)
                        pcmChunks.add(chunk)
                        codec.releaseOutputBuffer(outIdx, false)
                        if (bufferInfo.flags and android.media.MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                            outputDone = true
                        }
                    }
                }

                val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                val channelCount = audioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

                // Flatten chunks
                val totalSamples = pcmChunks.sumOf { it.size }
                val pcmShorts = ShortArray(totalSamples)
                var offset = 0
                for (chunk in pcmChunks) {
                    System.arraycopy(chunk, 0, pcmShorts, offset, chunk.size)
                    offset += chunk.size
                }

                // Mix down to mono
                val monoShorts: ShortArray = if (channelCount > 1) {
                    ShortArray(totalSamples / channelCount) { i ->
                        var sum = 0
                        for (ch in 0 until channelCount) sum += pcmShorts[i * channelCount + ch]
                        (sum / channelCount).toShort()
                    }
                } else {
                    pcmShorts
                }

                // Resample to 16kHz using linear interpolation
                val finalShorts = if (sampleRate != 16000) resampleTo16kLinear(monoShorts, sampleRate)
                                  else monoShorts

                // Normalise to float [-1.0, 1.0] — Whisper's expected input range
                FloatArray(finalShorts.size) { i -> finalShorts[i] / 32768.0f }
            } finally {
                try { codec?.stop() } catch (_: Exception) {}
                try { codec?.release() } catch (_: Exception) {}
                extractor.release()
            }
        }
    }

    // Linear interpolation resampler — avoids aliasing artifacts that
    // nearest-neighbor produces at 3:1 ratios (e.g. 48kHz → 16kHz).
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
     * Merges split contractions: "it" + "'s" → "it's"
     */
    private fun mergeContractions(
        words: List<WordTimestamp>
    ): List<WordTimestamp> {
        if (words.isEmpty()) return words
        val result = mutableListOf<WordTimestamp>()
        for (word in words) {
            val trimmed = word.word.trim()
            if (trimmed.startsWith("'") && result.isNotEmpty()) {
                val prev = result.removeAt(result.lastIndex)
                result.add(prev.copy(word = prev.word.trimEnd() + trimmed, endTimeMs = word.endTimeMs))
            } else {
                result.add(word)
            }
        }
        return result
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.NonCancellable) {
            whisperEngine.release()
        }
    }
}
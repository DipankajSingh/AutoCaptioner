package com.dipdev.aiautocaptioner.ui.processing

import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.TranscriptionSegment
import com.dipdev.aiautocaptioner.data.repository.TranscriptionWord
import com.dipdev.aiautocaptioner.service.TranscriptionService
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
import com.dipdev.aiautocaptioner.core.extensions.stateInDefault
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
    data class Transcribing(val progress: Float = 0f, val estimatedSecondsRemaining: Int? = null) : ProcessingStep()
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
    private val crashReporter: CrashReporter,
    private val audioExtractionUseCase: com.dipdev.aiautocaptioner.core.audio.AudioExtractionUseCase
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
    private var transcriptionStartTimeMs: Long = 0L

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
        viewModelScope.launch(kotlinx.coroutines.NonCancellable) {
            whisperEngine.release()
            context.stopService(Intent(context, TranscriptionService::class.java))
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
                TranscriptionService.updateProgress("Extracting audio...")
                ContextCompat.startForegroundService(context, Intent(context, TranscriptionService::class.java))
                projectRepository.updateStatus(projectId, ProjectStatus.EXTRACTING_AUDIO)

                // Step 1 - Now handled directly in memory

                // Step 2 — Persist selected language before transcription
                projectRepository.updateProject(
                    project.copy(transcriptionLanguage = language)
                )

                // Step 3 — Load model if needed
                _step.value = ProcessingStep.LoadingModel
                TranscriptionService.updateProgress("Loading AI model...")
                val activeModelFile = modelRepository.getActiveModel().first()?.let { model ->
                    modelRepository.getModelFile(model.id)
                }

                if (activeModelFile == null || !activeModelFile.exists()) {
                    _step.value = ProcessingStep.Error("No model downloaded")
                    context.stopService(Intent(context, TranscriptionService::class.java))
                    return@launch
                }

                if (!whisperEngine.isReady()) {
                    val success = whisperEngine.initialize(activeModelFile)
                    if (!success) {
                        _step.value = ProcessingStep.Error("Failed to load model")
                        context.stopService(Intent(context, TranscriptionService::class.java))
                        return@launch
                    }
                }

                // Step 4 — Transcribe using full audio with JNI progress callback
                transcriptionStartTimeMs = System.currentTimeMillis()
                _step.value = ProcessingStep.Transcribing(0f)
                projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBING)

                val pcmSamples = audioExtractionUseCase.extractAudioFloatArray(project.workingVideoPath)

                val allWords = whisperEngine.transcribeWithWordTimestamps(
                    samples = pcmSamples,
                    language = language,
                    onProgress = { percent ->
                        val progressFraction = percent / 100f
                        val elapsedMs = System.currentTimeMillis() - transcriptionStartTimeMs
                        val etaSecs: Int? = if (progressFraction > 0.05f) {
                            val estimatedTotalMs = elapsedMs / progressFraction
                            val remainingMs = estimatedTotalMs - elapsedMs
                            (remainingMs / 1000).toInt().coerceAtLeast(1)
                        } else null
                        _step.value = ProcessingStep.Transcribing(progressFraction, etaSecs)
                        TranscriptionService.updateProgress("Transcribing video... ${percent}%")
                    }
                )

                // Guard: if cancel() fired while JNI was running, discard the result
                if (isCancelled) return@launch

                if (allWords.isEmpty()) {
                    _step.value = ProcessingStep.Error("No words transcribed")
                    whisperEngine.release()
                    context.stopService(Intent(context, TranscriptionService::class.java))
                    return@launch
                }

                // Step 5 — Clean and group words into segments
                _step.value = ProcessingStep.Saving
                TranscriptionService.updateProgress("Saving transcription...")
                val mergedTimestamps = mergeContractions(allWords)
                
                // Keep punctuation for the smart grouping pass
                val rawTimestamps = mergedTimestamps.map { w ->
                    w.copy(word = w.word.trim())
                }.filter { it.word.isNotBlank() }
                
                val initialSegments = groupWordsIntoSegments(rawTimestamps)
                
                // Apply smart merge and split
                val smartlyGrouped = mergeAndSplitSegments(initialSegments)
                
                // Now strip the punctuation as originally intended
                val finalSegments = smartlyGrouped.mapNotNull { seg ->
                    val cleanedWords = seg.words.map { w ->
                        w.copy(word = w.word.trimEnd(',', '.', '!', '?', ';', ':'))
                    }.filter { it.word.isNotBlank() }
                    
                    if (cleanedWords.isNotEmpty()) {
                        seg.copy(
                            startTimeMs = cleanedWords.first().startTimeMs,
                            endTimeMs = cleanedWords.last().endTimeMs,
                            words = cleanedWords
                        )
                    } else {
                        null
                    }
                }

                captionRepository.saveTranscription(projectId, finalSegments)
                projectRepository.updateStatus(projectId, ProjectStatus.TRANSCRIBED)

                whisperEngine.release()
                context.stopService(Intent(context, TranscriptionService::class.java))
                _step.value = ProcessingStep.Done

            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.i("Processing", "Transcription cancelled")
                throw e
            } catch (e: Exception) {
                android.util.Log.e("Processing", "Error: ${e.message}", e)
                crashReporter.recordException(e)
                whisperEngine.release()
                context.stopService(Intent(context, TranscriptionService::class.java))
                _step.value = ProcessingStep.Error(e.message ?: "Unknown error")
            }
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
    private fun mergeContractions(words: List<WordTimestamp>): List<WordTimestamp> =
        words.fold(mutableListOf()) { acc, word ->
            val trimmed = word.word.trim()
            if (trimmed.startsWith("'") && acc.isNotEmpty()) {
                val prev = acc.removeAt(acc.lastIndex)
                acc.add(prev.copy(word = prev.word.trimEnd() + trimmed, endTimeMs = word.endTimeMs))
            } else {
                acc.add(word)
            }
            acc
        }

    private fun mergeAndSplitSegments(rawSegments: List<TranscriptionSegment>): List<TranscriptionSegment> {
        if (rawSegments.isEmpty()) return emptyList()

        // 1. Merge segments < 3 words
        val merged = mutableListOf<TranscriptionSegment>()
        var i = 0
        while (i < rawSegments.size) {
            var current = rawSegments[i]
            // Merge forward if < 3 words and not the last one
            while (current.words.size < 3 && i < rawSegments.size - 1) {
                i++
                val next = rawSegments[i]
                current = TranscriptionSegment(
                    startTimeMs = minOf(current.startTimeMs, next.startTimeMs),
                    endTimeMs = maxOf(current.endTimeMs, next.endTimeMs),
                    words = current.words + next.words
                )
            }
            merged.add(current)
            i++
        }

        // If the very last segment ended up with < 3 words, try merging it backwards
        if (merged.size > 1 && merged.last().words.size < 3) {
            val last = merged.removeLast()
            val prev = merged.removeLast()
            val combined = TranscriptionSegment(
                startTimeMs = minOf(prev.startTimeMs, last.startTimeMs),
                endTimeMs = maxOf(prev.endTimeMs, last.endTimeMs),
                words = prev.words + last.words
            )
            merged.add(combined)
        }

        // 2. Split segments > 10 words
        val finalSegments = mutableListOf<TranscriptionSegment>()
        for (seg in merged) {
            var remainingSeg = seg
            while (remainingSeg.words.size > 10) {
                val words = remainingSeg.words
                val mid = words.size / 2
                var splitIndex = -1
                var minDistance = Int.MAX_VALUE

                // Try to find a sentence boundary near the middle
                for (j in 0 until words.size - 1) {
                    val wordText = words[j].word
                    if (wordText.endsWith(".") || wordText.endsWith("!") || wordText.endsWith("?")) {
                        val distance = kotlin.math.abs(j - mid)
                        if (distance < minDistance) {
                            minDistance = distance
                            splitIndex = j + 1 // split AFTER this word
                        }
                    }
                }

                // If no punctuation found, or it's too far from the middle, split at exact midpoint
                if (splitIndex == -1 || minDistance > words.size / 3) {
                    splitIndex = mid
                }

                // Safety bounds
                if (splitIndex <= 0 || splitIndex >= words.size) {
                    splitIndex = mid
                }

                val leftWords = words.subList(0, splitIndex)
                val rightWords = words.subList(splitIndex, words.size)

                finalSegments.add(
                    TranscriptionSegment(
                        startTimeMs = leftWords.first().startTimeMs,
                        endTimeMs = leftWords.last().endTimeMs,
                        words = leftWords
                    )
                )

                remainingSeg = TranscriptionSegment(
                    startTimeMs = rightWords.first().startTimeMs,
                    endTimeMs = rightWords.last().endTimeMs,
                    words = rightWords
                )
            }
            finalSegments.add(remainingSeg)
        }

        return finalSegments
    }

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        // whisperEngine.release() is a suspend function, and viewModelScope is cancelled here.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            whisperEngine.release()
        }
    }
}
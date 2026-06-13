package com.dipdev.aiautocaptioner.ui.processing


import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.StatFs
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dipdev.aiautocaptioner.core.logging.CrashReporter
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine
import com.dipdev.aiautocaptioner.core.whisper.WhisperEngine.WordTimestamp
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.model.WhisperModel
import com.dipdev.aiautocaptioner.data.repository.CaptionRepository
import com.dipdev.aiautocaptioner.data.repository.DownloadState
import com.dipdev.aiautocaptioner.data.repository.ModelRepository
import com.dipdev.aiautocaptioner.data.repository.ProjectRepository
import com.dipdev.aiautocaptioner.data.repository.TranscriptionSegment
import com.dipdev.aiautocaptioner.data.repository.TranscriptionWord
import com.dipdev.aiautocaptioner.service.TranscriptionService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// ════════════════════════════════════════════════════════════════════════════════
// Processing Steps — represents the current state of the processing pipeline
// ════════════════════════════════════════════════════════════════════════════════
sealed class ProcessingStep {
    data object Idle : ProcessingStep()
    data object Ready : ProcessingStep()

    // First-time model setup
    data class SetupAI(val models: List<WhisperModel>, val recommendedModelId: String?) : ProcessingStep()
    data class DownloadingModel(
        val modelName: String,
        val progress: Int = 0,
        val downloadedBytes: Long = 0,
        val totalBytes: Long = 0
    ) : ProcessingStep()

    data object ExtractingAudio : ProcessingStep()
    data object LoadingModel : ProcessingStep()
    data class Transcribing(val progress: Float = 0f, val estimatedSecondsRemaining: Int? = null) : ProcessingStep()
    data object Saving : ProcessingStep()
    data object Done : ProcessingStep()
    data object Cancelling : ProcessingStep()
    data object Cancelled : ProcessingStep()
    data class Error(val message: String) : ProcessingStep()
}

// ════════════════════════════════════════════════════════════════════════════════
// Streamed segment — a single caption chunk decoded by the AI in real-time
// ════════════════════════════════════════════════════════════════════════════════
data class StreamedSegment(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

// ════════════════════════════════════════════════════════════════════════════════
// Safety check for first-time downloads
// ════════════════════════════════════════════════════════════════════════════════
sealed class ModelSafetyCheck {
    data object Idle : ModelSafetyCheck()
    data class StorageError(val requiredMb: Long) : ModelSafetyCheck()
    data class CellularWarning(val modelId: String, val sizeMb: Long) : ModelSafetyCheck()
    data object Passed : ModelSafetyCheck()
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

    companion object {
        private const val TAG = "ProcessingVM"
        private const val SEGMENT_DRIP_DELAY_MS = 400L
    }

    private val _step = MutableStateFlow<ProcessingStep>(ProcessingStep.Idle)
    val step: StateFlow<ProcessingStep> = _step.asStateFlow()

    // The language currently selected in the UI — bound to the picker
    private val _selectedLanguage = MutableStateFlow("en")
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private val _workingVideoPath = MutableStateFlow<String?>(null)
    val workingVideoPath: StateFlow<String?> = _workingVideoPath.asStateFlow()

    // ── Drip-feed segment streaming ──────────────────────────────────────
    // Raw segments arrive here from the JNI callback (can be bursty)
    private val _segmentBuffer = Channel<StreamedSegment>(Channel.UNLIMITED)
    // UI observes this — segments appear one at a time with pacing
    private val _streamedSegments = MutableStateFlow<List<StreamedSegment>>(emptyList())
    val streamedSegments: StateFlow<List<StreamedSegment>> = _streamedSegments.asStateFlow()
    private var dripJob: Job? = null

    // Total segment count for the Done screen summary
    private val _segmentCount = MutableStateFlow(0)
    val segmentCount: StateFlow<Int> = _segmentCount.asStateFlow()

    // One-shot event: auto-navigate after Done
    private val _navigateToStyleEditor = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToStyleEditor: SharedFlow<Unit> = _navigateToStyleEditor.asSharedFlow()

    // Safety check state for model downloads
    private val _safetyCheck = MutableStateFlow<ModelSafetyCheck>(ModelSafetyCheck.Idle)
    val safetyCheck: StateFlow<ModelSafetyCheck> = _safetyCheck.asStateFlow()

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

    /** Start draining the segment buffer, emitting one segment at a time to the UI. */
    private fun startDripFeed() {
        dripJob?.cancel()
        dripJob = viewModelScope.launch {
            for (segment in _segmentBuffer) {
                _streamedSegments.value += segment
                delay(SEGMENT_DRIP_DELAY_MS)
            }
        }
    }

    /** Stop the drip feed and drain any remaining buffered segments immediately. */
    private fun flushDripFeed() {
        dripJob?.cancel()
        dripJob = null
        // Drain anything left in the buffer
        val remaining = mutableListOf<StreamedSegment>()
        while (true) {
            val seg = _segmentBuffer.tryReceive().getOrNull() ?: break
            remaining.add(seg)
        }
        if (remaining.isNotEmpty()) {
            _streamedSegments.value += remaining
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Prepare — called when the screen first appears
    // ════════════════════════════════════════════════════════════════════════

    fun prepareForProject(projectId: String) {
        viewModelScope.launch {
            val project = projectRepository.getProjectById(projectId)
            _workingVideoPath.value = project?.workingVideoPath
            // Restore the language saved from last transcription
            _selectedLanguage.value = project?.transcriptionLanguage ?: "en"

            if (project?.status == ProjectStatus.TRANSCRIBED || project?.status == ProjectStatus.EXPORTED) {
                _step.value = ProcessingStep.Done
            } else {
                _step.value = ProcessingStep.Ready
            }
        }
    }

    fun selectLanguage(language: String) {
        _selectedLanguage.value = language
    }

    // ════════════════════════════════════════════════════════════════════════
    // First-time model setup — Option B (Bottom Sheet picker)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Called when the user taps "Generate Captions" but no model is downloaded.
     * Filters models by language compatibility and profiles the device to recommend one.
     */
    fun showModelSetup() {
        val language = _selectedLanguage.value
        val allModels = modelRepository.getAvailableModels()

        // Filter: if non-English language selected, only show multilingual models.
        // If English is selected, only show English-optimized models (.en models).
        val compatibleModels = if (language != "en") {
            allModels.filter { it.isMultilingual }
        } else {
            allModels.filter { !it.isMultilingual }
        }

        // Recommend based on RAM (same logic as DeviceCheckViewModel)
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val totalRamMb = (memInfo.totalMem / 1024 / 1024).toInt()

        val recommendedId = when {
            language != "en" -> when {
                totalRamMb >= 3000 -> "small"
                else -> "base"
            }
            else -> when {
                totalRamMb >= 3000 -> "small.en"
                totalRamMb >= 1500 -> "base.en"
                else -> "tiny.en"
            }
        }

        _step.value = ProcessingStep.SetupAI(
            models = compatibleModels,
            recommendedModelId = if (compatibleModels.any { it.id == recommendedId }) recommendedId else compatibleModels.firstOrNull()?.id
        )
    }

    /**
     * Called when a returning user taps "Change" on the model indicator.
     * Shows the same picker bottom sheet but also includes already-downloaded models.
     */
    fun showModelPicker() {
        val language = _selectedLanguage.value
        val allModels = modelRepository.getAvailableModels()

        val compatibleModels = if (language != "en") {
            allModels.filter { it.isMultilingual }
        } else {
            allModels.filter { !it.isMultilingual }
        }

        val currentModelId = activeModel.value?.id

        _step.value = ProcessingStep.SetupAI(
            models = compatibleModels,
            recommendedModelId = currentModelId ?: compatibleModels.firstOrNull()?.id
        )
    }

    /**
     * Run safety checks before downloading. Shows cellular/storage warnings if needed.
     */
    fun checkSafetyAndDownload(modelId: String) {
        val model = modelRepository.getModelById(modelId) ?: return

        // Check storage
        val statFs = StatFs(context.filesDir.absolutePath)
        val availableMb = (statFs.availableBlocksLong * statFs.blockSizeLong) / (1024 * 1024)
        val requiredMb = (model.sizeMb * 1.5).toLong()

        if (availableMb < requiredMb) {
            _safetyCheck.value = ModelSafetyCheck.StorageError(requiredMb)
            return
        }

        // Check cellular
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val networkCapabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val isCellular = networkCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true

        if (isCellular) {
            _safetyCheck.value = ModelSafetyCheck.CellularWarning(modelId, model.sizeMb.toLong())
            return
        }

        _safetyCheck.value = ModelSafetyCheck.Passed
        startModelDownload(modelId)
    }

    fun resetSafetyCheck() {
        _safetyCheck.value = ModelSafetyCheck.Idle
    }

    fun cancelModelSetup() {
        if (_step.value is ProcessingStep.SetupAI) {
            _step.value = ProcessingStep.Ready
        }
    }

    /**
     * Proceed with download after user confirms cellular warning.

     */
    fun confirmCellularDownload(modelId: String) {
        _safetyCheck.value = ModelSafetyCheck.Idle
        startModelDownload(modelId)
    }

    /**
     * Download the selected model inline, then automatically start processing.
     */
    private var pendingProjectId: String? = null

    fun downloadAndProcess(modelId: String, projectId: String) {
        pendingProjectId = projectId
        checkSafetyAndDownload(modelId)
    }

    private fun startModelDownload(modelId: String) {
        val model = modelRepository.getModelById(modelId) ?: return

        // If already downloaded, skip directly to processing
        if (model.isDownloaded) {
            pendingProjectId?.let { startProcessing(it) }
            return
        }

        _step.value = ProcessingStep.DownloadingModel(modelName = model.displayName)

        viewModelScope.launch {
            modelRepository.downloadModel(modelId).collect { state ->
                when (state) {
                    is DownloadState.Starting -> {
                        _step.value = ProcessingStep.DownloadingModel(modelName = model.displayName)
                    }
                    is DownloadState.Downloading -> {
                        _step.value = ProcessingStep.DownloadingModel(
                            modelName = model.displayName,
                            progress = state.progress,
                            downloadedBytes = state.downloadedBytes,
                            totalBytes = state.totalBytes
                        )
                    }
                    is DownloadState.Complete -> {
                        Log.i(TAG, "Model download complete, starting processing")
                        pendingProjectId?.let { startProcessing(it) }
                    }
                    is DownloadState.Error -> {
                        _step.value = ProcessingStep.Error("Model download failed: ${state.message}")
                    }
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Cancel
    // ════════════════════════════════════════════════════════════════════════

    fun cancel() {
        isCancelled = true          // Set BEFORE cancelling the job to close the race window
        _step.value = ProcessingStep.Cancelling
        activeJob?.cancel()
        activeJob = null
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            whisperEngine.release()
            context.stopService(Intent(context, TranscriptionService::class.java))
            _step.value = ProcessingStep.Cancelled
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Main processing pipeline
    // ════════════════════════════════════════════════════════════════════════

    fun startProcessing(projectId: String) {
        if (activeJob?.isActive == true) return
        isCancelled = false         // Reset flag for each fresh processing run
        _streamedSegments.value = emptyList() // Clear any previous segments
        _segmentCount.value = 0
        startDripFeed() // Begin draining the segment buffer
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

                // Step 4 — Transcribe using full audio with JNI progress + segment callbacks
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
                    },
                    onSegmentDecoded = { text, startMs, endMs ->
                        // Buffer segment for drip-feed to UI
                        val trimmed = text.trim()
                        if (trimmed.isNotBlank() && !trimmed.startsWith("[")) {
                            val segment = StreamedSegment(
                                text = trimmed,
                                startMs = startMs,
                                endMs = endMs
                            )
                            _segmentBuffer.trySend(segment)
                        }
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
                _segmentCount.value = finalSegments.size

                whisperEngine.release()
                context.stopService(Intent(context, TranscriptionService::class.java))
                flushDripFeed() // Show any remaining buffered segments immediately
                _step.value = ProcessingStep.Done

                // Auto-navigate after a brief success animation
                delay(2000)
                _navigateToStyleEditor.tryEmit(Unit)

            } catch (e: kotlinx.coroutines.CancellationException) {
                Log.i(TAG, "Transcription cancelled")
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Error: ${e.message}", e)
                crashReporter.recordException(e)
                whisperEngine.release()
                context.stopService(Intent(context, TranscriptionService::class.java))
                _step.value = ProcessingStep.Error(e.message ?: "Unknown error")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════
    // Word grouping utilities (unchanged from original)
    // ════════════════════════════════════════════════════════════════════════

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
            val shouldSplit = gapToNext > 1000 || currentWords.size >= 8
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
            val last = merged.removeAt(merged.lastIndex)
            val prev = merged.removeAt(merged.lastIndex)
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
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            whisperEngine.release()
        }
    }
}
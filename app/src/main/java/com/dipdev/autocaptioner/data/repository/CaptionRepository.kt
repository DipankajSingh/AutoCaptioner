package com.dipdev.autocaptioner.data.repository

import android.util.Log
import com.dipdev.autocaptioner.data.db.dao.CaptionSegmentDao
import com.dipdev.autocaptioner.data.db.dao.CaptionStyleDao
import com.dipdev.autocaptioner.data.db.dao.CaptionWordDao
import com.dipdev.autocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.autocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.autocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.autocaptioner.data.db.entity.EmphasisType
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptionRepository @Inject constructor(
    private val segmentDao: CaptionSegmentDao,
    private val wordDao: CaptionWordDao,
    private val styleDao: CaptionStyleDao
) {

    companion object {
        private const val TAG = "CaptionRepository"
    }

    // ================================================================
    // SEGMENTS
    // ================================================================

    // Observe segments for a project — auto-updates the caption editor
    fun getSegmentsForProject(projectId: String): Flow<List<CaptionSegmentEntity>> =
        segmentDao.getSegmentsForProject(projectId)

    // One-time read — used for export (don't need live updates during export)
    suspend fun getSegmentsOnce(projectId: String): List<CaptionSegmentEntity> =
        segmentDao.getSegmentsForProjectOnce(projectId)

    // ---- Save transcription results ----
    // Called after Whisper finishes transcribing
    // Takes raw Whisper output and saves it as structured segments + words
    // transcriptionData = list of segments, each with a list of words
    suspend fun saveTranscription(
        projectId: String,
        // Each item in the outer list = one caption segment (phrase)
        // Each item in the inner list = one word with timestamps
        // Format: List<Pair<segmentTimes, List<wordData>>>
        segments: List<TranscriptionSegment>
    ) {
        // First delete any existing transcription for this project
        // (in case user is re-transcribing)
        segmentDao.deleteSegmentsForProject(projectId)
        wordDao.deleteWordsForProject(projectId)

        // Build the entity lists to insert
        val segmentEntities = mutableListOf<CaptionSegmentEntity>()
        val wordEntities = mutableListOf<CaptionWordEntity>()

        segments.forEachIndexed { segmentIndex, segment ->
            val segmentId = UUID.randomUUID().toString()

            // Create the segment entity
            segmentEntities.add(
                CaptionSegmentEntity(
                    id = segmentId,
                    projectId = projectId,
                    index = segmentIndex,
                    startTimeMs = segment.startTimeMs,
                    endTimeMs = segment.endTimeMs,
                    // Join all word texts to form the full segment text
                    // "Hello" + " " + "world" = "Hello world"
                    text = segment.words.joinToString(" ") { it.word }
                )
            )

            // Create a word entity for each word in this segment
            segment.words.forEachIndexed { wordIndex, word ->
                wordEntities.add(
                    CaptionWordEntity(
                        id = UUID.randomUUID().toString(),
                        segmentId = segmentId,
                        projectId = projectId,
                        index = wordIndex,
                        word = word.word,
                        startTimeMs = word.startTimeMs,
                        endTimeMs = word.endTimeMs,
                        confidence = word.confidence
                    )
                )
            }
        }

        // Batch insert — much faster than inserting one by one
        // Room wraps this in a single database transaction
        segmentDao.insertAll(segmentEntities)
        wordDao.insertAll(wordEntities)

        Log.i(TAG, "Saved ${segmentEntities.size} segments, ${wordEntities.size} words")
    }

    // ---- Update a segment after user edits it ----
    suspend fun updateSegment(segment: CaptionSegmentEntity) {
        // Mark as edited so we know the user changed it
        segmentDao.updateSegment(segment.copy(isEdited = true))
    }

    /**
     * Post-process existing DB words to merge split contractions.
     * Whisper tokenises "it's" as ["it", "'s"] — this pass collapses them
     * in-place and rebuilds the parent segment text to match.
     */
    suspend fun fixContractionsForProject(projectId: String) {
        val allWords = wordDao.getAllWordsForProject(projectId)
        val segments = segmentDao.getSegmentsForProjectOnce(projectId)

        // Merge contraction suffixes into the preceding word
        val merged = mutableListOf<CaptionWordEntity>()
        for (word in allWords) {
            val trimmed = word.word.trim()
            if (trimmed.startsWith("'") && merged.isNotEmpty()) {
                val prev = merged.removeLast()
                merged.add(prev.copy(word = prev.word.trimEnd() + trimmed, endTimeMs = word.endTimeMs))
                // Delete the dangling suffix row
                wordDao.deleteWord(word.id)
            } else {
                merged.add(word)
            }
        }

        // Persist merged words and rebuild segment text
        for (w in merged) {
            wordDao.updateWord(w)
        }
        for (seg in segments) {
            val segWords = merged.filter { it.segmentId == seg.id }.sortedBy { it.index }
            val newText = segWords.joinToString(" ") { it.word }
            if (newText != seg.text) {
                segmentDao.updateSegment(seg.copy(text = newText))
            }
        }
        Log.i(TAG, "fixContractionsForProject: processed ${merged.size} words for $projectId")
    }

    // ================================================================
    // WORDS
    // ================================================================

    // Observe words for a segment — used in caption editor word list
    fun getWordsForSegment(segmentId: String): Flow<List<CaptionWordEntity>> =
        wordDao.getWordsForSegment(segmentId)

    // Get ALL words for preview — loaded once at preview start
    // The preview screen keeps this list in memory and binary-searches
    // it 60 times per second to find the active word
    suspend fun getAllWordsForProject(projectId: String): List<CaptionWordEntity> =
        wordDao.getAllWordsForProject(projectId)

    // Find the active word at a specific playback position
    // Used in preview to highlight the correct karaoke word
    suspend fun getActiveWordAtTime(
        projectId: String,
        currentTimeMs: Long
    ): CaptionWordEntity? =
        wordDao.getActiveWordAtTime(projectId, currentTimeMs)

    // Toggle emphasis on a word — called from caption editor
    suspend fun toggleEmphasis(
        wordId: String,
        isEmphasized: Boolean,
        emphasisType: EmphasisType = EmphasisType.BOUNCE
    ) {
        wordDao.updateEmphasis(wordId, isEmphasized, emphasisType)
    }

    suspend fun replaceWordsForSegment(segmentId: String, newWords: List<CaptionWordEntity>) {
        wordDao.deleteWordsForSegment(segmentId)
        wordDao.insertAll(newWords)
    }

    suspend fun updateWords(words: List<CaptionWordEntity>) {
        wordDao.insertAll(words)
    }

    // ================================================================
    // STYLES
    // ================================================================

    // Observe all styles for the style picker
    fun getAllStyles(): Flow<List<CaptionStyleEntity>> =
        styleDao.getAllStyles()

    // Get a specific style by ID
    suspend fun getStyleById(styleId: String): CaptionStyleEntity? =
        styleDao.getStyleById(styleId)

    // Save a new or modified style
    suspend fun saveStyle(style: CaptionStyleEntity) {
        styleDao.insertStyle(style)
        Log.i(TAG, "Saved style: ${style.name}")
    }

    // Delete a user's custom style
    // We check isDefault here as a safety guard
    suspend fun deleteStyle(style: CaptionStyleEntity) {
        if (style.isDefault) {
            Log.w(TAG, "Cannot delete default style: ${style.name}")
            return
        }
        styleDao.deleteStyle(style)
    }

    // Insert built-in preset styles on first launch
    // Called from SplashScreen ViewModel
    suspend fun initializeDefaultStyles() {
        // Only insert if no default styles exist yet
        if (styleDao.getDefaultStyleCount() > 0) return

        val defaults = listOf(
            // ---- Bold Pop ----
            // CapCut-style, works on any video
            CaptionStyleEntity(
                id = UUID.randomUUID().toString(),
                name = "Bold Pop",
                isDefault = true,
                fontFamily = "Montserrat",
                fontWeight = 900,
                fontSize = 52f,
                textColor = 0xFFFFFFFF,
                highlightColor = 0xFFFFD700,   // gold highlight
                outlineColor = 0xFF000000,
                outlineWidth = 4f,
                backgroundType = com.dipdev.autocaptioner.data.db.entity.BackgroundType.NONE,
                displayMode = com.dipdev.autocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                wordEnterAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.SCALE_POP,
                wordExitAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.FADE,
                positionX = 0.5f,
                positionY = 0.85f
            ),
            // ---- Minimal ----
            // Clean, subtle — good for professional content
            CaptionStyleEntity(
                id = UUID.randomUUID().toString(),
                name = "Minimal",
                isDefault = true,
                fontFamily = "Roboto",
                fontWeight = 400,
                fontSize = 40f,
                textColor = 0xFFFFFFFF,
                highlightColor = 0xFFFFFFFF,
                outlineColor = 0xFF000000,
                outlineWidth = 2f,
                backgroundType = com.dipdev.autocaptioner.data.db.entity.BackgroundType.NONE,
                displayMode = com.dipdev.autocaptioner.data.db.entity.DisplayMode.LINE_HIGHLIGHT,
                wordEnterAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.FADE,
                wordExitAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.FADE,
                positionX = 0.5f,
                positionY = 0.88f
            ),
            // ---- Neon ----
            // Eye-catching, good for music/entertainment content
            CaptionStyleEntity(
                id = UUID.randomUUID().toString(),
                name = "Neon",
                isDefault = true,
                fontFamily = "Montserrat",
                fontWeight = 700,
                fontSize = 48f,
                textColor = 0xFF00FF88,         // neon green
                highlightColor = 0xFFFF00FF,    // neon pink
                outlineColor = 0xFF000000,
                outlineWidth = 0f,
                shadowColor = 0xFF00FF88,        // glow effect via shadow
                shadowRadius = 12f,
                backgroundType = com.dipdev.autocaptioner.data.db.entity.BackgroundType.NONE,
                displayMode = com.dipdev.autocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                wordEnterAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.SCALE_POP,
                wordExitAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.FADE,
                positionX = 0.5f,
                positionY = 0.82f
            ),
            // ---- Cinema ----
            // Classic subtitle style — bottom center with background
            CaptionStyleEntity(
                id = UUID.randomUUID().toString(),
                name = "Cinema",
                isDefault = true,
                fontFamily = "Roboto",
                fontWeight = 400,
                fontSize = 36f,
                textColor = 0xFFFFFFFF,
                highlightColor = 0xFFFFFF00,    // yellow highlight
                outlineColor = 0xFF000000,
                outlineWidth = 1f,
                backgroundType = com.dipdev.autocaptioner.data.db.entity.BackgroundType.FULL_LINE,
                backgroundColor = 0xFF000000,
                backgroundOpacity = 0.7f,
                displayMode = com.dipdev.autocaptioner.data.db.entity.DisplayMode.PHRASE,
                wordEnterAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.FADE,
                wordExitAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.FADE,
                positionX = 0.5f,
                positionY = 0.92f
            ),
            // ---- TikTok ----
            // Center screen, thick outline, high energy
            CaptionStyleEntity(
                id = UUID.randomUUID().toString(),
                name = "TikTok",
                isDefault = true,
                fontFamily = "Montserrat",
                fontWeight = 900,
                fontSize = 56f,
                textColor = 0xFFFFFFFF,
                highlightColor = 0xFFFF4444,    // red highlight
                outlineColor = 0xFF000000,
                outlineWidth = 6f,
                backgroundType = com.dipdev.autocaptioner.data.db.entity.BackgroundType.NONE,
                displayMode = com.dipdev.autocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                wordEnterAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.BOUNCE,
                wordExitAnimation = com.dipdev.autocaptioner.data.db.entity.AnimationType.FADE,
                positionX = 0.5f,
                positionY = 0.5f               // center screen
            )
        )

        styleDao.insertDefaultStyles(defaults)
        Log.i(TAG, "Initialized ${defaults.size} default styles")
    }
}

// ================================================================
// Data transfer objects for transcription results
// These are NOT database entities — they're just temporary
// containers for passing Whisper's output into this repository
// ================================================================

// Represents one caption segment (a group of words shown together)
data class TranscriptionSegment(
    val startTimeMs: Long,
    val endTimeMs: Long,
    val words: List<TranscriptionWord>
)

// Represents one word with its timing from Whisper
data class TranscriptionWord(
    val word: String,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val confidence: Float = 1.0f
)
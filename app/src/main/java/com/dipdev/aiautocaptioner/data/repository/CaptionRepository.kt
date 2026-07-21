package com.dipdev.aiautocaptioner.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.dipdev.aiautocaptioner.data.db.AppDatabase
import com.dipdev.aiautocaptioner.data.db.dao.CaptionSegmentDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionStyleDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionWordDao
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaptionRepository @Inject constructor(
    private val db: AppDatabase,
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
        // Map segments to entities with a pre-generated ID so words can reference them
        val segmentsWithIds = segments.mapIndexed { segmentIndex, segment ->
            UUID.randomUUID().toString() to Pair(segmentIndex, segment)
        }

        val segmentEntities = segmentsWithIds.map { (id, indexedSegment) ->
            val (segmentIndex, segment) = indexedSegment
            CaptionSegmentEntity(
                id = id,
                projectId = projectId,
                index = segmentIndex,
                startTimeMs = segment.startTimeMs,
                endTimeMs = segment.endTimeMs,
                text = segment.words.joinToString(" ") { it.word }
            )
        }

        val wordEntities = segmentsWithIds.flatMap { (id, indexedSegment) ->
            val (_, segment) = indexedSegment
            segment.words.mapIndexed { wordIndex, word ->
                CaptionWordEntity(
                    id = UUID.randomUUID().toString(),
                    segmentId = id,
                    projectId = projectId,
                    index = wordIndex,
                    word = word.word,
                    startTimeMs = word.startTimeMs,
                    endTimeMs = word.endTimeMs,
                    confidence = word.confidence
                )
            }
        }

        // Run all operations inside a single transaction to ensure atomicity
        db.withTransaction {
            // Delete segments — words are cleaned up automatically via ON DELETE CASCADE
            segmentDao.deleteSegmentsForProject(projectId)
            segmentDao.insertAll(segmentEntities)
            wordDao.insertAll(wordEntities)
        }

        Log.i(TAG, "Saved ${segmentEntities.size} segments, ${wordEntities.size} words")
    }

    // ---- Update a segment after user edits it ----
    suspend fun updateSegment(segment: CaptionSegmentEntity) {
        // Mark as edited so we know the user changed it
        segmentDao.updateSegment(segment.copy(isEdited = true))
    }


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

    // Observe all words for a project
    fun getAllWordsForProjectFlow(projectId: String): Flow<List<CaptionWordEntity>> =
        wordDao.getAllWordsForProjectFlow(projectId)
    // Toggle emphasis on a word — called from caption editor
    suspend fun toggleEmphasis(
        wordId: String,
        isEmphasized: Boolean,
        emphasisType: EmphasisType = EmphasisType.BOUNCE
    ) {
        wordDao.updateEmphasis(wordId, isEmphasized, emphasisType)
    }

    suspend fun replaceWordsForSegment(segmentId: String, newWords: List<CaptionWordEntity>) {
        db.withTransaction {
            wordDao.deleteWordsForSegment(segmentId)
            wordDao.insertAll(newWords)
        }
    }

    // Update a list of existing words in-place (called from CaptionEditorViewModel
    // when the user edits a segment and the word count matches the original).
    // Uses @Update under the hood — does NOT insert new rows.
    suspend fun updateWords(words: List<CaptionWordEntity>) {
        wordDao.updateWords(words)
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
        } else {
            styleDao.deleteStyle(style)
        }
    }

    // Insert built-in preset styles on first launch
    // Called from SplashScreen ViewModel
    suspend fun initializeDefaultStyles() {
        db.withTransaction {
                    val defaults = listOf(
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Basic",
                            isDefault = true,
                            fontFamily = "Roboto",
                            fontWeight = 400,
                            fontSize = 40f,
                            textColor = 0xFFFFFFFF,
                            highlightColor = 0xFFFFFFFF,
                            outlineColor = 0xFF000000,
                            outlineWidth = 2f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.PHRASE,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            positionX = 0.5f,
                            positionY = 0.85f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Karaoke Pro",
                            isDefault = true,
                            fontFamily = "Montserrat",
                            fontWeight = 900,
                            fontSize = 50f,
                            textColor = 0xFFE0E0E0,
                            highlightColor = 0xFFFFC107,
                            karaokeFillColor = 0xFFFFC107,
                            outlineColor = 0xFF000000,
                            outlineWidth = 5f,
                            shadowColor = 0xAA000000,
                            shadowRadius = 8f,
                            shadowOffsetY = 4f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.KARAOKE_FILL,
                            karaokeHighlightMode = com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode.FILL_LEFT_RIGHT,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            positionX = 0.5f,
                            positionY = 0.82f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Cyberpunk",
                            isDefault = true,
                            fontFamily = "Roboto",
                            fontWeight = 700,
                            fontSize = 48f,
                            isItalic = true,
                            textColor = 0xFF00FFCC,
                            highlightColor = 0xFFFF0055,
                            outlineColor = 0xFF000000,
                            outlineWidth = 1f,
                            shadowColor = 0xFF00FFCC,
                            shadowRadius = 15f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.ELASTIC,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.FADE,
                            positionX = 0.5f,
                            positionY = 0.5f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Cinematic",
                            isDefault = true,
                            fontFamily = "Montserrat",
                            fontWeight = 400,
                            fontSize = 36f,
                            letterSpacing = 0.05f,
                            textColor = 0xFFFFFFFF,
                            highlightColor = 0xFFFFFFFF,
                            outlineColor = 0xFF000000,
                            outlineWidth = 0f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.BOX,
                            backgroundColor = 0xAA000000,
                            backgroundCornerRadius = 12f,
                            backgroundPaddingH = 24f,
                            backgroundPaddingV = 16f,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.PHRASE,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            positionX = 0.5f,
                            positionY = 0.90f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Typewriter",
                            isDefault = true,
                            fontFamily = "Roboto",
                            fontWeight = 700,
                            fontSize = 42f,
                            textColor = 0xFF00FF00,
                            highlightColor = 0xFF00FF00,
                            outlineColor = 0xFF000000,
                            outlineWidth = 3f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.TYPEWRITER,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.TYPEWRITER,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            positionX = 0.5f,
                            positionY = 0.85f
                        ),
                        // ---- New presets ----
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Hormozi",
                            isDefault = true,
                            fontFamily = "Bebas Neue",
                            fontWeight = 700,
                            fontSize = 52f,
                            textColor = 0xFFFFFFFF,
                            highlightColor = 0xFFFFD700,
                            outlineColor = 0xFFB8860B,
                            outlineWidth = 3f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.SCALE_POP,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.FADE,
                            positionX = 0.5f,
                            positionY = 0.82f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Neon Glow",
                            isDefault = true,
                            fontFamily = "Bebas Neue",
                            fontWeight = 700,
                            fontSize = 54f,
                            textColor = 0xFF00FFFF,
                            highlightColor = 0xFFFF69B4,
                            secondaryColor = 0xFF00CED1,
                            outlineColor = 0xFFFF00FF,
                            outlineWidth = 2f,
                            glowEnabled = true,
                            glowColor = 0xFF00FFFF,
                            glowRadius = 12f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.BOUNCE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.FADE,
                            positionX = 0.5f,
                            positionY = 0.85f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Story Time",
                            isDefault = true,
                            fontFamily = "Pacifico",
                            fontWeight = 400,
                            fontSize = 44f,
                            textColor = 0xFFF5F5DC,
                            highlightColor = 0xFFFFD700,
                            outlineColor = 0xFF8B4513,
                            outlineWidth = 2f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.FADE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.FADE,
                            positionX = 0.5f,
                            positionY = 0.80f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Tech Terminal",
                            isDefault = true,
                            fontFamily = "Space Mono",
                            fontWeight = 400,
                            fontSize = 38f,
                            textColor = 0xFF00FF41,
                            highlightColor = 0xFFFFFF00,
                            outlineColor = 0xFF003300,
                            outlineWidth = 2f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.FULL_LINE,
                            backgroundColor = 0xDD0A0A0A,
                            backgroundOpacity = 0.85f,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.TYPEWRITER,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.TYPEWRITER,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            textTransform = com.dipdev.aiautocaptioner.data.db.entity.TextTransform.UPPERCASE,
                            positionX = 0.5f,
                            positionY = 0.88f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Elegant",
                            isDefault = true,
                            fontFamily = "Playfair Display",
                            fontWeight = 700,
                            fontSize = 40f,
                            letterSpacing = 0.03f,
                            textColor = 0xFFD4AF37,
                            highlightColor = 0xFFFFD700,
                            secondaryColor = 0xFFF5E6B8,
                            outlineColor = 0xFF1A0A00,
                            outlineWidth = 3f,
                            shadowColor = 0x40000000,
                            shadowRadius = 4f,
                            shadowOffsetX = 1f,
                            shadowOffsetY = 1f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.PHRASE,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            gradientDirection = com.dipdev.aiautocaptioner.data.db.entity.GradientDirection.LEFT_RIGHT,
                            positionX = 0.5f,
                            positionY = 0.88f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Bold Pop",
                            isDefault = true,
                            fontFamily = "Montserrat",
                            fontWeight = 900,
                            fontSize = 50f,
                            textColor = 0xFFFFFFFF,
                            highlightColor = 0xFFFFC107,
                            outlineColor = 0xFF000000,
                            outlineWidth = 4f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.BOUNCE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.FADE,
                            positionX = 0.5f,
                            positionY = 0.82f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Retro Sign",
                            isDefault = true,
                            fontFamily = "Bungee",
                            fontWeight = 400,
                            fontSize = 46f,
                            textColor = 0xFFFF4500,
                            highlightColor = 0xFFFFD700,
                            outlineColor = 0xFFFF4500,
                            outlineWidth = 3f,
                            outlineOnly = true,
                            glowEnabled = true,
                            glowColor = 0xFFFF4500,
                            glowRadius = 10f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.PHRASE,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.NONE,
                            positionX = 0.5f,
                            positionY = 0.85f
                        ),
                        CaptionStyleEntity(
                            id = UUID.randomUUID().toString(),
                            name = "Smooth Gradient",
                            isDefault = true,
                            fontFamily = "Rubik",
                            fontWeight = 500,
                            fontSize = 44f,
                            textColor = 0xFF6A11CB,
                            highlightColor = 0xFFFFD700,
                            secondaryColor = 0xFF2575FC,
                            outlineColor = 0x00000000,
                            outlineWidth = 0f,
                            backgroundType = com.dipdev.aiautocaptioner.data.db.entity.BackgroundType.NONE,
                            displayMode = com.dipdev.aiautocaptioner.data.db.entity.DisplayMode.WORD_BY_WORD,
                            wordEnterAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.FADE,
                            wordExitAnimation = com.dipdev.aiautocaptioner.data.db.entity.AnimationType.FADE,
                            gradientDirection = com.dipdev.aiautocaptioner.data.db.entity.GradientDirection.DIAGONAL,
                            positionX = 0.5f,
                            positionY = 0.83f
                        ),
                    )

                    val existingNames = styleDao.getDefaultStyleNames().toSet()
                    val newDefaults = defaults.filter { it.name !in existingNames }
                    if (newDefaults.isNotEmpty()) {
                        styleDao.insertDefaultStyles(newDefaults)
                        Log.i(TAG, "Seeded ${newDefaults.size} new default styles (skipped ${defaults.size - newDefaults.size} existing)")
                    } else {
                        Log.i(TAG, "All ${defaults.size} default styles already present")
                    }
            }
    }

    suspend fun buildSrtContent(projectId: String): String {
        val segmentsList = getSegmentsOnce(projectId)
        val sb = java.lang.StringBuilder()
        segmentsList.forEachIndexed { index, segment ->
            sb.append(index + 1).append("\n")
            sb.append(formatSrtTime(segment.startTimeMs)).append(" --> ").append(formatSrtTime(segment.endTimeMs)).append("\n")
            val text = segment.text.ifBlank { " " }
            sb.append(text).append("\n\n")
        }
        return sb.toString()
    }

    private fun formatSrtTime(timeMs: Long): String {
        val hours = timeMs / 3600000
        val minutes = (timeMs % 3600000) / 60000
        val seconds = (timeMs % 60000) / 1000
        val millis = timeMs % 1000
        // Use Locale.US to guarantee ASCII digits — on Arabic/Persian locales
        // String.format() produces locale-specific digits (٠١٢٣) which break
        // every SRT parser.
        return String.format(java.util.Locale.US, "%02d:%02d:%02d,%03d", hours, minutes, seconds, millis)
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

package com.dipdev.aiautocaptioner.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.dipdev.aiautocaptioner.data.db.AppDatabase
import com.dipdev.aiautocaptioner.data.db.dao.CaptionSegmentDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionStyleDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionWordDao
import com.dipdev.aiautocaptioner.data.db.entity.*
import com.dipdev.aiautocaptioner.engine.style.PresetFactory
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

    // Get the first available style (fallback for projects with no activeStyleId)
    suspend fun getFirstStyle(): CaptionStyleEntity? =
        styleDao.getFirstStyle()

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
                PresetFactory.create(
                    name = "Beast Mode",
                    fontFamily = "Bebas Neue",
                    fontWeight = 900,
                    fontSize = 85f,
                    letterSpacing = -0.02f,
                    outlineWidth = 6f,
                    displayMode = DisplayMode.WORD_BY_WORD,
                    wordEnterAnimation = AnimationType.SCALE_POP,
                    wordExitAnimation = AnimationType.FADE,
                    animationDurationMs = 200,
                    maxWordsPerLine = 3,
                    maxLines = 2,
                    positionY = 0.60f,
                    textTransform = TextTransform.UPPERCASE
                ),
                PresetFactory.create(
                    name = "Hormozi",
                    fontFamily = "Montserrat",
                    fontWeight = 900,
                    fontSize = 90f,
                    highlightColor = 0xFFFFD700,
                    outlineWidth = 5f,
                    displayMode = DisplayMode.WORD_BY_WORD,
                    wordEnterAnimation = AnimationType.FADE,
                    wordExitAnimation = AnimationType.FADE,
                    animationDurationMs = 120,
                    maxWordsPerLine = 3,
                    maxLines = 2,
                    positionY = 0.65f,
                    textTransform = TextTransform.UPPERCASE,
                    letterSpacing = -0.01f
                ),
                PresetFactory.create(
                    name = "Box Background",
                    fontFamily = "Inter",
                    fontWeight = 600,
                    fontSize = 54f,
                    outlineWidth = 0f,
                    shadowRadius = 0f,
                    backgroundType = BackgroundType.BOX,
                    backgroundOpacity = 0.78f,
                    backgroundCornerRadius = 14f,
                    backgroundPaddingH = 12f,
                    backgroundPaddingV = 8f,
                    displayMode = DisplayMode.LINE_HIGHLIGHT,
                    wordEnterAnimation = AnimationType.FADE,
                    wordExitAnimation = AnimationType.FADE,
                    animationDurationMs = 200,
                    maxWordsPerLine = 5,
                    maxLines = 2,
                    positionY = 0.80f
                ),
                PresetFactory.create(
                    name = "Karaoke",
                    fontFamily = "Montserrat",
                    fontWeight = 700,
                    fontSize = 72f,
                    highlightColor = 0xFFFFD60A,
                    outlineWidth = 3.5f,
                    displayMode = DisplayMode.KARAOKE_FILL,
                    karaokeHighlightMode = KaraokeHighlightMode.FILL_LEFT_RIGHT,
                    karaokeFillColor = 0xFFFFD60A,
                    maxWordsPerLine = 4,
                    maxLines = 2,
                    positionY = 0.70f,
                    animationDurationMs = 150
                ),
                PresetFactory.create(
                    name = "Neon Glow",
                    fontFamily = "Bebas Neue",
                    fontWeight = 700,
                    fontSize = 80f,
                    textColor = 0xFF00FFFF,
                    outlineColor = 0xFF000000,
                    outlineWidth = 2.5f,
                    glowEnabled = true,
                    glowColor = 0xFF00FFFF,
                    glowRadius = 8f,
                    displayMode = DisplayMode.WORD_BY_WORD,
                    wordEnterAnimation = AnimationType.SCALE_POP,
                    wordExitAnimation = AnimationType.FADE,
                    animationDurationMs = 150,
                    maxWordsPerLine = 3,
                    maxLines = 2,
                    positionY = 0.55f,
                    textTransform = TextTransform.UPPERCASE
                ),
                PresetFactory.create(
                    name = "Clean Minimal",
                    fontFamily = "Inter",
                    fontWeight = 500,
                    fontSize = 50f,
                    outlineWidth = 0f,
                    shadowColor = 0x40000000,
                    shadowRadius = 4f,
                    shadowOffsetX = 2f,
                    shadowOffsetY = 2f,
                    backgroundType = BackgroundType.BOX,
                    backgroundColor = 0xFF000000,
                    backgroundOpacity = 0.40f,
                    backgroundCornerRadius = 8f,
                    backgroundPaddingH = 12f,
                    backgroundPaddingV = 6f,
                    displayMode = DisplayMode.PHRASE,
                    maxWordsPerLine = 6,
                    maxLines = 2,
                    positionY = 0.80f,
                    animationDurationMs = 200
                ),
                PresetFactory.create(
                    name = "Bold Highlight",
                    fontFamily = "Montserrat",
                    fontWeight = 700,
                    fontSize = 65f,
                    highlightColor = 0xFFFF4500,
                    outlineWidth = 3.5f,
                    displayMode = DisplayMode.WORD_BY_WORD,
                    wordEnterAnimation = AnimationType.FADE,
                    wordExitAnimation = AnimationType.FADE,
                    animationDurationMs = 120,
                    maxWordsPerLine = 4,
                    maxLines = 2,
                    positionY = 0.65f,
                    textTransform = TextTransform.TITLE_CASE
                ),
                PresetFactory.create(
                    name = "MrBeast Cyan",
                    fontFamily = "Bangers",
                    fontWeight = 700,
                    fontSize = 82f,
                    textColor = 0xFF00FFFF,
                    outlineColor = 0xFFFFFFFF,
                    outlineWidth = 3.5f,
                    letterSpacing = -0.02f,
                    displayMode = DisplayMode.WORD_BY_WORD,
                    wordEnterAnimation = AnimationType.SCALE_POP,
                    wordExitAnimation = AnimationType.FADE,
                    animationDurationMs = 200,
                    maxWordsPerLine = 3,
                    maxLines = 2,
                    positionY = 0.60f,
                    textTransform = TextTransform.UPPERCASE
                ),
                PresetFactory.create(
                    name = "Typewriter Reveal",
                    fontFamily = "Space Mono",
                    fontWeight = 700,
                    fontSize = 60f,
                    outlineWidth = 2.5f,
                    backgroundType = BackgroundType.PILL,
                    backgroundColor = 0xFF000000,
                    backgroundOpacity = 0.50f,
                    backgroundCornerRadius = 8f,
                    backgroundPaddingH = 14f,
                    backgroundPaddingV = 6f,
                    displayMode = DisplayMode.TYPEWRITER,
                    wordEnterAnimation = AnimationType.TYPEWRITER,
                    animationDurationMs = 60,
                    maxWordsPerLine = 5,
                    maxLines = 2,
                    positionY = 0.70f
                ),
                PresetFactory.create(
                    name = "Luxury Editorial",
                    fontFamily = "Playfair Display",
                    fontWeight = 700,
                    fontSize = 52f,
                    textColor = 0xFFFDFBF7,
                    outlineWidth = 0f,
                    letterSpacing = 0.08f,
                    shadowColor = 0x4C000000,
                    shadowRadius = 25f,
                    shadowOffsetX = 0f,
                    shadowOffsetY = 0f,
                    displayMode = DisplayMode.PHRASE,
                    animationDurationMs = 300,
                    maxWordsPerLine = 4,
                    maxLines = 2,
                    positionY = 0.60f,
                    textTransform = TextTransform.TITLE_CASE
                )
            )

            // Remove presets that no longer exist in the system definitions.
            styleDao.removeDeprecatedDefaultStyles(defaults.map { it.name })
            // Insert/update all presets.  Uses REPLACE, so existing rows with the
            // same stable ID get updated fields without generating new IDs.
            styleDao.insertDefaultStyles(defaults)
            Log.i(TAG, "Seeded ${defaults.size} default caption styles")
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

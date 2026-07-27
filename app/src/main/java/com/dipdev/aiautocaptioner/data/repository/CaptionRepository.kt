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
            // ── Preset definitions ────────────────────────────────────────────
            // maxWordsPerLine / maxLines follow industry conventions:
            //   WORD_BY_WORD presets: 1 word, 1 line (CapCut/TikTok single-word pop)
            //   KARAOKE_FILL presets: 4 words, 2 lines (CapCut/Reels style dynamic lyric pages)
            //   PHRASE/CINEMATIC:     5–7 words, 2 lines (full subtitle block)
            //   TYPEWRITER presets:   6 words, 2 lines (accumulate naturally)
            val defaults = listOf(
                PresetFactory.create(
                    name = "Basic",
                    fontFamily = "Roboto",
                    fontWeight = 400,
                    fontSize = 40f,
                    outlineWidth = 2f
                ),
                PresetFactory.create(
                    name = "Karaoke Pro",
                    fontFamily = "Montserrat",
                    fontWeight = 900,
                    fontSize = 50f,
                    highlightColor = 0xFFFFC107,
                    outlineWidth = 5f,
                    isKaraoke = true,
                    positionY = 0.82f
                ) {
                    it.copy(
                        karaokeFillColor = 0xFFFFC107,
                        karaokeHighlightMode = KaraokeHighlightMode.FILL_LEFT_RIGHT,
                        shadowColor = 0xAA000000,
                        shadowRadius = 8f,
                        shadowOffsetY = 4f
                    )
                },
                PresetFactory.create(
                    name = "Viral Pill",
                    fontFamily = "Montserrat",
                    fontWeight = 900,
                    fontSize = 52f,
                    textColor = 0xFFFFFFFF,
                    highlightColor = 0xFFFFC107,
                    outlineColor = 0xFF000000,
                    outlineWidth = 4f,
                    isKaraoke = false,
                    positionY = 0.80f
                ) {
                    it.copy(
                        displayMode = DisplayMode.LINE_HIGHLIGHT,
                        maxWordsPerLine = 3,
                        maxLines = 1,
                        karaokeHighlightMode = KaraokeHighlightMode.BACKGROUND_HIGHLIGHT,
                        activeWordBgColor = 0xFFFFC107,
                        activeWordTextColor = 0xFF000000,
                        activeWordCornerRadius = 14f
                    )
                },
                PresetFactory.create(
                    name = "Cyberpunk",
                    fontFamily = "Roboto",
                    fontWeight = 700,
                    fontSize = 48f,
                    textColor = 0xFF00FFCC,
                    highlightColor = 0xFFFF0055,
                    outlineColor = 0xFF00FFCC,
                    outlineWidth = 0f,
                    isWordByWord = true,
                    positionY = 0.5f
                ) {
                    it.copy(
                        isItalic = true,
                        wordEnterAnimation = AnimationType.ELASTIC,
                        wordExitAnimation = AnimationType.FADE,
                        glowEnabled = true,
                        glowColor = 0xFF00FFCC,
                        glowRadius = 10f,
                        shadowColor = 0xFF00FFCC,
                        shadowRadius = 15f
                    )
                },
                PresetFactory.create(
                    name = "Cinematic",
                    fontFamily = "Montserrat",
                    fontWeight = 400,
                    fontSize = 36f,
                    outlineWidth = 0f,
                    hasBg = true,
                    backgroundColor = 0xAA000000,
                    positionY = 0.90f
                ) {
                    it.copy(
                        letterSpacing = 0.05f,
                        maxWordsPerLine = 7,
                        backgroundCornerRadius = 12f,
                        backgroundPaddingH = 24f,
                        backgroundPaddingV = 16f
                    )
                },
                PresetFactory.create(
                    name = "Typewriter",
                    fontFamily = "Roboto",
                    fontWeight = 700,
                    fontSize = 42f,
                    textColor = 0xFF00FF00,
                    highlightColor = 0xFF00FF00,
                    outlineWidth = 3f,
                    isTypewriter = true
                ),
                // ---- New presets ----
                PresetFactory.create(
                    name = "Hormozi",
                    fontFamily = "Montserrat",
                    fontWeight = 900,
                    fontSize = 52f,
                    highlightColor = 0xFFFFD700,
                    outlineWidth = 5f,
                    isWordByWord = true,
                    positionY = 0.82f
                ) {
                    it.copy(
                        wordEnterAnimation = AnimationType.SCALE_POP,
                        textTransform = TextTransform.UPPERCASE
                    )
                },
                PresetFactory.create(
                    name = "Neon Glow",
                    fontFamily = "Bebas Neue",
                    fontWeight = 900,
                    fontSize = 54f,
                    textColor = 0xFF00FFFF,
                    highlightColor = 0xFFFF69B4,
                    outlineColor = 0xFFFF00FF,
                    outlineWidth = 2f,
                    isWordByWord = true,
                    positionY = 0.85f
                ) {
                    it.copy(
                        secondaryColor = 0xFF00CED1,
                        wordEnterAnimation = AnimationType.BOUNCE,
                        glowEnabled = true,
                        glowColor = 0xFF00FFFF,
                        glowRadius = 12f
                    )
                },
                PresetFactory.create(
                    name = "Story Time",
                    fontFamily = "Pacifico",
                    fontWeight = 400,
                    fontSize = 44f,
                    textColor = 0xFFF5F5DC,
                    highlightColor = 0xFFFFD700,
                    outlineColor = 0xFF8B4513,
                    outlineWidth = 2f,
                    isWordByWord = true,
                    positionY = 0.80f
                ),
                PresetFactory.create(
                    name = "Tech Terminal",
                    fontFamily = "Space Mono",
                    fontWeight = 400,
                    fontSize = 38f,
                    textColor = 0xFF00FF41,
                    highlightColor = 0xFFFFFF00,
                    outlineColor = 0xFF003300,
                    outlineWidth = 2f,
                    hasBg = true,
                    backgroundColor = 0xDD0A0A0A,
                    isTypewriter = true,
                    positionY = 0.88f
                ) {
                    it.copy(
                        backgroundType = BackgroundType.FULL_LINE,
                        backgroundOpacity = 0.85f,
                        textTransform = TextTransform.UPPERCASE
                    )
                },
                PresetFactory.create(
                    name = "Elegant",
                    fontFamily = "Playfair Display",
                    fontWeight = 700,
                    fontSize = 40f,
                    textColor = 0xFFD4AF37,
                    highlightColor = 0xFFFFD700,
                    outlineColor = 0xFF1A0A00,
                    outlineWidth = 3f,
                    positionY = 0.88f
                ) {
                    it.copy(
                        letterSpacing = 0.03f,
                        secondaryColor = 0xFFF5E6B8,
                        shadowColor = 0x40000000,
                        shadowRadius = 4f,
                        shadowOffsetX = 1f,
                        shadowOffsetY = 1f,
                        gradientDirection = GradientDirection.LEFT_RIGHT
                    )
                },
                PresetFactory.create(
                    name = "Bold Pop",
                    fontFamily = "Montserrat",
                    fontWeight = 900,
                    fontSize = 50f,
                    highlightColor = 0xFFFFC107,
                    outlineWidth = 4f,
                    isWordByWord = true,
                    positionY = 0.82f
                ) {
                    it.copy(
                        wordEnterAnimation = AnimationType.SCALE_POP
                    )
                },
                PresetFactory.create(
                    name = "Retro Sign",
                    fontFamily = "Bungee",
                    fontWeight = 400,
                    fontSize = 46f,
                    textColor = 0xFFFF4500,
                    highlightColor = 0xFFFFD700,
                    outlineColor = 0xFFFF4500,
                    outlineWidth = 3f,
                    positionY = 0.85f
                ) {
                    it.copy(
                        outlineOnly = true,
                        maxWordsPerLine = 5,
                        glowEnabled = true,
                        glowColor = 0xFFFF4500,
                        glowRadius = 10f
                    )
                },
                PresetFactory.create(
                    name = "Smooth Gradient",
                    fontFamily = "Rubik",
                    fontWeight = 500,
                    fontSize = 44f,
                    textColor = 0xFF6A11CB,
                    highlightColor = 0xFFFFD700,
                    outlineColor = 0x00000000,
                    outlineWidth = 0f,
                    isWordByWord = true,
                    positionY = 0.83f
                ) {
                    it.copy(
                        secondaryColor = 0xFF2575FC,
                        gradientDirection = GradientDirection.DIAGONAL
                    )
                }
            )

            // Seed any presets not yet in the DB (IGNORE strategy — safe to call repeatedly)
            val existingNames = styleDao.getDefaultStyleNames().toSet()
            val newDefaults = defaults.filter { it.name !in existingNames }
            if (newDefaults.isNotEmpty()) {
                styleDao.insertDefaultStyles(newDefaults)
                Log.i(TAG, "Seeded ${newDefaults.size} new default styles (skipped ${defaults.size - newDefaults.size} existing)")
            } else {
                Log.i(TAG, "All ${defaults.size} default styles already present")
            }

            // ── Patch already-seeded rows ─────────────────────────────────────
            // Existing installs have old rows with outdated preset values.
            // Patch all fields on default presets so they match the latest
            // definitions. User-customised styles (isDefault=false) are untouched.
            defaults.forEach { preset ->
                styleDao.patchDefaultStylePreset(
                    name            = preset.name,
                    fontFamily      = preset.fontFamily,
                    fontWeight      = preset.fontWeight,
                    textTransform   = preset.textTransform,
                    outlineColor    = preset.outlineColor,
                    outlineWidth    = preset.outlineWidth,
                    glowEnabled     = preset.glowEnabled,
                    glowColor       = preset.glowColor,
                    glowRadius      = preset.glowRadius,
                    maxWordsPerLine = preset.maxWordsPerLine,
                    maxLines        = preset.maxLines,
                    enterAnim       = preset.wordEnterAnimation,
                    exitAnim        = preset.wordExitAnimation,
                )
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

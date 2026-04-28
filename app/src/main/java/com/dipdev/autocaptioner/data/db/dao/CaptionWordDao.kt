package com.dipdev.autocaptioner.data.db.dao

import androidx.room.*
import com.dipdev.autocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.autocaptioner.data.db.entity.EmphasisType
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptionWordDao {

    // Get all words for a specific segment, in order
    // Used in the caption editor to show individual words
    @Query("SELECT * FROM caption_words WHERE segmentId = :segmentId ORDER BY `index` ASC")
    fun getWordsForSegment(segmentId: String): Flow<List<CaptionWordEntity>>

    // Get ALL words for an entire project, ordered by time
    // This is the critical query for the preview screen
    // The preview needs to find the active word at the current playback time
    // Having all words pre-loaded sorted by time makes this instant
    @Query("SELECT * FROM caption_words WHERE projectId = :projectId ORDER BY startTimeMs ASC")
    suspend fun getAllWordsForProject(projectId: String): List<CaptionWordEntity>

    // Find the specific word that should be highlighted at a given timestamp
    // Used during preview to find the active karaoke word
    // Example: at time=1250ms, find the word where 1000ms <= start <= 1250ms <= end
    @Query("""
        SELECT * FROM caption_words 
        WHERE projectId = :projectId 
        AND startTimeMs <= :currentTimeMs 
        AND endTimeMs >= :currentTimeMs 
        LIMIT 1
    """)
    suspend fun getActiveWordAtTime(projectId: String, currentTimeMs: Long): CaptionWordEntity?

    // Insert all words from transcription at once
    // Whisper returns hundreds of words — batch insert is much faster
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(words: List<CaptionWordEntity>)

    // Update emphasis settings on a word
    // Called when user long-presses a word in the caption editor
    @Query("""
        UPDATE caption_words 
        SET isEmphasized = :isEmphasized, emphasisType = :emphasisType 
        WHERE id = :wordId
    """)
    suspend fun updateEmphasis(
        wordId: String,
        isEmphasized: Boolean,
        emphasisType: EmphasisType
    )

    // Delete all words for a project (used when re-transcribing)
    @Query("DELETE FROM caption_words WHERE projectId = :projectId")
    suspend fun deleteWordsForProject(projectId: String)

    // Delete all words for a segment (used when restructuring edited text)
    @Query("DELETE FROM caption_words WHERE segmentId = :segmentId")
    suspend fun deleteWordsForSegment(segmentId: String)
}
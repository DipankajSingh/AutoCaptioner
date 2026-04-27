package com.dipdev.autocaptioner.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Words belong to segments — cascade delete applies here too
// If a segment is deleted, all its words are deleted automatically
@Entity(
    tableName = "caption_words",
    foreignKeys = [
        ForeignKey(
            entity = CaptionSegmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["segmentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    // Index on both segmentId and projectId
    // segmentId → fast lookup of all words in a segment
    // projectId → fast lookup of ALL words in a project (used during preview)
    indices = [
        Index("segmentId"),
        Index("projectId")
    ]
)
data class CaptionWordEntity(

    @PrimaryKey
    val id: String,

    // Which segment this word belongs to
    val segmentId: String,

    // We also store projectId directly on each word
    // This lets us query "all words for project X" without joining tables
    // Slightly denormalized but much faster for the preview screen
    // which needs to find the active word at 60fps
    val projectId: String,

    // Position of this word within its segment (0, 1, 2...)
    val index: Int,

    // The actual word text
    // Example: "Hello", "this", "is", "test"
    val word: String,

    // Word-level timestamps — this is the KEY feature for karaoke captions
    // These come directly from Whisper's token_timestamps feature
    // Example: startTimeMs=1000, endTimeMs=1250 means
    // the word "Hello" is spoken from 1.0s to 1.25s
    // This is what lets us highlight exactly the right word at the right time
    val startTimeMs: Long,
    val endTimeMs: Long,

    // How confident Whisper is that this word is correct (0.0 to 1.0)
    // Low confidence words can be highlighted differently in the editor
    // to hint to the user they might need correction
    // Example: 0.95 = very confident, 0.40 = uncertain
    val confidence: Float = 1.0f,

    // Whether the user marked this word for special emphasis animation
    // Example: a loud word, an important word, a funny word
    val isEmphasized: Boolean = false,

    // What kind of emphasis animation to apply to this word
    val emphasisType: EmphasisType = EmphasisType.NONE
)

// Types of emphasis animations available per word
enum class EmphasisType {
    NONE,       // no special animation
    BOUNCE,     // word bounces up and down when spoken
    SCALE,      // word grows bigger when spoken
    SHAKE,      // word shakes/vibrates when spoken
    COLOR_POP   // word flashes a different color when spoken
}
package com.dipdev.aiautocaptioner.data.db.dao

import androidx.room.*
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptionStyleDao {

    // Get all styles — shown in the style picker horizontal list
    // Flow = updates automatically when user saves a new style
    @Query("SELECT * FROM caption_styles ORDER BY isDefault DESC, name ASC")
    fun getAllStyles(): Flow<List<CaptionStyleEntity>>
    // ORDER BY isDefault DESC = default/preset styles appear first
    // then alphabetical by name

    // Get a specific style by id
    // Used when loading the project's active style
    @Query("SELECT * FROM caption_styles WHERE id = :styleId")
    suspend fun getStyleById(styleId: String): CaptionStyleEntity?

    // Save a new style or update existing one
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStyle(style: CaptionStyleEntity)

    // Delete a user's custom style
    // Default styles (isDefault=true) should never be deleted
    // We enforce this in the repository layer not here
    @Delete
    suspend fun deleteStyle(style: CaptionStyleEntity)

    // Insert all default preset styles at once
    // Called on first app launch from the database callback
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    // IGNORE = don't overwrite if already exists
    // This means re-running this won't reset user's customized presets
    suspend fun insertDefaultStyles(styles: List<CaptionStyleEntity>)

    // Check if default styles have been inserted yet
    @Query("SELECT COUNT(*) FROM caption_styles WHERE isDefault = 1")
    suspend fun getDefaultStyleCount(): Int

    // Get names of all default styles — used to skip already-seeded presets
    @Query("SELECT name FROM caption_styles WHERE isDefault = 1")
    suspend fun getDefaultStyleNames(): List<String>

    /**
     * Patch layout-critical fields on an existing default style identified by name.
     * Called during [CaptionRepository.initializeDefaultStyles] so that already-seeded
     * rows are updated to the latest preset values without resetting any fields the
     * user may have customised (font, color, position, etc.).
     */
    @Query("""
        UPDATE caption_styles
        SET maxWordsPerLine = :maxWordsPerLine,
            maxLines        = :maxLines
        WHERE name = :name AND isDefault = 1
    """)
    suspend fun patchDefaultStyleLayout(name: String, maxWordsPerLine: Int, maxLines: Int)
}
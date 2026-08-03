package com.dipdev.aiautocaptioner.data.db.dao

import androidx.room.*
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CaptionStyleDao {

    // Get all styles — shown in the style picker horizontal list
    // Flow = updates automatically when user saves a new style
    // ORDER BY: presets first (isDefault DESC), then by their explicit sort position,
    // then alphabetically within the same rank. User-created styles (sortOrder=999) fall last.
    @Query("SELECT * FROM caption_styles ORDER BY isDefault DESC, sortOrder ASC, name ASC")
    fun getAllStyles(): Flow<List<CaptionStyleEntity>>

    @Query("SELECT * FROM caption_styles ORDER BY isDefault DESC, sortOrder ASC, name ASC LIMIT 1")
    suspend fun getFirstStyle(): CaptionStyleEntity?

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

    // Insert/update all default preset styles at once.
    // Uses UPSERT (in-place ON CONFLICT DO UPDATE), NOT REPLACE: REPLACE is a
    // DELETE + INSERT, which fires the projects.activeStyleId ON DELETE SET NULL
    // foreign key and silently nulls every project's active style on seeding.
    // Safe to run on every launch: presets are immutable in the UI (editing a preset
    // always produces a new custom/draft row), so the upsert never clobbers user work,
    // and the onOpen unique index guarantees one row per preset name.
    @Upsert
    suspend fun upsertDefaultStyles(styles: List<CaptionStyleEntity>)

    // Remove any previously seeded preset styles that are no longer in the system definitions
    @Query("DELETE FROM caption_styles WHERE isDefault = 1 AND name NOT IN (:retainedNames)")
    suspend fun removeDeprecatedDefaultStyles(retainedNames: List<String>)
}
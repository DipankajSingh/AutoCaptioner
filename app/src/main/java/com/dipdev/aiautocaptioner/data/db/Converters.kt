package com.dipdev.aiautocaptioner.data.db

import androidx.room.TypeConverter
import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment

// TypeConverters tell Room how to convert types it can't store natively
// Room can store: String, Int, Long, Float, Double, Boolean, ByteArray
// Room CANNOT store: Enums, Lists, custom objects
// Solution: convert them TO a storable type when saving,
//           convert them BACK when reading

// For enums we store them as their String name
// Example: ProjectStatus.TRANSCRIBED is stored as "TRANSCRIBED" in the database
// This is human-readable in the database and survives app updates safely

class Converters {

    // ---- ProjectStatus ----
    // @TypeConverter tells Room: "use this function to convert this type"

    @TypeConverter
    fun fromProjectStatus(status: ProjectStatus): String = status.name
    // .name returns the enum constant's name as a String
    // ProjectStatus.IMPORTED.name = "IMPORTED"

    @TypeConverter
    fun toProjectStatus(value: String): ProjectStatus =
        ProjectStatus.valueOf(value)
    // valueOf() converts String back to enum constant
    // "IMPORTED" → ProjectStatus.IMPORTED

    // ---- DisplayMode ----
    @TypeConverter
    fun fromDisplayMode(mode: DisplayMode): String = mode.name

    @TypeConverter
    fun toDisplayMode(value: String): DisplayMode = DisplayMode.valueOf(value)

    // ---- AnimationType ----
    @TypeConverter
    fun fromAnimationType(type: AnimationType): String = type.name

    @TypeConverter
    fun toAnimationType(value: String): AnimationType = AnimationType.valueOf(value)

    // ---- BackgroundType ----
    @TypeConverter
    fun fromBackgroundType(type: BackgroundType): String = type.name

    @TypeConverter
    fun toBackgroundType(value: String): BackgroundType = BackgroundType.valueOf(value)

    // ---- KaraokeHighlightMode ----
    @TypeConverter
    fun fromKaraokeHighlightMode(mode: KaraokeHighlightMode): String = mode.name

    @TypeConverter
    fun toKaraokeHighlightMode(value: String): KaraokeHighlightMode =
        KaraokeHighlightMode.valueOf(value)

    // ---- TextAlignment ----
    @TypeConverter
    fun fromTextAlignment(alignment: TextAlignment): String = alignment.name

    @TypeConverter
    fun toTextAlignment(value: String): TextAlignment = TextAlignment.valueOf(value)

    // ---- EmphasisType ----
    @TypeConverter
    fun fromEmphasisType(type: EmphasisType): String = type.name

    @TypeConverter
    fun toEmphasisType(value: String): EmphasisType = EmphasisType.valueOf(value)
}
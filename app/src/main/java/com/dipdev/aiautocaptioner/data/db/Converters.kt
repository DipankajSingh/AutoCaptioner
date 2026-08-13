package com.dipdev.aiautocaptioner.data.db

import androidx.room.TypeConverter
import com.dipdev.aiautocaptioner.data.db.entity.AnimationType
import com.dipdev.aiautocaptioner.data.db.entity.BackgroundType
import com.dipdev.aiautocaptioner.data.db.entity.DisplayMode
import com.dipdev.aiautocaptioner.data.db.entity.EmphasisType
import com.dipdev.aiautocaptioner.data.db.entity.KaraokeHighlightMode
import com.dipdev.aiautocaptioner.data.db.entity.CreationMode
import com.dipdev.aiautocaptioner.data.db.entity.ProjectStatus
import com.dipdev.aiautocaptioner.data.db.entity.TextAlignment
import com.dipdev.aiautocaptioner.data.db.entity.TextTransform
import com.dipdev.aiautocaptioner.data.db.entity.GradientDirection


class Converters {

    @TypeConverter
    fun fromProjectStatus(status: ProjectStatus): String = status.name

    @TypeConverter
    fun toProjectStatus(value: String): ProjectStatus =
        ProjectStatus.valueOf(value)

    @TypeConverter
    fun fromCreationMode(mode: CreationMode): String = mode.name

    @TypeConverter
    fun toCreationMode(value: String): CreationMode = CreationMode.valueOf(value)

    @TypeConverter
    fun fromDisplayMode(mode: DisplayMode): String = mode.name

    @TypeConverter
    fun toDisplayMode(value: String): DisplayMode = DisplayMode.valueOf(value)

    @TypeConverter
    fun fromAnimationType(type: AnimationType): String = type.name

    @TypeConverter
    fun toAnimationType(value: String): AnimationType = AnimationType.valueOf(value)

    @TypeConverter
    fun fromBackgroundType(type: BackgroundType): String = type.name

    @TypeConverter
    fun toBackgroundType(value: String): BackgroundType = BackgroundType.valueOf(value)

    @TypeConverter
    fun fromKaraokeHighlightMode(mode: KaraokeHighlightMode): String = mode.name

    @TypeConverter
    fun toKaraokeHighlightMode(value: String): KaraokeHighlightMode =
        KaraokeHighlightMode.valueOf(value)

    @TypeConverter
    fun fromTextAlignment(alignment: TextAlignment): String = alignment.name

    @TypeConverter
    fun toTextAlignment(value: String): TextAlignment = TextAlignment.valueOf(value)

    @TypeConverter
    fun fromEmphasisType(type: EmphasisType): String = type.name

    @TypeConverter
    fun toEmphasisType(value: String): EmphasisType = EmphasisType.valueOf(value)

    @TypeConverter
    fun fromTextTransform(transform: TextTransform): String = transform.name

    @TypeConverter
    fun toTextTransform(value: String): TextTransform = TextTransform.valueOf(value)

    @TypeConverter
    fun fromGradientDirection(dir: GradientDirection): String = dir.name

    @TypeConverter
    fun toGradientDirection(value: String): GradientDirection = GradientDirection.valueOf(value)
}
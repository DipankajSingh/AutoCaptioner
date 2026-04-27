package com.dipdev.autocaptioner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.dipdev.autocaptioner.data.db.dao.CaptionSegmentDao
import com.dipdev.autocaptioner.data.db.dao.CaptionStyleDao
import com.dipdev.autocaptioner.data.db.dao.CaptionWordDao
import com.dipdev.autocaptioner.data.db.dao.ProjectDao
import com.dipdev.autocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.autocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.autocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.autocaptioner.data.db.entity.ProjectEntity

// @Database tells Room: "this class is the main database definition"
// entities = all tables in this database
// version = database schema version
//   → increment this number whenever you change table structure
//   → Room uses this to run migration scripts
// exportSchema = saves schema to a JSON file for version control
//   → set false for now to keep things simple
@Database(
    entities = [
        ProjectEntity::class,
        CaptionSegmentEntity::class,
        CaptionWordEntity::class,
        CaptionStyleEntity::class
    ],
    version = 3,
    exportSchema = false,
    autoMigrations = []

)

// TypeConverters tells Room how to store types it doesn't understand natively
// Room natively supports: String, Int, Long, Float, Double, Boolean, ByteArray
// For Enums we need a TypeConverter to tell Room: "store this enum as a String"
@TypeConverters(Converters::class)

// abstract class because Room generates the actual implementation
// We never instantiate this directly — Room.databaseBuilder() does it
abstract class AppDatabase : RoomDatabase() {


    // Each of these abstract functions returns a DAO
    // Room generates the implementations automatically
    // These match exactly what DatabaseModule.kt calls
    abstract fun projectDao(): ProjectDao
    abstract fun captionSegmentDao(): CaptionSegmentDao
    abstract fun captionWordDao(): CaptionWordDao
    abstract fun captionStyleDao(): CaptionStyleDao
}
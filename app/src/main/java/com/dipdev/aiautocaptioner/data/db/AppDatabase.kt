package com.dipdev.aiautocaptioner.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.dipdev.aiautocaptioner.data.db.dao.CaptionSegmentDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionStyleDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionWordDao
import com.dipdev.aiautocaptioner.data.db.dao.ProjectDao
import com.dipdev.aiautocaptioner.data.db.entity.CaptionSegmentEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionStyleEntity
import com.dipdev.aiautocaptioner.data.db.entity.CaptionWordEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity

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
    version = 6,
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

    abstract fun projectDao(): ProjectDao
    abstract fun captionSegmentDao(): CaptionSegmentDao
    abstract fun captionWordDao(): CaptionWordDao
    abstract fun captionStyleDao(): CaptionStyleDao

    companion object {
        /** Add exportedVideoPath column (nullable, default NULL) */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN exportedVideoPath TEXT")
            }
        }

        /** Add transcriptionLanguage column (nullable TEXT, default 'en') */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN transcriptionLanguage TEXT DEFAULT 'en'")
            }
        }
    }
}
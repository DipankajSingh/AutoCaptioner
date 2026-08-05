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
import com.dipdev.aiautocaptioner.data.db.entity.ExportedFileEntity
import com.dipdev.aiautocaptioner.data.db.entity.ProjectEntity
import com.dipdev.aiautocaptioner.data.db.dao.ExportedFileDao
import com.dipdev.aiautocaptioner.data.db.dao.ImageOverlayDao
import com.dipdev.aiautocaptioner.data.db.entity.ImageOverlayEntity

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
        CaptionStyleEntity::class,
        ExportedFileEntity::class,
        ImageOverlayEntity::class
    ],
    version = 20,
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
    abstract fun exportedFileDao(): ExportedFileDao
    abstract fun imageOverlayDao(): ImageOverlayDao

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

        /** Add transcribedWithModelId and exported_files table */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN transcribedWithModelId TEXT")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `exported_files` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `videoFilePath` TEXT,
                        `srtFilePath` TEXT,
                        `exportedAt` INTEGER NOT NULL,
                        `quality` TEXT,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_exported_files_projectId` ON `exported_files` (`projectId`)")
            }
        }

        /** Add foreign key for activeStyleId */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `projects_new` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `originalVideoUri` TEXT NOT NULL, 
                        `workingVideoPath` TEXT NOT NULL, 
                        `audioPath` TEXT, 
                        `thumbnailPath` TEXT, 
                        `videoDurationMs` INTEGER NOT NULL, 
                        `videoWidth` INTEGER NOT NULL, 
                        `videoHeight` INTEGER NOT NULL, 
                        `videoRotation` INTEGER NOT NULL, 
                        `videoFps` REAL NOT NULL, 
                        `status` TEXT NOT NULL, 
                        `activeStyleId` TEXT, 
                        `hasVisitedCaptionEditor` INTEGER NOT NULL, 
                        `createdAt` INTEGER NOT NULL, 
                        `updatedAt` INTEGER NOT NULL, 
                        `exportedVideoPath` TEXT, 
                        `transcriptionLanguage` TEXT DEFAULT 'en', 
                        `transcribedWithModelId` TEXT, 
                        PRIMARY KEY(`id`), 
                        FOREIGN KEY(`activeStyleId`) REFERENCES `caption_styles`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                """.trimIndent())
                
                db.execSQL("""
                    INSERT INTO `projects_new` (`id`, `title`, `originalVideoUri`, `workingVideoPath`, `audioPath`, `thumbnailPath`, `videoDurationMs`, `videoWidth`, `videoHeight`, `videoRotation`, `videoFps`, `status`, `activeStyleId`, `hasVisitedCaptionEditor`, `createdAt`, `updatedAt`, `exportedVideoPath`, `transcriptionLanguage`, `transcribedWithModelId`)
                    SELECT `id`, `title`, `originalVideoUri`, `workingVideoPath`, `audioPath`, `thumbnailPath`, `videoDurationMs`, `videoWidth`, `videoHeight`, `videoRotation`, `videoFps`, `status`, `activeStyleId`, `hasVisitedCaptionEditor`, `createdAt`, `updatedAt`, `exportedVideoPath`, `transcriptionLanguage`, `transcribedWithModelId` FROM `projects`
                """.trimIndent())
                
                db.execSQL("DROP TABLE `projects`")
                db.execSQL("ALTER TABLE `projects_new` RENAME TO `projects`")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_projects_activeStyleId` ON `projects` (`activeStyleId`)")
            }
        }

        /** Add facelessBackgroundType and facelessBackgroundValue columns */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN facelessBackgroundType TEXT")
                db.execSQL("ALTER TABLE projects ADD COLUMN facelessBackgroundValue TEXT")
            }
        }

        /** Add image_overlays table */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `image_overlays` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `imageUri` TEXT NOT NULL,
                        `positionX` REAL NOT NULL,
                        `positionY` REAL NOT NULL,
                        `scaleX` REAL NOT NULL,
                        `scaleY` REAL NOT NULL,
                        `startTimeMs` INTEGER NOT NULL,
                        `endTimeMs` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_image_overlays_projectId` ON `image_overlays` (`projectId`)")
            }
        }

        /** Add zOrder to image_overlays */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE image_overlays ADD COLUMN zOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Add creationMode to projects */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN creationMode TEXT NOT NULL DEFAULT 'ADVANCED'")
            }
        }

        /** Add naturalWidth and naturalHeight to image_overlays */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE image_overlays ADD COLUMN naturalWidth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE image_overlays ADD COLUMN naturalHeight INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Add textOpacity, textTransform, lineHeight, outlineOnly to caption_styles */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN textTransform TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN lineHeight REAL NOT NULL DEFAULT 1.2")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN textOpacity REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN outlineOnly INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Add gradientDirection, glowEnabled, glowColor, glowRadius to caption_styles */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN gradientDirection TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN glowEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN glowColor INTEGER NOT NULL DEFAULT 4294967295")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN glowRadius REAL NOT NULL DEFAULT 0.0")
            }
        }

        /** Add initialPrompt to projects */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN initialPrompt TEXT")
            }
        }

        /** Add activeWordBgColor, activeWordTextColor, and activeWordCornerRadius to caption_styles */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 0xFFFFC107 = 4294951175
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN activeWordBgColor INTEGER NOT NULL DEFAULT 4294951175")
                // 0xFF000000 = 4278190080
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN activeWordTextColor INTEGER NOT NULL DEFAULT 4278190080")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN activeWordCornerRadius REAL NOT NULL DEFAULT 100.0")
            }
        }

        /**
         * Deduplicate preset rows left over from before presets had stable IDs.
         *
         * Pre-fa59a9c versions seeded every preset with a fresh UUID.id, so
         * upgrading devices end up with two rows per preset (UUID id + stable id).
         * Remap project references to the stable row and drop the legacy rows.
         * (The uniqueness guard for preset names is applied in onOpen — Room
         * can't validate a partial index it doesn't know about.)
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Point projects at the stable preset row for their current style
                //    (id = "preset_" + lower(name) with spaces -> underscores). Only
                //    touched when a default row whose name has a stable twin exists,
                //    so custom styles and removed presets are never orphaned.
                db.execSQL("""
                    UPDATE `projects`
                    SET `activeStyleId` = (
                        SELECT 'preset_' || lower(replace(s.name, ' ', '_'))
                        FROM `caption_styles` s
                        WHERE s.isDefault = 1 AND s.id = `projects`.`activeStyleId`
                        LIMIT 1
                    )
                    WHERE `activeStyleId` IS NOT NULL
                      AND EXISTS (
                        SELECT 1
                        FROM `caption_styles` s
                        WHERE s.isDefault = 1 AND s.id = `projects`.`activeStyleId`
                          AND EXISTS (
                            SELECT 1 FROM `caption_styles` s2
                            WHERE s2.isDefault = 1
                              AND s2.id = 'preset_' || lower(replace(s.name, ' ', '_'))
                          )
                      )
                """.trimIndent())

                // 2. Drop legacy UUID rows that now have a stable twin.
                db.execSQL("""
                    DELETE FROM `caption_styles`
                    WHERE `isDefault` = 1
                      AND `id` <> 'preset_' || lower(replace(`name`, ' ', '_'))
                      AND EXISTS (
                        SELECT 1 FROM `caption_styles` s2
                        WHERE s2.isDefault = 1
                          AND s2.id = 'preset_' || lower(replace(`caption_styles`.`name`, ' ', '_'))
                      )
                """.trimIndent())
            }
        }
        /**
         * Adds sortOrder column to caption_styles for explicit preset ordering in the UI strip.
         *
         * Existing user-created styles default to 999, putting them after all built-in presets.
         * Built-in presets receive their correct sortOrder on the next launch via the standard
         * UPSERT seeding path in CaptionRepository.initializeDefaultStyles().
         *
         * IMPORTANT: We also drop `index_caption_styles_default_name` here.
         * That index is a partial WHERE index (WHERE isDefault = 1) that Room cannot represent
         * in @Entity annotations, so it is created in DatabaseModule.onOpen() instead.
         * Room's schema validator runs AFTER migration but BEFORE onOpen, and compares the
         * live table schema against its compiled expected schema (which has zero indices for
         * caption_styles). If the index exists on the device from a previous session it causes
         * "Migration didn't properly handle" even though the migration itself is correct.
         * Dropping it here lets the validator pass; onOpen() recreates it immediately after.
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Drop the partial unique index — onOpen() will recreate it after validation
                db.execSQL("DROP INDEX IF EXISTS `index_caption_styles_default_name`")
                // Add the new sortOrder column (DEFAULT 999 matches @ColumnInfo(defaultValue="999"))
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 999")
            }
        }

        /**
         * Adds textThickness column to caption_styles for synthetic emboldening.
         *
         * DEFAULT 0.0 matches @ColumnInfo(defaultValue="0.0") in CaptionStyleEntity
         * so Room's schema validator does not raise "Migration didn't properly handle".
         * Existing rows (and presets) keep 0.0 — no visual change until the user raises it.
         *
         * IMPORTANT: Also drop `index_caption_styles_default_name` like MIGRATION_18_19.
         * onOpen() recreates the partial unique index after validation, so it exists on
         * devices already running v19. Room's validator runs right after this migration
         * and compares the live schema against its compiled expected schema (zero indices
         * for caption_styles) — a leftover index fails validation with
         * "Migration didn't properly handle" even though the ALTER is correct.
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_caption_styles_default_name`")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN textThickness REAL NOT NULL DEFAULT 0.0")
            }
        }
    }
}
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
import com.dipdev.aiautocaptioner.data.db.entity.TextOverlayEntity
import com.dipdev.aiautocaptioner.data.db.dao.TextOverlayDao


@Database(
    entities = [
        ProjectEntity::class,
        CaptionSegmentEntity::class,
        CaptionWordEntity::class,
        CaptionStyleEntity::class,
        ExportedFileEntity::class,
        ImageOverlayEntity::class,
        TextOverlayEntity::class
    ],
    version = 24,
    exportSchema = false,
    autoMigrations = []
)

@TypeConverters(Converters::class)

abstract class AppDatabase : RoomDatabase() {

    abstract fun projectDao(): ProjectDao
    abstract fun captionSegmentDao(): CaptionSegmentDao
    abstract fun captionWordDao(): CaptionWordDao
    abstract fun captionStyleDao(): CaptionStyleDao
    abstract fun exportedFileDao(): ExportedFileDao
    abstract fun imageOverlayDao(): ImageOverlayDao
    abstract fun textOverlayDao(): TextOverlayDao

    companion object {
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN exportedVideoPath TEXT")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN transcriptionLanguage TEXT DEFAULT 'en'")
            }
        }

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

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN facelessBackgroundType TEXT")
                db.execSQL("ALTER TABLE projects ADD COLUMN facelessBackgroundValue TEXT")
            }
        }

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

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE image_overlays ADD COLUMN zOrder INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN creationMode TEXT NOT NULL DEFAULT 'ADVANCED'")
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE image_overlays ADD COLUMN naturalWidth INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE image_overlays ADD COLUMN naturalHeight INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN textTransform TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN lineHeight REAL NOT NULL DEFAULT 1.2")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN textOpacity REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN outlineOnly INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN gradientDirection TEXT NOT NULL DEFAULT 'NONE'")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN glowEnabled INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN glowColor INTEGER NOT NULL DEFAULT 4294967295")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN glowRadius REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE projects ADD COLUMN initialPrompt TEXT")
            }
        }

        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN activeWordBgColor INTEGER NOT NULL DEFAULT 4294951175")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN activeWordTextColor INTEGER NOT NULL DEFAULT 4278190080")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN activeWordCornerRadius REAL NOT NULL DEFAULT 100.0")
            }
        }


        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {

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

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_caption_styles_default_name`")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 999")
            }
        }

        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_caption_styles_default_name`")
                db.execSQL("ALTER TABLE caption_styles ADD COLUMN textThickness REAL NOT NULL DEFAULT 0.0")
            }
        }

        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL("DROP INDEX IF EXISTS `index_caption_styles_default_name`")

                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `text_overlays` (
                        `id` TEXT NOT NULL,
                        `projectId` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `fontAssetPath` TEXT NOT NULL,
                        `textColorArgb` INTEGER NOT NULL,
                        `backgroundColorArgb` INTEGER NOT NULL,
                        `backgroundOpacity` REAL NOT NULL,
                        `textAlignment` TEXT NOT NULL,
                        `fontSize` REAL NOT NULL,
                        `positionX` REAL NOT NULL,
                        `positionY` REAL NOT NULL,
                        `scaleX` REAL NOT NULL,
                        `scaleY` REAL NOT NULL,
                        `rotation` REAL NOT NULL,
                        `startTimeMs` INTEGER NOT NULL,
                        `endTimeMs` INTEGER NOT NULL,
                        `zOrder` INTEGER NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`projectId`) REFERENCES `projects`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_text_overlays_projectId` ON `text_overlays` (`projectId`)")
            }
        }

        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_caption_styles_default_name`")

                db.execSQL("ALTER TABLE image_overlays ADD COLUMN opacity REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE image_overlays ADD COLUMN filterName TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE image_overlays ADD COLUMN isFlippedX INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_caption_styles_default_name`")

                db.execSQL("ALTER TABLE text_overlays ADD COLUMN backgroundStyle TEXT NOT NULL DEFAULT 'NONE'")
            }
        }

        val MIGRATION_23_24 = object : Migration(23, 24) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_caption_styles_default_name`")
                db.execSQL("ALTER TABLE text_overlays ADD COLUMN textWidth REAL DEFAULT NULL")
            }
        }
    }
}
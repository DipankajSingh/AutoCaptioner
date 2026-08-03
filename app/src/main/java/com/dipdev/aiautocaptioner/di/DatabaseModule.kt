package com.dipdev.aiautocaptioner.di

import android.content.Context
import androidx.room.Room
import com.dipdev.aiautocaptioner.data.db.AppDatabase
import com.dipdev.aiautocaptioner.data.db.dao.CaptionSegmentDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionStyleDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionWordDao
import com.dipdev.aiautocaptioner.data.db.dao.ExportedFileDao
import com.dipdev.aiautocaptioner.data.db.dao.ProjectDao
import com.dipdev.aiautocaptioner.data.db.dao.ImageOverlayDao
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "aiautocaptioner.db"
    ).addCallback(object : RoomDatabase.Callback() {
        override fun onOpen(db: SupportSQLiteDatabase) {
            super.onOpen(db)
            if (!db.isReadOnly) {
                db.execSQL("PRAGMA foreign_keys = ON;")
                // Schema-level invariant: two default rows can never share a name.
                // Partial index (isDefault = 1) so user styles may still reuse a
                // preset name. Created here, not in a migration, because Room's
                // schema validation can't anticipate a partial index.
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_caption_styles_default_name` " +
                        "ON `caption_styles` (`name`) WHERE `isDefault` = 1"
                )
            }
        }
    }).addMigrations(
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7,
        AppDatabase.MIGRATION_7_8,
        AppDatabase.MIGRATION_8_9,
        AppDatabase.MIGRATION_9_10,
        AppDatabase.MIGRATION_10_11,
        AppDatabase.MIGRATION_11_12,
        AppDatabase.MIGRATION_12_13,
        AppDatabase.MIGRATION_13_14,
        AppDatabase.MIGRATION_14_15,
        AppDatabase.MIGRATION_15_16,
        AppDatabase.MIGRATION_16_17,
        AppDatabase.MIGRATION_17_18,
        AppDatabase.MIGRATION_18_19
    ).build()

    @Provides
    @Singleton
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    @Singleton
    fun provideCaptionSegmentDao(db: AppDatabase): CaptionSegmentDao = db.captionSegmentDao()

    @Provides
    @Singleton
    fun provideCaptionWordDao(db: AppDatabase): CaptionWordDao = db.captionWordDao()

    @Provides
    @Singleton
    fun provideCaptionStyleDao(db: AppDatabase): CaptionStyleDao = db.captionStyleDao()

    @Provides
    @Singleton
    fun provideExportedFileDao(db: AppDatabase): ExportedFileDao = db.exportedFileDao()

    @Provides
    @Singleton
    fun provideImageOverlayDao(db: AppDatabase): ImageOverlayDao = db.imageOverlayDao()
}
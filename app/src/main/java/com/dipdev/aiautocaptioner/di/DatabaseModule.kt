package com.dipdev.aiautocaptioner.di

import android.content.Context
import androidx.room.Room
import com.dipdev.aiautocaptioner.data.db.AppDatabase
import com.dipdev.aiautocaptioner.data.db.dao.CaptionSegmentDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionStyleDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionWordDao
import com.dipdev.aiautocaptioner.data.db.dao.ExportedFileDao
import com.dipdev.aiautocaptioner.data.db.dao.ProjectDao
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
            }
        }
    }).addMigrations(
        AppDatabase.MIGRATION_4_5,
        AppDatabase.MIGRATION_5_6,
        AppDatabase.MIGRATION_6_7
    ).build()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideCaptionSegmentDao(db: AppDatabase): CaptionSegmentDao = db.captionSegmentDao()

    @Provides
    fun provideCaptionWordDao(db: AppDatabase): CaptionWordDao = db.captionWordDao()

    @Provides
    fun provideCaptionStyleDao(db: AppDatabase): CaptionStyleDao = db.captionStyleDao()

    @Provides
    fun provideExportedFileDao(db: AppDatabase): ExportedFileDao = db.exportedFileDao()
}
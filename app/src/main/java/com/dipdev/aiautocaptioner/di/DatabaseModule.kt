package com.dipdev.aiautocaptioner.di

import android.content.Context
import androidx.room.Room
import com.dipdev.aiautocaptioner.data.db.AppDatabase
import com.dipdev.aiautocaptioner.data.db.dao.CaptionSegmentDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionStyleDao
import com.dipdev.aiautocaptioner.data.db.dao.CaptionWordDao
import com.dipdev.aiautocaptioner.data.db.dao.ProjectDao
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
    ).addMigrations(AppDatabase.MIGRATION_4_5, AppDatabase.MIGRATION_5_6).build()

    @Provides
    fun provideProjectDao(db: AppDatabase): ProjectDao = db.projectDao()

    @Provides
    fun provideCaptionSegmentDao(db: AppDatabase): CaptionSegmentDao = db.captionSegmentDao()

    @Provides
    fun provideCaptionWordDao(db: AppDatabase): CaptionWordDao = db.captionWordDao()

    @Provides
    fun provideCaptionStyleDao(db: AppDatabase): CaptionStyleDao = db.captionStyleDao()
}
package com.den4dr.share2Obsidian.di

import android.content.Context
import androidx.room.Room
import com.den4dr.share2Obsidian.data.db.AppDatabase
import com.den4dr.share2Obsidian.data.db.TemplateDao
import com.den4dr.share2Obsidian.data.repository.TemplateRepository
import com.den4dr.share2Obsidian.data.repository.TemplateRepositoryImpl
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "share2obsidian.db")
            // 【マイグレーション登録】: バージョン昇順で登録する（登録漏れは Room が IllegalStateException を投げる）
            // 🔵 信頼性レベル: TASK-0057 要件定義・制約条件（マイグレーション登録順序制約）に基づく
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .build()

    @Provides
    @Singleton
    fun provideTemplateDao(db: AppDatabase): TemplateDao = db.templateDao()

    @Provides
    @Singleton
    fun provideTemplateRepository(dao: TemplateDao): TemplateRepository =
        TemplateRepositoryImpl(dao)
}

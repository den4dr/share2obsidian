package com.den4dr.share2Obsidian.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.den4dr.share2Obsidian.data.datastore.NoteSettingsRepository
import com.den4dr.share2Obsidian.data.datastore.NoteSettingsRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

private val Context.noteSettingsDataStore: DataStore<Preferences>
    by preferencesDataStore(name = "note_settings")

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {

    @Provides
    @Singleton
    fun provideNoteSettingsRepository(
        @ApplicationContext context: Context,
    ): NoteSettingsRepository = NoteSettingsRepositoryImpl(context.noteSettingsDataStore)
}

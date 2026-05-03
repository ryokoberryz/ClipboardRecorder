package com.example.clipboardrecorder.di

import android.content.Context
import androidx.room.Room
import com.example.clipboardrecorder.data.ClipboardDao
import com.example.clipboardrecorder.data.ClipboardDatabase
import com.example.clipboardrecorder.data.local.SettingsDataStore
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
    fun provideDatabase(@ApplicationContext context: Context): ClipboardDatabase {
        return Room.databaseBuilder(
            context,
            ClipboardDatabase::class.java,
            "clipboard_database"
        ).build()
    }

    @Provides
    fun provideClipboardDao(database: ClipboardDatabase): ClipboardDao {
        return database.clipboardDao()
    }

    @Provides
    @Singleton
    fun provideSettingsDataStore(@ApplicationContext context: Context): SettingsDataStore {
        return SettingsDataStore(context)
    }
}

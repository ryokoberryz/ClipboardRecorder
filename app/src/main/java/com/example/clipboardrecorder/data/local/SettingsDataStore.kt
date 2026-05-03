package com.example.clipboardrecorder.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.clipboardrecorder.data.model.AppSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val MAX_RECORDS_KEY = intPreferencesKey("max_records")
        private val RETENTION_DAYS_KEY = intPreferencesKey("retention_days")
        private val AUTO_CLEANUP_KEY = booleanPreferencesKey("auto_cleanup")
        private val SHOW_TOAST_KEY = booleanPreferencesKey("show_toast")
        private val AUTO_RECORD_KEY = booleanPreferencesKey("auto_record")

        const val DEFAULT_MAX_RECORDS = 100
        const val DEFAULT_RETENTION_DAYS = 30
        const val DEFAULT_AUTO_CLEANUP = true
        const val DEFAULT_SHOW_TOAST = true
        const val DEFAULT_AUTO_RECORD = false
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            maxRecords = preferences[MAX_RECORDS_KEY] ?: DEFAULT_MAX_RECORDS,
            retentionDays = preferences[RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS,
            autoCleanup = preferences[AUTO_CLEANUP_KEY] ?: DEFAULT_AUTO_CLEANUP,
            showToast = preferences[SHOW_TOAST_KEY] ?: DEFAULT_SHOW_TOAST,
            autoRecordEnabled = preferences[AUTO_RECORD_KEY] ?: DEFAULT_AUTO_RECORD
        )
    }

    suspend fun getSettings(): AppSettings {
        return context.dataStore.data.map { preferences ->
            AppSettings(
                maxRecords = preferences[MAX_RECORDS_KEY] ?: DEFAULT_MAX_RECORDS,
                retentionDays = preferences[RETENTION_DAYS_KEY] ?: DEFAULT_RETENTION_DAYS,
                autoCleanup = preferences[AUTO_CLEANUP_KEY] ?: DEFAULT_AUTO_CLEANUP,
                showToast = preferences[SHOW_TOAST_KEY] ?: DEFAULT_SHOW_TOAST,
                autoRecordEnabled = preferences[AUTO_RECORD_KEY] ?: DEFAULT_AUTO_RECORD
            )
        }.first()
    }

    suspend fun updateMaxRecords(maxRecords: Int) {
        context.dataStore.edit { preferences ->
            preferences[MAX_RECORDS_KEY] = maxRecords
        }
    }

    suspend fun updateRetentionDays(days: Int) {
        context.dataStore.edit { preferences ->
            preferences[RETENTION_DAYS_KEY] = days
        }
    }

    suspend fun updateAutoCleanup(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_CLEANUP_KEY] = enabled
        }
    }

    suspend fun updateShowToast(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SHOW_TOAST_KEY] = enabled
        }
    }

    suspend fun updateAutoRecord(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_RECORD_KEY] = enabled
        }
    }
}

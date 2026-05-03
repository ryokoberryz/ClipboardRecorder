package com.example.clipboardrecorder.data

import com.example.clipboardrecorder.data.local.SettingsDataStore
import com.example.clipboardrecorder.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClipboardRepository @Inject constructor(
    private val clipboardDao: ClipboardDao,
    private val settingsDataStore: SettingsDataStore
) {
    val allRecords: Flow<List<ClipboardRecord>> = clipboardDao.getAllRecords()
    
    val settings: Flow<AppSettings> = settingsDataStore.settings

    suspend fun insertRecord(content: String): Long {
        val record = ClipboardRecord(content = content)
        val id = clipboardDao.insertRecord(record)
        cleanupIfNeeded()
        return id
    }

    suspend fun deleteRecord(record: ClipboardRecord) {
        clipboardDao.deleteRecord(record)
    }

    suspend fun deleteRecordById(id: Long) {
        clipboardDao.deleteRecordById(id)
    }

    suspend fun deleteRecordsByIds(ids: List<Long>) {
        clipboardDao.deleteRecordsByIds(ids)
    }

    suspend fun deleteAllRecords() {
        clipboardDao.deleteAllRecords()
    }

    suspend fun getRecordsByIds(ids: List<Long>): List<ClipboardRecord> {
        return clipboardDao.getRecordsByIds(ids)
    }

    suspend fun updateRecord(record: ClipboardRecord) {
        clipboardDao.updateRecord(record)
    }

    private suspend fun cleanupIfNeeded() {
        val settings = settingsDataStore.getSettings()
        
        if (settings.maxRecords > 0) {
            val count = clipboardDao.getRecordCount()
            if (count > settings.maxRecords) {
                val recordsToDelete = count - settings.maxRecords
                clipboardDao.deleteOldestRecords(recordsToDelete)
            }
        }
        
        if (settings.retentionDays > 0) {
            val cutoffTime = System.currentTimeMillis() - (settings.retentionDays * 24 * 60 * 60 * 1000L)
            clipboardDao.deleteOldRecords(cutoffTime)
        }
    }

    suspend fun updateMaxRecords(maxRecords: Int) {
        settingsDataStore.updateMaxRecords(maxRecords)
    }

    suspend fun updateRetentionDays(days: Int) {
        settingsDataStore.updateRetentionDays(days)
    }

    suspend fun updateAutoCleanup(enabled: Boolean) {
        settingsDataStore.updateAutoCleanup(enabled)
    }

    suspend fun updateShowToast(enabled: Boolean) {
        settingsDataStore.updateShowToast(enabled)
    }

    suspend fun updateAutoRecord(enabled: Boolean) {
        settingsDataStore.updateAutoRecord(enabled)
    }
}

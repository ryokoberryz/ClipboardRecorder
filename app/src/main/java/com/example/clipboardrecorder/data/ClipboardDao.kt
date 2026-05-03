package com.example.clipboardrecorder.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ClipboardDao {
    
    @Query("SELECT * FROM clipboard_records ORDER BY timestamp DESC")
    fun getAllRecords(): Flow<List<ClipboardRecord>>
    
    @Query("SELECT * FROM clipboard_records WHERE id = :id")
    suspend fun getRecordById(id: Long): ClipboardRecord?
    
    @Query("SELECT * FROM clipboard_records ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentRecords(limit: Int): Flow<List<ClipboardRecord>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: ClipboardRecord): Long
    
    @Delete
    suspend fun deleteRecord(record: ClipboardRecord)
    
    @Query("DELETE FROM clipboard_records WHERE id = :id")
    suspend fun deleteRecordById(id: Long)
    
    @Query("DELETE FROM clipboard_records WHERE id IN (SELECT id FROM clipboard_records ORDER BY timestamp ASC LIMIT :limit)")
    suspend fun deleteOldestRecords(limit: Int)

    @Query("DELETE FROM clipboard_records WHERE timestamp < :timestamp")
    suspend fun deleteOldRecords(timestamp: Long)
    
    @Query("DELETE FROM clipboard_records")
    suspend fun deleteAllRecords()
    
    @Query("SELECT COUNT(*) FROM clipboard_records")
    suspend fun getRecordCount(): Int
    
    @Query("SELECT * FROM clipboard_records WHERE id IN (:ids)")
    suspend fun getRecordsByIds(ids: List<Long>): List<ClipboardRecord>
    
    @Query("DELETE FROM clipboard_records WHERE id IN (:ids)")
    suspend fun deleteRecordsByIds(ids: List<Long>)
    
    @Update
    suspend fun updateRecord(record: ClipboardRecord)
}

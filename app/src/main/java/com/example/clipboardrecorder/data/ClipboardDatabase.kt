package com.example.clipboardrecorder.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ClipboardRecord::class],
    version = 2,
    exportSchema = false
)
abstract class ClipboardDatabase : RoomDatabase() {
    abstract fun clipboardDao(): ClipboardDao
}

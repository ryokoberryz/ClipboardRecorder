package com.example.clipboardrecorder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clipboard_records")
data class ClipboardRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val content: String,
    val timestamp: Long = System.currentTimeMillis()
)

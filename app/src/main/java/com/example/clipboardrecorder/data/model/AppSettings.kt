package com.example.clipboardrecorder.data.model

data class AppSettings(
    val maxRecords: Int = 100,
    val retentionDays: Int = 30,
    val autoCleanup: Boolean = true,
    val showToast: Boolean = true,
    val autoRecordEnabled: Boolean = false
)

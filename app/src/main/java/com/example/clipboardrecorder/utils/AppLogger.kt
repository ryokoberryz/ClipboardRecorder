package com.example.clipboardrecorder.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

object AppLogger {
    private const val MAX_LOG_SIZE = 200
    private val logs = ConcurrentLinkedDeque<String>()
    private val dateFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.getDefault())

    fun d(tag: String, message: String) {
        addLog("D", tag, message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        val fullMessage = if (throwable != null) {
            "$message\n${throwable.stackTraceToString()}"
        } else {
            message
        }
        addLog("E", tag, fullMessage)
    }

    fun i(tag: String, message: String) {
        addLog("I", tag, message)
    }

    fun w(tag: String, message: String) {
        addLog("W", tag, message)
    }

    @Synchronized
    private fun addLog(level: String, tag: String, message: String) {
        val timestamp = dateFormat.format(Date())
        val logEntry = "[$timestamp] $level/$tag: $message"

        logs.addFirst(logEntry)

        while (logs.size > MAX_LOG_SIZE) {
            logs.removeLast()
        }
    }

    fun getLogs(): String {
        return logs.joinToString("\n")
    }

    fun clearLogs() {
        logs.clear()
    }
}

package com.example.clipboardrecorder.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ClipboardReceiver(
    private val onClipboardChanged: (String) -> Unit
) : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent?.action == "android.intent.action.CLIPBOARD_CHANGED") {
            val text = intent.getStringExtra("text") ?: ""
            if (text.isNotEmpty()) {
                onClipboardChanged(text)
            }
        }
    }
}

package com.example.clipboardrecorder.utils

import android.content.ClipboardManager
import android.content.Context

object ClipboardHelper {
    private const val TAG = "ClipboardHelper"

    fun readClipboardText(context: Context): String? {
        return try {
            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: run {
                    AppLogger.e(TAG, "无法获取 ClipboardManager")
                    return null
                }

            val clip = clipboardManager.primaryClip
            if (clip == null || clip.itemCount == 0) {
                AppLogger.d(TAG, "剪贴板无内容")
                return null
            }

            val item = clip.getItemAt(0)
            val text = item.text?.toString() ?: ""

            if (text.isEmpty()) {
                AppLogger.d(TAG, "剪贴板内容为空")
                return null
            }

            AppLogger.d(TAG, "成功读取剪贴板内容，长度: ${text.length}")
            text
        } catch (e: Exception) {
            AppLogger.e(TAG, "读取剪贴板失败", e)
            null
        }
    }

    fun readClipboardText(context: Context, logPrefix: String): String? {
        val text = readClipboardText(context)
        if (text != null) {
            AppLogger.i(TAG, "[$logPrefix] 读取到剪贴板内容: ${text.take(30)}...")
        }
        return text
    }
}

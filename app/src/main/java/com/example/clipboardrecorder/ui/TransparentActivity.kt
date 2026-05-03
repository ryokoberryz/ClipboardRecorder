package com.example.clipboardrecorder.ui

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.clipboardrecorder.R
import com.example.clipboardrecorder.data.ClipboardRepository
import com.example.clipboardrecorder.utils.AppLogger
import com.example.clipboardrecorder.utils.ClipboardHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TransparentActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: ClipboardRepository

    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "TransparentActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transparent)
        AppLogger.i(TAG, "透明Activity启动")
        
        handler.postDelayed({
            readClipboardAndSave()
        }, 200)
    }

    private fun readClipboardAndSave() {
        val text = ClipboardHelper.readClipboardText(this, TAG)
        
        if (text != null) {
            saveToDatabase(text)
        } else {
            Toast.makeText(this, "剪贴板无内容或读取失败", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun saveToDatabase(text: String) {
        lifecycleScope.launch {
            try {
                val id = repository.insertRecord(text)
                AppLogger.i(TAG, "剪贴板内容保存成功, ID: $id")
                
                Toast.makeText(
                    this@TransparentActivity,
                    "已记录: ${text.take(20)}${if (text.length > 20) "..." else ""}",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                AppLogger.e(TAG, "保存剪贴板内容失败", e)
                Toast.makeText(this@TransparentActivity, "保存失败", Toast.LENGTH_SHORT).show()
            } finally {
                finish()
            }
        }
    }

    @Suppress("DEPRECATION")
    override fun finish() {
        super.finish()
        overridePendingTransition(0, 0)
    }
}

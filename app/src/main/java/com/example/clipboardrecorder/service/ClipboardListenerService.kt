package com.example.clipboardrecorder.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.clipboardrecorder.ClipboardApp
import com.example.clipboardrecorder.R
import com.example.clipboardrecorder.data.ClipboardRepository
import com.example.clipboardrecorder.ui.MainActivity
import com.example.clipboardrecorder.ui.TransparentActivity
import com.example.clipboardrecorder.utils.AppLogger
import com.example.clipboardrecorder.utils.ClipboardHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardListenerService : Service() {

    @Inject
    lateinit var repository: ClipboardRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String = ""
    private var lastSaveTime: Long = 0
    private var lastAutoLaunchTime: Long = 0

    private val handler = Handler(Looper.getMainLooper())

    private val isServiceRunning = AtomicBoolean(false)
    @Volatile
    private var showToast = true
    @Volatile
    private var autoRecordEnabled = false

    private val debounceIntervalMs = 500L

    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        AppLogger.d(TAG, "剪贴板变化事件触发, autoRecord=$autoRecordEnabled")
        handler.post {
            if (autoRecordEnabled) {
                launchAutoRecord()
            } else {
                checkClipboard()
            }
        }
    }

    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning.get()) {
                reRegisterClipboardListener()
                handler.postDelayed(this, 300_000L)
            }
        }
    }

    companion object {
        private const val TAG = "ClipboardService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.example.clipboardrecorder.START"
        const val ACTION_STOP = "com.example.clipboardrecorder.STOP"

        fun start(context: Context) {
            val intent = Intent(context, ClipboardListenerService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, ClipboardListenerService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        isServiceRunning.set(true)
        AppLogger.i(TAG, "服务创建")
        Log.d(TAG, "Service onCreate")

        startForeground(NOTIFICATION_ID, createNotification())
        loadInitialSettings()
        registerClipboardListener()
        startHealthCheck()
        observeSettings()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "服务启动命令, action: ${intent?.action}")
        Log.d(TAG, "Service onStartCommand, action: ${intent?.action}")

        when (intent?.action) {
            ACTION_STOP -> {
                isServiceRunning.set(false)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        if (!isServiceRunning.get()) {
            isServiceRunning.set(true)
            registerClipboardListener()
            startHealthCheck()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLogger.w(TAG, "服务销毁")
        Log.d(TAG, "Service onDestroy")
        isServiceRunning.set(false)
        unregisterClipboardListener()
        stopHealthCheck()
        handler.removeCallbacksAndMessages(null)
        serviceScope.cancel()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, ClipboardApp.CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_clipboard)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun registerClipboardListener() {
        AppLogger.d(TAG, "注册剪贴板监听器")
        Log.d(TAG, "Registering clipboard listener")
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
        AppLogger.i(TAG, "剪贴板监听器注册成功")
    }

    private fun unregisterClipboardListener() {
        AppLogger.d(TAG, "注销剪贴板监听器")
        clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
    }

    private fun reRegisterClipboardListener() {
        AppLogger.d(TAG, "重新注册剪贴板监听器")
        try {
            clipboardManager?.removePrimaryClipChangedListener(clipChangedListener)
            clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
            AppLogger.i(TAG, "剪贴板监听器重新注册成功")
        } catch (e: Exception) {
            AppLogger.e(TAG, "重新注册监听器失败", e)
        }
    }

    private fun startHealthCheck() {
        handler.postDelayed(healthCheckRunnable, 300_000L)
    }

    private fun stopHealthCheck() {
        handler.removeCallbacks(healthCheckRunnable)
    }

    private fun loadInitialSettings() {
        runBlocking {
            try {
                val settings = repository.settings.first()
                showToast = settings.showToast
                autoRecordEnabled = settings.autoRecordEnabled
                AppLogger.i(TAG, "初始设置加载完成, autoRecord=$autoRecordEnabled")
            } catch (e: Exception) {
                AppLogger.e(TAG, "加载初始设置失败", e)
            }
        }
    }

    private fun observeSettings() {
        serviceScope.launch {
            repository.settings.collect { settings ->
                showToast = settings.showToast
                autoRecordEnabled = settings.autoRecordEnabled
            }
        }
    }

    private fun checkClipboard() {
        val text = ClipboardHelper.readClipboardText(this)

        if (text == null || text == lastClipboardText) return

        val now = System.currentTimeMillis()
        if (now - lastSaveTime < debounceIntervalMs) return

        lastClipboardText = text
        lastSaveTime = now
        onClipboardChanged(text)
    }

    private fun launchAutoRecord() {
        val now = System.currentTimeMillis()
        if (now - lastAutoLaunchTime < debounceIntervalMs) return

        lastAutoLaunchTime = now
        AppLogger.i(TAG, "自动记录模式：通过PendingIntent启动透明Activity")
        val intent = Intent(this, TransparentActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        try {
            val pi = PendingIntent.getActivity(
                this,
                1002,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            pi.send()
        } catch (e: Exception) {
            AppLogger.e(TAG, "自动记录启动失败，回退到startActivity", e)
            try {
                startActivity(intent)
            } catch (e2: Exception) {
                AppLogger.e(TAG, "startActivity也失败", e2)
            }
        }
    }

    private fun onClipboardChanged(text: String) {
        AppLogger.i(TAG, "保存剪贴板内容到数据库, 长度: ${text.length}")
        Log.d(TAG, "Saving clipboard text to database: ${text.take(50)}...")
        if (text.isNotEmpty()) {
            serviceScope.launch {
                try {
                    val id = repository.insertRecord(text)
                    AppLogger.i(TAG, "剪贴板内容保存成功, ID: $id")
                    Log.d(TAG, "Clipboard text saved successfully, id: $id")

                    if (showToast) {
                        handler.post {
                            Toast.makeText(
                                this@ClipboardListenerService,
                                "已记录: ${text.take(20)}${if (text.length > 20) "..." else ""}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "保存剪贴板内容失败", e)
                    Log.e(TAG, "Failed to save clipboard text", e)
                }
            }
        }
    }
}

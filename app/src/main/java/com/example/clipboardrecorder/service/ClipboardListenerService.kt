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
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.example.clipboardrecorder.ClipboardApp
import com.example.clipboardrecorder.R
import com.example.clipboardrecorder.data.ClipboardRepository
import com.example.clipboardrecorder.ui.MainActivity
import com.example.clipboardrecorder.utils.AppLogger
import com.example.clipboardrecorder.utils.ClipboardHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ClipboardListenerService : Service() {

    @Inject
    lateinit var repository: ClipboardRepository

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var clipboardManager: ClipboardManager? = null
    private var lastClipboardText: String = ""
    private var lastClipboardTime: Long = 0
    
    private val handler = Handler(Looper.getMainLooper())
    private val checkInterval = 300L
    
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceRunning = false
    
    private val clipChangedListener = ClipboardManager.OnPrimaryClipChangedListener {
        AppLogger.d(TAG, "剪贴板变化事件触发")
        handler.post {
            checkClipboard("监听器回调")
        }
    }
    
    private val periodicCheckRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning) {
                checkClipboard("定时检查")
                handler.postDelayed(this, checkInterval)
            }
        }
    }
    
    private val restartRunnable = object : Runnable {
        override fun run() {
            if (isServiceRunning) {
                AppLogger.d(TAG, "定期重启服务检查")
                reRegisterClipboardListener()
                handler.postDelayed(this, 30000)
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
        isServiceRunning = true
        AppLogger.i(TAG, "服务创建 - 版本 0.5")
        Log.d(TAG, "Service onCreate")
        
        acquireWakeLock()
        startForeground(NOTIFICATION_ID, createNotification())
        registerClipboardListener()
        startPeriodicCheck()
        startRestartCheck()
        checkClipboard("服务启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        AppLogger.d(TAG, "服务启动命令, action: ${intent?.action}")
        Log.d(TAG, "Service onStartCommand, action: ${intent?.action}")
        
        when (intent?.action) {
            ACTION_STOP -> {
                isServiceRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }
        
        if (!isServiceRunning) {
            isServiceRunning = true
            registerClipboardListener()
            startPeriodicCheck()
            startRestartCheck()
        }
        
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        AppLogger.w(TAG, "服务销毁")
        Log.d(TAG, "Service onDestroy")
        isServiceRunning = false
        unregisterClipboardListener()
        stopPeriodicCheck()
        stopRestartCheck()
        handler.removeCallbacksAndMessages(null)
        releaseWakeLock()
        serviceScope.cancel()
    }
    
    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ClipboardRecorder::ClipboardService"
            ).apply {
                acquire(10 * 60 * 1000L)
            }
            AppLogger.i(TAG, "WakeLock已获取")
        } catch (e: Exception) {
            AppLogger.e(TAG, "获取WakeLock失败", e)
        }
    }
    
    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    AppLogger.i(TAG, "WakeLock已释放")
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "释放WakeLock失败", e)
        }
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
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun registerClipboardListener() {
        AppLogger.d(TAG, "注册剪贴板监听器")
        Log.d(TAG, "Registering clipboard listener")
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager?.addPrimaryClipChangedListener(clipChangedListener)
        AppLogger.i(TAG, "剪贴板监听器注册成功")
        Log.d(TAG, "Clipboard listener registered successfully")
    }

    private fun unregisterClipboardListener() {
        AppLogger.d(TAG, "注销剪贴板监听器")
        Log.d(TAG, "Unregistering clipboard listener")
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
    
    private fun startPeriodicCheck() {
        AppLogger.i(TAG, "启动定时检查机制，间隔: ${checkInterval}ms")
        handler.post(periodicCheckRunnable)
    }
    
    private fun stopPeriodicCheck() {
        AppLogger.d(TAG, "停止定时检查")
        handler.removeCallbacks(periodicCheckRunnable)
    }
    
    private fun startRestartCheck() {
        AppLogger.i(TAG, "启动定期重启检查")
        handler.postDelayed(restartRunnable, 30000)
    }
    
    private fun stopRestartCheck() {
        AppLogger.d(TAG, "停止定期重启检查")
        handler.removeCallbacks(restartRunnable)
    }

    private fun checkClipboard(source: String) {
        val text = ClipboardHelper.readClipboardText(this, source)
        
        if (text != null && text != lastClipboardText) {
            val currentTime = System.currentTimeMillis()
            lastClipboardText = text
            lastClipboardTime = currentTime
            onClipboardChanged(text)
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
                    
                    handler.post {
                        Toast.makeText(
                            this@ClipboardListenerService,
                            "已记录: ${text.take(20)}${if (text.length > 20) "..." else ""}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "保存剪贴板内容失败", e)
                    Log.e(TAG, "Failed to save clipboard text", e)
                }
            }
        }
    }
}

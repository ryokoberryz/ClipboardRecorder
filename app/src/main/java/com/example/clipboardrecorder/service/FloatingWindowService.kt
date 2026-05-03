package com.example.clipboardrecorder.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.example.clipboardrecorder.R
import com.example.clipboardrecorder.ui.TransparentActivity
import com.example.clipboardrecorder.utils.AppLogger

class FloatingWindowService : android.app.Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG = "FloatingWindowService"
        const val ACTION_SHOW = "com.example.clipboardrecorder.SHOW"
        const val ACTION_HIDE = "com.example.clipboardrecorder.HIDE"

        fun show(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_SHOW
            }
            context.startService(intent)
        }

        fun hide(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java).apply {
                action = ACTION_HIDE
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        AppLogger.i(TAG, "悬浮窗服务创建")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SHOW -> {
                if (floatingView == null) {
                    createFloatingWindow()
                }
            }
            ACTION_HIDE -> {
                removeFloatingWindow()
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        AppLogger.i(TAG, "悬浮窗服务销毁")
        removeFloatingWindow()
        handler.removeCallbacksAndMessages(null)
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    private fun createFloatingWindow() {
        if (floatingView != null) return
        
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 200
        }

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)
        
        setupTouchListener(layoutParams)
        
        try {
            windowManager?.addView(floatingView, layoutParams)
            AppLogger.i(TAG, "悬浮窗已创建")
            Toast.makeText(this, "悬浮窗已启用，点击读取剪贴板", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            AppLogger.e(TAG, "创建悬浮窗失败", e)
            Toast.makeText(this, "创建悬浮窗失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTouchListener(layoutParams: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        floatingView?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layoutParams.x
                    initialY = layoutParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isMoved = true
                        layoutParams.x = initialX + dx
                        layoutParams.y = initialY + dy
                        windowManager?.updateViewLayout(floatingView, layoutParams)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) {
                        AppLogger.i(TAG, "悬浮窗按钮点击")
                        readClipboardViaTransparentActivity()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun removeFloatingWindow() {
        try {
            floatingView?.let {
                windowManager?.removeView(it)
            }
            floatingView = null
            AppLogger.i(TAG, "悬浮窗已移除")
        } catch (e: Exception) {
            AppLogger.e(TAG, "移除悬浮窗失败", e)
        }
    }

    private fun readClipboardViaTransparentActivity() {
        try {
            val intent = Intent(this, TransparentActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            AppLogger.i(TAG, "已启动透明Activity")
        } catch (e: Exception) {
            AppLogger.e(TAG, "启动透明Activity失败", e)
            Toast.makeText(this, "启动失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

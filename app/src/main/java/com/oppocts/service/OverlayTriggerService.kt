package com.oppocts.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.oppocts.R
import com.oppocts.trigger.CTSTrigger

class OverlayTriggerService : Service() {

    companion object {
        private const val TAG = "OverlayTriggerService"
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "overlay_trigger_channel"

        private const val LONG_PRESS_TIMEOUT_MS = 400L
        private const val MOVE_SLOP_PX = 30f
        const val ACTION_UPDATE_LAYOUT = "ACTION_UPDATE_OVERLAY_LAYOUT"
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private val handler = Handler(Looper.getMainLooper())

    private var initialX = 0f
    private var initialY = 0f
    private var isLongPressDetected = false

    private val longPressRunnable = Runnable {
        isLongPressDetected = true
        Log.d(TAG, "Bottom bar long-press detected! Triggering CTS...")
        triggerCTS()
    }

    override fun onCreate() {
        super.onCreate()
        startForegroundServiceNotification()
        setupOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE_LAYOUT) {
            updateOverlayLayout()
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Trigger Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Running bottom trigger for Circle to Search"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OPPO CTS")
            .setContentText("トリガーサービス実行中")
            .setSmallIcon(R.drawable.ic_cts)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return

        overlayView = View(this).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = event.x
                        initialY = event.y
                        isLongPressDetected = false
                        handler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT_MS)
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = Math.abs(event.x - initialX)
                        val dy = Math.abs(event.y - initialY)
                        if (dx > MOVE_SLOP_PX || dy > MOVE_SLOP_PX) {
                            handler.removeCallbacks(longPressRunnable)
                        }
                        true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(longPressRunnable)
                        true
                    }
                    else -> false
                }
            }
        }

        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)
        val heightPx = prefs.getInt("trigger_height_px", 70)
        val yOffsetPx = prefs.getInt("trigger_y_offset_px", 0)
        val isDebug = prefs.getBoolean("overlay_debug", false)

        overlayView?.setBackgroundColor(if (isDebug) Color.parseColor("#66FF0000") else Color.TRANSPARENT)

        // 画面最下端（ナビゲーションバー領域）へ完全に食い込ませるフラグ群
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = yOffsetPx
            // ディスプレイカットアウト・ナビバー領域への貫通
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun updateOverlayLayout() {
        if (overlayView == null || windowManager == null || layoutParams == null) return

        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)
        val heightPx = prefs.getInt("trigger_height_px", 70)
        val yOffsetPx = prefs.getInt("trigger_y_offset_px", 0)
        val isDebug = prefs.getBoolean("overlay_debug", false)

        overlayView?.setBackgroundColor(if (isDebug) Color.parseColor("#66FF0000") else Color.TRANSPARENT)

        layoutParams?.height = heightPx
        layoutParams?.y = yOffsetPx

        try {
            windowManager?.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay view layout", e)
        }
    }

    private fun triggerCTS() {
        try {
            val vibrator = getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK))
                } else {
                    vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibration failed", e)
        }

        CTSTrigger.trigger(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(longPressRunnable)
        if (overlayView != null && windowManager != null) {
            try {
                windowManager?.removeView(overlayView)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing overlay view", e)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

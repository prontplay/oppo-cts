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

        private const val LONG_PRESS_TIMEOUT_MS = 400L // 0.4초 길게 누르기
        private const val MOVE_SLOP_PX = 30f // 스와이프 오인 방지 허용 오차
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
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

    private fun startForegroundServiceNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Overlay Trigger Service",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Running invisible bottom trigger for Circle to Search"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("OPPO CTS")
            .setContentText("Bottom trigger is active")
            .setSmallIcon(R.drawable.ic_cts)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupOverlay() {
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        if (windowManager == null) {
            Log.e(TAG, "WindowManager is null, cannot add overlay")
            return
        }

        // 투명 터치 감지 뷰 생성
        overlayView = View(this).apply {
            setBackgroundColor(Color.TRANSPARENT)

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

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = yOffsetPx
        }

        try {
            windowManager?.addView(overlayView, layoutParams)
            Log.d(TAG, "Overlay trigger view attached successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay view", e)
        }
    }

    private fun triggerCTS() {
        // 純正CTS準拠のハプティクス（触覚フィードバック）を実行
        try {
            val vibrator = getSystemService(Vibrator::class.java)
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    vibrator.vibrate(
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK)
                    )
                } else {
                    vibrator.vibrate(
                        VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to vibrate", e)
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

package com.oppocts.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.preference.PreferenceManager
import com.oppocts.R
import com.oppocts.shizuku.GmsFlagSetter
import com.oppocts.shizuku.ShizukuHelper
import com.oppocts.trigger.CTSTrigger
import com.oppocts.ui.SettingsActivity
import kotlinx.coroutines.*
import rikka.shizuku.Shizuku

/**
 * 부팅 완료 리시버 — 재부팅 후 자동으로 CTS 환경을 복원합니다.
 *
 * 1. Shizuku 대기 → 없으면 알림으로 안내
 * 2. CTS Flag 재설정
 * 3. 키 모니터링 재시작
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
        private const val MAX_WAIT_MS = 60_000L
        private const val POLL_INTERVAL_MS = 3_000L
        private const val CHANNEL_ID = "cts_status"
        private const val NOTIF_ID = 1001
    }

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        Log.i(TAG, "Boot completed / package updated, scheduling CTS restore...")
        createNotificationChannel(context)

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val isOverlayEnabled = prefs.getBoolean("overlay_trigger_enabled", false)
        if (isOverlayEnabled && Settings.canDrawOverlays(context)) {
            Log.i(TAG, "Starting OverlayTriggerService on boot")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(Intent(context, OverlayTriggerService::class.java))
            } else {
                context.startService(Intent(context, OverlayTriggerService::class.java))
            }
        }

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val shizukuReady = waitForShizuku()
                if (!shizukuReady) {
                    Log.w(TAG, "Shizuku not available, showing notification")
                    showNotification(
                        context,
                        "⚠️ CTS 비활성 상태",
                        "Shizuku가 실행되지 않았습니다. 무선 디버깅을 켜고 Shizuku를 시작해주세요."
                    )
                    pendingResult.finish()
                    return@launch
                }

                Log.i(TAG, "Shizuku is ready, restoring CTS environment...")

                // 1. UserService 바인딩
                CTSTrigger.bindService()
                delay(2000)

                // 2. CTS Flag 재설정
                try {
                    GmsFlagSetter.enableCTSFlag()
                    Log.i(TAG, "CTS Flag restored")
                } catch (e: Exception) {
                    Log.e(TAG, "CTS Flag restore failed", e)
                }

                // 3. 키 모니터링 재시작
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val triggerMethod = prefs.getString(
                    OppoAccessibilityService.PREF_TRIGGER_METHOD, "none"
                ) ?: "none"

                if (triggerMethod != "none") {
                    var waitMs = 0
                    while (!CTSTrigger.isServiceBound() && waitMs < 5000) {
                        delay(500)
                        waitMs += 500
                    }
                    if (CTSTrigger.isServiceBound()) {
                        CTSTrigger.startKeyMonitoring(triggerMethod)
                        Log.i(TAG, "Key monitoring started: $triggerMethod")
                    }
                }

                // 성공 알림
                showNotification(
                    context,
                    "✅ CTS 준비 완료",
                    "CTS 환경이 자동으로 복원되었습니다."
                )
                Log.i(TAG, "CTS environment restored!")

                // 성공 알림은 5초 후 자동 제거
                delay(5000)
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                nm.cancel(NOTIF_ID)

            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore CTS environment", e)
                showNotification(
                    context,
                    "❌ CTS 복원 실패",
                    "오류: ${e.message}"
                )
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CTS 상태",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "CTS 환경 상태 알림"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun showNotification(context: Context, title: String, message: String) {
        val intent = Intent(context, SettingsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = android.app.Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(android.app.Notification.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIF_ID, notification)
    }

    private suspend fun waitForShizuku(): Boolean {
        var waited = 0L
        while (waited < MAX_WAIT_MS) {
            try {
                if (Shizuku.pingBinder() && ShizukuHelper.hasPermission()) {
                    return true
                }
            } catch (_: Exception) {}
            delay(POLL_INTERVAL_MS)
            waited += POLL_INTERVAL_MS
        }
        return false
    }
}

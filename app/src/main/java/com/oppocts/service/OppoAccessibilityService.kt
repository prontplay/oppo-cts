package com.oppocts.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import androidx.preference.PreferenceManager
import com.oppocts.trigger.CTSTrigger

/**
 * OPPO 접근성 서비스 — 물리 버튼 매핑으로 CTS를 트리거합니다.
 *
 * 지원하는 트리거 방식:
 * - 볼륨 아래 더블 클릭
 * - 볼륨 위 더블 클릭
 * - 볼륨 아래 길게 누르기
 * - 볼륨 위+아래 동시 누르기
 * - 카메라 버튼 길게 누르기
 * - 단축키 버튼
 */
class OppoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OppoAccessibility"

        // 트리거 옵션 상수
        const val TRIGGER_NONE = "none"
        const val TRIGGER_VOL_DOWN_DOUBLE = "vol_down_double"
        const val TRIGGER_VOL_UP_DOUBLE = "vol_up_double"
        const val TRIGGER_VOL_DOWN_LONG = "vol_down_long"
        const val TRIGGER_VOL_BOTH = "vol_both"
        const val TRIGGER_CAMERA_LONG = "camera_long"
        const val TRIGGER_SHORTCUT_KEY = "shortcut_key"

        const val PREF_TRIGGER_METHOD = "cts_trigger_method"
        const val DEFAULT_TRIGGER = TRIGGER_VOL_DOWN_DOUBLE

        const val ACTION_KEY_EVENT = "com.oppocts.KEY_EVENT_DETECTED"
        const val EXTRA_KEY_INFO = "key_info"

        // 최근 키 이벤트 로그 (디버그용)
        val recentKeyEvents = mutableListOf<String>()

        // 타이밍 상수
        private const val DOUBLE_CLICK_TIMEOUT_MS = 400L
        private const val LONG_PRESS_THRESHOLD_MS = 500L

        var isRunning = false
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    // 더블 클릭 상태
    private var lastVolDownTime = 0L
    private var lastVolUpTime = 0L

    // 길게 누르기 상태
    private var longPressRunnable: Runnable? = null
    private var longPressKey = 0

    // 동시 누르기 상태
    private var volDownHeld = false
    private var volUpHeld = false

    // 트리거 중복 방지
    private var isTriggering = false

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        Log.i(TAG, "Accessibility service connected")
        CTSTrigger.bindService()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        isRunning = false
        reset()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        reset()
    }

    private fun getTriggerMethod(): String {
        return prefs.getString(PREF_TRIGGER_METHOD, DEFAULT_TRIGGER) ?: DEFAULT_TRIGGER
    }

    override fun onKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.onKeyEvent(event)

        // 모든 키 이벤트 로깅 (디버그)
        val actionStr = if (event.action == KeyEvent.ACTION_DOWN) "DOWN" else "UP"
        val keyInfo = "key=${event.keyCode} (${KeyEvent.keyCodeToString(event.keyCode)}) action=$actionStr repeat=${event.repeatCount}"
        Log.d(TAG, "onKeyEvent: $keyInfo")
        synchronized(recentKeyEvents) {
            recentKeyEvents.add(0, keyInfo)
            if (recentKeyEvents.size > 20) recentKeyEvents.removeAt(recentKeyEvents.size - 1)
        }
        // 브로드캐스트로 UI에 전달
        sendBroadcast(Intent(ACTION_KEY_EVENT).putExtra(EXTRA_KEY_INFO, keyInfo))

        if (isTriggering) return super.onKeyEvent(event)

        val method = getTriggerMethod()
        if (method == TRIGGER_NONE) return super.onKeyEvent(event)

        when (method) {
            TRIGGER_VOL_DOWN_DOUBLE -> return handleDoubleClick(event, KeyEvent.KEYCODE_VOLUME_DOWN)
            TRIGGER_VOL_UP_DOUBLE -> return handleDoubleClick(event, KeyEvent.KEYCODE_VOLUME_UP)
            TRIGGER_VOL_DOWN_LONG -> return handleLongPress(event, KeyEvent.KEYCODE_VOLUME_DOWN)
            TRIGGER_VOL_BOTH -> return handleBothVolume(event)
            TRIGGER_CAMERA_LONG -> return handleLongPress(event, KeyEvent.KEYCODE_CAMERA)
            TRIGGER_SHORTCUT_KEY -> return handleShortcutKey(event)
        }

        return super.onKeyEvent(event)
    }

    /**
     * 더블 클릭 감지.
     * 두 번째 클릭을 소비하여 볼륨 변경을 방지.
     */
    private fun handleDoubleClick(event: KeyEvent, targetKey: Int): Boolean {
        if (event.keyCode != targetKey) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            val now = System.currentTimeMillis()
            val lastTime = if (targetKey == KeyEvent.KEYCODE_VOLUME_DOWN) lastVolDownTime else lastVolUpTime

            if (now - lastTime < DOUBLE_CLICK_TIMEOUT_MS) {
                // 더블 클릭 감지!
                if (targetKey == KeyEvent.KEYCODE_VOLUME_DOWN) lastVolDownTime = 0L
                else lastVolUpTime = 0L
                fireTrigger()
                return true // 키 이벤트 소비 (볼륨 변경 방지)
            } else {
                if (targetKey == KeyEvent.KEYCODE_VOLUME_DOWN) lastVolDownTime = now
                else lastVolUpTime = now
            }
        }
        return false
    }

    /**
     * 길게 누르기 감지.
     */
    private fun handleLongPress(event: KeyEvent, targetKey: Int): Boolean {
        if (event.keyCode != targetKey) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    longPressKey = targetKey
                    cancelLongPress()
                    longPressRunnable = Runnable {
                        fireTrigger()
                    }
                    handler.postDelayed(longPressRunnable!!, LONG_PRESS_THRESHOLD_MS)
                }
            }
            KeyEvent.ACTION_UP -> {
                if (longPressKey == targetKey) {
                    cancelLongPress()
                }
            }
        }
        return false
    }

    /**
     * 볼륨 위+아래 동시 누르기 감지.
     */
    private fun handleBothVolume(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                volDownHeld = event.action == KeyEvent.ACTION_DOWN
            }
            KeyEvent.KEYCODE_VOLUME_UP -> {
                volUpHeld = event.action == KeyEvent.ACTION_DOWN
            }
            else -> return false
        }

        if (volDownHeld && volUpHeld) {
            volDownHeld = false
            volUpHeld = false
            fireTrigger()
            return true
        }
        return false
    }

    /**
     * 단축키 버튼 감지.
     * OPPO Find X8 Ultra의 커스텀 단축키.
     * 다양한 keycode를 시도합니다.
     */
    private fun handleShortcutKey(event: KeyEvent): Boolean {
        // OPPO 단축키 버튼의 가능한 keycode들
        val shortcutKeys = setOf(
            KeyEvent.KEYCODE_NOTIFICATION,     // 알림 키
            KeyEvent.KEYCODE_ASSIST,           // 어시스턴트 키
            KeyEvent.KEYCODE_VOICE_ASSIST,     // 음성 어시스턴트
            286,                               // OPPO custom key
            287,                               // OPPO custom key 2
            288                                // OPPO custom key 3
        )

        if (event.keyCode !in shortcutKeys) return false

        if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            Log.d(TAG, "Shortcut key detected: keyCode=${event.keyCode}")
            fireTrigger()
            return true
        }
        return false
    }

    /**
     * CTS 트리거 실행 (진동 피드백 포함).
     */
    private fun fireTrigger() {
        if (isTriggering) return
        isTriggering = true

        Log.i(TAG, "Button trigger detected! Firing CTS...")
        vibrate()

        if (!CTSTrigger.isServiceBound()) {
            CTSTrigger.bindService()
        }

        handler.postDelayed({
            val success = CTSTrigger.trigger(applicationContext)
            if (!success) {
                Log.w(TAG, "CTS trigger failed")
            }
            isTriggering = false
        }, 200)
    }

    private fun vibrate() {
        try {
            val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            val attr = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK), attr)
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(longArrayOf(0, 1, 75, 76), -1, attr)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Vibration failed: ${e.message}")
        }
    }

    private fun cancelLongPress() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun reset() {
        cancelLongPress()
        volDownHeld = false
        volUpHeld = false
        lastVolDownTime = 0L
        lastVolUpTime = 0L
        isTriggering = false
    }
}

package com.oppocts.trigger

import android.os.Bundle
import android.os.IBinder
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.oppocts.IVISTrigger
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Shizuku UserService — getevent 기반 키 모니터링 + CTS 트리거.
 *
 * shell 권한으로:
 * 1. getevent -l 로 하드웨어 키 이벤트를 커널 레벨에서 감지
 * 2. showSessionFromSession 으로 CTS 트리거
 *
 * 접근성 서비스 없이 ColorOS에서도 키 매핑 가능.
 */
class VISTriggerService : IVISTrigger.Stub() {

    companion object {
        private const val TAG = "VISTriggerService"

        // 트리거 옵션 (OppoAccessibilityService와 동일한 값)
        const val TRIGGER_VOL_DOWN_DOUBLE = "vol_down_double"
        const val TRIGGER_VOL_UP_DOUBLE = "vol_up_double"
        const val TRIGGER_VOL_DOWN_LONG = "vol_down_long"
        const val TRIGGER_VOL_BOTH = "vol_both"
        const val TRIGGER_CAMERA_LONG = "camera_long"
        const val TRIGGER_SHORTCUT_KEY = "shortcut_key"

        private const val DOUBLE_CLICK_TIMEOUT_MS = 400L
        private const val LONG_PRESS_THRESHOLD_MS = 600L
    }

    // getevent 모니터링 스레드
    @Volatile private var monitorThread: Thread? = null
    @Volatile private var monitorProcess: Process? = null
    @Volatile private var currentTriggerMethod: String? = null
    @Volatile private var isTriggering = false

    // 키 상태 추적
    @Volatile private var lastKeyDownTime = 0L
    @Volatile private var lastKeyCode = ""
    @Volatile private var clickCount = 0
    @Volatile private var volDownHeld = false
    @Volatile private var volUpHeld = false

    // 디버그용 최근 키 이벤트
    private val recentKeys = mutableListOf<String>()

    override fun triggerCTS(): Boolean {
        Log.d(TAG, "triggerCTS() called in Shizuku process")
        return try {
            triggerViaShowSessionFromSession()
        } catch (e: Exception) {
            Log.e(TAG, "triggerCTS() failed", e)
            false
        }
    }

    override fun startKeyMonitoring(triggerMethod: String?) {
        if (triggerMethod.isNullOrEmpty()) {
            Log.w(TAG, "No trigger method specified")
            return
        }
        stopKeyMonitoring()
        currentTriggerMethod = triggerMethod
        Log.i(TAG, "Starting key monitoring: method=$triggerMethod")

        monitorThread = Thread {
            try {
                // getevent -l 은 사람이 읽을 수 있는 형식으로 이벤트를 출력
                val process = Runtime.getRuntime().exec(arrayOf("getevent", "-l"))
                monitorProcess = process
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (Thread.currentThread().isInterrupted) break
                    line?.let { parseLine(it) }
                }
            } catch (e: InterruptedException) {
                Log.d(TAG, "Monitor thread interrupted")
            } catch (e: Exception) {
                Log.e(TAG, "getevent monitoring failed", e)
            }
        }.apply {
            name = "getevent-monitor"
            isDaemon = true
            start()
        }
    }

    override fun stopKeyMonitoring() {
        Log.d(TAG, "Stopping key monitoring")
        currentTriggerMethod = null
        monitorProcess?.destroy()
        monitorProcess = null
        monitorThread?.interrupt()
        monitorThread = null
        resetState()
    }

    override fun getDetectedKeys(): String {
        return synchronized(recentKeys) {
            recentKeys.takeLast(15).joinToString("\n")
        }
    }

    /**
     * getevent 출력 라인 파싱.
     *
     * 형식: /dev/input/eventX: EV_KEY       KEY_VOLUMEDOWN       DOWN
     *       /dev/input/eventX: EV_KEY       KEY_VOLUMEDOWN       UP
     */
    private fun parseLine(line: String) {
        if (!line.contains("EV_KEY")) return

        val parts = line.trim().split(Regex("\\s+"))
        if (parts.size < 4) return

        // parts: [/dev/input/eventX:, EV_KEY, KEY_NAME, DOWN/UP/value]
        val keyName = parts[2]
        val action = parts[3]

        val isDown = action == "DOWN" || action == "0001"
        val isUp = action == "UP" || action == "0000"

        if (!isDown && !isUp) return

        val actionStr = if (isDown) "DOWN" else "UP"
        val keyInfo = "$keyName $actionStr"
        Log.d(TAG, "Key event: $keyInfo")
        synchronized(recentKeys) {
            recentKeys.add(keyInfo)
            if (recentKeys.size > 30) recentKeys.removeAt(0)
        }

        val method = currentTriggerMethod ?: return

        when (method) {
            TRIGGER_VOL_DOWN_DOUBLE -> handleDoubleClick(keyName, isDown, "KEY_VOLUMEDOWN")
            TRIGGER_VOL_UP_DOUBLE -> handleDoubleClick(keyName, isDown, "KEY_VOLUMEUP")
            TRIGGER_VOL_DOWN_LONG -> handleLongPress(keyName, isDown, isUp, "KEY_VOLUMEDOWN")
            TRIGGER_VOL_BOTH -> handleBothVolume(keyName, isDown, isUp)
            TRIGGER_CAMERA_LONG -> handleLongPress(keyName, isDown, isUp, "KEY_CAMERA")
            TRIGGER_SHORTCUT_KEY -> handleShortcutKey(keyName, isDown)
        }
    }

    private fun handleDoubleClick(keyName: String, isDown: Boolean, targetKey: String) {
        if (keyName != targetKey || !isDown) return

        val now = SystemClock.elapsedRealtime()
        if (lastKeyCode == targetKey && (now - lastKeyDownTime) < DOUBLE_CLICK_TIMEOUT_MS) {
            clickCount++
            if (clickCount >= 2) {
                Log.i(TAG, "Double click detected: $targetKey")
                clickCount = 0
                lastKeyDownTime = 0
                fireTrigger()
            }
        } else {
            clickCount = 1
            lastKeyDownTime = now
            lastKeyCode = targetKey
        }
    }

    private fun handleLongPress(keyName: String, isDown: Boolean, isUp: Boolean, targetKey: String) {
        if (keyName != targetKey) return

        if (isDown) {
            if (lastKeyCode != targetKey) {
                lastKeyCode = targetKey
                lastKeyDownTime = SystemClock.elapsedRealtime()
            } else {
                // 키가 계속 눌려있는 상태 — 시간 체크
                val elapsed = SystemClock.elapsedRealtime() - lastKeyDownTime
                if (elapsed >= LONG_PRESS_THRESHOLD_MS && !isTriggering) {
                    Log.i(TAG, "Long press detected: $targetKey (${elapsed}ms)")
                    fireTrigger()
                    lastKeyCode = "" // 다음 이벤트까지 초기화
                }
            }
        } else if (isUp) {
            lastKeyCode = ""
        }
    }

    private fun handleBothVolume(keyName: String, isDown: Boolean, isUp: Boolean) {
        when (keyName) {
            "KEY_VOLUMEDOWN" -> volDownHeld = isDown
            "KEY_VOLUMEUP" -> volUpHeld = isDown
            else -> return
        }
        if (volDownHeld && volUpHeld) {
            Log.i(TAG, "Both volume keys detected")
            volDownHeld = false
            volUpHeld = false
            fireTrigger()
        }
    }

    private fun handleShortcutKey(keyName: String, isDown: Boolean) {
        // OPPO 커스텀 키 가능한 이름들
        val shortcutKeys = setOf(
            "KEY_ASSISTANT", "KEY_VOICECOMMAND", "KEY_NOTIFICATION",
            "KEY_MACRO", "KEY_KBD_LCD_MENU1", "KEY_FN",
            "KEY_UNKNOWN" // 커스텀 키는 종종 UNKNOWN으로 표시
        )
        if (keyName in shortcutKeys && isDown) {
            Log.i(TAG, "Shortcut key detected: $keyName")
            fireTrigger()
        }
    }

    private fun fireTrigger() {
        if (isTriggering) return
        isTriggering = true

        Thread {
            try {
                val success = triggerViaShowSessionFromSession()
                Log.i(TAG, "CTS trigger result: $success")
            } catch (e: Exception) {
                Log.e(TAG, "CTS trigger failed", e)
            } finally {
                // 중복 방지를 위해 1초 쿨다운
                Thread.sleep(1000)
                isTriggering = false
            }
        }.start()
    }

    private fun resetState() {
        lastKeyDownTime = 0
        lastKeyCode = ""
        clickCount = 0
        volDownHeld = false
        volUpHeld = false
        isTriggering = false
    }

    // ===== CTS 트리거 (showSessionFromSession) =====

    private fun triggerViaShowSessionFromSession(): Boolean {
        val bundle = Bundle().apply {
            putLong("invocation_time_ms", SystemClock.elapsedRealtime())
            putInt("omni.entry_point", 1)
            putBoolean("cts_trigger", true)
        }

        val smClass = Class.forName("android.os.ServiceManager")
        val getService = smClass.getMethod("getService", String::class.java)
        val binder = getService.invoke(null, "voiceinteraction") as? IBinder
            ?: run { Log.e(TAG, "voiceinteraction binder is null"); return false }

        val iVimsClass = Class.forName(
            "com.android.internal.app.IVoiceInteractionManagerService"
        )
        val stubClass = Class.forName(
            "com.android.internal.app.IVoiceInteractionManagerService\$Stub"
        )
        val asInterface = stubClass.getMethod("asInterface", IBinder::class.java)
        val vims = asInterface.invoke(null, binder)
            ?: run { Log.e(TAG, "asInterface returned null"); return false }

        val callingPackages = arrayOf("hyperOS_home", "com.oppocts", "com.miui.home", "com.android.systemui")

        // 직접 리플렉션 (4-param)
        try {
            val method = iVimsClass.getMethod(
                "showSessionFromSession",
                IBinder::class.java, Bundle::class.java,
                Int::class.javaPrimitiveType, String::class.java
            )
            for (pkg in callingPackages) {
                try {
                    val result = method.invoke(vims, null, bundle, 7, pkg) as? Boolean ?: false
                    if (result) {
                        Log.d(TAG, "showSessionFromSession succeeded (4-param, pkg=$pkg)")
                        return true
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "4-param pkg=$pkg threw: ${e.cause?.message ?: e.message}")
                }
            }
        } catch (_: NoSuchMethodException) {}

        // 직접 리플렉션 (3-param)
        try {
            val method = iVimsClass.getMethod(
                "showSessionFromSession",
                IBinder::class.java, Bundle::class.java,
                Int::class.javaPrimitiveType
            )
            val result = method.invoke(vims, null, bundle, 7) as? Boolean ?: false
            if (result) return true
        } catch (_: NoSuchMethodException) {}
        catch (e: Exception) { Log.d(TAG, "3-param threw: ${e.cause?.message ?: e.message}") }

        // HiddenApiBypass 폴백
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val result = HiddenApiBypass.invoke(
                    iVimsClass, vims, "showSessionFromSession",
                    null, bundle, 7, "hyperOS_home"
                ) as? Boolean ?: false
                if (result) return true
            } else {
                val result = HiddenApiBypass.invoke(
                    iVimsClass, vims, "showSessionFromSession",
                    null, bundle, 7
                ) as? Boolean ?: false
                if (result) return true
            }
        } catch (e: Exception) {
            Log.d(TAG, "HiddenApiBypass failed: ${e.cause?.message ?: e.message}")
        }

        Log.e(TAG, "All trigger methods failed")
        return false
    }

    override fun destroy() {
        stopKeyMonitoring()
        Log.d(TAG, "Service destroyed")
    }
}

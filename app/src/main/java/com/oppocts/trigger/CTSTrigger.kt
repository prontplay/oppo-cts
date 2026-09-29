package com.oppocts.trigger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.oppocts.BuildConfig
import com.oppocts.IVISTrigger
import com.oppocts.shizuku.ShizukuHelper
import rikka.shizuku.Shizuku

/**
 * CTS 트리거 – VoiceInteractionSession 기반 (MiCTS 방식).
 *
 * Shizuku UserService를 통해 shell 권한으로
 * VoiceInteractionManagerService.showSessionForActiveService(null, 8192)
 * 를 호출하여 서클 투 서치를 활성화합니다.
 */
object CTSTrigger {

    private const val TAG = "CTSTrigger"

    @Volatile
    private var visTriggerService: IVISTrigger? = null

    @Volatile
    private var isBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            visTriggerService = IVISTrigger.Stub.asInterface(service)
            isBound = true
            Log.d(TAG, "VISTriggerService connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            visTriggerService = null
            isBound = false
            Log.d(TAG, "VISTriggerService disconnected")
        }
    }

    /**
     * Shizuku UserService 바인딩.
     * 트리거 전에 반드시 호출해야 합니다.
     */
    fun bindService() {
        if (isBound) return
        if (!ShizukuHelper.hasPermission()) {
            Log.e(TAG, "Shizuku permission not granted")
            return
        }

        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, VISTriggerService::class.java.name)
            )
                .daemon(true)
                .processNameSuffix("vis_trigger")
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE)

            Shizuku.bindUserService(args, serviceConnection)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind VISTriggerService", e)
        }
    }

    /**
     * Shizuku UserService 언바인딩.
     */
    fun unbindService() {
        if (!isBound) return
        try {
            val args = Shizuku.UserServiceArgs(
                ComponentName(BuildConfig.APPLICATION_ID, VISTriggerService::class.java.name)
            )
            Shizuku.unbindUserService(args, serviceConnection, true)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unbind VISTriggerService", e)
        }
        visTriggerService = null
        isBound = false
    }

    /**
     * 서비스 연결 여부
     */
    fun isServiceBound(): Boolean = isBound

    /**
     * getevent 기반 키 모니터링 시작.
     * Shizuku UserService에서 커널 레벨 키 이벤트를 감지합니다.
     */
    fun startKeyMonitoring(triggerMethod: String) {
        try {
            visTriggerService?.startKeyMonitoring(triggerMethod)
            Log.d(TAG, "Key monitoring started: $triggerMethod")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start key monitoring", e)
        }
    }

    /**
     * 키 모니터링 중지.
     */
    fun stopKeyMonitoring() {
        try {
            visTriggerService?.stopKeyMonitoring()
            Log.d(TAG, "Key monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop key monitoring", e)
        }
    }

    /**
     * 감지된 키 이벤트 로그 조회 (디버그용).
     */
    fun getDetectedKeys(): String {
        return try {
            visTriggerService?.detectedKeys ?: "서비스 미연결"
        } catch (e: Exception) {
            "오류: ${e.message}"
        }
    }


    /**
     * CTS 트리거 실행.
     * @return 결과 메시지
     */
    fun triggerWithLog(context: Context): String {
        val log = StringBuilder()

        // 방법 1: Shizuku UserService (VIS 방식 - 가장 정확)
        try {
            val service = visTriggerService
            if (service != null) {
                val result = service.triggerCTS()
                if (result) {
                    log.appendLine("✅ VIS 트리거 성공! (SHOW_WITH_SCREENSHOT=8192)")
                    return log.toString()
                } else {
                    log.appendLine("❌ VIS 트리거 실패 (showSessionForActiveService 호출 실패)")
                }
            } else {
                log.appendLine("⚠️ VIS 서비스 미연결 (바인딩 대기 중)")
            }
        } catch (e: Exception) {
            log.appendLine("❌ VIS 트리거 에러: ${e.message}")
        }

        // 방법 2: cmd voiceinteraction show (폴백)
        if (ShizukuHelper.hasPermission()) {
            log.appendLine("\n[cmd voiceinteraction show 시도]")
            val r = ShizukuHelper.executeCommand("cmd voiceinteraction show")
            log.appendLine("-> ${r ?: "null"}")
        }

        return log.toString()
    }

    /**
     * 기존 trigger 호환 메서드.
     */
    /**
     * 기존 trigger 호환 메서드.
     * 이제 Shizuku 없이도 작동하는 네이티브 방식을 최우선으로 시도합니다.
     */
    fun trigger(context: Context): Boolean {
        // 1. 최우선: MiCTS 방식 네이티브 서비스 호출 (Shizuku 불필요, 가장 빠름)
        Log.d(TAG, "Attempting native service trigger (MiCTS style)...")
        if (triggerViaNativeService(context, false)) { // 로그 출력 없이 내부적으로 시도
            return true
        }

        // 2. 폴백: Shizuku UserService (VIS 방식 - 권한 있는 상태라면 가장 정확)
        try {
            val service = visTriggerService
            if (service != null && service.triggerCTS()) return true
        } catch (e: Exception) {
            Log.e(TAG, "VIS trigger failed", e)
        }

        // 3. 폴백: cmd voiceinteraction show (Shizuku)
        if (ShizukuHelper.hasPermission()) {
            val r = ShizukuHelper.executeCommand("cmd voiceinteraction show")
            if (r != null) return true
        }

        // 최종 폴백: Intent
        return triggerViaIntent(context)
    }

    private fun triggerViaIntent(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("android.intent.extra.ASSIST_CONTEXT_TYPE_CTS", true)
                putExtra("invocationType", 1)
                putExtra("is_omnisearch", true)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Intent trigger failed", e)
            false
        }
    }

    /**
     * Shizuku 없이 순수 인텐트로 CTS 트리거 시도.
     * 여러 방법을 순서대로 시도하고 결과를 반환합니다.
     */
    fun triggerViaIntentNoShizuku(context: Context): String {
        val log = StringBuilder()
        
        // 0. MiCTS 방식 네이티브 서비스 호출 시도
        log.appendLine("[MiCTS 네이티브 서비스 시도]")
        log.appendLine(if (triggerViaNativeService(context, true)) "네이티브 호출 성공" else "네이티브 호출 실패")
        log.appendLine("-----------------")

        val attempts = listOf(
            // 1. 직접 Omnient Activity
            "Omnient Activity" to {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.google.android.googlequicksearchbox",
                        "com.google.android.apps.search.omnient.device.OmnientActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("source", "long_press_nav")
                }
            },
            // 2. Circle to Search 직접 액션
            "CTS Action" to {
                Intent("com.google.android.apps.search.omnient.TRIGGER").apply {
                    setPackage("com.google.android.googlequicksearchbox")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            },
            // 3. Search Long Press
            "SEARCH_LONG_PRESS" to {
                Intent("android.intent.action.SEARCH_LONG_PRESS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            },
            // 4. Google Lens
            "Google Lens" to {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.google.android.googlequicksearchbox",
                        "com.google.android.apps.search.lens.LensActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("lens_entry_point", "cts")
                }
            },
            // 5. ASSIST + CTS 플래그
            "ACTION_ASSIST+CTS" to {
                Intent(Intent.ACTION_ASSIST).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("is_omnisearch", true)
                    putExtra("omni.entry_point", 1)
                    putExtra("cts_trigger", true)
                }
            },
            // 6. VoiceInteract 직접
            "VOICE_ASSIST" to {
                Intent("android.intent.action.VOICE_ASSIST").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    setPackage("com.google.android.googlequicksearchbox")
                }
            },
            // 7. GMS 통한 CTS
            "GMS_CTS" to {
                Intent().apply {
                    component = android.content.ComponentName(
                        "com.google.android.gms",
                        "com.google.android.gms.search.cta.CircleToSearchActivity"
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
        )

        for ((name, intentBuilder) in attempts) {
            try {
                val intent = intentBuilder()
                context.startActivity(intent)
                log.appendLine("✅ $name 성공!")
                return log.toString()
            } catch (e: Exception) {
                log.appendLine("❌ $name 실패: ${e.message?.take(60)}")
            }
        }

        log.appendLine("\n모든 인텐트 방법 실패. Shizuku 필요.")
        return log.toString()
    }

    /**
     * MiCTS 방식: 시스템 서비스를 직접 호출하여 CTS 트리거 시도.
     * Shizuku 없이 앱 권한으로 시도합니다 (보통 실패하지만 MiCTS가 쓰는 특정 파라미터 적용).
     */
    fun triggerViaNativeService(context: Context, withLog: Boolean = true): Boolean {
        val log = StringBuilder()
        
        // 1. VoiceInteractionManagerService.showSessionFromSession 시도
        try {
            val bundle = android.os.Bundle().apply {
                putLong("invocation_time_ms", android.os.SystemClock.elapsedRealtime())
                putInt("omni.entry_point", 1)
                putBoolean("micts_trigger", true)
            }
            
            val iVimsClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService")
            val smClass = Class.forName("android.os.ServiceManager")
            val binder = smClass.getMethod("getService", String::class.java).invoke(null, "voiceinteraction") as? android.os.IBinder
            val stubClass = Class.forName("com.android.internal.app.IVoiceInteractionManagerService\$Stub")
            val vims = stubClass.getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
            
            if (withLog) log.appendLine("VIS 서비스 획득 성공. showSessionFromSession 시도...")
            
            val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ (Spoofing name to hyperOS_home as MiCTS does)
                org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(iVimsClass, vims, "showSessionFromSession", null, bundle, 7, "hyperOS_home") as Boolean
            } else {
                org.lsposed.hiddenapibypass.HiddenApiBypass.invoke(iVimsClass, vims, "showSessionFromSession", null, bundle, 7) as Boolean
            }
            
            if (result) {
                if (withLog) log.appendLine("✅ VIS 네이티브 호출 성공!")
                return true
            } else {
                if (withLog) log.appendLine("❌ VIS 네이티브 호출 실패 (false 반환)")
            }
        } catch (e: Exception) {
            if (withLog) log.appendLine("❌ VIS 네이티브 에러: ${e.message}")
        }

        // 2. ContextualSearchManagerService.startContextualSearch 시도 (Android 14+)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                val smClass = Class.forName("android.os.ServiceManager")
                val binder = smClass.getMethod("getService", String::class.java).invoke(null, "contextual_search") as? android.os.IBinder
                if (binder != null) {
                    val icsmClass = Class.forName("android.app.contextualsearch.IContextualSearchManager")
                    val stubClass = Class.forName("android.app.contextualsearch.IContextualSearchManager\$Stub")
                    val icsm = stubClass.getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
                    
                    if (withLog) log.appendLine("CSMS 서비스 획득 성공. startContextualSearch 시도...")
                    icsmClass.getDeclaredMethod("startContextualSearch", Int::class.java).invoke(icsm, 1)
                    if (withLog) log.appendLine("✅ CSMS 네이티브 메서드 호출 완료")
                    return true // 이 메서드는 void이므로 호출 성공 시 true 간주
                } else {
                    if (withLog) log.appendLine("⚠️ CSMS 서비스를 찾을 수 없음 (기기 미지원)")
                }
            } catch (e: Exception) {
                if (withLog) log.appendLine("❌ CSMS 네이티브 에러: ${e.message}")
            }
        }
        
        if (withLog && log.isNotEmpty()) {
            Log.d("CTSTrigger", log.toString())
        }
        return false
    }
}

package com.oppocts.shizuku

import android.util.Log

/**
 * GMS Flag 수정을 통한 CTS 활성화 (최종 완성 버전).
 */
object GmsFlagSetter {

    private const val TAG = "GmsFlagSetter"

    fun enableCTSFlag(): Boolean {
        // GMS/Google 앱 전체에 걸쳐 Circle to Search를 활성화하는 모든 핵심 ID들
        val flagIds = listOf(
            "45631784", // Omnient Enabler
            "45434440", // Lens Enabler
            "45353727", // Contextual Search
            "45657807", // Visual Entry Point
            "45656205", // Visual Entry Point v2
            "45657474", // Omnient Entrypoint
            "45350500", // Overlay Enable
            "45656105", // Screen Capture Enable
            "45641470"  // Search Entrypoint
        )
        
        val namespaces = listOf("android_gms", "search", "google", "omnient")
        val packages = listOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.apps.search.omnient.device",
            "com.google.android.gms"
        )

        val cmds = mutableListOf<String>()
        
        // 1. DeviceConfig 기반 네임스페이스별 설정
        for (ns in namespaces) {
            cmds.add("device_config put $ns CircleToSearch__is_enabled true")
            cmds.add("device_config put $ns CircleToSearch__is_available true")
            cmds.add("device_config put $ns CircleToSearch__is_visual_entry_point_enabled true")
            cmds.add("device_config put $ns Omnient__is_enabled true")
            cmds.add("device_config put $ns is_lens_omni_enabled true")
            cmds.add("device_config put $ns contextual_search_enabled true")
            cmds.add("device_config put $ns is_omnisearch_enabled true")
        }
        
        // 2. Phenotype Override (ID 기반 강제 주입)
        for (pkg in packages) {
            for (id in flagIds) {
                cmds.add("am broadcast -a com.google.android.gms.phenotype.FLAG_OVERRIDE " +
                        "--es packageName $pkg " +
                        "--es user '' " +
                        "--es name $id " +
                        "--es intVal 1 " +
                        "--es committed 1")
            }
        }

        var successCount = 0
        for (cmd in cmds) {
            if (ShizukuHelper.executeCommand(cmd) != null) {
                successCount++
            }
        }

        // 설정 반영을 위한 강제 종료
        val pkgsToStop = listOf(
            "com.google.android.googlequicksearchbox",
            "com.google.android.gms",
            "com.google.android.apps.search.omnient.device"
        )
        for (p in pkgsToStop) {
            ShizukuHelper.executeCommand("am force-stop $p")
        }
        
        return successCount > 0
    }

    fun isCTSEnabled(): Boolean {
        // 여러 네임스페이스/키를 확인 — 하나라도 true면 활성 상태
        val checks = listOf(
            "device_config get android_gms CircleToSearch__is_enabled",
            "device_config get search CircleToSearch__is_enabled",
            "device_config get android_gms CircleToSearch__is_available",
            "device_config get android_gms Omnient__is_enabled"
        )
        for (cmd in checks) {
            val res = ShizukuHelper.executeCommand(cmd)
            if (res?.trim() == "true") return true
        }
        // showSessionFromSession은 flag와 무관하게 작동하므로
        // flag가 없어도 어시스턴트가 Google이면 CTS 사용 가능
        return AssistantSetter.isGoogleAssistant()
    }

    fun disableCTSFlag(): Boolean {
        ShizukuHelper.executeCommand("device_config delete android_gms CircleToSearch__is_enabled")
        return true
    }
}

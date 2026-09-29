package com.oppocts.shizuku

import android.util.Log

/**
 * 기본 어시스턴트를 Google로 변경.
 *
 * OPPO CN 기기는 기본적으로 Breeno가 어시스턴트로 설정되어 있어
 * Circle to Search가 동작하지 않습니다.
 * Shizuku를 통해 ADB 레벨에서 기본 어시스턴트를 Google로 변경합니다.
 */
object AssistantSetter {

    private const val TAG = "AssistantSetter"

    // Google 어시스턴트 컴포넌트명
    private const val GOOGLE_ASSISTANT_COMPONENT =
        "com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService"

    // Google Voice Recognition 서비스 (어시스턴트 전환 시 함께 변경 필요)
    private const val GOOGLE_VOICE_RECOGNITION_SERVICE =
        "com.google.android.googlequicksearchbox/com.google.android.voicesearch.serviceapi.GoogleRecognitionService"

    /**
     * 현재 기본 어시스턴트 확인
     * @return 현재 어시스턴트 컴포넌트명, 확인 불가 시 null
     */
    fun getCurrentAssistant(): String? {
        return ShizukuHelper.executeCommand("settings get secure assistant")
    }

    /**
     * 기본 어시스턴트가 Google인지 확인
     */
    fun isGoogleAssistant(): Boolean {
        val current = getCurrentAssistant() ?: return false
        if (current == "null" || current.isBlank()) return false
        return current.contains("com.google.android.googlequicksearchbox")
    }

    /**
     * 기본 어시스턴트를 Google로 변경.
     * OPPO CN에서 어시스턴트 전환에 필요한 세 가지 secure setting을 모두 변경합니다.
     * @return true: 변경 성공
     */
    fun setGoogleAssistant(): Boolean {
        return try {
            // 1. assistant: 기본 어시스턴트 설정
            ShizukuHelper.executeCommand(
                "settings put secure assistant $GOOGLE_ASSISTANT_COMPONENT"
            )

            // 2. voice_interaction_service: VIS 서비스 설정
            ShizukuHelper.executeCommand(
                "settings put secure voice_interaction_service $GOOGLE_ASSISTANT_COMPONENT"
            )

            // 3. voice_recognition_service: 음성 인식 서비스 (OPPO CN에서 필수)
            ShizukuHelper.executeCommand(
                "settings put secure voice_recognition_service $GOOGLE_VOICE_RECOGNITION_SERVICE"
            )

            Log.i(TAG, "All assistant settings applied")

            // 변경 확인 (설정이 적용되려면 약간의 지연 필요)
            Thread.sleep(500)
            val result = isGoogleAssistant()
            Log.i(TAG, "Assistant verification: $result (current=${getCurrentAssistant()})")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set Google assistant", e)
            false
        }
    }
}

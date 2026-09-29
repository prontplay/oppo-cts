package com.ctslauncher.shizuku

import android.content.Context
import rikka.shizuku.Shizuku
import java.io.BufferedReader
import java.io.InputStreamReader

object AssistantSetter {
    fun setDefaultAssistant(context: Context, packageName: String): Boolean {
        try {
            if (!Shizuku.pingBinder() || Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return false
            }
            val serviceComponent = "$packageName/com.google.android.voiceinteraction.GsaVoiceInteractionService"
            val command = "settings put secure voice_interaction_service $serviceComponent"
            val process = Shizuku.newProcess(arrayOf("sh", "-c", command), null, null)
            process.waitFor()
            return process.exitValue() == 0
        } catch (e: Throwable) {
            e.printStackTrace()
            return false
        }
    }
}

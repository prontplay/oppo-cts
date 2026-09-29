package com.oppocts.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku 서비스 관리 헬퍼.
 *
 * Shizuku는 루팅 없이 ADB 레벨 명령어를 실행할 수 있게 해주는 앱입니다.
 * 실제 Shizuku SDK API를 사용합니다 (reflection 아님).
 */
object ShizukuHelper {

    private const val TAG = "ShizukuHelper"
    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    /**
     * Shizuku 설치 여부 확인
     */
    fun isInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Shizuku 실행 중인지 확인.
     * Shizuku.pingBinder()는 SDK에서 직접 제공하는 메서드입니다.
     */
    fun isRunning(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Log.d(TAG, "Shizuku not running", e)
            false
        }
    }

    /**
     * Shizuku 권한 확인.
     * isRunning()이 false이면 checkSelfPermission()은 예외를 던질 수 있으므로 먼저 확인합니다.
     */
    fun hasPermission(): Boolean {
        if (!isRunning()) return false
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Exception) {
            Log.d(TAG, "Cannot check Shizuku permission", e)
            false
        }
    }

    /**
     * Shizuku 권한 요청
     */
    fun requestPermission(requestCode: Int) {
        try {
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request Shizuku permission", e)
        }
    }

    /**
     * ADB shell 명령어 실행.
     * @return 명령어 실행 결과(stdout + stderr) 문자열, 실패 시 null
     */
    fun executeCommand(command: String): String? {
        if (!hasPermission()) {
            Log.e(TAG, "Shizuku permission not granted, cannot execute: $command")
            return null
        }
        return try {
            val shizukuClass = Class.forName("rikka.shizuku.Shizuku")
            val newProcessMethod = shizukuClass.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).also { it.isAccessible = true }

            val args = arrayOf("sh", "-c", command)
            val process = newProcessMethod.invoke(null, args, null, null) as Process
            
            // stdout과 stderr를 모두 읽음
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            
            val combined = if (error.isNotBlank()) "OUT: $output\nERR: $error" else output
            Log.d(TAG, "Command executed: $command -> $combined")
            combined.trim()
        } catch (e: Exception) {
            Log.e(TAG, "Command execution failed: $command", e)
            null
        }
    }


    /**
     * 전체 상태 문자열 반환 (디버그/UI용)
     */
    fun getStatusSummary(context: Context): String {
        val installed = isInstalled(context)
        val running = if (installed) isRunning() else false
        val permission = if (running) hasPermission() else false

        return buildString {
            append("설치: ${if (installed) "✅" else "❌"}")
            append(" | 실행: ${if (running) "✅" else "❌"}")
            append(" | 권한: ${if (permission) "✅" else "❌"}")
        }
    }
}

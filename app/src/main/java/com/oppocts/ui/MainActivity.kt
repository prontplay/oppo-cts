package com.oppocts.ui

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.oppocts.R
import com.oppocts.trigger.CTSTrigger
import kotlinx.coroutines.*

/**
 * 메인 Activity — 앱 아이콘 탭 시 CTS 즉시 실행.
 *
 * MiCTS와 동일한 동작:
 * - 첫 실행 → SetupWizardActivity로 이동
 * - 이후 실행 → CTS 즉시 트리거 후 Activity 종료 (투명 Activity)
 */
class MainActivity : AppCompatActivity() {

    private val prefs: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 첫 실행 확인
        val isFirstRun = prefs.getBoolean("first_run", true)
        if (isFirstRun) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }

        // CTS 트리거 실패로 인한 대안 실행인 경우
        val triggerFailed = intent.getBooleanExtra("trigger_failed", false)
        if (triggerFailed) {
            Toast.makeText(this, getString(R.string.cts_trigger_failed), Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // UserService 바인딩 후 CTS 트리거
        triggerCTS()
    }

    private fun triggerCTS() {
        lifecycleScope.launch {
            // 1. 최우선: Shizuku 없이 가능한 네이티브 트리거 시도 (제일 빠름)
            val nativeSuccess = withContext(Dispatchers.IO) {
                CTSTrigger.triggerViaNativeService(applicationContext, false)
            }
            
            if (nativeSuccess) {
                finish()
                return@launch
            }

            // 2. 실패 시 Shizuku 서비스 바인딩 및 대기
            CTSTrigger.bindService()
            withContext(Dispatchers.IO) {
                var wait = 0
                while (!CTSTrigger.isServiceBound() && wait < 2000) { // 대기 시간 2초로 단축
                    Thread.sleep(100)
                    wait += 100
                }
            }

            val success = withContext(Dispatchers.IO) {
                CTSTrigger.trigger(applicationContext)
            }

            if (!success) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.cts_trigger_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
            finish()
        }
    }
}

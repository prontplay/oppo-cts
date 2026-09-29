package com.oppocts.trigger

import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.oppocts.shizuku.ShizukuHelper
import kotlinx.coroutines.*

/**
 * Quick Settings 타일 서비스.
 * 빠른 설정 패널에서 한 번 탭으로 CTS를 트리거합니다.
 */
class CTSTileService : TileService() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onStartListening() {
        super.onStartListening()
        // Shizuku 연결 상태에 따라 타일 상태 업데이트
        val hasPermission = try { ShizukuHelper.hasPermission() } catch (_: Exception) { false }
        qsTile?.apply {
            state = if (hasPermission) Tile.STATE_INACTIVE else Tile.STATE_UNAVAILABLE
            updateTile()
        }

        // 미리 바인딩 시작 (타일 클릭 시 즉시 응답)
        if (hasPermission) {
            CTSTrigger.bindService()
        }
    }

    override fun onClick() {
        super.onClick()

        if (!ShizukuHelper.hasPermission()) {
            Toast.makeText(this, "Shizuku가 실행 중이 아닙니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 타일 상태를 활성으로 변경
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            updateTile()
        }

        // 패널을 닫고 CTS 트리거
        collapseStatusBarPanel()

        scope.launch {
            // 바인딩이 안 되어 있으면 바인딩 후 대기
            if (!CTSTrigger.isServiceBound()) {
                CTSTrigger.bindService()
                withContext(Dispatchers.IO) {
                    var wait = 0
                    while (!CTSTrigger.isServiceBound() && wait < 2000) {
                        Thread.sleep(100)
                        wait += 100
                    }
                }
            }

            // 패널 닫힘 애니메이션 대기
            delay(300)

            val success = withContext(Dispatchers.IO) {
                CTSTrigger.trigger(applicationContext)
            }

            if (!success) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        applicationContext,
                        "CTS 트리거 실패. 설정을 확인하세요.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            // 타일 상태 복원
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                updateTile()
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun collapseStatusBarPanel() {
        try {
            val service = getSystemService("statusbar")
            val clazz = Class.forName("android.app.StatusBarManager")
            val method = clazz.getMethod("collapsePanels")
            method.invoke(service)
        } catch (_: Exception) {
            // 일부 기기에서 실패할 수 있음
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}

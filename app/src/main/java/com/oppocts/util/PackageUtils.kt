package com.oppocts.util

import android.content.pm.PackageManager

/**
 * 패키지 관련 공통 유틸리티.
 */
object PackageUtils {

    /**
     * 특정 패키지가 기기에 설치되어 있는지 확인합니다.
     */
    fun isInstalled(packageManager: PackageManager, packageName: String): Boolean {
        return try {
            packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}

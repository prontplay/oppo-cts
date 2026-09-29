package com.oppocts.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.oppocts.trigger.CTSTrigger

class OppoAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "OppoAccessibilityService"
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "OppoAccessibilityService connected successfully")
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "OppoAccessibilityService unbound")
        return super.onUnbind(intent)
    }
}

package com.ctslauncher.ui

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import com.ctslauncher.trigger.CTSTrigger

class TriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)
        val delayMs = prefs.getInt("trigger_delay_ms", 200).toLong()

        Handler(Looper.getMainLooper()).postDelayed({
            CTSTrigger.trigger(this)
        }, delayMs)

        finish()
    }
}

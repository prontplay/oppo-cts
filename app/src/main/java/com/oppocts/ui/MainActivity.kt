package com.ctslauncher.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ctslauncher.R
import com.ctslauncher.trigger.CTSTrigger

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("cts_prefs", Context.MODE_PRIVATE)
        val setupCompleted = prefs.getBoolean("setup_completed", false)

        if (!setupCompleted) {
            startActivity(Intent(this, SetupWizardActivity::class.java))
            finish()
            return
        }

        val triggerMethod = prefs.getInt("trigger_method", 0)
        if (triggerMethod == 2) {
            val delayMs = prefs.getInt("trigger_delay_ms", 200).toLong()
            Handler(Looper.getMainLooper()).postDelayed({
                val success = CTSTrigger.trigger(this)
                if (!success) {
                    Toast.makeText(this, getString(R.string.toast_cts_failed), Toast.LENGTH_SHORT).show()
                }
                finish()
            }, delayMs)
        } else {
            startActivity(Intent(this, SettingsActivity::class.java))
            finish()
        }
    }
}

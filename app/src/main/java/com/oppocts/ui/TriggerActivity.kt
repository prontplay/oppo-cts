package com.ctslauncher.ui

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.ctslauncher.R
import com.ctslauncher.trigger.CTSTrigger

class TriggerActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val success = CTSTrigger.trigger(this)
        if (!success) {
            Toast.makeText(this, getString(R.string.toast_cts_failed), Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

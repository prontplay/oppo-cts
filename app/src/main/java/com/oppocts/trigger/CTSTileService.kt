package com.ctslauncher.trigger

import android.content.Intent
import android.service.quicksettings.TileService
import com.ctslauncher.ui.TriggerActivity

class CTSTileService : TileService() {
    override fun onClick() {
        super.onClick()
        val intent = Intent(this, TriggerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivityAndCollapse(intent)
    }
}

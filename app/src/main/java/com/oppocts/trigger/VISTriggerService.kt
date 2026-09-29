package com.ctslauncher.trigger

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.ctslauncher.IVISTrigger

class VISTriggerService : Service() {
    private val binder = object : IVISTrigger.Stub {
        override fun trigger() {
            CTSTrigger.trigger(applicationContext)
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
}

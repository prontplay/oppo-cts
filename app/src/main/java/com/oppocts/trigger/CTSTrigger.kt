package com.ctslauncher.trigger

import android.content.Context
import android.content.Intent
import android.net.Uri

object CTSTrigger {
    fun trigger(context: Context): Boolean {
        return try {
            val intent = Intent(Intent.ACTION_ASSIST).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("googleassistant://")).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }
}

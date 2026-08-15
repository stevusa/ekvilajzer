package com.codex.xwaveeq

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // We deliberately avoid silently starting a foreground service here.
            // Android 12+ places restrictions on background service startup.
            context.getSharedPreferences("xwave_eq", Context.MODE_PRIVATE)
                .edit()
                .putLong("last_boot", System.currentTimeMillis())
                .apply()
        }
    }
}

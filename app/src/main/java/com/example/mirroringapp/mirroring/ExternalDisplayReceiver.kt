package com.example.mirroringapp.mirroring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ExternalDisplayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WIFI_DISPLAY_STATUS_CHANGED) {
            Log.d(TAG, "Wireless display status changed")
        }
    }

    companion object {
        private const val TAG = "ExternalDisplayReceiver"
        private const val WIFI_DISPLAY_STATUS_CHANGED = "android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED"
    }
}

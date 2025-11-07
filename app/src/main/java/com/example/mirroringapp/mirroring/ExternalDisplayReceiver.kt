package com.example.mirroringapp.mirroring

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import timber.log.Timber

class ExternalDisplayReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == WIFI_DISPLAY_STATUS_CHANGED) {
            val displayManager = context.getSystemService(DisplayManager::class.java)
            
            // Check for external displays
            val displays = displayManager?.displays
            val hasExternalDisplay = displays?.any { it.displayId != android.view.Display.DEFAULT_DISPLAY } == true
            
            Timber.d("Display status changed - External display connected: $hasExternalDisplay")
            
            // TODO: Notify user or update UI when external display connects/disconnects
            // Consider using a broadcast to MainActivity or updating a shared StateFlow
        }
    }

    companion object {
        private const val TAG = "ExternalDisplayReceiver"
        private const val WIFI_DISPLAY_STATUS_CHANGED = "android.hardware.display.action.WIFI_DISPLAY_STATUS_CHANGED"
    }
}

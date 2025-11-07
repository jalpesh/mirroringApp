package com.example.mirroringapp.mirroring

import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build

class MirroringIntentFactory(private val context: Context) {

    private val projectionManager: MediaProjectionManager? =
        context.getSystemService(MediaProjectionManager::class.java)

    fun createProjectionIntent(): Intent? {
        return projectionManager?.createScreenCaptureIntent()
    }
}

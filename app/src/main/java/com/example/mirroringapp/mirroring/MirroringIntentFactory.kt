package com.example.mirroringapp.mirroring

import android.app.Application
import android.content.Intent
import android.media.projection.MediaProjectionManager

class MirroringIntentFactory(private val app: Application) {

    private val projectionManager: MediaProjectionManager? =
        app.getSystemService(MediaProjectionManager::class.java)

    fun createProjectionIntent(): Intent? {
        return projectionManager?.createScreenCaptureIntent()
    }
}

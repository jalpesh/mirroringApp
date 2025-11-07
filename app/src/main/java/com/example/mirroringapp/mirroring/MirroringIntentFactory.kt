package com.example.mirroringapp.mirroring

import android.app.Application
import android.content.Intent
import android.hardware.display.DisplayManager
import android.media.projection.MediaProjectionManager
import android.os.Build

class MirroringIntentFactory(private val app: Application) {

    private val projectionManager: MediaProjectionManager? =
        app.getSystemService(MediaProjectionManager::class.java)

    fun createProjectionIntent(): Intent? {
        return projectionManager?.createScreenCaptureIntent()?.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                putExtra(MediaProjectionManager.EXTRA_MEDIA_PROJECTION_SOURCE, MediaProjectionManager.PROJECTION_SOURCE_SCREEN)
            }
        }
    }
}

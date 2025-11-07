package com.example.mirroringapp.mirroring

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MirroringSession(
    private val context: Context,
    private val projection: MediaProjection,
    private val connectionOption: ConnectionOption,
    private val lowLatency: Boolean,
    private val hardwareEncoder: Boolean
) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    fun start() {
        scope.launch {
            val metrics = context.resources.displayMetrics
            val targetDisplay = pickDisplay(connectionOption)
            val width = targetDisplay?.mode?.physicalWidth ?: metrics.widthPixels
            val height = targetDisplay?.mode?.physicalHeight ?: metrics.heightPixels
            val density = targetDisplay?.let { DisplayMetrics.DENSITY_DEFAULT } ?: metrics.densityDpi

            val pixelFormat = if (hardwareEncoder) PixelFormat.RGBA_8888 else PixelFormat.RGB_565
            val maxImages = if (lowLatency) 2 else 4
            imageReader = ImageReader.newInstance(
                width,
                height,
                pixelFormat,
                maxImages
            ).apply {
                setOnImageAvailableListener({ reader ->
                    reader.acquireLatestImage()?.close()
                }, Handler(Looper.getMainLooper()))
            }

            val flags = buildVirtualDisplayFlags(connectionOption)
            virtualDisplay = projection.createVirtualDisplay(
                "MirroringSession",
                width,
                height,
                density,
                flags,
                imageReader?.surface,
                null,
                null
            )
        }
    }

    fun stop() {
        scope.launch {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            projection.stop()
        }
    }

    private fun pickDisplay(option: ConnectionOption): Display? {
        return when (option) {
            ConnectionOption.USB_C -> displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.firstOrNull()
            ConnectionOption.WIFI_DIRECT, ConnectionOption.MIRACAST -> displayManager?.getDisplays()?.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        }
    }

    private fun buildVirtualDisplayFlags(option: ConnectionOption): Int {
        var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        if (option != ConnectionOption.USB_C) {
            flags = flags or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        }
        if (lowLatency && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            flags = flags or DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
        }
        return flags
    }
}

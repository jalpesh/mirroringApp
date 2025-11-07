package com.example.mirroringapp.mirroring

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Manages screen mirroring session with support for:
 * - USB-C: Direct rendering via Presentation API (zero-lag)
 * - WiFi Direct/Miracast: Hardware-encoded H.264 streaming
 */
class MirroringSession(
    private val context: Context,
    private val projection: MediaProjection,
    private val connectionOption: ConnectionOption,
    private val lowLatency: Boolean,
    private val hardwareEncoder: Boolean
) {

    private val displayManager = context.getSystemService(DisplayManager::class.java)
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)

    private var virtualDisplay: VirtualDisplay? = null
    private var presentation: MirroringPresentation? = null
    private var videoEncoder: VideoEncoder? = null
    private var renderSurface: Surface? = null

    fun start() {
        scope.launch {
            try {
                val metrics = context.resources.displayMetrics
                val targetDisplay = pickDisplay(connectionOption)
                
                if (targetDisplay == null && connectionOption == ConnectionOption.USB_C) {
                    Timber.e("No external display found for USB-C connection")
                    return@launch
                }
                
                val width = targetDisplay?.mode?.physicalWidth ?: metrics.widthPixels
                val height = targetDisplay?.mode?.physicalHeight ?: metrics.heightPixels
                val density = targetDisplay?.let { DisplayMetrics.DENSITY_DEFAULT } ?: metrics.densityDpi

                Timber.i("Starting mirroring: ${width}x${height}, connection=$connectionOption, lowLatency=$lowLatency, hwEncoder=$hardwareEncoder")

                // Choose rendering method based on connection type
                when (connectionOption) {
                    ConnectionOption.USB_C -> {
                        // Direct rendering to external display - ZERO LAG
                        startUsbCMirroring(targetDisplay!!, width, height, density)
                    }
                    ConnectionOption.WIFI_DIRECT, ConnectionOption.MIRACAST -> {
                        // Hardware-encoded streaming
                        startWirelessMirroring(width, height, density)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Failed to start mirroring")
            }
        }
    }

    private fun startUsbCMirroring(display: Display, width: Int, height: Int, density: Int) {
        try {
            // Create Presentation for direct rendering to external display
            presentation = MirroringPresentation(context, display) { surfaceHolder ->
                // Surface is ready - create virtual display that renders to it
                renderSurface = surfaceHolder.surface
                createVirtualDisplay(width, height, density, renderSurface!!)
                Timber.d("USB-C presentation surface ready")
            }
            
            presentation?.show()
            Timber.i("USB-C mirroring started with Presentation API")
        } catch (e: Exception) {
            Timber.e(e, "Failed to start USB-C mirroring")
        }
    }

    private fun startWirelessMirroring(width: Int, height: Int, density: Int) {
        try {
            // Create hardware encoder for H.264 streaming
            videoEncoder = VideoEncoder(width, height, lowLatency)
            renderSurface = videoEncoder?.start()
            
            if (renderSurface != null) {
                createVirtualDisplay(width, height, density, renderSurface!!)
                Timber.i("Wireless mirroring started with hardware encoding")
            } else {
                Timber.e("Failed to create encoder surface")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to start wireless mirroring")
        }
    }

    private fun createVirtualDisplay(width: Int, height: Int, density: Int, surface: Surface) {
        val flags = buildVirtualDisplayFlags(connectionOption)
        virtualDisplay = projection.createVirtualDisplay(
            "MirroringSession",
            width,
            height,
            density,
            flags,
            surface,
            null,
            null
        )
        Timber.d("Virtual display created: ${width}x${height}")
    }

    fun stop() {
        try {
            Timber.i("Stopping mirroring session")
            
            virtualDisplay?.release()
            virtualDisplay = null
            
            presentation?.dismiss()
            presentation = null
            
            videoEncoder?.stop()
            videoEncoder = null
            
            renderSurface?.release()
            renderSurface = null
            
            projection.stop()
            scope.cancel()
            
            Timber.i("Mirroring session stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping mirroring")
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
        return flags
    }
}

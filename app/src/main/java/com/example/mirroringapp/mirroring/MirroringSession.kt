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
                Timber.d("=== MIRRORING SESSION START ===")
                Timber.d("Connection Option: $connectionOption")
                Timber.d("Low Latency: $lowLatency")
                Timber.d("Hardware Encoder: $hardwareEncoder")
                
                val metrics = context.resources.displayMetrics
                Timber.d("Phone Display: ${metrics.widthPixels}x${metrics.heightPixels}, density=${metrics.densityDpi}")
                
                // Use USB Display Manager for USB-C connections
                var targetDisplay: Display? = null
                
                if (connectionOption == ConnectionOption.USB_C) {
                    val usbDisplayManager = UsbDisplayManager(context)
                    val usbInfo = usbDisplayManager.detectUsbDisplay()
                    
                    Timber.i(usbDisplayManager.getStatusMessage(usbInfo))
                    
                    when (usbInfo.type) {
                        UsbDisplayManager.UsbDisplayType.DISPLAYPORT_ALT_MODE -> {
                            // Direct DisplayPort - display already available
                            targetDisplay = usbInfo.externalDisplay
                            Timber.i("✅ Using DisplayPort Alt Mode - Direct rendering")
                        }
                        UsbDisplayManager.UsbDisplayType.USB_ACCESSORY -> {
                            // USB Accessory - wait for display to initialize
                            Timber.i("⏳ USB Accessory detected - Waiting for display...")
                            targetDisplay = usbDisplayManager.waitForExternalDisplay(5000)
                            
                            if (targetDisplay != null) {
                                Timber.i("✅ USB Accessory display ready")
                            } else {
                                Timber.w("⚠️ USB Accessory display not ready yet")
                            }
                        }
                        UsbDisplayManager.UsbDisplayType.UNKNOWN -> {
                            Timber.w("⚠️ USB connection detected but display not ready")
                            // Try to get display anyway
                            targetDisplay = usbDisplayManager.getExternalDisplay()
                        }
                        UsbDisplayManager.UsbDisplayType.NONE -> {
                            Timber.e("❌ No USB display connection detected")
                        }
                    }
                    
                    if (targetDisplay == null) {
                        Timber.e("❌ CRITICAL: No external display found for USB-C connection")
                        Timber.e("USB-C mirroring requires an external display to be connected")
                        Timber.e("Possible causes:")
                        Timber.e("  1. Adapter not fully connected")
                        Timber.e("  2. HDMI cable not plugged into TV")
                        Timber.e("  3. TV not on or wrong HDMI input selected")
                        Timber.e("  4. Adapter needs time to initialize (wait a few seconds)")
                        return@launch
                    }
                } else {
                    // Wireless connection - use phone display
                    targetDisplay = pickDisplay(connectionOption)
                }
                
                val width = targetDisplay?.mode?.physicalWidth ?: metrics.widthPixels
                val height = targetDisplay?.mode?.physicalHeight ?: metrics.heightPixels
                val density = targetDisplay?.let { DisplayMetrics.DENSITY_DEFAULT } ?: metrics.densityDpi

                Timber.i("✅ Target Display Selected: ${width}x${height} @ ${density}dpi")
                Timber.i("Starting mirroring: connection=$connectionOption, lowLatency=$lowLatency, hwEncoder=$hardwareEncoder")

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

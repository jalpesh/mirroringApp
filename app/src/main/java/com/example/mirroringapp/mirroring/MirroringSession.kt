package com.example.mirroringapp.mirroring

import android.Manifest
import android.app.Presentation
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.Display
import android.view.Surface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private var presentation: Presentation? = null
    private var activeSurface: Surface? = null
    private var handlerThread: HandlerThread? = null
    private var projectionStopped = false

    private var targetWidth: Int = 0
    private var targetHeight: Int = 0
    private var targetDensity: Int = 0

    fun start() {
        projectionStopped = false
        scope.launch {
            val metrics = context.resources.displayMetrics
            val targetDisplay = pickDisplay(connectionOption)
            configureTargetDimensions(metrics, targetDisplay)

            if (connectionOption == ConnectionOption.USB_C && targetDisplay != null) {
                withContext(Dispatchers.Main) {
                    presentation = ExternalDisplayPresentation(
                        context = context,
                        display = targetDisplay,
                        onSurfaceReady = ::onSurfaceReady,
                        onSurfaceDestroyed = ::onSurfaceDestroyed
                    ).also { it.show() }
                }
            } else {
                val fallbackSurface = createFallbackSurface(targetWidth, targetHeight)
                onSurfaceReady(fallbackSurface)
            }
        }
    }

    fun stop() {
        scope.launch {
            releaseVirtualDisplay()
            releaseSurface()
            imageReader?.close()
            imageReader = null
            handlerThread?.quitSafely()
            handlerThread = null
            withContext(Dispatchers.Main) {
                presentation?.dismiss()
                presentation = null
            }
            if (!projectionStopped) {
                projection.stop()
                projectionStopped = true
            }
        }
    }

    private fun configureTargetDimensions(metrics: DisplayMetrics, display: Display?) {
        targetWidth = display?.mode?.physicalWidth ?: metrics.widthPixels
        targetHeight = display?.mode?.physicalHeight ?: metrics.heightPixels
        targetDensity = display?.let { DisplayMetrics.DENSITY_DEFAULT } ?: metrics.densityDpi
        if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bestMode = display.supportedModes.maxByOrNull { it.refreshRate }
            if (bestMode != null) {
                targetWidth = bestMode.physicalWidth
                targetHeight = bestMode.physicalHeight
            }
        }
    }

    private fun createFallbackSurface(width: Int, height: Int): Surface {
        val pixelFormat = if (hardwareEncoder) PixelFormat.RGBA_8888 else PixelFormat.RGB_565
        val maxImages = if (lowLatency) 2 else 4
        imageReader?.close()
        imageReader = ImageReader.newInstance(
            width,
            height,
            pixelFormat,
            maxImages
        ).apply {
            ensureHandlerThread()
            setOnImageAvailableListener({ reader ->
                reader.acquireLatestImage()?.close()
            }, handlerThread?.looper?.let { Handler(it) })
        }
        return imageReader!!.surface
    }

    private fun ensureHandlerThread() {
        if (handlerThread?.isAlive == true) return
        handlerThread = HandlerThread("MirroringImageReader").apply { start() }
    }

    private fun onSurfaceReady(surface: Surface) {
        scope.launch {
            activeSurface = surface
            createVirtualDisplay(surface)
        }
    }

    private fun onSurfaceDestroyed(surface: Surface) {
        scope.launch {
            if (activeSurface == surface) {
                releaseVirtualDisplay()
                releaseSurface()
            }
        }
    }

    private fun createVirtualDisplay(surface: Surface) {
        releaseVirtualDisplay()
        val flags = buildVirtualDisplayFlags(connectionOption)
        virtualDisplay = projection.createVirtualDisplay(
            "MirroringSession",
            targetWidth,
            targetHeight,
            targetDensity,
            flags,
            surface,
            null,
            null
        )
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun releaseSurface() {
        val surface = activeSurface
        if (surface != null && surface != imageReader?.surface && surface.isValid) {
            surface.release()
        }
        activeSurface = null
    }

    private fun pickDisplay(option: ConnectionOption): Display? {
        return when (option) {
            ConnectionOption.USB_C -> displayManager?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)?.firstOrNull()
            ConnectionOption.WIFI_DIRECT, ConnectionOption.MIRACAST ->
                displayManager?.getDisplays()?.firstOrNull { it.displayId != Display.DEFAULT_DISPLAY }
        }
    }

    private fun buildVirtualDisplayFlags(option: ConnectionOption): Int {
        var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        if (option != ConnectionOption.USB_C) {
            flags = flags or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        }
        if (lowLatency && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && hasTrustedDisplayPermission()) {
            flags = flags or DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
        }
        return flags
    }

    private fun hasTrustedDisplayPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.ADD_TRUSTED_DISPLAY) ==
            PackageManager.PERMISSION_GRANTED
    }
}

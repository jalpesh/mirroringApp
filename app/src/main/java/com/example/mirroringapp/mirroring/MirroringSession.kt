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
import android.util.Log
import android.view.Display
import android.view.Surface
import com.example.mirroringapp.util.PerformanceLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val scopeJob = SupervisorJob()
    private val scope = CoroutineScope(scopeJob + Dispatchers.IO)

    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var presentation: Presentation? = null
    private var activeSurface: Surface? = null
    private var handlerThread: HandlerThread? = null
    private var videoEncoder: VideoEncoder? = null
    private var projectionStopped = false
    private var cleanupJob: Job? = null

    private val performanceLogger = PerformanceLogger()

    private var targetWidth: Int = 0
    private var targetHeight: Int = 0
    private var targetDensity: Int = 0

    fun start() {
        projectionStopped = false
        performanceLogger.reset()
        scope.launch {
            val metrics = context.resources.displayMetrics
            val targetDisplay = pickDisplay(connectionOption)
            configureTargetDimensions(metrics, targetDisplay)

            when {
                connectionOption == ConnectionOption.USB_C && targetDisplay != null -> {
                    withContext(Dispatchers.Main) {
                        presentation = ExternalDisplayPresentation(
                            context = context,
                            display = targetDisplay,
                            onSurfaceReady = ::onSurfaceReady,
                            onSurfaceDestroyed = ::onSurfaceDestroyed
                        ).also { it.show() }
                    }
                }
                connectionOption != ConnectionOption.USB_C && hardwareEncoder -> {
                    val encoderSurface = prepareVideoEncoder()
                    if (encoderSurface != null) {
                        onSurfaceReady(encoderSurface)
                    } else {
                        val fallbackSurface = createFallbackSurface(targetWidth, targetHeight)
                        onSurfaceReady(fallbackSurface)
                    }
                }
                else -> {
                    val fallbackSurface = createFallbackSurface(targetWidth, targetHeight)
                    onSurfaceReady(fallbackSurface)
                }
            }
        }
    }

    fun stop() {
        if (cleanupJob != null) return

        cleanupJob = scope.launch {
            releaseVirtualDisplay()
            releaseSurface()
            releaseVideoEncoder()
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
            performanceLogger.reset()
        }.also { job ->
            job.invokeOnCompletion {
                scopeJob.cancel()
            }
        }
    }

    private fun configureTargetDimensions(metrics: DisplayMetrics, display: Display?) {
        if (display != null) {
            val displayMetrics = runCatching {
                context.createDisplayContext(display).resources.displayMetrics
            }.getOrDefault(metrics)

            targetWidth = displayMetrics.widthPixels.takeIf { it > 0 } ?: metrics.widthPixels
            targetHeight = displayMetrics.heightPixels.takeIf { it > 0 } ?: metrics.heightPixels
            targetDensity = displayMetrics.densityDpi.takeIf { it > 0 } ?: metrics.densityDpi

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val bestMode = display.supportedModes
                    .filter { it.modeId == display.mode.modeId || it.refreshRate >= display.refreshRate }
                    .maxByOrNull { it.refreshRate }
                if (bestMode != null) {
                    targetWidth = bestMode.physicalWidth
                    targetHeight = bestMode.physicalHeight
                }
            }
        } else {
            targetWidth = metrics.widthPixels
            targetHeight = metrics.heightPixels
            targetDensity = metrics.densityDpi
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
                reader.acquireLatestImage()?.let { image ->
                    performanceLogger.onFrame(image.timestamp)
                    image.close()
                }
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
                releaseVideoEncoder()
            }
        }
    }

    private fun createVirtualDisplay(surface: Surface) {
        releaseVirtualDisplay()
        val initialFlags = buildVirtualDisplayFlags(connectionOption)
        virtualDisplay = try {
            projection.createVirtualDisplay(
                "MirroringSession",
                targetWidth,
                targetHeight,
                targetDensity,
                initialFlags,
                surface,
                null,
                null
            )
        } catch (security: SecurityException) {
            val needsFallback =
                initialFlags and DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED != 0
            if (needsFallback) {
                Log.w(TAG, "Trusted flag rejected; retrying without it", security)
                projection.createVirtualDisplay(
                    "MirroringSession",
                    targetWidth,
                    targetHeight,
                    targetDensity,
                    buildVirtualDisplayFlags(connectionOption, includeTrustedFlag = false),
                    surface,
                    null,
                    null
                )
            } else {
                throw security
            }
        }
    }

    private fun releaseVirtualDisplay() {
        virtualDisplay?.release()
        virtualDisplay = null
    }

    private fun releaseSurface() {
        val surface = activeSurface
        val encoderSurface = videoEncoder?.getInputSurface()
        if (surface != null && surface != imageReader?.surface && surface != encoderSurface && surface.isValid) {
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

    private fun buildVirtualDisplayFlags(
        option: ConnectionOption,
        includeTrustedFlag: Boolean = true,
    ): Int {
        var flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
        if (option != ConnectionOption.USB_C) {
            flags = flags or DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
        }
        if (
            includeTrustedFlag &&
            lowLatency &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            hasTrustedDisplayPermission()
        ) {
            flags = flags or DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
        }
        return flags
    }

    private fun hasTrustedDisplayPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            context.checkSelfPermission(Manifest.permission.ADD_TRUSTED_DISPLAY) ==
            PackageManager.PERMISSION_GRANTED
    }

    private fun prepareVideoEncoder(): Surface? {
        releaseVideoEncoder()
        val bitrate = calculateBitrate()
        val encoder = VideoEncoder(
            width = targetWidth,
            height = targetHeight,
            bitrate = bitrate,
            frameRate = 60,
            iFrameIntervalSeconds = if (lowLatency) 1 else 2,
            performanceLogger = performanceLogger
        )
        return runCatching {
            encoder.prepare()
            videoEncoder = encoder
            encoder.getInputSurface()
        }.onFailure { throwable ->
            encoder.release()
            performanceLogger.onError(throwable)
            Log.e(TAG, "Falling back to ImageReader after encoder failure", throwable)
        }.getOrNull()
    }

    private fun releaseVideoEncoder() {
        videoEncoder?.release()
        videoEncoder = null
    }

    private fun calculateBitrate(): Int {
        val pixels = targetWidth.toLong() * targetHeight.toLong()
        val multiplier = if (lowLatency) 2L else 4L
        val candidate = pixels * 4L * multiplier
        return candidate.coerceIn(2_000_000L, 40_000_000L).toInt()
    }

    companion object {
        private const val TAG = "MirroringSession"
    }
}

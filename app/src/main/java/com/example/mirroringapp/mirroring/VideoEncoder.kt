package com.example.mirroringapp.mirroring

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import com.example.mirroringapp.util.PerformanceLogger

/**
 * Simple H.264 encoder wrapper that exposes the input [Surface] used by wireless mirroring
 * pathways. Encoded frames are currently dropped, but we keep logging so the user can inspect
 * throughput and frame pacing while evaluating network options.
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val bitrate: Int,
    private val frameRate: Int,
    private val iFrameIntervalSeconds: Int,
    private val performanceLogger: PerformanceLogger
) {

    private var codec: MediaCodec? = null
    private var inputSurface: Surface? = null
    private var handlerThread: HandlerThread? = null

    @Throws(Exception::class)
    fun prepare() {
        if (codec != null) return

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iFrameIntervalSeconds)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setInteger(MediaFormat.KEY_PRIORITY, 0) // realtime
            }
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        handlerThread = HandlerThread("MirroringVideoEncoder").apply { start() }
        val callback = object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                // No-op. We rely on the surface input path.
            }

            override fun onOutputBufferAvailable(codec: MediaCodec, index: Int, info: MediaCodec.BufferInfo) {
                performanceLogger.onFrame()
                codec.releaseOutputBuffer(index, false)
            }

            override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                performanceLogger.onError(e)
            }

            override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                // Format changes are logged implicitly through frame pacing updates.
            }
        }

        val handler = handlerThread?.looper?.let { Handler(it) }
        if (handler != null) {
            encoder.setCallback(callback, handler)
        } else {
            encoder.setCallback(callback)
        }
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        inputSurface = encoder.createInputSurface()
        encoder.start()
        codec = encoder
    }

    fun getInputSurface(): Surface? = inputSurface

    fun release() {
        codec?.let { encoder ->
            runCatching { encoder.stop() }
            encoder.release()
        }
        codec = null
        inputSurface?.release()
        inputSurface = null
        handlerThread?.quitSafely()
        handlerThread = null
    }
}

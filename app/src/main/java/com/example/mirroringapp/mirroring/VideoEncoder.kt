package com.example.mirroringapp.mirroring

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.view.Surface
import timber.log.Timber

/**
 * Hardware-accelerated video encoder for wireless mirroring.
 * Uses H.264 encoding optimized for low latency.
 */
class VideoEncoder(
    private val width: Int,
    private val height: Int,
    private val lowLatency: Boolean
) {
    private var encoder: MediaCodec? = null
    private var inputSurface: Surface? = null

    fun start(): Surface? {
        return try {
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                // Color format for hardware encoding
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                
                // Bitrate - higher for better quality, lower for less lag
                val bitrate = if (lowLatency) {
                    // Lower bitrate for faster encoding
                    width * height * 2
                } else {
                    // Higher bitrate for better quality
                    width * height * 4
                }
                setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
                
                // Frame rate
                setInteger(MediaFormat.KEY_FRAME_RATE, 60)
                
                // I-frame interval - lower for less lag but more bandwidth
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, if (lowLatency) 1 else 2)
                
                // Low latency mode
                if (lowLatency) {
                    setInteger(MediaFormat.KEY_LATENCY, 0)
                    setInteger(MediaFormat.KEY_PRIORITY, 0) // Realtime priority
                }
                
                // Profile and level
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                inputSurface = createInputSurface()
                
                // Set callback for encoded data
                setCallback(object : MediaCodec.Callback() {
                    override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                        // Not used with Surface input
                    }

                    override fun onOutputBufferAvailable(
                        codec: MediaCodec,
                        index: Int,
                        info: MediaCodec.BufferInfo
                    ) {
                        try {
                            val outputBuffer = codec.getOutputBuffer(index)
                            if (outputBuffer != null && info.size > 0) {
                                // TODO: Send encoded data over network for WiFi Direct/Miracast
                                // For now, just release the buffer
                                Timber.v("Encoded frame: ${info.size} bytes")
                            }
                            codec.releaseOutputBuffer(index, false)
                        } catch (e: Exception) {
                            Timber.e(e, "Error processing encoded buffer")
                        }
                    }

                    override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                        Timber.e(e, "Encoder error")
                    }

                    override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                        Timber.d("Encoder format changed: $format")
                    }
                })
                
                start()
            }

            Timber.i("VideoEncoder started: ${width}x${height}, lowLatency=$lowLatency")
            inputSurface
        } catch (e: Exception) {
            Timber.e(e, "Failed to start encoder")
            null
        }
    }

    fun stop() {
        try {
            encoder?.stop()
            encoder?.release()
            encoder = null
            inputSurface?.release()
            inputSurface = null
            Timber.i("VideoEncoder stopped")
        } catch (e: Exception) {
            Timber.e(e, "Error stopping encoder")
        }
    }
}

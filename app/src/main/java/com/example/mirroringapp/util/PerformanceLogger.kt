package com.example.mirroringapp.util

import android.util.Log

/**
 * Performance logging utility for measuring frame timing and lag.
 * Essential for debugging and optimizing mirroring performance.
 */
object PerformanceLogger {
    private const val TAG = "MirroringPerf"
    private var frameCount = 0L
    private var lastLogTime = 0L
    private var totalFrameTime = 0L
    private var minFrameTime = Long.MAX_VALUE
    private var maxFrameTime = 0L

    fun logFrameStart(): Long {
        return System.nanoTime()
    }

    fun logFrameEnd(startTime: Long) {
        val frameTime = (System.nanoTime() - startTime) / 1_000_000 // Convert to ms
        frameCount++
        totalFrameTime += frameTime
        minFrameTime = minOf(minFrameTime, frameTime)
        maxFrameTime = maxOf(maxFrameTime, frameTime)

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastLogTime >= 1000) { // Log every second
            val avgFrameTime = totalFrameTime / frameCount
            val fps = 1000.0 / avgFrameTime
            
            Log.i(TAG, """
                Performance Stats:
                - FPS: ${"%.2f".format(fps)}
                - Avg Frame Time: ${avgFrameTime}ms
                - Min Frame Time: ${minFrameTime}ms
                - Max Frame Time: ${maxFrameTime}ms
                - Total Frames: $frameCount
            """.trimIndent())

            // Reset for next interval
            lastLogTime = currentTime
            frameCount = 0
            totalFrameTime = 0
            minFrameTime = Long.MAX_VALUE
            maxFrameTime = 0
        }
    }

    fun logError(message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    fun logInfo(message: String) {
        Log.i(TAG, message)
    }

    fun logWarning(message: String) {
        Log.w(TAG, message)
    }
}

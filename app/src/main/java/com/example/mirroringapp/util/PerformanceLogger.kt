package com.example.mirroringapp.util

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight performance tracker that logs frames per second and frame timing stats for the
 * active mirroring pipeline. The logger is intentionally allocation-free on the hot path.
 */
class PerformanceLogger(private val tag: String = "MirroringPerf") {

    private var windowStartNs: Long = 0L
    private var lastFrameTimestampNs: Long = 0L
    private var framesInWindow: Int = 0
    private var minFrameDeltaNs: Long = Long.MAX_VALUE
    private var maxFrameDeltaNs: Long = Long.MIN_VALUE

    @Synchronized
    fun onFrame(timestampNs: Long = System.nanoTime()) {
        if (windowStartNs == 0L) {
            windowStartNs = timestampNs
            lastFrameTimestampNs = timestampNs
            framesInWindow = 1
            return
        }

        val deltaNs = timestampNs - lastFrameTimestampNs
        if (deltaNs > 0) {
            minFrameDeltaNs = min(minFrameDeltaNs, deltaNs)
            maxFrameDeltaNs = max(maxFrameDeltaNs, deltaNs)
        }
        lastFrameTimestampNs = timestampNs
        framesInWindow++

        val elapsedNs = timestampNs - windowStartNs
        if (elapsedNs >= TimeUnit.SECONDS.toNanos(1)) {
            val fps = framesInWindow.toDouble() * TimeUnit.SECONDS.toNanos(1).toDouble() / elapsedNs
            val averageDeltaMs = if (framesInWindow > 1) {
                (elapsedNs.toDouble() / (framesInWindow - 1)) / TimeUnit.MILLISECONDS.toNanos(1)
            } else {
                0.0
            }
            val minDeltaMs = if (minFrameDeltaNs != Long.MAX_VALUE) {
                minFrameDeltaNs.toDouble() / TimeUnit.MILLISECONDS.toNanos(1)
            } else {
                0.0
            }
            val maxDeltaMs = if (maxFrameDeltaNs != Long.MIN_VALUE) {
                maxFrameDeltaNs.toDouble() / TimeUnit.MILLISECONDS.toNanos(1)
            } else {
                0.0
            }
            Log.d(tag, "fps=%.1f frame(ms) min/avg/max=%.2f/%.2f/%.2f".format(fps, minDeltaMs, averageDeltaMs, maxDeltaMs))
            resetLocked(timestampNs)
        }
    }

    @Synchronized
    fun onError(throwable: Throwable) {
        Log.e(tag, "Mirroring pipeline error", throwable)
        resetLocked(System.nanoTime())
    }

    @Synchronized
    fun reset() {
        resetLocked(0L)
    }

    private fun resetLocked(referenceTimestampNs: Long) {
        windowStartNs = referenceTimestampNs
        lastFrameTimestampNs = referenceTimestampNs
        framesInWindow = if (referenceTimestampNs == 0L) 0 else 1
        minFrameDeltaNs = Long.MAX_VALUE
        maxFrameDeltaNs = Long.MIN_VALUE
    }
}

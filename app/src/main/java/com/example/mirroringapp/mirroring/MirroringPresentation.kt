package com.example.mirroringapp.mirroring

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Presentation for direct rendering to external display via USB-C.
 * This provides zero-lag mirroring by rendering directly to the external display
 * without encoding or network transmission.
 */
class MirroringPresentation(
    outerContext: Context,
    display: Display,
    private val onSurfaceReady: (SurfaceHolder) -> Unit
) : Presentation(outerContext, display) {

    private var surfaceView: SurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Create full-screen surface for rendering
        surfaceView = SurfaceView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    // Surface is ready - notify session to start rendering
                    onSurfaceReady(holder)
                }

                override fun surfaceChanged(
                    holder: SurfaceHolder,
                    format: Int,
                    width: Int,
                    height: Int
                ) {
                    // Surface dimensions changed
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    // Surface destroyed - stop rendering
                }
            })
        }

        val container = FrameLayout(context)
        container.addView(surfaceView)
        setContentView(container)
    }

    override fun onStop() {
        super.onStop()
        surfaceView = null
    }
}

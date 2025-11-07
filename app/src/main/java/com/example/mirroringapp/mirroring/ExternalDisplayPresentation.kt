package com.example.mirroringapp.mirroring

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Surface
import android.view.SurfaceTexture
import android.view.TextureView
import android.view.ViewGroup.LayoutParams.MATCH_PARENT

/**
 * Presentation that owns a [TextureView] surface used as the sink for the mirrored content.
 * The surface lifecycle is forwarded to the caller so the mirroring session can drive the
 * [MediaProjection] virtual display directly into the HDMI backed display.
 */
class ExternalDisplayPresentation(
    context: Context,
    display: android.view.Display,
    private val onSurfaceReady: (Surface) -> Unit,
    private val onSurfaceDestroyed: (Surface) -> Unit
) : Presentation(context, display), TextureView.SurfaceTextureListener {

    private lateinit var textureView: TextureView
    private var activeSurface: Surface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        textureView = TextureView(context).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            surfaceTextureListener = this@ExternalDisplayPresentation
            isOpaque = true
        }
        setContentView(textureView)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val presentationSurface = Surface(surface)
        activeSurface = presentationSurface
        onSurfaceReady(presentationSurface)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        // Surface size follows the external display; no-op because the virtual display
        // will be recreated if necessary by the mirroring session.
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        activeSurface?.let(onSurfaceDestroyed)
        activeSurface = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // No-op. The HDMI sink handles presenting the frames.
    }
}

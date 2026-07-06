package com.sketcher.sketchercompanionv1.projection

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.dto.FillStyle

class WirelessProjectionManager(private val context: Context) {

    private var presentation: CanvasPresentation? = null
    private val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    var isActive = false
        private set

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {
            Log.d("WirelessProjection", "Display added: $displayId")
            if (isActive) {
                // Delay slightly to let the display initialize
                startPresentation()
            }
        }

        override fun onDisplayRemoved(displayId: Int) {
            Log.d("WirelessProjection", "Display removed: $displayId")
            if (presentation?.display?.displayId == displayId) {
                stopPresentation(onlyDismiss = true)
            }
        }

        override fun onDisplayChanged(displayId: Int) {}
    }

    fun start() {
        if (isActive) return
        isActive = true
        displayManager.registerDisplayListener(displayListener, null)
        startPresentation()
    }

    fun stop() {
        if (!isActive) return
        isActive = false
        displayManager.unregisterDisplayListener(displayListener)
        stopPresentation(onlyDismiss = false)
    }

    private fun startPresentation() {
        presentation?.dismiss()
        presentation = null

        val displays = displayManager.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        if (displays.isNotEmpty()) {
            val targetDisplay = displays[0]
            Log.d("WirelessProjection", "Starting presentation on display: ${targetDisplay.name}")
            try {
                presentation = CanvasPresentation(context, targetDisplay)
                presentation?.show()
            } catch (e: Exception) {
                Log.e("WirelessProjection", "Failed to show presentation", e)
            }
        } else {
            Log.d("WirelessProjection", "No presentation display found to start presentation")
        }
    }

    private fun stopPresentation(onlyDismiss: Boolean) {
        presentation?.dismiss()
        presentation = null
        if (onlyDismiss) {
            Log.d("WirelessProjection", "Presentation dismissed because display was removed")
        } else {
            Log.d("WirelessProjection", "Presentation stopped manually")
        }
    }

    fun updateCanvas(
        layers: List<Layer>,
        componentLibrary: Map<String, ComponentDefinition>,
        backgroundStyle: FillStyle,
        cameraMatrixValues: FloatArray,
        phoneW: Float,
        phoneH: Float,
        strokeColor: Int,
        fillColor: Int,
        isStrokeActive: Boolean,
        isFillActive: Boolean,
        fillStyle: FillStyle?,
        strokeStyle: FillStyle?,
        livePoints: List<com.sketcher.sketchercompanionv1.StrokePoint>?,
        livePath: android.graphics.Path?,
        committedPath: android.graphics.Path?,
        liveFillPath: android.graphics.Path?,
        liveRadius: Float
    ) {
        presentation?.updateCanvas(
            layers = layers,
            componentLibrary = componentLibrary,
            backgroundStyle = backgroundStyle,
            cameraMatrixValues = cameraMatrixValues,
            phoneW = phoneW,
            phoneH = phoneH,
            strokeColor = strokeColor,
            fillColor = fillColor,
            isStrokeActive = isStrokeActive,
            isFillActive = isFillActive,
            fillStyle = fillStyle,
            strokeStyle = strokeStyle,
            livePoints = livePoints,
            livePath = livePath,
            committedPath = committedPath,
            liveFillPath = liveFillPath,
            liveRadius = liveRadius
        )
    }
}

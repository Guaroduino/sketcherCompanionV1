package com.sketcher.sketchercompanionv1.projection

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.ViewGroup
import android.widget.FrameLayout
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.dto.FillStyle

class CanvasPresentation(
    outerContext: Context,
    display: Display
) : Presentation(outerContext, display) {

    private lateinit var canvasView: PresentationCanvasView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        canvasView = PresentationCanvasView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        root.addView(canvasView)
        setContentView(root)
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
        if (::canvasView.isInitialized) {
            canvasView.updateState(
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
}

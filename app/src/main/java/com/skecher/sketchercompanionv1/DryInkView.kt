package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

class DryInkView(context: Context) : View(context) {
    private val strokes = mutableListOf<Stroke>()
    private val renderer = CanvasStrokeRenderer.create()
    private val matrix = Matrix()

    init {
        // Aceleración de hardware para mejor rendimiento
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun addStrokes(newStrokes: Collection<Stroke>) {
        strokes.addAll(newStrokes)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokes.forEach { stroke ->
            renderer.draw(canvas, stroke, matrix)
        }
    }
}
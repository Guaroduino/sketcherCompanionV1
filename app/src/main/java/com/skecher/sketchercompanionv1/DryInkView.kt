package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

class DryInkView(context: Context) : View(context) {
    private val strokes = mutableListOf<Stroke>()
    private val redoStack = mutableListOf<Stroke>()
    private val renderer = CanvasStrokeRenderer.create()
    private val drawMatrix = Matrix()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun addStrokes(newStrokes: Collection<Stroke>) {
        if (newStrokes.isNotEmpty()) {
            strokes.addAll(newStrokes)
            redoStack.clear()
            invalidate()
        }
    }

    fun clearCanvas() {
        strokes.clear()
        redoStack.clear()
        invalidate()
    }

    fun undo() {
        if (strokes.isNotEmpty()) {
            redoStack.add(strokes.removeAt(strokes.lastIndex))
            invalidate()
        }
    }

    fun redo() {
        if (redoStack.isNotEmpty()) {
            strokes.add(redoStack.removeAt(redoStack.lastIndex))
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (stroke in strokes) {
            renderer.draw(canvas, stroke, drawMatrix)
        }
    }
}
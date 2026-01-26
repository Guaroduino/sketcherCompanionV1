package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

class SketcherCanvasView(context: Context) : View(context) {

    private val viewMatrix = Matrix()
    private val strokeRenderer = CanvasStrokeRenderer.create()
    private val strokes = mutableListOf<Stroke>()

    /**
     * FIX ROTACIÓN: Usamos 'post' para asegurar que la invalidación
     * ocurra cuando la vista ya esté adjunta y medida.
     */
    fun restoreStrokes(savedStrokes: List<Stroke>) {
        strokes.clear()
        strokes.addAll(savedStrokes)
        // Importante: postInvalidate asegura que se ejecute en el hilo UI en el momento correcto
        post {
            requestLayout()
            invalidate()
        }
    }

    fun addStroke(stroke: Stroke) {
        strokes.add(stroke)
        invalidate()
    }

    fun eraseStrokeAt(worldX: Float, worldY: Float): Stroke? {
        for (i in strokes.indices.reversed()) {
            val stroke = strokes[i]
            if (StrokeGeometry.isStrokeTouched(stroke, worldX, worldY)) {
                strokes.removeAt(i)
                invalidate()
                return stroke
            }
        }
        return null
    }

    fun setCameraMatrix(matrix: Matrix) {
        viewMatrix.set(matrix)
        invalidate()
    }

    fun clearCanvas() {
        strokes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(viewMatrix)
        for (stroke in strokes) {
            strokeRenderer.draw(canvas, stroke, Matrix())
        }
        canvas.restore()
    }
}
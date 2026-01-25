package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import java.util.Collections

class DryInkView(context: Context) : View(context) {
    private val strokes = Collections.synchronizedList(mutableListOf<Stroke>())
    private val renderer by lazy { CanvasStrokeRenderer.create() }
    private val currentMatrix = Matrix() // Esta es la cámara

    private val undoStack = mutableListOf<List<Stroke>>()
    private val redoStack = mutableListOf<List<Stroke>>()

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setMatrix(matrix: Matrix) {
        currentMatrix.set(matrix)
        invalidate()
    }

    fun addStrokes(newStrokes: Collection<Stroke>) {
        if (newStrokes.isNotEmpty()) {
            synchronized(strokes) {
                undoStack.add(ArrayList(strokes))
                redoStack.clear()
                strokes.addAll(newStrokes)
            }
            invalidate()
        }
    }

    fun undo() {
        synchronized(strokes) {
            if (undoStack.isNotEmpty()) {
                redoStack.add(ArrayList(strokes))
                val previous = undoStack.removeAt(undoStack.lastIndex)
                strokes.clear()
                strokes.addAll(previous)
                invalidate()
            }
        }
    }

    fun redo() {
        synchronized(strokes) {
            if (redoStack.isNotEmpty()) {
                undoStack.add(ArrayList(strokes))
                val next = redoStack.removeAt(redoStack.lastIndex)
                strokes.clear()
                strokes.addAll(next)
                invalidate()
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // DIBUJADO CLÁSICO (Compatible con tu versión buena)
        // Pasamos la matriz al renderer, NO transformamos el canvas global.
        synchronized(strokes) {
            for (stroke in strokes) {
                renderer.draw(canvas, stroke, currentMatrix)
            }
        }
    }
}
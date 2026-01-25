package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke
import java.util.Collections

class DryInkView(context: Context) : View(context) {
    // Usamos una lista sincronizada para evitar choques de hilos
    private val strokes = Collections.synchronizedList(mutableListOf<Stroke>())
    
    // Inicialización perezosa del renderer para evitar errores de contexto
    private val renderer by lazy { CanvasStrokeRenderer.create() }
    
    private var currentMatrix = Matrix()

    private val undoStack = mutableListOf<List<Stroke>>()
    private val redoStack = mutableListOf<List<Stroke>>()

    fun setMatrix(matrix: Matrix) {
        currentMatrix.set(matrix)
        postInvalidate()
    }

    fun addStrokes(newStrokes: Collection<Stroke>) {
        if (newStrokes.isNotEmpty()) {
            synchronized(strokes) {
                // Guardamos copia para undo
                undoStack.add(ArrayList(strokes))
                redoStack.clear()
                
                // Añadimos los nuevos trazos
                strokes.addAll(newStrokes)
            }
            // postInvalidate es seguro de llamar desde cualquier hilo (background o UI)
            postInvalidate()
        }
    }

    fun undo() {
        synchronized(strokes) {
            if (undoStack.isNotEmpty()) {
                redoStack.add(ArrayList(strokes))
                val previous = undoStack.removeAt(undoStack.lastIndex)
                strokes.clear()
                strokes.addAll(previous)
            }
        }
        postInvalidate()
    }

    fun redo() {
        synchronized(strokes) {
            if (redoStack.isNotEmpty()) {
                undoStack.add(ArrayList(strokes))
                val next = redoStack.removeAt(redoStack.lastIndex)
                strokes.clear()
                strokes.addAll(next)
            }
        }
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // Sincronizamos el bloque de dibujo para que nadie toque la lista mientras dibujamos
        synchronized(strokes) {
            if (strokes.isEmpty()) return

            for (stroke in strokes) {
                try {
                    // Dibujamos cada trazo
                    renderer.draw(canvas, stroke, currentMatrix)
                } catch (e: Exception) {
                    // Si un trazo nativo falla, evitamos que la app se cierre
                    e.printStackTrace()
                }
            }
        }
    }
}
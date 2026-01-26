package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

/**
 * Vista de renderizado VECTORIAL optimizada.
 * Soporta Zoom Infinito y edición (borrado) de trazos.
 */
class SketcherCanvasView(context: Context) : View(context) {

    // Matriz de la cámara (Zoom/Pan)
    private val viewMatrix = Matrix()
    
    // Renderer oficial de Google (Hardware Accelerated)
    private val strokeRenderer = CanvasStrokeRenderer.create()

    // Lista maestra de todos los trazos en el "Mundo"
    private val strokes = mutableListOf<Stroke>()

    /**
     * Agrega un trazo nuevo a la lista y actualiza la vista.
     */
    fun addStroke(stroke: Stroke) {
        strokes.add(stroke)
        invalidate() // Pide a Android que llame a onDraw
    }

    /**
     * NUEVO: Busca y elimina un trazo en las coordenadas dadas.
     * Itera desde el último trazo (el más nuevo) hacia atrás, para borrar lo que está "encima".
     *
     * @param worldX Coordenada X en el mundo (no en pantalla)
     * @param worldY Coordenada Y en el mundo (no en pantalla)
     * @return true si se borró algo, false si no se tocó nada.
     */
    fun eraseStrokeAt(worldX: Float, worldY: Float): Boolean {
        // Recorremos la lista al revés para borrar primero el trazo más reciente (visual layer order)
        for (i in strokes.indices.reversed()) {
            val stroke = strokes[i]
            
            // Usamos la lógica geométrica del archivo StrokeGeometry.kt
            if (StrokeGeometry.isStrokeTouched(stroke, worldX, worldY)) {
                strokes.removeAt(i)
                invalidate() // Redibujamos la vista sin el trazo eliminado
                return true
            }
        }
        return false
    }

    /**
     * Actualiza la transformación de la cámara.
     * Al usar vectores, el redibujado siempre es nítido al nuevo nivel de zoom.
     */
    fun setCameraMatrix(matrix: Matrix) {
        viewMatrix.set(matrix)
        invalidate()
    }

    /**
     * Limpia todo el lienzo (útil para un botón de "Nuevo Dibujo").
     */
    fun clearCanvas() {
        strokes.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 1. Aplicamos la transformación de la cámara al Canvas completo
        canvas.save()
        canvas.concat(viewMatrix)
        
        // 2. Dibujamos todos los trazos vectoriales.
        // CanvasStrokeRenderer es muy eficiente y maneja la intersección de curvas correctamente.
        for (stroke in strokes) {
            strokeRenderer.draw(canvas, stroke, Matrix()) // Matrix() identidad porque el canvas ya tiene el zoom
        }
        
        canvas.restore()
    }
}
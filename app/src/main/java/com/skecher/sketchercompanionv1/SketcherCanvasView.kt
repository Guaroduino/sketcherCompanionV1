package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke



data class FillData(val path: android.graphics.Path, val color: Int)
data class Layer(
    val id: String, 
    val strokes: MutableList<Stroke>, 
    val fills: MutableList<FillData>,
    var isVisible: Boolean = true,
    var opacity: Float = 1f
)

class SketcherCanvasView(context: Context) : View(context) {

    private val viewMatrix = Matrix()
    private val strokeRenderer = CanvasStrokeRenderer.create()
    
    // LAYERS (Replaces flat lists)
    private val layers = mutableListOf<Layer>()
    
    // PREVIEW STATE (For live drawing/filling)
    private var currentFillPath: android.graphics.Path? = null
    private var currentFillColor: Int? = null
    private val fillPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * FIX ROTACIÓN: Usamos 'post' para asegurar que la invalidación
     * ocurra cuando la vista ya esté adjunta y medida.
     */
    fun setLayers(newLayers: List<Layer>) {
        layers.clear()
        layers.addAll(newLayers)
        
        // USAMOS POST: Esto pone la orden de dibujo en la cola del hilo principal.
        // Asegura que se ejecute en el siguiente ciclo, cuando la vista ya esté lista.
        post {
            invalidate()
        }
    }

    fun addStroke(stroke: Stroke, layerIndex: Int) {
        if (layerIndex in layers.indices) {
            layers[layerIndex].strokes.add(stroke)
            invalidate()
        }
    }
    
    // --- LASSO FILL METHODS ---
    fun updateCurrentFill(path: android.graphics.Path, color: Int) {
        currentFillPath = path
        currentFillColor = color
        invalidate()
    }

    fun finishCurrentFill(layerIndex: Int) {
        if (currentFillPath != null && currentFillColor != null) {
            if (layerIndex in layers.indices) {
                 layers[layerIndex].fills.add(FillData(currentFillPath!!, currentFillColor!!))
            }
            currentFillPath = null
            currentFillColor = null
            invalidate()
        }
    }

    fun eraseContentAt(worldX: Float, worldY: Float): Any? {
        // Iterate layers top-down (reversed)
        for (layer in layers.reversed()) {
            if (!layer.isVisible) continue 
            
            // 1. Check Strokes (Top priority usually, or same layer order)
            // Let's check strokes first as they are "on top" of fills in drawing order
            for (i in layer.strokes.indices.reversed()) {
                val stroke = layer.strokes[i]
                if (StrokeGeometry.isStrokeTouched(stroke, worldX, worldY)) {
                    layer.strokes.removeAt(i)
                    invalidate()
                    return stroke
                }
            }
            
            // 2. Check Fills
            for (i in layer.fills.indices.reversed()) {
                val fill = layer.fills[i]
                // Hit test path
                val bounds = android.graphics.RectF()
                fill.path.computeBounds(bounds, true)
                
                if (bounds.contains(worldX, worldY)) {
                    // Precise check using Region
                    val region = android.graphics.Region()
                    region.setPath(fill.path, android.graphics.Region(
                        bounds.left.toInt(), bounds.top.toInt(), 
                        bounds.right.toInt(), bounds.bottom.toInt()
                    ))
                    
                    if (region.contains(worldX.toInt(), worldY.toInt())) {
                        layer.fills.removeAt(i)
                        invalidate()
                        return fill
                    }
                }
            }
        }
        return null
    }

    fun setCameraMatrix(matrix: Matrix) {
        viewMatrix.set(matrix)
        invalidate()
    }

    fun clearCanvas() {
        layers.forEach { 
            it.strokes.clear()
            it.fills.clear() 
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.save()
        canvas.concat(viewMatrix)
        
        // Loop through layers (Bottom to Top)
        for (layer in layers) {
            if (!layer.isVisible) continue

            // Handle Layer Opacity
            val saveCount = if (layer.opacity < 1f) {
                // Bounds could be optimized, but using full canvas for simplicity now
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (layer.opacity * 255).toInt())
            } else {
                canvas.save()
            }
            
             // 1. Layer Fills
            for (fill in layer.fills) {
                fillPaint.color = fill.color
                canvas.drawPath(fill.path, fillPaint)
            }
            
            // 2. Layer Strokes
            for (stroke in layer.strokes) {
                strokeRenderer.draw(canvas, stroke, Matrix())
            }
            
            canvas.restoreToCount(saveCount)
        }

        // 3. Current Fill in progress (Preview) - Drawn on top of everything primarily
        // Or should it be per layer? The prompt implies preview is separate.
        // "Update onDraw to render ... 2. Current Fill in progress (Preview). 3. Ink Strokes".
        // The prompt asked for:
        // "Update onDraw to render in this exact order: 1. Saved Fills (Bottom layer). 2. Current Fill in progress. 3. Ink Strokes."
        // BUT that was for the PREVIOUS task.
        // FOR THIS TASK: "Refactor onDraw(): It must now loop through the layers list... Inside that loop, draw that layer's fills first, then that layer's strokes."
        // Logic check: Where does the "Preview" go?
        // Usually preview is "above all" or "in active layer".
        // I'll keep Fill Preview on top of everything for now as it's a "UI" element of the tool.
        
        currentFillPath?.let { path ->
            currentFillColor?.let { color ->
                fillPaint.color = color
                canvas.drawPath(path, fillPaint)
            }
        }

        canvas.restore()
    }
}


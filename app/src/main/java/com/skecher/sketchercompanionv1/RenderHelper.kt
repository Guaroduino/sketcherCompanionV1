package com.skecher.sketchercompanionv1

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer

object RenderHelper {

    private val strokeRenderer = CanvasStrokeRenderer.create()

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val vectorPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val imagePaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        isDither = true
    }

    fun drawElementRecursive(
        canvas: Canvas, 
        element: LayerElement,
        componentLibrary: Map<String, ComponentDefinition>,
        isDimmed: Boolean = false
    ) {
        if (isDimmed) {
            val paint = Paint().apply { alpha = 80 } // ~30% opacity
            canvas.saveLayer(null, paint)
        }

        when (element) {
            is GroupElement -> {
                canvas.save()
                canvas.concat(element.matrix)
                for (child in element.elements) {
                    drawElementRecursive(canvas, child, componentLibrary)
                }
                canvas.restore()
            }
            is ComponentInstance -> {
                val definition = componentLibrary[element.definitionId]
                if (definition != null) {
                    canvas.save()
                    canvas.concat(element.matrix)
                    for (child in definition.elements) {
                        drawElementRecursive(canvas, child, componentLibrary)
                    }
                    canvas.restore()
                }
            }
            is VectorStroke -> drawVectorStroke(element, canvas)
            is AndroidInkElement -> drawInkStroke(element, canvas)
            is FillData -> drawFill(element, canvas)
            is ImageElement -> drawImage(element, canvas)
            is SvgElement -> element.render(canvas)
        }

        if (isDimmed) {
            canvas.restore()
        }
    }

    fun drawVectorStroke(vStroke: VectorStroke, canvas: Canvas) {
        vectorPaint.color = vStroke.color
        canvas.drawPath(vStroke.path, vectorPaint)
    }

    fun drawInkStroke(element: AndroidInkElement, canvas: Canvas) {
        canvas.save()
        canvas.concat(element.localMatrix)
        strokeRenderer.draw(canvas, element.stroke, Matrix()) 
        canvas.restore()
    }

    fun drawFill(fill: FillData, canvas: Canvas) {
        fillPaint.color = fill.color
        canvas.drawPath(fill.path, fillPaint)
    }

    fun drawImage(element: ImageElement, canvas: Canvas) {
        canvas.drawBitmap(element.bitmap, element.matrix, imagePaint)
    }
}

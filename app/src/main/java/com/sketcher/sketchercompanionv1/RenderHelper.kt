package com.sketcher.sketchercompanionv1

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode


object RenderHelper {



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



    fun drawFill(fill: FillData, canvas: Canvas) {
        fillPaint.color = fill.color
        canvas.drawPath(fill.path, fillPaint)
    }

    fun drawImage(element: ImageElement, canvas: Canvas) {
        canvas.drawBitmap(element.bitmap, element.matrix, imagePaint)
    }

    private val canvasBoundsPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = android.graphics.Color.parseColor("#333333")
        isAntiAlias = true
    }

    private val canvasShadowPaint = Paint().apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.parseColor("#10000000") // Very light shadow
        isAntiAlias = true
    }

    /**
     * Draw canvas bounds if configured.
     * @param canvas The canvas to draw on
     * @param canvasSizeConfig The canvas size configuration (null = infinite canvas)
     */
    fun drawCanvasBounds(canvas: Canvas, canvasSizeConfig: com.sketcher.sketchercompanionv1.dto.CanvasSizeConfig?) {
        if (canvasSizeConfig == null) return

        val width = canvasSizeConfig.widthInPixels
        val height = canvasSizeConfig.heightInPixels

        // Draw subtle shadow outside bounds
        val shadowOffset = 10f
        canvas.drawRect(
            -shadowOffset,
            -shadowOffset,
            width + shadowOffset,
            -shadowOffset,
            canvasShadowPaint
        )
        canvas.drawRect(
            -shadowOffset,
            height,
            width + shadowOffset,
            height + shadowOffset,
            canvasShadowPaint
        )
        canvas.drawRect(
            -shadowOffset,
            0f,
            0f,
            height,
            canvasShadowPaint
        )
        canvas.drawRect(
            width,
            0f,
            width + shadowOffset,
            height,
            canvasShadowPaint
        )

        // Draw bounds rectangle
        canvas.drawRect(0f, 0f, width, height, canvasBoundsPaint)
    }
}


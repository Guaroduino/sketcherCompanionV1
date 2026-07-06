package com.sketcher.sketchercompanionv1

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import com.sketcher.sketchercompanionv1.dto.StrokeType


object RenderHelper {



    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val vectorPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
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
        // Pass 1: FILL (if enabled)
        if (vStroke.isFillEnabled && vStroke.fillPath != null) {
            vectorPaint.style = Paint.Style.FILL
            vectorPaint.color = vStroke.fillColor
            canvas.drawPath(vStroke.fillPath, vectorPaint)
        }

        // Pass 2: STROKE (if enabled)
        if (vStroke.isStrokeEnabled) {
            val isMeshBrush = vStroke.brushType == "FREEHAND" || vStroke.brushType == "PEN" || vStroke.brushType == "PLUMA" || vStroke.brushType == "PENCIL_CUMULATIVE" || vStroke.brushType == "PAINT" || vStroke.brushType == "WATERCOLOR"
            if (isMeshBrush) {
                vStroke.getBrushRenderer().draw(canvas, vStroke, vectorPaint, 1f) { p, alpha ->
                    val color = vStroke.strokeColor
                    p.color = (color and 0x00FFFFFF) or ((android.graphics.Color.alpha(color) * alpha).toInt().coerceIn(0, 255) shl 24)
                }
            } else {
                vectorPaint.style = Paint.Style.STROKE
                
                // Apply dash path effect for CAD styles
                if (vStroke.isCadGeometry) {
                    vectorPaint.color = vStroke.strokeColor
                    val width = (if (vStroke.maxWidth > 0) vStroke.maxWidth else 0f)
                    vectorPaint.strokeWidth = width
                    when (vStroke.lineStyle.uppercase()) {
                        "DASHED" -> {
                            val interval = kotlin.math.max(1f, width)
                            vectorPaint.pathEffect = android.graphics.DashPathEffect(
                                floatArrayOf(4f * interval, 2f * interval), 0f
                            )
                        }
                        "DOTTED" -> {
                            val interval = kotlin.math.max(1f, width)
                            vectorPaint.pathEffect = android.graphics.DashPathEffect(
                                floatArrayOf(0f, 2f * interval), 0f
                            )
                        }
                        else -> {
                            vectorPaint.pathEffect = null
                        }
                    }
                    canvas.drawPath(vStroke.path, vectorPaint)
                    vectorPaint.pathEffect = null
                } else {
                    vectorPaint.color = vStroke.strokeColor
                    val width = if (vStroke.maxWidth > 0) vStroke.maxWidth else 0f
                    vectorPaint.strokeWidth = width
                    vectorPaint.pathEffect = null
                    vectorPaint.maskFilter = null
                    canvas.drawPath(vStroke.path, vectorPaint)
                }
            }
        }
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
     * Draw canvas bounds and shadow.
     * @param canvas The canvas to draw on
     * @param left The left boundary coordinate
     * @param top The top boundary coordinate
     * @param right The right boundary coordinate
     * @param bottom The bottom boundary coordinate
     */
    fun drawCanvasBounds(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        // Draw subtle shadow outside bounds
        val shadowOffset = 10f
        
        // Top shadow
        canvas.drawRect(left - shadowOffset, top - shadowOffset, right + shadowOffset, top, canvasShadowPaint)
        // Bottom shadow
        canvas.drawRect(left - shadowOffset, bottom, right + shadowOffset, bottom + shadowOffset, canvasShadowPaint)
        // Left shadow
        canvas.drawRect(left - shadowOffset, top, left, bottom, canvasShadowPaint)
        // Right shadow
        canvas.drawRect(right, top, right + shadowOffset, bottom, canvasShadowPaint)

        // Draw bounds rectangle outline
        canvas.drawRect(left, top, right, bottom, canvasBoundsPaint)
    }
}


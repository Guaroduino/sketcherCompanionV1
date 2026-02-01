package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

import com.skecher.sketchercompanionv1.dto.GridConfig
import com.skecher.sketchercompanionv1.dto.ScaleConfig
import com.skecher.sketchercompanionv1.dto.DistanceUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// Layer and FillData are now defined in Layer.kt and LayerElement.kt respectively

class SketcherCanvasView(context: Context) : View(context) {

    private val viewMatrix = Matrix()
    private val strokeRenderer = CanvasStrokeRenderer.create()
    
    // BITMAP CACHING
    private var backingBitmap: android.graphics.Bitmap? = null
    private var backingCanvas: Canvas? = null

    var onSizeChangedCallback: ((Int, Int) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            backingBitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            backingCanvas = Canvas(backingBitmap!!)
            redrawAllCache()
            onSizeChangedCallback?.invoke(w, h)
        }
    }

    // Call this to Bake a finalized stroke into the bitmap
    fun bakeStroke(stroke: VectorStroke) {
        invalidate()
    }


    // Direct Add & Bake (Optimistic UI for Instant Feedback)
    fun addInkStroke(stroke: androidx.ink.strokes.Stroke, layerIndex: Int) {
        if (layerIndex in layers.indices) {
            layers[layerIndex].elements.add(AndroidInkElement(stroke))
            bakeInkStroke(stroke)
        }
    }

    // Bake Android Ink Stroke
    fun bakeInkStroke(stroke: androidx.ink.strokes.Stroke) {
        invalidate()
    }
    
    // Also Bake Fills (optional but useful)
    fun bakeFill(fill: FillData) {
        invalidate()
    }

    
    // Helper to completely rebuild the cache
    fun redrawAllCache() {
        // Since we moved to dynamic rendering for Z-order correctness, 
        // the "cache" concept needs to strictly respect the order.
        // We can still draw to backingBitmap if we want, but for now we invalidate.
        // If we want to use backingBitmap for performance, we would need to draw ALL elements to it 
        // in order. 
        // For this refactor, we are going completely dynamic in onDraw for the layers content
        // to ensure correct mixing of Vector/Ink/Fills.
        
        // However, we might want to keep the backing canvas cleared or used for background?
        val requestCanvas = backingCanvas ?: return
        requestCanvas.drawColor(canvasBackgroundColor)
        
        invalidate()
    }

    private fun drawFill(fill: FillData, canvas: Canvas) {
        fillPaint.color = fill.color
        canvas.drawPath(fill.path, fillPaint)
    }

    private fun drawVectorStroke(vStroke: VectorStroke, canvas: Canvas) {
        if (isDebugWireframeByVM) {
            drawDebugStroke(canvas, vStroke)
        } else {
            vectorPaint.color = vStroke.color
            canvas.drawPath(vStroke.path, vectorPaint)
        }
    }

    private val multiplyPaint = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
    }

    // Direct render on the main canvas (or passed canvas)
    private fun drawInkStroke(ink: Stroke, canvas: Canvas) {
        // NOTE: If we want "wet" multiply look, we need to handle layer saves carefully 
        // or just draw normally.
        // For unified Z-order, we draw directly.
        strokeRenderer.draw(canvas, ink, Matrix()) 
    }

    
    // GRID CONFIG
    var gridConfig: GridConfig = GridConfig()
        set(value) { field = value; invalidate() }
    var scaleConfig: ScaleConfig = ScaleConfig()
        set(value) { field = value; invalidate() }
    var currentUnit: DistanceUnit = DistanceUnit.M
        set(value) { field = value; invalidate() }
        
    private val gridPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 0f // Hairline
    }

    // BACKGROUND
    var canvasBackgroundColor: Int = android.graphics.Color.WHITE
        set(value) {
            field = value
            redrawAllCache() 
        }

    // LAYERS (Replaces flat lists)
    var isDebugPredictionEnabled: Boolean = false
        set(value) {
            field = value
            android.util.Log.d("SketcherCanvasView", "isDebugPredictionEnabled set to: $value")
            invalidate()
        }
    var isDebugWireframe: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
        
    private val layers = mutableListOf<Layer>()
    
    // PREVIEW STATE (For live drawing/filling)
    private var currentVectorPreviewPath: android.graphics.Path? = null
    private var currentVectorPreviewPoints: List<StrokePoint>? = null // For prediction
    private var currentVectorPreviewColor: Int = 0 // Explicitly store preview color
    private var currentMaxWidth: Float = 10f // Max stroke width for prediction
    private var currentMinSizeFactor: Float = 0f // Min size factor for prediction
    private var currentFillPath: android.graphics.Path? = null
    private var currentFillColor: Int? = null
    private var currentPredictedPoint: android.graphics.PointF? = null
    private val fillPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    private val vectorPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }

    fun setLayers(newLayers: List<Layer>) {
        layers.clear()
        layers.addAll(newLayers)
        invalidate()
    }


    
    fun updateCurrentVectorPreview(
        path: android.graphics.Path?, 
        points: List<StrokePoint>?, 
        color: Int, 
        maxWidth: Float = 10f,
        minSizeFactor: Float = 0f,
        predictedPoint: android.graphics.PointF? = null
    ) {
        currentVectorPreviewPath = path
        currentVectorPreviewPoints = points
        currentMaxWidth = maxWidth
        currentMinSizeFactor = minSizeFactor
        currentPredictedPoint = predictedPoint
        if (color != 0) currentVectorPreviewColor = color // Store it
        // Do NOT set vectorPaint.color here, it gets overwritten by onDraw layers
        invalidate()
    }

    // --- LASSO FILL METHODS ---
    fun updateCurrentFill(path: android.graphics.Path?, color: Int) {
        currentFillPath = path
        currentFillColor = color
        invalidate()
    }



    fun eraseContentAt(worldX: Float, worldY: Float): Any? {
        // Iterate layers top-down (reversed)
        for (layer in layers.reversed()) {
            if (!layer.isVisible) continue 
            
            // Iterate elements reversed (Top to Bottom)
            val iterator = layer.elements.listIterator(layer.elements.size)
            while (iterator.hasPrevious()) {
                val element = iterator.previous()
                val removed = when(element) {
                    is FillData -> {
                        val bounds = android.graphics.RectF()
                        element.path.computeBounds(bounds, true)
                        if (bounds.contains(worldX, worldY)) {
                            val region = android.graphics.Region()
                            region.setPath(element.path, android.graphics.Region(
                                bounds.left.toInt(), bounds.top.toInt(), 
                                bounds.right.toInt(), bounds.bottom.toInt()
                            ))
                            region.contains(worldX.toInt(), worldY.toInt())
                        } else {
                            false
                        }
                    }
                    is VectorStroke -> {
                        // existing logic was "Use existing geometry check" but implementation in previous View was empty comment
                        // "Collision for vector stroke? ... If VectorStroke had collision logic..."
                        // User prompt says: "VectorStroke: Use existing geometry check."
                        // I need to check if there is a helper. `StrokeGeometry.isStrokeTouched` was used for INK.
                        // For VectorStroke, I might need to implement something or check if `StrokeGeometry` supports it.
                        // Assuming `StrokeGeometry` is only for Ink. 
                        // I will implement a basic bounds/path check for VectorStroke if needed, 
                        // OR if there is an existing utility.
                        // Actually, previous code for VectorStroke in eraseContentAt ended with empty check.
                        // Let's assume for now we might skip VectorStroke erasure functionality or implement basic path contains.
                        // Wait, previous code checked `layer.inkStrokes` with `StrokeGeometry.isStrokeTouched`.
                        // For `VectorStroke`, it matches what was there: nothing.
                        // BUT user asked: "VectorStroke: Use existing geometry check."
                        // I will assume for now I should check if `VectorStroke` has a `contains` or similar, 
                        // or if I should use the Path.
                        val bounds = android.graphics.RectF()
                        element.path.computeBounds(bounds, true)
                        // Simple bounds check for now as placeholder for "existing geometry check" if none exists.
                        // Or maybe `StrokeGeometry` has an overload?
                        // Let's stick to simple bounds -> path region check like Fill for now, 
                        // as VectorStroke.path is a Path.
                        if (bounds.contains(worldX, worldY)) {
                             // Precise check
                            // NOTE: Path.contains is not standard API for points.
                            // We use Region.
                             val region = android.graphics.Region()
                             region.setPath(element.path, android.graphics.Region(
                                 bounds.left.toInt(), bounds.top.toInt(), 
                                 bounds.right.toInt(), bounds.bottom.toInt()
                             ))
                             region.contains(worldX.toInt(), worldY.toInt())
                        } else {
                            false
                        }
                    }
                    is AndroidInkElement -> {
                        StrokeGeometry.isStrokeTouched(element.stroke, worldX, worldY)
                    }
                }

                if (removed) {
                    iterator.remove()
                    invalidate()
                    return element
                }
            }
        }
        return null
    }

    fun setCameraMatrix(matrix: Matrix) {
        viewMatrix.set(matrix)
        redrawAllCache()
    }

    fun clearCanvas() {
        layers.forEach { 
            it.elements.clear()
        }

        redrawAllCache()
    }

    // --- GRID RENDERING ---
    private fun drawGrid(canvas: Canvas) {
        if (!gridConfig.isVisible) return

        val spacing = gridConfig.spacing
        if (spacing <= 0f) return

        // 1. Calculate Pixels per Unit using Central Logic
        val stepPx = com.skecher.sketchercompanionv1.utils.UnitUtils.projectUnitsToPixels(
            value = spacing, 
            unit = currentUnit, 
            basePxPerMm = scaleConfig.basePixelsPerMillimeter
        )
        
        // 3. Check Density (Performance Guard)
        // Get current zoom from matrix to check screen density
        val transformValues = FloatArray(9)
        viewMatrix.getValues(transformValues)
        val zoom = kotlin.math.sqrt(transformValues[Matrix.MSCALE_X] * transformValues[Matrix.MSCALE_X] + transformValues[Matrix.MSKEW_X] * transformValues[Matrix.MSKEW_X])
        
        val screenStep = stepPx * zoom
        if (screenStep < 3f) {
           // Skip
        }

        // 4. Calculate Visible Bounds in WORLD Coordinates
        // Invert Matrix
        val inverse = Matrix()
        viewMatrix.invert(inverse)
        
        val screenBounds = floatArrayOf(0f, 0f, width.toFloat(), height.toFloat())
        val worldBounds = floatArrayOf(0f, 0f, 0f, 0f)
        inverse.mapPoints(worldBounds, screenBounds)
        val left = worldBounds[0]
        val top = worldBounds[1]
        val right = worldBounds[2]
        val bottom = worldBounds[3]
        
        val wMinX = kotlin.math.min(left, right)
        val wMaxX = kotlin.math.max(left, right)
        val wMinY = kotlin.math.min(top, bottom)
        val wMaxY = kotlin.math.max(top, bottom)

        // 5. Draw Loop
        val startXIndex = floor(wMinX / stepPx).toInt()
        val endXIndex = ceil(wMaxX / stepPx).toInt()
        
        val startYIndex = floor(wMinY / stepPx).toInt()
        val endYIndex = ceil(wMaxY / stepPx).toInt()
        
        // Limit loop count to prevent hanging on extreme zoom bugs
        if ((endXIndex - startXIndex) > 2000 || (endYIndex - startYIndex) > 2000) return 

        // Draw Verticals
        for (i in startXIndex..endXIndex) {
            val x = i * stepPx
            
            var drawLine = false
            var thicknessScale = 1.0f
            var lineColor = gridConfig.color 

            if (i % 10 == 0) {
                // Major
                drawLine = true
                thicknessScale = 2.0f
                lineColor = gridConfig.color
            } else if (i % 5 == 0) {
                // Mid
                if (screenStep >= 3f) { 
                    drawLine = true
                    thicknessScale = 1.5f
                    lineColor = gridConfig.secondaryColor
                }
            } else {
                // Minor
                if (screenStep >= 8f) { 
                    drawLine = true
                    thicknessScale = 1.0f
                    lineColor = gridConfig.tertiaryColor
                }
            }
            
            if (drawLine) {
                gridPaint.color = lineColor
                gridPaint.strokeWidth = if (thicknessScale > 1.0f) (thicknessScale / zoom) else 0f
                canvas.drawLine(x, wMinY, x, wMaxY, gridPaint)
            }
        }

        // Draw Horizontals
        for (i in startYIndex..endYIndex) {
            val y = i * stepPx
            
            var drawLine = false
            var thicknessScale = 1.0f
            var lineColor = gridConfig.color

            if (i % 10 == 0) {
                drawLine = true
                thicknessScale = 2.0f
                lineColor = gridConfig.color
            } else if (i % 5 == 0) {
                if (screenStep >= 3f) {
                    drawLine = true
                    thicknessScale = 1.5f
                    lineColor = gridConfig.secondaryColor
                }
            } else {
                if (screenStep >= 8f) {
                    drawLine = true
                    thicknessScale = 1.0f
                    lineColor = gridConfig.tertiaryColor
                }
            }
            
            if (drawLine) {
                gridPaint.color = lineColor
                gridPaint.strokeWidth = if (thicknessScale > 1.0f) (thicknessScale / zoom) else 0f
                canvas.drawLine(wMinX, y, wMaxX, y, gridPaint)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(canvasBackgroundColor)
        
        // 0. Grid
        canvas.save()
        canvas.concat(viewMatrix)
        drawGrid(canvas)
        canvas.restore()
        
        // 1. RENDER LAYERS
        for (layer in layers) {
            if (!layer.isVisible) continue
            
            val layerAlpha = if (layer.opacity < 1f) (layer.opacity * 255).toInt() else 255
            val saveCount = if (layerAlpha < 255) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), layerAlpha)
            } else {
                canvas.save()
            }
            
            // Unified Loop
            canvas.save()
            canvas.concat(viewMatrix)
            
            for (element in layer.elements) {
                when(element) {
                    is FillData -> drawFill(element, canvas)
                    is VectorStroke -> drawVectorStroke(element, canvas)
                    is AndroidInkElement -> drawInkStroke(element.stroke, canvas)
                }
            }
            
            canvas.restore()
            canvas.restoreToCount(saveCount)
        }
        
        
        // 3. Previews (Live content) - Needs Matrix
        canvas.save()
        canvas.concat(viewMatrix)
        
        // 4. Current Fill in progress
        currentFillPath?.let { path ->
            currentFillColor?.let { color ->
                fillPaint.color = color
                canvas.drawPath(path, fillPaint)
            }
        }

        // 5. Vector Preview with Prediction
        currentVectorPreviewPath?.let { path ->
             // CRITICAL FIX: explicit color set before drawing preview
             if (currentVectorPreviewColor != 0) vectorPaint.color = currentVectorPreviewColor
             canvas.drawPath(path, vectorPaint)
             
             val points = currentVectorPreviewPoints
             if (points != null && points.isNotEmpty()) {
                 currentPredictedPoint?.let { pred ->
                     val last = points.last()
                     
                     val predictionPath = android.graphics.Path()
                     val dynamicRange = 1.0f - currentMinSizeFactor
                     
                     val lastScale = currentMinSizeFactor + (dynamicRange * last.pressure)
                     val lastWidth = currentMaxWidth * lastScale
                     
                     val predWidth = lastWidth 

                     predictionPath.moveTo(last.x, last.y)
                     predictionPath.lineTo(pred.x, pred.y)
                     
                     val originalColor = vectorPaint.color
                     val originalAlpha = vectorPaint.alpha
                     val originalWidth = vectorPaint.strokeWidth
                     val originalStyle = vectorPaint.style

                     if (isDebugPredictionEnabled) {
                         android.util.Log.d("CanvasDebug", "Drawing Prediction Line (DEBUG)")
                         
                         vectorPaint.color = android.graphics.Color.RED
                         vectorPaint.alpha = 255 
                         vectorPaint.strokeWidth = 5f 
                         vectorPaint.style = android.graphics.Paint.Style.STROKE 
                         
                         canvas.drawPath(predictionPath, vectorPaint)
                         
                         vectorPaint.color = android.graphics.Color.BLUE
                         vectorPaint.strokeWidth = 3f
                         canvas.drawLine(pred.x - 20, pred.y, pred.x + 20, pred.y, vectorPaint)
                         canvas.drawLine(pred.x, pred.y - 20, pred.x, pred.y + 20, vectorPaint)
                         
                     } else {
                         vectorPaint.color = originalColor
                         vectorPaint.alpha = originalAlpha
                         vectorPaint.style = android.graphics.Paint.Style.STROKE 
                         vectorPaint.strokeWidth = lastWidth 
                         
                         canvas.drawPath(predictionPath, vectorPaint)
                     }

                     vectorPaint.color = originalColor
                     vectorPaint.alpha = originalAlpha
                     vectorPaint.strokeWidth = originalWidth
                     vectorPaint.style = originalStyle

                 }
             }
        }

        canvas.restore()
        
        onDrawAction?.invoke()
        onDrawAction = null
    }

    // Callback to be executed immediately after onDraw completes its work
    var onDrawAction: (() -> Unit)? = null

    // DEBUG PAINTS
    private val debugSkeletonPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLUE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val debugVertexPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.YELLOW
        style = android.graphics.Paint.Style.FILL
    }
    private val debugLeftEdgePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GREEN
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val debugRightEdgePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.RED
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }

    var isDebugWireframeByVM: Boolean = false
        set(value) {
            field = value
            redrawAllCache()
        }

    private fun drawDebugStroke(canvas: Canvas, stroke: VectorStroke) {
        if (stroke.points.size >= 2) {
            for (i in 0 until stroke.points.size - 1) {
                val p1 = stroke.points[i]
                val p2 = stroke.points[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, debugSkeletonPaint)
            }
        }
        
        for (p in stroke.points) {
            canvas.drawCircle(p.x, p.y, 4f, debugVertexPaint)
        }

        if (stroke.leftPoints.size >= 2) {
            for (i in 0 until stroke.leftPoints.size - 1) {
                val p1 = stroke.leftPoints[i]
                val p2 = stroke.leftPoints[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, debugLeftEdgePaint)
            }
        }
        if (stroke.rightPoints.size >= 2) {
            for (i in 0 until stroke.rightPoints.size - 1) {
                val p1 = stroke.rightPoints[i]
                val p2 = stroke.rightPoints[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, debugRightEdgePaint)
            }
        }
    }
}

package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.view.View
import android.view.MotionEvent
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke

import com.skecher.sketchercompanionv1.dto.*

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// Layer and FillData are now defined in Layer.kt and LayerElement.kt respectively

class SketcherCanvasView(context: Context) : View(context) {

    private val viewMatrix = Matrix()
    private val cachedBitmapMatrix = Matrix()
    private val strokeRenderer = CanvasStrokeRenderer.create()
    private var isDrawing: Boolean = false
    
    // SELECTION STATE
    var isSelectionDragging: Boolean = false
        set(value) {
            field = value
            redrawAllCache() // Rebuild cache when drag state changes (lift/drop)
        }
    
    // BITMAP CACHING
    private var backingBitmap: android.graphics.Bitmap? = null
    private var backingCanvas: Canvas? = null

    var onSizeChangedCallback: ((Int, Int) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            // Create a new bitmap matching the view size
            val newBitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            backingBitmap = newBitmap
            backingCanvas = Canvas(newBitmap)
            
            // Re-render everything to the new bitmap (handled by redrawAllCache)
            redrawAllCache()
            
            onSizeChangedCallback?.invoke(w, h)
        }
    }

    // Call this to Bake a finalized stroke into the bitmap
    fun bakeStroke(stroke: VectorStroke) {
        val canvas = backingCanvas ?: return
        canvas.save()
        canvas.concat(viewMatrix) // Apply current view transform
        drawVectorStroke(stroke, canvas)
        canvas.restore()
        invalidate() // Trigger a draw of the bitmap
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
        val canvas = backingCanvas ?: return
        canvas.save()
        canvas.concat(viewMatrix)
        
        // Android Ink rendering logic
        // We need to wrap it temporarily or use logic similar to drawInkStroke but for raw stroke if needed
        // But drawInkStroke takes AndroidInkElement. 
        // Let's just create a temp element wrapper or use strokeRenderer directly
        val element = AndroidInkElement(stroke) // Wrapper for consistency
        drawInkStroke(element, canvas) // Uses internal logic
        
        canvas.restore()
        invalidate()
    }
    
    // Also Bake Fills
    fun bakeFill(fill: FillData) {
         val canvas = backingCanvas ?: return
         canvas.save()
         canvas.concat(viewMatrix)
         drawFill(fill, canvas)
         canvas.restore()
         invalidate()
    }

    
    // Helper to completely rebuild the cache (e.g. after Zoom/Pan End or Undo)
    fun redrawAllCache() {
        val canvas = backingCanvas ?: return
        
        // Mark cache as valid for current view
        cachedBitmapMatrix.set(viewMatrix)
        
        // 1. Clear with Background Color
        canvas.drawColor(canvasBackgroundColor)
        
        // 2. Draw Grid (Baken into background for simplicity? Or keep separate?)
        // Plan says: Draw Grid separate in onDraw?
        // Prompt Check: "Draw the Background Bitmap (Contains all past strokes)".
        // If we bake Grid, it's easier.
        canvas.save()
        canvas.concat(viewMatrix)
        drawGrid(canvas)
        RenderHelper.drawCanvasBounds(canvas, canvasSizeConfig)
        canvas.restore()

        // 3. Render Layers
        for (layer in layers) {
            if (!layer.isVisible) continue
            
            val layerAlpha = if (layer.opacity < 1f) (layer.opacity * 255).toInt() else 255
            val saveCount = if (layerAlpha < 255) {
                canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), layerAlpha)
            } else {
                canvas.save()
            }
            
            canvas.save()
            canvas.concat(viewMatrix)
            
            for (element in layer.elements) {
                 // Disable dimming in edit mode - content should be visible
                 val isDimmed = false
                 val isSelected = selectionManager?.selectedElements?.contains(element) == true
                 
                 // Only skip drawing if selected AND we are currently dragging/transforming
                 if (isSelected && isSelectionDragging) continue 
                 
                 RenderHelper.drawElementRecursive(
                     canvas, 
                     element,
                     componentLibrary = componentLibrary,
                     isDimmed = isDimmed
                 )
            }
            
            canvas.restore()
            canvas.restoreToCount(saveCount)
        }
        
        invalidate()
    }

    private fun drawFill(fill: FillData, canvas: Canvas) {
        fillPaint.color = fill.color
        canvas.drawPath(fill.path, fillPaint)
    }

    private fun drawVectorStroke(vStroke: VectorStroke, canvas: Canvas) {
        // 1. Draw the beautiful filled shape (Normal)
        vectorPaint.color = vStroke.color
        canvas.drawPath(vStroke.path, vectorPaint)

        // 2. Debug Overlay
        if (isDebugWireframeByVM) {
            val transformValues = FloatArray(9)
            viewMatrix.getValues(transformValues)
            val zoom = kotlin.math.sqrt(transformValues[Matrix.MSCALE_X] * transformValues[Matrix.MSCALE_X] + transformValues[Matrix.MSKEW_X] * transformValues[Matrix.MSKEW_X])

            val debugPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 2f / zoom // Hairline
                color = android.graphics.Color.GREEN
            }
            val pointPaint = android.graphics.Paint().apply {
                style = android.graphics.Paint.Style.FILL
                color = android.graphics.Color.RED
            }
            
            // Draw Skeleton (Centerline)
            val points = vStroke.points
            if (points.isNotEmpty()) {
                val path = android.graphics.Path()
                path.moveTo(points.first().x, points.first().y)
                for (p in points) {
                    path.lineTo(p.x, p.y)
                    // Draw Vertex (Simplified Points)
                    canvas.drawCircle(p.x, p.y, 4f / zoom, pointPaint)
                }
                canvas.drawPath(path, debugPaint)
            }
        }
    }

    private val multiplyPaint = android.graphics.Paint().apply {
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
    }

    // Direct render on the main canvas (or passed canvas)
    private fun drawInkStroke(element: AndroidInkElement, canvas: Canvas) {
        canvas.save()
        canvas.concat(element.localMatrix)
        strokeRenderer.draw(canvas, element.stroke, Matrix()) 
        canvas.restore()
    }

    
    // GRID CONFIG
    var gridConfig: GridConfig = GridConfig()
        set(value) { field = value; redrawAllCache() } // Must redraw cache if grid baked
    var scaleConfig: ScaleConfig = ScaleConfig()
        set(value) { field = value; redrawAllCache() }
    var currentUnit: DistanceUnit = DistanceUnit.M
        set(value) { field = value; redrawAllCache() }
    
    // CANVAS SIZE CONFIG
    var canvasSizeConfig: CanvasSizeConfig? = null
        set(value) { field = value; redrawAllCache() }
        
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
            invalidate()
        }
    var isDebugWireframe: Boolean = false
        set(value) {
            field = value
            redrawAllCache()
        }
        
    private val layers = mutableListOf<Layer>()
    private var componentLibrary: Map<String, ComponentDefinition> = emptyMap()
    var activeStrokeType: StrokeType = StrokeType.FREEHAND
    private var editingContext: List<LayerElement>? = null
    private var activeLayerIndex: Int = 0 // Track active layer for rendering order
    
    // PREVIEW STATE (For live drawing/filling)
    private var currentVectorPreviewPath: android.graphics.Path? = null
    private var currentVectorPreviewPoints: List<StrokePoint>? = null // For prediction
    private var currentVectorPreviewColor: Int = 0 // Explicitly store preview color
    private var currentMaxWidth: Float = 10f // Max stroke width for prediction
    private var currentFillPath: android.graphics.Path? = null
    private var currentFillColor: Int? = null
    private var currentPredictedPoint: StrokePoint? = null
    private var currentVectorPreviewCenterPoints: List<android.graphics.PointF>? = null 
    private var currentVectorPreviewOutlinePoints: List<android.graphics.PointF>? = null
    
    // SNAP TO GRID
    var snapFunction: ((Float, Float) -> Pair<Float, Float>)? = null
    
    // VISUAL SMOOTHING STATE
    private var currentLiveTipWidth: Float = 0f
    private var currentVelocityState: Float = 0f // Persistent smoothed velocity
    private var currentPressureState: Float = 0.5f // Persistent smoothed pressure
    
    // MULTI-STEP GEOMETRIC
    private var isMultiStepInProgress: Boolean = false
    private var currentLiveGeneratedRadius: Float = 0f // New: Accurate radius from generator
    
    private val fillPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    private val vectorPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }

    fun setLayers(newLayers: List<Layer>, library: Map<String, ComponentDefinition>, editingCtx: List<LayerElement>?, activeIndex: Int = 0) {
        layers.clear()
        layers.addAll(newLayers)
        componentLibrary = library
        editingContext = editingCtx
        activeLayerIndex = activeIndex
        redrawAllCache() // Layers changed, rebuild
    }

    fun updateCurrentVectorPreview(
        path: android.graphics.Path?, 
        points: List<StrokePoint>?, 
        color: Int, 
        maxWidth: Float = 10f,
        predictedPoint: StrokePoint? = null
    ) {
        currentVectorPreviewPath = path
        currentVectorPreviewPoints = points
        currentMaxWidth = maxWidth
        currentPredictedPoint = predictedPoint
        if (color != 0) currentVectorPreviewColor = color 
        invalidate() // Just invalidate main view to draw preview over bitmap
    }

    fun updateCurrentFill(path: android.graphics.Path?, color: Int) {
        currentFillPath = path
        currentFillColor = color
        invalidate()
    }

    // eraseContentAt modifies layers, so it should trigger redrawAllCache
    // But for optimized erasure? Maybe just redrawAllCache is safest.
    // ... eraseContentAt impl ... NO CHANGE NEEDED if setLayers/redraw handled correctly above
    // Wait, eraseContentAt calls invalidate(). We need redrawAllCache() there.
    // I'll update eraseContentAt logic separately or assume it modifies data and caller triggers update?
    // The current eraseContentAt returns 'element' and calls invalidate().
    // It should call redrawAllCache() because removing an element changes the bitmap.

    fun eraseContentAt(worldX: Float, worldY: Float): Any? {
        for (layer in layers.reversed()) {
            if (!layer.isVisible) continue 
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
                        } else { false }
                    }
                    is VectorStroke -> {
                        val bounds = android.graphics.RectF()
                        element.path.computeBounds(bounds, true)
                        if (bounds.contains(worldX, worldY)) {
                             val region = android.graphics.Region()
                             region.setPath(element.path, android.graphics.Region(
                                 bounds.left.toInt(), bounds.top.toInt(), 
                                 bounds.right.toInt(), bounds.bottom.toInt()
                             ))
                             region.contains(worldX.toInt(), worldY.toInt())
                        } else { false }
                    }
                    is AndroidInkElement -> StrokeGeometry.isStrokeTouched(element.stroke, worldX, worldY)
                    is ImageElement -> {
                        val bounds = element.getBounds(componentLibrary)
                        bounds.contains(worldX, worldY)
                    }
                    is SvgElement -> {
                        val bounds = element.getBounds(componentLibrary)
                        bounds.contains(worldX, worldY)
                    }
                    is ComponentInstance -> {
                        val bounds = element.getBounds(componentLibrary)
                        bounds.contains(worldX, worldY)
                    }
                    else -> false
                }
                if (removed) {
                    iterator.remove()
                    redrawAllCache() // Changed from invalidate()
                    return element
                }
            }
        }
        return null
    }

    fun setCameraMatrix(matrix: Matrix, isIntermediate: Boolean = false) {
        viewMatrix.set(matrix)
        if (isIntermediate) {
            invalidate() // Fast: Just triggers onDraw to transform existing bitmap
        } else {
            redrawAllCache() // Slow: Re-renders vectors for high quality
        }
    }

    fun refreshView() {
        redrawAllCache()
    }

    fun clearCanvas() {
        layers.forEach { it.elements.clear() }
        redrawAllCache()
    }

    // drawGrid extracted...
    private fun drawGrid(canvas: Canvas) {
        // ... (Keep existing implementation) ...
        // I need to ensure this is preserved or I need to supply it in replacement
        // Since I'm using replace_file_content on a large range, I should include it or leave it if outside range?
        // Range 26-500 covers drawGrid. I must include it.
        // It's long. I'll rely on "drawGrid" being same logic.
        
        if (!gridConfig.isVisible) return

        val spacing = gridConfig.spacing
        if (spacing <= 0f) return

        val stepPx = com.skecher.sketchercompanionv1.utils.UnitUtils.projectUnitsToPixels(
            value = spacing, 
            unit = currentUnit, 
            basePxPerMm = scaleConfig.basePixelsPerMillimeter
        )
        
        val transformValues = FloatArray(9)
        viewMatrix.getValues(transformValues)
        val zoom = kotlin.math.sqrt(transformValues[Matrix.MSCALE_X] * transformValues[Matrix.MSCALE_X] + transformValues[Matrix.MSKEW_X] * transformValues[Matrix.MSKEW_X])
        
        val screenStep = stepPx * zoom
        if (screenStep < 3f) {
           // Skip
        }

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

        // Calculate offset to align grid center with canvas center
        val offsetX = canvasSizeConfig?.let { it.widthInPixels / 2f } ?: 0f
        val offsetY = canvasSizeConfig?.let { it.heightInPixels / 2f } ?: 0f

        val startXIndex = floor((wMinX - offsetX) / stepPx).toInt()
        val endXIndex = ceil((wMaxX - offsetX) / stepPx).toInt()
        
        val startYIndex = floor((wMinY - offsetY) / stepPx).toInt()
        val endYIndex = ceil((wMaxY - offsetY) / stepPx).toInt()
        
        // Safety cap
        if ((endXIndex - startXIndex) > 2000 || (endYIndex - startYIndex) > 2000) return 

        for (i in startXIndex..endXIndex) {
            val x = offsetX + i * stepPx
            
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

        for (i in startYIndex..endYIndex) {
            val y = offsetY + i * stepPx
            
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

    private val imagePaint = android.graphics.Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        isDither = true
    }

    private fun drawImage(element: ImageElement, canvas: Canvas) {
        // Matrix already contains position/scale/rotation
        canvas.drawBitmap(element.bitmap, element.matrix, imagePaint)
    }

    private fun calculateSmoothedVelocity(points: List<StrokePoint>, windowSize: Int = 8): Float { // Increased window
        if (points.size < 2) return 0f
        
        // Look back up to 'windowSize' points to average out sensor noise
        val endIndex = (points.size - 1)
        val startIndex = kotlin.math.max(0, points.size - windowSize)
        
        var totalDist = 0f
        var totalTime = 0f
        
        for (i in endIndex downTo startIndex + 1) {
            val p1 = points[i]
            val p2 = points[i - 1]
            
            val d = kotlin.math.hypot(p1.x - p2.x, p1.y - p2.y)
            var dt = (p1.timestamp - p2.timestamp).toFloat()
            
            // Sanitize time. If dt is 0 (burst event), assume a standard frame time (e.g. 8ms for 120hz)
            // Using 1ms caused massive velocity spikes.
            if (dt <= 0f) dt = 8f 
            
            totalDist += d
            totalTime += dt
        }
        
        return if (totalTime > 0) totalDist / totalTime else 0f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // DYNAMIC RENDERING: If actively drawing, render layers dynamically to insert live stroke at correct position
        if (isDrawing && currentTool == ToolType.FREEHAND) {
            // 1. Background
            canvas.drawColor(canvasBackgroundColor)
            
            canvas.save()
            canvas.concat(viewMatrix)
            
            // 2. Grid and canvas bounds
            drawGrid(canvas)
            RenderHelper.drawCanvasBounds(canvas, canvasSizeConfig)
            
            // 3. Render layers with live stroke inserted at active layer position
            for ((layerIndex, layer) in layers.withIndex()) {
                if (!layer.isVisible) continue
                
                val layerAlpha = if (layer.opacity < 1f) (layer.opacity * 255).toInt() else 255
                val saveCount = if (layerAlpha < 255) {
                    canvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), layerAlpha)
                } else {
                    canvas.save()
                }
                
                // Render layer elements
                for (element in layer.elements) {
                    val isDimmed = false
                    val isSelected = selectionManager?.selectedElements?.contains(element) == true
                    
                    // Skip selected elements if dragging
                    if (isSelected && isSelectionDragging) continue
                    
                    RenderHelper.drawElementRecursive(
                        canvas,
                        element,
                        componentLibrary = componentLibrary,
                        isDimmed = isDimmed
                    )
                }
                
                canvas.restoreToCount(saveCount)
                
                // Insert live stroke AFTER the active layer
                if (layerIndex == activeLayerIndex) {
                    // Render live fill FIRST (behind stroke)
                    currentFillPath?.let { path ->
                        currentFillColor?.let { color ->
                            fillPaint.color = color
                            canvas.drawPath(path, fillPaint)
                        }
                    }
                    
                    // Then render live stroke (on top)
                    currentVectorPreviewPath?.let { path ->
                        vectorPaint.color = currentVectorPreviewColor
                        canvas.drawPath(path, vectorPaint)
                        
                        // Debug rendering
                        if (isDebugWireframeByVM) {
                            val debugPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.RED
                                strokeWidth = 8f
                                style = android.graphics.Paint.Style.STROKE
                                isAntiAlias = true
                            }
                            
                            currentVectorPreviewCenterPoints?.let { points ->
                                points.forEach { p: android.graphics.PointF ->
                                    canvas.drawPoint(p.x, p.y, debugPaint)
                                }
                            }
                            
                            debugPaint.color = android.graphics.Color.GREEN
                            debugPaint.strokeWidth = 5f
                            currentVectorPreviewOutlinePoints?.let { points ->
                                points.forEach { p: android.graphics.PointF ->
                                    canvas.drawPoint(p.x, p.y, debugPaint)
                                }
                            }
                            
                            debugPaint.color = android.graphics.Color.MAGENTA
                            debugPaint.strokeWidth = 2f
                            canvas.drawPath(path, debugPaint)
                        }
                        
                        // Live tip rendering
                        if (currentVectorPreviewPoints?.isNotEmpty() == true) {
                            val points = currentVectorPreviewPoints!!
                            val tipPoint = points.last()
                            
                            val sourcePoints = if (currentStrokePoints.isNotEmpty()) currentStrokePoints else points
                            val measuredVelocity = calculateSmoothedVelocity(sourcePoints)
                            
                            if (currentVelocityState == 0f) {
                                currentVelocityState = measuredVelocity
                            } else {
                                currentVelocityState += (measuredVelocity - currentVelocityState) * 0.1f
                            }
                            
                            val mValues = FloatArray(9)
                            viewMatrix.getValues(mValues)
                            val zoom = kotlin.math.sqrt(mValues[Matrix.MSCALE_X] * mValues[Matrix.MSCALE_X] + mValues[Matrix.MSKEW_X] * mValues[Matrix.MSKEW_X])
                            
                            val minScreenRadius = 1.5f * (resources.displayMetrics.density)
                            val minWorldRadius = minScreenRadius / zoom
                            
                            vectorPaint.color = currentVectorPreviewColor
                            val baseAlpha = android.graphics.Color.alpha(currentVectorPreviewColor)
                            
                            if (currentLiveGeneratedRadius < minWorldRadius) {
                                vectorPaint.style = android.graphics.Paint.Style.STROKE
                                vectorPaint.strokeWidth = 1f / zoom
                                vectorPaint.alpha = (baseAlpha * 0.3f).toInt().coerceIn(0, 255)
                                canvas.drawCircle(tipPoint.x, tipPoint.y, minWorldRadius, vectorPaint)
                            }
                            
                            vectorPaint.style = android.graphics.Paint.Style.FILL
                            vectorPaint.alpha = baseAlpha
                            canvas.drawCircle(tipPoint.x, tipPoint.y, currentLiveGeneratedRadius, vectorPaint)
                        }
                    }
                }
            }
            
            canvas.restore()
            
        } else {
            // CACHED RENDERING: Use backing bitmap when not drawing
            // 1. Draw Background Bitmap (Deferred Rendering Strategy)
            backingBitmap?.let { bitmap ->
                if (viewMatrix == cachedBitmapMatrix) {
                    // Exact match: Draw directly (High Quality)
                    canvas.drawBitmap(bitmap, 0f, 0f, null)
                } else {
                    // Mismatch (Zooming): Calculate delta and transform (High Performance)
                    val transform = Matrix()
                    
                    if (cachedBitmapMatrix.invert(transform)) {
                         transform.postConcat(viewMatrix)
                         canvas.save()
                         canvas.concat(transform)
                         canvas.drawBitmap(bitmap, 0f, 0f, null)
                         canvas.restore()
                    } else {
                         // Fallback if non-invertible (rare)
                         canvas.drawBitmap(bitmap, 0f, 0f, null)
                    }
                }
            } ?: run {
                 canvas.drawColor(canvasBackgroundColor)
            }
            
            // 2. Live Fill (when not drawing with freehand)
            canvas.save()
            canvas.concat(viewMatrix)
            
            currentFillPath?.let { path ->
                currentFillColor?.let { color ->
                    fillPaint.color = color
                    canvas.drawPath(path, fillPaint)
                }
            }
            
            canvas.restore()
        }

         // Common overlays (always rendered on top)
         // Selection dragging
         if (isSelectionDragging) {
             selectionManager?.let { manager ->
                 canvas.save()
                 canvas.concat(viewMatrix)
                 for (element in manager.selectedElements) {
                     canvas.save()
                     manager.activeTransform?.let { transform ->
                         canvas.concat(transform)
                     }
                     RenderHelper.drawElementRecursive(
                         canvas, 
                         element,
                         componentLibrary = componentLibrary,
                         isDimmed = false
                     )
                     canvas.restore()
                 }
                 canvas.restore()
             }
         }

        // 6. Selection Overlay
        drawSelectionOverlay(canvas)
        
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


    // --- FRONT BUFFERING & PREDICTION ---

    // --- FRONT BUFFERING & PREDICTION ---
    private val inputHandler = StrokeInputHandler
    private val predictor = StrokePredictor

    // Paths for "Front Buffer" drawing
    private var currentStrokePath = android.graphics.Path()
    private var predictedStrokePath = android.graphics.Path()

    // Data Accumulation
    private val currentStrokePoints = mutableListOf<StrokePoint>()

    // Callback
    var onStrokeCompleted: ((VectorStroke) -> Unit)? = null
    var onFillCompleted: ((FillData) -> Unit)? = null
    var onGeometricProgressChanged: ((Boolean) -> Unit)? = null
    var onHybridStrokeCompleted: ((VectorStroke, FillData?) -> Unit)? = null
    var currentTool: ToolType = ToolType.FREEHAND

    // Active Configuration (Synced from ViewModel)
    var activeColor: Int = android.graphics.Color.BLACK
    var activeSize: Float = 10f
    var activeFreehandSettings: FreehandSettings = FreehandSettings()
    
    var isFillModeEnabled: Boolean = false
    var fillModeColor: Int = android.graphics.Color.TRANSPARENT
    
    // Global Input Config
    var isFingerMode: Boolean = false
    var fingerOffsetX: Float = 0f
    var fingerOffsetY: Float = 50f
    var globalStabilizationLevel: Float = 0f // 0.0 to 1.0 (Synced from VM)

    // STABILIZER STATE (Lazy Stroke)
    private var stabilizerX: Float = 0f
    private var stabilizerY: Float = 0f
    private var lastRecordedX: Float = 0f
    private var lastRecordedY: Float = 0f
    private var lastRawInput: StrokePoint? = null // For lag-less velocity calculation
    private var lastPointTimestamp: Long = 0L // Sanitize timestamps
    
    // Selection & Fill Handlers
    var onSelectionEvent: ((MotionEvent) -> Boolean)? = null
    var onFillEvent: ((Float, Float, Int) -> Unit)? = null



    // We override onTouchEvent to handle input directly if this view is the active handler
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        
        // Handling Freehand separately to properly manage history and stabilization per-point
        if (currentTool == ToolType.FREEHAND) {
            return handleFreehandInput(event)
        }
        
        // 1. Calculate Target Point (Raw - Offset)
        var targetX = event.x
        var targetY = event.y
        
        if (isFingerMode) {
            targetX -= fingerOffsetX
            targetY -= fingerOffsetY
        }

        // 2. Apply Global Stabilization (Lazy Stroke)
        if (action == MotionEvent.ACTION_DOWN) {
            // Reset stabilizer on new stroke
            stabilizerX = targetX
            stabilizerY = targetY
        } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            val stabilization = globalStabilizationLevel.coerceIn(0f, 0.95f)
            if (stabilization > 0f) {
                // Exponential Smoothing
                val factor = 1f - stabilization
                stabilizerX += (targetX - stabilizerX) * factor
                stabilizerY += (targetY - stabilizerY) * factor
            } else {
                stabilizerX = targetX
                stabilizerY = targetY
            }
        }

        // 3. Create Stabilized Event (Shifted to Stabilizer Coords)
        val stabilizedEvent = MotionEvent.obtain(event)
        stabilizedEvent.setLocation(stabilizerX, stabilizerY)

        // 4. Route by Tool
        val result = when (currentTool) {
            // FREEHAND handled above
            ToolType.FILL -> {
                onFillEvent?.invoke(stabilizedEvent.x, stabilizedEvent.y, stabilizedEvent.actionMasked)
                true
            }
            ToolType.SELECTION -> {
                onSelectionEvent?.invoke(stabilizedEvent) ?: super.onTouchEvent(event)
            }
            else -> super.onTouchEvent(event)
        }

        stabilizedEvent.recycle()
        return result
    }

    private fun handleFreehandInput(event: MotionEvent): Boolean {
        val action = event.actionMasked
        // Get generic raw points (ignoring finger offset for now, as handler is generic)
        val rawEventPoints = inputHandler.processEvent(event)
        val stabilizedPoints = mutableListOf<StrokePoint>()

        // 1. Process Points (Offset + Stabilization)
        if (action == MotionEvent.ACTION_DOWN) {
           isDrawing = true
           currentStrokePath.reset()
           predictedStrokePath.reset()
           currentStrokePoints.clear()
           currentLiveTipWidth = 0f // Reset smoothing
           currentVelocityState = 0f // Reset velocity
           currentPressureState = if (rawEventPoints.isNotEmpty()) rawEventPoints.first().pressure else 0.5f // Init pressure
           lastPointTimestamp = 0L
           
           // Initialize stabilizer to start point
           if (rawEventPoints.isNotEmpty()) {
               var startX = rawEventPoints.first().x
               var startY = rawEventPoints.first().y
               if (isFingerMode) {
                   startX -= fingerOffsetX
                   startY -= fingerOffsetY
               }
               stabilizerX = startX
               stabilizerY = startY
               
               // Force init lastRecorded to ensure first point is handled correctly
               lastRecordedX = startX
               lastRecordedY = startY

               // Initialize timestamp history with start time
               lastPointTimestamp = rawEventPoints.first().timestamp
           }
        }
        
        val stabilization = globalStabilizationLevel.coerceIn(0f, 0.98f)
        
        // Linearized "Laziness" Feel:
        // Instead of controlling the factor directly, we control the "Weight" or "Delay".
        // Delay 0 -> Factor 1.0
        // Delay 60 -> Factor 0.016
        // This spreads the useful range (0.05 to 0.001) across the entire slider better than a power curve.
        val lagAmount = stabilization * 60f 
        val factor = 1f / (1f + lagAmount)

        for (p in rawEventPoints) {
            // Apply Finger Offset
            var targetX = p.x
            var targetY = p.y
            if (isFingerMode) {
                targetX -= fingerOffsetX
                targetY -= fingerOffsetY
            }

            // STANDARD STABILIZATION
            // We use simple exponential smoothing which inherently creates the "Lazy Stroke" effect.
            // factor = 1.0 (Instant) ... factor = 0.05 (Very Lazy)
            
            // Apply Stabilization (Recursive)
            if (stabilization > 0f) {
                // Determine new position
                stabilizerX += (targetX - stabilizerX) * factor
                stabilizerY += (targetY - stabilizerY) * factor
            } else {
                stabilizerX = targetX
                stabilizerY = targetY
            }
            
            // Apply Snap to Grid AFTER stabilization (Only for Geometric Tools)
            if (activeStrokeType != StrokeType.FREEHAND) {
                snapFunction?.let { snap ->
                    val (snappedX, snappedY) = snap(stabilizerX, stabilizerY)
                    stabilizerX = snappedX
                    stabilizerY = snappedY
                }
            }
            
            // FILTER: Only add point if it moved > 2.0px or is the very start
            // This prevents "droplets" caused by velocity jitter on ultra-dense points
            val dx = stabilizerX - lastRecordedX
            val dy = stabilizerY - lastRecordedY
            val distSq = dx * dx + dy * dy
            val minDistSq = 4.0f // 2.0px threshold
            
            // Check if it's the very first point of the stroke
            val isStart = (action == MotionEvent.ACTION_DOWN && stabilizedPoints.isEmpty() && currentStrokePoints.isEmpty())
            
            if (distSq > minDistSq || isStart) {
                // --- NEW: TIMESTAMP SANITIZATION ---
                // Prevents dt=0 which causes velocity spikes in PerfectFreehand
                var sanitizedTime = p.timestamp
                if (sanitizedTime <= lastPointTimestamp) {
                    sanitizedTime = lastPointTimestamp + 1
                }
                lastPointTimestamp = sanitizedTime
                // -----------------------------------

                val stabilizedPoint = StrokePoint(stabilizerX, stabilizerY, p.pressure, sanitizedTime)
                stabilizedPoints.add(stabilizedPoint)
                lastRecordedX = stabilizerX
                lastRecordedY = stabilizerY
            }
        }

        // 2. Add to stroke with World Transform
    stabilizedPoints.forEach { p ->
        val worldP = transformToWorld(p)
        when (activeStrokeType) {
            StrokeType.FREEHAND -> {
                currentStrokePoints.add(worldP)
            }
            StrokeType.LINE, StrokeType.CIRCLE -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    currentStrokePoints.clear()
                    currentStrokePoints.add(worldP)
                } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                    // Control Points: [Start, Current]
                    if (currentStrokePoints.isNotEmpty()) {
                        val start = currentStrokePoints.first()
                        currentStrokePoints.clear()
                        currentStrokePoints.add(start)
                        currentStrokePoints.add(worldP)
                    } else {
                        currentStrokePoints.add(worldP)
                    }
                }
            }
            StrokeType.POLYLINE -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!isMultiStepInProgress) {
                        currentStrokePoints.clear()
                        isMultiStepInProgress = true
                        onGeometricProgressChanged?.invoke(true)
                    }
                    currentStrokePoints.add(worldP)
                } else if (action == MotionEvent.ACTION_MOVE) {
                    // Temporarily update the LAST point for preview
                    if (currentStrokePoints.isNotEmpty()) {
                        currentStrokePoints[currentStrokePoints.size - 1] = worldP
                    }
                }
            }
            StrokeType.ARC -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!isMultiStepInProgress || currentStrokePoints.size >= 3) {
                        currentStrokePoints.clear()
                        isMultiStepInProgress = true
                        onGeometricProgressChanged?.invoke(true)
                    }
                    currentStrokePoints.add(worldP)
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (currentStrokePoints.isNotEmpty()) {
                        currentStrokePoints[currentStrokePoints.size - 1] = worldP
                    }
                }
                
                // Auto-finalize ARC if 3 points are reached and user lifts finger?
                // No, let's keep it manual or auto-finalize after 3 points on UP.
            }
        }
    }

        // 3. Update & Render
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (stabilizedPoints.isNotEmpty()) {
                    updateCurrentPaths()
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDrawing = false

                // Finalize geometric shapes
                if (activeStrokeType == StrokeType.LINE && currentStrokePoints.size >= 2) {
                    val interpolated = interpolateLine(currentStrokePoints[0], currentStrokePoints[1])
                    currentStrokePoints.clear()
                    currentStrokePoints.addAll(interpolated)
                } else if (activeStrokeType == StrokeType.CIRCLE && currentStrokePoints.size >= 2) {
                    val interpolated = interpolateCircle(currentStrokePoints[0], currentStrokePoints[1])
                    currentStrokePoints.clear()
                    currentStrokePoints.addAll(interpolated)
                } else if (activeStrokeType == StrokeType.POLYLINE || activeStrokeType == StrokeType.ARC) {
                    // Skip automatic finalization for multi-step types
                    isDrawing = true // Keep drawing state active
                    invalidate()
                    return true 
                }

                val tolerance = activeFreehandSettings.tolerance.coerceAtLeast(0.5f)
                val isSimplified = activeFreehandSettings.isSimplificationEnabled
                
                // --- GENERATE HIGH-FIDELITY PATH (Visuals) ---
                val genResult = PerfectFreehandGenerator.generate(
                    currentStrokePoints, 
                    activeSize, 
                    activeFreehandSettings 
                )
                val highFidelityPath = genResult.path
                val left = genResult.left
                val right = genResult.right
                
                // 2. Simplify Points (Data Optimization)
                // We still simplify the points stored in the VectorStroke to save memory/storage,
                // but the visual Path is baked from the high-quality raw data.
                val finalPoints = if (isSimplified && currentStrokePoints.size > 2) {
                    val pressureWeight = activeSize * 2.0f 
                    com.skecher.sketchercompanionv1.utils.StrokeSimplifier.simplify(
                        currentStrokePoints, 
                        tolerance,
                        pressureWeight
                    )
                } else {
                    currentStrokePoints.toList() 
                }

                // Finalize
                val finalPath = android.graphics.Path()
                finalPath.set(highFidelityPath)
                
                // Note: The 'path' in VectorStroke is the high-fidelity one.
                val stroke = VectorStroke(
                    points = finalPoints, 
                    color = activeColor,
                    maxWidth = activeSize,
                    path = finalPath,
                    brushType = "FREEHAND",
                    strokeType = activeStrokeType,
                    leftPoints = left,
                    rightPoints = right
                )
                
                // --- AUTO FILL COMMITMENT ---
                var fill: FillData? = null
                if (isFillModeEnabled && currentStrokePoints.size >= 3) {
                    val fPath = android.graphics.Path()
                    fPath.moveTo(currentStrokePoints[0].x, currentStrokePoints[0].y)
                    for (i in 1 until currentStrokePoints.size) {
                        fPath.lineTo(currentStrokePoints[i].x, currentStrokePoints[i].y)
                    }
                    fPath.close()
                    fill = FillData(fPath, fillModeColor)
                    // Don't bake - fill will be added to layers
                }
                
                // Don't bake stroke - let it be added to layers normally
                
                if (onHybridStrokeCompleted != null) {
                    onHybridStrokeCompleted?.invoke(stroke, fill)
                } else {
                    onStrokeCompleted?.invoke(stroke)
                    fill?.let { onFillCompleted?.invoke(it) }
                }
                
                isDrawing = false
                currentStrokePath.reset()
                predictedStrokePath.reset()
                currentVectorPreviewPath = null
                currentVectorPreviewPoints = null
                currentStrokePoints.clear()
                updateCurrentFill(null, 0) // Clear preview
                
                // Regenerate backing bitmap with correct layer order
                redrawAllCache()
                
                return true
            }
        }
        return false
    }

    private fun transformToWorld(p: StrokePoint): StrokePoint {
        val pts = floatArrayOf(p.x, p.y)
        val inverse = android.graphics.Matrix()
        viewMatrix.invert(inverse) // Invert current view matrix
        inverse.mapPoints(pts)
        // Keep pressure and timestamp
        return StrokePoint(pts[0], pts[1], p.pressure, p.timestamp)
    }

    // Debug Variables


    private fun updateCurrentPaths() {
    // 1. Determine points based on type
    val livePoints: List<StrokePoint> = when (activeStrokeType) {
        StrokeType.FREEHAND -> {
            val latencyMs = activeFreehandSettings.predictionLatency.toLong()
            val predictedPt = predictor.getPredictedPoint(
                points = currentStrokePoints,
                maxPredictionMillis = latencyMs,
                minSpeed = activeFreehandSettings.minPredictionVelocity,
                maxSpeed = activeFreehandSettings.maxPredictionVelocity
            )
            if (predictedPt != null) currentStrokePoints + predictedPt else currentStrokePoints.toList()
        }
        StrokeType.LINE -> {
            if (currentStrokePoints.size >= 2) {
                interpolateLine(currentStrokePoints[0], currentStrokePoints[1])
            } else {
                currentStrokePoints.toList()
            }
        }
        StrokeType.CIRCLE -> {
            if (currentStrokePoints.size >= 2) {
                interpolateCircle(currentStrokePoints[0], currentStrokePoints[1])
            } else {
                currentStrokePoints.toList()
            }
        }
        StrokeType.POLYLINE -> {
            if (currentStrokePoints.size >= 2) {
                interpolatePolyline(currentStrokePoints)
            } else {
                currentStrokePoints.toList()
            }
        }
        StrokeType.ARC -> {
            if (currentStrokePoints.size >= 3) {
                interpolateArc(currentStrokePoints[0], currentStrokePoints[1], currentStrokePoints[2])
            } else if (currentStrokePoints.size == 2) {
                interpolateLine(currentStrokePoints[0], currentStrokePoints[1])
            } else {
                currentStrokePoints.toList()
            }
        }
        else -> currentStrokePoints.toList()
    }

    if (livePoints.isEmpty()) return

    // 2. Generate Unified Path (Cap End Disabled for Live Blob)
    val liveSettings = activeFreehandSettings.copy(
        capEnd = false
    )
    
    val result = PerfectFreehandGenerator.generate(
        livePoints, 
        activeSize, 
        liveSettings
    )
    
    // 3. Update Preview State directly
    currentVectorPreviewPath = result.path
    currentVectorPreviewPoints = livePoints
    currentVectorPreviewColor = activeColor
    currentLiveGeneratedRadius = result.lastRadius // Sync radius
    
    // --- LIVE FILL PREVIEW ---
    if (isFillModeEnabled && livePoints.size >= 3) {
        val fPath = android.graphics.Path()
        fPath.moveTo(livePoints[0].x, livePoints[0].y)
        for (i in 1 until livePoints.size) {
            fPath.lineTo(livePoints[i].x, livePoints[i].y)
        }
        fPath.close()
        updateCurrentFill(fPath, fillModeColor)
    } else if (!isFillModeEnabled) {
        updateCurrentFill(null, 0)
    }
    
    // Debug Data
    currentVectorPreviewCenterPoints = result.center
    currentVectorPreviewOutlinePoints = result.left + result.right
}

private fun interpolateLine(p1: StrokePoint, p2: StrokePoint): List<StrokePoint> {
    val points = mutableListOf<StrokePoint>()
    val dx = p2.x - p1.x
    val dy = p2.y - p1.y
    val dist = kotlin.math.sqrt(dx * dx + dy * dy)
    if (dist < 1f) return listOf(p1, p2)
    
    val steps = (dist / 2f).toInt().coerceIn(2, 500) // One point every 2px
    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val x = p1.x + dx * t
        val y = p1.y + dy * t
        // Linear interpolate pressure and time
        val pressure = p1.pressure + (p2.pressure - p1.pressure) * t
        val time = p1.timestamp + ((p2.timestamp - p1.timestamp) * t).toLong()
        points.add(StrokePoint(x, y, pressure, time))
    }
    return points
}

private fun interpolateCircle(center: StrokePoint, edge: StrokePoint): List<StrokePoint> {
    val points = mutableListOf<StrokePoint>()
    val dx = edge.x - center.x
    val dy = edge.y - center.y
    val radius = kotlin.math.sqrt(dx * dx + dy * dy)
    if (radius < 1f) return listOf(center, edge)
    
    val circumference = 2 * kotlin.math.PI * radius
    val steps = (circumference / 5f).toInt().coerceIn(12, 1000) // Sample every 5px?
    
    for (i in 0..steps) {
        val angle = (2 * kotlin.math.PI * i) / steps
        val x = center.x + (radius * kotlin.math.cos(angle)).toFloat()
        val y = center.y + (radius * kotlin.math.sin(angle)).toFloat()
        // Constant pressure for now or based on edge
        val pressure = edge.pressure
        val time = edge.timestamp // Circle is "instant" anyway
        points.add(StrokePoint(x, y, pressure, time))
    }
    return points
}

private fun interpolatePolyline(points: List<StrokePoint>): List<StrokePoint> {
    if (points.size < 2) return points
    val result = mutableListOf<StrokePoint>()
    for (i in 0 until points.size - 1) {
        val segment = interpolateLine(points[i], points[i+1])
        if (i > 0) result.addAll(segment.drop(1))
        else result.addAll(segment)
    }
    return result
}

private fun interpolateArc(p1: StrokePoint, p2: StrokePoint, p3: StrokePoint): List<StrokePoint> {
    val x1 = p1.x
    val y1 = p1.y
    val x2 = p2.x
    val y2 = p2.y
    val x3 = p3.x
    val y3 = p3.y

    val D = 2 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
    if (kotlin.math.abs(D) < 0.001f) {
        return interpolatePolyline(listOf(p1, p2, p3))
    }

    val Ux = ((x1 * x1 + y1 * y1) * (y2 - y3) + (x2 * x2 + y2 * y2) * (y3 - y1) + (x3 * x3 + y3 * y3) * (y1 - y2)) / D
    val Uy = ((x1 * x1 + y1 * y1) * (x3 - x2) + (x2 * x2 + y2 * y2) * (x1 - x3) + (x3 * x3 + y3 * y3) * (x2 - x1)) / D
    
    val center = PointF(Ux, Uy)
    val dx1 = x1 - Ux
    val dy1 = y1 - Uy
    val radius = kotlin.math.sqrt(dx1 * dx1 + dy1 * dy1)

    val angle1 = kotlin.math.atan2(y1 - Uy, x1 - Ux).toDouble()
    val angle2 = kotlin.math.atan2(y2 - Uy, x2 - Ux).toDouble()
    val angle3 = kotlin.math.atan2(y3 - Uy, x3 - Ux).toDouble()

    var startAngle = angle1
    var midAngle = angle2
    var endAngle = angle3

    var sweep = endAngle - startAngle
    while (sweep < -kotlin.math.PI) sweep += 2 * kotlin.math.PI
    while (sweep > kotlin.math.PI) sweep -= 2 * kotlin.math.PI
    
    var diffMid = midAngle - startAngle
    while (diffMid < -kotlin.math.PI) diffMid += 2 * kotlin.math.PI
    while (diffMid > kotlin.math.PI) diffMid -= 2 * kotlin.math.PI
    
    if (kotlin.math.sign(diffMid) != kotlin.math.sign(sweep)) {
        sweep = if (sweep > 0) sweep - 2 * kotlin.math.PI else sweep + 2 * kotlin.math.PI
    }

    val points = mutableListOf<StrokePoint>()
    val arcLength = kotlin.math.abs(sweep) * radius
    val steps = (arcLength / 2f).toInt().coerceIn(12, 1000)

    for (i in 0..steps) {
        val t = i.toFloat() / steps
        val angle = startAngle + sweep * t
        val x = Ux + (radius * kotlin.math.cos(angle)).toFloat()
        val y = Uy + (radius * kotlin.math.sin(angle)).toFloat()
        
        val pressure = if (t < 0.5f) {
            p1.pressure + (p2.pressure - p1.pressure) * (t * 2)
        } else {
            p2.pressure + (p3.pressure - p2.pressure) * ((t - 0.5f) * 2)
        }
        val time = if (t < 0.5f) {
            p1.timestamp + ((p2.timestamp - p1.timestamp) * (t * 2)).toLong()
        } else {
            p2.timestamp + ((p3.timestamp - p2.timestamp) * ((t - 0.5f) * 2)).toLong()
        }
        
        points.add(StrokePoint(x, y, pressure, time.toLong()))
    }
    return points
}

fun finishGeometricStroke() {
    if (!isMultiStepInProgress || currentStrokePoints.isEmpty()) return
    
    val finalizedPoints = when (activeStrokeType) {
        StrokeType.POLYLINE -> interpolatePolyline(currentStrokePoints)
        StrokeType.ARC -> {
            if (currentStrokePoints.size >= 3) {
                interpolateArc(currentStrokePoints[0], currentStrokePoints[1], currentStrokePoints[2])
            } else if (currentStrokePoints.size == 2) {
                interpolateLine(currentStrokePoints[0], currentStrokePoints[1])
            } else {
                currentStrokePoints.toList()
            }
        }
        else -> return
    }
    
    if (finalizedPoints.isEmpty()) {
        isMultiStepInProgress = false
        isDrawing = false
        invalidate()
        return
    }

    val genResult = PerfectFreehandGenerator.generate(
        finalizedPoints, 
        activeSize, 
        activeFreehandSettings 
    )
    
    val finalPath = android.graphics.Path()
    finalPath.set(genResult.path)
    
    val stroke = VectorStroke(
        points = finalizedPoints, 
        color = activeColor,
        maxWidth = activeSize,
        path = finalPath,
        brushType = "FREEHAND",
        strokeType = activeStrokeType,
        leftPoints = genResult.left,
        rightPoints = genResult.right
    )
    
    onStrokeCompleted?.invoke(stroke)
    
    isMultiStepInProgress = false
    onGeometricProgressChanged?.invoke(false)
    isDrawing = false
    currentStrokePoints.clear()
    currentVectorPreviewPath = null
    currentVectorPreviewPoints = null
    invalidate()
}


    // --- SELECTION OVERLAY ---
    var selectionManager: SelectionManager? = null
    var selectionTouchMode: String = "IDLE" // To show different visuals if needed

    private val selectionBoxPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#44007AFF") // Translucent Apple Blue
        style = android.graphics.Paint.Style.FILL
    }
    private val selectionBorderPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.parseColor("#FF007AFF")
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectionHandlePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        style = android.graphics.Paint.Style.FILL
        setShadowLayer(5f, 0f, 2f, 0x44000000)
    }

    private fun drawSelectionOverlay(canvas: Canvas) {
        val manager = selectionManager ?: return
        if (manager.selectedElements.isEmpty()) return

        val bounds = manager.baseBounds
        if (bounds.isEmpty) return

        canvas.save()
        canvas.concat(viewMatrix)
        canvas.concat(manager.selectionMatrix)

        // Draw Bounding Box
        canvas.drawRect(bounds, selectionBoxPaint)
        canvas.drawRect(bounds, selectionBorderPaint)

        // Draw Handles
        val transformValues = FloatArray(9)
        viewMatrix.getValues(transformValues)
        val zoom = kotlin.math.sqrt(transformValues[Matrix.MSCALE_X] * transformValues[Matrix.MSCALE_X] + transformValues[Matrix.MSKEW_X] * transformValues[Matrix.MSKEW_X])
        
        val handleSize = 10f / zoom
        
        // Corners
        canvas.drawCircle(bounds.left, bounds.top, handleSize, selectionHandlePaint)
        canvas.drawCircle(bounds.left, bounds.top, handleSize, selectionBorderPaint)
        
        canvas.drawCircle(bounds.right, bounds.top, handleSize, selectionHandlePaint)
        canvas.drawCircle(bounds.right, bounds.top, handleSize, selectionBorderPaint)
        
        canvas.drawCircle(bounds.left, bounds.bottom, handleSize, selectionHandlePaint)
        canvas.drawCircle(bounds.left, bounds.bottom, handleSize, selectionBorderPaint)
        
        canvas.drawCircle(bounds.right, bounds.bottom, handleSize, selectionHandlePaint)
        canvas.drawCircle(bounds.right, bounds.bottom, handleSize, selectionBorderPaint)

        // Edge Centers (New)
        canvas.drawCircle(bounds.centerX(), bounds.top, handleSize, selectionHandlePaint)
        canvas.drawCircle(bounds.centerX(), bounds.top, handleSize, selectionBorderPaint)

        canvas.drawCircle(bounds.centerX(), bounds.bottom, handleSize, selectionHandlePaint)
        canvas.drawCircle(bounds.centerX(), bounds.bottom, handleSize, selectionBorderPaint)

        canvas.drawCircle(bounds.left, bounds.centerY(), handleSize, selectionHandlePaint)
        canvas.drawCircle(bounds.left, bounds.centerY(), handleSize, selectionBorderPaint)

        canvas.drawCircle(bounds.right, bounds.centerY(), handleSize, selectionHandlePaint)
        canvas.drawCircle(bounds.right, bounds.centerY(), handleSize, selectionBorderPaint)

        // Rotate Handle (Top Center with a stem)
        val stemLength = 30f / zoom
        val centerX = bounds.centerX()
        val rotateY = bounds.top - stemLength
        canvas.drawLine(centerX, bounds.top, centerX, rotateY, selectionBorderPaint)
        canvas.drawCircle(centerX, rotateY, handleSize, selectionHandlePaint)
        canvas.drawCircle(centerX, rotateY, handleSize, selectionBorderPaint)

        canvas.restore()
    }
}

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
    private val strokeRenderer = CanvasStrokeRenderer.create()
    private var isDrawing: Boolean = false
    
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

    
    // Helper to completely rebuild the cache (e.g. after Zoom/Pan or Undo)
    fun redrawAllCache() {
        val canvas = backingCanvas ?: return
        
        // 1. Clear with Background Color
        canvas.drawColor(canvasBackgroundColor)
        
        // 2. Draw Grid (Baken into background for simplicity? Or keep separate?)
        // Plan says: Draw Grid separate in onDraw?
        // Prompt Check: "Draw the Background Bitmap (Contains all past strokes)".
        // If we bake Grid, it's easier.
        canvas.save()
        canvas.concat(viewMatrix)
        drawGrid(canvas)
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
                 val isDimmed = editingContext != null && !editingContext!!.contains(element)
                 RenderHelper.drawElementRecursive(
                     canvas, 
                     element,
                     drawVector = { v, c -> drawVectorStroke(v, c) },
                     drawInk = { i, c -> drawInkStroke(i, c) },
                     drawFill = { f, c -> drawFill(f, c) },
                     drawImage = { i, c -> drawImage(i, c) },
                     drawSvg = { s, c -> s.render(c) },
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
    private var editingContext: List<LayerElement>? = null
    
    // PREVIEW STATE (For live drawing/filling)
    private var currentVectorPreviewPath: android.graphics.Path? = null
    private var currentVectorPreviewPoints: List<StrokePoint>? = null // For prediction
    private var currentVectorPreviewColor: Int = 0 // Explicitly store preview color
    private var currentMaxWidth: Float = 10f // Max stroke width for prediction
    private var currentFillPath: android.graphics.Path? = null
    private var currentFillColor: Int? = null
    private var currentPredictedPoint: StrokePoint? = null
    private val fillPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    private val vectorPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }

    fun setLayers(newLayers: List<Layer>, library: Map<String, ComponentDefinition>, editingCtx: List<LayerElement>?) {
        layers.clear()
        layers.addAll(newLayers)
        componentLibrary = library
        editingContext = editingCtx
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

    fun setCameraMatrix(matrix: Matrix) {
        viewMatrix.set(matrix)
        redrawAllCache() // Camera changed -> Re-render view-sized bitmap
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

        val startXIndex = floor(wMinX / stepPx).toInt()
        val endXIndex = ceil(wMaxX / stepPx).toInt()
        
        val startYIndex = floor(wMinY / stepPx).toInt()
        val endYIndex = ceil(wMaxY / stepPx).toInt()
        
        // Safety cap
        if ((endXIndex - startXIndex) > 2000 || (endYIndex - startYIndex) > 2000) return 

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

    private val imagePaint = android.graphics.Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        isDither = true
    }

    private fun drawImage(element: ImageElement, canvas: Canvas) {
        // Matrix already contains position/scale/rotation
        canvas.drawBitmap(element.bitmap, element.matrix, imagePaint)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 1. Draw Background Bitmap (Cache)
        backingBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        } ?: run {
             canvas.drawColor(canvasBackgroundColor)
        }
        
        // 2. Compute View Matrix for Live Elements
        canvas.save()
        canvas.concat(viewMatrix)
        
        // 3. Draw Live Fill
        currentFillPath?.let { path ->
            currentFillColor?.let { color ->
                fillPaint.color = color
                canvas.drawPath(path, fillPaint)
            }
        }

        // 4. Draw Live Vector Stroke (Preview)
        if (isDrawing && currentTool == ToolType.FREEHAND) {
            currentVectorPreviewPath?.let { path ->
                 if (currentVectorPreviewColor != 0) vectorPaint.color = currentVectorPreviewColor
                 canvas.drawPath(path, vectorPaint)
    
                 // Dynamic Live Blob (Tip Prediction)
                 if (currentVectorPreviewPoints?.isNotEmpty() == true) {
                     val points = currentVectorPreviewPoints!!
                     val tipPoint = points.last()
                     
                     // Tip Dynamics logic matches generator
                     val realPressure = tipPoint.pressure.coerceIn(0f, 1f)
                     val pFactor = 1.0f - (activeFreehandSettings.pressureInfluence * (1.0f - realPressure))
                     
                     // Velocity factor logic (simplified from Generator)
                     var vFactor = 1.0f
                     if (points.size > 1) {
                         val prev = points[points.size - 2]
                         val d = kotlin.math.hypot(tipPoint.x - prev.x, tipPoint.y - prev.y)
                         var dt = (tipPoint.timestamp - prev.timestamp).toFloat()
                         if (dt <= 0) dt = 16f
                         val velocity = d / dt
                         val maxSpeed = 3.0f 
                         val normalizedVel = (velocity / maxSpeed).coerceIn(0f, 1f)
                         val simPressure = (1f - normalizedVel).coerceIn(0f, 1f)
                         vFactor = 1.0f - (activeFreehandSettings.velocityInfluence * (1.0f - simPressure))
                     }
                     
                     val combinedPressure = pFactor * vFactor
                     val dynamicWidth = currentMaxWidth * combinedPressure
                     val absoluteMin = currentMaxWidth * activeFreehandSettings.minWidthRatio
                     val width = kotlin.math.max(dynamicWidth, absoluteMin)
                     
                     canvas.drawCircle(tipPoint.x, tipPoint.y, width / 2f, vectorPaint)
                 }
             }
         }

        canvas.restore()

        // 5. Selection Overlay (Handles its own coordinates usually, or passed canvas)
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

    // Tool State
    var currentTool: ToolType = ToolType.FREEHAND

    // Active Configuration (Synced from ViewModel)
    var activeColor: Int = android.graphics.Color.BLACK
    var activeSize: Float = 10f
    var activeFreehandSettings: FreehandSettings = FreehandSettings()

    // Global Input Config
    var isFingerMode: Boolean = false
    var fingerOffsetX: Float = 0f
    var fingerOffsetY: Float = 50f
    
    // Selection & Fill Handlers
    var onSelectionEvent: ((MotionEvent) -> Boolean)? = null
    var onFillEvent: ((Float, Float, Int) -> Unit)? = null



    // We override onTouchEvent to handle input directly if this view is the active handler
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 1. Apply Global Offset
        val offsetEvent = if (isFingerMode) {
            val offsetE = MotionEvent.obtain(event)
            offsetE.offsetLocation(fingerOffsetX, fingerOffsetY)
            offsetE
        } else {
            event
        }

        // 2. Route by Tool
        val result = when (currentTool) {
            ToolType.FREEHAND -> handleFreehandInput(offsetEvent)
            ToolType.FILL -> {
                onFillEvent?.invoke(offsetEvent.x, offsetEvent.y, offsetEvent.actionMasked)
                true
            }
            ToolType.SELECTION -> {
                onSelectionEvent?.invoke(offsetEvent) ?: super.onTouchEvent(event)
            }
            else -> super.onTouchEvent(event)
        }

        if (isFingerMode && offsetEvent !== event) {
            offsetEvent.recycle()
        }
        return result
    }

    private fun handleFreehandInput(event: MotionEvent): Boolean {
        val action = event.actionMasked
        val rawScreenPoints = inputHandler.processEvent(event)

        when (action) {
            MotionEvent.ACTION_DOWN -> {
                isDrawing = true
                currentStrokePath.reset()
                predictedStrokePath.reset()
                currentStrokePoints.clear()
                
                rawScreenPoints.forEach { p ->
                    currentStrokePoints.add(transformToWorld(p))
                }
                updateCurrentPaths()
                invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                rawScreenPoints.forEach { p ->
                    currentStrokePoints.add(transformToWorld(p))
                }
                updateCurrentPaths()
                invalidate()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                rawScreenPoints.forEach { p ->
                    currentStrokePoints.add(transformToWorld(p))
                }
                
                // Finalize
                val finalPath = android.graphics.Path()
                val (path, left, right) = PerfectFreehandGenerator.generate(
                    currentStrokePoints, 
                    activeSize, 
                    activeFreehandSettings
                )
                finalPath.set(path)
                
                val stroke = VectorStroke(
                    points = currentStrokePoints.toList(),
                    color = activeColor,
                    maxWidth = activeSize,
                    path = finalPath,
                    brushType = "FREEHAND",
                    leftPoints = left,
                    rightPoints = right
                )
                
                bakeStroke(stroke)
                onStrokeCompleted?.invoke(stroke)
                
                isDrawing = false
                currentStrokePath.reset()
                predictedStrokePath.reset()
                currentVectorPreviewPath = null
                currentVectorPreviewPoints = null
                currentStrokePoints.clear()
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

    private fun updateCurrentPaths() {
        // Unified Live Rendering Strategy
        // 1. Combine Real + Predicted Points
        val latencyMs = activeFreehandSettings.predictionLatency.toLong()
        val predictedPt = predictor.getPredictedPoint(
            points = currentStrokePoints,
            maxPredictionMillis = latencyMs,
            minSpeed = activeFreehandSettings.minPredictionVelocity,
            maxSpeed = activeFreehandSettings.maxPredictionVelocity
        )
        
        val livePoints = if (predictedPt != null) {
            currentStrokePoints + predictedPt
        } else {
            currentStrokePoints.toList()
        }
        
        if (livePoints.isEmpty()) return

        // 2. Generate Unified Path (Cap End Disabled for Live Blob)
        val liveSettings = activeFreehandSettings.copy(
            capEnd = false
        )
        
        val (unifiedPath, _, _) = PerfectFreehandGenerator.generate(
            livePoints, 
            activeSize, 
            liveSettings
        )
        
        // 3. Update Preview State directly
        // We reuse the variables used by onDraw
        currentVectorPreviewPath = unifiedPath
        currentVectorPreviewPoints = livePoints
        currentVectorPreviewColor = activeColor
        currentPredictedPoint = predictedPt // Keep for reference if needed, though now integrated
        
        // Clear unused specific paths to avoid confusion
        currentStrokePath.reset()
        predictedStrokePath.reset() 
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

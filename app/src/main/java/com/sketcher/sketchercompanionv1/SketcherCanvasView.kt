package com.sketcher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.View
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.os.Handler
import android.os.Looper
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.UnitUtils
import kotlin.math.round
import androidx.core.view.drawToBitmap

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Layer and FillData are now defined in Layer.kt and LayerElement.kt respectively

class SketcherCanvasView(context: Context) : View(context) {

    init {
        isClickable = true
        isFocusable = true
    }

    // --- COROUTINES ---
    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var redrawJob: Job? = null

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope.cancel()
    }

    // --- TRANSFORMS & STATE ---
    private val viewMatrix = Matrix()
    private val inverseMatrix = Matrix()
    private val cachedBitmapMatrix = Matrix()
    private val drawTransformMatrix = Matrix() // Persistent matrix for onDraw scaling
    private val matrixValuesBuffer = FloatArray(9) // Reuse for equality check
    private var isDrawing: Boolean = false

    private val redrawHandler = Handler(Looper.getMainLooper())
    private val delayedRedrawRunnable = Runnable { redrawAllCache() }
    
    // Viewport indicators for live canvas projection
    var projectionViewports: List<SketcherViewModel.ProjectionViewport> = emptyList()
        set(value) {
            field = value
            invalidate()
        }

    var isCapturingForProjection: Boolean = false

    fun drawToBitmapForProjection(): android.graphics.Bitmap {
        isCapturingForProjection = true
        try {
            return this.drawToBitmap()
        } finally {
            isCapturingForProjection = false
        }
    }

    fun getLiveStrokePoints(): List<StrokePoint>? = currentVectorPreviewPoints?.toList()
    fun getLiveStrokePath(): android.graphics.Path? = currentVectorPreviewPath
    fun getLiveFillPath(): android.graphics.Path? = currentFillPath
    fun getLiveGeneratedRadius(): Float = currentLiveGeneratedRadius
    
    // Pan tracking state
    private var lastPanX: Float = 0f
    private var lastPanY: Float = 0f

    // Transient Strokes Cache
    private val transientStrokes = mutableListOf<VectorStroke>()
    private val transientFills = mutableListOf<FillData>()
    
    // Temp storage for coordinate mapping
    private val tempTouchPoint = FloatArray(2)

    // --- GESTURES ---
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val currentScale = getMatrixScale(viewMatrix)
            val projectedZoom = currentScale * detector.scaleFactor
            // CLAMP ZOOM: 0.2f to 12.0f (Matched Legacy)
            val clampedZoom = projectedZoom.coerceIn(0.2f, 12.0f)
            val effectiveFactor = clampedZoom / currentScale
            
            viewMatrix.postScale(effectiveFactor, effectiveFactor, detector.focusX, detector.focusY)
            setCameraMatrix(viewMatrix, isIntermediate = true)
            onCameraMatrixChanged?.invoke(viewMatrix)
            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            redrawAllCache()
        }
    })



    // --- GESTURE DETECTOR (Legacy Pan Logic) ---
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean {
            return true // Essential for detecting scroll/pan
        }

        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
            // Only pan if 2+ fingers are down (Legacy behavior)
            if (e2.pointerCount >= 2) {
                viewMatrix.postTranslate(-dX, -dY)
                setCameraMatrix(viewMatrix, isIntermediate = true)
                onCameraMatrixChanged?.invoke(viewMatrix)
                return true
            }
            return false
        }
    })

    var onCameraMatrixChanged: ((Matrix) -> Unit)? = null

    fun getMatrixValues(outValues: FloatArray) {
        viewMatrix.getValues(outValues)
    }

    private fun getMatrixScale(matrix: Matrix): Float {
        matrix.getValues(matrixValuesBuffer)
        val sX = matrixValuesBuffer[Matrix.MSCALE_X]
        val skX = matrixValuesBuffer[Matrix.MSKEW_X]
        return kotlin.math.sqrt(sX * sX + skX * skX)
    }

    /**
     * Checks if the provided matrix is effectively equal to current view matrix.
     * Prevents feedback loops in Compose AndroidView updates.
     */
    fun isCameraEqual(other: Matrix): Boolean {
        val currentValues = FloatArray(9)
        val otherValues = FloatArray(9)
        viewMatrix.getValues(currentValues)
        other.getValues(otherValues)
        for (i in 0 until 9) {
            if (kotlin.math.abs(currentValues[i] - otherValues[i]) > 0.0001f) return false
        }
        return true
    }
    
    // SELECTION STATE (Wired from SketcherSurface/ViewModel)
    var selectionManager: SelectionManager? = null
    var isSelectionDragging: Boolean = false
        set(value) {
            field = value
            redrawAllCache() 
        }
    
    // CACHING
    private var backingPicture: android.graphics.Picture? = null

    var onSizeChangedCallback: ((Int, Int) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            redrawAllCache()
            onSizeChangedCallback?.invoke(w, h)
        }
    }

    // --- RENDER ENGINE & PIPELINE ---
    private val renderEngine = RenderEngine()
    private val strokePipeline = StrokePipeline(
        onUpdate = { update ->
            currentVectorPreviewPath = update.previewPath
            currentVectorPreviewPoints = update.previewPoints
            currentVectorPreviewCenterPoints = update.centerPoints
            currentVectorPreviewOutlinePoints = update.outlinePoints
            currentLiveGeneratedRadius = update.lastRadius
            currentVectorPreviewColor = activeStrokeColor // Sync Stroke Color
            
            // Sync Fill State
            currentFillPath = update.fillPath
            currentFillColor = if (isFillActive) activeFillColor else null
            
            isDrawing = (update.previewPath != null || update.previewPoints != null)
            invalidate()
        },
        onStrokeCompleted = { stroke, fill ->
            onHybridStrokeCompleted?.invoke(stroke, fill) ?: run {
                 onStrokeCompleted?.invoke(stroke)
                 fill?.let { onFillCompleted?.invoke(it) }
            }
            transientStrokes.add(stroke)
            if (fill != null) transientFills.add(fill)
            redrawAllCache()
            isDrawing = false
        }
    )

    // --- CACHE MANAGEMENT ---
    fun redrawAllCache() {
        if (width <= 0 || height <= 0) return
        
        redrawJob?.cancel()
        
        // Snapshot state for background rendering
        val currentMatrix = Matrix(viewMatrix)
        val layersSnapshot = layers.toList()
        val currentWidth = width
        val currentHeight = height
        val currentSelectionManager = selectionManager
        val isTransformModeActive = currentTool == ToolType.SELECTION && currentSelectionMode == SketcherViewModel.SelectionMode.TRANSFORM_BOX
        val librarySnapshot = componentLibrary
        val strokesBaking = transientStrokes.toList()
        val fillsBaking = transientFills.toList()
        
        // Use Coroutines to draw offscreen and swap
        redrawJob = viewScope.launch {
            val offscreenPicture = withContext(Dispatchers.Default) {
                val picture = android.graphics.Picture()
                val canvas = picture.beginRecording(currentWidth, currentHeight)
                
                renderEngine.drawLayers(
                    canvas, 
                    layersSnapshot, 
                    currentMatrix, 
                    librarySnapshot, 
                    currentSelectionManager, 
                    isTransformModeActive
                )
                
                picture.endRecording()
                picture
            }
            
            // Swap to new picture on Main Thread
            backingPicture = offscreenPicture
            cachedBitmapMatrix.set(currentMatrix)
            transientStrokes.removeAll(strokesBaking)
            transientFills.removeAll(fillsBaking)
            invalidate()
        }
    }

    fun bakeStroke(stroke: VectorStroke) { redrawAllCache() }
    fun bakeFill(fill: FillData) { redrawAllCache() }
    fun bakeVectorStroke(vStroke: VectorStroke) { redrawAllCache() }

    // --- CONFIGURATION SYNC ---
    var gridConfig: GridConfig = GridConfig()
        set(value) { field = value; renderEngine.gridConfig = value; redrawAllCache(); updateSnapFunction() }
    var scaleConfig: ScaleConfig = ScaleConfig()
        set(value) { field = value; renderEngine.scaleConfig = value; redrawAllCache(); updateSnapFunction() }
    var currentUnit: DistanceUnit = DistanceUnit.M
        set(value) { field = value; renderEngine.currentUnit = value; redrawAllCache(); updateSnapFunction() }
    var canvasSizeConfig: CanvasSizeConfig? = null
        set(value) { field = value; renderEngine.canvasSizeConfig = value; redrawAllCache(); updateSnapFunction() }
    var canvasBackgroundColor: Int = android.graphics.Color.WHITE
        set(value) { field = value; renderEngine.canvasBackgroundColor = value; redrawAllCache() }
        
    // --- SNAP LOGIC ---
    var isSnapToGridEnabled: Boolean = false
        set(value) { 
            field = value
            updateSnapFunction()
        }
        
    private fun updateSnapFunction() {
        snapFunction = if (isSnapToGridEnabled) {
            { screenX: Float, screenY: Float ->
                // 1. Screen -> World
                val pts = floatArrayOf(screenX, screenY)
                inverseMatrix.mapPoints(pts)
                val worldX = pts[0]
                val worldY = pts[1]

                // 2. Snap in World Space
                val gridStepPx = UnitUtils.projectUnitsToPixels(
                    value = gridConfig.spacing,
                    unit = currentUnit,
                    basePxPerMm = scaleConfig.basePixelsPerMillimeter
                )
                
                // Calculate offset to align grid center with canvas center
                val offsetX = canvasSizeConfig?.let { it.widthInPixels / 2f } ?: 0f
                val offsetY = canvasSizeConfig?.let { it.heightInPixels / 2f } ?: 0f

                val snappedWorldX: Float
                val snappedWorldY: Float

                if (gridStepPx > 0) {
                    snappedWorldX = offsetX + round((worldX - offsetX) / gridStepPx) * gridStepPx
                    snappedWorldY = offsetY + round((worldY - offsetY) / gridStepPx) * gridStepPx
                } else {
                    snappedWorldX = worldX
                    snappedWorldY = worldY
                }
                
                // 3. World -> Screen
                pts[0] = snappedWorldX
                pts[1] = snappedWorldY
                viewMatrix.mapPoints(pts)
                
                Pair(pts[0], pts[1])
            }
        } else {
            null
        }
    }
    
    var isDebugPredictionEnabled: Boolean = false
        set(value) { field = value; invalidate() }
    var isDebugWireframe: Boolean = false
        set(value) { field = value; renderEngine.isDebugWireframe = value; redrawAllCache() }
    var isDebugWireframeByVM: Boolean = false
        set(value) { field = value; redrawAllCache() }

    // --- LAYER DATA ---
    private val layers = mutableListOf<Layer>()
    private var componentLibrary: Map<String, ComponentDefinition> = emptyMap()
    private var editingContext: List<LayerElement>? = null
    private var activeLayerIndex: Int = 0

    fun setLayers(newLayers: List<Layer>, library: Map<String, ComponentDefinition>, editingCtx: List<LayerElement>?, activeIndex: Int = 0) {
        // Optimization: Skip if no changes to content
        if (layers.size == newLayers.size && 
            layers == newLayers && 
            componentLibrary === library && 
            editingContext === editingCtx && 
            activeLayerIndex == activeIndex) {
            return
        }

        layers.clear()
        layers.addAll(newLayers)
        componentLibrary = library
        editingContext = editingCtx
        activeLayerIndex = activeIndex
        redrawAllCache()
    }
    
    // --- TOOL SYNC ---
    var activeStrokeType: StrokeType = StrokeType.FREEHAND
        set(value) { field = value; strokePipeline.activeStrokeType = value }
    var activeStrokeColor: Int = android.graphics.Color.BLACK
        set(value) { field = value; strokePipeline.activeStrokeColor = value }
    var activeFillColor: Int = android.graphics.Color.TRANSPARENT
        set(value) { field = value; strokePipeline.activeFillColor = value }
    var isStrokeActive: Boolean = true
        set(value) { field = value; strokePipeline.isStrokeActive = value }
    var isFillActive: Boolean = false
        set(value) { field = value; strokePipeline.isFillActive = value }
    
    var activeColor: Int = android.graphics.Color.BLACK
        set(value) { field = value; activeStrokeColor = value }
    var activeSize: Float = 10f
        set(value) { field = value; strokePipeline.activeSize = value }
    var activeFreehandSettings: FreehandSettings = FreehandSettings()
        set(value) { field = value; strokePipeline.activeFreehandSettings = value }
    var isFillModeEnabled: Boolean = false
        set(value) { field = value; isFillActive = value }
    var fillModeColor: Int = android.graphics.Color.TRANSPARENT
        set(value) { field = value; activeFillColor = value }
    var isFingerMode: Boolean = false
        set(value) { field = value; strokePipeline.isFingerMode = value }
    var fingerOffsetX: Float = 0f
        set(value) { field = value; strokePipeline.fingerOffsetX = value }
    var fingerOffsetY: Float = 50f
        set(value) { field = value; strokePipeline.fingerOffsetY = value }
    var globalStabilizationLevel: Float = 0f
        set(value) { field = value; strokePipeline.globalStabilizationLevel = value }
    var isPalmRejectionEnabled: Boolean = false
    var snapFunction: ((Float, Float) -> Pair<Float, Float>)? = null
        set(value) { field = value; strokePipeline.snapFunction = value }
    var currentTool: ToolType = ToolType.FREEHAND
    var currentSelectionMode: SketcherViewModel.SelectionMode = SketcherViewModel.SelectionMode.RECTANGLE
    // selectionManager declaration removed to avoid conflict
    
    // --- PREVIEW STATE ---
    private var currentVectorPreviewPath: android.graphics.Path? = null
    private var currentVectorPreviewPoints: List<StrokePoint>? = null 
    private var currentVectorPreviewCenterPoints: List<PointF>? = null
    private var currentVectorPreviewOutlinePoints: List<PointF>? = null
    private var currentVectorPreviewColor: Int = 0 
    private var currentFillPath: android.graphics.Path? = null
    private var currentFillColor: Int? = null
    private var currentLiveGeneratedRadius: Float = 0f
    private var currentLiveTipWidth: Float = 0f 
        get() = currentLiveGeneratedRadius * 2
 // New: Accurate radius from generator
    


    // updateCurrentVectorPreview removed - managed by StrokePipeline onUpdate


    fun updateCurrentFill(path: android.graphics.Path?, color: Int) {
        currentFillPath = path
        currentFillColor = color
        invalidate()
    }

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
                        bounds.contains(worldX, worldY) // Simplistic check, RenderEngine could do better region checks
                    }
                    is VectorStroke -> {
                        val bounds = android.graphics.RectF()
                        element.path.computeBounds(bounds, true)
                        bounds.contains(worldX, worldY)
                    }
                    is ImageElement -> element.getBoundingBox(componentLibrary).contains(worldX, worldY)
                    is SvgElement -> element.getBoundingBox(componentLibrary).contains(worldX, worldY)
                    is ComponentInstance -> element.getBoundingBox(componentLibrary).contains(worldX, worldY)
                    else -> false
                }
                if (removed) {
                    iterator.remove()
                    redrawAllCache()
                    return element
                }
            }
        }
        return null
    }

    fun setCameraMatrix(matrix: Matrix, isIntermediate: Boolean = false) {
        viewMatrix.set(matrix)
        viewMatrix.invert(inverseMatrix)
        
        if (isIntermediate) {
            // Immediate feedback via bitmap scaling in onDraw
            invalidate()
            
            // Defer expensive redraw to avoid UI lag during rapid updates (zoom/pan)
            redrawHandler.removeCallbacks(delayedRedrawRunnable)
            redrawHandler.postDelayed(delayedRedrawRunnable, 100)
        } else {
            // Final update should be crisp immediately
            redrawHandler.removeCallbacks(delayedRedrawRunnable)
            redrawAllCache()
        }
    }

    fun refreshView() { redrawAllCache() }
    fun clearCanvas() {
        layers.forEach { it.elements.clear() }
        redrawAllCache()
    }

    // --- DRAWING ---

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        // 1. Draw Cached Picture (Background & Layers)
        backingPicture?.let { picture ->
            if (viewMatrix == cachedBitmapMatrix) {
                canvas.drawPicture(picture)
            } else {
                // High-performance scaling for intermediate frames
                if (cachedBitmapMatrix.invert(drawTransformMatrix)) {
                     drawTransformMatrix.postConcat(viewMatrix)
                     canvas.save()
                     canvas.concat(drawTransformMatrix)
                     canvas.drawPicture(picture)
                     canvas.restore()
                } else {
                     canvas.drawPicture(picture)
                }
            }
        } ?: run {
             canvas.drawColor(canvasBackgroundColor)
        }

        // Draw Transient Strokes manually while background rendering finishes
        canvas.save()
        canvas.concat(viewMatrix)
        for (fill in transientFills) {
            renderEngine.drawElementRecursive(canvas, fill, componentLibrary)
        }
        for (stroke in transientStrokes) {
            renderEngine.drawElementRecursive(canvas, stroke, componentLibrary)
        }
        canvas.restore()

        // 2. Draw Live Content (Stroke & Fill)
        renderEngine.drawLiveStroke(
            canvas, 
            currentVectorPreviewPoints, 
            currentVectorPreviewPath,
            activeStrokeColor,
            fillPath = currentFillPath,
            fillColor = activeFillColor,
            isFillActive = isFillActive,
            isStrokeActive = isStrokeActive,
            currentLiveGeneratedRadius = currentLiveGeneratedRadius,
            viewMatrix = viewMatrix,
            isDrawing = isDrawing
        )

        // Selection path preview drawing
        if (currentTool == ToolType.SELECTION && (currentSelectionMode == SketcherViewModel.SelectionMode.FREEHAND || currentSelectionMode == SketcherViewModel.SelectionMode.RECTANGLE || currentSelectionMode == SketcherViewModel.SelectionMode.POLYGON)) {
            selectionManager?.let { manager ->
                val path = manager.lassoPath
                if (!path.isEmpty) {
                    val paint = Paint().apply {
                        style = Paint.Style.STROKE
                        strokeWidth = 2f * resources.displayMetrics.density
                        color = Color.parseColor("#FF007AFF")
                        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                        isAntiAlias = true
                    }
                    canvas.save()
                    canvas.concat(viewMatrix)
                    canvas.drawPath(path, paint)
                    canvas.restore()
                }
            }
        }

        // 3. Selection Overlays
        val isTransformBox = currentTool == ToolType.SELECTION && currentSelectionMode == SketcherViewModel.SelectionMode.TRANSFORM_BOX
        if (isTransformBox) {
             selectionManager?.let { manager ->
                  canvas.save()
                  canvas.concat(viewMatrix)
                  canvas.concat(manager.selectionMatrix)
                  for (element in manager.selectedElements) {
                      renderEngine.drawElementRecursive(canvas, element, componentLibrary)
                  }
                  canvas.restore()
             }
        }

        // 4. Selection Box/Handles
        selectionManager?.let { manager ->
            if (manager.selectedElements.isNotEmpty()) {
                renderEngine.drawSelectionOverlay(canvas, manager, viewMatrix)
            }
        }

        // 5. Draw Projection Viewports (Indicator overlay)
        if (!isCapturingForProjection && projectionViewports.isNotEmpty()) {
            val paint = Paint().apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f * resources.displayMetrics.density
                pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
                isAntiAlias = true
            }
            val textPaint = Paint().apply {
                textSize = 12f * resources.displayMetrics.density
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val textBgPaint = Paint().apply {
                style = Paint.Style.FILL
                isAntiAlias = true
            }

            for (viewport in projectionViewports) {
                paint.color = viewport.color
                textPaint.color = Color.WHITE
                textBgPaint.color = viewport.color

                // Draw viewport rect in screen/view space (so no viewMatrix transformation)
                val rect = RectF(viewport.left, viewport.top, viewport.right, viewport.bottom)
                canvas.drawRect(rect, paint)

                // Draw label at the top-left of the rectangle
                val text = viewport.label
                val textWidth = textPaint.measureText(text)
                val textHeight = textPaint.fontMetrics.descent - textPaint.fontMetrics.ascent
                
                // Draw a small background pill for the label
                val labelRect = RectF(
                    rect.left,
                    rect.top,
                    rect.left + textWidth + 8f * resources.displayMetrics.density,
                    rect.top + textHeight + 4f * resources.displayMetrics.density
                )
                canvas.drawRect(labelRect, textBgPaint)
                canvas.drawText(
                    text,
                    rect.left + 4f * resources.displayMetrics.density,
                    rect.top - textPaint.fontMetrics.ascent + 2f * resources.displayMetrics.density,
                    textPaint
                )
            }
        }
        
        onDrawAction?.invoke()
        onDrawAction = null
    }

    var onDrawAction: (() -> Unit)? = null

    // --- INPUT GESTURES ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // --- 1. GESTURES (Zoom/Pan) ---
        // Delegate to Scale and Gesture Detectors
        val wasInProgress = scaleDetector.isInProgress
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        
        // Manual Pan Logic Removed (Replaced by GestureDetector)

        // If we are zooming or have 2+ fingers, don't draw
        if (scaleDetector.isInProgress || event.pointerCount >= 2 || (wasInProgress && (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP))) {
            if (event.actionMasked == MotionEvent.ACTION_UP || (event.pointerCount == 2 && event.actionMasked == MotionEvent.ACTION_POINTER_UP)) {
                redrawAllCache()
            }
            return true
        }

        // Basic Palm Rejection: If enabled and we have a stylus, ignore non-stylus events
        if (currentTool != ToolType.SELECTION && isPalmRejectionEnabled && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
            return true // Consume event to keep stream alive for gestures, but don't draw
        }

        strokePipeline.canvasViewMatrix.set(viewMatrix)
        
        // Calculate current zoom factor
        val zoom = getMatrixScale(viewMatrix)
        strokePipeline.currentZoom = zoom
        
        return when (currentTool) {
            ToolType.SELECTION -> handleSelectionInput(event)
            ToolType.ERASER -> handleEraserInput(event)
            else -> strokePipeline.onTouchEvent(event)
        }
    }

    fun finishGeometricStroke() {
        strokePipeline.forceFinishGeometric()
    }

    // --- CALLBACKS ---
    var onStrokeCompleted: ((VectorStroke) -> Unit)? = null
    var onFillCompleted: ((FillData) -> Unit)? = null
    var onGeometricProgressChanged: ((Boolean) -> Unit)? = null
    var onHybridStrokeCompleted: ((VectorStroke, FillData?) -> Unit)? = null

    private fun handleEraserInput(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                // Map screen coordinates to world coordinates
                tempTouchPoint[0] = event.x
                tempTouchPoint[1] = event.y
                inverseMatrix.mapPoints(tempTouchPoint)
                
                val worldX = tempTouchPoint[0]
                val worldY = tempTouchPoint[1]
                
                // Erase (Object Eraser)
                eraseContentAt(worldX, worldY)
            }
        }
        return true
    }

    private fun handleSelectionInput(event: MotionEvent): Boolean {
        // Map screen coordinates to world coordinates
        tempTouchPoint[0] = event.x
        tempTouchPoint[1] = event.y
        inverseMatrix.mapPoints(tempTouchPoint)
        
        val worldX = tempTouchPoint[0]
        val worldY = tempTouchPoint[1]

        val manager = selectionManager
        if (manager != null) {
            when (currentSelectionMode) {
                SketcherViewModel.SelectionMode.RECTANGLE,
                SketcherViewModel.SelectionMode.POLYGON,
                SketcherViewModel.SelectionMode.FREEHAND -> {
                    // Update selection mode in selectionManager so it knows how to build the path
                    manager.isRectangleMode = currentSelectionMode == SketcherViewModel.SelectionMode.RECTANGLE
                    
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> manager.startSelection(worldX, worldY)
                        MotionEvent.ACTION_MOVE -> manager.updateSelection(worldX, worldY)
                        MotionEvent.ACTION_UP -> {
                            if (layers.isNotEmpty() && activeLayerIndex in layers.indices) {
                                manager.finalizeSelection(layers[activeLayerIndex], componentLibrary)
                            }
                        }
                    }
                }
                SketcherViewModel.SelectionMode.TRANSFORM_BOX -> {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> manager.handleTransformDown(worldX, worldY, event.x, event.y, viewMatrix)
                        MotionEvent.ACTION_MOVE -> manager.handleTransformMove(worldX, worldY)
                        MotionEvent.ACTION_UP -> manager.handleTransformUp()
                    }
                }
            }
            // Sync selection manager state with canvas dragging state
            isSelectionDragging = manager.activeTransform != null
            invalidate()
            return true
        }
        return false
    }

}


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

import com.sketcher.sketchercompanionv1.managers.SnapEngine

import com.sketcher.sketchercompanionv1.managers.SnapPoint

import com.sketcher.sketchercompanionv1.managers.SnapType

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

        // Ensure all drawing is hardware accelerated for minimum latency

        setLayerType(LAYER_TYPE_NONE, null)

    }



    // --- COROUTINES ---

    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var redrawJob: Job? = null



    override fun onDetachedFromWindow() {

        super.onDetachedFromWindow()

        viewScope.cancel()
        strokePipeline.cancel() // OPTIMIZATION C-8

        bitmapBuffer1?.recycle()

        bitmapBuffer2?.recycle()

        bitmapBuffer1 = null

        bitmapBuffer2 = null

        backingBitmap = null

    }



    // --- TRANSFORMS & STATE ---

    private val viewMatrix = Matrix()

    private val inverseMatrix = Matrix()

    private val cachedBitmapMatrix = Matrix()

    private val drawTransformMatrix = Matrix() // Persistent matrix for onDraw scaling

    private val matrixValuesBuffer = FloatArray(9) // Reuse for equality check

    private val cameraEqualCurrentBuffer = FloatArray(9)

    private val cameraEqualOtherBuffer = FloatArray(9)

    private var isDrawing: Boolean = false

    

    // Pre-allocated objects for onDraw to avoid GC churn

    private val dashPathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)

    private val projectionViewportPaint = Paint().apply {

        style = Paint.Style.STROKE

        pathEffect = dashPathEffect

        isAntiAlias = true

    }

    private val projectionTextPaint = Paint().apply {

        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)

        isAntiAlias = true

    }

    private val projectionTextBgPaint = Paint().apply {

        style = Paint.Style.FILL

        isAntiAlias = true

    }

    private val selectionLassoPaint = Paint().apply {

        style = Paint.Style.STROKE

        color = Color.parseColor("#FF007AFF")

        pathEffect = dashPathEffect

        isAntiAlias = true

    }

    private val hoverPreviewPaint = Paint().apply {

        style = Paint.Style.STROKE

        pathEffect = dashPathEffect

        isAntiAlias = true

    }

    private val eraserPreviewPaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#80FF3B30") // Semi-transparent system red
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val eraserPreviewFillPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#1AFF3B30") // Very light red fill (10% opacity)
        isAntiAlias = true
    }

    private var isEraserActive = false
    private var eraserWorldX = 0f
    private var eraserWorldY = 0f
    private var eraserRadiusWorld = 0f

    private val cadGuidePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        pathEffect = dashPathEffect
        isAntiAlias = true
    }

    private val cadMarkerPaint = Paint().apply {
        color = Color.CYAN
        style = Paint.Style.FILL_AND_STROKE
        isAntiAlias = true
    }

    private val tempViewportRect = RectF()

    private val tempLabelRect = RectF()



    // Performance Stats Timing & States

    private var lastFrameTimeNs: Long = 0L

    private val frameTimesNs = LongArray(10)

    private var frameTimeIndex = 0

    val fpsState = androidx.compose.runtime.mutableStateOf(0)

    val lastRedrawTimeMs = androidx.compose.runtime.mutableStateOf(0L)



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

    fun getLiveCommittedPath(): android.graphics.Path? = currentCommittedPreviewPath?.let { android.graphics.Path(it) }

    fun getLiveFillPath(): android.graphics.Path? = currentFillPath

    fun getLiveGeneratedRadius(): Float = currentLiveGeneratedRadius

    

    // Pan tracking state

    private var lastPanX: Float = 0f

    private var lastPanY: Float = 0f



    // Transient Strokes Cache

    private val transientStrokes = mutableListOf<VectorStroke>()

    private val transientFills = mutableListOf<FillData>()

    

    // Callbacks para CAD y Grip Editing

    var onGripEditCompleted: ((VectorStroke, List<StrokePoint>, List<StrokePoint>) -> Unit)? = null

    var onTrimRequested: ((Float, Float) -> Unit)? = null

    var onExtendRequested: ((Float, Float) -> Unit)? = null

    var onMirrorRequested: ((PointF, PointF) -> Unit)? = null

    var onTransformSelectedRequested: ((android.graphics.Matrix, String) -> Unit)? = null

    var onOffsetRequested: ((VectorStroke, Float, PointF) -> Unit)? = null

    var onFilletRequested: ((VectorStroke, VectorStroke, Float) -> Unit)? = null

    var onChamferRequested: ((VectorStroke, VectorStroke, Float, Float) -> Unit)? = null

    var isOrthoMode: Boolean = false

    private var startSelectionDragWorldX = 0f
    private var startSelectionDragWorldY = 0f

    private var mirrorStep = 0
    private var mirrorP1 = PointF()
    private var mirrorP2 = PointF()

    private var movePtPtStep = 0
    private var movePtPtSrc = PointF()
    private var movePtPtTgt = PointF()

    private var alignStep = 0
    private var alignSrc1 = PointF()
    private var alignTgt1 = PointF()
    private var alignSrc2 = PointF()
    private var alignTgt2 = PointF()

    private var offsetStep = 0
    private var offsetTargetStroke: VectorStroke? = null

    private var filletStep = 0
    private var filletStroke1: VectorStroke? = null

    private var chamferStep = 0
    private var chamferStroke1: VectorStroke? = null



    // Grip Editing Drag States

    private var activeDraggedStroke: VectorStroke? = null

    private var activeDraggedPointIndex: Int = -1

    private var originalStrokePoints: List<StrokePoint>? = null

    private var isDraggingGrip = false

    

    

    // Temp storage for coordinate mapping

    private val tempTouchPoint = FloatArray(2)



    private val screenPxPerMm: Float by lazy {

        UnitUtils.getScreenPxPerMm(context)

    }



    private fun getZoomScale100(): Float {

        val basePx = scaleConfig.basePixelsPerMillimeter

        val safeBasePx = if (basePx == 0f) 5f else basePx

        return screenPxPerMm / safeBasePx

    }



    // --- GESTURES ---

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

        override fun onScale(detector: ScaleGestureDetector): Boolean {

            val currentScale = getMatrixScale(viewMatrix)

            val zoomScale100 = getZoomScale100()

            val currentNormalizedZoom = currentScale / zoomScale100

            val projectedNormalizedZoom = currentNormalizedZoom * detector.scaleFactor

            

            // CLAMP ZOOM: 0.2f to 12.0f normalized zoom

            var clampedNormalizedZoom = projectedNormalizedZoom.coerceIn(0.2f, 12.0f)

            

            // Snap to 100% (1.0 normalized) if within threshold of 8%

            val snapThreshold = 0.08f

            if (kotlin.math.abs(clampedNormalizedZoom - 1.0f) < snapThreshold) {

                clampedNormalizedZoom = 1.0f

            }

            

            val clampedZoom = clampedNormalizedZoom * zoomScale100

            val effectiveFactor = clampedZoom / currentScale

            

            viewMatrix.postScale(effectiveFactor, effectiveFactor, detector.focusX, detector.focusY)

            clampMatrixToBounds(viewMatrix)

            setCameraMatrix(viewMatrix, isIntermediate = true)

            onCameraMatrixChanged?.invoke(viewMatrix)

            return true

        }



        override fun onScaleEnd(detector: ScaleGestureDetector) {

            redrawAllCache()

        }

    }).apply {
        isQuickScaleEnabled = false
        isStylusScaleEnabled = false
    }









    // --- GESTURE DETECTOR (Legacy Pan Logic) ---

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {

        override fun onDown(e: MotionEvent): Boolean {

            return true // Essential for detecting scroll/pan

        }



        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {

            // Only pan if 2+ fingers are down (Legacy behavior)

            if (e2.pointerCount >= 2) {

                viewMatrix.postTranslate(-dX, -dY)

                clampMatrixToBounds(viewMatrix)

                setCameraMatrix(viewMatrix, isIntermediate = true)

                onCameraMatrixChanged?.invoke(viewMatrix)

                return true

            }

            return false

        }



        override fun onDoubleTap(e: MotionEvent): Boolean {

            if (strokePipeline.isMultiStepInProgress) {

                finishGeometricStroke()

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



    private fun clampMatrixToBounds(matrix: Matrix) {

        val config = canvasSizeConfig ?: return

        matrix.getValues(matrixValuesBuffer)

        val scale = getMatrixScale(matrix)

        val currentTx = matrixValuesBuffer[Matrix.MTRANS_X]

        val currentTy = matrixValuesBuffer[Matrix.MTRANS_Y]



        val halfW = config.widthInPixels / 2f

        val halfH = config.heightInPixels / 2f



        val left = if (config.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) -halfW else 0f

        val top = if (config.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) -halfH else 0f

        val right = left + config.widthInPixels

        val bottom = top + config.heightInPixels



        // We want to limit panning so the canvas doesn't completely leave the screen.

        // Let's say the view dimensions are width and height.

        // The bounds of the paper in screen coordinates are:

        val screenLeft = left * scale + currentTx

        val screenRight = right * scale + currentTx

        val screenTop = top * scale + currentTy

        val screenBottom = bottom * scale + currentTy



        val paddingX = width / 2f

        val paddingY = height / 2f



        var dx = 0f

        var dy = 0f



        if (screenRight < paddingX) dx = paddingX - screenRight

        else if (screenLeft > width - paddingX) dx = (width - paddingX) - screenLeft



        if (screenBottom < paddingY) dy = paddingY - screenBottom

        else if (screenTop > height - paddingY) dy = (height - paddingY) - screenTop



        if (dx != 0f || dy != 0f) {

            matrix.postTranslate(dx, dy)

        }

    }



    /**

     * Checks if the provided matrix is effectively equal to current view matrix.

     * Prevents feedback loops in Compose AndroidView updates.

     */

    fun isCameraEqual(other: Matrix): Boolean {

        viewMatrix.getValues(cameraEqualCurrentBuffer)

        other.getValues(cameraEqualOtherBuffer)

        for (i in 0 until 9) {

            if (kotlin.math.abs(cameraEqualCurrentBuffer[i] - cameraEqualOtherBuffer[i]) > 0.0001f) return false

        }

        return true

    }

    

    // SELECTION STATE (Wired from SketcherSurface/ViewModel)

    var selectionManager: SelectionManager? = null

    var isSelectionDragging: Boolean = false

        set(value) {

            field = value

            invalidate() // OPTIMIZATION C-2 

        }

    

    // CACHING

    private var bitmapBuffer1: android.graphics.Bitmap? = null

    private var bitmapBuffer2: android.graphics.Bitmap? = null

    private var backingBitmap: android.graphics.Bitmap? = null



    private fun obtainBitmapBuffer(w: Int, h: Int, currentBacking: android.graphics.Bitmap?): android.graphics.Bitmap {

        val b1 = bitmapBuffer1

        if (b1 != null && b1.width == w && b1.height == h && b1 !== currentBacking) {

            b1.eraseColor(android.graphics.Color.TRANSPARENT)

            return b1

        }

        val b2 = bitmapBuffer2

        if (b2 != null && b2.width == w && b2.height == h && b2 !== currentBacking) {

            b2.eraseColor(android.graphics.Color.TRANSPARENT)

            return b2

        }



        val newBitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)

        if (currentBacking === bitmapBuffer1) {

            bitmapBuffer2?.recycle()

            bitmapBuffer2 = newBitmap

        } else {

            bitmapBuffer1?.recycle()

            bitmapBuffer1 = newBitmap

        }

        return newBitmap

    }

    private val bitmapPaint = Paint().apply {

        isFilterBitmap = true

        isAntiAlias = true

        isDither = true

    }



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

    private val strokePipeline: StrokePipeline = StrokePipeline(

        onUpdate = { update ->

            currentVectorPreviewPath = update.previewPath

            currentVectorPreviewPoints = update.previewPoints

            currentVectorPreviewCenterPoints = update.centerPoints

            currentVectorPreviewOutlinePoints = update.outlinePoints

            currentLiveGeneratedRadius = update.lastRadius

            currentVectorPreviewColor = activeStrokeColor // Sync Stroke Color

            currentCommittedPreviewPath = update.committedPreviewPath

            currentLiveIntersections = update.intersections

            currentStrokeBounds = update.bounds

            

            // Sync Fill State

            currentFillPath = update.fillPath

            currentFillColor = if (isFillActive) activeFillColor else null

            

            isDrawing = (update.previewPath != null || update.previewPoints != null)

            // Log removed (OPTIMIZATION C-1)

            

            if (!isDrawing) {

                liveMergedExistingStrokes.clear()

            }

            

            onGeometricProgressChanged?.invoke(update.isMultiStepInProgress)

            invalidate()

        },

        onStrokeCompleted = { stroke, fill ->
            currentCommittedPreviewPath = null
            currentLiveIntersections = emptyList()
            currentStrokeBounds = null

            val bakedFillStyle = stroke.fillStyle.copyWithOpacity(stroke.fillStyle.opacity * activeFillOpacity)
            val bakedFillColor = if (stroke.isFillEnabled) {
                val alpha = (android.graphics.Color.alpha(stroke.fillColor) / 255f)
                val finalAlpha = (alpha * activeFillOpacity * 255).toInt().coerceIn(0, 255)
                (stroke.fillColor and 0x00FFFFFF) or (finalAlpha shl 24)
            } else {
                stroke.fillColor
            }

            val bakedStroke = stroke.copy(
                fillStyle = bakedFillStyle,
                fillColor = bakedFillColor
            )

            val bakedFill = fill?.let { f ->
                val style = f.fillStyle.copyWithOpacity(f.fillStyle.opacity * activeFillOpacity)
                f.copy(fillStyle = style)
            }

            bakeStrokeDirectly(bakedStroke, bakedFill)

            onHybridStrokeCompleted?.invoke(bakedStroke, bakedFill) ?: run {
                 onStrokeCompleted?.invoke(bakedStroke)
                 bakedFill?.let { onFillCompleted?.invoke(it) }
            }

            isDrawing = false
            liveMergedExistingStrokes.clear()
        }

    )



    // --- CACHE MANAGEMENT ---

    fun redrawAllCache() {

        if (width <= 0 || height <= 0) return

        

        redrawJob?.cancel()

        

        // Snapshot state for background rendering

        val currentMatrix = Matrix(viewMatrix)

        val mergedStrokesSet = liveMergedExistingStrokes.toSet()

        val layersSnapshot = layers.map { layer ->

            val list = androidx.compose.runtime.snapshots.SnapshotStateList<LayerElement>()

            val filtered = if (mergedStrokesSet.isNotEmpty()) {

                layer.elements.filter { e ->

                    val stroke = e as? VectorStroke

                    stroke == null || stroke !in mergedStrokesSet

                }

            } else {

                layer.elements

            }

            list.addAll(filtered)

            layer.copy(elements = list)

        }

        val currentWidth = width

        val currentHeight = height

        val selectedElementsSnapshot = selectionManager?.selectedElements?.toSet() ?: emptySet()

        val isTransformModeActive = currentTool == ToolType.SELECTION && currentSelectionMode == SketcherViewModel.SelectionMode.TRANSFORM_BOX

        val librarySnapshot = componentLibrary

        val strokesBaking = transientStrokes.toList()

        val fillsBaking = transientFills.toList()

        val editingParentSnapshot = editingParent

        

        val startTime = System.currentTimeMillis()

        val backBuffer = obtainBitmapBuffer(currentWidth, currentHeight, backingBitmap)

        

        redrawJob = viewScope.launch {

            val job = coroutineContext[Job]

            val offscreenBitmap = withContext(Dispatchers.Default) {

                val canvas = android.graphics.Canvas(backBuffer)

                

                renderEngine.drawLayers(

                    canvas, 

                    layersSnapshot, 

                    currentMatrix, 

                    librarySnapshot, 

                    selectedElementsSnapshot, 

                    isTransformModeActive,

                    editingParent = editingParentSnapshot,

                    isCancelled = { job?.isCancelled == true }

                )

                backBuffer

            }

            

            lastRedrawTimeMs.value = System.currentTimeMillis() - startTime

            // Swap to new bitmap on Main Thread

            backingBitmap = offscreenBitmap

            cachedBitmapMatrix.set(currentMatrix)

            transientStrokes.removeAll(strokesBaking)
            updateLayerType()

            transientFills.removeAll(fillsBaking)

            invalidate()

        }

    }



    fun bakeStrokeDirectly(stroke: VectorStroke, fill: FillData?) {

        if (width <= 0 || height <= 0) return

        

        val bitmap = backingBitmap

        if (viewMatrix != cachedBitmapMatrix || bitmap == null) {

            fill?.let { transientFills.add(it) }

            transientStrokes.add(stroke)
            updateLayerType()

            redrawAllCache()

            return

        }

        

        redrawJob?.cancel()

        

        val librarySnapshot = componentLibrary

        

        val canvas = android.graphics.Canvas(bitmap)

        val activeLayerOpacity = layers.getOrNull(activeLayerIndex)?.opacity ?: 1f

        

        canvas.save()

        canvas.concat(viewMatrix)

        if (fill != null) {

            renderEngine.drawElementRecursive(canvas, fill, librarySnapshot, viewMatrix, activeLayerOpacity)

        }

        renderEngine.drawElementRecursive(canvas, stroke, librarySnapshot, viewMatrix, activeLayerOpacity)

        canvas.restore()

        

        lastBakedElement = stroke

        invalidate()

    }



    fun bakeStroke(stroke: VectorStroke) { redrawAllCache() }

    fun bakeFill(fill: FillData) { redrawAllCache() }

    fun bakeVectorStroke(vStroke: VectorStroke) { redrawAllCache() }



    // --- CONFIGURATION SYNC ---

    var gridConfig: GridConfig = GridConfig()

        set(value) {

            if (field == value) return

            field = value

            renderEngine.gridConfig = value

            redrawAllCache()

            updateSnapFunction()

        }

    var scaleConfig: ScaleConfig = ScaleConfig()

        set(value) {

            if (field == value) return

            field = value

            renderEngine.scaleConfig = value

            redrawAllCache()

            updateSnapFunction()

        }

    var currentUnit: DistanceUnit = DistanceUnit.M

        set(value) {

            if (field == value) return

            field = value

            renderEngine.currentUnit = value

            redrawAllCache()

            updateSnapFunction()

        }

    var canvasSizeConfig: CanvasSizeConfig? = null

        set(value) {

            if (field == value) return

            field = value

            renderEngine.canvasSizeConfig = value

            redrawAllCache()

            updateSnapFunction()

        }

    var canvasBackgroundStyle: FillStyle = FillStyle.Solid(android.graphics.Color.WHITE)
        set(value) {
            if (field == value) return
            field = value
            renderEngine.canvasBackgroundStyle = value
            if (value is FillStyle.Solid && canvasBackgroundColor != value.color) {
                canvasBackgroundColor = value.color
            }
            redrawAllCache()
        }

    var canvasBackgroundColor: Int = android.graphics.Color.WHITE
        set(value) {
            if (field == value) return
            field = value
            renderEngine.canvasBackgroundColor = value
            if (canvasBackgroundStyle !is FillStyle.Solid || (canvasBackgroundStyle as FillStyle.Solid).color != value) {
                canvasBackgroundStyle = FillStyle.Solid(value)
            }
            redrawAllCache()
        }

        

    // --- SNAP LOGIC ---

    var isSnapToGridEnabled: Boolean = false

        set(value) { 

            if (field == value) return

            field = value

            updateSnapFunction()

        }

        

    var isElementSnappingEnabled: Boolean = true

        set(value) {

            if (field == value) return

            field = value

            updateSnapFunction()

        }

        

    private var activeSnapPoints: List<SnapPoint> = emptyList()

    private var currentSnapPoint: SnapPoint? = null

    private var hoverWorldPoint: android.graphics.PointF? = null

        

    private fun updateSnapFunction() {

        snapFunction = if (isSnapToGridEnabled || isElementSnappingEnabled) {

            { screenX: Float, screenY: Float ->

                val pts = floatArrayOf(screenX, screenY)

                inverseMatrix.mapPoints(pts)

                val worldX = pts[0]

                val worldY = pts[1]



                var snappedWorldX = worldX

                var snappedWorldY = worldY

                var resolvedSnap: SnapPoint? = null



                if (isElementSnappingEnabled) {

                    val zoom = getMatrixScale(viewMatrix)

                    resolvedSnap = SnapEngine.resolveSnap(worldX, worldY, activeSnapPoints, zoom)

                    if (resolvedSnap != null) {

                        snappedWorldX = resolvedSnap.point.x

                        snappedWorldY = resolvedSnap.point.y

                    }

                }



                if (resolvedSnap == null && isSnapToGridEnabled) {

                    val gridStepPx = UnitUtils.projectUnitsToPixels(

                        value = gridConfig.spacing,

                        unit = currentUnit,

                        basePxPerMm = scaleConfig.basePixelsPerMillimeter

                    )

                    if (gridStepPx > 0) {

                        snappedWorldX = round(worldX / gridStepPx) * gridStepPx

                        snappedWorldY = round(worldY / gridStepPx) * gridStepPx

                    }

                }



                currentSnapPoint = resolvedSnap



                pts[0] = snappedWorldX

                pts[1] = snappedWorldY

                viewMatrix.mapPoints(pts)

                

                Pair(pts[0], pts[1])

            }

        } else {

            null

        }

    }



    var isSnapEndpointEnabled: Boolean = true

    var isSnapMidpointEnabled: Boolean = true

    var isSnapCenterEnabled: Boolean = true

    var isSnapIntersectionEnabled: Boolean = true



    private fun getCombinedSnapPoints(): List<SnapPoint> {

        val allPoints = SnapEngine.getSnapPoints(layers)

        

        val activeSettings = mutableSetOf<SnapType>()

        if (isSnapEndpointEnabled) activeSettings.add(SnapType.ENDPOINT)

        if (isSnapMidpointEnabled) activeSettings.add(SnapType.MIDPOINT)

        if (isSnapCenterEnabled) activeSettings.add(SnapType.CENTER)

        if (isSnapIntersectionEnabled) activeSettings.add(SnapType.INTERSECTION)

        

        val pts = allPoints.filter { it.type in activeSettings }.toMutableList()

        

        if (strokePipeline.isMultiStepInProgress) {

            val polyPoints = strokePipeline.currentStrokePointsList

            if (polyPoints.size >= 2 && isSnapEndpointEnabled) {

                for (i in 0 until polyPoints.size - 1) {

                    val p = polyPoints[i]

                    pts.add(SnapPoint(android.graphics.PointF(p.x, p.y), SnapType.ENDPOINT))

                }

            }

        }

        return pts

    }

    

    var isDebugPredictionEnabled: Boolean = false

        set(value) {

            if (field == value) return

            field = value

            invalidate()

        }

    var isDebugWireframe: Boolean = false

        set(value) {

            if (field == value) return

            field = value

            renderEngine.isDebugWireframe = value

            redrawAllCache()

        }

    var isDebugWireframeByVM: Boolean = false

        set(value) {

            if (field == value) return

            field = value

            redrawAllCache()

        }



    private var lastBakedElement: LayerElement? = null



    // --- LAYER DATA ---

    private val layers = mutableListOf<Layer>()

    private var componentLibrary: Map<String, ComponentDefinition> = emptyMap()

    private var editingContext: List<LayerElement>? = null

    private var editingParent: LayerElement? = null

    private var editingContainerMatrix: Matrix? = null

    private var activeLayerIndex: Int = 0



    private val activeContainer: List<LayerElement>

        get() = editingContext ?: layers.getOrNull(activeLayerIndex)?.elements ?: emptyList()



    private var lastUpdateTrigger: Int = -1



    fun setLayers(

        newLayers: List<Layer>, 

        library: Map<String, ComponentDefinition>, 

        editingCtx: List<LayerElement>?, 

        editingParentEl: LayerElement?,

        editingMatrix: Matrix?,

        updateTrigger: Int,

        activeIndex: Int = 0

    ) {

        val triggerChanged = lastUpdateTrigger != updateTrigger

        lastUpdateTrigger = updateTrigger



        // Optimization: Skip if no changes to content and no trigger change

        if (!triggerChanged &&

            layers.size == newLayers.size && 

            layers == newLayers && 

            componentLibrary === library && 

            editingContext === editingCtx && 

            editingParent === editingParentEl &&

            editingContainerMatrix === editingMatrix &&

            activeLayerIndex == activeIndex) {

            return

        }



        // Fast path for single stroke completion (incremental bake)

        if (isSingleElementAppend(newLayers)) {

            layers.clear()

            layers.addAll(newLayers)

            componentLibrary = library

            editingContext = editingCtx

            editingParent = editingParentEl

            editingContainerMatrix = editingMatrix

            activeLayerIndex = activeIndex

            lastBakedElement = null // Reset after skipping redraw

            return

        }



        layers.clear()

        layers.addAll(newLayers)

        componentLibrary = library

        editingContext = editingCtx

        editingParent = editingParentEl

        editingContainerMatrix = editingMatrix

        activeLayerIndex = activeIndex

        redrawAllCache()

    }



    private fun isSingleElementAppend(newLayers: List<Layer>): Boolean {

        if (layers.size != newLayers.size) return false

        var appendCount = 0

        for (i in layers.indices) {

            val oldLayer = layers[i]

            val newLayer = newLayers[i]

            if (oldLayer.id != newLayer.id || oldLayer.isVisible != newLayer.isVisible || oldLayer.opacity != newLayer.opacity || oldLayer.isLocked != newLayer.isLocked) {

                return false

            }

            if (oldLayer.elements.size != newLayer.elements.size) {

                if (i == activeLayerIndex && newLayer.elements.size == oldLayer.elements.size + 1 && newLayer.elements.subList(0, oldLayer.elements.size) == oldLayer.elements) {

                    val appended = newLayer.elements.last()

                    if (appended !== lastBakedElement) {

                        return false

                    }

                    appendCount++

                } else {

                    return false

                }

            }

        }

        return appendCount == 1

    }

    

    // --- TOOL SYNC ---

    var activeStrokeType: StrokeType = StrokeType.FREEHAND

        set(value) { if (field == value) return; field = value; strokePipeline.activeStrokeType = value }

    var activeStrokeColor: Int = android.graphics.Color.BLACK

        set(value) { if (field == value) return; field = value; strokePipeline.activeStrokeColor = value }

    var activeFillColor: Int = android.graphics.Color.TRANSPARENT
        set(value) {
            if (field == value) return
            field = value
            strokePipeline.activeFillColor = value
        }

    var activeFillStyle: FillStyle = FillStyle.Solid(android.graphics.Color.TRANSPARENT)
        set(value) {
            if (field == value) return
            field = value
            strokePipeline.activeFillStyle = value
        }

    var activeFillOpacity: Float = 0.5f
        set(value) {
            if (field == value) return
            field = value
            strokePipeline.activeFillOpacity = value
        }

    var isStrokeActive: Boolean = true

        set(value) { if (field == value) return; field = value; strokePipeline.isStrokeActive = value }

    var isFillActive: Boolean = false

        set(value) { if (field == value) return; field = value; strokePipeline.isFillActive = value }

    

    var activeColor: Int = android.graphics.Color.BLACK

        set(value) { if (field == value) return; field = value; activeStrokeColor = value }

    var activeSize: Float = 10f

        set(value) { if (field == value) return; field = value; strokePipeline.activeSize = value }

    var activeFreehandSettings: FreehandSettings = FreehandSettings()

        set(value) { if (field == value) return; field = value; strokePipeline.activeFreehandSettings = value }

    var isFlattenedOuterStrokeEnabled: Boolean = true

        set(value) { if (field == value) return; field = value; strokePipeline.isFlattenedOuterStrokeEnabled = value }

    var isFillModeEnabled: Boolean = false

        set(value) { if (field == value) return; field = value; isFillActive = value }

    var fillModeColor: Int = android.graphics.Color.TRANSPARENT

        set(value) { if (field == value) return; field = value; activeFillColor = value }

    var isFingerMode: Boolean = false

        set(value) { if (field == value) return; field = value; strokePipeline.isFingerMode = value }

    var fingerOffsetX: Float = 0f

        set(value) { if (field == value) return; field = value; strokePipeline.fingerOffsetX = value }

    var fingerOffsetY: Float = 50f

        set(value) { if (field == value) return; field = value; strokePipeline.fingerOffsetY = value }

    var globalStabilizationLevel: Float = 0f

        set(value) { if (field == value) return; field = value; strokePipeline.globalStabilizationLevel = value }

    var isPalmRejectionEnabled: Boolean = false

    var snapFunction: ((Float, Float) -> Pair<Float, Float>)? = null

        set(value) { field = value; strokePipeline.snapFunction = value }

    var currentTool: ToolType = ToolType.FREEHAND
        set(value) {
            if (field == value) return
            field = value
            strokePipeline.activeTool = value
            updateLayerType()
            if (value != ToolType.ERASER && value != ToolType.POINT_ERASER && value != ToolType.CUT_ERASER) {
                isEraserActive = false
            }
        }

    var activeEraserShape: com.sketcher.sketchercompanionv1.dto.EraserShape = com.sketcher.sketchercompanionv1.dto.EraserShape.CIRCLE
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    private fun updateLayerType() {
        val targetLayerType = LAYER_TYPE_NONE
        if (layerType != targetLayerType) {
            setLayerType(targetLayerType, null)
        }
    }

    var currentSelectionMode: SketcherViewModel.SelectionMode = SketcherViewModel.SelectionMode.RECTANGLE
        set(value) {
            if (field == value) return
            field = value
            redrawAllCache()
        }

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

    // Committed head of the live stroke (drawn separately, under the live tail)

    private var currentCommittedPreviewPath: android.graphics.Path? = null

    private var currentLiveIntersections: List<android.graphics.Path> = emptyList()

    private var currentStrokeBounds: android.graphics.RectF? = null

    private val liveMergedExistingStrokes = mutableListOf<VectorStroke>()



    // Pre-allocated drawing objects to avoid GC churn in onDraw

    private val saveLayerPaint = Paint()

    private val liveIntersectionPaint = Paint().apply {

        style = Paint.Style.FILL

        isAntiAlias = true

    }

    private val tempStrokeBounds = RectF()

    private val tempScreenBounds = RectF()

    private val drawCombinedMatrix = Matrix()

    private val reusableCombinedPath = android.graphics.Path()

    private val reusableFlattenedPath = android.graphics.Path()

    private val liveFillPaint = Paint().apply {

        style = Paint.Style.FILL

    }

    





    // updateCurrentVectorPreview removed - managed by StrokePipeline onUpdate





    fun updateCurrentFill(path: android.graphics.Path?, color: Int) {

        currentFillPath = path

        currentFillColor = color

        invalidate()

    }





    fun setCameraMatrix(matrix: Matrix, isIntermediate: Boolean = false) {

        viewMatrix.set(matrix)

        val combined = Matrix(viewMatrix)

        editingContainerMatrix?.let { combined.preConcat(it) }

        combined.invert(inverseMatrix)

        

        if (isIntermediate) {

            // Immediate feedback via bitmap scaling in onDraw

            invalidate()

            

            // Defer expensive redraw to avoid UI lag during rapid updates (zoom/pan)

            redrawHandler.removeCallbacks(delayedRedrawRunnable)

            redrawHandler.postDelayed(delayedRedrawRunnable, 60)

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

        // FPS Calculation

        val now = System.nanoTime()

        if (lastFrameTimeNs > 0L) {

            val frameTime = now - lastFrameTimeNs

            frameTimesNs[frameTimeIndex] = frameTime

            frameTimeIndex = (frameTimeIndex + 1) % frameTimesNs.size

            

            var sum = 0L

            var count = 0

            for (t in frameTimesNs) {

                if (t > 0L) {

                    sum += t

                    count++

                }

            }

            if (count > 0) {

                val avgFrameTimeNs = sum / count

                fpsState.value = (1_000_000_000L / avgFrameTimeNs).toInt()

            }

        }

        lastFrameTimeNs = now



        super.onDraw(canvas)

        

        // 1. Draw Cached Bitmap (Background & Layers)

        backingBitmap?.let { bitmap ->

            if (viewMatrix == cachedBitmapMatrix) {

                canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)

            } else {

                // High-performance scaling for intermediate frames (zoom/pan)

                if (cachedBitmapMatrix.invert(drawTransformMatrix)) {

                     drawTransformMatrix.postConcat(viewMatrix)

                     canvas.save()

                     canvas.concat(drawTransformMatrix)

                     canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)

                     canvas.restore()

                } else {

                     canvas.drawBitmap(bitmap, 0f, 0f, bitmapPaint)

                }

            }

        } ?: run {

             canvas.drawColor(canvasBackgroundColor)

        }



        // Combine view matrix and editing container matrix for drawing transient/live strokes in relative coordinate space

        drawCombinedMatrix.set(viewMatrix)

        editingContainerMatrix?.let { drawCombinedMatrix.preConcat(it) }



        // Draw Transient Strokes manually while background rendering finishes

        canvas.save()

        canvas.concat(drawCombinedMatrix)

        val activeLayerOpacity = layers.getOrNull(activeLayerIndex)?.opacity ?: 1f

        for (fill in transientFills) {

            renderEngine.drawElementRecursive(canvas, fill, componentLibrary, drawCombinedMatrix, activeLayerOpacity)

        }

        for (stroke in transientStrokes) {

            renderEngine.drawElementRecursive(canvas, stroke, componentLibrary, drawCombinedMatrix, activeLayerOpacity)

        }

        canvas.restore()



        // 2. Draw Live Content (Stroke & Fill)

        if (isDrawing && (isStrokeActive || isFillActive)) {

            val hasCommitted = currentCommittedPreviewPath != null

            val hasLive = currentVectorPreviewPath != null || currentFillPath != null

            

            if (hasCommitted || hasLive) {

                // Pass 1: Draw Live Fill (always directly on canvas, using its own activeFillColor with alpha, multiplied by activeLayerOpacity)

                if (isFillActive && currentFillPath != null) {
                    liveFillPaint.style = android.graphics.Paint.Style.FILL
                    renderEngine.applyFillStyle(liveFillPaint, activeFillStyle, alphaMultiplier = activeFillOpacity * activeLayerOpacity)
                    canvas.save()
                    canvas.concat(drawCombinedMatrix)
                    canvas.drawPath(currentFillPath!!, liveFillPaint)
                    canvas.restore()
                    liveFillPaint.shader = null
                }

                // Pass 2: Draw Live Stroke
                if (isStrokeActive || ((currentTool == ToolType.PAINT || currentTool == ToolType.WATERCOLOR) && isFillActive)) {
                    val strokeAlpha = if (isStrokeActive) (android.graphics.Color.alpha(activeStrokeColor) / 255f) else 0f
                    val fillAlpha = if (isFillActive) activeFillOpacity else 0f
                    val primaryAlpha = if (isStrokeActive) strokeAlpha else fillAlpha

                    val relativeFillAlpha = if (currentTool == ToolType.WATERCOLOR) {
                        activeFillOpacity
                    } else {
                        if (primaryAlpha > 0f) (fillAlpha / primaryAlpha).coerceIn(0f, 1f) else 0f
                    }

                    val effectiveStrokeOpacity = if (currentTool == ToolType.WATERCOLOR) {
                        activeLayerOpacity
                    } else {
                        primaryAlpha * activeLayerOpacity
                    }

                    val isCumulative = activeFreehandSettings.isCumulativeOpacity

                    val isCadDraw = activeStrokeType != StrokeType.FREEHAND

                    

                    if (effectiveStrokeOpacity < 1f && !isCumulative) {

                        // Non-cumulative semi-transparent stroke: use saveLayer to avoid connection seams

                        saveLayerPaint.alpha = (effectiveStrokeOpacity * 255).toInt().coerceIn(0, 255)

                        val bounds = currentStrokeBounds

                        val saveCount = if (bounds != null && !bounds.isEmpty) {

                            tempStrokeBounds.set(bounds)

                            drawCombinedMatrix.mapRect(tempScreenBounds, tempStrokeBounds)

                            val insetVal = if (currentTool == ToolType.WATERCOLOR) -60f else -4f
                            tempScreenBounds.inset(insetVal, insetVal)

                            canvas.saveLayer(tempScreenBounds, saveLayerPaint)

                        } else {

                            canvas.saveLayer(null, saveLayerPaint)

                        }

                        

                        val opaqueColor = activeStrokeColor or (0xFF shl 24)

                        val opaqueFillColor = (activeFillColor and 0x00FFFFFF) or ((relativeFillAlpha * 255).toInt().coerceIn(0, 255) shl 24)

                        

                        if (currentTool == ToolType.PAINT || currentTool == ToolType.WATERCOLOR) {

                            canvas.save()

                            canvas.concat(drawCombinedMatrix)

                            

                            val combinedPath = reusableCombinedPath.apply { rewind() }

                            if (currentCommittedPreviewPath != null && currentVectorPreviewPath != null) {

                                combinedPath.op(currentCommittedPreviewPath!!, currentVectorPreviewPath!!, android.graphics.Path.Op.UNION)

                            } else {

                                currentCommittedPreviewPath?.let { combinedPath.set(it) }

                                currentVectorPreviewPath?.let { combinedPath.set(it) }

                            }



                            // 1. Draw fills (underneath)
                            if (isFillActive) {
                                liveFillPaint.style = android.graphics.Paint.Style.FILL
                                val fillStyleSnap = activeFillStyle
                                val relativeFillStyle = when (fillStyleSnap) {
                                    is FillStyle.Solid -> FillStyle.Solid(fillStyleSnap.color or (0xFF shl 24))
                                    else -> fillStyleSnap
                                }
                                renderEngine.applyFillStyle(liveFillPaint, relativeFillStyle, alphaMultiplier = relativeFillAlpha)
                                canvas.drawPath(combinedPath, liveFillPaint)
                                liveFillPaint.shader = null
                            }



                            // 2. Draw borders (on top)

                            if (isStrokeActive) {
                                if (currentTool == ToolType.WATERCOLOR) {
                                    val origAlpha = android.graphics.Color.alpha(opaqueColor)
                                    
                                    val jitteredPath = com.sketcher.sketchercompanionv1.utils.JitterPathHelper.createJitterPath(
                                        combinedPath,
                                        activeFreehandSettings.watercolorJitterSegment,
                                        activeFreehandSettings.watercolorJitterDeviation,
                                        seed = 999L
                                    )

                                    // --- PASADA 1: CUERPO DIFUMINADO (CON CLIPPING) ---
                                    liveFillPaint.style = android.graphics.Paint.Style.STROKE
                                    val bodyAlpha = (origAlpha * activeFreehandSettings.watercolorCenterOpacity).toInt().coerceIn(0, 255)
                                    liveFillPaint.color = (opaqueColor and 0x00FFFFFF) or (bodyAlpha shl 24)
                                    liveFillPaint.strokeWidth = activeFreehandSettings.paintOutlineWidth
                                    
                                    liveFillPaint.pathEffect = null
                                    liveFillPaint.maskFilter = android.graphics.BlurMaskFilter(
                                        activeFreehandSettings.watercolorBlurRadius.coerceAtLeast(0.01f),
                                        android.graphics.BlurMaskFilter.Blur.NORMAL
                                    )

                                    if (activeFreehandSettings.watercolorEdgeMode == com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.INSIDE) {
                                        canvas.save()
                                        canvas.clipPath(combinedPath)
                                        canvas.drawPath(jitteredPath, liveFillPaint)
                                        canvas.restore()
                                    } else if (activeFreehandSettings.watercolorEdgeMode == com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.OUTSIDE) {
                                        canvas.save()
                                        canvas.clipOutPath(combinedPath)
                                        canvas.drawPath(jitteredPath, liveFillPaint)
                                        canvas.restore()
                                    } else {
                                        canvas.drawPath(jitteredPath, liveFillPaint)
                                    }
                                    
                                    liveFillPaint.pathEffect = null
                                    liveFillPaint.maskFilter = null
                                } else {
                                    liveFillPaint.style = android.graphics.Paint.Style.STROKE
                                    liveFillPaint.strokeWidth = activeFreehandSettings.paintOutlineWidth
                                    liveFillPaint.pathEffect = null
                                    liveFillPaint.maskFilter = null
                                    liveFillPaint.color = opaqueColor
                                    canvas.drawPath(combinedPath, liveFillPaint)
                                }
                            }

                            

                            if (isDebugWireframe && currentVectorPreviewPoints != null) {
                                renderEngine.drawDebugWireframe(
                                    canvas,
                                    currentVectorPreviewPoints!!,
                                    drawCombinedMatrix,
                                    currentVectorPreviewPath
                                )
                            }

                            canvas.restore()

                        } else {

                            currentCommittedPreviewPath?.let { committed ->

                                canvas.save()

                                canvas.concat(drawCombinedMatrix)

                                renderEngine.drawCommittedPreview(

                                    canvas, 

                                    committed, 

                                    opaqueColor, 

                                    opaqueFillColor, 

                                    isStrokeActive, 

                                    isFillActive, 

                                    activeStrokeType, brushType = currentTool.name

                                )

                                canvas.restore()

                            }

                            

                            renderEngine.drawLiveStroke(

                                canvas, 

                                currentVectorPreviewPoints, 

                                currentVectorPreviewPath,

                                opaqueColor,

                                fillPath = null, // Handled in Pass 1!

                                fillColor = opaqueFillColor,

                                isFillActive = isFillActive,

                                isStrokeActive = isStrokeActive,

                                currentLiveGeneratedRadius = currentLiveGeneratedRadius,

                                viewMatrix = drawCombinedMatrix,

                                isDrawing = isDrawing,

                                isCad = isCadDraw,

                                strokeType = activeStrokeType,

                                fillStyle = activeFillStyle, brushType = currentTool.name

                            )

                        }

                        

                        canvas.restoreToCount(saveCount)

                    } else {
                        // Cumulative or fully opaque: draw directly onto canvas
                        val layerStrokeColor = let {
                            val origColor = activeStrokeColor
                            val origAlpha = if (currentTool == ToolType.WATERCOLOR) 255 else android.graphics.Color.alpha(origColor)
                            val newAlpha = (origAlpha * activeLayerOpacity).toInt().coerceIn(0, 255)
                            (origColor and 0x00FFFFFF) or (newAlpha shl 24)
                        }
                        val layerFillColor = let {
                            val origColor = activeFillColor
                            val origAlpha = if (currentTool == ToolType.WATERCOLOR) 255 else android.graphics.Color.alpha(origColor)
                            val newAlpha = (origAlpha * activeFillOpacity * activeLayerOpacity).toInt().coerceIn(0, 255)
                            (origColor and 0x00FFFFFF) or (newAlpha shl 24)
                        }

                        if (currentTool == ToolType.PAINT || currentTool == ToolType.WATERCOLOR) {
                            val isWatercolor = currentTool == ToolType.WATERCOLOR
                            val bounds = currentStrokeBounds
                            val saveCount = if (isWatercolor) {
                                if (bounds != null && !bounds.isEmpty) {
                                    tempStrokeBounds.set(bounds)
                                    drawCombinedMatrix.mapRect(tempScreenBounds, tempStrokeBounds)
                                    tempScreenBounds.inset(-60f, -60f)
                                    canvas.saveLayer(tempScreenBounds, null)
                                } else {
                                    canvas.saveLayer(null, null)
                                }
                            } else {
                                0
                            }

                            canvas.save()
                            canvas.concat(drawCombinedMatrix)
                            
                            val combinedPath = reusableCombinedPath.apply { rewind() }
                            if (currentCommittedPreviewPath != null && currentVectorPreviewPath != null) {
                                combinedPath.op(currentCommittedPreviewPath!!, currentVectorPreviewPath!!, android.graphics.Path.Op.UNION)
                            } else {
                                currentCommittedPreviewPath?.let { combinedPath.set(it) }
                                currentVectorPreviewPath?.let { combinedPath.set(it) }
                            }

                            // 1. Draw fills (underneath)
                            if (isFillActive) {
                                liveFillPaint.style = android.graphics.Paint.Style.FILL
                                renderEngine.applyFillStyle(liveFillPaint, activeFillStyle, alphaMultiplier = activeFillOpacity * activeLayerOpacity)
                                canvas.drawPath(combinedPath, liveFillPaint)
                                liveFillPaint.shader = null
                            }

                            // 2. Draw borders (on top)
                            if (isStrokeActive) {
                                if (isWatercolor) {
                                    val origAlpha = android.graphics.Color.alpha(layerStrokeColor)
                                    
                                    val jitteredPath = com.sketcher.sketchercompanionv1.utils.JitterPathHelper.createJitterPath(
                                        combinedPath,
                                        activeFreehandSettings.watercolorJitterSegment,
                                        activeFreehandSettings.watercolorJitterDeviation,
                                        seed = 999L
                                    )

                                    // --- PASADA 1: CUERPO DIFUMINADO (CON CLIPPING) ---
                                    liveFillPaint.style = android.graphics.Paint.Style.STROKE
                                    val bodyAlpha = (origAlpha * activeFreehandSettings.watercolorCenterOpacity).toInt().coerceIn(0, 255)
                                    liveFillPaint.color = (layerStrokeColor and 0x00FFFFFF) or (bodyAlpha shl 24)
                                    liveFillPaint.strokeWidth = activeFreehandSettings.paintOutlineWidth
                                    
                                    liveFillPaint.pathEffect = null
                                    liveFillPaint.maskFilter = android.graphics.BlurMaskFilter(
                                        activeFreehandSettings.watercolorBlurRadius.coerceAtLeast(0.01f),
                                        android.graphics.BlurMaskFilter.Blur.NORMAL
                                    )

                                    if (activeFreehandSettings.watercolorEdgeMode == com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.INSIDE) {
                                        canvas.save()
                                        canvas.clipPath(combinedPath)
                                        canvas.drawPath(jitteredPath, liveFillPaint)
                                        canvas.restore()
                                    } else if (activeFreehandSettings.watercolorEdgeMode == com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.OUTSIDE) {
                                        canvas.save()
                                        canvas.clipOutPath(combinedPath)
                                        canvas.drawPath(jitteredPath, liveFillPaint)
                                        canvas.restore()
                                    } else {
                                        canvas.drawPath(jitteredPath, liveFillPaint)
                                    }
                                    
                                    liveFillPaint.pathEffect = null
                                    liveFillPaint.maskFilter = null
                                } else {
                                    liveFillPaint.style = android.graphics.Paint.Style.STROKE
                                    liveFillPaint.strokeWidth = activeFreehandSettings.paintOutlineWidth
                                    liveFillPaint.pathEffect = null
                                    liveFillPaint.maskFilter = null
                                    liveFillPaint.color = layerStrokeColor
                                    canvas.drawPath(combinedPath, liveFillPaint)
                                }
                            }
                            
                            if (isDebugWireframe && currentVectorPreviewPoints != null) {
                                renderEngine.drawDebugWireframe(
                                    canvas,
                                    currentVectorPreviewPoints!!,
                                    drawCombinedMatrix,
                                    currentVectorPreviewPath
                                )
                            }
                            canvas.restore() // restore matrix save
                            if (isWatercolor && saveCount > 0) {
                                canvas.restoreToCount(saveCount) // restore saveLayer
                            }
                        } else {
                            currentCommittedPreviewPath?.let { committed ->
                                canvas.save()
                                canvas.concat(drawCombinedMatrix)
                                renderEngine.drawCommittedPreview(
                                    canvas, 
                                    committed, 
                                    layerStrokeColor, 
                                    layerFillColor, 
                                    isStrokeActive, 
                                    isFillActive, 
                                    activeStrokeType,
                                    brushType = currentTool.name
                                )
                                canvas.restore()
                            }
                            
                            renderEngine.drawLiveStroke(
                                canvas, 
                                currentVectorPreviewPoints, 
                                currentVectorPreviewPath,
                                layerStrokeColor,
                                fillPath = null, // Handled in Pass 1!
                                fillColor = layerFillColor,
                                isFillActive = isFillActive,
                                isStrokeActive = isStrokeActive,
                                currentLiveGeneratedRadius = currentLiveGeneratedRadius,
                                viewMatrix = drawCombinedMatrix,
                                isDrawing = isDrawing,
                                isCad = isCadDraw,
                                strokeType = activeStrokeType,
                                fillStyle = activeFillStyle,
                                brushType = currentTool.name
                            )
                        }
                    }

                    

                    // Draw live cumulative intersections on top with transparent paint

                    if (isCumulative && currentLiveIntersections.isNotEmpty()) {

                        canvas.save()

                        canvas.concat(drawCombinedMatrix)

                        liveIntersectionPaint.color = activeStrokeColor

                        for (p in currentLiveIntersections) {

                            canvas.drawPath(p, liveIntersectionPaint)

                        }

                        canvas.restore()

                    }

                }

            }

        }



        // Selection path preview drawing

        if (currentTool == ToolType.SELECTION && (currentSelectionMode == SketcherViewModel.SelectionMode.FREEHAND || currentSelectionMode == SketcherViewModel.SelectionMode.RECTANGLE || currentSelectionMode == SketcherViewModel.SelectionMode.POLYGON)) {

            selectionManager?.let { manager ->

                val path = manager.lassoPath

                if (!path.isEmpty) {

                    val zoom = getMatrixScale(viewMatrix)
                    selectionLassoPaint.strokeWidth = (2f * resources.displayMetrics.density) / (if (zoom > 0f) zoom else 1f)
                    canvas.save()
                    canvas.concat(viewMatrix)
                    canvas.drawPath(path, selectionLassoPaint)
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

                  val combinedMatrix = Matrix(viewMatrix)

                  combinedMatrix.postConcat(manager.selectionMatrix)

                  for (element in manager.selectedElements) {

                      val elementLayerOpacity = layers.find { layer -> layer.elements.any { it === element } }?.opacity ?: 1f

                      renderEngine.drawElementRecursive(canvas, element, componentLibrary, combinedMatrix, elementLayerOpacity)

                  }

                  canvas.restore()

             }

        }



        // 4. Selection Box/Handles OR Grip Handles

        selectionManager?.let { manager ->

            if (manager.selectedElements.isNotEmpty()) {

                val density = resources.displayMetrics.density
                if (currentTool == ToolType.EDIT_POINTS) {
                    for (element in manager.selectedElements) {
                        if (element is VectorStroke) {
                            renderEngine.drawGrips(canvas, element, viewMatrix, density)
                        }
                    }
                } else {
                    renderEngine.drawSelectionOverlay(canvas, manager, viewMatrix, density)
                }

            }

        }



        // 5. Draw Projection Viewports (Indicator overlay)

        if (!isCapturingForProjection && projectionViewports.isNotEmpty()) {

            projectionViewportPaint.strokeWidth = 3f * resources.displayMetrics.density

            projectionTextPaint.textSize = 12f * resources.displayMetrics.density



            for (viewport in projectionViewports) {

                projectionViewportPaint.color = viewport.color

                projectionTextPaint.color = Color.WHITE

                projectionTextBgPaint.color = viewport.color



                // Draw viewport rect in screen/view space (so no viewMatrix transformation)

                tempViewportRect.set(viewport.left, viewport.top, viewport.right, viewport.bottom)

                canvas.drawRect(tempViewportRect, projectionViewportPaint)



                // Draw label at the top-left of the rectangle

                val text = viewport.label

                val textWidth = projectionTextPaint.measureText(text)

                val textHeight = projectionTextPaint.fontMetrics.descent - projectionTextPaint.fontMetrics.ascent

                

                // Draw a small background pill for the label

                tempLabelRect.set(

                    tempViewportRect.left,

                    tempViewportRect.top,

                    tempViewportRect.left + textWidth + 8f * resources.displayMetrics.density,

                    tempViewportRect.top + textHeight + 4f * resources.displayMetrics.density

                )

                canvas.drawRect(tempLabelRect, projectionTextBgPaint)

                canvas.drawText(

                    text,

                    tempViewportRect.left + 4f * resources.displayMetrics.density,

                    tempViewportRect.top - projectionTextPaint.fontMetrics.ascent + 2f * resources.displayMetrics.density,

                    projectionTextPaint

                )

            }

        }

        

        // Draw Stylus Hover Preview Line for multi-step tools

        if (!isDrawing && strokePipeline.isMultiStepInProgress) {

            val activeStrokeType = strokePipeline.activeStrokeType

            val startPt = if (activeStrokeType == StrokeType.BEZIER) {

                val pts = strokePipeline.currentStrokePointsList

                if (pts.size >= 2) pts[pts.size - 2] else null

            } else {

                strokePipeline.currentStrokePointsList.lastOrNull()

            }

            val hoverPt = hoverWorldPoint

            if (startPt != null && hoverPt != null) {

                canvas.save()

                canvas.concat(viewMatrix)

                hoverPreviewPaint.color = activeStrokeColor

                hoverPreviewPaint.strokeWidth = (activeSize / getMatrixScale(viewMatrix)).coerceIn(1f, 10f)

                canvas.drawLine(startPt.x, startPt.y, hoverPt.x, hoverPt.y, hoverPreviewPaint)

                canvas.restore()

            }

        }

        

        if (strokePipeline.isMultiStepInProgress && strokePipeline.activeStrokeType == StrokeType.BEZIER) {

            val tempStroke = VectorStroke(

                points = strokePipeline.currentStrokePointsList,

                strokeColor = activeStrokeColor,

                maxWidth = activeSize,

                path = android.graphics.Path(),

                strokeType = StrokeType.BEZIER

            )

            val density = resources.displayMetrics.density

            renderEngine.drawGrips(canvas, tempStroke, viewMatrix, density)

        }



        currentSnapPoint?.let { snap ->

            renderEngine.drawSnapMarker(canvas, snap, viewMatrix, resources.displayMetrics.density)

        }

        if (isEraserActive && eraserRadiusWorld > 0f && (currentTool == ToolType.ERASER || currentTool == ToolType.POINT_ERASER || currentTool == ToolType.CUT_ERASER)) {
            canvas.save()
            canvas.concat(viewMatrix)
            if (activeEraserShape == com.sketcher.sketchercompanionv1.dto.EraserShape.SQUARE) {
                canvas.drawRect(
                    eraserWorldX - eraserRadiusWorld,
                    eraserWorldY - eraserRadiusWorld,
                    eraserWorldX + eraserRadiusWorld,
                    eraserWorldY + eraserRadiusWorld,
                    eraserPreviewFillPaint
                )
            } else {
                canvas.drawCircle(eraserWorldX, eraserWorldY, eraserRadiusWorld, eraserPreviewFillPaint)
            }
            val zoom = getMatrixScale(viewMatrix)
            eraserPreviewPaint.strokeWidth = (1.5f * resources.displayMetrics.density) / (if (zoom > 0f) zoom else 1f)
            if (activeEraserShape == com.sketcher.sketchercompanionv1.dto.EraserShape.SQUARE) {
                canvas.drawRect(
                    eraserWorldX - eraserRadiusWorld,
                    eraserWorldY - eraserRadiusWorld,
                    eraserWorldX + eraserRadiusWorld,
                    eraserWorldY + eraserRadiusWorld,
                    eraserPreviewPaint
                )
            } else {
                canvas.drawCircle(eraserWorldX, eraserWorldY, eraserRadiusWorld, eraserPreviewPaint)
            }
            canvas.restore()
        }

        drawCadVisualGuides(canvas)

        onDrawAction?.invoke()

        onDrawAction = null

    }



    var onDrawAction: (() -> Unit)? = null



    override fun onHoverEvent(event: MotionEvent): Boolean {
        if (currentTool == ToolType.ERASER || currentTool == ToolType.POINT_ERASER || currentTool == ToolType.CUT_ERASER) {
            val action = event.actionMasked
            when (action) {
                MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                    val pts = floatArrayOf(event.x, event.y)
                    inverseMatrix.mapPoints(pts)
                    val worldX = pts[0]
                    val worldY = pts[1]
                    val diameterPx = activeSize * 2f
                    val radiusWorld = com.sketcher.sketchercompanionv1.utils.UnitUtils.pixelsToProjectUnits(
                        diameterPx, currentUnit, scaleConfig.basePixelsPerMillimeter
                    ) / 2f

                    eraserWorldX = worldX
                    eraserWorldY = worldY
                    eraserRadiusWorld = radiusWorld
                    isEraserActive = true
                    invalidate()
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    isEraserActive = false
                    invalidate()
                }
            }
            return true
        }

        if (!isElementSnappingEnabled && !isSnapToGridEnabled) {

            currentSnapPoint = null

            hoverWorldPoint = null

            invalidate()

            return super.onHoverEvent(event)

        }



        val action = event.actionMasked

        val zoom = getMatrixScale(viewMatrix)



        when (action) {

            MotionEvent.ACTION_HOVER_ENTER -> {

                activeSnapPoints = if (isElementSnappingEnabled) {

                    getCombinedSnapPoints()

                } else {

                    emptyList()

                }

            }

            MotionEvent.ACTION_HOVER_MOVE -> {

                val pts = floatArrayOf(event.x, event.y)

                inverseMatrix.mapPoints(pts)

                val worldX = pts[0]

                val worldY = pts[1]



                var snappedWorldX = worldX

                var snappedWorldY = worldY

                var resolvedSnap: SnapPoint? = null



                if (isElementSnappingEnabled) {

                    resolvedSnap = SnapEngine.resolveSnap(worldX, worldY, activeSnapPoints, zoom)

                    if (resolvedSnap != null) {

                        snappedWorldX = resolvedSnap.point.x

                        snappedWorldY = resolvedSnap.point.y

                    }

                }



                if (resolvedSnap == null && isSnapToGridEnabled) {

                    val gridStepPx = UnitUtils.projectUnitsToPixels(

                        value = gridConfig.spacing,

                        unit = currentUnit,

                        basePxPerMm = scaleConfig.basePixelsPerMillimeter

                    )

                    if (gridStepPx > 0) {

                        snappedWorldX = kotlin.math.round(worldX / gridStepPx) * gridStepPx

                        snappedWorldY = kotlin.math.round(worldY / gridStepPx) * gridStepPx

                    }

                }



                currentSnapPoint = resolvedSnap

                hoverWorldPoint = android.graphics.PointF(snappedWorldX, snappedWorldY)

                invalidate()

            }

            MotionEvent.ACTION_HOVER_EXIT -> {

                currentSnapPoint = null

                hoverWorldPoint = null

                invalidate()

            }

        }

        return true

    }



    override fun onTouchEvent(event: MotionEvent): Boolean {

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {

            val baseSnapPoints = if (isElementSnappingEnabled) {

                getCombinedSnapPoints()

            } else {

                emptyList()

            }



            val isDrawTool = currentTool == ToolType.FREEHAND || currentTool == ToolType.PEN || currentTool == ToolType.PAINT || currentTool == ToolType.WATERCOLOR

            if (isElementSnappingEnabled && isSnapEndpointEnabled && isDrawTool) {

                val pts = floatArrayOf(event.x, event.y)

                inverseMatrix.mapPoints(pts)

                val worldX = pts[0]

                val worldY = pts[1]



                var snappedWorldX = worldX

                var snappedWorldY = worldY

                val zoom = getMatrixScale(viewMatrix)

                val resolvedSnap = SnapEngine.resolveSnap(worldX, worldY, baseSnapPoints, zoom)

                if (resolvedSnap != null) {

                    snappedWorldX = resolvedSnap.point.x

                    snappedWorldY = resolvedSnap.point.y

                } else if (isSnapToGridEnabled) {

                    val gridStepPx = UnitUtils.projectUnitsToPixels(

                        value = gridConfig.spacing,

                        unit = currentUnit,

                        basePxPerMm = scaleConfig.basePixelsPerMillimeter

                    )

                    if (gridStepPx > 0) {

                        snappedWorldX = kotlin.math.round(worldX / gridStepPx) * gridStepPx

                        snappedWorldY = kotlin.math.round(worldY / gridStepPx) * gridStepPx

                    }

                }



                activeSnapPoints = baseSnapPoints + SnapPoint(android.graphics.PointF(snappedWorldX, snappedWorldY), SnapType.ENDPOINT)

            } else {

                activeSnapPoints = baseSnapPoints

            }

        } else if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {

            currentSnapPoint = null

            activeSnapPoints = emptyList()

        }



        // --- 1. GESTURES (Zoom/Pan) ---

        // Detect if a stylus is touching the screen to avoid gesture conflicts
        val hasStylus = (0 until event.pointerCount).any { i ->
            val toolType = event.getToolType(i)
            toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
        }

        // Delegate to Scale and Gesture Detectors
        val wasInProgress = scaleDetector.isInProgress

        if (hasStylus) {
            if (wasInProgress) {
                // If a gesture was active, cancel it to reset internal detector states cleanly
                val cancelEvent = MotionEvent.obtain(event)
                cancelEvent.setAction(MotionEvent.ACTION_CANCEL)
                scaleDetector.onTouchEvent(cancelEvent)
                gestureDetector.onTouchEvent(cancelEvent)
                cancelEvent.recycle()
            }
        } else {
            scaleDetector.onTouchEvent(event)
            gestureDetector.onTouchEvent(event)
        }

        // Manual Pan Logic Removed (Replaced by GestureDetector)



        // If we are zooming or have 2+ fingers, don't draw.

        // We only allow scale/pan block if we actually have 2+ pointers to avoid single-finger ghost/stuck scale detector states.

        val isScaleActive = scaleDetector.isInProgress && !hasStylus && event.pointerCount >= 2

        if (!hasStylus && (isScaleActive || event.pointerCount >= 2 || (wasInProgress && (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_POINTER_UP)))) {

            if (event.actionMasked == MotionEvent.ACTION_UP || (event.pointerCount == 2 && event.actionMasked == MotionEvent.ACTION_POINTER_UP)) {

                redrawAllCache()

            }

            return true

        }



        // Basic Palm Rejection: If enabled and we have a stylus, ignore non-stylus events

        if (currentTool != ToolType.SELECTION && isPalmRejectionEnabled && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {

            return true // Consume event to keep stream alive for gestures, but don't draw

        }



        val combined = Matrix(viewMatrix)

        editingContainerMatrix?.let { combined.preConcat(it) }

        strokePipeline.canvasViewMatrix.set(combined)

        

        // Calculate current zoom factor

        val zoom = getMatrixScale(viewMatrix)

        strokePipeline.currentZoom = zoom

        

        return when (currentTool) {

            ToolType.SELECTION -> handleSelectionInput(event)

            ToolType.ERASER, ToolType.POINT_ERASER, ToolType.CUT_ERASER -> handleEraserInput(event)

            ToolType.TRIM -> {

                if (event.actionMasked == MotionEvent.ACTION_DOWN) {

                    tempTouchPoint[0] = event.x

                    tempTouchPoint[1] = event.y

                    inverseMatrix.mapPoints(tempTouchPoint)

                    onTrimRequested?.invoke(tempTouchPoint[0], tempTouchPoint[1])

                }

                true

            }

            ToolType.EXTEND -> {

                if (event.actionMasked == MotionEvent.ACTION_DOWN) {

                    tempTouchPoint[0] = event.x

                    tempTouchPoint[1] = event.y

                    inverseMatrix.mapPoints(tempTouchPoint)

                    onExtendRequested?.invoke(tempTouchPoint[0], tempTouchPoint[1])

                }

                true

            }

            ToolType.EDIT_POINTS -> handleEditPointsInput(event)

            ToolType.MIRROR -> handleMirrorInput(event)

            ToolType.MOVE_PT_PT -> handleMovePtPtInput(event)

            ToolType.ALIGN_2_PT -> handleAlign2PtInput(event)

            ToolType.OFFSET -> handleOffsetInput(event)

            ToolType.FILLET -> handleFilletInput(event)

            ToolType.CHAMFER -> handleChamferInput(event)

            else -> strokePipeline.onTouchEvent(event)

        }

    }



    private fun handleEditPointsInput(event: MotionEvent): Boolean {

        val density = resources.displayMetrics.density

        val manager = selectionManager ?: return false

        

        when (event.actionMasked) {

            MotionEvent.ACTION_DOWN -> {

                isDraggingGrip = false

                activeDraggedStroke = null

                activeDraggedPointIndex = -1

                

                // Find if touch is near a control point (grip) of selected elements

                var foundStroke: VectorStroke? = null

                var foundPointIndex: Int = -1

                var minDistancePx = Float.MAX_VALUE

                

                val selected = manager.selectedElements.filterIsInstance<VectorStroke>()

                for (stroke in selected) {

                    for (idx in stroke.points.indices) {

                        val p = stroke.points[idx]

                        val pts = floatArrayOf(p.x, p.y)

                        viewMatrix.mapPoints(pts)

                        val dist = kotlin.math.hypot(event.x - pts[0], event.y - pts[1])

                        if (dist < 28f * density && dist < minDistancePx) { // 28dp touch target

                            foundStroke = stroke

                            foundPointIndex = idx

                            minDistancePx = dist

                        }

                    }

                }

                

                if (foundStroke != null) {

                    activeDraggedStroke = foundStroke

                    activeDraggedPointIndex = foundPointIndex

                    originalStrokePoints = foundStroke.points.map { it.copy() }

                    isDraggingGrip = true

                    invalidate()

                    return true

                } else {

                    // Try to select/deselect a single element

                    tempTouchPoint[0] = event.x

                    tempTouchPoint[1] = event.y

                    inverseMatrix.mapPoints(tempTouchPoint)

                    val worldX = tempTouchPoint[0]

                    val worldY = tempTouchPoint[1]

                    

                    val layerIndex = activeLayerIndex

                    if (layerIndex in layers.indices) {

                        val didSelect = manager.selectSingleAt(worldX, worldY, activeContainer, componentLibrary, addToSelection = false)

                        if (didSelect) {

                            invalidate()

                            return true

                        }

                    }

                }

            }

            MotionEvent.ACTION_MOVE -> {

                if (isDraggingGrip && activeDraggedStroke != null) {

                    var finalScreenX = event.x

                    var finalScreenY = event.y

                    

                    val snapFn = snapFunction

                    if (snapFn != null) {

                        val snapped = snapFn(event.x, event.y)

                        finalScreenX = snapped.first

                        finalScreenY = snapped.second

                    } else {

                        currentSnapPoint = null

                    }

                    

                    tempTouchPoint[0] = finalScreenX

                    tempTouchPoint[1] = finalScreenY

                    inverseMatrix.mapPoints(tempTouchPoint)

                    val worldX = tempTouchPoint[0]

                    val worldY = tempTouchPoint[1]

                    

                    val stroke = activeDraggedStroke!!

                    val origPts = originalStrokePoints ?: return true

                    val idx = activeDraggedPointIndex

                    

                    val ptsList = stroke.points.toMutableList()



                    if (stroke.strokeType == StrokeType.CIRCLE || stroke.strokeType == StrokeType.ELLIPSE) {



                        if (idx == 0) {



                            // Translate whole shape if center dragged



                            val dx = worldX - origPts[0].x



                            val dy = worldY - origPts[0].y



                            for (i in ptsList.indices) {



                                ptsList[i] = ptsList[i].copy(x = origPts[i].x + dx, y = origPts[i].y + dy)



                            }



                        } else {



                            ptsList[idx] = ptsList[idx].copy(x = worldX, y = worldY)



                        }



                    } else if (stroke.strokeType == StrokeType.BEZIER) {



                        val size = ptsList.size



                        val isClosed = (ptsList.first().x == ptsList.last().x && ptsList.first().y == ptsList.last().y)



                        



                        if (idx % 3 == 0) {



                            // Anchor point: translate it and its tangent handles together



                            val dx = worldX - origPts[idx].x



                            val dy = worldY - origPts[idx].y



                            ptsList[idx] = ptsList[idx].copy(x = worldX, y = worldY)



                            



                            if (isClosed) {



                                if (idx == 0) {



                                    ptsList[size - 1] = ptsList[size - 1].copy(x = worldX, y = worldY)



                                } else if (idx == size - 1) {



                                    ptsList[0] = ptsList[0].copy(x = worldX, y = worldY)



                                }



                            }



                            



                            if (idx + 1 < size) {



                                ptsList[idx + 1] = ptsList[idx + 1].copy(x = origPts[idx + 1].x + dx, y = origPts[idx + 1].y + dy)



                            } else if (isClosed && idx == size - 1) {



                                ptsList[1] = ptsList[1].copy(x = origPts[1].x + dx, y = origPts[1].y + dy)



                            }



                            



                            if (idx - 1 >= 0) {



                                ptsList[idx - 1] = ptsList[idx - 1].copy(x = origPts[idx - 1].x + dx, y = origPts[idx - 1].y + dy)



                            } else if (isClosed && idx == 0) {



                                ptsList[size - 2] = ptsList[size - 2].copy(x = origPts[size - 2].x + dx, y = origPts[size - 2].y + dy)



                            }



                        } else if (idx % 3 == 1) {



                            // Out-tangent handle



                            ptsList[idx] = ptsList[idx].copy(x = worldX, y = worldY)



                            val anchorIdx = idx - 1



                            if (anchorIdx >= 0) {



                                val anchor = ptsList[anchorIdx]



                                val dx = worldX - anchor.x



                                val dy = worldY - anchor.y



                                val oppIdx = anchorIdx - 1



                                if (oppIdx >= 0 && oppIdx < size) {



                                    ptsList[oppIdx] = ptsList[oppIdx].copy(x = anchor.x - dx, y = anchor.y - dy)



                                } else if (isClosed && anchorIdx == 0) {



                                    ptsList[size - 2] = ptsList[size - 2].copy(x = anchor.x - dx, y = anchor.y - dy)



                                }



                            }



                        } else if (idx % 3 == 2) {



                            // In-tangent handle



                            ptsList[idx] = ptsList[idx].copy(x = worldX, y = worldY)



                            val anchorIdx = idx + 1



                            if (anchorIdx < size) {



                                val anchor = ptsList[anchorIdx]



                                val dx = worldX - anchor.x



                                val dy = worldY - anchor.y



                                val oppIdx = anchorIdx + 1



                                if (oppIdx < size) {



                                    ptsList[oppIdx] = ptsList[oppIdx].copy(x = anchor.x - dx, y = anchor.y - dy)



                                } else if (isClosed && anchorIdx == size - 1) {



                                    ptsList[1] = ptsList[1].copy(x = anchor.x - dx, y = anchor.y - dy)



                                }



                            }



                        }



                    } else {



                        ptsList[idx] = ptsList[idx].copy(x = worldX, y = worldY)



                    }



                    stroke.points = ptsList

                    

                    val newPath = com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(stroke.strokeType, stroke.points)

                    stroke.path.rewind()

                    stroke.path.set(newPath)

                    

                    if (stroke.isFillEnabled && stroke.points.size >= 3) {

                        stroke.fillPath?.rewind()

                        stroke.fillPath?.set(newPath)

                    }

                    

                    redrawAllCache()

                    invalidate()

                }

            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {

                if (isDraggingGrip && activeDraggedStroke != null) {

                    val stroke = activeDraggedStroke!!

                    val origPts = originalStrokePoints

                    

                    if (origPts != null) {

                        val newPoints = stroke.points.map { it.copy() }

                        

                        // Temporarily restore original points so undo/redo command acts properly

                        val restoredPts = stroke.points.toMutableList()

                        for (i in restoredPts.indices) {

                            restoredPts[i] = restoredPts[i].copy(x = origPts[i].x, y = origPts[i].y)

                        }

                        stroke.points = restoredPts

                        stroke.path.rewind()

                        stroke.path.set(com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(stroke.strokeType, stroke.points))

                        if (stroke.isFillEnabled && stroke.points.size >= 3) {

                            stroke.fillPath?.rewind()

                            stroke.fillPath?.set(stroke.path)

                        }

                        

                        onGripEditCompleted?.invoke(stroke, origPts, newPoints)

                    }

                }

                isDraggingGrip = false

                activeDraggedStroke = null

                activeDraggedPointIndex = -1

                originalStrokePoints = null

                currentSnapPoint = null

                invalidate()

            }

        }

        return true

    }



    fun finishGeometricStroke() {

        strokePipeline.forceFinishGeometric()

        currentSnapPoint = null

        hoverWorldPoint = null

        invalidate()

    }



    fun cancelGeometricStroke() {

        strokePipeline.reset()

        currentSnapPoint = null

        hoverWorldPoint = null

        invalidate()

    }



    fun undoLastGeometricPoint() {

        strokePipeline.undoLastPoint()

        currentSnapPoint = null

        hoverWorldPoint = null

        invalidate()

    }



    // --- CALLBACKS ---

    var onStrokeCompleted: ((VectorStroke) -> Unit)? = null

    var onFillCompleted: ((FillData) -> Unit)? = null

    var onGeometricProgressChanged: ((Boolean) -> Unit)? = null

    var onHybridStrokeCompleted: ((VectorStroke, FillData?) -> Unit)? = null



    /**

     * Callback delegado al ViewModel (o a quien lo conecte) para ejecutar el borrado

     * de forma segura mediante el sistema de comandos.

     *

     * Parámetros: (worldX: Float, worldY: Float, diameterPx: Float) -> Boolean

     * Retorna true si se borró al menos un elemento.

     */

    var onRequestErase: ((Float, Float, Float) -> Boolean)? = null
    var onEraserDragStarted: (() -> Unit)? = null
    var onEraserDragEnded: (() -> Unit)? = null

    private fun handleEraserInput(event: MotionEvent): Boolean {
        // Mapear coordenadas de pantalla → espacio del mundo
        tempTouchPoint[0] = event.x
        tempTouchPoint[1] = event.y
        inverseMatrix.mapPoints(tempTouchPoint)
        val worldX = tempTouchPoint[0]
        val worldY = tempTouchPoint[1]
        val diameterPx = activeSize * 2f

        val radiusWorld = com.sketcher.sketchercompanionv1.utils.UnitUtils.pixelsToProjectUnits(
            diameterPx, currentUnit, scaleConfig.basePixelsPerMillimeter
        ) / 2f

        eraserWorldX = worldX
        eraserWorldY = worldY
        eraserRadiusWorld = radiusWorld

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isEraserActive = true
                onEraserDragStarted?.invoke()
                onRequestErase?.invoke(worldX, worldY, diameterPx)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                isEraserActive = true
                onRequestErase?.invoke(worldX, worldY, diameterPx)
                invalidate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isEraserActive = false
                onEraserDragEnded?.invoke()
                invalidate()
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

                                manager.finalizeSelection(activeContainer, componentLibrary)

                            }

                        }

                    }

                }

                SketcherViewModel.SelectionMode.TRANSFORM_BOX -> {
                    when (event.actionMasked) {
                        MotionEvent.ACTION_DOWN -> {
                            manager.handleTransformDown(worldX, worldY, event.x, event.y, viewMatrix, resources.displayMetrics.density)
                            if (manager.currentDragMode == SelectionManager.DragMode.TRANSLATE) {
                                startSelectionDragWorldX = worldX
                                startSelectionDragWorldY = worldY
                            }
                        }

                        MotionEvent.ACTION_MOVE -> {
                            var targetWorldX = worldX
                            var targetWorldY = worldY
                            if (isOrthoMode && manager.currentDragMode == SelectionManager.DragMode.TRANSLATE) {
                                val dx = kotlin.math.abs(worldX - startSelectionDragWorldX)
                                val dy = kotlin.math.abs(worldY - startSelectionDragWorldY)
                                if (dx > dy) {
                                    targetWorldY = startSelectionDragWorldY
                                } else {
                                    targetWorldX = startSelectionDragWorldX
                                }
                            }
                            manager.handleTransformMove(targetWorldX, targetWorldY)
                        }

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

    private fun resolveTouchSnap(x: Float, y: Float): PointF {
        val pts = floatArrayOf(x, y)
        inverseMatrix.mapPoints(pts)
        val worldX = pts[0]
        val worldY = pts[1]

        val zoom = getMatrixScale(viewMatrix)
        val snapPoints = if (isElementSnappingEnabled) {
            com.sketcher.sketchercompanionv1.managers.SnapEngine.getSnapPoints(layers)
        } else {
            emptyList()
        }
        val resolvedSnap = com.sketcher.sketchercompanionv1.managers.SnapEngine.resolveSnap(worldX, worldY, snapPoints, zoom)
        return if (resolvedSnap != null) {
            PointF(resolvedSnap.point.x, resolvedSnap.point.y)
        } else {
            PointF(worldX, worldY)
        }
    }

    private fun handleMirrorInput(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val snapped = resolveTouchSnap(event.x, event.y)
            if (mirrorStep == 0) {
                mirrorP1.set(snapped.x, snapped.y)
                mirrorP2.set(snapped.x, snapped.y)
                mirrorStep = 1
            } else {
                mirrorP2.set(snapped.x, snapped.y)
                onMirrorRequested?.invoke(PointF(mirrorP1.x, mirrorP1.y), PointF(mirrorP2.x, mirrorP2.y))
                mirrorStep = 0
            }
            invalidate()
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            if (mirrorStep == 1) {
                val snapped = resolveTouchSnap(event.x, event.y)
                mirrorP2.set(snapped.x, snapped.y)
                invalidate()
            }
        }
        return true
    }

    private fun handleMovePtPtInput(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val snapped = resolveTouchSnap(event.x, event.y)
            if (movePtPtStep == 0) {
                movePtPtSrc.set(snapped.x, snapped.y)
                movePtPtTgt.set(snapped.x, snapped.y)
                movePtPtStep = 1
            } else {
                movePtPtTgt.set(snapped.x, snapped.y)
                val dx = movePtPtTgt.x - movePtPtSrc.x
                val dy = movePtPtTgt.y - movePtPtSrc.y
                val matrix = android.graphics.Matrix().apply { postTranslate(dx, dy) }
                onTransformSelectedRequested?.invoke(matrix, "Mover Punto a Punto")
                movePtPtStep = 0
            }
            invalidate()
        } else if (event.actionMasked == MotionEvent.ACTION_MOVE) {
            if (movePtPtStep == 1) {
                val snapped = resolveTouchSnap(event.x, event.y)
                movePtPtTgt.set(snapped.x, snapped.y)
                invalidate()
            }
        }
        return true
    }

    private fun handleAlign2PtInput(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val snapped = resolveTouchSnap(event.x, event.y)
            when (alignStep) {
                0 -> {
                    alignSrc1.set(snapped.x, snapped.y)
                    alignStep = 1
                }
                1 -> {
                    alignTgt1.set(snapped.x, snapped.y)
                    alignStep = 2
                }
                2 -> {
                    alignSrc2.set(snapped.x, snapped.y)
                    alignStep = 3
                }
                3 -> {
                    alignTgt2.set(snapped.x, snapped.y)
                    val v1x = alignSrc2.x - alignSrc1.x
                    val v1y = alignSrc2.y - alignSrc1.y
                    val v2x = alignTgt2.x - alignTgt1.x
                    val v2y = alignTgt2.y - alignTgt1.y
                    val angleRad = kotlin.math.atan2(v2y, v2x) - kotlin.math.atan2(v1y, v1x)
                    val scale = kotlin.math.hypot(v2x, v2y) / kotlin.math.hypot(v1x, v1y).coerceAtLeast(0.001f)
                    val matrix = android.graphics.Matrix().apply {
                        postTranslate(-alignSrc1.x, -alignSrc1.y)
                        postScale(scale, scale)
                        postRotate(Math.toDegrees(angleRad.toDouble()).toFloat())
                        postTranslate(alignTgt1.x, alignTgt1.y)
                    }
                    onTransformSelectedRequested?.invoke(matrix, "Alinear a 2 Puntos")
                    alignStep = 0
                }
            }
            invalidate()
        }
        return true
    }

    private fun handleOffsetInput(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val pts = floatArrayOf(event.x, event.y)
            inverseMatrix.mapPoints(pts)
            val worldX = pts[0]
            val worldY = pts[1]
            
            if (offsetStep == 0) {
                val activeLayer = layers.getOrNull(activeLayerIndex) ?: return true
                val visibleStrokes = activeContainer.filterIsInstance<VectorStroke>()
                var hitStroke: VectorStroke? = null
                val manager = selectionManager ?: return true
                for (stroke in visibleStrokes.asReversed()) {
                    if (manager.isHit(stroke, worldX, worldY, componentLibrary)) {
                        hitStroke = stroke
                        break
                    }
                }
                if (hitStroke != null) {
                    offsetTargetStroke = hitStroke
                    offsetStep = 1
                }
            } else {
                val targetStroke = offsetTargetStroke
                if (targetStroke != null) {
                    var minDistance = Float.MAX_VALUE
                    val targetPts = targetStroke.points
                    if (targetPts.size >= 2) {
                        for (i in 0 until targetPts.size - 1) {
                            val p1 = targetPts[i]
                            val p2 = targetPts[i + 1]
                            val d = com.sketcher.sketchercompanionv1.utils.GeometryUtils.distanceToSegment(PointF(worldX, worldY), PointF(p1.x, p1.y), PointF(p2.x, p2.y))
                            if (d < minDistance) {
                                minDistance = d
                            }
                        }
                    } else if (targetPts.isNotEmpty()) {
                        minDistance = kotlin.math.hypot(worldX - targetPts[0].x, worldY - targetPts[0].y)
                    }
                    onOffsetRequested?.invoke(targetStroke, minDistance.coerceAtLeast(1f), PointF(worldX, worldY))
                }
                offsetStep = 0
                offsetTargetStroke = null
            }
            invalidate()
        }
        return true
    }

    private fun handleFilletInput(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val pts = floatArrayOf(event.x, event.y)
            inverseMatrix.mapPoints(pts)
            val worldX = pts[0]
            val worldY = pts[1]
            
            val activeLayer = layers.getOrNull(activeLayerIndex) ?: return true
            val visibleStrokes = activeContainer.filterIsInstance<VectorStroke>()
            var hitStroke: VectorStroke? = null
            val manager = selectionManager ?: return true
            for (stroke in visibleStrokes.asReversed()) {
                if (stroke.strokeType == StrokeType.LINE && manager.isHit(stroke, worldX, worldY, componentLibrary)) {
                    hitStroke = stroke
                    break
                }
            }
            if (hitStroke != null) {
                if (filletStep == 0) {
                    filletStroke1 = hitStroke
                    filletStep = 1
                } else {
                    val s1 = filletStroke1
                    if (s1 != null && s1 !== hitStroke) {
                        val radius = 30f
                        onFilletRequested?.invoke(s1, hitStroke, radius)
                    }
                    filletStep = 0
                    filletStroke1 = null
                }
            }
            invalidate()
        }
        return true
    }

    private fun handleChamferInput(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val pts = floatArrayOf(event.x, event.y)
            inverseMatrix.mapPoints(pts)
            val worldX = pts[0]
            val worldY = pts[1]
            
            val activeLayer = layers.getOrNull(activeLayerIndex) ?: return true
            val visibleStrokes = activeContainer.filterIsInstance<VectorStroke>()
            var hitStroke: VectorStroke? = null
            val manager = selectionManager ?: return true
            for (stroke in visibleStrokes.asReversed()) {
                if (stroke.strokeType == StrokeType.LINE && manager.isHit(stroke, worldX, worldY, componentLibrary)) {
                    hitStroke = stroke
                    break
                }
            }
            if (hitStroke != null) {
                if (chamferStep == 0) {
                    chamferStroke1 = hitStroke
                    chamferStep = 1
                } else {
                    val s1 = chamferStroke1
                    if (s1 != null && s1 !== hitStroke) {
                        val d1 = 20f
                        val d2 = 20f
                        onChamferRequested?.invoke(s1, hitStroke, d1, d2)
                    }
                    chamferStep = 0
                    chamferStroke1 = null
                }
            }
            invalidate()
        }
        return true
    }

    private fun drawCadVisualGuides(canvas: Canvas) {
        canvas.save()
        canvas.concat(viewMatrix)
        cadGuidePaint.strokeWidth = 2f / getMatrixScale(viewMatrix)
        
        when (currentTool) {
            ToolType.MIRROR -> {
                if (mirrorStep == 1) {
                    canvas.drawLine(mirrorP1.x, mirrorP1.y, mirrorP2.x, mirrorP2.y, cadGuidePaint)
                    canvas.drawCircle(mirrorP1.x, mirrorP1.y, 6f / getMatrixScale(viewMatrix), cadMarkerPaint)
                }
            }
            ToolType.MOVE_PT_PT -> {
                if (movePtPtStep == 1) {
                    canvas.drawLine(movePtPtSrc.x, movePtPtSrc.y, movePtPtTgt.x, movePtPtTgt.y, cadGuidePaint)
                    canvas.drawCircle(movePtPtSrc.x, movePtPtSrc.y, 6f / getMatrixScale(viewMatrix), cadMarkerPaint)
                }
            }
            ToolType.ALIGN_2_PT -> {
                if (alignStep >= 1) {
                    canvas.drawCircle(alignSrc1.x, alignSrc1.y, 6f / getMatrixScale(viewMatrix), cadMarkerPaint)
                }
                if (alignStep >= 2) {
                    canvas.drawCircle(alignTgt1.x, alignTgt1.y, 6f / getMatrixScale(viewMatrix), cadMarkerPaint)
                    canvas.drawLine(alignSrc1.x, alignSrc1.y, alignTgt1.x, alignTgt1.y, cadGuidePaint)
                }
                if (alignStep >= 3) {
                    canvas.drawCircle(alignSrc2.x, alignSrc2.y, 6f / getMatrixScale(viewMatrix), cadMarkerPaint)
                }
                if (alignStep >= 4) {
                    canvas.drawCircle(alignTgt2.x, alignTgt2.y, 6f / getMatrixScale(viewMatrix), cadMarkerPaint)
                    canvas.drawLine(alignSrc2.x, alignSrc2.y, alignTgt2.x, alignTgt2.y, cadGuidePaint)
                }
            }
            ToolType.OFFSET -> {
                if (offsetStep == 1 && offsetTargetStroke != null) {
                    val pts = offsetTargetStroke!!.points
                    if (pts.isNotEmpty()) {
                        canvas.drawCircle(pts[0].x, pts[0].y, 8f / getMatrixScale(viewMatrix), cadMarkerPaint)
                    }
                }
            }
            ToolType.FILLET -> {
                if (filletStep == 1 && filletStroke1 != null) {
                    val pts = filletStroke1!!.points
                    if (pts.isNotEmpty()) {
                        canvas.drawCircle(pts[0].x, pts[0].y, 8f / getMatrixScale(viewMatrix), cadMarkerPaint)
                    }
                }
            }
            ToolType.CHAMFER -> {
                if (chamferStep == 1 && chamferStroke1 != null) {
                    val pts = chamferStroke1!!.points
                    if (pts.isNotEmpty()) {
                        canvas.drawCircle(pts[0].x, pts[0].y, 8f / getMatrixScale(viewMatrix), cadMarkerPaint)
                    }
                }
            }
            else -> {}
        }
        canvas.restore()
    }

}




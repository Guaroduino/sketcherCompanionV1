package com.sketcher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.view.View
import android.view.MotionEvent
import com.sketcher.sketchercompanionv1.dto.*

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

// Layer and FillData are now defined in Layer.kt and LayerElement.kt respectively

class SketcherCanvasView(context: Context) : View(context) {

    // --- TRANSFORMS & STATE ---
    private val viewMatrix = Matrix()
    private val cachedBitmapMatrix = Matrix()
    private var isDrawing: Boolean = false
    
    // SELECTION STATE (Wired from SketcherSurface/ViewModel)
    var selectionManager: SelectionManager? = null
    var isSelectionDragging: Boolean = false
        set(value) {
            field = value
            redrawAllCache() 
        }
    
    // BITMAP CACHING
    private var backingBitmap: android.graphics.Bitmap? = null
    private var backingCanvas: Canvas? = null

    var onSizeChangedCallback: ((Int, Int) -> Unit)? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            backingBitmap?.recycle()
            backingBitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            backingCanvas = Canvas(backingBitmap!!)
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
            currentVectorPreviewColor = activeColor // Sync Color
            
            // Sync Fill State
            currentFillPath = update.fillPath
            if (update.fillColor != 0) currentFillColor = update.fillColor
            else if (update.fillPath == null) currentFillColor = null
            
            isDrawing = (update.previewPath != null || update.previewPoints != null)
            invalidate()
        },
        onStrokeCompleted = { stroke, fill ->
            onHybridStrokeCompleted?.invoke(stroke, fill) ?: run {
                 onStrokeCompleted?.invoke(stroke)
                 fill?.let { onFillCompleted?.invoke(it) }
            }
            redrawAllCache()
            isDrawing = false
        }
    )

    // --- CACHE MANAGEMENT ---
    fun redrawAllCache() {
        val canvas = backingCanvas ?: return
        if (width <= 0 || height <= 0) return
        
        // Ensure bitmap matches dimensions
        if (backingBitmap == null || backingBitmap?.width != width || backingBitmap?.height != height) {
            backingBitmap?.recycle()
            backingBitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
            backingCanvas = Canvas(backingBitmap!!)
        }

        val targetCanvas = backingCanvas ?: return
        
        // Use RenderEngine to redraw everything into the cache
        renderEngine.drawLayers(
            targetCanvas, 
            layers, 
            viewMatrix, 
            componentLibrary, 
            selectionManager, 
            isSelectionDragging
        )
        
        // Snapshot the matrix used for this cache
        cachedBitmapMatrix.set(viewMatrix)
        invalidate()
    }

    fun bakeStroke(stroke: VectorStroke) { redrawAllCache() }
    fun bakeFill(fill: FillData) { redrawAllCache() }
    fun bakeVectorStroke(vStroke: VectorStroke) { redrawAllCache() }

    // --- CONFIGURATION SYNC ---
    var gridConfig: GridConfig = GridConfig()
        set(value) { field = value; renderEngine.gridConfig = value; redrawAllCache() }
    var scaleConfig: ScaleConfig = ScaleConfig()
        set(value) { field = value; renderEngine.scaleConfig = value; redrawAllCache() }
    var currentUnit: DistanceUnit = DistanceUnit.M
        set(value) { field = value; renderEngine.currentUnit = value; redrawAllCache() }
    var canvasSizeConfig: CanvasSizeConfig? = null
        set(value) { field = value; renderEngine.canvasSizeConfig = value; redrawAllCache() }
    var canvasBackgroundColor: Int = android.graphics.Color.WHITE
        set(value) { field = value; renderEngine.canvasBackgroundColor = value; redrawAllCache() }
    
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
    var activeColor: Int = android.graphics.Color.BLACK
        set(value) { field = value; strokePipeline.activeColor = value }
    var activeSize: Float = 10f
        set(value) { field = value; strokePipeline.activeSize = value }
    var activeFreehandSettings: FreehandSettings = FreehandSettings()
        set(value) { field = value; strokePipeline.activeFreehandSettings = value }
    var isFillModeEnabled: Boolean = false
        set(value) { field = value; strokePipeline.isFillModeEnabled = value }
    var fillModeColor: Int = android.graphics.Color.TRANSPARENT
        set(value) { field = value; strokePipeline.fillModeColor = value }
    var isFingerMode: Boolean = false
        set(value) { field = value; strokePipeline.isFingerMode = value }
    var fingerOffsetX: Float = 0f
        set(value) { field = value; strokePipeline.fingerOffsetX = value }
    var fingerOffsetY: Float = 50f
        set(value) { field = value; strokePipeline.fingerOffsetY = value }
    var globalStabilizationLevel: Float = 0f
        set(value) { field = value; strokePipeline.globalStabilizationLevel = value }
    var snapFunction: ((Float, Float) -> Pair<Float, Float>)? = null
        set(value) { field = value; strokePipeline.snapFunction = value }
    var currentTool: ToolType = ToolType.FREEHAND
    
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
                    is ImageElement -> element.getBounds(componentLibrary).contains(worldX, worldY)
                    is SvgElement -> element.getBounds(componentLibrary).contains(worldX, worldY)
                    is ComponentInstance -> element.getBounds(componentLibrary).contains(worldX, worldY)
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
        if (isIntermediate) {
            invalidate() 
        } else {
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
        
        // 1. Draw Cached Bitmap (Background & Layers)
        backingBitmap?.let { bitmap ->
            if (viewMatrix == cachedBitmapMatrix) {
                canvas.drawBitmap(bitmap, 0f, 0f, null)
            } else {
                val transform = Matrix()
                if (cachedBitmapMatrix.invert(transform)) {
                     transform.postConcat(viewMatrix)
                     canvas.save()
                     canvas.concat(transform)
                     canvas.drawBitmap(bitmap, 0f, 0f, null)
                     canvas.restore()
                } else {
                     canvas.drawBitmap(bitmap, 0f, 0f, null)
                }
            }
        } ?: run {
             canvas.drawColor(canvasBackgroundColor)
        }

        // 2. Draw Live Content (Stroke & Fill)
        renderEngine.drawLiveStroke(
            canvas, 
            currentVectorPreviewPoints, 
            currentVectorPreviewPath,
            currentVectorPreviewColor,
            currentLiveGeneratedRadius,
            viewMatrix,
            isDrawing
        )
        
        // Live Fill
        if (currentFillPath != null && currentFillColor != null) {
            canvas.save()
            canvas.concat(viewMatrix)
            renderEngine.drawFill(canvas, FillData(currentFillPath!!, currentFillColor!!))
            canvas.restore()
        }

        // 3. Selection Overlays
        if (isSelectionDragging) {
             selectionManager?.let { manager ->
                 canvas.save()
                 canvas.concat(viewMatrix)
                 manager.activeTransform?.let { canvas.concat(it) }
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
        
        onDrawAction?.invoke()
        onDrawAction = null
    }

    var onDrawAction: (() -> Unit)? = null

    // --- INPUT GESTURES ---

    override fun onTouchEvent(event: MotionEvent): Boolean {
        strokePipeline.canvasViewMatrix.set(viewMatrix)
        
        // Calculate current zoom factor
        val mValues = FloatArray(9)
        viewMatrix.getValues(mValues)
        val zoom = kotlin.math.sqrt(
            mValues[android.graphics.Matrix.MSCALE_X] * mValues[android.graphics.Matrix.MSCALE_X] + 
            mValues[android.graphics.Matrix.MSKEW_X] * mValues[android.graphics.Matrix.MSKEW_X]
        )
        strokePipeline.currentZoom = zoom
        
        return when (currentTool) {
            ToolType.SELECTION -> false // Handled by Surface
            ToolType.ERASER -> false    // Handled by Surface
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

}


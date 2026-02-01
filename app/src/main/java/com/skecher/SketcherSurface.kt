package com.skecher.sketchercompanionv1

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Save // Save Icon
import androidx.compose.material.icons.filled.FolderOpen // Load Icon
import androidx.compose.material.icons.filled.FormatPaint // Fill Toggle Icon
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette // Background Color Icon
import androidx.compose.material.icons.filled.Straighten // Scale Icon
import com.skecher.sketchercompanionv1.ui.ColorPickerDialog
import com.skecher.sketchercompanionv1.ui.ScaleIndicator
import com.skecher.sketchercompanionv1.dto.ScaleConfig
import com.skecher.sketchercompanionv1.dto.GridConfig
import com.skecher.sketchercompanionv1.dto.DistanceUnit
import com.skecher.sketchercompanionv1.utils.UnitUtils
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Grid4x4
import kotlin.math.round
import kotlin.math.roundToInt


enum class ToolType { TECHNICAL_PEN, PRESSURE_PEN, MARKER, HIGHLIGHTER, FILL_SHAPE, POLYGON_SWEEPER, ERASER }

data class BrushTypeConfig(
    val type: ToolType,
    val icon: ImageVector,
    val family: BrushFamily?
)

private class RuntimeState {
    var toolType: ToolType = ToolType.TECHNICAL_PEN
    var brushFamily: BrushFamily? = StockBrushes.pressurePen()
    var color: Int = AndroidColor.BLACK
    var size: Float = 15f
    var opacity: Float = 1f
    
    // FILL Path
    val fillPath = android.graphics.Path()
    
    // VECTOR Points
    val vectorPoints = mutableListOf<StrokePoint>()
    
    // Velocity/Prediction Tracking
    var lastScreenX: Float = 0f
    var lastScreenY: Float = 0f
    var lastEventTime: Long = 0
    var smoothedVelocityX: Float = 0f
    var smoothedVelocityY: Float = 0f

    // Eager initialization to fallback if update() is delayed
    var activeBrush: Brush? = Brush.createWithColorLong(
        family = StockBrushes.pressurePen(),
        colorLong = AndroidColor.pack(AndroidColor.BLACK),
        size = 15f,
        epsilon = 0.1f
    )

    fun updateActiveBrush(currentZoom: Float) {
        if (toolType != ToolType.ERASER && toolType != ToolType.FILL_SHAPE && brushFamily != null) {
            val visualSize = size * currentZoom
            
            // Mix Opacity
            val baseColor = if (toolType == ToolType.HIGHLIGHTER) (color and 0x00FFFFFF) or 0x40000000 else color
            val alpha = (AndroidColor.alpha(baseColor) * opacity).toInt()
            val finalColor = (baseColor and 0x00FFFFFF) or (alpha shl 24)

            activeBrush = Brush.createWithColorLong(
                family = brushFamily!!,
                colorLong = AndroidColor.pack(finalColor),
                size = visualSize,
                epsilon = 0.1f
            )
        } else {
            activeBrush = null
        }
    }
}

fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}



@SuppressLint("ClickableViewAccessibility", "SourceLockedOrientationActivity")
@Composable
fun SketcherSurface(
    sketchViewModel: SketcherViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var canvasViewRef by remember { mutableStateOf<SketcherCanvasView?>(null) }

    // --- SAVE / LOAD HANDLERS (SAF) ---
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let {
            try {
                val json = sketchViewModel.getProjectJson()
                context.contentResolver.openOutputStream(it)?.use { output ->
                    output.write(json.toByteArray())
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { input ->
                    val json = input.bufferedReader().use { reader -> reader.readText() }
                    sketchViewModel.loadProjectFromJson(json)
                    // Update View and Redraw Cache
                    canvasViewRef?.setLayers(sketchViewModel.layers)
                    canvasViewRef?.redrawAllCache()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    // Detectamos cambio de configuración (rotación) automáticamente con Compose
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    // UI STATE
    var selectedTool by rememberSaveable { mutableStateOf(ToolType.TECHNICAL_PEN) }
    
    // Color Slots
    val defaultColors = listOf(AndroidColor.BLACK, AndroidColor.RED, AndroidColor.BLUE)
    val colorSlots = remember { mutableStateListOf(*defaultColors.toTypedArray()) }
    var selectedColorSlotIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedColor = colorSlots[selectedColorSlotIndex]
    
    var selectedSize by rememberSaveable { mutableStateOf(7f) }
    var selectedOpacity by rememberSaveable { mutableFloatStateOf(1f) }
    var stabilizationLevel by rememberSaveable { mutableStateOf(0f) }
    
    var showColorPicker by remember { mutableStateOf(false) }
    var showFillColorPicker by remember { mutableStateOf(false) } // Second picker for fill
    var showToolPopup by remember { mutableStateOf(false) }
    var showSizePopup by remember { mutableStateOf(false) }
    var showSettingsPopup by remember { mutableStateOf(false) }
    var showLayerManager by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) } // Background Picker State
    var showGridSettings by remember { mutableStateOf(false) }
    var isSnapEnabled by remember { mutableStateOf(false) }
    
    // FILL MODE STATE
    var isFillModeEnabled by rememberSaveable { mutableStateOf(false) }
    var fillModeColor by rememberSaveable { mutableStateOf(AndroidColor.YELLOW) } // Default Yellow

    // PRESSURE SENSITIVITY
    var pressureSensitivity by rememberSaveable { mutableFloatStateOf(0.6f) }
    
    // SIMPLIFICATION TOLERANCE (for RDP algorithm)
    var simplificationTolerance by rememberSaveable { mutableFloatStateOf(1.5f) }  // Default: balanced quality/performance
    
    // PRESSURE TOLERANCE (for RDP algorithm)
    var pressureTolerance by rememberSaveable { mutableFloatStateOf(0.05f) }  // Default: ~5% pressure change
    
    // Rotation Lock Effect
    LaunchedEffect(sketchViewModel.isRotationLocked) {
        activity?.requestedOrientation = if (sketchViewModel.isRotationLocked) {
             ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
             ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // LAYER CONSTRAINT: Disable Fill for Ink Tools
    LaunchedEffect(selectedTool) {
        val isInkTool = selectedTool == ToolType.MARKER || 
                        selectedTool == ToolType.HIGHLIGHTER || 
                        selectedTool == ToolType.PRESSURE_PEN
                        
        if (isInkTool && isFillModeEnabled) {
             isFillModeEnabled = false
        }
    }

    // MATRICES (Persistentes en ViewModel)
    val cameraMatrix = remember { Matrix().apply { setValues(sketchViewModel.cameraMatrixValues) } }
    val inverseMatrix = remember { Matrix().apply { 
        val temp = Matrix()
        temp.setValues(sketchViewModel.cameraMatrixValues)
        temp.invert(this)
    }}

    // ZOOM STATE (Exposed for UI Overlays, initialized from Material)
    // Must be defined AFTER cameraMatrix
    var currentZoom by remember { mutableFloatStateOf(InkUtils.getMatrixScale(cameraMatrix)) }

    val brushTypes = listOf(
        BrushTypeConfig(ToolType.TECHNICAL_PEN, Icons.Default.Create, StockBrushes.pressurePen()),
        BrushTypeConfig(ToolType.PRESSURE_PEN, Icons.Default.Brush, StockBrushes.pressurePen()),
        BrushTypeConfig(ToolType.MARKER, Icons.Default.Edit, StockBrushes.marker()),
        BrushTypeConfig(ToolType.HIGHLIGHTER, Icons.Default.Edit, StockBrushes.highlighter()),
        BrushTypeConfig(ToolType.FILL_SHAPE, Icons.Default.FormatPaint, null), // New Fill Tool, no Ink Brush
        BrushTypeConfig(ToolType.POLYGON_SWEEPER, Icons.Default.Grid4x4, null) // Polygon Sweeper Tool
    )

    // --- EFECTO DE RE-CENTRADO (Sin hacks visuales) ---
    LaunchedEffect(screenWidth, screenHeight, canvasViewRef) {
        kotlinx.coroutines.delay(50)
        
        val view = canvasViewRef ?: return@LaunchedEffect
        if (view.width == 0) return@LaunchedEffect

        val currentW = view.width.toFloat()
        val currentH = view.height.toFloat()
        val lastW = sketchViewModel.lastViewportWidth
        val lastH = sketchViewModel.lastViewportHeight

        if (lastW > 0 && currentW > 0 && (lastW != currentW || lastH != currentH)) {
            val deltaX = (currentW - lastW) / 2f
            val deltaY = (currentH - lastH) / 2f
            
            cameraMatrix.postTranslate(deltaX, deltaY)
            cameraMatrix.invert(inverseMatrix)
            sketchViewModel.saveCameraState(cameraMatrix)
            currentZoom = InkUtils.getMatrixScale(cameraMatrix) // Update Zoom
            view.setCameraMatrix(cameraMatrix)
        }
        
        sketchViewModel.saveDimensions(currentW, currentH)
        view.invalidate()
    }

    // --- WAKEUP FIX: Force initial update after composition ---
    var isReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(100)
        isReady = true // Force a recomposition/update
    }

    LaunchedEffect(sketchViewModel.isDebugWireframe) {
        canvasViewRef?.isDebugWireframeByVM = sketchViewModel.isDebugWireframe
    }

    // --- FIX: STARTUP AWAKENER REMOVED (Replaced by OnLayoutChangeListener in Factory) ---

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val container = FrameLayout(ctx)
                val params = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 
                    ViewGroup.LayoutParams.MATCH_PARENT
                )

                val canvasView = SketcherCanvasView(ctx).apply {
                    layoutParams = params
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                    
                    // Set initial background color
                    canvasBackgroundColor = sketchViewModel.backgroundColor

                    // STARTUP AWAKENER: Native Layout Listener
                    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            v: View?,
                            left: Int, top: Int, right: Int, bottom: Int,
                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                        ) {
                            if ((right - left) > 0 && (bottom - top) > 0) {
                                (v as? SketcherCanvasView)?.let { cv ->
                                    cv.setLayers(sketchViewModel.layers)
                                    cv.invalidate()
                                }
                                removeOnLayoutChangeListener(this)
                            }
                        }
                    })
                }
                canvasViewRef = canvasView
                
                canvasView.setLayers(sketchViewModel.layers)
                canvasView.setCameraMatrix(cameraMatrix)

                val wetView = InProgressStrokesView(ctx).apply {
                    layoutParams = params
                    setBackgroundColor(AndroidColor.TRANSPARENT)

                    // STARTUP AWAKENER for Wet View
                    addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
                        override fun onLayoutChange(
                            v: View?,
                            left: Int, top: Int, right: Int, bottom: Int,
                            oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
                        ) {
                            if ((right - left) > 0 && (bottom - top) > 0) {
                                (v as? InProgressStrokesView)?.invalidate()
                                removeOnLayoutChangeListener(this)
                            }
                        }
                    })
                    
                    val initialState = RuntimeState().apply {
                        toolType = selectedTool
                        color = selectedColor
                        size = selectedSize
                        opacity = selectedOpacity
                        
                         val currentConfig = brushTypes.find { it.type == selectedTool } ?: brushTypes.first()
                         brushFamily = currentConfig.family
 
                         val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                         updateActiveBrush(currentZoom)
                    }
                    tag = initialState
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }

                container.addView(canvasView)
                container.addView(wetView)

                // --- GESTOS ---
                val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val currentMatrixScale = InkUtils.getMatrixScale(cameraMatrix)
                        val projectedZoom = currentMatrixScale * detector.scaleFactor
                        
                        // CLAMP ZOOM: 20% to 300%
                        val clampedZoom = projectedZoom.coerceIn(0.2f, 3.0f)
                        val effectiveScaleFactor = clampedZoom / currentMatrixScale
                        
                        cameraMatrix.postScale(effectiveScaleFactor, effectiveScaleFactor, detector.focusX, detector.focusY)
                        canvasView.setCameraMatrix(cameraMatrix)
                        cameraMatrix.invert(inverseMatrix)
                        sketchViewModel.saveCameraState(cameraMatrix)
                        
                        // UPDATE STATE
                        // We must update the outer 'currentZoom' state variable (which is a MutableFloatState)
                        currentZoom = clampedZoom
                        
                        // Update Brush Size dynamically
                        (wetView.tag as? RuntimeState)?.updateActiveBrush(clampedZoom)

                        return true
                    }
                })

                val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        return true // Essential for detecting scroll/pan
                    }

                    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                        if (e2.pointerCount >= 2) {
                            cameraMatrix.postTranslate(-dX, -dY)
                            canvasView.setCameraMatrix(cameraMatrix)
                            cameraMatrix.invert(inverseMatrix)
                            sketchViewModel.saveCameraState(cameraMatrix)
                            return true
                        }
                        return false
                    }
                })

                val stabilizer = StrokeStabilizer()
                val strokeIdMap = mutableMapOf<Int, InProgressStrokeId>()
                
                // INPUT FILTERING STATE
                var lastInputX = 0f
                var lastInputY = 0f
                var lastInputPressure = 0f

                wetView.setOnTouchListener { v, event ->
                    // 1. ALWAYS Process Gestures First (Zoom/Pan)
                    // We dispatch to detectors regardless of tool type so fingers can zoom/pan
                    // even if Palm Rejection is ON (which only blocks drawing).
                    scaleDetector.onTouchEvent(event)
                    gestureDetector.onTouchEvent(event)
                    
                    // 2. PALM REJECTION CHECK (Blocks Tool Usage only)
                    // If Palm Rejection enabled AND not using a Stylus -> Block drawing
                    if (sketchViewModel.isPalmRejectionEnabled && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
                         return@setOnTouchListener true // Consume event so it doesn't propagate, but don't draw
                    }
                    
                    val state = v.tag as RuntimeState

                    // ... (Proceed)
                    
                    return@setOnTouchListener false // Let it process
                } // End of onTouchListener setup (Wait, we returned early above?)
                
                // Let's attach the Listener properly outside.
                
                // Listener logic moved to the bottom of the factory block to ensure single registration
                // and proper closure scope access.


                // Restore Touch Listener
                wetView.setOnTouchListener { v, event ->
                    // ... (rest of touch listener logic)
                    scaleDetector.onTouchEvent(event)
                    gestureDetector.onTouchEvent(event)
                    
                    // BOTTOM DEAD ZONE ...
                    val density = context.resources.displayMetrics.density
                    val deadZonePx = 40 * density
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y > (v.height - deadZonePx)) {
                         return@setOnTouchListener false
                    }
                    
                    // ... (rest of the file remains same until Eraser loop)
                    
                    // ...


                    // 2. PALM REJECTION CHECK (Blocks Tool Usage only)
                    // If Palm Rejection enabled AND not using a Stylus -> Block drawing
                    if (sketchViewModel.isPalmRejectionEnabled && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
                         return@setOnTouchListener true // Consume event so it doesn't propagate, but don't draw
                    }

                    val state = v.tag as RuntimeState
                    val action = event.actionMasked
                    
                    // --- HERRAMIENTAS ---
                    // --- HERRAMIENTAS ---
                    if (state.toolType == ToolType.ERASER) {
                        // OBJECT ERASER LOGIC
                        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                             if (event.pointerCount == 1) {
                                val touchPts = floatArrayOf(event.x, event.y)
                                inverseMatrix.mapPoints(touchPts)
                                val worldX = touchPts[0]
                                val worldY = touchPts[1]
                                
                                var needsRedraw = false
                                
                                // 1. Check Vector Strokes (Reversed to delete top-most first, optional)
                                val iterator = sketchViewModel.layers[sketchViewModel.activeLayerIndex].vectorStrokes.iterator()
                                val strokesToRemove = mutableListOf<VectorStroke>()
                                
                                // We iterate a COPY or use separate list to avoid ConcurrentModification if we remove directly in loop
                                // But here we are on UI thread, accessing list.
                                // Safe approach: Collect then remove.
                                // Actually, simpler: Iterate indices reversed? Or simple iterator remove?
                                // VectorStroke is in 'layer.vectorStrokes' which is a MutableList.
                                
                                // Let's iterate the ViewModel's active layer directly
                                val activeLayer = sketchViewModel.layers.getOrNull(sketchViewModel.activeLayerIndex)
                                if (activeLayer != null) {
                                     val vIter = activeLayer.vectorStrokes.iterator()
                                     while (vIter.hasNext()) {
                                         val stroke = vIter.next()
                                         if (CollisionUtils.isTouchingStroke(stroke.points, worldX, worldY)) {
                                             vIter.remove()
                                             needsRedraw = true
                                             // Break after one? Or erase all under finger?
                                             // "that entire stroke should be removed" - implies the one touched.
                                             // Usually iterating all is fine.
                                         }
                                     }
                                     
                                     // 2. Check Ink Strokes (LIVE VIEW)
                                     val kIter = activeLayer.inkStrokes.iterator()
                                     var inkRemoved = false
                                     while (kIter.hasNext()) {
                                         val stroke = kIter.next()
                                         // Check Collision
                                         if (CollisionUtils.isTouchingStroke(stroke, worldX, worldY)) {
                                             kIter.remove()
                                             inkRemoved = true
                                         }
                                     }
                                     
                                     // If Ink was removed, we must reload the Live View
                                     if (inkRemoved) {
                                         // User Request: "Call wetView.setStrokes(viewModel.inkStrokes)"
                                         // Used extension function defined below
                                         wetView.setStrokes(activeLayer.inkStrokes)
                                     }
                                }

                                if (needsRedraw) {
                                     canvasView.redrawAllCache()
                                }
                                
                                // RELOAD INK (If needed)
                                if (sketchViewModel.layers.getOrNull(sketchViewModel.activeLayerIndex)?.inkStrokes != null) {
                                     // Optimization: Only if ink changed.
                                     // ...
                                }
                             }
                        }
                    } else {
                        // MAIN DRAWING LOGIC (Vector + Ink)
                        // Allow entry if we have a BrushFamily (Ink) OR if it is a known Vector Tool
                        val isVectorTool = state.toolType == ToolType.TECHNICAL_PEN || 
                                           state.toolType == ToolType.FILL_SHAPE || 
                                           state.toolType == ToolType.POLYGON_SWEEPER
                                           
                        if (state.brushFamily != null || isVectorTool) {
                            if (event.pointerCount == 1) {
                                val pid = event.getPointerId(0)
                                
                                val rawTouchPts = floatArrayOf(event.x, event.y)
                                inverseMatrix.mapPoints(rawTouchPts)
                                val worldXRaw = rawTouchPts[0]
                                val worldYRaw = rawTouchPts[1]
    
                                // --- SNAP TO GRID LOGIC (IMMEDIATE) ---
                                // Check snap BEFORE processing any stroke/stabilizer logic
                                var effectiveX = worldXRaw
                                var effectiveY = worldYRaw
    
                                if (isSnapEnabled) {
                                    val gridStepPx = UnitUtils.projectUnitsToPixels(
                                        value = sketchViewModel.gridConfig.spacing,
                                        unit = sketchViewModel.currentUnit,
                                        basePxPerMm = sketchViewModel.scaleConfig.basePixelsPerMillimeter
                                    )
    
                                    if (gridStepPx > 0) {
                                        effectiveX = (kotlin.math.round(worldXRaw / gridStepPx) * gridStepPx)
                                        effectiveY = (kotlin.math.round(worldYRaw / gridStepPx) * gridStepPx)
                                    }
                                }
                            
                                when (action) {
                                    MotionEvent.ACTION_DOWN -> {
                                        // FORCE WAKEUP
                                        v.invalidate()
    
                                        // Reset Filtering
                                        lastInputX = effectiveX
                                        lastInputY = effectiveY
                                        lastInputPressure = event.pressure
                                        
                                        state.lastScreenX = event.x
                                        state.lastScreenY = event.y
                                        state.lastEventTime = event.eventTime
                                        state.smoothedVelocityX = 0f
                                        state.smoothedVelocityY = 0f
    
                                        // 1. Reset Stabilizer with EFFECTIVE (Snapped) Coordinates
                                        stabilizer.reset(effectiveX, effectiveY)
                                        
                                        // --- VECTOR TOOL INITIALIZATION ---
                                        if (isVectorTool) {
                                            state.vectorPoints.clear()
                                            
                                            var pressure = event.pressure
                                            pressure = adjustPressure(pressure, pressureSensitivity)
                                            
                                            // effectiveX/Y are ALREADY in World Space
                                            state.vectorPoints.add(StrokePoint(effectiveX, effectiveY, pressure))
                                            
                                            // Vector Preview (Tech Pen / Polygon Sweeper)
                                            if (state.toolType != ToolType.FILL_SHAPE) {
                                                // Generate preview...
                                                val (path, _, _) = if (state.toolType == ToolType.POLYGON_SWEEPER) {
                                                    PathGenerator.generateOrganicFillPath(state.vectorPoints, state.size, sketchViewModel.penMinSizeFactor)
                                                } else {
                                                    PathGenerator.generateStrokePath(state.vectorPoints, state.size, sketchViewModel.penMinSizeFactor)
                                                }
                                                val alpha = (state.opacity * 255).toInt()
                                                val colorWithAlpha = androidx.core.graphics.ColorUtils.setAlphaComponent(state.color, alpha)
                                                canvasView.updateCurrentVectorPreview(path, state.vectorPoints.toList(), colorWithAlpha, state.size, sketchViewModel.penMinSizeFactor)
                                            }
            
                                            // Fill Path Start (For Tech Pen OR Fill Tool)
                                            if (isFillModeEnabled || state.toolType == ToolType.FILL_SHAPE) {
                                                state.fillPath.reset()
                                                state.fillPath.moveTo(effectiveX, effectiveY)
                                                canvasView.updateCurrentFill(state.fillPath, if(state.toolType == ToolType.FILL_SHAPE) state.color else fillModeColor)
                                            }
                                        }
                                        
                                        // --- INK TOOL INITIALIZATION ---
                                        // Only run Ink logic if we actually have an active Ink Brush
                                        // Markers and Highlighters usually fall here.
                                        if (state.activeBrush != null && !isVectorTool) { 
                                            // Synthesize Event for Ink Engine (Needs Screen Coordinates)
                                            val snapScreenPts = floatArrayOf(effectiveX, effectiveY)
                                            cameraMatrix.mapPoints(snapScreenPts)
                                            
                                            val pointerIndex = 0
                                            val props = arrayOf(MotionEvent.PointerProperties())
                                            props[0] = MotionEvent.PointerProperties()
                                            event.getPointerProperties(pointerIndex, props[0])
                                            
                                            val coords = arrayOf(MotionEvent.PointerCoords())
                                            coords[0] = MotionEvent.PointerCoords()
                                            event.getPointerCoords(pointerIndex, coords[0])
                                            
                                            // Overwrite with Snapped Screen Coords
                                            coords[0].x = snapScreenPts[0]
                                            coords[0].y = snapScreenPts[1]
    
                                            if (state.toolType == ToolType.PRESSURE_PEN) {
                                                coords[0].pressure = adjustPressure(coords[0].pressure, pressureSensitivity)
                                            }
                                            
                                            val snappedEvent = MotionEvent.obtain(
                                                event.downTime, event.eventTime, event.action, 1, props, coords,
                                                event.metaState, event.buttonState, event.xPrecision, event.yPrecision,
                                                event.deviceId, event.edgeFlags, event.source, event.flags
                                            )
    
                                            try {
                                                state.activeBrush?.let { brush ->
                                                    strokeIdMap[pid] = wetView.startStroke(snappedEvent, pid, brush)
                                                }
                                            } finally {
                                                snappedEvent.recycle()
                                            }
                                            
                                            // Fill Path Start (Secondary Fill for Ink)
                                            if (isFillModeEnabled) {
                                                state.fillPath.reset()
                                                state.fillPath.moveTo(effectiveX, effectiveY)
                                                canvasView.updateCurrentFill(state.fillPath, fillModeColor)
                                            }
                                        }
                                    }
                                    MotionEvent.ACTION_MOVE -> {
                                        // INPUT FILTERING
                                        val dist = kotlin.math.hypot(effectiveX - lastInputX, effectiveY - lastInputY)
                                        val pressureDelta = kotlin.math.abs(event.pressure - lastInputPressure)
    
                                        if (dist > 2.0f || pressureDelta > 0.1f) {
                                            lastInputX = effectiveX
                                            lastInputY = effectiveY
                                            lastInputPressure = event.pressure
                                            
                                            val stabilizedPoint = stabilizer.update(effectiveX, effectiveY, stabilizationLevel)
                                            val stabWorldX = stabilizedPoint.x
                                            val stabWorldY = stabilizedPoint.y
                                            
                                            // --- VECTOR UPDATE ---
                                            if (isVectorTool) {
                                                // Batch Histroy
                                                val historySize = event.historySize
                                                for (i in 0 until historySize) {
                                                     val hx = event.getHistoricalX(i)
                                                     val hy = event.getHistoricalY(i)
                                                     val hPressure = event.getHistoricalPressure(i)
                                                     val hTouchPts = floatArrayOf(hx, hy)
                                                     inverseMatrix.mapPoints(hTouchPts)
                                                     var hWorldX = hTouchPts[0]
                                                     var hWorldY = hTouchPts[1]
                                                     
                                                     if (isSnapEnabled) {
                                                         val gridStepPx = UnitUtils.projectUnitsToPixels(sketchViewModel.gridConfig.spacing, sketchViewModel.currentUnit, sketchViewModel.scaleConfig.basePixelsPerMillimeter)
                                                         if (gridStepPx > 0) {
                                                             hWorldX = (kotlin.math.round(hWorldX / gridStepPx) * gridStepPx)
                                                             hWorldY = (kotlin.math.round(hWorldY / gridStepPx) * gridStepPx)
                                                         }
                                                     }
                                                     val hStabilized = stabilizer.update(hWorldX, hWorldY, stabilizationLevel)
                                                     var hPressureAdjusted = adjustPressure(hPressure, pressureSensitivity)
                                                     
                                                      state.vectorPoints.add(StrokePoint(hStabilized.x, hStabilized.y, hPressureAdjusted))
                                                }
                                                
                                                // Current Point
                                                var pressure = event.pressure
                                                pressure = adjustPressure(pressure, pressureSensitivity)
                                                state.vectorPoints.add(StrokePoint(stabWorldX, stabWorldY, pressure))
                                                
                                                // Fill Path Update
                                                if (isFillModeEnabled || state.toolType == ToolType.FILL_SHAPE) {
                                                    state.fillPath.lineTo(stabWorldX, stabWorldY)
                                                    val previewPath = android.graphics.Path(state.fillPath)
                                                    previewPath.close()
                                                    canvasView.updateCurrentFill(previewPath, if(state.toolType == ToolType.FILL_SHAPE) state.color else fillModeColor)
                                                }
                                                
                                                // Vector Prediction & Preview
                                                val dt = (event.eventTime - state.lastEventTime).toFloat().coerceAtLeast(1f)
                                                val rawVelX = (event.x - state.lastScreenX) / dt
                                                val rawVelY = (event.y - state.lastScreenY) / dt
                                                val smoothFactor = sketchViewModel.predictionSmoothing
                                                state.smoothedVelocityX = (state.smoothedVelocityX * smoothFactor) + (rawVelX * (1f - smoothFactor))
                                                state.smoothedVelocityY = (state.smoothedVelocityY * smoothFactor) + (rawVelY * (1f - smoothFactor))
                                                state.lastScreenX = event.x
                                                state.lastScreenY = event.y
                                                state.lastEventTime = event.eventTime
                                                
                                                var predWorldX = stabWorldX
                                                var predWorldY = stabWorldY
                                                
                                                 if (sketchViewModel.isPredictionEnabled) {
                                                    val velocityMag = kotlin.math.hypot(state.smoothedVelocityX, state.smoothedVelocityY)
                                                    val minLag = 15f
                                                    val maxLag = sketchViewModel.predictionLagMs
                                                    val minVel = sketchViewModel.predictionVelocityMin
                                                    val maxVel = sketchViewModel.predictionVelocityMax
                                                    val range = (maxVel - minVel).coerceAtLeast(1f)
                                                    val velocityT = ((velocityMag - minVel) / range).coerceIn(0f, 1f)
                                                    val predictionLagMs = minLag + (maxLag - minLag) * velocityT
                                                    val predScreenX = event.x + (state.smoothedVelocityX * predictionLagMs)
                                                    val predScreenY = event.y + (state.smoothedVelocityY * predictionLagMs)
                                                    val predWorldPts = floatArrayOf(predScreenX, predScreenY)
                                                    inverseMatrix.mapPoints(predWorldPts)
                                                    predWorldX = predWorldPts[0]
                                                    predWorldY = predWorldPts[1]
                                                 }
                                                 val predictedPoint = android.graphics.PointF(predWorldX, predWorldY)
                                                
                                                if (state.toolType != ToolType.FILL_SHAPE) {
                                                     val (path, _, _) = if (state.toolType == ToolType.POLYGON_SWEEPER) {
                                                         PathGenerator.generateOrganicFillPath(state.vectorPoints, state.size, sketchViewModel.penMinSizeFactor)
                                                     } else {
                                                         PathGenerator.generateStrokePath(state.vectorPoints, state.size, sketchViewModel.penMinSizeFactor)
                                                     }
                                                     val alpha = (state.opacity * 255).toInt()
                                                     val colorWithAlpha = androidx.core.graphics.ColorUtils.setAlphaComponent(state.color, alpha)
                                                     canvasView.updateCurrentVectorPreview(path, state.vectorPoints.toList(), colorWithAlpha, state.size, sketchViewModel.penMinSizeFactor, predictedPoint)
                                                }
    
                                            }
                                            
                                            // --- INK UPDATE ---
                                            if (state.activeBrush != null && !isVectorTool) {
                                                 val stabScreenPts = floatArrayOf(stabWorldX, stabWorldY)
                                                 cameraMatrix.mapPoints(stabScreenPts)
                                                 
                                                 val pointerIndex = 0
                                                 val props = arrayOf(MotionEvent.PointerProperties())
                                                 props[0] = MotionEvent.PointerProperties()
                                                 event.getPointerProperties(pointerIndex, props[0])
                                                 val coords = arrayOf(MotionEvent.PointerCoords())
                                                 coords[0] = MotionEvent.PointerCoords()
                                                 event.getPointerCoords(pointerIndex, coords[0])
                                                 coords[0].x = stabScreenPts[0]
                                                 coords[0].y = stabScreenPts[1]
                                                 
                                                  if (state.toolType == ToolType.PRESSURE_PEN) {
                                                     coords[0].pressure = adjustPressure(coords[0].pressure, pressureSensitivity)
                                                 }
                                                 
                                                 val stabilizedEvent = MotionEvent.obtain(
                                                    event.downTime, event.eventTime, event.action, 1, props, coords,
                                                    event.metaState, event.buttonState, event.xPrecision, event.yPrecision,
                                                    event.deviceId, event.edgeFlags, event.source, event.flags
                                                 )
            
                                                 try {
                                                     strokeIdMap[pid]?.let { wetView.addToStroke(stabilizedEvent, pid, it, null) }
                                                 } finally {
                                                     stabilizedEvent.recycle()
                                                 }
                                                 
                                                 // Fill Move
                                                 if (isFillModeEnabled) {
                                                     state.fillPath.lineTo(stabWorldX, stabWorldY)
                                                     val previewPath = android.graphics.Path(state.fillPath)
                                                     previewPath.close()
                                                     canvasView.updateCurrentFill(previewPath, fillModeColor)
                                                 }
                                            }
                                        }
                                    }
                                    MotionEvent.ACTION_UP -> {
                                        val stabilizedPoint = stabilizer.update(effectiveX, effectiveY, stabilizationLevel)
                                        val stabWorldX = stabilizedPoint.x
                                        val stabWorldY = stabilizedPoint.y
                                        
                                        // --- VECTOR COMMIT ---
                                        if (isVectorTool) {
                                            var pressure = event.pressure
                                            pressure = adjustPressure(pressure, pressureSensitivity)
                                            state.vectorPoints.add(StrokePoint(stabWorldX, stabWorldY, pressure))
                                            
                                            // Commit Fill (Fill Tool OR Tech Pen Fill)
                                            if (isFillModeEnabled || state.toolType == ToolType.FILL_SHAPE) {
                                                state.fillPath.lineTo(stabWorldX, stabWorldY)
                                                state.fillPath.close() // Close
                                                val finalFillColor = if(state.toolType == ToolType.FILL_SHAPE) state.color else fillModeColor
                                                val fillData = FillData(android.graphics.Path(state.fillPath), finalFillColor)
                                                sketchViewModel.addFill(fillData)
                                                canvasView.updateCurrentFill(null, 0)
                                                canvasView.bakeFill(fillData)
                                                canvasView.setLayers(sketchViewModel.layers)
                                            }
                                            
                                            // Commit Stroke (Tech Pen / Polygon)
                                            if (state.toolType == ToolType.TECHNICAL_PEN || state.toolType == ToolType.POLYGON_SWEEPER) {
                                                // Simplify Path
                                                val simplifiedPoints = if (simplificationTolerance > 0f) {
                                                    com.skecher.sketchercompanionv1.utils.VectorUtils.simplifyPoints(
                                                        state.vectorPoints,
                                                        zoomScale = InkUtils.getMatrixScale(cameraMatrix),
                                                        cornerThresholdDegrees = sketchViewModel.simplificationAngleThreshold,
                                                        pressureTolerance = pressureTolerance
                                                    )
                                                } else {
                                                    state.vectorPoints
                                                }

                                                 val (path, leftPts, rightPts) = if (state.toolType == ToolType.POLYGON_SWEEPER) {
                                                     PathGenerator.generateOrganicFillPath(simplifiedPoints, state.size, sketchViewModel.penMinSizeFactor)
                                                 } else {
                                                     PathGenerator.generateStrokePath(simplifiedPoints, state.size, sketchViewModel.penMinSizeFactor)
                                                }
                                                
                                                  val alpha = (state.opacity * 255).toInt()
                                                  val colorWithAlpha = androidx.core.graphics.ColorUtils.setAlphaComponent(state.color, alpha)
                                                  
                                                  val stroke = VectorStroke(
                                                      points = simplifiedPoints.toList(),
                                                      color = colorWithAlpha,
                                                      maxWidth = state.size,
                                                      path = path,
                                                      leftPoints = leftPts,
                                                      rightPoints = rightPts
                                                  )
                                                  sketchViewModel.addVectorStroke(stroke)
                                                  canvasView.updateCurrentVectorPreview(null, null, 0) // Clear
                                                  canvasView.bakeStroke(stroke)
                                                  canvasView.setLayers(sketchViewModel.layers)
                                            }
                                        }
                                        
                                        // --- INK COMMIT ---
                                        if (state.activeBrush != null && !isVectorTool) {
                                            val snapScreenPts = floatArrayOf(stabWorldX, stabWorldY)
                                            cameraMatrix.mapPoints(snapScreenPts)
                                            
                                            val pointerIndex = 0
                                            val props = arrayOf(MotionEvent.PointerProperties())
                                            props[0] = MotionEvent.PointerProperties()
                                            event.getPointerProperties(pointerIndex, props[0])
                                            val coords = arrayOf(MotionEvent.PointerCoords())
                                            coords[0] = MotionEvent.PointerCoords()
                                            event.getPointerCoords(pointerIndex, coords[0])
                                            coords[0].x = snapScreenPts[0]
                                            coords[0].y = snapScreenPts[1]
                                            
                                            val snappedEvent = MotionEvent.obtain(
                                                event.downTime, event.eventTime, event.action, 1, props, coords,
                                                event.metaState, event.buttonState, event.xPrecision, event.yPrecision,
                                                event.deviceId, event.edgeFlags, event.source, event.flags
                                            )
                                           
                                            try {
                                                strokeIdMap[pid]?.let { 
                                                    wetView.finishStroke(snappedEvent, pid, it)
                                                    strokeIdMap.remove(pid)
                                                }
                                            } finally {
                                                snappedEvent.recycle()
                                            }
                                            
                                            // Ink Fill Commit
                                            if (isFillModeEnabled) {
                                                state.fillPath.lineTo(stabWorldX, stabWorldY)
                                                state.fillPath.close()
                                                val fillData = FillData(android.graphics.Path(state.fillPath), fillModeColor)
                                                sketchViewModel.addFill(fillData)
                                                canvasView.updateCurrentFill(null, 0)
                                                canvasView.bakeFill(fillData)
                                                canvasView.setLayers(sketchViewModel.layers)
                                            }
                                        }
    
                                    }
                                }
                            }
                        } else if (strokeIdMap.isNotEmpty()) {
                            strokeIdMap.forEach { (_, sid) -> wetView.cancelStroke(sid, event) }
                            strokeIdMap.clear()
                            // Cancel Fill too?
                            if (isFillModeEnabled) {
                                state.fillPath.reset()
                                canvasView.updateCurrentFill(null, 0) // Clear
                            }
                        }
                    }
                    true
                }

                // --- INK LISTENER ---
                wetView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                        // Transform screen-space strokes to world-space
                        // wetView captures at current zoom with scaled brush, so we need to:
                        // 1. Transform coordinates to world-space (inverse matrix)
                        // 2. Scale brush size back to world-space (divide by zoom)
                        val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                        
                        // CRITICAL: Recalculate inverse matrix from CURRENT camera state
                        // The inverseMatrix variable is stale (calculated at startup)
                        val currentInverse = Matrix()
                        if (!cameraMatrix.invert(currentInverse)) {
                            // Matrix is not invertible, skip transformation
                            return
                        }
                        
                        for (entry in strokes) {
                            try {
                                val screenStroke = entry.value
                                
                                // Transform to world coordinates using CURRENT inverse
                                val worldStroke = InkUtils.transformStrokeToWorld(
                                    screenStroke = screenStroke,
                                    inverseMatrix = currentInverse,
                                    currentZoom = currentZoom
                                )
                                
                                worldStroke?.let { 
                                    // Store in world-space (will be rendered with viewMatrix in onDraw)
                                    sketchViewModel.addStroke(it)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                        
                        // CRITICAL: Remove finished strokes from wetView's rendering buffer
                        // This prevents duplication between wetView and our dynamic rendering
                        wetView.removeFinishedStrokes(strokes.keys)
                        
                        // Trigger redraw to show the transformed strokes
                        canvasView.invalidate()
                    }
                })
                container
            },
            update = { view ->
                val container = view as FrameLayout
                val canvasView = container.getChildAt(0) as SketcherCanvasView
                val wetView = container.getChildAt(1) as InProgressStrokesView
                val state = wetView.tag as RuntimeState
                
                // CRITICAL FIX: Ensure layers depend on ViewModel state updates
                canvasView.setLayers(sketchViewModel.layers)

                val currentConfig = brushTypes.find { it.type == selectedTool } ?: brushTypes.first()
                
                state.toolType = selectedTool
                state.brushFamily = currentConfig.family
                state.color = selectedColor
                state.size = selectedSize
                state.opacity = selectedOpacity
                
                val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                state.updateActiveBrush(currentZoom)
                
                canvasView.invalidate()
                wetView.invalidate()
                
                // Update Background Color
                if (canvasView.canvasBackgroundColor != sketchViewModel.backgroundColor) {
                    canvasView.canvasBackgroundColor = sketchViewModel.backgroundColor
                }
                
                // Sync Grid & Units
                canvasView.gridConfig = sketchViewModel.gridConfig
                canvasView.scaleConfig = sketchViewModel.scaleConfig
                canvasView.currentUnit = sketchViewModel.currentUnit
                
                // Sync Debug Flags
                canvasView.isDebugWireframe = sketchViewModel.isDebugWireframe
                canvasView.isDebugPredictionEnabled = sketchViewModel.isDebugPredictionEnabled
            }
        )

        // --- UI LAYER ---
        // We define the custom density here, but ONLY apply it to the BottomMenuBar (Toolbar)
        // so that the toolbar items shrink/fit better, but Dialogs/Popups remain standard
        // to avoid hit-testing or window issues.
        val currentDensity = androidx.compose.ui.platform.LocalDensity.current
        val customDensity = remember(currentDensity, sketchViewModel.interfaceScale) {
            androidx.compose.ui.unit.Density(
                density = currentDensity.density * sketchViewModel.interfaceScale,
                fontScale = currentDensity.fontScale
            )
        }

        CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides customDensity) {
            
            TopMenuBar(
                modifier = Modifier.align(Alignment.TopCenter),
                canUndo = sketchViewModel.canUndo,
                onUndo = { sketchViewModel.undo(); canvasViewRef?.setLayers(sketchViewModel.layers); canvasViewRef?.redrawAllCache() },
                canRedo = sketchViewModel.canRedo,
                onRedo = { sketchViewModel.redo(); canvasViewRef?.setLayers(sketchViewModel.layers); canvasViewRef?.redrawAllCache() },
                onLayersClick = { showLayerManager = !showLayerManager },
                onSave = { saveLauncher.launch("project.json") },
                onLoad = { loadLauncher.launch(arrayOf("application/json")) },
                onNewDrawing = {
                    sketchViewModel.clear()
                    // Reset View Camera
                    cameraMatrix.reset()
                    cameraMatrix.invert(inverseMatrix) // Fix: Update inverse matrix too!
                    canvasViewRef?.setCameraMatrix(cameraMatrix)
                    canvasViewRef?.setLayers(sketchViewModel.layers)
                    canvasViewRef?.redrawAllCache()
                    canvasViewRef?.invalidate()
                    currentZoom = 1f // Reset Zoom State
                },
                onSettingsClick = { showSettingsPopup = true },
                onGridClick = { showGridSettings = true },
                isSnapEnabled = isSnapEnabled,
                onToggleSnap = { isSnapEnabled = !isSnapEnabled },
                activeLayerName = if (sketchViewModel.layers.isNotEmpty() && sketchViewModel.activeLayerIndex in sketchViewModel.layers.indices) {
                    sketchViewModel.layers[sketchViewModel.activeLayerIndex].name
                } else "Layer"
            )
            
            // SCALE INDICATOR (Top Left)
            ScaleIndicator(
                scaleConfig = sketchViewModel.scaleConfig,
                currentUnit = sketchViewModel.currentUnit,
                currentZoom = currentZoom,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 80.dp, start = 16.dp) // Below Toobar
            )

            BottomMenuBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter),
                tools = brushTypes,
                selectedTool = selectedTool,
                onToolSelected = { selectedTool = it },
                colorSlots = colorSlots,
                selectedColorSlotIndex = selectedColorSlotIndex,
                onColorSlotSelected = { selectedColorSlotIndex = it },
                onColorChangeRequest = { showColorPicker = true },
                selectedSize = selectedSize,
                onSizeChangeRequest = { 
                    // Use a small delay or check to prevent immediate re-opening
                    // when clicking the icon to close.
                    showSizePopup = !showSizePopup 
                },
                isEraserActive = selectedTool == ToolType.ERASER,
                onEraserToggle = {
                    selectedTool = if (selectedTool == ToolType.ERASER) ToolType.TECHNICAL_PEN else ToolType.ERASER
                },
                showToolPopup = showToolPopup,
                onShowToolPopupChange = { showToolPopup = it },
                isFillModeEnabled = isFillModeEnabled,
                onToggleFillMode = { if(selectedTool != ToolType.MARKER && selectedTool != ToolType.HIGHLIGHTER && selectedTool != ToolType.PRESSURE_PEN) isFillModeEnabled = !isFillModeEnabled },
                fillColor = fillModeColor,
                onFillColorChangeRequest = { showFillColorPicker = true },
                backgroundColor = sketchViewModel.backgroundColor,
                onBackgroundColorChangeRequest = { showBackgroundColorPicker = true }
            )
        }
        
        if (showBackgroundColorPicker) {
            ColorPickerDialog(
                initialColor = sketchViewModel.backgroundColor,
                onDismiss = { showBackgroundColorPicker = false },
                onColorSelected = { color ->
                    sketchViewModel.backgroundColor = color
                    showBackgroundColorPicker = false
                }
            )
        }
        
        if (showFillColorPicker) {
            ColorPickerDialog(
                initialColor = fillModeColor,
                onDismiss = { showFillColorPicker = false },
                onColorSelected = { color ->
                    fillModeColor = color
                    showFillColorPicker = false
                }
            )
        }
        

        
        if (showGridSettings) {
            GridSettingsDialog(
                currentGridConfig = sketchViewModel.gridConfig,
                currentUnit = sketchViewModel.currentUnit,
                onUpdateGrid = { visible, spacing, color, color2, color3 -> 
                    sketchViewModel.updateGridConfig(visible, spacing, color, color2, color3) 
                },
                onUpdateUnit = { unit -> sketchViewModel.setUnit(unit) },
                onDismiss = { showGridSettings = false }
            )
        }


        // 2. DIALOGS & POPUPS (Standard Density)
        // These are now OUTSIDE the CompositionLocalProvider, so they use the system density.
        if (showLayerManager) {
            LayerManagerDialog(
                layers = sketchViewModel.layers,
                activeLayerIndex = sketchViewModel.activeLayerIndex,
                onToggleVisibility = { sketchViewModel.toggleLayerVisibility(it) },
                onOpacityChanged = { idx, op -> sketchViewModel.setLayerOpacity(idx, op) },
                onActiveLayerChanged = { sketchViewModel.setActiveLayer(it) },
                onAddLayer = { sketchViewModel.addNewLayer(true) }, // Default add to top
                onDeleteLayer = { sketchViewModel.removeActiveLayer() },
                onMoveUp = { sketchViewModel.moveActiveLayerUp() },
                onMoveDown = { sketchViewModel.moveActiveLayerDown() },
                onDismiss = { showLayerManager = false }
            )
        }

        if (showColorPicker) {
            ColorPickerDialog(
                initialColor = colorSlots[selectedColorSlotIndex],
                onDismiss = { showColorPicker = false },
                onColorSelected = { color ->
                    colorSlots[selectedColorSlotIndex] = color
                    showColorPicker = false
                }
            )
        }

        if (showSizePopup) {
            SizeSelectorPopup(
                currentSize = selectedSize,
                onSizeChanged = { selectedSize = it },
                currentOpacity = selectedOpacity,
                onOpacityChanged = { selectedOpacity = it },
                stabilizationLevel = stabilizationLevel,
                onStabilizationLevelChanged = { stabilizationLevel = it },
                pressureSensitivity = pressureSensitivity,
                onPressureSensitivityChanged = { pressureSensitivity = it },
                presets = sketchViewModel.brushSizePresets,
                onPresetSelected = { selectedSize = it },
                onPresetSave = { index, size -> sketchViewModel.updateBrushSizePreset(index, size) },
                penMinSizeFactor = sketchViewModel.penMinSizeFactor,
                onPenMinSizeFactorChanged = { sketchViewModel.penMinSizeFactor = it },
                isVectorPen = (selectedTool == ToolType.TECHNICAL_PEN || selectedTool == ToolType.PRESSURE_PEN),
                isPolygonSweeper = (selectedTool == ToolType.POLYGON_SWEEPER),
                polygonSides = sketchViewModel.polygonSides,
                onPolygonSidesChanged = { sketchViewModel.polygonSides = it },
                polygonRotationSpeed = sketchViewModel.polygonRotationSpeed,
                onPolygonRotationSpeedChanged = { sketchViewModel.polygonRotationSpeed = it },
                isPolygonRandomRotation = sketchViewModel.isPolygonRandomRotation,
                onPolygonRandomRotationChanged = { sketchViewModel.isPolygonRandomRotation = it },
                onDismiss = { showSizePopup = false }
            )
        }

        if (showSettingsPopup) {
           SettingsDialog(
               onDismiss = { showSettingsPopup = false },
               isRotationLocked = sketchViewModel.isRotationLocked,
               onToggleRotationLock = { sketchViewModel.toggleRotationLock() },
               isPalmRejectionEnabled = sketchViewModel.isPalmRejectionEnabled,
               onTogglePalmRejection = { sketchViewModel.togglePalmRejection() },
               interfaceScale = sketchViewModel.interfaceScale,
               onInterfaceScaleChanged = { sketchViewModel.updateInterfaceScale(it) },
               simplificationAngleThreshold = sketchViewModel.simplificationAngleThreshold,
               onSimplificationAngleThresholdChanged = { 
                   sketchViewModel.simplificationAngleThreshold = it
                   // Persist
                   context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
                       .edit().putFloat("simplification_angle_threshold", it).apply()
               },
               isDebugWireframe = sketchViewModel.isDebugWireframe,
               onToggleDebugWireframe = { sketchViewModel.isDebugWireframe = !sketchViewModel.isDebugWireframe },
               isDebugPredictionEnabled = sketchViewModel.isDebugPredictionEnabled,
               onToggleDebugPrediction = { sketchViewModel.isDebugPredictionEnabled = !sketchViewModel.isDebugPredictionEnabled },
               isPredictionEnabled = sketchViewModel.isPredictionEnabled,
               onTogglePrediction = { sketchViewModel.isPredictionEnabled = !sketchViewModel.isPredictionEnabled },
               predictionLagMs = sketchViewModel.predictionLagMs,
               onPredictionLagChanged = { sketchViewModel.predictionLagMs = it },
               predictionSmoothing = sketchViewModel.predictionSmoothing,
               onPredictionSmoothingChanged = { sketchViewModel.predictionSmoothing = it},
               predictionVelocityMin = sketchViewModel.predictionVelocityMin,
               onPredictionVelocityMinChanged = { sketchViewModel.predictionVelocityMin = it },
               predictionVelocityMax = sketchViewModel.predictionVelocityMax,
               onPredictionVelocityMaxChanged = { sketchViewModel.predictionVelocityMax = it },
               simplificationTolerance = simplificationTolerance,
               onSimplificationToleranceChanged = { simplificationTolerance = it },
               pressureTolerance = pressureTolerance,
               onPressureToleranceChanged = { pressureTolerance = it },
               currentScaleConfig = sketchViewModel.scaleConfig,
               onUpdateProjectConfig = { unit, resolution -> 
                   sketchViewModel.updateScaleConfig(unit, resolution)
               }
           )
        }
    }
}



@Composable
fun BottomMenuBar(
    modifier: Modifier = Modifier,
    tools: List<BrushTypeConfig>,
    selectedTool: ToolType,
    onToolSelected: (ToolType) -> Unit,
    colorSlots: List<Int>,
    selectedColorSlotIndex: Int,
    onColorSlotSelected: (Int) -> Unit,
    onColorChangeRequest: () -> Unit,
    selectedSize: Float,
    onSizeChangeRequest: () -> Unit,
    isEraserActive: Boolean,
    onEraserToggle: () -> Unit,
    showToolPopup: Boolean,
    onShowToolPopupChange: (Boolean) -> Unit,
    isFillModeEnabled: Boolean,
    onToggleFillMode: () -> Unit,
    fillColor: Int,
    onFillColorChangeRequest: () -> Unit,
    backgroundColor: Int,
    onBackgroundColorChangeRequest: () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.9f)) // Semi-transparent
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()), // Make it scrollable
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TOOL SELECTOR
            Box {
                val currentToolConfig = tools.find { it.type == selectedTool } ?: tools.first()
                val icon = if(selectedTool == ToolType.ERASER) Icons.Default.Edit else currentToolConfig.icon
                
                IconButton(onClick = { if(selectedTool != ToolType.ERASER) onShowToolPopupChange(!showToolPopup) else onToolSelected(ToolType.TECHNICAL_PEN)}) {
                   Icon(icon, contentDescription = "Tool", tint = if (selectedTool == ToolType.ERASER) Color.Gray else Color.Black)
                }
                
                DropdownMenu(
                    expanded = showToolPopup,
                    onDismissRequest = { onShowToolPopupChange(false) }
                ) {
                    tools.forEach { tool ->
                        DropdownMenuItem(
                            text = { 
                                val param = tool.type.name
                                val label = when (param) {
                                    "POLYGON_SWEEPER" -> "Organic Fill"
                                    "TECHNICAL_PEN" -> "Technical Pen"
                                    "PRESSURE_PEN" -> "Pressure Pen"
                                    "FILL_SHAPE" -> "Fill Tool"
                                    else -> param.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }
                                }
                                Text(label) 
                            },
                            leadingIcon = { Icon(tool.icon, null) },
                            onClick = {
                                onToolSelected(tool.type)
                                onShowToolPopupChange(false)
                            }
                        )
                    }
                }
            }

            // COLOR SLOTS
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                colorSlots.forEachIndexed { index, color ->
                    ColorSlot(
                        color = color,
                        isSelected = index == selectedColorSlotIndex,
                        onClick = { 
                            if (index == selectedColorSlotIndex) onColorChangeRequest() 
                            else onColorSlotSelected(index)
                        }
                    )
                }
            }
            
            VerticalDivider(modifier = Modifier.height(24.dp))

            // FILL MODE TOGGLE
            Row(
                 verticalAlignment = Alignment.CenterVertically
            ) {
                 IconButton(onClick = onToggleFillMode) {
                     Icon(
                        Icons.Default.FormatPaint,
                        contentDescription = "Auto Fill",
                        tint = if (isFillModeEnabled) Color.Black else Color.LightGray
                     )
                 }
                 
                 // FILL COLOR PREVIEW (Only if enabled)
                 if (isFillModeEnabled) {
                     Box(
                         modifier = Modifier
                             .size(24.dp)
                             .clip(CircleShape)
                             .background(Color(fillColor))
                             .border(2.dp, Color.Black, CircleShape)
                             .clickable(onClick = onFillColorChangeRequest)
                     )
                 }
            }

            VerticalDivider(modifier = Modifier.height(24.dp))

            // SIZE PREVIEW
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color.LightGray)
                    .clickable(onClick = onSizeChangeRequest),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(selectedSize.coerceIn(2f, 36f).dp)
                        .clip(CircleShape)
                        .background(Color.Black)
                )
            }
            
            VerticalDivider(modifier = Modifier.height(24.dp))

            // ERASER
            IconButton(onClick = onEraserToggle) {
                Icon(
                    Icons.Default.Delete, 
                    contentDescription = "Eraser",
                    tint = if (isEraserActive) Color.Red else Color.Gray
                )
            }

            VerticalDivider(modifier = Modifier.height(24.dp))
            
            // BACKGROUND COLOR PICKER
             Box(
                 modifier = Modifier
                     .size(32.dp)
                     .clip(CircleShape)
                     .background(Color(backgroundColor))
                     .border(1.dp, Color.Gray, CircleShape)
                     .clickable(onClick = onBackgroundColorChangeRequest),
                 contentAlignment = Alignment.Center
             ) {
                 // Icon overlay to indicate it's background?
                 // Or just the color. 
                 // Let's add a small icon overlay if white to differentiate.
                 if (backgroundColor == android.graphics.Color.WHITE) {
                     Icon(Icons.Default.Palette, contentDescription = "Background", tint = Color.Black.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
                 }
             }
        }
    }
}

@Composable
fun TopMenuBar(
    modifier: Modifier = Modifier,
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    onLayersClick: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onNewDrawing: () -> Unit,
    onSettingsClick: () -> Unit,
    onGridClick: () -> Unit,
    isSnapEnabled: Boolean,
    onToggleSnap: () -> Unit,
    activeLayerName: String
) {
    var showProjectMenu by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color.White.copy(alpha = 0.9f))
            .padding(8.dp)
    ) {
        // 1. LEFT: Project & Tools
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Project Menu
            Box {
                IconButton(onClick = { showProjectMenu = true }) {
                    Icon(Icons.Default.Menu, contentDescription = "Project")
                }
                DropdownMenu(
                    expanded = showProjectMenu,
                    onDismissRequest = { showProjectMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("New Drawing") },
                        leadingIcon = { Icon(Icons.Default.Refresh, null) },
                        onClick = { onNewDrawing(); showProjectMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Save Project") },
                        leadingIcon = { Icon(Icons.Default.Save, null) },
                        onClick = { onSave(); showProjectMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Load Project") },
                        leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                        onClick = { onLoad(); showProjectMenu = false }
                    )
                }
            }

            
            IconButton(onClick = onGridClick) {
                Icon(Icons.Default.GridOn, contentDescription = "Grid")
            }

            IconButton(onClick = onToggleSnap) {
                Icon(Icons.Default.Grid4x4, contentDescription = "Snap to Grid", tint = if (isSnapEnabled) Color.Black else Color.LightGray)
            }
        }

        // 2. CENTER: Undo/Redo (Perfectly Centered)
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Undo", tint = if (canUndo) Color.Black else Color.LightGray)
            }
            // Small spacer betweeen undo/redo?
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Redo", tint = if (canRedo) Color.Black else Color.LightGray)
            }
        }

        // 3. RIGHT: Layer Name, Layers, Settings
        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = activeLayerName,
                style = MaterialTheme.typography.labelMedium,
                color = Color.Gray,
                modifier = Modifier.padding(end = 8.dp)
            )

            IconButton(onClick = onLayersClick) {
                Icon(Icons.Default.List, contentDescription = "Layers")
            }
            
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.MoreVert, contentDescription = "Settings")
            }
        }
    }
}

@Composable
fun ColorSlot(color: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(2.dp, if (isSelected) Color.Black else Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
    )
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun SizeSelectorPopup(
    currentSize: Float, 
    onSizeChanged: (Float) -> Unit, 
    currentOpacity: Float,
    onOpacityChanged: (Float) -> Unit,
    stabilizationLevel: Float,
    onStabilizationLevelChanged: (Float) -> Unit,
    pressureSensitivity: Float,
    onPressureSensitivityChanged: (Float) -> Unit,
    presets: List<Float>,
    onPresetSelected: (Float) -> Unit,
    onPresetSave: (Int, Float) -> Unit,
    penMinSizeFactor: Float,
    onPenMinSizeFactorChanged: (Float) -> Unit,
    isVectorPen: Boolean,
    isPolygonSweeper: Boolean = false,
    polygonSides: Int = 5,
    onPolygonSidesChanged: (Int) -> Unit = {},
    polygonRotationSpeed: Float = 0.5f,
    onPolygonRotationSpeedChanged: (Float) -> Unit = {},
    isPolygonRandomRotation: Boolean = false,
    onPolygonRandomRotationChanged: (Boolean) -> Unit = {},
    onDismiss: () -> Unit
) {
    // Non-linear Slider Logic (Quadratic)
    // t = 0..1
    // Size = Min + (Max - Min) * t^2
    // t = sqrt((Size - Min) / (Max - Min))
    val minSize = 1f
    val maxSize = 100f
    
    // Calculate initial slider position from currentSize
    // Clamp magnitude to avoid NaN with sqrt of negative numbers
    val initialT = kotlin.math.sqrt(((currentSize - minSize) / (maxSize - minSize)).coerceAtLeast(0f))
    var sliderValue by remember { mutableFloatStateOf(initialT) }

    Popup(
        alignment = Alignment.BottomCenter, 
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.PopupProperties(focusable = true)
    ) {
        Column(
            modifier = Modifier
                .padding(bottom = 100.dp) // Lift above bottom bar
                .width(250.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Size: ${currentSize.toInt()}")
            
            Slider(
                value = sliderValue,
                onValueChange = { t ->
                    sliderValue = t
                    // Quadratic mapping
                    val nonLinearSize = minSize + (maxSize - minSize) * (t * t)
                    onSizeChanged(nonLinearSize)
                },
                valueRange = 0f..1f
            )
            
            // PRESETS
            Text("Presets (Long press to Save)", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                presets.forEachIndexed { index, size ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray.copy(alpha = 0.3f))
                            .combinedClickable(
                                onClick = { 
                                    onPresetSelected(size)
                                    // Update slider visual
                                    val newT = kotlin.math.sqrt(((size - minSize) / (maxSize - minSize)).coerceAtLeast(0f))
                                    sliderValue = newT
                                },
                                onLongClick = { onPresetSave(index, currentSize) }
                            )
                            .border(1.dp, Color.Gray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        // Dot representing size
                        Box(
                            modifier = Modifier
                                .size((size.coerceIn(2f, 24f)).dp)
                                .clip(CircleShape)
                                .background(Color.Black)
                        )
                    }
                }
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            
            Text("Opacity: ${(currentOpacity * 100).toInt()}%")
            Slider(
                value = currentOpacity,
                onValueChange = onOpacityChanged,
                valueRange = 0.01f..1f // Min 1%
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Estabilizador: ${(stabilizationLevel).toInt()}%")
            Slider(
                value = stabilizationLevel,
                onValueChange = onStabilizationLevelChanged,
                valueRange = 0f..100f
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Text("Sensibilidad: $pressureSensitivity")
            Slider(
                value = pressureSensitivity,
                onValueChange = onPressureSensitivityChanged,
                valueRange = 0.1f..2.0f
            )

            if (isVectorPen) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                Text("Tamaño Mínimo: ${(penMinSizeFactor * 100).toInt()}%")
                Slider(
                    value = penMinSizeFactor,
                    onValueChange = onPenMinSizeFactorChanged,
                    valueRange = 0f..1f
                )
                
            }
            
            // POLYGON SWEEPER SETTINGS
            if (isPolygonSweeper) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                
                // Shape name helper
                val shapeName = when(polygonSides) {
                    3 -> "Triángulo"
                    4 -> "Cuadrado"
                    5 -> "Pentágono"
                    6 -> "Hexágono"
                    7 -> "Heptágono"
                    8 -> "Octágono"
                    9 -> "Eneágono"
                    10 -> "Decágono"
                    else -> "Polígono"
                }
                
                Text("Lados: $polygonSides ($shapeName)")
                Slider(
                    value = polygonSides.toFloat(),
                    onValueChange = { onPolygonSidesChanged(it.toInt()) },
                    valueRange = 3f..10f,
                    steps = 7 - 1 // 8 total values (3-10), so 6 steps between
                )
                
                Text("Rotación: ${String.format("%.2f", polygonRotationSpeed)}")
                Slider(
                    value = polygonRotationSpeed,
                    onValueChange = onPolygonRotationSpeedChanged,
                    valueRange = 0f..2.0f
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Caos (Random)")
                    Switch(
                        checked = isPolygonRandomRotation,
                        onCheckedChange = onPolygonRandomRotationChanged
                    )
                }
            }
        }
    }
}
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    isRotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    isPalmRejectionEnabled: Boolean,
    onTogglePalmRejection: () -> Unit,
    interfaceScale: Float,
    onInterfaceScaleChanged: (Float) -> Unit,
    simplificationAngleThreshold: Float,
    onSimplificationAngleThresholdChanged: (Float) -> Unit,
    isDebugWireframe: Boolean,
    onToggleDebugWireframe: () -> Unit,
    isDebugPredictionEnabled: Boolean,
    onToggleDebugPrediction: () -> Unit,
    isPredictionEnabled: Boolean,
    onTogglePrediction: () -> Unit,
    predictionLagMs: Float,
    onPredictionLagChanged: (Float) -> Unit,
    predictionSmoothing: Float,
    onPredictionSmoothingChanged: (Float) -> Unit,
    predictionVelocityMin: Float,
    onPredictionVelocityMinChanged: (Float) -> Unit,
    predictionVelocityMax: Float,
    onPredictionVelocityMaxChanged: (Float) -> Unit,
    simplificationTolerance: Float,
    onSimplificationToleranceChanged: (Float) -> Unit,
    pressureTolerance: Float,
    onPressureToleranceChanged: (Float) -> Unit,
    currentScaleConfig: ScaleConfig,
    onUpdateProjectConfig: (String, Float) -> Unit
) {
    var resolutionText by remember { mutableStateOf(currentScaleConfig.basePixelsPerMillimeter.toString()) }
    var selectedUnit by remember { mutableStateOf(DistanceUnit.fromSymbol(currentScaleConfig.unitName)) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings & Project", style = MaterialTheme.typography.titleLarge)
            
            // --- PROJECT CONFIGURATION ---
            Text("Project", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // Resolution
            Column {
                Text("Base Resolution (px/mm)", style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = resolutionText,
                    onValueChange = { resolutionText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Pixels per real-world millimeter.", fontSize = 10.sp, color = Color.Gray)
            }
            
            // Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unit")
                Row {
                    DistanceUnit.entries.forEach { unit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier
                                .clickable { selectedUnit = unit }
                                .padding(4.dp)
                        ) {
                            RadioButton(
                                selected = (selectedUnit == unit),
                                onClick = { selectedUnit = unit }
                            )
                            Text(text = unit.symbol, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
            
            // Info Calculation
            val resolution = resolutionText.toFloatOrNull() ?: 0f
            if (resolution > 0) {
                 val unitMm = selectedUnit.toMillimeters
                 val pixels = unitMm * resolution
                 Text(
                     text = "1 ${selectedUnit.symbol} = ${pixels.toInt()} px",
                     style = MaterialTheme.typography.bodySmall,
                     fontWeight = FontWeight.Bold
                 )
            }
            
            HorizontalDivider()
            
            // --- APP SETTINGS ---
            Text("App Preferences", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Lock Rotation")
                Switch(checked = isRotationLocked, onCheckedChange = { onToggleRotationLock() })
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Stylus Only")
                Switch(checked = isPalmRejectionEnabled, onCheckedChange = { onTogglePalmRejection() })
            }
            
            Column {
                Text("Interface Scale: ${(interfaceScale * 100).toInt()}%")
                Slider(
                    value = interfaceScale,
                    onValueChange = onInterfaceScaleChanged,
                    valueRange = 0.5f..2.0f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Debug Geometry (Wireframe)")
                Switch(checked = isDebugWireframe, onCheckedChange = { onToggleDebugWireframe() })
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Enable Prediction (Lag Comp)")
                Switch(checked = isPredictionEnabled, onCheckedChange = { onTogglePrediction() })
            }

            if (isPredictionEnabled) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Debug Prediction (Red Line)")
                    Switch(checked = isDebugPredictionEnabled, onCheckedChange = { onToggleDebugPrediction() })
                }
                
                if (isDebugPredictionEnabled) {
                Column {
                    Text("Max Prediction Lag: ${predictionLagMs.toInt()} ms")
                    Slider(
                        value = predictionLagMs,
                        onValueChange = onPredictionLagChanged,
                        valueRange = 0f..150f
                    )
                    
                    Text("Prediction Smoothing: ${(predictionSmoothing * 100).toInt()}%")
                    Slider(
                        value = predictionSmoothing,
                        onValueChange = onPredictionSmoothingChanged,
                        valueRange = 0.0f..0.99f
                    )
                    Text("Higher = Less Jitter, More Trailing Hook", fontSize = 10.sp, color = Color.Gray)
                    
                    HorizontalDivider()
                    Text("Velocity Thresholds (px/s)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    
                    Text("Min Velocity (Start Lag): ${predictionVelocityMin.toInt()} px/s")
                    Slider(
                        value = predictionVelocityMin,
                        onValueChange = onPredictionVelocityMinChanged,
                        valueRange = 0f..1000f
                    )
                    
                    Text("Max Velocity (Full Lag): ${predictionVelocityMax.toInt()} px/s")
                    Slider(
                         value = predictionVelocityMax,
                         onValueChange = onPredictionVelocityMaxChanged,
                         valueRange = 1000f..5000f
                    )
                }
            }
            }
            
            Column {
                Text("Simplificación (General)", style = MaterialTheme.typography.titleSmall)
                Text("Tolerancia Distancia: ${String.format("%.1f", simplificationTolerance)}px")
                Slider(
                    value = simplificationTolerance,
                    onValueChange = onSimplificationToleranceChanged,
                    valueRange = 0f..5f
                )
                Text("0 = Desactivado. Mayor = Menos puntos.", fontSize = 10.sp, color = Color.Gray)

                Text("Tolerancia Presión: ${String.format("%.2f", pressureTolerance)}")
                Slider(
                    value = pressureTolerance,
                    onValueChange = onPressureToleranceChanged,
                    valueRange = 0.01f..0.2f
                )
                Text("Preservación de grosor (Menor = Más detalle)", fontSize = 10.sp, color = Color.Gray)

                Text("Preservación de Esquinas: ${simplificationAngleThreshold.toInt()}°")
                Slider(
                    value = simplificationAngleThreshold,
                    onValueChange = onSimplificationAngleThresholdChanged,
                    valueRange = 0f..90f
                )
                Text("Ángulo mínimo para forzar un punto (0 = Todo, 90 = Suave)", fontSize = 10.sp, color = Color.Gray)
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onDismiss, colors = ButtonDefaults.textButtonColors()) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val res = resolutionText.toFloatOrNull()
                    if (res != null) {
                        onUpdateProjectConfig(selectedUnit.symbol, res)
                    }
                    onDismiss()
                }) {
                    Text("Apply & Close")
                }
            }
        }
    }
}

@Composable
fun LayerManagerDialog(
    layers: List<Layer>,
    activeLayerIndex: Int,
    onToggleVisibility: (Int) -> Unit,
    onOpacityChanged: (Int, Float) -> Unit,
    onActiveLayerChanged: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Capas", style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Text("X", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            Divider()

            // Layers List (Reversed visual order)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                // Display in reverse order so top layer is at top of list
                // We need to map index correctly back to original list
                val reversedIndices = layers.indices.reversed().toList()
                
                itemsIndexed(reversedIndices) { _, originalIndex ->
                    val layer = layers[originalIndex]
                    val isActive = originalIndex == activeLayerIndex
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color.LightGray.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { onActiveLayerChanged(originalIndex) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Visibility
                        IconButton(
                            onClick = { onToggleVisibility(originalIndex) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                tint = if (layer.isVisible) Color.Black else Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Name
                        Text(
                            text = layer.id,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        )
                        
                        // Opacity
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(layer.opacity * 100).toInt()}%", fontSize = 10.sp)
                            Slider(
                                value = layer.opacity,
                                onValueChange = { onOpacityChanged(originalIndex, it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.width(80.dp).height(20.dp)
                            )
                        }
                    }
                }
            }

            Divider()

            // Footer Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Add
                IconButton(onClick = onAddLayer) {
                    Icon(Icons.Default.Add, contentDescription = "Add Layer")
                }
                
                // Move Up (Visual Up = Higher Index)
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                }
                
                // Move Down (Visual Down = Lower Index)
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                }
                
                // Delete
                IconButton(onClick = onDeleteLayer) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Active", tint = Color.Red)
                }
            }
        }
    }
}



@Composable
fun GridSettingsDialog(
    currentGridConfig: GridConfig,
    currentUnit: DistanceUnit,
    onUpdateGrid: (Boolean, Float, Int, Int, Int) -> Unit,
    onUpdateUnit: (DistanceUnit) -> Unit,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(currentGridConfig.isVisible) }
    var spacingText by remember { mutableStateOf(currentGridConfig.spacing.toString()) }
    var expandedUnit by remember { mutableStateOf(false) }
    
    // Colors
    var primaryColor by remember { mutableIntStateOf(currentGridConfig.color) }
    var secondaryColor by remember { mutableIntStateOf(currentGridConfig.secondaryColor) }
    var tertiaryColor by remember { mutableIntStateOf(currentGridConfig.tertiaryColor) }

    // Color Pickers State
    var showPrimaryPicker by remember { mutableStateOf(false) }
    var showSecondaryPicker by remember { mutableStateOf(false) }
    var showTertiaryPicker by remember { mutableStateOf(false) }

    val updateConfig = {
        val spacing = spacingText.toFloatOrNull() ?: 1f
        if (spacing > 0) {
            onUpdateGrid(isVisible, spacing, primaryColor, secondaryColor, tertiaryColor)
        }
    }

    if (showPrimaryPicker) {
        ColorPickerDialog(
            initialColor = primaryColor,
            onDismiss = { showPrimaryPicker = false },
            onColorSelected = { 
                primaryColor = it
                showPrimaryPicker = false
                updateConfig()
            }
        )
    }
    if (showSecondaryPicker) {
        ColorPickerDialog(
            initialColor = secondaryColor,
            onDismiss = { showSecondaryPicker = false },
            onColorSelected = { 
                secondaryColor = it
                showSecondaryPicker = false
                updateConfig()
            }
        )
    }
    if (showTertiaryPicker) {
        ColorPickerDialog(
            initialColor = tertiaryColor,
            onDismiss = { showTertiaryPicker = false },
            onColorSelected = { 
                tertiaryColor = it
                showTertiaryPicker = false
                updateConfig()
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Grid Settings", style = MaterialTheme.typography.titleLarge)
            
            // Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Show Grid")
                Switch(
                    checked = isVisible, 
                    onCheckedChange = { 
                        isVisible = it
                        updateConfig()
                    }
                )
            }
            
            HorizontalDivider()

            // Unit Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Unit")
                Box {
                    Button(onClick = { expandedUnit = true }) {
                        Text(currentUnit.symbol)
                    }
                    DropdownMenu(expanded = expandedUnit, onDismissRequest = { expandedUnit = false }) {
                        DistanceUnit.entries.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.symbol) },
                                onClick = {
                                    onUpdateUnit(unit)
                                    expandedUnit = false
                                }
                            )
                        }
                    }
                }
            }

            // Spacing Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Spacing (${currentUnit.symbol})")
                OutlinedTextField(
                    value = spacingText,
                    onValueChange = { 
                        spacingText = it
                        updateConfig()
                    },
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }
            
            HorizontalDivider()
            
            Text("Line Colors", style = MaterialTheme.typography.labelMedium)
            
            // Primary Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Primary (Major)")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(primaryColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showPrimaryPicker = true }
                )
            }
            
            // Secondary Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Secondary (Mid)")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(secondaryColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showSecondaryPicker = true }
                )
            }
            
            // Tertiary Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tertiary (Minor)")
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(tertiaryColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showTertiaryPicker = true }
                )
            }

            // Close
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
        }
    }
}

// EXTENSIONS
private fun InProgressStrokesView.setStrokes(strokes: List<Stroke>) {
    // This function mimics a "setStrokes" capability for InProgressStrokesView.
    // Since InProgressStrokesView is designed for active input, clearing and re-adding 
    // static strokes is not its primary function, but required for this "Dual Rendering" strategy
    // where we want to keep Ink "live".
    
    // this.clear() // ERROR: 'clear' is unresolved. InProgressStrokesView doesn't support manual clearing.
    // Instead, we rely on the fact that removing the strokes from the underlying data 
    // AND calling a redraw logic is what matters.
    
    // HOWEVER: For "Live Ink" staying on screen, the view retains them internally.
    // If we want to "Reload" (e.g. after ERASE), we need to clear that internal state.
    // If the API doesn't expose 'clear', we might be stuck with accumulation.
    // End of implementation

}

private fun adjustPressure(pressure: Float, sensitivity: Float): Float {
    if (pressure <= 0f) return 0f
    if (pressure >= 1f) return 1f
    return try {
        java.lang.Math.pow(pressure.toDouble(), (1.0 / sensitivity.coerceAtLeast(0.01f)).toDouble()).toFloat()
    } catch (e: Exception) {
        pressure
    }
}


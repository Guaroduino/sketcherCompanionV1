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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.view.WindowCompat
import com.skecher.sketchercompanionv1.ui.FileMenu
import com.skecher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme
import com.skecher.sketchercompanionv1.GroupElement
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
import com.skecher.sketchercompanionv1.dto.*
import com.skecher.sketchercompanionv1.utils.UnitUtils
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Grid4x4
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import com.skecher.sketchercompanionv1.ui.InputSettingsPopup
import com.skecher.sketchercompanionv1.ui.ToolSettingsPopup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Build
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import java.io.InputStream
import androidx.compose.material.icons.filled.Image // Import Icon

fun decodeSampledBitmapFromUri(context: Context, uri: android.net.Uri, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
    try {
        var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()
        return bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}


// ToolType moved to ProjectDTOs.kt
enum class SelectionTouchMode { IDLE, SELECTING_AREA, DRAGGING_CONTENT, DRAGGING_CORNER, ROTATING }

data class BrushTypeConfig(
    val type: ToolType,
    val icon: ImageVector,
    val family: BrushFamily?,
    val nameResId: Int // Add resource ID
)

// Helper to get name
@Composable
fun getToolName(type: ToolType): String {
    return when(type) {
        ToolType.FREEHAND -> "LÃ¡piz" // Default 
        ToolType.FILL -> stringResource(R.string.tool_fill)
        ToolType.ERASER -> stringResource(R.string.tool_eraser)
        ToolType.SELECTION -> stringResource(R.string.tool_selection)
        else -> ""
    }
}

private class RuntimeState {
    var toolType: ToolType = ToolType.FREEHAND
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
        if (toolType != ToolType.ERASER && toolType != ToolType.FILL && brushFamily != null) {
            val visualSize = size * currentZoom
            
            // Mix Opacity
            val baseColor = color
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
    // --- SAVE / LOAD HANDLERS (SAF) ---
    val imagePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            val type = context.contentResolver.getType(it) ?: ""
            if (type.contains("svg") || it.toString().endsWith(".svg", ignoreCase = true)) {
                sketchViewModel.insertSvg(context, it)
            } else {
                sketchViewModel.insertImage(context, it)
            }
        }
    }

    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let {
            sketchViewModel.saveProjectToZip(context, it)
        }
    }

    val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            try {
                sketchViewModel.loadProjectFromZip(context, it)
                // Update View and Redraw Cache
                canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                canvasViewRef?.redrawAllCache()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    val exportSvgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { uri ->
        uri?.let {
             sketchViewModel.exportSvg(context, it)
        }
    }
    
    // Detectamos cambio de configuraciÃ³n (rotaciÃ³n) automÃ¡ticamente con Compose
    val configuration = LocalConfiguration.current
    var showInputSettings by rememberSaveable { mutableStateOf(false) }
    
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    var showColorPicker by remember { mutableStateOf(false) }
    var showToolPopup by remember { mutableStateOf(false) }
    var showSizePopup by remember { mutableStateOf(false) }
    var showSettingsPopup by remember { mutableStateOf(false) }
    var showLayerManager by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) } 
    var showGridSettings by remember { mutableStateOf(false) }
    var showToolSettingsPopup by remember { mutableStateOf(false) }
    var showFillColorPicker by remember { mutableStateOf(false) }

    // Convenience accessors (optional, but keep for clarity if used)
    val isFillModeEnabled = sketchViewModel.isFillModeEnabled
    val fillModeColor = sketchViewModel.fillModeColor
    
    // Simplification Vars (Removed)
    
    // Rotation Lock Effect
    LaunchedEffect(sketchViewModel.isRotationLocked) {
        activity?.requestedOrientation = if (sketchViewModel.isRotationLocked) {
             ActivityInfo.SCREEN_ORIENTATION_LOCKED
        } else {
             ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    // MATRICES (Persistentes en ViewModel)
    val cameraMatrix = remember { Matrix().apply { setValues(sketchViewModel.cameraMatrixValues) } }
    val inverseMatrix = remember { Matrix().apply { 
        val temp = Matrix()
        temp.setValues(sketchViewModel.cameraMatrixValues)
        temp.invert(this)
    }}

    // ZOOM STATE 
    var currentZoom by remember { mutableFloatStateOf(InkUtils.getMatrixScale(cameraMatrix)) }

    // --- VIEWMODEL DELEGATIONS (Fix Unresolved References) ---
    
    val onToggleRotationLock: () -> Unit = { sketchViewModel.toggleRotationLock() }
    val onTogglePalmRejection: () -> Unit = { sketchViewModel.togglePalmRejection() }
    val onInterfaceScaleChanged: (Float) -> Unit = { sketchViewModel.updateInterfaceScale(it) }
    val onToolbarAlphaChanged: (Float) -> Unit = { sketchViewModel.updateToolbarAlpha(it) }
    val onToggleToolbarBlur: () -> Unit = { sketchViewModel.toggleToolbarBlur() }
    val onToggleDebugWireframe: () -> Unit = { sketchViewModel.isDebugWireframe = !sketchViewModel.isDebugWireframe }
    
    val isRotationLocked = sketchViewModel.isRotationLocked
    val isPalmRejectionEnabled = sketchViewModel.isPalmRejectionEnabled
    val interfaceScale = sketchViewModel.interfaceScale
    val toolbarAlpha = sketchViewModel.toolbarAlpha
    val isToolbarBlurEnabled = sketchViewModel.isToolbarBlurEnabled
    val isDebugWireframe = sketchViewModel.isDebugWireframe
    val toolbarBackgroundColor = sketchViewModel.toolbarBackgroundColor
    val onToolbarBackgroundColorChanged: (Int) -> Unit = { sketchViewModel.updateToolbarBackgroundColor(it) }
    
    val activeToolType = sketchViewModel.currentTool


    // VIEW REFS
    var wetViewRef by remember { mutableStateOf<InProgressStrokesView?>(null) }

    // CAMERA SYNC OBSERVER
    LaunchedEffect(sketchViewModel.cameraUpdateTrigger) {
        val vmMatrixValues = sketchViewModel.cameraMatrixValues
        cameraMatrix.setValues(vmMatrixValues)
        cameraMatrix.invert(inverseMatrix)
        
        canvasViewRef?.setCameraMatrix(cameraMatrix)
        canvasViewRef?.invalidate()
        
        // Update Local Zoom State
        val newZoom = InkUtils.getMatrixScale(cameraMatrix)
        currentZoom = newZoom
        
        // Update WetView Brush Size
        (wetViewRef?.tag as? RuntimeState)?.updateActiveBrush(newZoom)
        wetViewRef?.invalidate()
    }

    val brushTypes = listOf(
        BrushTypeConfig(ToolType.FREEHAND, Icons.Default.Create, null, R.string.tool_pressure_pen), 
        BrushTypeConfig(ToolType.FILL, Icons.Default.FormatPaint, null, R.string.tool_fill),
        BrushTypeConfig(ToolType.SELECTION, Icons.Default.TouchApp, null, R.string.tool_selection)
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

    LaunchedEffect(sketchViewModel.currentTool, canvasViewRef) {
        canvasViewRef?.currentTool = sketchViewModel.currentTool
    }

    LaunchedEffect(sketchViewModel.isDebugWireframe) {
        canvasViewRef?.isDebugWireframeByVM = sketchViewModel.isDebugWireframe
    }

    LaunchedEffect(canvasViewRef) {
        canvasViewRef?.selectionManager = sketchViewModel.selectionManager
    }

    LaunchedEffect(sketchViewModel.currentFreehandSettings, canvasViewRef) {
        canvasViewRef?.activeFreehandSettings = sketchViewModel.currentFreehandSettings
    }

    LaunchedEffect(sketchViewModel.currentSize, canvasViewRef) {
        canvasViewRef?.activeSize = sketchViewModel.currentSize
    }
    
    LaunchedEffect(sketchViewModel.currentColor, canvasViewRef) {
        canvasViewRef?.activeColor = sketchViewModel.currentColor
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
                    
                    this.onSizeChangedCallback = { w, h ->
                        sketchViewModel.saveDimensions(w.toFloat(), h.toFloat())
                    }
                    
                    // Callback wire-up
                    this.onStrokeCompleted = { stroke ->
                        sketchViewModel.addVectorStroke(stroke)
                    }

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
                                    cv.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                                    cv.invalidate()
                                }
                                v?.removeOnLayoutChangeListener(this)
                            }
                        }
                    })
                }
                canvasViewRef = canvasView
                
                canvasView.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
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
                        toolType = sketchViewModel.currentTool
                        color = sketchViewModel.currentColor
                        size = sketchViewModel.currentSize
                        opacity = sketchViewModel.currentOpacity
                        
                        // Sync Global Settings to CanvasView
                        canvasView.isFingerMode = sketchViewModel.fingerModeActive
                        canvasView.fingerOffsetX = sketchViewModel.fingerOffsetXValue
                        canvasView.fingerOffsetY = sketchViewModel.fingerOffsetYValue
                        canvasView.globalStabilizationLevel = sketchViewModel.globalStabilizationLevel

                         val currentConfig = brushTypes.find { it.type == sketchViewModel.currentTool } ?: brushTypes.first()
                         brushFamily = currentConfig.family
 
                         val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                         updateActiveBrush(currentZoom)
                    }
                    tag = initialState
                    setLayerType(View.LAYER_TYPE_HARDWARE, null)
                }
                // Capture Ref
                wetViewRef = wetView

                container.addView(canvasView)
                container.addView(wetView)

                var isPanning = false // Track panning state for deferred update

                // --- GESTOS ---
                val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        val currentMatrixScale = InkUtils.getMatrixScale(cameraMatrix)
                        val projectedZoom = currentMatrixScale * detector.scaleFactor
                        
                        // CLAMP ZOOM: 20% to 300%
                        val clampedZoom = projectedZoom.coerceIn(0.2f, 3.0f)
                        val effectiveScaleFactor = clampedZoom / currentMatrixScale
                        
                        cameraMatrix.postScale(effectiveScaleFactor, effectiveScaleFactor, detector.focusX, detector.focusY)
                        canvasView.setCameraMatrix(cameraMatrix, isIntermediate = true) // Intermediate Draw
                        cameraMatrix.invert(inverseMatrix)
                        sketchViewModel.saveCameraState(cameraMatrix)
                        
                        // UPDATE STATE
                        // We must update the outer 'currentZoom' state variable (which is a MutableFloatState)
                        currentZoom = clampedZoom
                        
                        // Update Brush Size dynamically
                        (wetView.tag as? RuntimeState)?.updateActiveBrush(clampedZoom)

                        return true
                    }

                    override fun onScaleEnd(detector: ScaleGestureDetector) {
                         canvasView.refreshView() // High Quality Redraw
                    }
                })

                val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean {
                        return true // Essential for detecting scroll/pan
                    }

                    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                        if (e2.pointerCount >= 2) {
                            isPanning = true
                            cameraMatrix.postTranslate(-dX, -dY)
                            canvasView.setCameraMatrix(cameraMatrix, isIntermediate = true) // Intermediate Draw
                            cameraMatrix.invert(inverseMatrix)
                            sketchViewModel.saveCameraState(cameraMatrix)
                            return true
                        }
                        return false
                    }
                })

                // val stabilizer = StrokeStabilizer() // REMOVED
                val strokeIdMap = mutableMapOf<Int, InProgressStrokeId>()
                
                // SELECTION STATE
                var selTouchMode = SelectionTouchMode.IDLE
                var activeHandle = -1 // 0-3 corners, 4 rotation
                val selPath = android.graphics.Path()
                val initialBox = android.graphics.RectF()
                val currentBox = android.graphics.RectF()
                var pivotX = 0f
                var pivotY = 0f
                var lastX = 0f
                var lastY = 0f
                var startAngle = 0f

                // INPUT FILTERING STATE
                var lastInputX = 0f
                var lastInputY = 0f
                var stabilizerX = 0f
                var stabilizerY = 0f
                var lastInputPressure = 0f
                
                // Active Pointer Tracking for Robust Single-Touch
                var activePointerId = -1


                // Restore Touch Listener
                wetView.setOnTouchListener { v, event ->
                    // ... (rest of touch listener logic)
                    scaleDetector.onTouchEvent(event)
                    gestureDetector.onTouchEvent(event)

                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                        if (isPanning) {
                            canvasView.refreshView() // High Quality Redraw after Pan
                            isPanning = false
                        }
                    }
                    
                    // BOTTOM DEAD ZONE ...
                    val density = context.resources.displayMetrics.density
                    val deadZonePx = 40 * density
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y > (v.height - deadZonePx)) {
                         return@setOnTouchListener false
                    }
                    
                    
                    // 2. PALM REJECTION CHECK (Blocks Tool Usage only)
                    // If Palm Rejection enabled AND not using a Stylus -> Block drawing
                    if (sketchViewModel.isPalmRejectionEnabled && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
                         return@setOnTouchListener true // Consume event so it doesn't propagate, but don't draw
                    }

                    // 3. FREEHAND: Allow fallthrough to CanvasView
                    if (sketchViewModel.currentTool == ToolType.FREEHAND) {
                         return@setOnTouchListener false
                    }

                    val state = v.tag as RuntimeState
                    val action = event.actionMasked
                    
                    // --- SYNC STATE FROM VIEWMODEL (CRITICAL FOR FIRST STROKE & COLOR LAG) ---
                    if (action == MotionEvent.ACTION_DOWN) {
                         state.toolType = sketchViewModel.currentTool
                         state.color = sketchViewModel.currentColor
                         state.size = sketchViewModel.currentSize
                         state.opacity = sketchViewModel.currentOpacity
                         
                         val currentConfig = brushTypes.find { it.type == state.toolType } ?: brushTypes.first()
                         state.brushFamily = currentConfig.family

                         val zoom = InkUtils.getMatrixScale(cameraMatrix)
                         state.updateActiveBrush(zoom)
                    }

                    // --- TOOL CLASSIFICATION ---
                    val isFillTool = state.toolType == ToolType.FILL
                    val isVectorTool = isFillTool
                    
                    // Ink Tool = Active Brush AND NOT a Vector Tool (Marker, Highlighter, Pressure Pen)
                    val isInkTool = state.activeBrush != null && !isVectorTool

                    // Parallel Capture: Capture Vector Points if it's a Vector Tool OR (Ink Tool + Fill Mode)
                    val shouldCaptureVector = isVectorTool || (isInkTool && sketchViewModel.isFillModeEnabled)

                        if (state.toolType == ToolType.SELECTION) {
                            val touchPts = floatArrayOf(event.x, event.y)
                            inverseMatrix.mapPoints(touchPts)
                            val wx = touchPts[0]
                            val wy = touchPts[1]

                            when (action) {
                                MotionEvent.ACTION_DOWN -> {
                                    val manager = sketchViewModel.selectionManager
                                    val bounds = manager.baseBounds
                                    val zoom = InkUtils.getMatrixScale(cameraMatrix)
                                    val handleSize = 25f / zoom 

                                    activeHandle = -1
                                    selTouchMode = SelectionTouchMode.IDLE
                                    
                                    if (!bounds.isEmpty) {
                                        val selMatrix = manager.selectionMatrix
                                        // 0:TL  1:TR  2:BL  3:BR  4:ROT  5:CENTER 
                                        // 6:TC  7:BC  8:LC  9:RC
                                        val pts = floatArrayOf(
                                            bounds.left, bounds.top,                   // 0: TL
                                            bounds.right, bounds.top,                  // 1: TR
                                            bounds.left, bounds.bottom,                // 2: BL
                                            bounds.right, bounds.bottom,               // 3: BR
                                            bounds.centerX(), bounds.top - (30f / zoom),// 4: ROT
                                            bounds.centerX(), bounds.centerY(),        // 5: CENTER
                                            bounds.centerX(), bounds.top,              // 6: TC
                                            bounds.centerX(), bounds.bottom,           // 7: BC
                                            bounds.left, bounds.centerY(),             // 8: LC
                                            bounds.right, bounds.centerY()             // 9: RC
                                        )
                                        selMatrix.mapPoints(pts)

                                        // Hit test rotation handle
                                        if (kotlin.math.hypot(wx - pts[8], wy - pts[9]) < handleSize) {
                                            activeHandle = 4
                                            selTouchMode = SelectionTouchMode.ROTATING
                                            pivotX = pts[10]
                                            pivotY = pts[11]
                                            startAngle = Math.toDegrees(Math.atan2((wy - pivotY).toDouble(), (wx - pivotX).toDouble())).toFloat()
                                        } else {
                                            // Hit test corners
                                            if (kotlin.math.hypot(wx - pts[0], wy - pts[1]) < handleSize) activeHandle = 0
                                            else if (kotlin.math.hypot(wx - pts[2], wy - pts[3]) < handleSize) activeHandle = 1
                                            else if (kotlin.math.hypot(wx - pts[4], wy - pts[5]) < handleSize) activeHandle = 2
                                            else if (kotlin.math.hypot(wx - pts[6], wy - pts[7]) < handleSize) activeHandle = 3
                                            // Hit test edges
                                            else if (kotlin.math.hypot(wx - pts[12], wy - pts[13]) < handleSize) activeHandle = 6 // TC
                                            else if (kotlin.math.hypot(wx - pts[14], wy - pts[15]) < handleSize) activeHandle = 7 // BC
                                            else if (kotlin.math.hypot(wx - pts[16], wy - pts[17]) < handleSize) activeHandle = 8 // LC
                                            else if (kotlin.math.hypot(wx - pts[18], wy - pts[19]) < handleSize) activeHandle = 9 // RC

                                            if (activeHandle != -1) {
                                                selTouchMode = SelectionTouchMode.DRAGGING_CORNER
                                                // Set Pivot (Opposite point)
                                                val oppIdx = when(activeHandle) {
                                                    0 -> 3 // TL -> BR
                                                    1 -> 2 // TR -> BL
                                                    2 -> 1 // BL -> TR
                                                    3 -> 0 // BR -> TL
                                                    6 -> 7 // TC -> BC
                                                    7 -> 6 // BC -> TC
                                                    8 -> 9 // LC -> RC
                                                    9 -> 8 // RC -> LC
                                                    else -> 5
                                                }
                                                pivotX = pts[oppIdx * 2]
                                                pivotY = pts[oppIdx * 2 + 1]
                                            } else {
                                                // Content Drag Hit Test
                                                val invM = Matrix()
                                                selMatrix.invert(invM)
                                                val localTouch = floatArrayOf(wx, wy)
                                                invM.mapPoints(localTouch)
                                                if (bounds.contains(localTouch[0], localTouch[1])) {
                                                    selTouchMode = SelectionTouchMode.DRAGGING_CONTENT
                                                }
                                            }
                                        }
                                    }

                                    if (selTouchMode == SelectionTouchMode.IDLE) {
                                        selTouchMode = SelectionTouchMode.SELECTING_AREA
                                        selPath.reset()
                                        selPath.moveTo(wx, wy)
                                        if (sketchViewModel.currentSelectionMode == SketcherViewModel.SelectionMode.RECTANGLE) {
                                            initialBox.set(wx, wy, wx, wy)
                                        }
                                    }
                                    
                                    lastX = wx
                                    lastY = wy
                                }
                            MotionEvent.ACTION_MOVE -> {
                                val manager = sketchViewModel.selectionManager
                                val dx = wx - lastX
                                val dy = wy - lastY

                                when (selTouchMode) {
                                    SelectionTouchMode.SELECTING_AREA -> {
                                        if (sketchViewModel.currentSelectionMode == SketcherViewModel.SelectionMode.RECTANGLE) {
                                            val rectPath = android.graphics.Path()
                                            rectPath.addRect(
                                                kotlin.math.min(lastX, wx), kotlin.math.min(lastY, wy),
                                                kotlin.math.max(lastX, wx), kotlin.math.max(lastY, wy),
                                                android.graphics.Path.Direction.CW
                                            )
                                            canvasView.updateCurrentFill(rectPath, android.graphics.Color.argb(60, 0, 122, 255))
                                        } else {
                                            selPath.lineTo(wx, wy)
                                            canvasView.updateCurrentFill(selPath, android.graphics.Color.argb(60, 0, 122, 255))
                                        }
                                    }
                                    SelectionTouchMode.DRAGGING_CONTENT -> {
                                        val m = Matrix()
                                        m.postTranslate(dx, dy)
                                        manager.applyTransform(m)
                                        lastX = wx
                                        lastY = wy
                                    }
                                    SelectionTouchMode.DRAGGING_CORNER -> {
                                        val selM = manager.selectionMatrix
                                        val invM = android.graphics.Matrix()
                                        selM.invert(invM)

                                        // Map touch points and pivot to local space
                                        val locLast = floatArrayOf(lastX, lastY)
                                        val locCurr = floatArrayOf(wx, wy)
                                        val locPivot = floatArrayOf(pivotX, pivotY)
                                        invM.mapPoints(locLast)
                                        invM.mapPoints(locCurr)
                                        invM.mapPoints(locPivot)

                                        val oldDX = locLast[0] - locPivot[0]
                                        val oldDY = locLast[1] - locPivot[1]
                                        val newDX = locCurr[0] - locPivot[0]
                                        val newDY = locCurr[1] - locPivot[1]

                                        // Calculate scale factors in local space
                                        var sx = if (kotlin.math.abs(oldDX) > 0.01f) newDX / oldDX else 1f
                                        var sy = if (kotlin.math.abs(oldDY) > 0.01f) newDY / oldDY else 1f

                                        if (activeHandle <= 3) {
                                            // Corner handle: Uniform scale if locked
                                            if (sketchViewModel.isSelectionAspectRatioLocked) {
                                                val s = if (kotlin.math.abs(sx) > kotlin.math.abs(sy)) sx else sy
                                                sx = s
                                                sy = s
                                            }
                                        } else {
                                            // Edge handle: One-direction scale in local space
                                            // 6:TC, 7:BC (Top/Bottom) -> Sy only
                                            // 8:LC, 9:RC (Left/Right) -> Sx only
                                            if (activeHandle == 6 || activeHandle == 7) sx = 1f
                                            if (activeHandle == 8 || activeHandle == 9) sy = 1f
                                        }

                                        val sMatrix = android.graphics.Matrix()
                                        sMatrix.postScale(sx, sy, locPivot[0], locPivot[1])
                                        
                                        // Incremental world matrix: I = M * S_local * M^-1
                                        val imM = android.graphics.Matrix()
                                        imM.set(invM)
                                        imM.postConcat(sMatrix)
                                        imM.postConcat(selM)
                                        
                                        manager.applyTransform(imM)
                                        lastX = wx
                                        lastY = wy
                                    }
                                    SelectionTouchMode.ROTATING -> {
                                        val currentAngle = Math.toDegrees(Math.atan2((wy - pivotY).toDouble(), (wx - pivotX).toDouble())).toFloat()
                                        val deltaAngle = currentAngle - startAngle
                                        
                                        val m = Matrix()
                                        m.postRotate(deltaAngle, pivotX, pivotY)
                                        manager.applyTransform(m)
                                        startAngle = currentAngle
                                    }
                                    else -> {}
                                }
                                canvasView.invalidate()
                            }
                            MotionEvent.ACTION_UP -> {
                                val manager = sketchViewModel.selectionManager
                                if (selTouchMode == SelectionTouchMode.SELECTING_AREA) {
                                    canvasView.updateCurrentFill(null, 0)
                                    val dist = kotlin.math.hypot(wx - lastX, wy - lastY)
                                    val isAllLayers = sketchViewModel.selectionScope == SketcherViewModel.SelectionScope.ALL_LAYERS

                                    if (dist < 5f) {
                                        // Tap -> Select single
                                        if (isAllLayers) {
                                             for (i in sketchViewModel.layers.indices.reversed()) {
                                                 // Try to select on this layer. If hit, it replaced selection (due to false flag) and we break.
                                                 // If miss, it cleared selection, so we continue clean to next layer.
                                                 if (manager.selectSingleAt(wx, wy, sketchViewModel.layers[i], sketchViewModel.componentLibrary, addToSelection = false)) {
                                                     break 
                                                 }
                                             }
                                        } else {
                                            manager.selectSingleAt(wx, wy, sketchViewModel.layers[sketchViewModel.activeLayerIndex], sketchViewModel.componentLibrary, addToSelection = false)
                                        }
                                    } else {
                                        val selectionPath = if (sketchViewModel.currentSelectionMode == SketcherViewModel.SelectionMode.RECTANGLE) {
                                            android.graphics.Path().apply {
                                                addRect(
                                                    kotlin.math.min(lastX, wx), kotlin.math.min(lastY, wy),
                                                    kotlin.math.max(lastX, wx), kotlin.math.max(lastY, wy),
                                                    android.graphics.Path.Direction.CW
                                                )
                                            }
                                        } else {
                                            selPath
                                        }

                                        if (isAllLayers) {
                                            manager.clearSelection()
                                             sketchViewModel.layers.forEach { layer ->
                                                 manager.selectArea(selectionPath, layer, sketchViewModel.componentLibrary, addToSelection = true)
                                             }
                                        } else {
                                            manager.selectArea(selectionPath, sketchViewModel.layers[sketchViewModel.activeLayerIndex], sketchViewModel.componentLibrary, addToSelection = false)
                                        }
                                    }
                                }
                                selTouchMode = SelectionTouchMode.IDLE
                                canvasView.invalidate()
                            }
                        }
                        return@setOnTouchListener true
                    }

                    if (state.toolType == ToolType.ERASER) {
                        // OBJECT ERASER LOGIC (Robust activePointerId)
                        if (action == MotionEvent.ACTION_DOWN) {
                             activePointerId = event.getPointerId(0)
                        }
                        
                        // Only process if this is the active pointer
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex != -1) {
                             if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                                val touchPts = floatArrayOf(event.getX(pointerIndex), event.getY(pointerIndex))
                                inverseMatrix.mapPoints(touchPts)
                                val worldX = touchPts[0]
                                val worldY = touchPts[1]
                                
                                val erased = sketchViewModel.erase(worldX, worldY, state.size)
                                
                                if (erased) {
                                      canvasView.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                                     canvasView.redrawAllCache()
                                }
                             }
                        }
                        
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                            if (event.getPointerId(event.actionIndex) == activePointerId) {
                                activePointerId = -1
                            }
                        }
                        if (action == MotionEvent.ACTION_CANCEL) {
                            activePointerId = -1
                        }

                    } else if (state.brushFamily != null || isVectorTool) { 
                        // DRAWING LOGIC (Ink & Vector)
                        
                        when (action) {
                            MotionEvent.ACTION_DOWN -> {
                                activePointerId = event.getPointerId(0)
                                v.invalidate()
                                canvasView.invalidate()

                                // Effective Coordinates
                                val rawTouchPts = floatArrayOf(event.x, event.y)
                                inverseMatrix.mapPoints(rawTouchPts)
                                var effectiveX = rawTouchPts[0]
                                var effectiveY = rawTouchPts[1]

                                if (sketchViewModel.isSnapToGridEnabled) {
                                    val gridStepPx = UnitUtils.projectUnitsToPixels(
                                        value = sketchViewModel.gridConfig.spacing,
                                        unit = sketchViewModel.currentUnit,
                                        basePxPerMm = sketchViewModel.scaleConfig.basePixelsPerMillimeter
                                    )
                                    if (gridStepPx > 0) {
                                        effectiveX = (kotlin.math.round(effectiveX / gridStepPx) * gridStepPx)
                                        effectiveY = (kotlin.math.round(effectiveY / gridStepPx) * gridStepPx)
                                    }
                                }

                                lastInputX = effectiveX
                                lastInputY = effectiveY
                                lastInputPressure = event.pressure
                                
                                state.lastScreenX = event.x
                                state.lastScreenY = event.y
                                state.lastEventTime = event.eventTime
                                state.smoothedVelocityX = 0f
                                state.smoothedVelocityY = 0f

                                stabilizerX = effectiveX
                                stabilizerY = effectiveY
                                
                                // Vector/Fill Init
                                if (shouldCaptureVector) {
                                    state.vectorPoints.clear()
                                    var pressure = adjustPressure(event.pressure, 1.0f)
                                    state.vectorPoints.add(StrokePoint(effectiveX, effectiveY, pressure))
                                    
                                    if (state.toolType == ToolType.FILL || sketchViewModel.isFillModeEnabled) {
                                        state.vectorPoints.add(StrokePoint(effectiveX, effectiveY, adjustPressure(event.pressure, 1.0f)))
                                    }


                                    if (sketchViewModel.isFillModeEnabled || state.toolType == ToolType.FILL) {
                                        state.fillPath.reset()
                                        state.fillPath.moveTo(effectiveX, effectiveY)
                                        canvasView.updateCurrentFill(state.fillPath, if(state.toolType == ToolType.FILL) state.color else sketchViewModel.fillModeColor)
                                    }
                                }
                                
                                // INK Init
                                if (isInkTool) { 
                                    val snapScreenPts = floatArrayOf(effectiveX, effectiveY)
                                    cameraMatrix.mapPoints(snapScreenPts)
                                    
                                    // Construct Event
                                    val props = arrayOf(MotionEvent.PointerProperties())
                                    props[0] = MotionEvent.PointerProperties()
                                    event.getPointerProperties(0, props[0])
                                    val coords = arrayOf(MotionEvent.PointerCoords())
                                    coords[0] = MotionEvent.PointerCoords()
                                    event.getPointerCoords(0, coords[0])
                                    coords[0].x = snapScreenPts[0]
                                    coords[0].y = snapScreenPts[1]
                                    // Remove deprecated PRESSURE_PEN logic
                                    
                                    val snappedEvent = MotionEvent.obtain(
                                        event.downTime, event.eventTime, event.action, 1, props, coords,
                                        event.metaState, event.buttonState, event.xPrecision, event.yPrecision,
                                        event.deviceId, event.edgeFlags, event.source, event.flags
                                    )
                                    try {
                                        state.activeBrush?.let { brush ->
                                            strokeIdMap[activePointerId] = wetView.startStroke(snappedEvent, activePointerId, brush)
                                        }
                                    } finally {
                                        snappedEvent.recycle()
                                    }
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val pointerIndex = event.findPointerIndex(activePointerId)
                                if (pointerIndex == -1 || event.pointerCount > 1) {
                                    // INTERRUPTED
                                    if (activePointerId != -1) {
                                         if (strokeIdMap.containsKey(activePointerId)) {
                                            wetView.cancelStroke(strokeIdMap[activePointerId]!!, event)
                                            strokeIdMap.remove(activePointerId)
                                         }
                                         if (shouldCaptureVector) {
                                             state.vectorPoints.clear()
                                             canvasView.updateCurrentVectorPreview(null, null, 0)
                                             if (sketchViewModel.isFillModeEnabled) canvasView.updateCurrentFill(null, 0)
                                         }
                                         activePointerId = -1
                                    }
                                } else {
                                    // Process
                                    val rawTouchPts = floatArrayOf(event.getX(pointerIndex), event.getY(pointerIndex))
                                    inverseMatrix.mapPoints(rawTouchPts)
                                    var effectiveX = rawTouchPts[0]
                                    var effectiveY = rawTouchPts[1]

                                    if (sketchViewModel.isSnapToGridEnabled) {
                                        val gridStepPx = UnitUtils.projectUnitsToPixels(sketchViewModel.gridConfig.spacing, sketchViewModel.currentUnit, sketchViewModel.scaleConfig.basePixelsPerMillimeter)
                                        if (gridStepPx > 0) {
                                            effectiveX = (kotlin.math.round(effectiveX / gridStepPx) * gridStepPx)
                                            effectiveY = (kotlin.math.round(effectiveY / gridStepPx) * gridStepPx)
                                        }
                                    }

                                    // Apply Stabilization
                                    val stabilization = sketchViewModel.globalStabilizationLevel.coerceIn(0f, 0.95f)
                                    if (stabilization > 0f) {
                                        val factor = 1f - stabilization
                                        stabilizerX += (effectiveX - stabilizerX) * factor
                                        stabilizerY += (effectiveY - stabilizerY) * factor
                                        effectiveX = stabilizerX
                                        effectiveY = stabilizerY
                                    } else {
                                        stabilizerX = effectiveX
                                        stabilizerY = effectiveY
                                    }

                                    val dist = kotlin.math.hypot(effectiveX - lastInputX, effectiveY - lastInputY)
                                    // ... threshold ...
                                    if (dist > 1.0f) {
                                        lastInputX = effectiveX
                                        lastInputY = effectiveY
                                        lastInputPressure = event.getPressure(pointerIndex)
                                        
                                        val stabilizedPoint = StrokePoint(effectiveX, effectiveY, 0.5f) // No stabilizer
                                        
                                        if (shouldCaptureVector) {
                                             // ... Vector Add & Preview ...
                                            val p = adjustPressure(event.getPressure(pointerIndex), 1.0f)
                                            // Fill
                                            if (sketchViewModel.isFillModeEnabled || state.toolType == ToolType.FILL) {
                                                state.vectorPoints.add(StrokePoint(effectiveX, effectiveY, p))
                                            }

                                            // Fill
                                            if (sketchViewModel.isFillModeEnabled || state.toolType == ToolType.FILL) {
                                                state.fillPath.lineTo(effectiveX, effectiveY)
                                                val pp = android.graphics.Path(state.fillPath)
                                                pp.close()
                                                val c = if(state.toolType == ToolType.FILL) state.color else sketchViewModel.fillModeColor
                                                val a = (state.opacity * 255).toInt()
                                                canvasView.updateCurrentFill(pp, androidx.core.graphics.ColorUtils.setAlphaComponent(c, a))
                                            }
                                            

                                        }
                                        
                                        if (isInkTool) {
                                             val snapScreenPts = floatArrayOf(stabilizedPoint.x, stabilizedPoint.y)
                                             cameraMatrix.mapPoints(snapScreenPts)
                                             // Synthesize Move
                                             val props = arrayOf(MotionEvent.PointerProperties())
                                             props[0] = MotionEvent.PointerProperties()
                                             event.getPointerProperties(pointerIndex, props[0])
                                             val coords = arrayOf(MotionEvent.PointerCoords())
                                             coords[0] = MotionEvent.PointerCoords()
                                             event.getPointerCoords(pointerIndex, coords[0])
                                             coords[0].x = snapScreenPts[0]
                                             coords[0].y = snapScreenPts[1]
                                              // Legacy PRESSURE_PEN removal
                                             val ev = MotionEvent.obtain(event.downTime, event.eventTime, event.action, 1, props, coords, event.metaState, event.buttonState, event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags, event.source, event.flags)
                                             try {
                                                  strokeIdMap[activePointerId]?.let { wetView.addToStroke(ev, activePointerId, it, null) }
                                             } finally { ev.recycle() }
                                        }
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                                if (activePointerId != -1 && event.getPointerId(event.actionIndex) == activePointerId) {
                                    // Finish
                                    // Finish
                                    // val stabilizedPoint = stabilizer.update(lastInputX, lastInputY, 0.1f) 
                                    val stabilizedPoint = StrokePoint(lastInputX, lastInputY, 0.5f)

                                    if (shouldCaptureVector) {
                                         // Commit Vector/Fill
                                         if (state.toolType == ToolType.FILL || sketchViewModel.isFillModeEnabled) {
                                             state.vectorPoints.add(StrokePoint(stabilizedPoint.x, stabilizedPoint.y, adjustPressure(lastInputPressure, 1.0f)))
                                         }

                                         
                                         if (sketchViewModel.isFillModeEnabled || state.toolType == ToolType.FILL) {
                                              state.fillPath.lineTo(stabilizedPoint.x, stabilizedPoint.y)
                                              state.fillPath.close()
                                              val c = if(state.toolType == ToolType.FILL) state.color else sketchViewModel.fillModeColor
                                              val a = (state.opacity * 255).toInt()
                                              val fData = FillData(android.graphics.Path(state.fillPath), androidx.core.graphics.ColorUtils.setAlphaComponent(c, a))
                                              sketchViewModel.addFill(fData)
                                              canvasView.updateCurrentFill(null, 0)
                                              canvasView.bakeFill(fData)
                                              canvasView.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                                         }
                                         

                                    }
                                    
                                    if (isInkTool) {
                                        val snapScreenPts = floatArrayOf(stabilizedPoint.x, stabilizedPoint.y)
                                        cameraMatrix.mapPoints(snapScreenPts)
                                        // Synthesize Up
                                         val pointerIndex = event.findPointerIndex(activePointerId)
                                         // Safety check
                                         if (pointerIndex != -1) {
                                             val props = arrayOf(MotionEvent.PointerProperties())
                                             props[0] = MotionEvent.PointerProperties()
                                             event.getPointerProperties(pointerIndex, props[0])
                                             val coords = arrayOf(MotionEvent.PointerCoords())
                                             coords[0] = MotionEvent.PointerCoords()
                                             event.getPointerCoords(pointerIndex, coords[0])
                                             coords[0].x = snapScreenPts[0]
                                             coords[0].y = snapScreenPts[1]
                                             val ev = MotionEvent.obtain(event.downTime, event.eventTime, MotionEvent.ACTION_UP, 1, props, coords, event.metaState, event.buttonState, event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags, event.source, event.flags)
                                             try {
                                                  strokeIdMap[activePointerId]?.let { 
                                                      wetView.finishStroke(ev, activePointerId, it)
                                                      strokeIdMap.remove(activePointerId)
                                                  }
                                             } finally { ev.recycle() }
                                         }
                                    }
                                    activePointerId = -1
                                }
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                 if (activePointerId != -1) {
                                     strokeIdMap[activePointerId]?.let { wetView.cancelStroke(it, event); strokeIdMap.remove(activePointerId) }
                                     if (shouldCaptureVector) {
                                         state.vectorPoints.clear()
                                         canvasView.updateCurrentVectorPreview(null, null, 0)
                                         canvasView.updateCurrentFill(null, 0)
                                     }
                                     activePointerId = -1
                                 }
                            }
                        }
                    } else if (strokeIdMap.isNotEmpty()) {
                        // Cleanup
                        strokeIdMap.forEach { (_, sid) -> wetView.cancelStroke(sid, event) }
                        strokeIdMap.clear()
                        state.fillPath.reset()
                        canvasView.updateCurrentFill(null, 0)
                        activePointerId = -1
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
                canvasView.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)

                val currentConfig = brushTypes.find { it.type == sketchViewModel.currentTool } ?: brushTypes.first()
                
                state.toolType = sketchViewModel.currentTool
                state.brushFamily = currentConfig.family
                state.color = sketchViewModel.currentColor
                state.size = sketchViewModel.currentSize
                state.opacity = sketchViewModel.currentOpacity
                
                // Sync Active Configs for Perfect Freehand (Front Buffer)
                val alpha = (sketchViewModel.currentOpacity * 255).toInt()
                val blendedColor = androidx.core.graphics.ColorUtils.setAlphaComponent(sketchViewModel.currentColor, alpha)
                canvasView.activeColor = blendedColor
                canvasView.activeSize = sketchViewModel.currentSize
                canvasView.activeFreehandSettings = sketchViewModel.currentFreehandSettings

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
                
                // Sync Input Config
                canvasView.globalStabilizationLevel = sketchViewModel.globalStabilizationLevel
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
                modifier = Modifier.align(Alignment.TopCenter).zIndex(1000f),
                canUndo = sketchViewModel.canUndo,
                onUndo = { sketchViewModel.undo(); canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext); canvasViewRef?.redrawAllCache() },
                canRedo = sketchViewModel.canRedo,
                onRedo = { sketchViewModel.redo(); canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext); canvasViewRef?.redrawAllCache() },
                onLayersClick = { showLayerManager = !showLayerManager },
                onSave = { saveLauncher.launch("project.skc") },
                onLoad = { loadLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                onImportImage = { imagePickerLauncher.launch(arrayOf("image/*", "image/svg+xml")) },
                onSaveTemplate = { name -> sketchViewModel.saveTemplate(context, name) },
                onLoadTemplate = { file -> sketchViewModel.loadFromTemplate(context, file) },
                onExportSvg = { exportSvgLauncher.launch("drawing.svg") },
                onNewDrawing = {
                    sketchViewModel.clear()
                    // Reset View Camera
                    cameraMatrix.reset()
                    cameraMatrix.invert(inverseMatrix)
                    canvasViewRef?.setCameraMatrix(cameraMatrix)
                    canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                    canvasViewRef?.redrawAllCache()
                    canvasViewRef?.invalidate()
                    currentZoom = 1f 
                },
                onSettingsClick = { showSettingsPopup = true },
                onGridClick = { showGridSettings = true },
                onZoomReset = { 
                    sketchViewModel.resetCamera()
                    // sketchViewModel will trigger update via state change in update block
                },
                onZoomExtend = {
                    sketchViewModel.fitContent()
                },
                activeLayerName = if (sketchViewModel.layers.isNotEmpty() && sketchViewModel.activeLayerIndex in sketchViewModel.layers.indices) {
                    sketchViewModel.layers[sketchViewModel.activeLayerIndex].name
                } else "Layer",
                toolbarBackgroundColor = sketchViewModel.toolbarBackgroundColor,
                toolbarAlpha = sketchViewModel.toolbarAlpha,
                isToolbarBlurEnabled = sketchViewModel.isToolbarBlurEnabled
            )
            
            // SCALE INDICATOR (Top Left)
            ScaleIndicator(
                scaleConfig = sketchViewModel.scaleConfig,
                currentUnit = sketchViewModel.currentUnit,
                currentZoom = currentZoom,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .zIndex(1000f)
                    .padding(top = 80.dp, start = 16.dp) // Below Toobar
            )

            BottomMenuBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .zIndex(1000f),
                tools = brushTypes,
                selectedTool = sketchViewModel.currentTool,
                activeDrawingTool = sketchViewModel.lastDrawingTool,
                onToolSelected = { newTool -> 
                    if (sketchViewModel.currentTool == ToolType.SELECTION && newTool != ToolType.SELECTION) {
                        sketchViewModel.selectionManager.clearSelection()
                        canvasViewRef?.invalidate()
                    }
                    sketchViewModel.selectTool(newTool)
                },
                colorSlots = sketchViewModel.availableColors,
                selectedColorSlotIndex = sketchViewModel.selectedColorIndex,
                onColorSlotSelected = { sketchViewModel.selectedColorIndex = it; sketchViewModel.updateCurrentColorFromSlot() },
                onColorChangeRequest = { showColorPicker = true },
                selectedSize = sketchViewModel.currentSize,
                onSizeChangeRequest = { 
                    showSizePopup = !showSizePopup 
                },
                isEraserActive = sketchViewModel.currentTool == ToolType.ERASER,
                onEraserToggle = {
                    if (sketchViewModel.currentTool == ToolType.ERASER) {
                         sketchViewModel.selectTool(sketchViewModel.lastDrawingTool)
                    } else {
                         sketchViewModel.selectTool(ToolType.ERASER)
                    }
                },
                showToolPopup = showToolPopup,
                onShowToolPopupChange = { showToolPopup = it },
                isFillModeEnabled = sketchViewModel.isFillModeEnabled,
                onToggleFillMode = { sketchViewModel.isFillModeEnabled = !sketchViewModel.isFillModeEnabled },
                fillColor = sketchViewModel.fillModeColor,
                onFillColorChangeRequest = { showFillColorPicker = true },
                backgroundColor = sketchViewModel.backgroundColor,
                onBackgroundColorChangeRequest = { showBackgroundColorPicker = true },
                onDeleteSelection = { 
                    sketchViewModel.deleteSelection()
                    canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                    canvasViewRef?.redrawAllCache()
                    canvasViewRef?.invalidate()
                },
                selectionMode = sketchViewModel.currentSelectionMode,
                onSelectionModeChanged = { sketchViewModel.currentSelectionMode = it },
                isAspectRatioLocked = sketchViewModel.isSelectionAspectRatioLocked,
                onToggleAspectRatioLock = { sketchViewModel.isSelectionAspectRatioLocked = !sketchViewModel.isSelectionAspectRatioLocked },
                onGroup = { 
                    sketchViewModel.groupSelection()
                    canvasViewRef?.invalidate()
                },
                onUngroup = { 
                    sketchViewModel.ungroupSelection()
                    canvasViewRef?.invalidate()
                },
                onMakeComponent = {
                    sketchViewModel.makeComponent()
                    canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                    canvasViewRef?.invalidate()
                },
                onEnterEditMode = {
                    sketchViewModel.enterEditMode()
                    canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                    canvasViewRef?.invalidate()
                },
                canEnterEditMode = sketchViewModel.canEnterEditMode,
                onExitEditMode = {
                    sketchViewModel.exitEditMode()
                    canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                    canvasViewRef?.invalidate()
                },
                isEditingContextActive = sketchViewModel.editingContext != null,
                onCopy = { sketchViewModel.copy() },
                onCut = { 
                    sketchViewModel.cut()
                    canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                    canvasViewRef?.redrawAllCache()
                    canvasViewRef?.invalidate()
                },
                onPaste = { 
                    sketchViewModel.paste()
                    canvasViewRef?.setLayers(sketchViewModel.layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                    canvasViewRef?.redrawAllCache()
                    canvasViewRef?.invalidate()
                },
                canPaste = sketchViewModel.canPaste,
                selectionScope = sketchViewModel.selectionScope,
                onToggleSelectionScope = {
                    sketchViewModel.selectionScope = if (sketchViewModel.selectionScope == SketcherViewModel.SelectionScope.CURRENT_LAYER) 
                        SketcherViewModel.SelectionScope.ALL_LAYERS 
                    else 
                        SketcherViewModel.SelectionScope.CURRENT_LAYER
                },
                isGroupSelected = sketchViewModel.isGroupSelected,
                isSelectionEmpty = sketchViewModel.isSelectionEmpty,
                 isDebugWireframe = sketchViewModel.isDebugWireframe,
                onToggleDebugWireframe = { sketchViewModel.isDebugWireframe = !sketchViewModel.isDebugWireframe },
                currentScaleConfig = sketchViewModel.scaleConfig,
                onUpdateProjectConfig = { unit, resolution -> 
                    sketchViewModel.updateScaleConfig(unit, resolution)
                },
                toolbarBackgroundColor = sketchViewModel.toolbarBackgroundColor,
                onToolbarBackgroundColorChanged = { sketchViewModel.updateToolbarBackgroundColor(it) },
                toolbarAlpha = sketchViewModel.toolbarAlpha,
                isToolbarBlurEnabled = sketchViewModel.isToolbarBlurEnabled,
                onConfigureTool = {
                    showToolSettingsPopup = true
                },
                onInputSettingsClick = {
                    showInputSettings = true
                }
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
                initialColor = sketchViewModel.fillModeColor,
                onDismiss = { showFillColorPicker = false },
                onColorSelected = { color ->
                    sketchViewModel.fillModeColor = color
                    showFillColorPicker = false
                }
            )
        }
        

        
        if (showGridSettings) {
            GridSettingsDialog(
                currentGridConfig = sketchViewModel.gridConfig,
                isSnapEnabled = sketchViewModel.isSnapToGridEnabled,
                currentUnit = sketchViewModel.currentUnit,
                onUpdateGrid = { visible, spacing, color, color2, color3 -> 
                    sketchViewModel.updateGridConfig(visible, spacing, color, color2, color3) 
                },
                onUpdateSnap = { sketchViewModel.isSnapToGridEnabled = it },
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
                initialColor = sketchViewModel.availableColors[sketchViewModel.selectedColorIndex],
                onDismiss = { showColorPicker = false },
                onColorSelected = { color ->
                    if (sketchViewModel.selectedColorIndex in sketchViewModel.availableColors.indices) {
                        sketchViewModel.availableColors[sketchViewModel.selectedColorIndex] = color
                        sketchViewModel.updateCurrentColorFromSlot()
                    }
                    showColorPicker = false
                }
            )
        }

        if (showSizePopup) {
            SizeSelectorPopup(
                currentSize = sketchViewModel.currentSize,
                onSizeChanged = { sketchViewModel.setToolSize(it) },
                currentOpacity = sketchViewModel.currentOpacity,
                onOpacityChanged = { sketchViewModel.setToolOpacity(it) },
                presets = sketchViewModel.brushSizePresets,
                onPresetSelected = { sketchViewModel.setToolSize(it) },
                onPresetSave = { index, size -> sketchViewModel.updateBrushSizePreset(index, size) },
                activeToolType = sketchViewModel.currentTool,
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
               isDebugWireframe = sketchViewModel.isDebugWireframe,
               onToggleDebugWireframe = { sketchViewModel.isDebugWireframe = !sketchViewModel.isDebugWireframe },
               currentScaleConfig = sketchViewModel.scaleConfig,
               onUpdateProjectConfig = { unit, resolution -> 
                   sketchViewModel.updateScaleConfig(unit, resolution)
               },
               toolbarBackgroundColor = sketchViewModel.toolbarBackgroundColor,
               onToolbarBackgroundColorChanged = { sketchViewModel.updateToolbarBackgroundColor(it) },
               toolbarAlpha = sketchViewModel.toolbarAlpha,
               onToolbarAlphaChanged = { sketchViewModel.updateToolbarAlpha(it) },
               isToolbarBlurEnabled = sketchViewModel.isToolbarBlurEnabled,
               onToggleToolbarBlur = { sketchViewModel.toggleToolbarBlur() }
           )
        }
        
        // --- TOOL SETTINGS POPUP ---
        
        if (showToolSettingsPopup) {
             com.skecher.sketchercompanionv1.ui.ToolSettingsPopup(
                 toolType = sketchViewModel.currentTool,
                 freehandSettings = sketchViewModel.currentFreehandSettings,
                 onFreehandSettingsChanged = { sketchViewModel.updateFreehandSettings(it) },
                 onDismiss = { showToolSettingsPopup = false }
             )
        }

        // --- INPUT SETTINGS POPUP ---
        if (showInputSettings) {
            InputSettingsPopup(
                viewModel = sketchViewModel,
                onDismiss = { showInputSettings = false }
            )
        }
    }
}



@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun BottomMenuBar(
    modifier: Modifier = Modifier,
    tools: List<BrushTypeConfig>,
    selectedTool: ToolType,
    activeDrawingTool: ToolType, // New Param
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
    isDebugWireframe: Boolean,
    onToggleDebugWireframe: () -> Unit,
    currentScaleConfig: ScaleConfig,
    onUpdateProjectConfig: (String, Float) -> Unit,
    toolbarBackgroundColor: Int,
    onToolbarBackgroundColorChanged: (Int) -> Unit,
    onDeleteSelection: () -> Unit,
    selectionMode: SketcherViewModel.SelectionMode,
    onSelectionModeChanged: (SketcherViewModel.SelectionMode) -> Unit,
    isAspectRatioLocked: Boolean,
    onToggleAspectRatioLock: () -> Unit,
    onGroup: () -> Unit,
    onUngroup: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onPaste: () -> Unit,
    canPaste: Boolean,
    selectionScope: SketcherViewModel.SelectionScope,
    onToggleSelectionScope: () -> Unit,
    isGroupSelected: Boolean,
    isSelectionEmpty: Boolean,
    onMakeComponent: () -> Unit,
    onEnterEditMode: () -> Unit,
    canEnterEditMode: Boolean,
    onExitEditMode: () -> Unit,
    isEditingContextActive: Boolean,
    fillColor: Int,
    onFillColorChangeRequest: () -> Unit,
    backgroundColor: Int,
    onBackgroundColorChangeRequest: () -> Unit,
    toolbarAlpha: Float,
    isToolbarBlurEnabled: Boolean,
    onConfigureTool: () -> Unit = {}, // New Callback
    onInputSettingsClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Separate background layer to apply blur/color without affecting content
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(toolbarBackgroundColor).copy(alpha = if (isToolbarBlurEnabled) (toolbarAlpha * 0.7f).coerceIn(0f, 1f) else toolbarAlpha)).then(if (isToolbarBlurEnabled) Modifier.blur(16.dp) else Modifier)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .horizontalScroll(rememberScrollState()), // Make it scrollable
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BACK BUTTON (Exit Isolation)
            if (isEditingContextActive) {
                IconButton(onClick = onExitEditMode) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit Isolation", tint = Color.Red)
                }
                Text("Isolado", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                VerticalDivider(modifier = Modifier.height(24.dp))
            }
            // TOOL SELECTOR
            Box {
                // Use activeDrawingTool to show the persistent drawing tool
                val currentToolConfig = tools.find { it.type == activeDrawingTool } ?: tools.first()
                val icon = currentToolConfig.icon
                
                // Custom Button with Long Click
                Box(
                     modifier = Modifier
                         .size(40.dp)
                         .clip(CircleShape)
                         .combinedClickable(
                             onClick = { onToolSelected(activeDrawingTool) },
                             onLongClick = { onShowToolPopupChange(true) }
                         ),
                     contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = "Tool", tint = Color.Black)
                }
                
                DropdownMenu(
                    expanded = showToolPopup,
                    onDismissRequest = { onShowToolPopupChange(false) }
                ) {
                    tools.forEach { tool ->
                        // Don't show Eraser/Selection in this specific brush list if handled elsewhere?
                        // User wants to change "The Tool".
                        if (tool.type != ToolType.ERASER) {
                            DropdownMenuItem(
                                text = { 
                                    Text(getToolName(tool.type)) 
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
            } // End Box (Tool Selector)

            // SETTINGS BUTTON (For All Active Drawing Tools)
            // SETTINGS BUTTON (For All Active Drawing Tools)
            if (activeDrawingTool == ToolType.FREEHAND) {
                 IconButton(onClick = onConfigureTool) {
                     Icon(androidx.compose.material.icons.Icons.Filled.Build, contentDescription = "Configurar", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                 }
                 VerticalDivider(modifier = Modifier.height(24.dp))
            }

            // GLOBAL INPUT SETTINGS (Tune Icon)
            IconButton(onClick = onInputSettingsClick) {
                 Icon(Icons.Default.Tune, contentDescription = "Ajustes de Entrada", tint = MaterialTheme.colorScheme.secondary)
            }
            VerticalDivider(modifier = Modifier.height(24.dp))

            // COLOR SLOTS
            if (selectedTool != ToolType.SELECTION && selectedTool != ToolType.ERASER) {
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
            }

            // FILL MODE TOGGLE
            val isFillTool = selectedTool == ToolType.FILL
            if (!isFillTool && selectedTool != ToolType.SELECTION && selectedTool != ToolType.ERASER) {
                Row(
                     verticalAlignment = Alignment.CenterVertically
                ) {
                     IconButton(
                         onClick = onToggleFillMode
                     ) {
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
            }

            // SIZE PREVIEW
            if (selectedTool != ToolType.SELECTION && selectedTool != ToolType.ERASER) {
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
            }

            // ERASER
            if (selectedTool != ToolType.SELECTION) {
                IconButton(onClick = onEraserToggle) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Eraser",
                        tint = if (isEraserActive) Color.Red else Color.Gray
                    )
                }

                VerticalDivider(modifier = Modifier.height(24.dp))
            }
            
            // BACKGROUND COLOR PICKER
            if (selectedTool != ToolType.SELECTION && selectedTool != ToolType.ERASER) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(backgroundColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable(onClick = onBackgroundColorChangeRequest),
                    contentAlignment = Alignment.Center
                ) {
                    if (backgroundColor == android.graphics.Color.WHITE) {
                        Icon(Icons.Default.Palette, contentDescription = "Background", tint = Color.Black.copy(alpha=0.5f), modifier = Modifier.size(16.dp))
                    }
                }
            }

            // SHARED SELECTION/ERASER TOOLS
            if (selectedTool == ToolType.SELECTION || selectedTool == ToolType.ERASER) {
                IconButton(onClick = onToggleSelectionScope) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = "Selection Scope",
                        tint = if (selectionScope == SketcherViewModel.SelectionScope.ALL_LAYERS) Color.Blue else Color.Gray
                    )
                }
                VerticalDivider(modifier = Modifier.height(24.dp))
            }

             // --- SELECTION SPECIFIC ---
             if (selectedTool == ToolType.SELECTION) {
                 VerticalDivider(modifier = Modifier.height(24.dp))
                 
                 // Mode Toggles
                 var showSelectionMenu by remember { mutableStateOf(false) }
                 Box {
                     IconButton(onClick = { showSelectionMenu = true }) {
                         Icon(
                             if (selectionMode == SketcherViewModel.SelectionMode.RECTANGLE) Icons.Default.AspectRatio else Icons.Default.Gesture,
                             contentDescription = "Selection Mode",
                             tint = Color.Blue
                         )
                     }
                     
                     DropdownMenu(
                         expanded = showSelectionMenu,
                         onDismissRequest = { showSelectionMenu = false }
                     ) {
                         DropdownMenuItem(
                             text = { Text("Rectangle Selection") },
                             leadingIcon = { Icon(Icons.Default.AspectRatio, null) },
                             onClick = {
                                 onSelectionModeChanged(SketcherViewModel.SelectionMode.RECTANGLE)
                                 showSelectionMenu = false
                             }
                         )
                         DropdownMenuItem(
                             text = { Text("Lasso Selection") },
                             leadingIcon = { Icon(Icons.Default.Gesture, null) },
                             onClick = {
                                 onSelectionModeChanged(SketcherViewModel.SelectionMode.FREEHAND)
                                 showSelectionMenu = false
                             }
                         )
                     }
                 }


                 // ACTION PLACEHOLDERS
                 IconButton(onClick = onCopy, enabled = !isSelectionEmpty) {
                     Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = if (!isSelectionEmpty) Color.Black else Color.Gray)
                 }
                 IconButton(onClick = onCut, enabled = !isSelectionEmpty) {
                     Icon(Icons.Default.ContentCut, contentDescription = "Cut", tint = if (!isSelectionEmpty) Color.Black else Color.Gray)
                 }
                 IconButton(onClick = onPaste, enabled = canPaste) {
                     Icon(
                         Icons.Default.ContentPaste, 
                         contentDescription = "Paste", 
                         tint = if (canPaste) Color.Blue else Color.Gray
                     )
                 }

                 VerticalDivider(modifier = Modifier.height(24.dp))

                 IconButton(onClick = onGroup, enabled = !isSelectionEmpty) {
                     Icon(Icons.Default.Group, contentDescription = "Group", tint = if (!isSelectionEmpty) Color.Black else Color.Gray)
                 }

                  IconButton(onClick = onUngroup, enabled = isGroupSelected) {
                      Icon(Icons.Default.Schema, contentDescription = "Ungroup", tint = if (isGroupSelected) Color.Black else Color.Gray)
                  }

                  IconButton(onClick = onMakeComponent, enabled = !isSelectionEmpty) {
                      Icon(Icons.Default.Extension, contentDescription = "Make Component", tint = if (!isSelectionEmpty) Color.Black else Color.Gray)
                  }

                  IconButton(onClick = onEnterEditMode, enabled = canEnterEditMode) {
                      Icon(Icons.Default.Edit, contentDescription = "Edit Isolated", tint = if (canEnterEditMode) Color.Blue else Color.Gray)
                  }

                 VerticalDivider(modifier = Modifier.height(24.dp))

                 // DELETE SELECTION
                 IconButton(onClick = onDeleteSelection, enabled = !isSelectionEmpty) {
                     Icon(Icons.Default.Delete, contentDescription = "Delete Selection", tint = if (!isSelectionEmpty) Color.Red else Color.Gray)
                 }

                 VerticalDivider(modifier = Modifier.height(24.dp))

                 // ASPECT RATIO TOGGLE
                 IconButton(onClick = onToggleAspectRatioLock) {
                     Icon(
                         if (isAspectRatioLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                         contentDescription = "Lock Aspect Ratio",
                         tint = if (isAspectRatioLocked) Color.Blue else Color.Gray
                     )
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
    onImportImage: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    onLoadTemplate: (java.io.File) -> Unit,
    onExportSvg: () -> Unit,
    onNewDrawing: () -> Unit,
    onSettingsClick: () -> Unit,
    onGridClick: () -> Unit,
    onZoomReset: () -> Unit,
    onZoomExtend: () -> Unit,
    activeLayerName: String,
    toolbarBackgroundColor: Int,
    toolbarAlpha: Float,
    isToolbarBlurEnabled: Boolean
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp, start = 16.dp, end = 16.dp)
    ) {
        // Separate background layer to apply blur/color without affecting content
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(32.dp))
                .background(Color(toolbarBackgroundColor).copy(alpha = if (isToolbarBlurEnabled) (toolbarAlpha * 0.7f).coerceIn(0f, 1f) else toolbarAlpha)).then(if (isToolbarBlurEnabled) Modifier.blur(16.dp) else Modifier)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
        // 1. LEFT: Project & Tools & Zoom
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Project Menu
            FileMenu(
                onNewDrawing = onNewDrawing,
                onSave = onSave,
                onLoad = onLoad,
                onImportImage = onImportImage,
                onSettingsClick = onSettingsClick,
                onSaveTemplate = onSaveTemplate,
                onLoadTemplate = onLoadTemplate,
                onExportSvg = onExportSvg
            )

            // Grid
            IconButton(onClick = onGridClick) {
                Icon(Icons.Default.GridOn, contentDescription = "Grid")
            }

             // Zoom Controls
             IconButton(onClick = onZoomReset) {
                 // "100%" Icon usually text
                 Box(contentAlignment = Alignment.Center) {
                     Icon(Icons.Default.Search, contentDescription = "Zoom 100%")
                     Text("1:1", fontSize = 8.sp, fontWeight = FontWeight.Bold)
                 }
             }
             
             IconButton(onClick = onZoomExtend) {
                 Icon(Icons.Default.AspectRatio, contentDescription = "Zoom Extend")
             }
        }

        // 2. CENTER: Undo/Redo (Perfectly Centered)
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_undo), tint = if (canUndo) Color.Black else Color.LightGray)
            }
            // Small spacer betweeen undo/redo?
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.action_redo), tint = if (canRedo) Color.Black else Color.LightGray)
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
    presets: List<Float>,
    onPresetSelected: (Float) -> Unit,
    onPresetSave: (Int, Float) -> Unit,
    activeToolType: ToolType,
    onDismiss: () -> Unit
) {
    // Visibility Logic
    // Visibility Logic
    val showSize = activeToolType != ToolType.FILL && activeToolType != ToolType.SELECTION
    val showOpacity = activeToolType != ToolType.SELECTION
    // Removed Stabilizer and Pressure logic for cleanup

    // Non-linear Slider Logic (Quadratic)
    val minSize = 1f
    val maxSize = 100f
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
            
            if (showSize) {
                Text("${stringResource(R.string.label_size)}: ${currentSize.toInt()}")
                
                Slider(
                    value = sliderValue,
                    onValueChange = { t ->
                        sliderValue = t
                        val nonLinearSize = minSize + (maxSize - minSize) * (t * t)
                        onSizeChanged(nonLinearSize)
                    },
                    valueRange = 0f..1f
                )
                
                // PRESETS
                Text(stringResource(R.string.popup_presets_hint), fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
                
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
                                        val newT = kotlin.math.sqrt(((size - minSize) / (maxSize - minSize)).coerceAtLeast(0f))
                                        sliderValue = newT
                                    },
                                    onLongClick = { onPresetSave(index, currentSize) }
                                )
                                .border(1.dp, Color.Gray, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
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
            }
            
            if (showOpacity) {
                Text("${stringResource(R.string.label_opacity)}: ${(currentOpacity * 100).toInt()}%")
                Slider(
                    value = currentOpacity,
                    onValueChange = onOpacityChanged,
                    valueRange = 0.01f..1f
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
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
    isDebugWireframe: Boolean,
    onToggleDebugWireframe: () -> Unit,
    currentScaleConfig: ScaleConfig,
    onUpdateProjectConfig: (String, Float) -> Unit,
    toolbarBackgroundColor: Int,
    onToolbarBackgroundColorChanged: (Int) -> Unit,
    toolbarAlpha: Float,
    onToolbarAlphaChanged: (Float) -> Unit,
    isToolbarBlurEnabled: Boolean,
    onToggleToolbarBlur: () -> Unit
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
            Text("${stringResource(R.string.settings_title)} & ${stringResource(R.string.project_settings)}", style = MaterialTheme.typography.titleLarge)
            
            // --- PROJECT CONFIGURATION ---
            Text(stringResource(R.string.project_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // Resolution
            Column {
                Text(stringResource(R.string.settings_base_resolution), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = resolutionText,
                    onValueChange = { resolutionText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.settings_resolution_hint), fontSize = 10.sp, color = Color.Gray)
            }
            
            // Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_unit))
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
            Text(stringResource(R.string.app_prefs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_lock_rotation))
                Switch(checked = isRotationLocked, onCheckedChange = { onToggleRotationLock() })
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_stylus_only))
                Switch(checked = isPalmRejectionEnabled, onCheckedChange = { onTogglePalmRejection() })
            }
            
            Column {
                Text("${stringResource(R.string.settings_interface_scale)}: ${(interfaceScale * 100).toInt()}%")
                Slider(
                    value = interfaceScale,
                    onValueChange = onInterfaceScaleChanged,
                    valueRange = 0.5f..2.0f
                )
            }

            HorizontalDivider()
            
            // --- TOOLBAR APPEARANCE ---
            Text("Apariencia de Barras", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            var showToolbarColorPicker by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color de Fondo")
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(toolbarBackgroundColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showToolbarColorPicker = true }
                )
            }
            
            if (showToolbarColorPicker) {
                ColorPickerDialog(
                    initialColor = toolbarBackgroundColor,
                    onDismiss = { showToolbarColorPicker = false },
                    onColorSelected = { 
                        onToolbarBackgroundColorChanged(it)
                        showToolbarColorPicker = false
                    }
                )
            }
            
            Column {
                Text("Transparencia: ${(toolbarAlpha * 100).toInt()}%")
                Slider(
                    value = toolbarAlpha,
                    onValueChange = onToolbarAlphaChanged,
                    valueRange = 0.0f..1.0f
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Efecto de Desenfoque (Blur)")
                Switch(checked = isToolbarBlurEnabled, onCheckedChange = { onToggleToolbarBlur() })
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_debug_wireframe))
                Switch(checked = isDebugWireframe, onCheckedChange = { onToggleDebugWireframe() })
            }
            
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onDismiss, colors = ButtonDefaults.textButtonColors()) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val res = resolutionText.toFloatOrNull()
                    if (res != null) {
                        onUpdateProjectConfig(selectedUnit.symbol, res)
                    }
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_apply))
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
                Text(stringResource(R.string.layer_title), style = MaterialTheme.typography.titleLarge)
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
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.layer_add))
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
    isSnapEnabled: Boolean,
    currentUnit: DistanceUnit,
    onUpdateGrid: (Boolean, Float, Int, Int, Int) -> Unit,
    onUpdateSnap: (Boolean) -> Unit,
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
            Text(stringResource(R.string.grid_title), style = MaterialTheme.typography.titleLarge)
            
            // Grid Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.grid_show))
                Switch(
                    checked = isVisible, 
                    onCheckedChange = { 
                        isVisible = it
                        updateConfig()
                    }
                )
            }

            // Snap Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.grid_snap))
                Switch(
                    checked = isSnapEnabled, 
                    onCheckedChange = { onUpdateSnap(it) }
                )
            }
            
            HorizontalDivider()

            // Unit Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_unit))
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
                Text("${stringResource(R.string.grid_spacing)} (${currentUnit.symbol})")
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
            
            Text(stringResource(R.string.label_line_colors), style = MaterialTheme.typography.labelMedium)
            
            // Primary Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.grid_primary))
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
                Text(stringResource(R.string.grid_secondary))
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
                Text(stringResource(R.string.grid_tertiary))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(tertiaryColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showTertiaryPicker = true }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    updateConfig()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_apply))
                }
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



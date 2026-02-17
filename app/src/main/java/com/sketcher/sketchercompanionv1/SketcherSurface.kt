package com.sketcher.sketchercompanionv1

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
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.sketcher.sketchercompanionv1.ui.FileMenu
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfImportDialog // Import
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfExportDialog // Import
import com.sketcher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme
import com.sketcher.sketchercompanionv1.GroupElement

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
import com.sketcher.sketchercompanionv1.ui.ColorPickerDialog
import com.sketcher.sketchercompanionv1.ui.ScaleIndicator
import com.sketcher.sketchercompanionv1.ui.ToolSettingsPopup
import com.sketcher.sketchercompanionv1.ui.LazyStrokePopup
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.UnitUtils
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
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
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
    // val family: BrushFamily?, // Removed
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
    // BrushFamily removed (Ink)
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

    // Active Brush Logic removed (Ink)

    fun updateActiveBrush(currentZoom: Float) {
        // No-op: Logic moved to VectorStroke generator or handled by ViewModel settings
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
    sketchViewModel: SketcherViewModel,
    projectActions: com.sketcher.sketchercompanionv1.ui.model.ProjectActions
) {
    val context = LocalContext.current
    val activity = context.findActivity()

    var canvasViewRef by remember { mutableStateOf<SketcherCanvasView?>(null) }

    // Detectamos cambio de configuración (rotación) automáticamente con Compose
    val configuration = LocalConfiguration.current
    var showLazyStrokePopup by rememberSaveable { mutableStateOf(false) }
    
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    var showColorPicker by remember { mutableStateOf(false) }
    var showToolPopup by remember { mutableStateOf(false) }
    var showSizePopup by remember { mutableStateOf(false) }
    var showLayerManager by remember { mutableStateOf(false) }
    var showBackgroundColorPicker by remember { mutableStateOf(false) } 
    var showToolSettingsPopup by remember { mutableStateOf(false) }
    var showFillColorPicker by remember { mutableStateOf(false) }
    var showHomeSavedFeedback by remember { mutableStateOf(false) }
    
    // UI Feedback State
    val showHomeRestoredFeedback = sketchViewModel.showHomeRestoredFeedback
    LaunchedEffect(showHomeRestoredFeedback) {
        if (showHomeRestoredFeedback) {
            kotlinx.coroutines.delay(2000)
            sketchViewModel.showHomeRestoredFeedback = false
        }
    }

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
    
    // Auto-hide Home Saved Feedback
    LaunchedEffect(showHomeSavedFeedback) {
        if (showHomeSavedFeedback) {
            kotlinx.coroutines.delay(2000)
            showHomeSavedFeedback = false
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
    val onToggleTooltips: () -> Unit = { sketchViewModel.toggleTooltips() }
    
    val isRotationLocked = sketchViewModel.isRotationLocked
    val isPalmRejectionEnabled = sketchViewModel.isPalmRejectionEnabled
    val showTooltips = sketchViewModel.showTooltips
    val interfaceScale = sketchViewModel.interfaceScale
    val toolbarAlpha = sketchViewModel.toolbarAlpha
    val isToolbarBlurEnabled = sketchViewModel.isToolbarBlurEnabled
    val isDebugWireframe = sketchViewModel.isDebugWireframe
    val toolbarBackgroundColor = sketchViewModel.toolbarBackgroundColor
    val onToolbarBackgroundColorChanged: (Int) -> Unit = { sketchViewModel.updateToolbarBackgroundColor(it) }
    
    val activeToolType = sketchViewModel.currentTool


    // VIEW REFS


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
        
        // Update WetView Brush Size - Removed
    }

    val brushTypes = listOf(
        BrushTypeConfig(ToolType.FREEHAND, Icons.Default.Create, R.string.tool_pressure_pen), 
        BrushTypeConfig(ToolType.FILL, Icons.Default.FormatPaint, R.string.tool_fill),
        BrushTypeConfig(ToolType.SELECTION, Icons.Default.TouchApp, R.string.tool_selection)
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
        // FIX: Force update active size with UNIT CONVERSION
        val sizePx = com.sketcher.sketchercompanionv1.utils.UnitUtils.projectUnitsToPixels(
             value = sketchViewModel.currentSize,
             unit = sketchViewModel.currentUnit,
             basePxPerMm = sketchViewModel.scaleConfig.basePixelsPerMillimeter
         )
        canvasViewRef?.activeSize = sizePx
    }

    LaunchedEffect(sketchViewModel.isDebugWireframe) {
        canvasViewRef?.isDebugWireframeByVM = sketchViewModel.isDebugWireframe
    }

    LaunchedEffect(canvasViewRef) {
        canvasViewRef?.selectionManager = sketchViewModel.selectionManager
        canvasViewRef?.activeStrokeType = sketchViewModel.currentStrokeType
    }

    LaunchedEffect(sketchViewModel.currentFreehandSettings, canvasViewRef) {
        canvasViewRef?.activeFreehandSettings = sketchViewModel.currentFreehandSettings
    }

    LaunchedEffect(sketchViewModel.currentSize, canvasViewRef) {
        // FIX: Ensure active size conversion (Units -> Pixels) when size changes (e.g. Slider or Tool Switch)
        val sizePx = com.sketcher.sketchercompanionv1.utils.UnitUtils.projectUnitsToPixels(
             value = sketchViewModel.currentSize,
             unit = sketchViewModel.currentUnit,
             basePxPerMm = sketchViewModel.scaleConfig.basePixelsPerMillimeter
         )
        canvasViewRef?.activeSize = sizePx
    }
    
    val strokeColor by sketchViewModel.strokeColor.collectAsStateWithLifecycle()
    val fillColor by sketchViewModel.fillColor.collectAsStateWithLifecycle()
    val isStrokeActive by sketchViewModel.isStrokeActive.collectAsStateWithLifecycle()
    val isFillActive by sketchViewModel.isFillActive.collectAsStateWithLifecycle()

    LaunchedEffect(strokeColor, canvasViewRef) {
        canvasViewRef?.activeStrokeColor = strokeColor
    }
    
    LaunchedEffect(fillColor, canvasViewRef) {
        canvasViewRef?.activeFillColor = fillColor
    }
    
    LaunchedEffect(isStrokeActive, canvasViewRef) {
        canvasViewRef?.isStrokeActive = isStrokeActive
    }
    
    LaunchedEffect(isFillActive, canvasViewRef) {
        canvasViewRef?.isFillActive = isFillActive
    }

    // --- FIX: STARTUP AWAKENER REMOVED (Replaced by OnLayoutChangeListener in Factory) ---

    val layers by sketchViewModel.layers.collectAsStateWithLifecycle()
    
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView<FrameLayout>(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val container = FrameLayout(ctx)
                var isPanning = false
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
                    
                    this.onHybridStrokeCompleted = { stroke, fill ->
                        sketchViewModel.addHybridStroke(stroke, fill)
                    }

                    this.onGeometricProgressChanged = { inProgress ->
                        sketchViewModel.updateGeometricStrokeInProgress(inProgress)
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
                                    cv.setLayers(sketchViewModel.layers.value, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
                                    cv.invalidate()
                                }
                                v?.removeOnLayoutChangeListener(this)
                            }
                        }
                    })
                    
                    this.tag = RuntimeState() // Attach RuntimeState to CanvasView
                }
                canvasViewRef = canvasView
                
                // Initial state
                canvasView.setLayers(layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext, sketchViewModel.activeLayerIndex)
                canvasView.setCameraMatrix(cameraMatrix)

                container.addView(canvasView)
                
                val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        // Forward check to view matrix (optional, but view handles it now)
                         return true
                    }
                    override fun onScaleEnd(detector: ScaleGestureDetector) {
                         canvasView.refreshView()
                    }
                })

                val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onDown(e: MotionEvent): Boolean = true
                })

                // val stabilizer = StrokeStabilizer() // REMOVED
                // val strokeIdMap = mutableMapOf<Int, InProgressStrokeId>() // REMOVED
                
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
                // wetView.setOnTouchListener REMOVED - Moved to CanvasView if needed or handled differently.
                // Assuming legacy "wetView" was handling touch. We must now attach touch listener to "canvasView".
                // Wire up callbacks to ViewModel
                canvasView.onStrokeCompleted = { stroke -> sketchViewModel.addVectorStroke(stroke) }
                canvasView.onFillCompleted = { fill -> sketchViewModel.addFill(fill) }
                canvasView.onHybridStrokeCompleted = { s, f -> sketchViewModel.addHybridStroke(s, f) }

                canvasView.setOnTouchListener { v, event ->
                    // PASS PROP
                    canvasView.isSnapToGridEnabled = sketchViewModel.isSnapToGridEnabled
                    
                    // Wire up internal matrix changes to external state
                    canvasView.onCameraMatrixChanged = { matrix ->
                         sketchViewModel.saveCameraState(matrix)
                         cameraMatrix.set(matrix)
                         cameraMatrix.invert(inverseMatrix)
                         currentZoom = InkUtils.getMatrixScale(matrix)
                    }

                    // Delegate to View's own touch handling
                    false 
                }
                
                container // Ensure factory returns the container
            },
            update = { view ->
                val container = view as FrameLayout
                val canvasView = container.getChildAt(0) as SketcherCanvasView
                // wetView removed
                val state = canvasView.tag as RuntimeState // Get state from CanvasView
                
                
                // CRITICAL FIX: Ensure layers depend on ViewModel state updates
                canvasView.setLayers(layers, sketchViewModel.componentLibrary, sketchViewModel.editingContext, sketchViewModel.activeLayerIndex)

                // val currentConfig = brushTypes.find { it.type == sketchViewModel.currentTool } ?: brushTypes.first()
                
                state.toolType = sketchViewModel.currentTool
                // state.brushFamily = currentConfig.family // Removed
                state.color = sketchViewModel.currentColor
                
                // CONVERT UNITS: Project Units (e.g. mm) -> Pixels
                val sizePx = com.sketcher.sketchercompanionv1.utils.UnitUtils.projectUnitsToPixels(
                    value = sketchViewModel.currentSize,
                    unit = sketchViewModel.currentUnit,
                    basePxPerMm = sketchViewModel.scaleConfig.basePixelsPerMillimeter
                )
                
                state.size = sizePx
                state.opacity = sketchViewModel.currentOpacity
                
                // Sync Active Configs for Perfect Freehand (Front Buffer)
                canvasView.activeStrokeColor = strokeColor
                canvasView.activeFillColor = fillColor
                canvasView.isStrokeActive = isStrokeActive
                canvasView.isFillActive = isFillActive
                canvasView.activeSize = sizePx // Use pixels for rendering

                // UX IMPROVEMENT: Stabilization only makes sense for Freehand. 
                // Snap to Grid is ignored for Freehand (in CanvasView), so we allow stabilization there.
                val shouldDisableStabilization = (sketchViewModel.currentStrokeType != com.sketcher.sketchercompanionv1.dto.StrokeType.FREEHAND)
                
                val effectiveSettings = if (shouldDisableStabilization) {
                    sketchViewModel.currentFreehandSettings.copy(
                        smoothing = 0f,
                        streamline = 0f,
                        predictionLatency = 0f // Optional: reduce latency for geometric tools
                    )
                } else {
                    sketchViewModel.currentFreehandSettings
                }

                canvasView.activeFreehandSettings = effectiveSettings

                val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                // state.updateActiveBrush(currentZoom) // Removed
                
                canvasView.invalidate()
                // wetView.invalidate() // Removed
                
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
                // Disable Lazy Stroke (Global Stabilization) for Geometric Tools
                val isGeometric = sketchViewModel.currentStrokeType != com.sketcher.sketchercompanionv1.dto.StrokeType.FREEHAND
                canvasView.globalStabilizationLevel = if (isGeometric) 0f else sketchViewModel.globalStabilizationLevel
                
                // SNAP FUNCTION
                canvasView.isSnapToGridEnabled = sketchViewModel.isSnapToGridEnabled
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
                onUndo = { sketchViewModel.undo(); canvasViewRef?.setLayers(sketchViewModel.layers.value, sketchViewModel.componentLibrary, sketchViewModel.editingContext); canvasViewRef?.redrawAllCache() },
                canRedo = sketchViewModel.canRedo,
                onRedo = { sketchViewModel.redo(); canvasViewRef?.setLayers(sketchViewModel.layers.value, sketchViewModel.componentLibrary, sketchViewModel.editingContext); canvasViewRef?.redrawAllCache() },
                onLayersClick = { showLayerManager = !showLayerManager },
                onSave = projectActions.onSave,
                onLoad = projectActions.onLoad,
                onImportImage = projectActions.onImportImage,
                onExportSvg = projectActions.onExportSvg,
                onImportSvg = projectActions.onImportSvg,
                onImportDxf = projectActions.onImportDxf,
                onExportDxf = projectActions.onExportDxf,
                onSaveTemplate = projectActions.onTemplatesSave,
                onLoadTemplate = projectActions.onTemplatesLoad,
                onExportPng = projectActions.onExportPng,
                onExportPdf = projectActions.onExportPdf,
                onNewDrawing = projectActions.onNew,
                onSettingsClick = projectActions.onSettings,
                onPaperSizeClick = projectActions.onPaperSize,
                onGridClick = projectActions.onGridSettings,
                onZoomReset = { 
                    sketchViewModel.resetCamera()
                    // sketchViewModel will trigger update via state change in update block
                },
                onSetHomeCamera = {
                    sketchViewModel.saveHomeCamera()
                    showHomeSavedFeedback = true
                },
                onZoomExtend = {
                    sketchViewModel.fitContent()
                },
                onZoom100 = { sketchViewModel.setZoomOneHundred() },
                activeLayerName = if (layers.isNotEmpty() && sketchViewModel.activeLayerIndex in layers.indices) {
                    layers[sketchViewModel.activeLayerIndex].name
                } else "Layer",
                toolbarBackgroundColor = sketchViewModel.toolbarBackgroundColor,
                toolbarAlpha = sketchViewModel.toolbarAlpha,
                isToolbarBlurEnabled = sketchViewModel.isToolbarBlurEnabled,
                showTooltips = showTooltips,
                onMinimize = {
                    activity?.moveTaskToBack(true)
                },
                onExit = {
                    activity?.finishAndRemoveTask()
                }
            )

            // --- SUBTLE CONFIRMATION OVERLAY ---
            if (showHomeSavedFeedback) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .zIndex(2000f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Vista Home Guardada", color = Color.White)
                }
            }
            
            if (showHomeRestoredFeedback) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 80.dp)
                        .zIndex(2000f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text("Vista Restaurada", color = Color.White)
                }
            }
            
            // SCALE INDICATOR (Top Left)
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
                showLazyStrokePopup = showLazyStrokePopup,
                lazyStrokeValue = sketchViewModel.globalStabilizationLevel,
                onLazyStrokeValueChange = { sketchViewModel.setGlobalStabilization(it) },
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
                    canvasViewRef?.setLayers(sketchViewModel.layers.value, sketchViewModel.componentLibrary, sketchViewModel.editingContext)
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
                    canvasViewRef?.invalidate()
                },
                onEnterEditMode = {
                    sketchViewModel.enterEditMode()
                    canvasViewRef?.invalidate()
                },
                canEnterEditMode = sketchViewModel.canEnterEditMode,
                onExitEditMode = {
                    sketchViewModel.exitEditMode()
                    canvasViewRef?.invalidate()
                },
                isEditingContextActive = sketchViewModel.editingContext != null,
                onCopy = { sketchViewModel.copy() },
                onCut = { 
                    sketchViewModel.cut()
                    canvasViewRef?.redrawAllCache()
                    canvasViewRef?.invalidate()
                },
                canPaste = sketchViewModel.canPaste,
                onPaste = { 
                    sketchViewModel.paste() 
                    canvasViewRef?.redrawAllCache()
                    canvasViewRef?.invalidate()
                },
                selectionScope = sketchViewModel.selectionScope,
                onToggleSelectionScope = {
                    sketchViewModel.selectionScope = if (sketchViewModel.selectionScope == SketcherViewModel.SelectionScope.CURRENT_LAYER) 
                        SketcherViewModel.SelectionScope.ALL_LAYERS 
                    else 
                        SketcherViewModel.SelectionScope.CURRENT_LAYER
                },
                isGroupSelected = sketchViewModel.selectionManager.selectedElements.any { it is GroupElement },
                isSelectionEmpty = sketchViewModel.selectionManager.selectedElements.isEmpty(),
                selectionLayerInfo = sketchViewModel.getSelectionLayerInfo(),
                onMoveToLayer = { index ->
                    sketchViewModel.moveSelectionToLayer(index)
                    canvasViewRef?.redrawAllCache()
                    canvasViewRef?.invalidate()
                },
                allLayers = layers,
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
                currentStrokeType = sketchViewModel.currentStrokeType,
                onStrokeTypeChanged = { 
                    sketchViewModel.updateStrokeType(it)
                    canvasViewRef?.activeStrokeType = it
                },
                onInputSettingsClick = {
                    showLazyStrokePopup = !showLazyStrokePopup
                },
                isGeometricStrokeInProgress = sketchViewModel.isGeometricStrokeInProgress,
                onFinishGeometricStroke = { canvasViewRef?.finishGeometricStroke() },
                showTooltips = showTooltips
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
        

        


        // 2. DIALOGS & POPUPS (Standard Density)
        // These are now OUTSIDE the CompositionLocalProvider, so they use the system density.
        if (showLayerManager) {
            LayerManagerDialog(
                layers = layers,
                activeLayerIndex = sketchViewModel.activeLayerIndex,
                onToggleVisibility = { sketchViewModel.toggleLayerVisibility(it) },
                onOpacityChanged = { idx, op -> sketchViewModel.setLayerOpacity(idx, op) },
                onActiveLayerChanged = { sketchViewModel.setActiveLayer(it) },
                onAddLayer = { sketchViewModel.addNewLayer(true) }, // Default add to top
                onDeleteLayer = { sketchViewModel.removeActiveLayer() },
                onMoveUp = { sketchViewModel.moveLayerUp(sketchViewModel.activeLayerIndex) },
                onMoveDown = { sketchViewModel.moveLayerDown(sketchViewModel.activeLayerIndex) },
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
                unit = sketchViewModel.currentUnit, // Convert for display
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

        // --- TOOL SETTINGS POPUP ---

    if (showToolSettingsPopup) {
             com.sketcher.sketchercompanionv1.ui.ToolSettingsPopup(
                 toolType = sketchViewModel.currentTool,
                 unit = sketchViewModel.currentUnit, // Use current unit
                 freehandSettings = sketchViewModel.currentFreehandSettings,
                 onFreehandSettingsChanged = { sketchViewModel.updateFreehandSettings(it) },
                 selectionScope = sketchViewModel.selectionScope,
                 onToggleSelectionScope = {
                     sketchViewModel.selectionScope = if (sketchViewModel.selectionScope == SketcherViewModel.SelectionScope.CURRENT_LAYER)
                         SketcherViewModel.SelectionScope.ALL_LAYERS else SketcherViewModel.SelectionScope.CURRENT_LAYER
                 },
                 onDismiss = { showToolSettingsPopup = false }
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
    selectionLayerInfo: String = "",
    onMoveToLayer: (Int) -> Unit = {},
    allLayers: List<Layer> = emptyList(),
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
    onInputSettingsClick: () -> Unit = {},
    showLazyStrokePopup: Boolean = false,
    lazyStrokeValue: Float = 0f,
    onLazyStrokeValueChange: (Float) -> Unit = {},
    currentStrokeType: StrokeType = StrokeType.FREEHAND,
    onStrokeTypeChanged: (StrokeType) -> Unit = {},
    isGeometricStrokeInProgress: Boolean = false,
    onFinishGeometricStroke: () -> Unit = {},
    showTooltips: Boolean = true
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
                    TooltipWrapper(text = "Herramienta: ${getToolName(activeDrawingTool)}", enabled = showTooltips) {
                        Icon(icon, contentDescription = "Tool", tint = Color.Black)
                    }
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

            // STROKE TYPE SELECTOR
            if (selectedTool == ToolType.FREEHAND) {
                var showStrokeTypePopup by remember { mutableStateOf(false) }
                Box {
                    IconButton(onClick = { showStrokeTypePopup = true }) {
                        val icon = when (currentStrokeType) {
                            StrokeType.FREEHAND -> Icons.Default.Gesture
                            StrokeType.LINE -> Icons.Default.HorizontalRule
                            StrokeType.POLYLINE -> Icons.Default.Timeline
                            StrokeType.CIRCLE -> Icons.Default.RadioButtonUnchecked
                            StrokeType.ARC -> Icons.Default.Refresh
                        }
                        TooltipWrapper(text = "Tipo de Trazo", enabled = showTooltips) {
                            Icon(icon, contentDescription = "Stroke Type", tint = Color.Black)
                        }
                    }

                    DropdownMenu(
                        expanded = showStrokeTypePopup,
                        onDismissRequest = { showStrokeTypePopup = false }
                    ) {
                        StrokeType.entries.forEach { type ->
                            DropdownMenuItem(
                                text = { Text(when(type) {
                                    StrokeType.FREEHAND -> "Libre"
                                    StrokeType.LINE -> "Línea"
                                    StrokeType.POLYLINE -> "Polilínea"
                                    StrokeType.CIRCLE -> "Círculo"
                                    StrokeType.ARC -> "Arco"
                                }) },
                                leadingIcon = {
                                    Icon(when(type) {
                                        StrokeType.FREEHAND -> Icons.Default.Gesture
                                        StrokeType.LINE -> Icons.Default.HorizontalRule
                                        StrokeType.POLYLINE -> Icons.Default.Timeline
                                        StrokeType.CIRCLE -> Icons.Default.RadioButtonUnchecked
                                        StrokeType.ARC -> Icons.Default.Refresh
                                    }, null)
                                },
                                onClick = {
                                    onStrokeTypeChanged(type)
                                    showStrokeTypePopup = false
                                }
                            )
                        }
                    }
                }
            }

            // FINISH GEOMETRIC STROKE (Checkmark)
            if (isGeometricStrokeInProgress) {
                IconButton(onClick = onFinishGeometricStroke) {
                    TooltipWrapper(text = "Finalizar Figura", enabled = showTooltips) {
                        Icon(Icons.Default.Check, contentDescription = "Finish Geometric", tint = Color(0xFF4CAF50))
                    }
                }
                VerticalDivider(modifier = Modifier.height(24.dp))
            }

            // SETTINGS BUTTON (For All Active Drawing Tools)
            // SETTINGS BUTTON (For All Active Drawing Tools)
            if (activeDrawingTool == ToolType.FREEHAND) {
                TooltipWrapper(text = "Configurar Pincel", enabled = showTooltips) {
                    IconButton(onClick = onConfigureTool) {
                        Icon(androidx.compose.material.icons.Icons.Filled.Build, contentDescription = "Configurar", tint = androidx.compose.material3.MaterialTheme.colorScheme.primary)
                    }
                }
                VerticalDivider(modifier = Modifier.height(24.dp))
            }

            // GLOBAL INPUT SETTINGS (Tune Icon)
            Box {
                TooltipWrapper(text = "Ajustes de Entrada (Estabilizador)", enabled = showTooltips) {
                    IconButton(onClick = onInputSettingsClick) {
                         Icon(Icons.Default.Tune, contentDescription = "Ajustes de Entrada", tint = MaterialTheme.colorScheme.secondary)
                    }
                }
                
                if (showLazyStrokePopup) {
                    LazyStrokePopup(
                        value = lazyStrokeValue,
                        onValueChange = onLazyStrokeValueChange,
                        onDismiss = onInputSettingsClick
                    )
                }
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
                    TooltipWrapper(text = "Relleno Automático", enabled = showTooltips) {
                         IconButton(
                             onClick = onToggleFillMode
                         ) {
                             Icon(
                                Icons.Default.FormatPaint,
                                contentDescription = "Auto Fill",
                                tint = if (isFillModeEnabled) Color.Black else Color.LightGray
                             )
                         }
                    }
                     
                     // FILL COLOR PREVIEW (Only if enabled)
                     if (isFillModeEnabled) {
                         TooltipWrapper(text = "Color de Relleno", enabled = showTooltips) {
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
                }

                VerticalDivider(modifier = Modifier.height(24.dp))
            }

            // SIZE PREVIEW
            if (selectedTool != ToolType.SELECTION) {
                TooltipWrapper(text = "Tamaño y Opacidad", enabled = showTooltips) {
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
                }
                
                VerticalDivider(modifier = Modifier.height(24.dp))
            }

            // ERASER
            if (selectedTool != ToolType.SELECTION) {
                TooltipWrapper(text = "Borrador", enabled = showTooltips) {
                    IconButton(onClick = onEraserToggle) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Eraser",
                            tint = if (isEraserActive) Color.Red else Color.Gray
                        )
                    }
                }

                VerticalDivider(modifier = Modifier.height(24.dp))
            }
            
            // BACKGROUND COLOR PICKER
            if (selectedTool != ToolType.SELECTION && selectedTool != ToolType.ERASER) {
                TooltipWrapper(text = "Color de Fondo", enabled = showTooltips) {
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
                 TooltipWrapper(text = "Copiar", enabled = showTooltips) {
                     IconButton(onClick = onCopy, enabled = !isSelectionEmpty) {
                         Icon(Icons.Default.ContentCopy, contentDescription = "Copy", tint = if (!isSelectionEmpty) Color.Black else Color.Gray)
                     }
                 }
                 TooltipWrapper(text = "Cortar", enabled = showTooltips) {
                     IconButton(onClick = onCut, enabled = !isSelectionEmpty) {
                         Icon(Icons.Default.ContentCut, contentDescription = "Cut", tint = if (!isSelectionEmpty) Color.Black else Color.Gray)
                     }
                 }
                 TooltipWrapper(text = "Pegar", enabled = showTooltips) {
                     IconButton(onClick = onPaste, enabled = canPaste) {
                         Icon(
                             Icons.Default.ContentPaste, 
                             contentDescription = "Paste", 
                             tint = if (canPaste) Color.Blue else Color.Gray
                         )
                     }
                 }

                 VerticalDivider(modifier = Modifier.height(24.dp))

                 TooltipWrapper(text = "Agrupar", enabled = showTooltips) {
                     IconButton(onClick = onGroup, enabled = !isSelectionEmpty) {
                         Icon(Icons.Default.Group, contentDescription = "Group", tint = if (!isSelectionEmpty) Color.Black else Color.Gray)
                     }
                 }

                  TooltipWrapper(text = "Desagrupar", enabled = showTooltips) {
                      IconButton(onClick = onUngroup, enabled = isGroupSelected) {
                          Icon(Icons.Default.Schema, contentDescription = "Ungroup", tint = if (isGroupSelected) Color.Black else Color.Gray)
                      }
                  }

                  TooltipWrapper(text = "Crear Componente", enabled = showTooltips) {
                      IconButton(onClick = onMakeComponent, enabled = !isSelectionEmpty) {
                          Icon(Icons.Default.Extension, contentDescription = "Make Component", tint = if (!isSelectionEmpty) Color.Black else Color.Gray)
                      }
                  }

                  TooltipWrapper(text = "Editar Aislado", enabled = showTooltips) {
                      IconButton(onClick = onEnterEditMode, enabled = canEnterEditMode) {
                          Icon(Icons.Default.Edit, contentDescription = "Edit Isolated", tint = if (canEnterEditMode) Color.Blue else Color.Gray)
                      }
                  }
 
                  VerticalDivider(modifier = Modifier.height(24.dp))

                  // --- LAYER REASSIGNMENT ---
                  if (!isSelectionEmpty) {
                      var showLayerMenu by remember { mutableStateOf(false) }
                      Box {
                          TextButton(
                              onClick = { showLayerMenu = true },
                              contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                          ) {
                              Row(verticalAlignment = Alignment.CenterVertically) {
                                  Icon(
                                      Icons.Default.Layers, 
                                      contentDescription = null, 
                                      modifier = Modifier.size(18.dp),
                                      tint = Color.Blue
                                  )
                                  Spacer(modifier = Modifier.width(4.dp))
                                  Text(
                                      text = selectionLayerInfo,
                                      style = MaterialTheme.typography.labelMedium,
                                      color = Color.Blue,
                                      maxLines = 1
                                  )
                              }
                          }
 
                          DropdownMenu(
                              expanded = showLayerMenu,
                              onDismissRequest = { showLayerMenu = false }
                          ) {
                              allLayers.forEachIndexed { index, layer ->
                                  DropdownMenuItem(
                                      text = { Text(layer.name) },
                                      onClick = {
                                          onMoveToLayer(index)
                                          showLayerMenu = false
                                      },
                                      leadingIcon = {
                                          Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp),
                                               tint = Color.Transparent) // Placeholder or logic for check
                                      }
                                  )
                              }
                          }
                      }
                      VerticalDivider(modifier = Modifier.height(24.dp))
                  }

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
    onImportSvg: () -> Unit,
    onImportDxf: () -> Unit,
    onExportDxf: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    onLoadTemplate: (java.io.File) -> Unit,
    onExportSvg: () -> Unit,
    onExportPng: () -> Unit,
    onExportPdf: () -> Unit,
    onNewDrawing: () -> Unit,
    onSettingsClick: () -> Unit,
    onPaperSizeClick: () -> Unit,
    onGridClick: () -> Unit,
    onZoomReset: () -> Unit,
    onSetHomeCamera: () -> Unit = {},
    onZoomExtend: () -> Unit,
    onZoom100: () -> Unit = {},
    activeLayerName: String,
    toolbarBackgroundColor: Int,
    toolbarAlpha: Float,
    isToolbarBlurEnabled: Boolean,
    showTooltips: Boolean = true,
    onMinimize: () -> Unit = {},
    onExit: () -> Unit = {}
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
            // Project Menu
            FileMenu(
                onNewDrawing = onNewDrawing,
                onSave = onSave,
                onLoad = onLoad,
                onImportImage = onImportImage,
                onImportSvg = onImportSvg,
                onImportDxf = onImportDxf,
                onExportSvg = onExportSvg,
                onExportPng = onExportPng,
                onExportPdf = onExportPdf,
                onExportDxf = onExportDxf,
                onSettingsClick = onSettingsClick,
                onSaveTemplate = onSaveTemplate,
                onLoadTemplate = onLoadTemplate,
                onPaperSizeClick = onPaperSizeClick
            )

            // Grid
            TooltipWrapper(text = "Cuadrícula", enabled = showTooltips) {
                IconButton(onClick = onGridClick) {
                    Icon(Icons.Default.GridOn, contentDescription = "Grid")
                }
            }

              // Zoom Controls
              TooltipWrapper(text = "Vista Inicial (Home)", enabled = showTooltips) {
                  Box(
                      modifier = Modifier
                          .size(40.dp)
                          .clip(CircleShape)
                          .combinedClickable(
                              onClick = onZoomReset,
                              onLongClick = onSetHomeCamera
                          ),
                      contentAlignment = Alignment.Center
                  ) {
                      Icon(Icons.Default.Home, contentDescription = "Home View")
                  }
              }
              
              TooltipWrapper(text = "Ajustar Contenido", enabled = showTooltips) {
                  Box(
                      modifier = Modifier
                          .size(40.dp)
                          .clip(CircleShape)
                          .combinedClickable(
                              onClick = onZoomExtend,
                              onLongClick = onZoom100
                          ),
                      contentAlignment = Alignment.Center
                  ) {
                      Icon(Icons.Default.AspectRatio, contentDescription = "Zoom Extend")
                  }
              }
        }

        // 2. CENTER: Undo/Redo (Perfectly Centered)
        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TooltipWrapper(text = stringResource(R.string.action_undo), enabled = true) {
                IconButton(onClick = onUndo, enabled = canUndo) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_undo), tint = if (canUndo) Color.Black else Color.LightGray)
                }
            }
            // Small spacer betweeen undo/redo?
            Spacer(modifier = Modifier.width(8.dp))
            TooltipWrapper(text = stringResource(R.string.action_redo), enabled = true) {
                IconButton(onClick = onRedo, enabled = canRedo) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = stringResource(R.string.action_redo), tint = if (canRedo) Color.Black else Color.LightGray)
                }
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
                TooltipWrapper(text = "Capas", enabled = showTooltips) {
                    Icon(Icons.Default.List, contentDescription = "Layers")
                }
            }

            // Exit / Minimize Button
            TooltipWrapper(text = "Minimizar / Salir (Long Click)", enabled = showTooltips) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            onClick = onMinimize,
                            onLongClick = onExit
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close, 
                        contentDescription = "Exit",
                        tint = Color.Red.copy(alpha = 0.7f)
                    )
                }
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

@Composable
fun SizeSelectorPopup(
    currentSize: Float, 
    unit: DistanceUnit,
    onSizeChanged: (Float) -> Unit, 
    currentOpacity: Float,
    onOpacityChanged: (Float) -> Unit,
    presets: List<Float>,
    onPresetSelected: (Float) -> Unit,
    onPresetSave: (Int, Float) -> Unit,
    activeToolType: ToolType,
    onDismiss: () -> Unit,
    isEraseAllLayersEnabled: Boolean = false,
    onToggleEraseAllLayers: () -> Unit = {}
) {
    // Visibility Logic
    val showSize = activeToolType != ToolType.FILL && activeToolType != ToolType.SELECTION
    val showOpacity = activeToolType != ToolType.SELECTION

    // Adaptive Range based on Unit
    val minSize = 0.1f
    val maxSize = if (unit == DistanceUnit.MM) 50f else 100f // 50mm is HUGE (~2 inches), 100px is decent.
    
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
                // Display Value with Unit (Always formatted)
                val formattedSize = String.format("%.1f", currentSize)
                Text("${stringResource(R.string.label_size)}: $formattedSize ${unit.symbol}")
                
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
            
            // Erase All Layers toggle (only for eraser)
            if (activeToolType == ToolType.ERASER) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.eraser_all_layers))
                    Switch(
                        checked = isEraseAllLayersEnabled,
                        onCheckedChange = { onToggleEraseAllLayers() }
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
    isDebugWireframe: Boolean,
    onToggleDebugWireframe: () -> Unit,
    currentScaleConfig: ScaleConfig,
    onUpdateProjectConfig: (String, Float) -> Unit,
    toolbarBackgroundColor: Int,
    onToolbarBackgroundColorChanged: (Int) -> Unit,
    toolbarAlpha: Float,
    onToolbarAlphaChanged: (Float) -> Unit,
    isToolbarBlurEnabled: Boolean,
    onToggleToolbarBlur: () -> Unit,
    showTooltips: Boolean,
    onToggleTooltips: () -> Unit
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mostrar Ayudas (Tooltips)")
                Switch(checked = showTooltips, onCheckedChange = { onToggleTooltips() })
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


private fun adjustPressure(pressure: Float, sensitivity: Float): Float {
    if (pressure <= 0f) return 0f
    if (pressure >= 1f) return 1f
    return try {
        java.lang.Math.pow(pressure.toDouble(), (1.0 / sensitivity.coerceAtLeast(0.01f)).toDouble()).toFloat()
    } catch (e: Exception) {
        pressure
    }
}
@Composable
fun ExportPngDialog(
    viewModel: SketcherViewModel, // Need VM for preview generation
    onDismiss: () -> Unit,
    onExport: (ExportPngConfig) -> Unit
) {
    var transparent by remember { mutableStateOf(false) }
    var useHomeView by remember { mutableStateOf(true) }
    
    // Resolution State
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableFloatStateOf(1f) }
    
    // Preview State
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Init Logic
    LaunchedEffect(Unit) {
        val defaults = viewModel.getExportDefaults(useHomeView)
        widthText = defaults.first.toString()
        heightText = defaults.second.toString()
        aspectRatio = defaults.first.toFloat() / defaults.second.toFloat()
    }
    
    // Update defaults when mode changes
    LaunchedEffect(useHomeView) {
        val defaults = viewModel.getExportDefaults(useHomeView)
        widthText = defaults.first.toString()
        heightText = defaults.second.toString()
        aspectRatio = defaults.first.toFloat() / defaults.second.toFloat()
    }
    
    // Generate Preview Effect
    LaunchedEffect(transparent, useHomeView) {
        isLoadingPreview = true
        // Generate a small preview
        val defaults = viewModel.getExportDefaults(useHomeView)
        val previewWidth = 400
        val previewHeight = (previewWidth / (defaults.first.toFloat() / defaults.second.toFloat())).toInt()
        
        val config = ExportPngConfig(transparent, useHomeView, previewWidth, previewHeight)
        
        // Run on IO/Generic
        launch(kotlinx.coroutines.Dispatchers.Default) {
            val bmp = viewModel.renderExportBitmap(config)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                previewBitmap = bmp
                isLoadingPreview = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar como PNG") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- PREVIEW ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (transparent) Color.LightGray else Color.Black) // Checkerboard logic would be better but simple gray for now
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    if (isLoadingPreview) {
                        CircularProgressIndicator()
                    }
                }
                
                HorizontalDivider()

                // --- OPTIONS ---
                Text("Opciones de fondo:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                     Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { transparent = false }) {
                        RadioButton(selected = !transparent, onClick = { transparent = false })
                        Text("Sólido")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { transparent = true }) {
                        RadioButton(selected = transparent, onClick = { transparent = true })
                        Text("Transparente")
                    }
                }

                HorizontalDivider()

                Text("Área a exportar:", style = MaterialTheme.typography.labelMedium)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useHomeView = true }) {
                        RadioButton(selected = useHomeView, onClick = { useHomeView = true })
                        Text("Vista Home (Lo que ves)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useHomeView = false }) {
                        RadioButton(selected = !useHomeView, onClick = { useHomeView = false })
                        Text("Ajustar a contenido (Todo)")
                    }
                }
                
                HorizontalDivider()
                
                // --- RESOLUTION ---
                Text("Resolución:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { 
                            widthText = it.filter { c -> c.isDigit() }
                            val newW = widthText.toFloatOrNull()
                            if (newW != null && aspectRatio > 0) {
                                heightText = (newW / aspectRatio).toInt().toString()
                            }
                        },
                        label = { Text("Ancho (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { 
                            heightText = it.filter { c -> c.isDigit() }
                            val newH = heightText.toFloatOrNull()
                            if (newH != null && aspectRatio > 0) {
                                widthText = (newH * aspectRatio).toInt().toString()
                            }
                        },
                        label = { Text("Alto (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)

                    )
                }
                 val w = widthText.toIntOrNull() ?: 0
                 val h = heightText.toIntOrNull() ?: 0
                 if (w > 0 && h > 0) {
                     val sizeMb = w * h * 4 / 1024 / 1024.0
                     Text("Tamaño aprox: ${"%.2f".format(sizeMb)} MB", fontSize = 11.sp, color = Color.Gray)
                 }

            }
        },
        confirmButton = {
            Button(onClick = { 
                val w = widthText.toIntOrNull() ?: 1920
                val h = heightText.toIntOrNull() ?: 1080
                onExport(ExportPngConfig(transparent, useHomeView, w, h)) 
            }) {
                Text("Exportar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun ExportSvgDialog(
    viewModel: SketcherViewModel,
    onDismiss: () -> Unit,
    onExport: (ExportSvgConfig) -> Unit
) {
    var includeBackground by remember { mutableStateOf(viewModel.lastExportSvgConfig.includeBackground) }
    var useHomeView by remember { mutableStateOf(viewModel.lastExportSvgConfig.useHomeView) }
    
    // Preview Logic
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(includeBackground, useHomeView) {
        isLoadingPreview = true
        // Generate a small bitmap preview for the SVG (visualizing the content)
        val defaults = viewModel.getExportDefaults(useHomeView)
        val previewWidth = 400
        val previewHeight = (previewWidth / (defaults.first.toFloat() / defaults.second.toFloat())).toInt()
        
        val pngConfigForPreview = ExportPngConfig(!includeBackground, useHomeView, previewWidth, previewHeight)
        
        launch(kotlinx.coroutines.Dispatchers.Default) {
             val bmp = viewModel.renderExportBitmap(pngConfigForPreview)
             kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                 previewBitmap = bmp
                 isLoadingPreview = false
             }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar como SVG") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- PREVIEW ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingPreview) {
                        CircularProgressIndicator()
                    } else {
                        if (previewBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            Text("No hay contenido para exportar", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                
                HorizontalDivider()
                
                // --- OPTIONS ---
                Text("Opciones de exportación:", style = MaterialTheme.typography.labelMedium)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeBackground, onCheckedChange = { includeBackground = it })
                    Text("Incluir color de fondo")
                }
                
                Text("Área de exportación:", style = MaterialTheme.typography.labelMedium)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useHomeView = true }) {
                        RadioButton(selected = useHomeView, onClick = { useHomeView = true })
                        Text("Vista Home (Lo que ves)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useHomeView = false }) {
                        RadioButton(selected = !useHomeView, onClick = { useHomeView = false })
                        Text("Ajustar a contenido (Todo)")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val defaults = viewModel.getExportDefaults(useHomeView)
                onExport(ExportSvgConfig(includeBackground, useHomeView, defaults.first.toFloat(), defaults.second.toFloat())) 
            }) {
                Text("Exportar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipWrapper(
    text: String,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (enabled && text.isNotEmpty()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(text)
                }
            },
            state = rememberTooltipState()
        ) {
            content()
        }
    } else {
        content()
    }
}



package com.skecher.sketchercompanionv1

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
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
import com.skecher.sketchercompanionv1.ui.ColorPickerDialog
import kotlin.math.roundToInt

enum class ToolType { PEN, MARKER, HIGHLIGHTER, ERASER }

data class BrushTypeConfig(
    val type: ToolType,
    val icon: ImageVector,
    val family: BrushFamily?
)

private class RuntimeState {
    var toolType: ToolType = ToolType.PEN
    var brushFamily: BrushFamily? = StockBrushes.pressurePen()
    var color: Int = AndroidColor.BLACK
    var size: Float = 15f
    // Eager initialization to fallback if update() is delayed
    var activeBrush: Brush? = Brush.createWithColorLong(
        family = StockBrushes.pressurePen(),
        colorLong = AndroidColor.pack(AndroidColor.BLACK),
        size = 15f,
        epsilon = 0.1f
    )

    fun updateActiveBrush(currentZoom: Float) {
        if (toolType != ToolType.ERASER && brushFamily != null) {
            val visualSize = size * currentZoom
            val finalColor = if (toolType == ToolType.HIGHLIGHTER) (color and 0x00FFFFFF) or 0x40000000 else color
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
    
    // Detectamos cambio de configuración (rotación) automáticamente con Compose
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp
    val screenHeight = configuration.screenHeightDp

    // UI STATE
    var selectedTool by rememberSaveable { mutableStateOf(ToolType.PEN) }
    
    // Color Slots
    val defaultColors = listOf(AndroidColor.BLACK, AndroidColor.RED, AndroidColor.BLUE)
    val colorSlots = remember { mutableStateListOf(*defaultColors.toTypedArray()) }
    var selectedColorSlotIndex by rememberSaveable { mutableIntStateOf(0) }
    val selectedColor = colorSlots[selectedColorSlotIndex]
    
    var selectedSize by rememberSaveable { mutableStateOf(15f) }
    
    var showColorPicker by remember { mutableStateOf(false) }
    var showToolPopup by remember { mutableStateOf(false) }
    var showSizePopup by remember { mutableStateOf(false) }
    var showSettingsPopup by remember { mutableStateOf(false) }

    var canvasViewRef by remember { mutableStateOf<SketcherCanvasView?>(null) }
    
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

    val brushTypes = listOf(
        BrushTypeConfig(ToolType.PEN, Icons.Default.Create, StockBrushes.pressurePen()),
        BrushTypeConfig(ToolType.MARKER, Icons.Default.Edit, StockBrushes.marker()),
        BrushTypeConfig(ToolType.HIGHLIGHTER, Icons.Default.Edit, StockBrushes.highlighter())
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
            view.setCameraMatrix(cameraMatrix)
        }
        
        sketchViewModel.saveDimensions(currentW, currentH)
        view.invalidate()
    }

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
                }
                canvasViewRef = canvasView
                
                canvasView.restoreStrokes(sketchViewModel.strokes)
                canvasView.setCameraMatrix(cameraMatrix)

                val wetView = InProgressStrokesView(ctx).apply {
                    layoutParams = params
                    setBackgroundColor(AndroidColor.TRANSPARENT)
                    
                    val initialState = RuntimeState().apply {
                        toolType = selectedTool
                        color = selectedColor
                        size = selectedSize
                        
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
                        cameraMatrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                        canvasView.setCameraMatrix(cameraMatrix)
                        cameraMatrix.invert(inverseMatrix)
                        sketchViewModel.saveCameraState(cameraMatrix)
                        
                        // FIX: Update brush size dynamically on zoom
                        val zoom = InkUtils.getMatrixScale(cameraMatrix)
                        (wetView.tag as? RuntimeState)?.updateActiveBrush(zoom)
                        
                        return true
                    }
                })

                val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
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

                val strokeIdMap = mutableMapOf<Int, InProgressStrokeId>()

                wetView.setOnTouchListener { v, event ->
                    // PALM REJECTION (Stylus Only Check)
                    if (sketchViewModel.isPalmRejectionEnabled && event.getToolType(0) != MotionEvent.TOOL_TYPE_STYLUS) {
                         return@setOnTouchListener false
                    }

                    // BOTTOM DEAD ZONE (Navigation Protection)
                    // If touch STARTS in bottom 40dp, ignore it to allow system gesture.
                    val density = context.resources.displayMetrics.density
                    val deadZonePx = 40 * density
                    val screenH = context.resources.displayMetrics.heightPixels
                    
                    if (event.actionMasked == MotionEvent.ACTION_DOWN && event.y > (v.height - deadZonePx)) {
                        return@setOnTouchListener false
                    }

                    scaleDetector.onTouchEvent(event)
                    gestureDetector.onTouchEvent(event)
                    
                    val state = v.tag as RuntimeState
                    val action = event.actionMasked
                    val isEraserTool = state.toolType == ToolType.ERASER

                    if (event.pointerCount == 1) {
                        val pid = event.getPointerId(0)
                        val touchPts = floatArrayOf(event.x, event.y)
                        inverseMatrix.mapPoints(touchPts)
                        val worldX = touchPts[0]
                        val worldY = touchPts[1]

                        when (action) {
                            MotionEvent.ACTION_DOWN -> {
                                if (!isEraserTool && state.brushFamily != null) {
                                    // SAFETY NET: Ensure brush is ready
                                    if (state.activeBrush == null) {
                                         val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                                         state.updateActiveBrush(currentZoom)
                                    }
                                    
                                    state.activeBrush?.let { brush ->
                                        strokeIdMap[pid] = wetView.startStroke(event, pid, brush)
                                    }
                                } else {
                                    val deletedStroke = canvasView.eraseStrokeAt(worldX, worldY)
                                    deletedStroke?.let { sketchViewModel.removeStroke(it) }
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!isEraserTool) {
                                    strokeIdMap[pid]?.let { wetView.addToStroke(event, pid, it, null) }
                                } else {
                                    val deletedStroke = canvasView.eraseStrokeAt(worldX, worldY)
                                    deletedStroke?.let { sketchViewModel.removeStroke(it) }
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                if (!isEraserTool) {
                                    strokeIdMap[pid]?.let {
                                        wetView.finishStroke(event, pid, it)
                                        strokeIdMap.remove(pid)
                                    }
                                }
                                v.performClick()
                            }
                        }
                    } else if (strokeIdMap.isNotEmpty()) {
                        strokeIdMap.forEach { (_, sid) -> wetView.cancelStroke(sid, event) }
                        strokeIdMap.clear()
                    }
                    true
                }

                wetView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                        for (entry in strokes) {
                            val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                            val worldStroke = InkUtils.transformStrokeToWorld(
                                screenStroke = entry.value,
                                inverseMatrix = inverseMatrix,
                                currentZoom = currentZoom
                            )
                            worldStroke?.let { 
                                canvasView.addStroke(it)
                                sketchViewModel.addStroke(it)
                            }
                        }
                        wetView.removeFinishedStrokes(strokes.keys)
                    }
                })
                container
            },
            update = { view ->
                val container = view as FrameLayout
                val wetView = container.getChildAt(1) as InProgressStrokesView
                val state = wetView.tag as RuntimeState
                
                val currentConfig = brushTypes.find { it.type == selectedTool } ?: brushTypes.first()
                
                state.toolType = selectedTool
                state.brushFamily = currentConfig.family
                state.color = selectedColor
                state.size = selectedSize
                
                val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                state.updateActiveBrush(currentZoom)
                
                wetView.invalidate()
            }
        )

        // --- NEW BOTTOM MENU BAR ---
        BottomMenuBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .scale(sketchViewModel.interfaceScale),
            tools = brushTypes,
            selectedTool = selectedTool,
            onToolSelected = { selectedTool = it },
            colorSlots = colorSlots,
            selectedColorSlotIndex = selectedColorSlotIndex,
            onColorSlotSelected = { selectedColorSlotIndex = it },
            onColorChangeRequest = { showColorPicker = true },
            selectedSize = selectedSize,
            onSizeChangeRequest = { showSizePopup = !showSizePopup },
            isEraserActive = selectedTool == ToolType.ERASER,
            onEraserToggle = {
                selectedTool = if (selectedTool == ToolType.ERASER) ToolType.PEN else ToolType.ERASER
            },
            canUndo = sketchViewModel.canUndo,
            onUndo = { sketchViewModel.undo(); canvasViewRef?.restoreStrokes(sketchViewModel.strokes) },
            canRedo = sketchViewModel.canRedo,
            onRedo = { sketchViewModel.redo(); canvasViewRef?.restoreStrokes(sketchViewModel.strokes) },
            onSettingsClick = { showSettingsPopup = true },
            showToolPopup = showToolPopup,
            onShowToolPopupChange = { showToolPopup = it }
        )

        // DIALOGS & POPUPS
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
               onInterfaceScaleChanged = { sketchViewModel.updateInterfaceScale(it) }
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
    canUndo: Boolean,
    onUndo: () -> Unit,
    canRedo: Boolean,
    onRedo: () -> Unit,
    onSettingsClick: () -> Unit,
    showToolPopup: Boolean,
    onShowToolPopupChange: (Boolean) -> Unit
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
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // TOOL SELECTOR
            Box {
                val currentToolConfig = tools.find { it.type == selectedTool } ?: tools.first()
                val icon = if(selectedTool == ToolType.ERASER) Icons.Default.Edit else currentToolConfig.icon
                
                IconButton(onClick = { if(selectedTool != ToolType.ERASER) onShowToolPopupChange(!showToolPopup) else onToolSelected(ToolType.PEN)}) {
                   Icon(icon, contentDescription = "Tool", tint = if (selectedTool == ToolType.ERASER) Color.Gray else Color.Black)
                }
                
                DropdownMenu(
                    expanded = showToolPopup,
                    onDismissRequest = { onShowToolPopupChange(false) }
                ) {
                    tools.forEach { tool ->
                        DropdownMenuItem(
                            text = { Text(tool.type.name) },
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

            // UNDO / REDO
            IconButton(onClick = onUndo, enabled = canUndo) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Undo", tint = if (canUndo) Color.Black else Color.LightGray)
            }
            IconButton(onClick = onRedo, enabled = canRedo) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Redo", tint = if (canRedo) Color.Black else Color.LightGray)
            }

            // SETTINGS
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

@Composable
fun SizeSelectorPopup(currentSize: Float, onSizeChanged: (Float) -> Unit, onDismiss: () -> Unit) {
    Popup(alignment = Alignment.BottomCenter, onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(bottom = 100.dp) // Lift above bottom bar
                .width(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .border(1.dp, Color.LightGray, RoundedCornerShape(16.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Brush Size: ${currentSize.toInt()}")
            Slider(
                value = currentSize,
                onValueChange = onSizeChanged,
                valueRange = 1f..100f
            )
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
    onInterfaceScaleChanged: (Float) -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Settings", style = MaterialTheme.typography.titleMedium)
            
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
                Text("Stylus Only (Palm Rejection)")
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
            
            Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Close")
            }
        }
    }
}

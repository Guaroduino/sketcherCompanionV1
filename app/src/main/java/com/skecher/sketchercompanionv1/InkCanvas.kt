package com.skecher.sketchercompanionv1

// --- IMPORTS GENERALES ---
import android.annotation.SuppressLint
import android.graphics.Matrix
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// --- IMPORTS DE INK ---
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.MutableStrokeInputBatch 
import androidx.input.motionprediction.MotionEventPredictor
import android.graphics.Color as AndroidColor

enum class AppTool { PEN, MARKER, HIGHLIGHTER, ERASER }

class EditorState {
    var maxSize: Float = 5f
    var tool: AppTool = AppTool.PEN
    var color: Int = android.graphics.Color.BLACK
    var stylusOnly: Boolean = false
}

@Composable
fun InkCanvas() {
    val context = LocalContext.current

// --- ESTADOS DE LA UI ---
    var activeTool by remember { mutableStateOf(AppTool.PEN) }
    var activeColor by remember { mutableStateOf(ComposeColor.Black) }
    var currentSize by remember { mutableFloatStateOf(5f) }
    var isStylusOnlyMode by remember { mutableStateOf(false) }

    val editorState = remember { EditorState() }

// Debug
    var debugPressure by remember { mutableFloatStateOf(0f) }
    var debugToolType by remember { mutableStateOf("-") }

// Popups
    var showColorPalette by remember { mutableStateOf(false) }
    var showToolPopup by remember { mutableStateOf(false) }
    var showSizePopup by remember { mutableStateOf(false) }
    var showSettingsPopup by remember { mutableStateOf(false) }

    var dryInkViewRef by remember { mutableStateOf<DryInkView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val container = FrameLayout(ctx)
                container.setBackgroundColor(AndroidColor.WHITE)

                // 1. Capa Seca
                val dryView = DryInkView(ctx)
                dryView.layoutParams = FrameLayout.LayoutParams(-1, -1)
                container.addView(dryView)
                dryInkViewRef = dryView

                // 2. Capa Húmeda
                val wetView = InProgressStrokesView(ctx)
                wetView.layoutParams = FrameLayout.LayoutParams(-1, -1)
                wetView.setBackgroundColor(AndroidColor.TRANSPARENT)
                wetView.isFocusable = true
                wetView.isFocusableInTouchMode = true
                container.addView(wetView)

                // 3. Lógica de Tinta + Predictor
                val predictor = MotionEventPredictor.newInstance(wetView)
                val pointerIdToStrokeId = mutableMapOf<Int, InProgressStrokeId>()
                val identityMatrix = Matrix()

                // State for Navigation
                val currentMatrix = Matrix()
                val inverseMatrix = Matrix()
                var isNavigating = false

                // Helper to apply matrix updates
                fun updateTransform() {
                    currentMatrix.invert(inverseMatrix)
                    dryView.setMatrix(currentMatrix)
                }

                val scaleDetector = android.view.ScaleGestureDetector(ctx, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                        val scaleFactor = detector.scaleFactor
                        currentMatrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                        updateTransform()
                        return true
                    }
                })

                val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                        currentMatrix.postTranslate(-distanceX, -distanceY)
                        updateTransform()
                        return true
                    }
                })

                wetView.setOnTouchListener(object : View.OnTouchListener {
                    @SuppressLint("ClickableViewAccessibility")
                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        try {
                             // Handle Navigation Gestures
                            scaleDetector.onTouchEvent(event)
                            gestureDetector.onTouchEvent(event)

                            val pointerCount = event.pointerCount
                            val isScaling = scaleDetector.isInProgress

                            // Determine Mode:
                            if (pointerCount >= 2 || isScaling || isNavigating) {
                                isNavigating = true
                                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                                     // Reset nav state on last finger up
                                     if(pointerCount <= 1) isNavigating = false
                                }
                                
                                // Cancel any active strokes if we switched to nav
                                if (pointerIdToStrokeId.isNotEmpty()) {
                                    pointerIdToStrokeId.forEach { (pid, sid) ->
                                        wetView.cancelStroke(sid, event)
                                    }
                                    pointerIdToStrokeId.clear()
                                }
                                return true
                            }
                            
                            // --- DRAWING MODE ---
                            
                            predictor.record(event)
                            val action = event.actionMasked
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)

                            val cfgSize = editorState.maxSize
                            val cfgTool = editorState.tool
                            val cfgColor = editorState.color
                            val cfgStylusOnly = editorState.stylusOnly

                            debugPressure = event.pressure
                            val toolTypeInt = event.getToolType(pointerIndex)
                            debugToolType = if (toolTypeInt == MotionEvent.TOOL_TYPE_STYLUS) "Stylus" else "Dedo"

                            if (cfgStylusOnly && toolTypeInt != MotionEvent.TOOL_TYPE_STYLUS) return false

                            // Calculate effective brush size based on zoom
                            val values = FloatArray(9)
                            currentMatrix.getValues(values)
                            val currentScale = values[Matrix.MSCALE_X]
                            
                            val effectiveSize = cfgSize * currentScale

                            when (action) {
                                MotionEvent.ACTION_DOWN -> {
                                    v.parent.requestDisallowInterceptTouchEvent(true)

                                    val brush = when (cfgTool) {
                                        AppTool.ERASER -> Brush.createWithColorLong(
                                            family = StockBrushes.marker(),
                                            colorLong = AndroidColor.valueOf(AndroidColor.WHITE).pack(),
                                            size = effectiveSize * 3, epsilon = 0.5f
                                        )
                                        AppTool.PEN -> Brush.createWithColorLong(
                                            family = StockBrushes.pressurePen(),
                                            colorLong = AndroidColor.valueOf(cfgColor).pack(),
                                            size = effectiveSize,
                                            epsilon = 0.1f
                                        )
                                        AppTool.MARKER -> Brush.createWithColorLong(
                                            family = StockBrushes.marker(),
                                            colorLong = AndroidColor.valueOf(cfgColor).pack(),
                                            size = effectiveSize, epsilon = 0.1f
                                        )
                                        AppTool.HIGHLIGHTER -> Brush.createWithColorLong(
                                            family = StockBrushes.highlighter(),
                                            colorLong = AndroidColor.valueOf(cfgColor).let {
                                                AndroidColor.pack(it.red(), it.green(), it.blue(), 0.4f)
                                            },
                                            size = effectiveSize * 3, epsilon = 0.1f
                                        )
                                    }

                                    val strokeId = wetView.startStroke(event, pointerId, brush, identityMatrix)
                                    pointerIdToStrokeId[pointerId] = strokeId
                                    v.invalidate()
                                    return true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val predictedEvent = try { predictor.predict() } catch (e: Exception) { null }
                                    try {
                                        for (i in 0 until event.pointerCount) {
                                            val pId = event.getPointerId(i)
                                            val strokeId = pointerIdToStrokeId[pId] ?: continue
                                            wetView.addToStroke(event, pId, strokeId, predictedEvent)
                                        }
                                    } finally {
                                        // predictedEvent?.recycle() 
                                    }
                                    v.invalidate()
                                    return true
                                }
                                MotionEvent.ACTION_UP -> {
                                    val strokeId = pointerIdToStrokeId[pointerId]
                                    if (strokeId != null) {
                                        wetView.finishStroke(event, pointerId, strokeId)
                                        pointerIdToStrokeId.remove(pointerId)
                                    }
                                    v.performClick()
                                    v.invalidate()
                                    return true
                                }
                                MotionEvent.ACTION_CANCEL -> {
                                    val strokeId = pointerIdToStrokeId[pointerId]
                                    if (strokeId != null) {
                                        wetView.cancelStroke(strokeId, event)
                                        pointerIdToStrokeId.remove(pointerId)
                                    }
                                    return true
                                }
                            }
                        } catch (e: Exception) { e.printStackTrace() }
                        return false
                    }
                })

                wetView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                        try {
                            // Convertimos y transformamos de forma segura
                            val worldStrokes = strokes.values.mapNotNull { screenStroke ->
                                try {
                                    transformStrokeToWorld(screenStroke, inverseMatrix)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    null
                                }
                            }
                            
                            if (worldStrokes.isNotEmpty()) {
                                dryView.addStrokes(worldStrokes)
                            }
                            wetView.removeFinishedStrokes(strokes.keys)
                        } catch (e: Exception) {
                            android.util.Log.e("InkCanvas", "Error procesando trazos: ${e.message}")
                        }
                    }
                })
                
                wetView.postDelayed({ wetView.requestLayout(); wetView.invalidate() }, 100)
                container
            },
            update = {
                editorState.maxSize = currentSize
                editorState.tool = activeTool
                editorState.color = activeColor.toArgb()
                editorState.stylusOnly = isStylusOnlyMode
            }
        )

// --- DEBUGGER SUPERIOR ---
        Column(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 40.dp)
                .background(ComposeColor.Black.copy(0.8f), RoundedCornerShape(8.dp)).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("S-PEN INFO", color = ComposeColor.Cyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            HorizontalDivider(Modifier.padding(vertical = 4.dp), color = ComposeColor.Gray)
            Text(debugToolType, color = ComposeColor.White, fontSize = 12.sp)
            Text("Presión: ${String.format("%.3f", debugPressure)}", color = if(debugPressure>0) ComposeColor.Green else ComposeColor.Red, fontSize = 14.sp)
        }

// --- BARRA INFERIOR ---
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showColorPalette) {
                Row(Modifier.padding(bottom=16.dp).background(ComposeColor.White, CircleShape).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ComposeColor.Black, ComposeColor.Red, ComposeColor.Blue, ComposeColor(0xFF4CAF50), ComposeColor(0xFFFF9800)).forEach { c ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c).clickable { activeColor = c; showColorPalette = false })
                    }
                }
            }

            Row(
                modifier = Modifier.height(64.dp).fillMaxWidth(0.95f).background(ComposeColor(0xFFEEEEEE), CircleShape)
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
// Selector
                Box {
                    InternalToolButton(
                        icon = when(activeTool) {
                            AppTool.PEN -> Icons.Default.Edit
                            AppTool.MARKER -> Icons.Default.Create
                            AppTool.HIGHLIGHTER -> Icons.Default.Star
                            else -> Icons.Outlined.Create
                        },
                        isSelected = activeTool != AppTool.ERASER
                    ) { showToolPopup = true }

                    DropdownMenu(expanded = showToolPopup, onDismissRequest = { showToolPopup = false }) {
                        DropdownMenuItem(
                            text = { Text("Pluma (Presión)") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { activeTool = AppTool.PEN; showToolPopup = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Marcador (Fijo)") },
                            leadingIcon = { Icon(Icons.Default.Create, contentDescription = null) },
                            onClick = { activeTool = AppTool.MARKER; showToolPopup = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Resaltador") },
                            leadingIcon = { Icon(Icons.Default.Star, contentDescription = null) },
                            onClick = { activeTool = AppTool.HIGHLIGHTER; showToolPopup = false }
                        )
                    }
                }

// Color
                Box(Modifier.size(40.dp).clip(CircleShape).background(activeColor).clickable { showColorPalette = !showColorPalette })

// Tamaño (Slider)
                Box {
                    InternalSizeButton(currentSize, activeColor, showSizePopup) { showSizePopup = true }
                    DropdownMenu(expanded = showSizePopup, onDismissRequest = { showSizePopup = false }, modifier = Modifier.width(200.dp).background(ComposeColor.White).padding(16.dp)) {
                        Text("Grosor: ${currentSize.toInt()} px", fontSize=14.sp)
                        Slider(value = currentSize, onValueChange = { currentSize = it }, valueRange = 1f..60f)
                    }
                }

                InternalToolButton(Icons.Outlined.Delete, activeTool == AppTool.ERASER) { activeTool = AppTool.ERASER }
                Box(Modifier.width(1.dp).height(24.dp).background(ComposeColor.Gray))
                IconButton({ dryInkViewRef?.undo() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Undo") }
                IconButton({ dryInkViewRef?.redo() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Redo") }

                Box {
                    IconButton({ showSettingsPopup = true }) { Icon(Icons.Default.MoreVert, "Settings") }
                    DropdownMenu(expanded = showSettingsPopup, onDismissRequest = { showSettingsPopup = false }, modifier = Modifier.background(ComposeColor.White).padding(16.dp)) {
                        Text("Configuración", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top=8.dp)) {
                            Text("Solo Stylus", fontSize = 12.sp, modifier = Modifier.padding(end=8.dp))
                            Switch(checked = isStylusOnlyMode, onCheckedChange = { isStylusOnlyMode = it })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InternalToolButton(icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).background(if(isSelected) ComposeColor.Black else ComposeColor.Transparent).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if(isSelected) ComposeColor.White else ComposeColor.Black)
    }
}

@Composable
fun InternalSizeButton(currentSize: Float, currentColor: ComposeColor, isOpen: Boolean, onClick: () -> Unit) {
    val displaySize = (currentSize.coerceIn(2f, 30f)).dp
    Box(Modifier.size(48.dp).clip(CircleShape).background(if(isOpen) ComposeColor(0xFFE0E0E0) else ComposeColor.Transparent).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(displaySize).clip(CircleShape).background(currentColor))
    }
}

// --- HELPER CORREGIDO ---
fun transformStrokeToWorld(stroke: Stroke, inverseMatrix: Matrix): Stroke {
    val inputs = stroke.inputs
    
    // CORRECCIÓN PRINCIPAL: Usamos el constructor directo.
    // .create() no existe en esta versión de la librería.
    val builder = MutableStrokeInputBatch() 
    
    val size = inputs.size
    val pts = FloatArray(2)
    val toolType = if (size > 0) inputs.get(0).toolType else MotionEvent.TOOL_TYPE_UNKNOWN

    for (i in 0 until size) {
        val input = inputs.get(i)
        pts[0] = input.x
        pts[1] = input.y
        inverseMatrix.mapPoints(pts)

        builder.add(
            toolType,
            pts[0],
            pts[1],
            input.elapsedTimeMillis,
            input.pressure,
            input.orientationRadians,
            input.tiltRadians
        )
    }
    return Stroke(stroke.brush, builder)
}
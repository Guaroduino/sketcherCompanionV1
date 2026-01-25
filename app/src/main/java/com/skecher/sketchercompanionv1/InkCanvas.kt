package com.skecher.sketchercompanionv1

import android.annotation.SuppressLint
import android.graphics.Matrix
import android.view.MotionEvent
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
import androidx.compose.material.icons.filled.*
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

// --- IMPORTS ---
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.input.motionprediction.MotionEventPredictor
import android.graphics.Color as AndroidColor
import kotlin.math.sqrt

enum class AppTool { PEN, MARKER, HIGHLIGHTER, ERASER }

class EditorState {
    var maxSize: Float = 5f
    var tool: AppTool = AppTool.PEN
    var color: Int = android.graphics.Color.BLACK
    var stylusOnly: Boolean = false
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun InkCanvas() {
    val context = LocalContext.current

    // --- ESTADOS ---
    var activeTool by remember { mutableStateOf(AppTool.PEN) }
    var activeColor by remember { mutableStateOf(ComposeColor.Black) }
    var currentSize by remember { mutableFloatStateOf(5f) }
    var isStylusOnlyMode by remember { mutableStateOf(false) }

    val editorState = remember { EditorState() }
    var dryInkViewRef by remember { mutableStateOf<DryInkView?>(null) }

    // UI States
    var showColorPalette by remember { mutableStateOf(false) }
    var showToolPopup by remember { mutableStateOf(false) }
    var showSizePopup by remember { mutableStateOf(false) }
    var showSettingsPopup by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val container = FrameLayout(ctx)
                container.setBackgroundColor(AndroidColor.WHITE)

                // 1. CAPA SECA
                val dryView = DryInkView(ctx)
                dryView.layoutParams = FrameLayout.LayoutParams(-1, -1)
                container.addView(dryView)
                dryInkViewRef = dryView

                // 2. CAPA HÚMEDA
                val wetView = InProgressStrokesView(ctx)
                wetView.layoutParams = FrameLayout.LayoutParams(-1, -1)
                wetView.setBackgroundColor(AndroidColor.TRANSPARENT)
                container.addView(wetView)

                val predictor = MotionEventPredictor.newInstance(wetView)
                val pointerIdToStrokeId = mutableMapOf<Int, InProgressStrokeId>()

                val currentMatrix = Matrix()
                val identityMatrix = Matrix()

                // --- LÓGICA DE GESTOS (Igual a tu versión buena) ---
                fun updateCamera() {
                    dryView.setMatrix(currentMatrix)
                }

                val scaleDetector = android.view.ScaleGestureDetector(ctx, object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                        currentMatrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                        updateCamera()
                        return true
                    }
                })

                val gestureDetector = android.view.GestureDetector(ctx, object : android.view.GestureDetector.SimpleOnGestureListener() {
                    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                        if (e2.pointerCount < 2) return false
                        currentMatrix.postTranslate(-distanceX, -distanceY)
                        updateCamera()
                        return true
                    }
                    override fun onDown(e: MotionEvent): Boolean = true
                })

                wetView.setOnTouchListener { v, event ->
                    scaleDetector.onTouchEvent(event)
                    gestureDetector.onTouchEvent(event)

                    if (event.pointerCount >= 2 || scaleDetector.isInProgress) {
                        pointerIdToStrokeId.forEach { (_, sid) -> wetView.cancelStroke(sid, event) }
                        pointerIdToStrokeId.clear()
                        return@setOnTouchListener true
                    }

                    val action = event.actionMasked
                    val pointerId = event.getPointerId(event.actionIndex)
                    val type = event.getToolType(event.actionIndex)

                    if (editorState.stylusOnly && type != MotionEvent.TOOL_TYPE_STYLUS) return@setOnTouchListener true

                    predictor.record(event)

                    when (action) {
                        MotionEvent.ACTION_DOWN -> {
                            v.parent.requestDisallowInterceptTouchEvent(true)

                            // Corrección visual del zoom
                            val values = FloatArray(9); currentMatrix.getValues(values)
                            val scaleX = values[Matrix.MSCALE_X]
                            val visualSize = editorState.maxSize * (if (scaleX > 0) scaleX else 1f)

                            val brushColor = AndroidColor.valueOf(editorState.color)
                            val wetBrush = when (editorState.tool) {
                                AppTool.PEN -> Brush.createWithColorLong(StockBrushes.pressurePen(), brushColor.pack(), visualSize, 0.1f)
                                AppTool.HIGHLIGHTER -> {
                                    val alpha = AndroidColor.pack(brushColor.red(), brushColor.green(), brushColor.blue(), 0.4f)
                                    Brush.createWithColorLong(StockBrushes.highlighter(), alpha, visualSize * 3, 0.1f)
                                }
                                else -> Brush.createWithColorLong(StockBrushes.marker(), brushColor.pack(), visualSize, 0.1f)
                            }

                            val strokeId = wetView.startStroke(event, pointerId, wetBrush, identityMatrix)
                            pointerIdToStrokeId[pointerId] = strokeId
                        }
                        MotionEvent.ACTION_MOVE -> {
                            val predicted = try { predictor.predict() } catch (e: Exception) { null }
                            for (i in 0 until event.pointerCount) {
                                val pId = event.getPointerId(i)
                                pointerIdToStrokeId[pId]?.let { wetView.addToStroke(event, pId, it, predicted) }
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            pointerIdToStrokeId.remove(pointerId)?.let { wetView.finishStroke(event, pointerId, it) }
                            v.performClick()
                        }
                        MotionEvent.ACTION_CANCEL -> {
                            pointerIdToStrokeId.remove(pointerId)?.let { wetView.cancelStroke(it, event) }
                        }
                    }
                    v.invalidate()
                    true
                }

                wetView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                        val tempInverse = Matrix()
                        if (currentMatrix.invert(tempInverse)) {

                            // Pincel Real (Tamaño base)
                            val brushColor = AndroidColor.valueOf(editorState.color)
                            val dryBrush = when (editorState.tool) {
                                AppTool.PEN -> Brush.createWithColorLong(StockBrushes.pressurePen(), brushColor.pack(), editorState.maxSize, 0.1f)
                                AppTool.HIGHLIGHTER -> {
                                    val alpha = AndroidColor.pack(brushColor.red(), brushColor.green(), brushColor.blue(), 0.4f)
                                    Brush.createWithColorLong(StockBrushes.highlighter(), alpha, editorState.maxSize * 3, 0.1f)
                                }
                                AppTool.ERASER -> Brush.createWithColorLong(StockBrushes.marker(), AndroidColor.valueOf(AndroidColor.WHITE).pack(), editorState.maxSize * 3, 0.5f)
                                else -> Brush.createWithColorLong(StockBrushes.marker(), brushColor.pack(), editorState.maxSize, 0.1f)
                            }

                            // Transformación Pantalla -> Mundo
                            val worldStrokes = strokes.values.mapNotNull {
                                transformStrokeToWorld(it, tempInverse, dryBrush)
                            }

                            dryView.addStrokes(worldStrokes)

                            // !!! ESTO ES LO QUE ARREGLA EL FLICKER/DESAPARICIÓN !!!
                            // Tu versión buena usaba 'post', la mía lo quitó. Lo ponemos de vuelta.
                            wetView.post {
                                wetView.removeFinishedStrokes(strokes.keys)
                            }
                        } else {
                            wetView.removeFinishedStrokes(strokes.keys)
                        }
                    }
                })
                container
            },
            update = {
                editorState.maxSize = currentSize
                editorState.tool = activeTool
                editorState.color = activeColor.toArgb()
                editorState.stylusOnly = isStylusOnlyMode
            }
        )

        // --- TOOLBAR ---
        // (Tu UI original, sin cambios)
        Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showColorPalette) {
                Row(Modifier.padding(bottom=16.dp).background(ComposeColor.White, CircleShape).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(ComposeColor.Black, ComposeColor.Red, ComposeColor.Blue, ComposeColor(0xFF4CAF50), ComposeColor(0xFFFF9800)).forEach { c ->
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c).clickable { activeColor = c; showColorPalette = false })
                    }
                }
            }
            Row(modifier = Modifier.height(64.dp).fillMaxWidth(0.95f).background(ComposeColor(0xFFEEEEEE), CircleShape).horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box {
                    InternalToolButton(icon = when(activeTool) { AppTool.PEN -> Icons.Default.Edit; AppTool.MARKER -> Icons.Default.Create; AppTool.HIGHLIGHTER -> Icons.Default.Star; else -> Icons.Outlined.Create }, isSelected = activeTool != AppTool.ERASER) { showToolPopup = true }
                    DropdownMenu(expanded = showToolPopup, onDismissRequest = { showToolPopup = false }) {
                        DropdownMenuItem(text = { Text("Pluma") }, leadingIcon = { Icon(Icons.Default.Edit, null) }, onClick = { activeTool = AppTool.PEN; showToolPopup = false })
                        DropdownMenuItem(text = { Text("Marcador") }, leadingIcon = { Icon(Icons.Default.Create, null) }, onClick = { activeTool = AppTool.MARKER; showToolPopup = false })
                        DropdownMenuItem(text = { Text("Resaltador") }, leadingIcon = { Icon(Icons.Default.Star, null) }, onClick = { activeTool = AppTool.HIGHLIGHTER; showToolPopup = false })
                    }
                }
                Box(Modifier.size(40.dp).clip(CircleShape).background(activeColor).clickable { showColorPalette = !showColorPalette })
                Box {
                    InternalSizeButton(currentSize, activeColor, showSizePopup) { showSizePopup = true }
                    DropdownMenu(expanded = showSizePopup, onDismissRequest = { showSizePopup = false }, modifier = Modifier.width(200.dp).padding(16.dp)) {
                        Text("Grosor: ${currentSize.toInt()} px")
                        Slider(value = currentSize, onValueChange = { currentSize = it }, valueRange = 1f..60f)
                    }
                }
                InternalToolButton(Icons.Outlined.Delete, activeTool == AppTool.ERASER) { activeTool = AppTool.ERASER }
                Box(Modifier.width(1.dp).height(24.dp).background(ComposeColor.Gray))
                IconButton({ dryInkViewRef?.undo() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) }
                IconButton({ dryInkViewRef?.redo() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null) }
                Box {
                    IconButton({ showSettingsPopup = true }) { Icon(Icons.Default.MoreVert, null) }
                    DropdownMenu(expanded = showSettingsPopup, onDismissRequest = { showSettingsPopup = false }, modifier = Modifier.padding(16.dp)) {
                        Text("Configuración", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Solo Stylus", modifier = Modifier.padding(end=8.dp)); Switch(checked = isStylusOnlyMode, onCheckedChange = { isStylusOnlyMode = it })
                        }
                    }
                }
            }
        }
    }
}

// --- HELPERS ---

fun transformStrokeToWorld(stroke: Stroke, inverseMatrix: Matrix, targetBrush: Brush): Stroke? {
    val inputs = stroke.inputs
    if (inputs.size == 0) return null
    val builder = MutableStrokeInputBatch()
    val pts = FloatArray(2)

    for (i in 0 until inputs.size) {
        val input = inputs.get(i)
        pts[0] = input.x
        pts[1] = input.y
        inverseMatrix.mapPoints(pts)

        // Esta es la ÚNICA corrección de seguridad: Si viene un 3.90, lo volvemos 1.0.
        // Si viene bien (0.5), se queda en 0.5. Invisible para el usuario, evita el crash.
        val safePressure = if (input.pressure > 1.0f) 1.0f else input.pressure

        builder.add(input.toolType, pts[0], pts[1], input.elapsedTimeMillis, safePressure, input.orientationRadians, input.tiltRadians)
    }
    // Usamos try-catch por si acaso, pero con safePressure no debería fallar
    return try {
        Stroke(targetBrush, builder)
    } catch (e: Exception) {
        null
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
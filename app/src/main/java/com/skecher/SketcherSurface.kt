package com.skecher.sketchercompanionv1

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke

// --- TIPOS DE HERRAMIENTA ---
enum class ToolType {
    PEN, MARKER, HIGHLIGHTER, ERASER
}

// Configuración solo del "Tipo" de pincel (Familia)
data class BrushTypeConfig(
    val type: ToolType,
    val icon: ImageVector,
    val family: BrushFamily? // Null si es borrador
)

// Clase para pasar el estado "vivo" a la vista de Android
private class RuntimeState {
    var toolType: ToolType = ToolType.PEN
    var brushFamily: BrushFamily? = StockBrushes.pressurePen()
    var color: Int = Color.BLACK
    var size: Float = 15f
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun SketcherSurface() {
    // --- ESTADO DE LA UI ---
    var selectedTool by remember { mutableStateOf(ToolType.PEN) }
    var selectedColor by remember { mutableStateOf(Color.BLACK) }
    var selectedSize by remember { mutableStateOf(15f) }
    
    var canvasViewRef by remember { mutableStateOf<SketcherCanvasView?>(null) }

    // Definición de familias de pinceles
    val brushTypes = listOf(
        BrushTypeConfig(ToolType.PEN, Icons.Default.Create, StockBrushes.pressurePen()),
        BrushTypeConfig(ToolType.MARKER, Icons.Default.Edit, StockBrushes.marker()),
        BrushTypeConfig(ToolType.HIGHLIGHTER, Icons.Default.Edit, StockBrushes.highlighter()),
        BrushTypeConfig(ToolType.ERASER, Icons.Default.Delete, null)
    )

    // Paleta de colores para probar
    val colors = listOf(Color.BLACK, Color.RED, Color.BLUE, Color.GREEN, Color.MAGENTA, Color.CYAN, Color.YELLOW)

    Box(modifier = Modifier.fillMaxSize()) {
        
        // 1. EL LIENZO ANDROID
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val container = FrameLayout(ctx)
                val canvasView = SketcherCanvasView(ctx)
                canvasViewRef = canvasView

                val wetView = InProgressStrokesView(ctx).apply {
                    setBackgroundColor(Color.TRANSPARENT)
                    // Guardamos un objeto de estado en el tag para accederlo desde los listeners
                    tag = RuntimeState()
                }

                container.addView(canvasView)
                container.addView(wetView)

                // Matrices y Gestos
                val cameraMatrix = Matrix()
                val inverseMatrix = Matrix()

                val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScale(detector: ScaleGestureDetector): Boolean {
                        cameraMatrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                        canvasView.setCameraMatrix(cameraMatrix)
                        cameraMatrix.invert(inverseMatrix)
                        return true
                    }
                })

                val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                    override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                        if (e2.pointerCount >= 2) {
                            cameraMatrix.postTranslate(-dX, -dY)
                            canvasView.setCameraMatrix(cameraMatrix)
                            cameraMatrix.invert(inverseMatrix)
                            return true
                        }
                        return false
                    }
                })

                val strokeIdMap = mutableMapOf<Int, InProgressStrokeId>()

                wetView.setOnTouchListener { v, event ->
                    scaleDetector.onTouchEvent(event)
                    gestureDetector.onTouchEvent(event)
                    
                    // LEEMOS EL ESTADO ACTUAL (¡Aquí estaba el error antes!)
                    val state = v.tag as RuntimeState
                    
                    val action = event.actionMasked
                    val isStylus = event.getToolType(0) == MotionEvent.TOOL_TYPE_STYLUS
                    val isEraserTool = state.toolType == ToolType.ERASER
                    val isErasing = isEraserTool || (!isStylus && state.toolType != ToolType.ERASER)

                    if (event.pointerCount == 1) {
                        val pid = event.getPointerId(0)
                        val touchPts = floatArrayOf(event.x, event.y)
                        inverseMatrix.mapPoints(touchPts)
                        val worldX = touchPts[0]
                        val worldY = touchPts[1]

                        when (action) {
                            MotionEvent.ACTION_DOWN -> {
                                if (!isErasing && state.brushFamily != null) {
                                    // --- DIBUJAR ---
                                    val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                                    val visualSize = state.size * currentZoom // Usamos el tamaño del slider

                                    // IMPORTANTE: Ajuste de Alpha para el resaltador
                                    val finalColor = if (state.toolType == ToolType.HIGHLIGHTER) {
                                        // Forzamos alpha bajo para que acumule color
                                        (state.color and 0x00FFFFFF) or 0x40000000 
                                    } else {
                                        state.color
                                    }

                                    val brush = Brush.createWithColorLong(
                                        family = state.brushFamily!!,
                                        colorLong = Color.pack(finalColor),
                                        size = visualSize,
                                        epsilon = 0.1f
                                    )
                                    strokeIdMap[pid] = wetView.startStroke(event, pid, brush)
                                } else {
                                    // --- BORRAR ---
                                    canvasView.eraseStrokeAt(worldX, worldY)
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                if (!isErasing) {
                                    strokeIdMap[pid]?.let { wetView.addToStroke(event, pid, it, null) }
                                } else {
                                    canvasView.eraseStrokeAt(worldX, worldY)
                                }
                            }
                            MotionEvent.ACTION_UP -> {
                                if (!isErasing) {
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
                            worldStroke?.let { canvasView.addStroke(it) }
                        }
                        wetView.post { wetView.removeFinishedStrokes(strokes.keys) }
                    }
                })

                container
            },
            // BLOQUE UPDATE: Aquí actualizamos el objeto de estado cuando cambia Compose
            update = { view ->
                // Buscamos la wetView dentro del FrameLayout container
                val container = view as FrameLayout
                val wetView = container.getChildAt(1) // Índice 1 es wetView
                
                val state = wetView.tag as RuntimeState
                
                // Actualizamos los valores "vivos"
                val currentConfig = brushTypes.find { it.type == selectedTool } ?: brushTypes.first()
                state.toolType = selectedTool
                state.brushFamily = currentConfig.family
                state.color = selectedColor
                state.size = selectedSize
            }
        )

        // 2. PANEL DE CONTROL (UI)
        ToolbarOverlay(
            modifier = Modifier.align(Alignment.CenterStart),
            tools = brushTypes,
            selectedTool = selectedTool,
            onToolSelected = { selectedTool = it },
            colors = colors,
            selectedColor = selectedColor,
            onColorSelected = { selectedColor = it },
            selectedSize = selectedSize,
            onSizeChanged = { selectedSize = it },
            onClearCanvas = { canvasViewRef?.clearCanvas() }
        )
    }
}

// --- COMPONENTES UI MEJORADOS ---
@Composable
fun ToolbarOverlay(
    modifier: Modifier = Modifier,
    tools: List<BrushTypeConfig>,
    selectedTool: ToolType,
    onToolSelected: (ToolType) -> Unit,
    colors: List<Int>,
    selectedColor: Int,
    onColorSelected: (Int) -> Unit,
    selectedSize: Float,
    onSizeChanged: (Float) -> Unit,
    onClearCanvas: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(16.dp)
            .width(80.dp) // Un poco más ancho para los controles
            .clip(RoundedCornerShape(16.dp))
            .background(androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.9f))
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Herramientas
        tools.forEach { tool ->
            ToolButton(
                icon = tool.icon,
                isSelected = selectedTool == tool.type,
                onClick = { onToolSelected(tool.type) },
                tint = if (tool.type == ToolType.ERASER) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color(selectedColor)
            )
        }

        Divider()

        // 2. Colores (Solo si no es borrador)
        if (selectedTool != ToolType.ERASER) {
            colors.take(4).forEach { color -> // Mostramos 4 colores de ejemplo
                ColorButton(
                    color = color,
                    isSelected = selectedColor == color,
                    onClick = { onColorSelected(color) }
                )
            }
        }

        Divider()

        // 3. Tamaño (Slider vertical o botones +/- simplificados para el toolbar)
        if (selectedTool != ToolType.ERASER) {
            Text(text = "${selectedSize.toInt()}", fontSize = 12.sp)
            // Slider simple (el Slider vertical nativo es experimental, usamos uno horizontal pequeño o botones)
            // Para simplicidad en este layout estrecho, ponemos 2 botones de tamaño
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
               SmallButton(text = "-", onClick = { if(selectedSize > 5) onSizeChanged(selectedSize - 5) })
               SmallButton(text = "+", onClick = { if(selectedSize < 100) onSizeChanged(selectedSize + 5) })
            }
        }

        Divider()
        
        // 4. Limpiar
        IconButton(onClick = onClearCanvas) {
            Icon(Icons.Default.Refresh, contentDescription = "Limpiar", tint = androidx.compose.ui.graphics.Color.Red)
        }
    }
}

@Composable
fun Divider() {
    Box(modifier = Modifier.height(1.dp).fillMaxWidth().background(androidx.compose.ui.graphics.Color.Gray))
}

@Composable
fun ToolButton(icon: ImageVector, isSelected: Boolean, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
    }
}

@Composable
fun ColorButton(color: Int, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color(color))
            .border(2.dp, if (isSelected) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
            .clickable(onClick = onClick)
    )
}

@Composable
fun SmallButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(androidx.compose.ui.graphics.Color.Gray)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = androidx.compose.ui.graphics.Color.White, fontSize = 14.sp)
    }
}
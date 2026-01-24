package com.skecher.sketchercompanionv1

// --- IMPORTS GENERALES ---
import android.annotation.SuppressLint
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
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
import androidx.compose.material3.* // Material 3
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
import android.graphics.Color as AndroidColor
import androidx.input.motionprediction.MotionEventPredictor

// --- ENUMS ---
enum class AppTool { PEN, MARKER, HIGHLIGHTER, ERASER }

// --- CLASE PUENTE PARA SINCRONIZACIÓN ---
class EditorState {
    var maxSize: Float = 10f
    var tool: AppTool = AppTool.PEN
    var color: Int = android.graphics.Color.BLACK
    var stylusOnly: Boolean = false
}

@Composable
fun InkCanvas() {
    val context = LocalContext.current

    // --- ESTADOS DE LA UI (COMPOSE) ---
    var activeTool by remember { mutableStateOf(AppTool.PEN) }
    var activeColor by remember { mutableStateOf(ComposeColor.Black) }
    var currentSize by remember { mutableFloatStateOf(15f) }
    var isStylusOnlyMode by remember { mutableStateOf(false) }

    // Objeto puente
    val editorState = remember { EditorState() }

    // --- VARIABLES PARA EL DEBUGGER EN PANTALLA ---
    var debugPressure by remember { mutableFloatStateOf(0f) }
    var debugToolType by remember { mutableStateOf("-") }
    var debugCalculatedWidth by remember { mutableFloatStateOf(0f) }

    // Popups
    var showColorPalette by remember { mutableStateOf(false) }
    var showToolPopup by remember { mutableStateOf(false) }
    var showSizePopup by remember { mutableStateOf(false) }
    var showSettingsPopup by remember { mutableStateOf(false) }

    // Referencias a vistas
    var dryInkViewRef by remember { mutableStateOf<DryInkView?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {

        // --------------------------------------------------
        // CAPA NATIVA (INK)
        // --------------------------------------------------
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val container = FrameLayout(ctx)
                container.setBackgroundColor(Color.WHITE)

                // 1. Capa Seca (Historial)
                val dryView = DryInkView(ctx)
                dryView.layoutParams = FrameLayout.LayoutParams(-1, -1)
                container.addView(dryView)
                dryInkViewRef = dryView

                // 2. Capa Húmeda (Dibujo en progreso)
                val wetView = InProgressStrokesView(ctx)
                wetView.layoutParams = FrameLayout.LayoutParams(-1, -1)
                wetView.setBackgroundColor(Color.TRANSPARENT)
                wetView.isFocusable = true
                wetView.isFocusableInTouchMode = true
                container.addView(wetView)

                // 3. Lógica de Tinta
                val predictor = MotionEventPredictor.newInstance(wetView)
                val pointerIdToStrokeId = mutableMapOf<Int, InProgressStrokeId>()

                wetView.setOnTouchListener(object : View.OnTouchListener {
                    @SuppressLint("ClickableViewAccessibility")
                    override fun onTouch(v: View, event: MotionEvent): Boolean {
                        try {
                            val action = event.actionMasked
                            val pointerIndex = event.actionIndex
                            val pointerId = event.getPointerId(pointerIndex)

                            // --- LEER CONFIGURACIÓN ACTUALIZADA ---
                            val cfgSize = editorState.maxSize
                            val cfgTool = editorState.tool
                            val cfgColor = editorState.color
                            val cfgStylusOnly = editorState.stylusOnly

                            // --- DATOS DE DEBUG ---
                            debugPressure = event.pressure
                            val toolTypeInt = event.getToolType(pointerIndex)
                            debugToolType = if (toolTypeInt == MotionEvent.TOOL_TYPE_STYLUS) "Stylus" else "Dedo/Otro"

                            // Cálculo visual para el debugger
                            debugCalculatedWidth = if (cfgTool == AppTool.PEN) cfgSize * event.pressure else cfgSize

                            // --- PALM REJECTION ---
                            if (cfgStylusOnly && toolTypeInt != MotionEvent.TOOL_TYPE_STYLUS) {
                                return false
                            }

                            // Alimentar al predictor con cada evento
                            predictor.record(event)

                            when (action) {
                                MotionEvent.ACTION_DOWN -> {
                                    v.requestUnbufferedDispatch(event)
                                    v.parent.requestDisallowInterceptTouchEvent(true)

                                    // OPTIMIZACIÓN: Crear el pincel SOLO cuando empieza el trazo
                                    val brush = when (cfgTool) {
                                        AppTool.ERASER -> Brush.createWithColorLong(
                                            family = StockBrushes.marker(),
                                            colorLong = AndroidColor.valueOf(AndroidColor.WHITE).pack(),
                                            size = cfgSize * 3, epsilon = 0.1f
                                        )
                                        AppTool.PEN -> Brush.createWithColorLong(
                                            family = StockBrushes.pressurePen(), // SENSIBLE A PRESIÓN
                                            colorLong = AndroidColor.valueOf(cfgColor).pack(),
                                            size = cfgSize,
                                            epsilon = 0.001f // Alta fidelidad
                                        )
                                        AppTool.MARKER -> Brush.createWithColorLong(
                                            family = StockBrushes.marker(), // GROSOR FIJO
                                            colorLong = AndroidColor.valueOf(cfgColor).pack(),
                                            size = cfgSize,
                                            epsilon = 0.1f
                                        )
                                        AppTool.HIGHLIGHTER -> Brush.createWithColorLong(
                                            family = StockBrushes.highlighter(), // TRANSPARENTE
                                            colorLong = AndroidColor.valueOf(cfgColor).let {
                                                AndroidColor.pack(it.red(), it.green(), it.blue(), 0.4f)
                                            },
                                            size = cfgSize * 2, epsilon = 0.1f
                                        )
                                    }

                                    // Iniciar el trazo con el pincel creado
                                    val strokeId = wetView.startStroke(event, pointerId, brush)
                                    pointerIdToStrokeId[pointerId] = strokeId
                                    v.invalidate()
                                    return true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val predictedEvent = predictor.predict()
                                    try {
                                        for (i in 0 until event.pointerCount) {
                                            val pId = event.getPointerId(i)
                                            // Aquí ya no creamos pinceles, solo agregamos puntos al trazo existente
                                            val strokeId = pointerIdToStrokeId[pId] ?: continue
                                            wetView.addToStroke(event, pId, strokeId, predictedEvent)
                                        }
                                    } finally {
                                        predictedEvent?.recycle()
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

                // Listener para guardar trazos en la capa seca
                wetView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                        dryView.addStrokes(strokes.values)
                        wetView.removeFinishedStrokes(strokes.keys)
                    }
                })

                wetView.postDelayed({ wetView.requestLayout(); wetView.invalidate() }, 100)
                container
            },
            // BLOQUE UPDATE: Sincroniza Compose -> AndroidView
            update = {
                editorState.maxSize = currentSize
                editorState.tool = activeTool
                editorState.color = activeColor.toArgb()
                editorState.stylusOnly = isStylusOnlyMode
            }
        )

        // --------------------------------------------------
        // PANEL DE DEBUG (SUPERIOR)
        // --------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 40.dp)
                .background(ComposeColor.Black.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("DEBUGGER", color = ComposeColor.Cyan, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = ComposeColor.Gray)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("INPUT", color = ComposeColor.Gray, fontSize = 10.sp)
                    Text(debugToolType, color = ComposeColor.White, fontSize = 12.sp)
                    Text("P: ${String.format("%.2f", debugPressure)}", color = if(debugPressure > 0) ComposeColor.Green else ComposeColor.Red, fontSize = 12.sp)
                }
                Column {
                    Text("GROSOR", color = ComposeColor.Gray, fontSize = 10.sp)
                    Text("Max: ${currentSize.toInt()}", color = ComposeColor.Yellow, fontSize = 12.sp)
                    Text("Calc: ${debugCalculatedWidth.toInt()}", color = ComposeColor.Magenta, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }

        // --------------------------------------------------
        // BARRA DE HERRAMIENTAS (INFERIOR)
        // --------------------------------------------------
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // Paleta de Colores
            if (showColorPalette) {
                Row(
                    modifier = Modifier.padding(bottom = 16.dp).background(ComposeColor.White, CircleShape).padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val colors = listOf(ComposeColor.Black, ComposeColor.Red, ComposeColor.Blue, ComposeColor(0xFF4CAF50), ComposeColor(0xFFFF9800), ComposeColor(0xFF9C27B0))
                    colors.forEach { color ->
                        Box(modifier = Modifier.size(36.dp).clip(CircleShape).background(color)
                            .clickable { activeColor = color; showColorPalette = false; if(activeTool == AppTool.ERASER) activeTool = AppTool.PEN })
                    }
                }
            }

            // Barra Principal
            Row(
                modifier = Modifier
                    .height(64.dp)
                    .fillMaxWidth(0.95f)
                    .background(ComposeColor(0xFFEEEEEE), CircleShape)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. HERRAMIENTAS
                Box {
                    InternalToolButton(
                        icon = when(activeTool) {
                            AppTool.PEN -> Icons.Default.Edit
                            AppTool.MARKER -> Icons.Default.Create
                            AppTool.HIGHLIGHTER -> Icons.Default.Star
                            else -> Icons.Outlined.Create
                        },
                        isSelected = activeTool != AppTool.ERASER,
                        onClick = { if (activeTool != AppTool.ERASER) showToolPopup = true else activeTool = AppTool.PEN }
                    )
                    DropdownMenu(
                        expanded = showToolPopup,
                        onDismissRequest = { showToolPopup = false },
                        modifier = Modifier.background(ComposeColor.White, RoundedCornerShape(12.dp))
                    ) {
                        DropdownMenuItem(
                            text = { Text("Pluma (Presión)") },
                            leadingIcon = { Icon(Icons.Default.Edit, null) },
                            onClick = { activeTool = AppTool.PEN; showToolPopup = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Marcador (Fijo)") },
                            leadingIcon = { Icon(Icons.Default.Create, null) },
                            onClick = { activeTool = AppTool.MARKER; showToolPopup = false }
                        )
                        DropdownMenuItem(
                            text = { Text("Resaltador") },
                            leadingIcon = { Icon(Icons.Default.Star, null) },
                            onClick = { activeTool = AppTool.HIGHLIGHTER; showToolPopup = false }
                        )
                    }
                }

                // 2. COLOR
                Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(activeColor)
                    .clickable { showColorPalette = !showColorPalette })

                // 3. TAMAÑO
                Box {
                    InternalSizeButton(currentSize, activeColor, showSizePopup) { showSizePopup = true }
                    DropdownMenu(
                        expanded = showSizePopup,
                        onDismissRequest = { showSizePopup = false },
                        modifier = Modifier.width(200.dp).background(ComposeColor.White, RoundedCornerShape(12.dp)).padding(16.dp)
                    ) {
                        Text("Grosor Máximo", fontSize = 12.sp, color = ComposeColor.Gray)
                        Text("${currentSize.toInt()} px", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = currentSize,
                            onValueChange = { currentSize = it },
                            valueRange = 2f..100f,
                            steps = 97
                        )
                    }
                }

                // 4. BORRADOR
                InternalToolButton(Icons.Outlined.Delete, activeTool == AppTool.ERASER) {
                    activeTool = AppTool.ERASER
                    Toast.makeText(context, "Borrador", Toast.LENGTH_SHORT).show()
                }

                Box(Modifier.width(1.dp).height(24.dp).background(ComposeColor.Gray))

                // 5. UNDO/REDO
                IconButton({ dryInkViewRef?.undo() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Undo") }
                IconButton({ dryInkViewRef?.redo() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, "Redo") }

                // 6. CONFIGURACIÓN
                Box {
                    IconButton({ showSettingsPopup = true }) { Icon(Icons.Default.MoreVert, "Settings") }
                    DropdownMenu(
                        expanded = showSettingsPopup,
                        onDismissRequest = { showSettingsPopup = false },
                        modifier = Modifier.background(ComposeColor.White, RoundedCornerShape(12.dp)).padding(16.dp)
                    ) {
                        Text("Configuración", fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Solo Stylus", fontSize = 12.sp, modifier = Modifier.weight(1f))
                            Switch(checked = isStylusOnlyMode, onCheckedChange = { isStylusOnlyMode = it })
                        }
                    }
                }
            }
        }
    }
}

// --- COMPONENTES UI AUXILIARES ---

@Composable
fun InternalToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) ComposeColor.Black else ComposeColor.Transparent
    val iconColor = if (isSelected) ComposeColor.White else ComposeColor.Black

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconColor)
    }
}

@Composable
fun InternalSizeButton(
    currentSize: Float,
    currentColor: ComposeColor,
    isOpen: Boolean,
    onClick: () -> Unit
) {
    val displaySize = (currentSize.coerceIn(2f, 35f)).dp
    val backgroundColor = if (isOpen) ComposeColor(0xFFE0E0E0) else ComposeColor.Transparent

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Círculo de Preview
        Box(
            modifier = Modifier
                .size(displaySize)
                .clip(CircleShape)
                .background(currentColor)
        )
    }
}
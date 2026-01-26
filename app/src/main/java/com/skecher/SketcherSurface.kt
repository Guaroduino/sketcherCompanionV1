package com.skecher.sketchercompanionv1

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Matrix
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke

// Enumeración simple para las herramientas (puede expandirse luego)
enum class SketchTool {
    PEN, ERASER
}

@SuppressLint("ClickableViewAccessibility")
@Composable
fun SketcherSurface() {
    // Estado para controlar la herramienta actual (por defecto PEN)
    // Nota: 'remember' aquí mantiene el estado si hay recomposiciones, 
    // pero para rotaciones de pantalla necesitarías un ViewModel.
    var currentTool by remember { mutableStateOf(SketchTool.PEN) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val container = FrameLayout(ctx)
            
            // 1. Instanciamos nuestro Motor Vectorial (Capa Seca)
            val canvasView = SketcherCanvasView(ctx)
            
            // 2. Instanciamos la Capa Húmeda de la librería
            val wetView = InProgressStrokesView(ctx).apply {
                setBackgroundColor(Color.TRANSPARENT)
            }

            container.addView(canvasView)
            container.addView(wetView)

            // --- GESTIÓN DE MATRICES (CÁMARA) ---
            val cameraMatrix = Matrix()
            val inverseMatrix = Matrix()

            // --- GESTOS (ZOOM Y PAN) ---
            // ScaleGestureDetector para el Zoom (Pinch)
            val scaleDetector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    cameraMatrix.postScale(detector.scaleFactor, detector.scaleFactor, detector.focusX, detector.focusY)
                    
                    // Actualizamos la vista vectorial (Zoom Infinito)
                    canvasView.setCameraMatrix(cameraMatrix)
                    
                    // Calculamos la inversa para poder mapear toques de pantalla -> mundo
                    cameraMatrix.invert(inverseMatrix)
                    return true
                }
            })

            // GestureDetector para el Pan (Desplazamiento)
            val gestureDetector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dX: Float, dY: Float): Boolean {
                    // Solo permitimos desplazar si se usan 2 o más dedos (para no interferir con el dibujo)
                    if (e2.pointerCount >= 2) {
                        cameraMatrix.postTranslate(-dX, -dY)
                        canvasView.setCameraMatrix(cameraMatrix)
                        cameraMatrix.invert(inverseMatrix)
                        return true
                    }
                    return false
                }
            })

            // --- LÓGICA DE INTERACCIÓN (DIBUJAR / BORRAR) ---
            val strokeIdMap = mutableMapOf<Int, InProgressStrokeId>()

            wetView.setOnTouchListener { v, event ->
                // Pasamos los eventos a los detectores de gestos primero
                scaleDetector.onTouchEvent(event)
                gestureDetector.onTouchEvent(event)
                
                val action = event.actionMasked
                
                // DETECCIÓN INTELIGENTE DE HERRAMIENTA
                // Si el evento viene de un STYLUS -> PEN. Si es un DEDO -> ERASER.
                // (Esto permite borrar tocando la pantalla mientras sostienes el lápiz)
                val toolType = event.getToolType(0)
                currentTool = if (toolType == MotionEvent.TOOL_TYPE_STYLUS) SketchTool.PEN else SketchTool.ERASER

                // Solo procesamos acciones de edición si hay 1 puntero activo
                if (event.pointerCount == 1) {
                    val pid = event.getPointerId(0)
                    
                    // IMPORTANTE: Convertimos coordenadas de PANTALLA a MUNDO
                    // Esto es vital para saber qué trazo estamos borrando, independientemente del Zoom.
                    val touchPts = floatArrayOf(event.x, event.y)
                    inverseMatrix.mapPoints(touchPts)
                    val worldX = touchPts[0]
                    val worldY = touchPts[1]

                    when (action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (currentTool == SketchTool.PEN) {
                                // --- DIBUJAR ---
                                // Truco de Zoom: Inflamos el pincel visualmente para que coincida con el mundo
                                val currentZoom = InkUtils.getMatrixScale(cameraMatrix)
                                val visualSize = InkUtils.BASE_BRUSH_SIZE * currentZoom

                                val brush = Brush.createWithColorLong(
                                    family = StockBrushes.pressurePen(),
                                    colorLong = Color.pack(Color.BLACK),
                                    size = visualSize,
                                    epsilon = 0.1f
                                )
                                strokeIdMap[pid] = wetView.startStroke(event, pid, brush)
                            } else {
                                // --- BORRAR ---
                                // Intentamos borrar en el punto de contacto inicial
                                canvasView.eraseStrokeAt(worldX, worldY)
                            }
                        }
                        MotionEvent.ACTION_MOVE -> {
                            if (currentTool == SketchTool.PEN) {
                                strokeIdMap[pid]?.let { wetView.addToStroke(event, pid, it, null) }
                            } else {
                                // Borrado continuo al arrastrar el dedo ("goma de borrar")
                                canvasView.eraseStrokeAt(worldX, worldY)
                            }
                        }
                        MotionEvent.ACTION_UP -> {
                            if (currentTool == SketchTool.PEN) {
                                strokeIdMap[pid]?.let {
                                    wetView.finishStroke(event, pid, it)
                                    strokeIdMap.remove(pid)
                                }
                            }
                            v.performClick()
                        }
                    }
                } else {
                    // Si entran más dedos (ej: para hacer zoom), cancelamos cualquier trazo activo
                    if (strokeIdMap.isNotEmpty()) {
                        strokeIdMap.forEach { (_, sid) -> wetView.cancelStroke(sid, event) }
                        strokeIdMap.clear()
                    }
                }
                true
            }

            // --- TRANSFERENCIA DE WET A DRY ---
            wetView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                    for (entry in strokes) {
                        // 1. Transformamos el trazo finalizado a coordenadas de MUNDO
                        // Aquí se corrige la presión y se restaura el tamaño base del pincel
                        val worldStroke = InkUtils.transformStrokeToWorld(entry.value, inverseMatrix)
                        
                        // 2. Lo agregamos a nuestro motor vectorial permanente
                        worldStroke?.let { canvasView.addStroke(it) }
                    }
                    // 3. Limpiamos la vista temporal (Wet)
                    wetView.post { wetView.removeFinishedStrokes(strokes.keys) }
                }
            })

            container
        }
    )
}
package com.skecher.sketchercompanionv1

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

// Imports de Ink
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import android.graphics.Color as AndroidColor

// Import del Predictor
import androidx.input.motionprediction.MotionEventPredictor

@Composable
fun InkCanvas() {
    val context = LocalContext.current

    // Instanciamos las vistas recordando su estado
    val dryInkView = remember { DryInkView(context) }
    val inProgressStrokesView = remember { InProgressStrokesView(context) }

    Box(modifier = Modifier.fillMaxSize()) {

        // --- CAPA 1: Tinta Seca (Fondo) ---
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                dryInkView.setBackgroundColor(AndroidColor.TRANSPARENT)
                dryInkView
            }
        )

        // --- CAPA 2: Tinta Húmeda (Frente) ---
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                inProgressStrokesView.setBackgroundColor(AndroidColor.TRANSPARENT)
                
                // [ELIMINADO] setZOrderMediaOverlay(true) -> Esto causaba el error

                // Aseguramos que tenga foco para recibir eventos
                inProgressStrokesView.isFocusable = true
                inProgressStrokesView.isFocusableInTouchMode = true

                val predictor = MotionEventPredictor.newInstance(inProgressStrokesView)
                
                val brush = Brush.createWithColorLong(
                    family = StockBrushes.pressurePen(),
                    colorLong = AndroidColor.valueOf(AndroidColor.BLACK).pack(),
                    size = 5f,
                    epsilon = 0.1f
                )

                val pointerIdToStrokeId = mutableMapOf<Int, InProgressStrokeId>()

                inProgressStrokesView.setOnTouchListener(object : View.OnTouchListener {
                    @SuppressLint("ClickableViewAccessibility")
                    override fun onTouch(view: View, event: MotionEvent): Boolean {
                        predictor.record(event)
                        val action = event.actionMasked
                        val pointerIndex = event.actionIndex
                        val pointerId = event.getPointerId(pointerIndex)

                        when (action) {
                            MotionEvent.ACTION_DOWN -> {
                                view.requestUnbufferedDispatch(event)
                                val strokeId = inProgressStrokesView.startStroke(event, pointerId, brush)
                                pointerIdToStrokeId[pointerId] = strokeId
                                return true
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val predictedEvent = predictor.predict()
                                try {
                                    for (i in 0 until event.pointerCount) {
                                        val pId = event.getPointerId(i)
                                        val strokeId = pointerIdToStrokeId[pId] ?: continue
                                        inProgressStrokesView.addToStroke(event, pId, strokeId, predictedEvent)
                                    }
                                } finally {
                                    predictedEvent?.recycle()
                                }
                                return true
                            }
                            MotionEvent.ACTION_UP -> {
                                val strokeId = pointerIdToStrokeId[pointerId]
                                if (strokeId != null) {
                                    inProgressStrokesView.finishStroke(event, pointerId, strokeId)
                                    pointerIdToStrokeId.remove(pointerId)
                                }
                                view.performClick()
                                return true
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                val strokeId = pointerIdToStrokeId[pointerId]
                                if (strokeId != null) {
                                    inProgressStrokesView.cancelStroke(strokeId, event)
                                    pointerIdToStrokeId.remove(pointerId)
                                }
                                return true
                            }
                            else -> return false
                        }
                    }
                })

                inProgressStrokesView.addFinishedStrokesListener(object : InProgressStrokesFinishedListener {
                    override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                        dryInkView.addStrokes(strokes.values)
                        inProgressStrokesView.removeFinishedStrokes(strokes.keys)
                    }
                })

                // --- NUEVO HACK DE INICIALIZACIÓN ---
                // Cambiar el alpha fuerza al compositor de hardware a refrescar la capa
                inProgressStrokesView.post {
                    inProgressStrokesView.alpha = 0.99f
                    inProgressStrokesView.post {
                        inProgressStrokesView.alpha = 1.0f
                    }
                }

                inProgressStrokesView
            }
        )
    }
}
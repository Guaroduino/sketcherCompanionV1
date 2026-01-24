package com.skecher.sketchercompanionv1


import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

// Imports de Ink
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.Stroke
import android.graphics.Color as AndroidColor // Alias para evitar conflicto con Compose Color

// Import del Predictor (Input library)
import androidx.input.motionprediction.MotionEventPredictor

@Composable
fun InkCanvas() {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            val container = FrameLayout(context)

            // 1. Tinta Seca
            val dryInkView = DryInkView(context)
            container.addView(dryInkView)

            // 2. Tinta Húmeda
            val inProgressStrokesView = InProgressStrokesView(context)
            inProgressStrokesView.layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            container.addView(inProgressStrokesView)

            // 3. Inicialización
            val predictor = MotionEventPredictor.newInstance(inProgressStrokesView)

            val brush = Brush.createWithColorLong(
                family = StockBrushes.pressurePen(),
                colorLong = AndroidColor.valueOf(AndroidColor.BLACK).pack(),
                size = 5f,
                epsilon = 0.1f
            )

            val pointerIdToStrokeId = mutableMapOf<Int, InProgressStrokeId>()

            inProgressStrokesView.setOnTouchListener(object : View.OnTouchListener {
                // ... (TU LÓGICA DE ONTOUCH SIGUE EXACTAMENTE IGUAL) ...
                // ... No cambies nada dentro del listener ...
                @SuppressLint("ClickableViewAccessibility")
                override fun onTouch(view: View, event: MotionEvent): Boolean {
                    // Copia aquí tu código del onTouch que ya tenías
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

            container
        },
        // --- AGREGA ESTE BLOQUE UPDATE ---
        // Esto se ejecuta justo después de crear la vista y asegura que se dibuje
        update = { container ->
            container.requestLayout()
            container.invalidate()
        }
    )
}
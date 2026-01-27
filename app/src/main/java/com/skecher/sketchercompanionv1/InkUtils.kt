package com.skecher.sketchercompanionv1

import android.graphics.Color
import android.graphics.Matrix
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke

object InkUtils {
    const val BASE_BRUSH_SIZE = 15f

    fun getMatrixScale(matrix: Matrix): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    /**
     * Transforma el trazo y "desinfla" el pincel basándose en el zoom actual.
     * Recupera Familia y Color directamente del trazo original.
     */
    fun transformStrokeToWorld(
        screenStroke: Stroke,
        inverseMatrix: Matrix,
        currentZoom: Float // <--- Solo necesitamos saber el Zoom para corregir el tamaño
    ): Stroke? {
        try {
            // EVITAR Crash por zoom inválido
            if (currentZoom <= 0.001f || currentZoom.isNaN()) return null

            val inputs = screenStroke.inputs
            if (inputs.size == 0) return null

            val builder = MutableStrokeInputBatch()
            val pts = FloatArray(2)

            for (i in 0 until inputs.size) {
                val input = inputs.get(i)
                pts[0] = input.x
                pts[1] = input.y
                inverseMatrix.mapPoints(pts)

                val safePressure = input.pressure.coerceIn(0f, 1f)

                builder.add(
                    type = input.toolType,
                    x = pts[0],
                    y = pts[1],
                    elapsedTimeMillis = input.elapsedTimeMillis,
                    pressure = safePressure,
                    orientationRadians = input.orientationRadians,
                    tiltRadians = input.tiltRadians
                )
            }

            // CORRECCIÓN MAESTRA:
            // En lugar de pedir la config externa, "clonamos" el pincel que ya se usó,
            // pero corregimos su tamaño (Desinflar: TamañoVisual / Zoom = TamañoReal).
            val originalBrush = screenStroke.brush
            
            val realSize = originalBrush.size / currentZoom

            val targetBrush = Brush.createWithColorLong(
                family = originalBrush.family,
                colorLong = originalBrush.colorLong,
                size = realSize, 
                epsilon = originalBrush.epsilon
            )

            return Stroke(targetBrush, builder)
        } catch (e: Exception) {
            // Si algo falla en la transformación, retornamos null para no romper el ciclo
            return null
        }
    }
}
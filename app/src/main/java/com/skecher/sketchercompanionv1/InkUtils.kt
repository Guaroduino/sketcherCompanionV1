package com.skecher.sketchercompanionv1

import android.graphics.Color
import android.graphics.Matrix
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke

object InkUtils {
    const val BASE_BRUSH_SIZE = 15f

    /**
     * Obtiene el nivel de zoom actual de una matriz de transformación.
     */
    fun getMatrixScale(matrix: Matrix): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }

    /**
     * Transforma un trazo de pantalla a coordenadas de mundo.
     * Incluye el "escudo de presión" para evitar el crash.
     */
    fun transformStrokeToWorld(screenStroke: Stroke, inverseMatrix: Matrix): Stroke? {
        val inputs = screenStroke.inputs
        if (inputs.size == 0) return null

        val builder = MutableStrokeInputBatch()
        val pts = FloatArray(2)

        for (i in 0 until inputs.size) {
            val input = inputs.get(i)
            pts[0] = input.x
            pts[1] = input.y
            inverseMatrix.mapPoints(pts)

            // CORRECCIÓN CRÍTICA: Forzar rango [0, 1] para evitar crash de IllegalArgumentException
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

        // Creamos un pincel "Estándar" para guardar el trazo, ignorando el "inflado" visual del zoom
        val baseBrush = Brush.createWithColorLong(
            family = StockBrushes.pressurePen(),
            colorLong = Color.pack(Color.BLACK),
            size = BASE_BRUSH_SIZE,
            epsilon = 0.1f
        )

        return Stroke(baseBrush, builder)
    }
}
package com.sketcher.sketchercompanionv1

import android.graphics.Matrix

object InkUtils {
    // const val BASE_BRUSH_SIZE = 15f // Unused if we remove Ink

    fun getMatrixScale(matrix: Matrix): Float {
        val values = FloatArray(9)
        matrix.getValues(values)
        return values[Matrix.MSCALE_X]
    }
}

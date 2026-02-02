package com.skecher.sketchercompanionv1

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import java.util.UUID

data class ImageElement(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val imageFileName: String, // Filename for storage (e.g. "img_123.png")
    val matrix: Matrix = Matrix()
) : LayerElement {

    // Helper property for serialization
    val matrixValues: FloatArray
        get() {
            val values = FloatArray(9)
            matrix.getValues(values)
            return values
        }

    override fun getBounds(): RectF {
        val rect = RectF(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        matrix.mapRect(rect)
        return rect
    }

    override fun transform(tMatrix: Matrix) {
        matrix.postConcat(tMatrix)
    }

    override fun copyElement(): LayerElement {
        return ImageElement(
            id = UUID.randomUUID().toString(),
            bitmap = bitmap, // Shared bitmap
            imageFileName = imageFileName,
            matrix = Matrix(matrix)
        )
    }
}

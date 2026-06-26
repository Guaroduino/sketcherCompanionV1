package com.sketcher.sketchercompanionv1

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.graphics.PointF
import java.util.UUID

data class ImageElement(
    val id: String = UUID.randomUUID().toString(),
    val bitmap: Bitmap,
    val imageFileName: String, // Filename for storage (e.g. "img_123.png")
    val matrix: Matrix = Matrix(),
    @Transient var originalBitmap: Bitmap? = null,
    val originalImageFileName: String? = null,
    val transparentColors: List<Int> = emptyList(),
    val tolerance: Float = 0f,
    val cropRect: RectF? = null,
    val cropPath: List<PointF>? = null,
    val transparentColorTolerances: List<Float> = emptyList(),
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false
) : LayerElement {

    // Helper property for serialization
    val matrixValues: FloatArray
        get() {
            val values = FloatArray(9)
            matrix.getValues(values)
            return values
        }

    override fun getBoundingBox(library: Map<String, ComponentDefinition>): RectF {
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
            matrix = Matrix(matrix),
            originalBitmap = originalBitmap,
            originalImageFileName = originalImageFileName,
            transparentColors = transparentColors,
            tolerance = tolerance,
            cropRect = cropRect,
            cropPath = cropPath,
            transparentColorTolerances = transparentColorTolerances,
            rotation = rotation,
            flipHorizontal = flipHorizontal,
            flipVertical = flipVertical
        )
    }
}


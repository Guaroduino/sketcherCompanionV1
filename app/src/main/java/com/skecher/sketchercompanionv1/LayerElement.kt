package com.skecher.sketchercompanionv1

import android.graphics.Path

import android.graphics.Matrix
import android.graphics.RectF
import androidx.ink.strokes.StrokeInput

sealed interface LayerElement : Transformable {
    fun copyElement(): LayerElement
}

interface Transformable {
    fun getBounds(): RectF
    fun transform(matrix: Matrix) // Mutates the element's data
}

class AndroidInkElement(val stroke: androidx.ink.strokes.Stroke) : LayerElement {
    var localMatrix: Matrix = Matrix()
    
    override fun getBounds(): RectF {
        val rect = RectF()
        // Compute bounds of the stroke inputs
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        
        val inputs = stroke.inputs
        if (inputs.size == 0) return RectF()

        for (i in 0 until inputs.size) {
            val input = inputs.get(i)
            if (input.x < minX) minX = input.x
            if (input.x > maxX) maxX = input.x
            if (input.y < minY) minY = input.y
            if (input.y > maxY) maxY = input.y
        }
        
        // Inflate by brush radius to capture thickness
        val radius = stroke.brush.size / 2f
        rect.set(minX - radius, minY - radius, maxX + radius, maxY + radius)
        
        localMatrix.mapRect(rect)
        return rect
    }

    override fun transform(matrix: Matrix) {
        localMatrix.postConcat(matrix)
    }

    override fun copyElement(): LayerElement {
        return AndroidInkElement(stroke).apply {
            localMatrix.set(this@AndroidInkElement.localMatrix)
        }
    }
}

data class FillData(val path: Path, val color: Int) : LayerElement {
    override fun getBounds(): RectF {
        val rect = RectF()
        path.computeBounds(rect, true)
        return rect
    }

    override fun transform(matrix: Matrix) {
        path.transform(matrix)
    }

    override fun copyElement(): LayerElement {
        return FillData(Path(path), color)
    }
}

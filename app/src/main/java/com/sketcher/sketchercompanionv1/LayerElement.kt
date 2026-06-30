package com.sketcher.sketchercompanionv1

import android.graphics.Path
import android.graphics.Matrix
import android.graphics.RectF
import com.sketcher.sketchercompanionv1.dto.FillStyle

sealed interface LayerElement : Transformable {
    fun copyElement(): LayerElement
    fun invalidateCache() {}
}

interface Transformable {
    fun getBoundingBox(library: Map<String, ComponentDefinition> = emptyMap()): RectF
    fun transform(matrix: Matrix) // Mutates the element's data
}


data class FillData(val path: Path, val fillStyle: FillStyle) : LayerElement {
    private val cachedBounds = RectF().apply { path.computeBounds(this, true) }

    // Fallback property for backward compatibility with exporters and secondary utilities
    val color: Int
        get() = when (val style = fillStyle) {
            is FillStyle.Solid -> style.color
            is FillStyle.MathTexture -> style.primaryColor
            else -> android.graphics.Color.TRANSPARENT
        }

    override fun getBoundingBox(library: Map<String, ComponentDefinition>): RectF {
        return cachedBounds
    }

    override fun transform(matrix: Matrix) {
        path.transform(matrix)
        path.computeBounds(cachedBounds, true)
    }

    override fun copyElement(): LayerElement {
        return FillData(Path(path), fillStyle)
    }
}



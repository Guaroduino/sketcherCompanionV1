package com.sketcher.sketchercompanionv1

import android.graphics.Path
import android.graphics.Matrix
import android.graphics.RectF

sealed interface LayerElement : Transformable {
    fun copyElement(): LayerElement
    fun invalidateCache() {}
}

interface Transformable {
    fun getBoundingBox(library: Map<String, ComponentDefinition> = emptyMap()): RectF
    fun transform(matrix: Matrix) // Mutates the element's data
}

data class FillData(val path: Path, val color: Int) : LayerElement {
    private val cachedBounds = RectF().apply { path.computeBounds(this, true) }

    override fun getBoundingBox(library: Map<String, ComponentDefinition>): RectF {
        return cachedBounds
    }

    override fun transform(matrix: Matrix) {
        path.transform(matrix)
        path.computeBounds(cachedBounds, true)
    }

    override fun copyElement(): LayerElement {
        return FillData(Path(path), color)
    }
}


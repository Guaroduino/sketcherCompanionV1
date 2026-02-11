package com.sketcher.sketchercompanionv1

import android.graphics.Path
import android.graphics.Matrix
import android.graphics.RectF

sealed interface LayerElement : Transformable {
    fun copyElement(): LayerElement
}

interface Transformable {
    fun getBounds(library: Map<String, ComponentDefinition> = emptyMap()): RectF
    fun transform(matrix: Matrix) // Mutates the element's data
}



data class FillData(val path: Path, val color: Int) : LayerElement {
    override fun getBounds(library: Map<String, ComponentDefinition>): RectF {
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


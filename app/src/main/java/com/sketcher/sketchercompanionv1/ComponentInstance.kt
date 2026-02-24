package com.sketcher.sketchercompanionv1

import android.graphics.Matrix
import android.graphics.RectF

data class ComponentInstance(
    val id: String,
    val definitionId: String, // Link to master
    val matrix: Matrix = Matrix()
) : LayerElement, Transformable {

    override fun transform(tMatrix: Matrix) {
        matrix.postConcat(tMatrix)
    }

    override fun getBoundingBox(library: Map<String, ComponentDefinition>): RectF {
        val definition = library[definitionId] ?: return RectF()
        val rect = RectF()
        for (element in definition.elements) {
            val childBounds = (element as? Transformable)?.getBoundingBox(library) ?: RectF()
            if (rect.isEmpty) rect.set(childBounds)
            else rect.union(childBounds)
        }
        matrix.mapRect(rect)
        return rect
    }

    override fun copyElement(): LayerElement {
        return ComponentInstance(id, definitionId, Matrix(matrix))
    }
}


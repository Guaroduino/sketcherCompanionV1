package com.sketcher.sketchercompanionv1

import android.graphics.Matrix
import android.graphics.RectF

data class ComponentInstance(
    val id: String,
    val definitionId: String, // Link to master
    val matrix: Matrix = Matrix()
) : LayerElement, Transformable {

    @Transient
    private var cachedBounds: RectF? = null

    override fun invalidateCache() {
        cachedBounds = null
    }

    override fun transform(tMatrix: Matrix) {
        matrix.postConcat(tMatrix)
        cachedBounds = null
    }

    override fun getBoundingBox(library: Map<String, ComponentDefinition>): RectF {
        cachedBounds?.let { return it }
        val definition = library[definitionId] ?: return RectF()
        val rect = RectF()
        for (element in definition.elements) {
            val childBounds = element.getBoundingBox(library)
            if (rect.isEmpty) rect.set(childBounds)
            else rect.union(childBounds)
        }
        matrix.mapRect(rect)
        cachedBounds = rect
        return rect
    }

    override fun copyElement(): LayerElement {
        return ComponentInstance(java.util.UUID.randomUUID().toString(), definitionId, Matrix(matrix))
    }
}


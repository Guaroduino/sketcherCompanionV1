package com.sketcher.sketchercompanionv1

import android.graphics.Matrix
import android.graphics.RectF

data class GroupElement(
    val id: String,
    val elements: MutableList<LayerElement>, // Children
    val matrix: Matrix = Matrix()     // Local transformation
) : LayerElement, Transformable {

    override fun transform(tMatrix: Matrix) {
        // Groups transform by updating their own matrix, not the children directly
        matrix.postConcat(tMatrix)
    }

    override fun getBounds(library: Map<String, ComponentDefinition>): RectF {
        val unionRect = RectF()
        if (elements.isEmpty()) return unionRect
        
        // 1. Calculate union of children
        elements.forEachIndexed { index, element ->
            val childBounds = (element as? Transformable)?.getBounds(library) ?: RectF()
            if (index == 0) unionRect.set(childBounds)
            else unionRect.union(childBounds)
        }
        
        // 2. Map the union rect by the group's matrix
        matrix.mapRect(unionRect)
        return unionRect
    }

    override fun copyElement(): LayerElement {
        // Deep copy of children and matrix
        val copiedElements = elements.map { it.copyElement() }.toMutableList()
        val copiedMatrix = Matrix(matrix)
        return GroupElement(id, copiedElements, copiedMatrix)
    }
}


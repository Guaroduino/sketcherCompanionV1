package com.skecher.sketchercompanionv1

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.mutableStateListOf

class SelectionManager {
    val selectedElements = mutableStateListOf<LayerElement>()
    val selectionMatrix = Matrix()

    var baseBounds = RectF()
    
    // Transient Transform (For smooth dragging without data degradation)
    var activeTransform: Matrix? = null

    fun selectSingleAt(x: Float, y: Float, layer: Layer, library: Map<String, ComponentDefinition>, addToSelection: Boolean = false): Boolean {
        // Reverse iterate to get top-most (elements at the end of the list are "on top")
        val iterator = layer.elements.listIterator(layer.elements.size)
        while (iterator.hasPrevious()) {
            val element = iterator.previous()
            if (isHit(element, x, y, library)) {
                if (!addToSelection) {
                    selectedElements.clear()
                    selectionMatrix.reset() // Reset for new selection
                }
                selectedElements.add(element)
                recalculateBaseBounds(library)
                return true
            }
        }
        if (!addToSelection) clearSelection()
        return false
    }

    fun selectArea(selectionPath: Path, layer: Layer, library: Map<String, ComponentDefinition>, addToSelection: Boolean = false) {
        if (!addToSelection) {
            selectedElements.clear()
            selectionMatrix.reset() // Reset for new selection
        }
        val selectionBounds = RectF()
        selectionPath.computeBounds(selectionBounds, true)

        layer.elements.forEach { element ->
            val elementBounds = element.getBounds(library)
            if (RectF.intersects(selectionBounds, elementBounds)) {
                selectedElements.add(element)
            }
        }
        recalculateBaseBounds(library)
    }

    fun recalculateBaseBounds(library: Map<String, ComponentDefinition>) {
        // NOTE: Keep selectionMatrix intact - it represents the cumulative transform of the box
        // Only reset baseBounds to match NEW element bounds after a fresh selection
        baseBounds.setEmpty()
        if (selectedElements.isEmpty()) return
        
        selectedElements.forEachIndexed { index, element ->
            if (index == 0) {
                baseBounds.set(element.getBounds(library))
            } else {
                baseBounds.union(element.getBounds(library))
            }
        }
    }

    fun getSelectionBounds(): RectF {
        // Return transformed bounds for simple logic, but better to use baseBounds + Matrix elsewhere
        val rect = RectF(baseBounds)
        selectionMatrix.mapRect(rect)
        return rect
    }

    fun applyTransform(matrix: Matrix) {
        // Accumulate into activeTransform instead of modifying elements immediately
        if (activeTransform == null) activeTransform = Matrix()
        activeTransform!!.postConcat(matrix)
        
        selectionMatrix.postConcat(matrix)
    }
    
    fun commitTransform() {
        activeTransform?.let { transform ->
             selectedElements.forEach { it.transform(transform) }
             activeTransform = null
             // NOTE: Do NOT reset selectionMatrix here
             // It should persist to keep the transform box aligned with the transformed elements
        }
    }
    
    fun clearSelection() {
        selectedElements.clear()
        selectionMatrix.reset()
        activeTransform = null
        baseBounds.setEmpty()
    }

    private fun isHit(element: LayerElement, x: Float, y: Float, library: Map<String, ComponentDefinition>): Boolean {
        // Simple bounds check for now. Can be refined for strokes.
        return element.getBounds(library).contains(x, y)
    }
}

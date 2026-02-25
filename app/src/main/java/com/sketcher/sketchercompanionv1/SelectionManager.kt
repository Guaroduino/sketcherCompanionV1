package com.sketcher.sketchercompanionv1

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import com.sketcher.sketchercompanionv1.managers.LayerManager

class SelectionManager {
    val selectedElements = mutableStateListOf<LayerElement>()
    
    // Observable Compose States
    val hasSelection: State<Boolean> = derivedStateOf { selectedElements.isNotEmpty() }
    val selectionCount: State<Int> = derivedStateOf { selectedElements.size }

    val selectionMatrix = Matrix()
    var activeTransform: Matrix? = null
    var baseBounds = RectF()
    
    // Transient Transform (For smooth dragging without data degradation)

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
            val elementBounds = element.getBoundingBox(library)
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
                baseBounds.set(element.getBoundingBox(library))
            } else {
                baseBounds.union(element.getBoundingBox(library))
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
    
    // --- TOUCH INTERPOLATION HOOKS ---
    fun startSelection(worldX: Float, worldY: Float) {
        // TODO: Initialize freehand selection path
    }

    fun updateSelection(worldX: Float, worldY: Float) {
        // TODO: Append points to freehand selection path
    }

    fun finalizeSelection(activeLayer: com.sketcher.sketchercompanionv1.Layer) {
        // TODO: Compute collisions against elements in activeLayer and populate selectedElements
    }

    fun handleTransformDown(worldX: Float, worldY: Float) {
        // TODO: Detect if touching a handle (scale/rotate) or inside (translate)
    }

    fun handleTransformMove(worldX: Float, worldY: Float) {
        // TODO: Update activeTransform matrix based on drag delta
    }

    fun handleTransformUp() {
        // TODO: Commit transform to elements / update matrix
    }
    // ---------------------------------

    fun clearSelection() {
        selectedElements.clear()
        selectionMatrix.reset()
        activeTransform = null
        baseBounds.setEmpty()
    }

    fun deleteSelected(layerManager: LayerManager, activeLayerIndex: Int, onPerformSnapshot: (String, () -> Unit) -> Unit) {
        if (selectedElements.isEmpty()) return
        onPerformSnapshot("Borrar Selección") {
            val currentLayers = layerManager.layers.toMutableList()
            currentLayers.forEachIndexed { index, layer ->
                val remaining = layer.elements.filter { it !in selectedElements }
                if (remaining.size != layer.elements.size) {
                    currentLayers[index] = layer.copy(elements = remaining.toMutableStateList())
                }
            }
            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            clearSelection()
        }
    }

    fun duplicateSelected(layerManager: LayerManager, activeLayerIndex: Int, onPerformSnapshot: (String, () -> Unit) -> Unit) {
        if (selectedElements.isEmpty()) return
        onPerformSnapshot("Duplicar Selección") {
            val currentLayers = layerManager.layers.toMutableList()
            val activeLayer = currentLayers[activeLayerIndex]
            
            // For simplicity, duplicate them in the active layer with a slight offset
            val offsetMatrix = Matrix().apply { postTranslate(20f, 20f) }
            val duplicatedElements = selectedElements.map { 
                val copy = it.copyElement()
                copy.transform(offsetMatrix)
                copy
            }
            
            currentLayers[activeLayerIndex] = activeLayer.copy(
                elements = (activeLayer.elements + duplicatedElements).toMutableStateList()
            )
            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            
            // Select the new elements
            selectedElements.clear()
            selectedElements.addAll(duplicatedElements)
            selectionMatrix.reset()
            // Recalculate base bounds based on the new selection
            // Requires library for bounds? The manager doesn't hold it, so we might need a workaround or pass it,
            // but the method recalculateBaseBounds handles it using bounds logic if called from ViewModel later.
        }
    }

    private fun isHit(element: LayerElement, x: Float, y: Float, library: Map<String, ComponentDefinition>): Boolean {
        // Simple bounds check for now. Can be refined for strokes.
        return element.getBoundingBox(library).contains(x, y)
    }
}


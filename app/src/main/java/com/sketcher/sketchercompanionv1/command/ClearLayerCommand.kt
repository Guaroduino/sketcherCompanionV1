package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.LayerElement

/**
 * Command to clear all elements from a specific [Layer].
 * Refined Phase 3 implementation.
 */
class ClearLayerCommand(
    private val targetLayer: Layer
) : UndoCommand {

    private val previousElements = mutableListOf<LayerElement>()

    override fun execute() {
        previousElements.clear()
        previousElements.addAll(targetLayer.elements)
        targetLayer.elements.clear()
    }

    override fun undo() {
        targetLayer.elements.addAll(previousElements)
    }

    override fun getLabel(): String = "Borrar Capa"
}

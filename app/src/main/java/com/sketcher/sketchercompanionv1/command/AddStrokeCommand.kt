package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.VectorStroke

/**
 * Command to add a [VectorStroke] to a specific [Layer].
 * Refined Phase 3 implementation.
 */
class AddStrokeCommand(
    private val targetLayer: Layer,
    private val strokeToAdd: VectorStroke
) : UndoCommand {

    override fun execute() {
        targetLayer.elements.add(strokeToAdd)
    }

    override fun undo() {
        targetLayer.elements.remove(strokeToAdd)
    }

    override fun getLabel(): String = "Añadir Trazo"
}

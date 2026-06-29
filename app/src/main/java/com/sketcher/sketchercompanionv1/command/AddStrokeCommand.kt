package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.LayerElement
import com.sketcher.sketchercompanionv1.VectorStroke

/**
 * Command to add a [VectorStroke] to a specific container.
 * Refined Phase 3 implementation.
 */
class AddStrokeCommand(
    private val targetContainer: MutableList<LayerElement>,
    private val strokeToAdd: VectorStroke
) : UndoCommand {

    override fun execute() {
        targetContainer.add(strokeToAdd)
    }

    override fun undo() {
        targetContainer.remove(strokeToAdd)
    }

    override fun getLabel(): String = "Añadir Trazo"
}

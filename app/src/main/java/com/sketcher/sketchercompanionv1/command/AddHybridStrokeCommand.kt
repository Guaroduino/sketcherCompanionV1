package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.FillData
import com.sketcher.sketchercompanionv1.LayerElement
import com.sketcher.sketchercompanionv1.VectorStroke

/**
 * Command to add both a [VectorStroke] and [FillData] to a specific container.
 * Refined Phase 3 implementation.
 */
class AddHybridStrokeCommand(
    private val targetContainer: MutableList<LayerElement>,
    private val strokeToAdd: VectorStroke,
    private val fillToAdd: FillData?
) : UndoCommand {

    override fun execute() {
        fillToAdd?.let { targetContainer.add(it) }
        targetContainer.add(strokeToAdd)
    }

    override fun undo() {
        targetContainer.remove(strokeToAdd)
        fillToAdd?.let { targetContainer.remove(it) }
    }

    override fun getLabel(): String = "Añadir Trazo con Relleno"
}

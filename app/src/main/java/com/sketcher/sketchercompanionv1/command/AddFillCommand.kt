package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.FillData
import com.sketcher.sketchercompanionv1.Layer

/**
 * Command to add [FillData] to a specific [Layer].
 * Refined Phase 3 implementation.
 */
class AddFillCommand(
    private val targetLayer: Layer,
    private val fillToAdd: FillData
) : UndoCommand {

    override fun execute() {
        targetLayer.elements.add(fillToAdd)
    }

    override fun undo() {
        targetLayer.elements.remove(fillToAdd)
    }

    override fun getLabel(): String = "Añadir Relleno"
}

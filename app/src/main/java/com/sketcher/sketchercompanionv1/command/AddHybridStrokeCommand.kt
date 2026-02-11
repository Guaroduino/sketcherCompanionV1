package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.FillData
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.VectorStroke

/**
 * Command to add both a [VectorStroke] and [FillData] to a specific [Layer].
 * Refined Phase 3 implementation.
 */
class AddHybridStrokeCommand(
    private val targetLayer: Layer,
    private val strokeToAdd: VectorStroke,
    private val fillToAdd: FillData?
) : UndoCommand {

    override fun execute() {
        fillToAdd?.let { targetLayer.elements.add(it) }
        targetLayer.elements.add(strokeToAdd)
    }

    override fun undo() {
        targetLayer.elements.remove(strokeToAdd)
        fillToAdd?.let { targetLayer.elements.remove(it) }
    }

    override fun getLabel(): String = "Añadir Trazo con Relleno"
}

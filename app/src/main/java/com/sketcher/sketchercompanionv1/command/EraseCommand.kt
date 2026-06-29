package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.LayerElement

/**
 * Command to erase a [LayerElement] from a [Layer].
 * Supports Undo by restoring it to its original position.
 */
class EraseCommand(
    private val targetContainer: MutableList<LayerElement>,
    private val elementToRemove: LayerElement
) : UndoCommand {

    private var originalIndex: Int = -1

    override fun execute() {
        val index = targetContainer.indexOf(elementToRemove)
        if (index != -1) {
            originalIndex = index
            targetContainer.removeAt(index)
        }
    }

    override fun undo() {
        if (originalIndex != -1) {
            if (originalIndex <= targetContainer.size) {
                targetContainer.add(originalIndex, elementToRemove)
            } else {
                targetContainer.add(elementToRemove)
            }
        }
    }

    override fun getLabel(): String = "Borrador"
}

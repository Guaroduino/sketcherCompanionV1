package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.LayerElement

/**
2:  * Generic Command to replace a set of [LayerElement]s in a [Layer] with a new set.
3:  * Used for CAD operations like Trim, Extend, and Grip Editing.
4:  */
class ModifyElementsCommand(
    private val targetLayer: Layer,
    private val elementsToRemove: List<LayerElement>,
    private val elementsToAdd: List<LayerElement>,
    private val label: String = "Modificar Elementos"
) : UndoCommand {

    override fun execute() {
        targetLayer.elements.removeAll(elementsToRemove)
        targetLayer.elements.addAll(elementsToAdd)
    }

    override fun undo() {
        targetLayer.elements.removeAll(elementsToAdd)
        targetLayer.elements.addAll(elementsToRemove)
    }

    override fun getLabel(): String = label
}

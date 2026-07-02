package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.LayerElement

/**
 * Command to replace a set of original elements with modified/split versions during a partial erase action.
 * Supports Undo and Redo while preserving original z-indices.
 */
class PointEraseCommand(
    private val targetContainer: MutableList<LayerElement>,
    private val originalElements: List<LayerElement>,
    private val replacementElements: List<LayerElement>
) : UndoCommand {

    private val originalIndices = originalElements.map { targetContainer.indexOf(it) }

    override fun execute() {
        for (el in originalElements) {
            targetContainer.remove(el)
        }
        for (el in replacementElements) {
            if (!targetContainer.contains(el)) {
                targetContainer.add(el)
            }
        }
    }

    override fun undo() {
        for (el in replacementElements) {
            targetContainer.remove(el)
        }
        for (i in originalElements.indices) {
            val el = originalElements[i]
            val idx = originalIndices[i]
            if (idx in 0..targetContainer.size) {
                targetContainer.add(idx, el)
            } else {
                targetContainer.add(el)
            }
        }
    }

    override fun getLabel(): String = "Borrador de Puntos"
}

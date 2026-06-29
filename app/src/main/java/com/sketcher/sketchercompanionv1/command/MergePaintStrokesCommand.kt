package com.sketcher.sketchercompanionv1.command

import com.sketcher.sketchercompanionv1.LayerElement
import com.sketcher.sketchercompanionv1.VectorStroke

/**
 * Command to merge a new paint stroke with existing overlapping paint strokes.
 * Supports Undo and Redo operations.
 */
class MergePaintStrokesCommand(
    private val targetContainer: MutableList<LayerElement>,
    private val existingStrokes: List<VectorStroke>,
    private val newStroke: VectorStroke,
    private val mergedStroke: VectorStroke
) : UndoCommand {

    override fun execute() {
        targetContainer.removeAll(existingStrokes)
        targetContainer.add(mergedStroke)
    }

    override fun undo() {
        targetContainer.remove(mergedStroke)
        targetContainer.addAll(existingStrokes)
    }

    override fun getLabel(): String = "Fusionar Pintura"
}

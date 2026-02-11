package com.sketcher.sketchercompanionv1.command

/**
 * Interface for reversible actions in the application.
 * Part of the Phase 3 Command Pattern refactor.
 */
interface UndoCommand {
    /**
     * Executes (or re-executes) the action.
     */
    fun execute()

    /**
     * Reverses the action.
     */
    fun undo()

    /**
     * Optional label for UI hints (e.g., "Undo Stroke").
     */
    fun getLabel(): String
}

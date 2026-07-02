package com.sketcher.sketchercompanionv1.command

/**
 * Command to execute and undo multiple commands as a single transaction.
 */
class CompoundCommand(
    private val commands: List<UndoCommand>,
    private val label: String = "Acción Compuesta"
) : UndoCommand {

    override fun execute() {
        for (cmd in commands) {
            cmd.execute()
        }
    }

    override fun undo() {
        // Revert in reverse order
        for (i in commands.indices.reversed()) {
            commands[i].undo()
        }
    }

    override fun getLabel(): String = label
}

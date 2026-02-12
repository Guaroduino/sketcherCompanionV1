package com.sketcher.sketchercompanionv1.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

object ToolRegistry {
    val allTools = listOf(
        StudioTool("pencil", Icons.Default.Edit, "Pencil"),
        StudioTool("eraser", Icons.Default.AutoFixNormal, "Eraser"),
        StudioTool("brush", Icons.Default.Brush, "Brush"),
        StudioTool("line", Icons.Default.Timeline, "Line"),
        StudioTool("circle", Icons.Default.RadioButtonUnchecked, "Circle"),
        StudioTool("square", Icons.Default.CheckBoxOutlineBlank, "Square"),
        StudioTool("undo", Icons.Default.Undo, "Undo"),
        StudioTool("redo", Icons.Default.Redo, "Redo"),
        StudioTool("layers", Icons.Default.Layers, "Layers"),
        StudioTool("palette", Icons.Default.Palette, "Palette"),
        StudioTool("settings", Icons.Default.Settings, "Settings"),
        StudioTool("save", Icons.Default.Save, "Save"),
        StudioTool("play", Icons.Default.PlayArrow, "Play"),
        StudioTool("pause", Icons.Default.Pause, "Pause"),
        StudioTool("zoom_in", Icons.Default.ZoomIn, "Zoom In"),
        StudioTool("zoom_out", Icons.Default.ZoomOut, "Zoom Out"),
        StudioTool("menu", Icons.Default.Menu, "Menu"),
        StudioTool("divider", Icons.Default.Remove, "Divider")
    )

    fun getToolById(id: String): StudioTool? = allTools.find { it.id == id }
}

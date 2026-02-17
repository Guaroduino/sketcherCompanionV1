package com.sketcher.sketchercompanionv1.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Tune

object ToolRegistry {
    val allTools = listOf(
        StudioTool("pencil", Icons.Default.Edit, "Pencil", isPlaceholder = false),
        StudioTool("eraser", Icons.Default.AutoFixNormal, "Eraser", isPlaceholder = false),
        StudioTool("brush", Icons.Default.Brush, "Brush", isPlaceholder = false),
        StudioTool("line", Icons.Default.Timeline, "Line", isPlaceholder = false),
        StudioTool("circle", Icons.Default.RadioButtonUnchecked, "Circle", isPlaceholder = false),
        StudioTool("square", Icons.Default.CheckBoxOutlineBlank, "Square", isPlaceholder = false),
        StudioTool("undo", Icons.Default.Undo, "Undo", isPlaceholder = false),
        StudioTool("redo", Icons.Default.Redo, "Redo", isPlaceholder = false),
        StudioTool("layers", Icons.Default.Layers, "Layers", isPlaceholder = true),
        StudioTool("palette", Icons.Default.Palette, "Palette", isPlaceholder = true),
        StudioTool("opacity", Icons.Default.Opacity, "Opacity", isPlaceholder = true),
        StudioTool("settings", Icons.Default.Settings, "Settings", isPlaceholder = false),
        StudioTool("save", Icons.Default.Save, "Save", isPlaceholder = true),
        StudioTool("play", Icons.Default.PlayArrow, "Play", isPlaceholder = true),
        StudioTool("pause", Icons.Default.Pause, "Pause", isPlaceholder = true),
        StudioTool("zoom_in", Icons.Default.ZoomIn, "Zoom In", isPlaceholder = true),
        StudioTool("zoom_out", Icons.Default.ZoomOut, "Zoom Out", isPlaceholder = true),
        StudioTool("menu", Icons.Default.Menu, "Menu", isPlaceholder = false),
        StudioTool(StudioTool.PROPERTIES_TOOL_ID, Icons.Default.Tune, "Properties", isPlaceholder = false),
        StudioTool(StudioTool.STABILIZATION_TOOL_ID, Icons.Default.Timeline, "Stabilization", isPlaceholder = false),
        StudioTool(StudioTool.SIZE_OPACITY_TOOL_ID, Icons.Default.Lens, "Size & Opacity", isPlaceholder = false),
        StudioTool("divider", Icons.Default.Remove, "Divider", isPlaceholder = false),
        StudioTool("edit", Icons.Default.Edit, "Edit", isPlaceholder = true),
        StudioTool("create", Icons.Default.Add, "Create", isPlaceholder = true)
    )

    fun getToolById(id: String): StudioTool? = allTools.find { it.id == id }
}

package com.sketcher.sketchercompanionv1.ui.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.outlined.*

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
        // Selection tools
        StudioTool("tool_selection", Icons.Outlined.AllOut, "Selection", isPlaceholder = false),
        StudioTool("tool_selection_freehand", Icons.Outlined.AllOut, "Lasso Selection", isPlaceholder = false, parentGroupId = "tool_selection"),
        StudioTool("tool_selection_polygon", Icons.Outlined.Timeline, "Polygon Selection", isPlaceholder = false, parentGroupId = "tool_selection"),
        StudioTool("tool_selection_rect", Icons.Outlined.Crop, "Rect Selection", isPlaceholder = false, parentGroupId = "tool_selection"),
        
        // Contextual Selection Actions
        StudioTool("context_deselect", Icons.Outlined.Deselect, "Deselect", isPlaceholder = false, isContextual = true),
        StudioTool("context_transform", Icons.Outlined.OpenWith, "Transform", isPlaceholder = false, isContextual = true),
        StudioTool("context_copy", Icons.Outlined.ContentCopy, "Duplicate", isPlaceholder = false, isContextual = true),
        StudioTool("context_delete", Icons.Outlined.Delete, "Delete", isPlaceholder = false, isContextual = true),
        StudioTool("context_flip_horizontal", Icons.Outlined.Flip, "Flip Horizontal", isPlaceholder = false, isContextual = true),
        StudioTool("context_flip_vertical", Icons.Outlined.Flip, "Flip Vertical", isPlaceholder = false, isContextual = true),
        StudioTool("context_edit_image", Icons.Default.Edit, "Edit Image", isPlaceholder = false, isContextual = true),
        
        // Existing tools...
        StudioTool("layers", Icons.Default.Layers, "Layers", isPlaceholder = true),
        StudioTool("palette", Icons.Default.Palette, "Palette", isPlaceholder = true),
        StudioTool("opacity", Icons.Default.Opacity, "Opacity", isPlaceholder = true),
        StudioTool("settings", Icons.Default.Settings, "Settings", isPlaceholder = false),
        StudioTool("save", Icons.Default.Save, "Save", isPlaceholder = true),
        StudioTool("play", Icons.Default.PlayArrow, "Play", isPlaceholder = true),
        StudioTool("pause", Icons.Default.Pause, "Pause", isPlaceholder = true),
        StudioTool("zoom_in", Icons.Default.ZoomIn, "Zoom In", isPlaceholder = false),
        StudioTool("zoom_out", Icons.Default.ZoomOut, "Zoom Out", isPlaceholder = false),
        StudioTool("zoom_fit", Icons.Default.FitScreen, "Zoom Extends", isPlaceholder = false),
        StudioTool("home_view", Icons.Default.Home, "Reset View", isPlaceholder = false),
        StudioTool("menu", Icons.Default.Menu, "Menu", isPlaceholder = false),
        StudioTool(StudioTool.PROPERTIES_TOOL_ID, Icons.Default.Tune, "Properties", isPlaceholder = false),
        StudioTool(StudioTool.STABILIZATION_TOOL_ID, Icons.Default.Timeline, "Stabilization", isPlaceholder = false),
        StudioTool(StudioTool.SIZE_OPACITY_TOOL_ID, Icons.Default.Lens, "Size & Opacity", isPlaceholder = false),
        StudioTool("divider", Icons.Default.Remove, "Divider", isPlaceholder = false),
        StudioTool("edit", Icons.Default.Edit, "Edit", isPlaceholder = true),
        StudioTool("create", Icons.Default.Add, "Create", isPlaceholder = true),
        StudioTool("stroke_color", Icons.Default.BorderColor, "Stroke Color", isPlaceholder = false),
        StudioTool("fill_color", Icons.Default.FormatColorFill, "Fill Color", isPlaceholder = false)
    )

    fun getToolById(id: String): StudioTool? = allTools.find { it.id == id }

    fun getSubToolsFor(registryId: String): List<StudioTool> {
        val tool = allTools.find { it.id == registryId } ?: return emptyList()
        val groupId = tool.parentGroupId ?: tool.id
        return allTools.filter { !it.isPlaceholder && (it.parentGroupId == groupId || it.id == groupId) && it.id != registryId }
    }
}

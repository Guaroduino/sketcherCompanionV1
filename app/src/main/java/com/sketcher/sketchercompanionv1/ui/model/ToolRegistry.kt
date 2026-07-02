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
    var showExperimental: Boolean = true

    val allTools: List<StudioTool>
        get() = if (showExperimental) {
            fullToolsList
        } else {
            fullToolsList.filter { !it.isExperimental }
        }

    private val fullToolsList = listOf(
        StudioTool("pencil", Icons.Default.Edit, "Pencil", isPlaceholder = false),
        StudioTool("pen", Icons.Default.Gesture, "Pen", isPlaceholder = false, parentGroupId = "pencil"),
        StudioTool("eraser", Icons.Default.AutoFixNormal, "Borrador", isPlaceholder = false, subTools = listOf(
            StudioTool("eraser", Icons.Default.AutoFixNormal, "Borrador de Trazo", isPlaceholder = false, parentGroupId = "eraser"),
            StudioTool("point_eraser", Icons.Default.AutoFixNormal, "Borrador de Puntos", isPlaceholder = false, parentGroupId = "eraser"),
            StudioTool("cut_eraser", Icons.Default.AutoFixNormal, "Borrador de Corte", isPlaceholder = false, parentGroupId = "eraser")
        )),
        StudioTool("point_eraser", Icons.Default.AutoFixNormal, "Borrador de Puntos", isPlaceholder = false, parentGroupId = "eraser"),
        StudioTool("cut_eraser", Icons.Default.AutoFixNormal, "Borrador de Corte", isPlaceholder = false, parentGroupId = "eraser"),
        StudioTool("paint", Icons.Default.Brush, "Paint", isPlaceholder = false, parentGroupId = "pencil"),
        StudioTool("watercolor", Icons.Default.Palette, "Acuarela", isPlaceholder = false, parentGroupId = "pencil", isExperimental = true),
        StudioTool("pluma", Icons.Default.Gesture, "Pluma", isPlaceholder = false, parentGroupId = "pencil"),
        StudioTool("pencil_cumulative", Icons.Default.Edit, "Pencil Acumulativo", isPlaceholder = false, parentGroupId = "pencil", isExperimental = true),
        
        StudioTool("stroke_type", Icons.Default.Gesture, "Tipo de Trazo", isPlaceholder = false, subTools = listOf(
            StudioTool("stroke_freehand", Icons.Default.Gesture, "Mano Alzada", isPlaceholder = false, parentGroupId = "stroke_type"),
            StudioTool("stroke_line", Icons.Default.Timeline, "Línea", isPlaceholder = false, parentGroupId = "stroke_type"),
            StudioTool("stroke_circle", Icons.Default.RadioButtonUnchecked, "Círculo", isPlaceholder = false, parentGroupId = "stroke_type"),
            StudioTool("stroke_polyline", Icons.Default.Polyline, "Polilínea", isPlaceholder = false, parentGroupId = "stroke_type"),
            StudioTool("stroke_arc", Icons.Default.Gesture, "Arco", isPlaceholder = false, parentGroupId = "stroke_type"),
            StudioTool("stroke_ellipse", Icons.Default.Adjust, "Elipse", isPlaceholder = false, parentGroupId = "stroke_type"),
            StudioTool("stroke_spline", Icons.Default.Gesture, "Spline", isPlaceholder = false, parentGroupId = "stroke_type"),
            StudioTool("stroke_bezier", Icons.Default.FormatShapes, "Bézier", isPlaceholder = false, parentGroupId = "stroke_type")
        )),

        StudioTool("trim", Icons.Default.ContentCut, "Trim", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("extend", Icons.Default.TrendingFlat, "Extend", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("orto", Icons.Default.SquareFoot, "Ortho Mode", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("mirror", Icons.Default.Compare, "Mirror", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("mover_pt_pt", Icons.Default.OpenWith, "Move Point to Point", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("align_2_pt", Icons.Default.AlignHorizontalLeft, "Align 2 Points", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("offset", Icons.Default.CopyAll, "Offset", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("fillet", Icons.Default.RoundedCorner, "Fillet", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("chamfer", Icons.Default.Architecture, "Chamfer", isPlaceholder = false, parentGroupId = "edit_points"),
        StudioTool("edit_points", Icons.Default.Build, "Edit Points", isPlaceholder = false, subTools = listOf(
            StudioTool("orto", Icons.Default.SquareFoot, "Ortho Mode", isPlaceholder = false, parentGroupId = "edit_points"),
            StudioTool("mirror", Icons.Default.Compare, "Mirror", isPlaceholder = false, parentGroupId = "edit_points"),
            StudioTool("mover_pt_pt", Icons.Default.OpenWith, "Move Point to Point", isPlaceholder = false, parentGroupId = "edit_points"),
            StudioTool("align_2_pt", Icons.Default.AlignHorizontalLeft, "Align 2 Points", isPlaceholder = false, parentGroupId = "edit_points"),
            StudioTool("offset", Icons.Default.CopyAll, "Offset", isPlaceholder = false, parentGroupId = "edit_points"),
            StudioTool("fillet", Icons.Default.RoundedCorner, "Fillet", isPlaceholder = false, parentGroupId = "edit_points"),
            StudioTool("chamfer", Icons.Default.Architecture, "Chamfer", isPlaceholder = false, parentGroupId = "edit_points"),
            StudioTool("trim", Icons.Default.ContentCut, "Trim", isPlaceholder = false, parentGroupId = "edit_points"),
            StudioTool("extend", Icons.Default.TrendingFlat, "Extend", isPlaceholder = false, parentGroupId = "edit_points")
        )),
        StudioTool("toggle_snap", Icons.Default.FilterCenterFocus, "Toggle Snap", isPlaceholder = false, isExperimental = true),
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
        StudioTool("context_flip_vertical", Icons.Outlined.Flip, "Flip Vertical", isPlaceholder = false, isContextual = true, parentGroupId = "context_flip_horizontal"),
        StudioTool("context_edit_image", Icons.Default.Edit, "Edit Image", isPlaceholder = false, isContextual = true),
        StudioTool("context_group", Icons.Outlined.GroupWork, "Grupo", isPlaceholder = false, isContextual = true),
        StudioTool("context_component", Icons.Outlined.Widgets, "Componente", isPlaceholder = false, isContextual = true, parentGroupId = "context_group"),
        StudioTool("context_ungroup", Icons.Outlined.LinkOff, "Desagrupar", isPlaceholder = false, isContextual = true, parentGroupId = "context_group"),
        StudioTool("context_make_unique", Icons.Outlined.AutoAwesome, "Hacer Único", isPlaceholder = false, isContextual = true, parentGroupId = "context_group"),
        StudioTool("context_edit", Icons.Outlined.Edit, "Editar", isPlaceholder = false, isContextual = true),
        
        StudioTool("action_copy", Icons.Outlined.ContentCopy, "Copiar", isPlaceholder = false),
        StudioTool("action_cut", Icons.Outlined.ContentCut, "Cortar", isPlaceholder = false),
        StudioTool("action_paste", Icons.Outlined.ContentPaste, "Pegar", isPlaceholder = false),
        
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
        StudioTool(StudioTool.SIZE_OPACITY_TOOL_ID, Icons.Default.Lens, "Brush Settings", isPlaceholder = false),
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

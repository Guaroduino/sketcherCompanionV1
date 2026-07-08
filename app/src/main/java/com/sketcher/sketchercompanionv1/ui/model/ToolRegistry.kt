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
import com.sketcher.sketchercompanionv1.R

object ToolRegistry {
    var showExperimental: Boolean = true

    val allTools: List<StudioTool>
        get() = if (showExperimental) {
            fullToolsList
        } else {
            fullToolsList.filter { !it.isExperimental }
        }

    private val fullToolsList = listOf(
        StudioTool("pencil", Icons.Default.Edit, "Pencil", R.drawable.ic_tabler_pencil, isPlaceholder = false),
        StudioTool("pen", Icons.Default.Gesture, "Pen", R.drawable.ic_tabler_pen, isPlaceholder = false, parentGroupId = "pencil"),
        StudioTool("eraser", Icons.Default.AutoFixNormal, "Borrador", R.drawable.ic_tabler_eraser, isPlaceholder = false, subTools = listOf(
            StudioTool("eraser", Icons.Default.AutoFixNormal, "Borrador de Trazo", R.drawable.ic_tabler_eraser, isPlaceholder = false, parentGroupId = "eraser"),
            StudioTool("point_eraser", Icons.Default.AutoFixNormal, "Borrador de Puntos", R.drawable.ic_tabler_point_eraser, isPlaceholder = false, parentGroupId = "eraser"),
            StudioTool("cut_eraser", Icons.Default.AutoFixNormal, "Borrador de Corte", R.drawable.ic_tabler_cut_eraser, isPlaceholder = false, parentGroupId = "eraser")
        )),
        StudioTool("point_eraser", Icons.Default.AutoFixNormal, "Borrador de Puntos", R.drawable.ic_tabler_point_eraser, isPlaceholder = false, parentGroupId = "eraser"),
        StudioTool("cut_eraser", Icons.Default.AutoFixNormal, "Borrador de Corte", R.drawable.ic_tabler_cut_eraser, isPlaceholder = false, parentGroupId = "eraser"),
        StudioTool("paint", Icons.Default.Brush, "Paint", R.drawable.ic_tabler_paint, isPlaceholder = false, parentGroupId = "pencil"),
        StudioTool("watercolor", Icons.Default.Palette, "Acuarela", R.drawable.ic_tabler_watercolor, isPlaceholder = false, parentGroupId = "pencil", isExperimental = true),
        StudioTool("pluma", Icons.Default.Gesture, "Pluma", R.drawable.ic_tabler_pluma, isPlaceholder = false, parentGroupId = "pencil"),
        StudioTool("pencil_cumulative", Icons.Default.Edit, "Pencil Acumulativo", R.drawable.ic_tabler_pencil_cumulative, isPlaceholder = false, parentGroupId = "pencil", isExperimental = true),
        StudioTool("text", Icons.Default.Title, "Texto", isPlaceholder = false),
        
        StudioTool("stroke_freehand", Icons.Default.Gesture, "Mano Alzada", R.drawable.ic_tabler_stroke_freehand, isPlaceholder = false, subTools = listOf(
            StudioTool("stroke_line", Icons.Default.Timeline, "Línea", R.drawable.ic_tabler_stroke_line, isPlaceholder = false, parentGroupId = "stroke_freehand"),
            StudioTool("stroke_circle", Icons.Default.RadioButtonUnchecked, "Círculo", R.drawable.ic_tabler_stroke_circle, isPlaceholder = false, parentGroupId = "stroke_freehand"),
            StudioTool("stroke_polyline", Icons.Default.Polyline, "Polilínea", R.drawable.ic_tabler_stroke_polyline, isPlaceholder = false, parentGroupId = "stroke_freehand"),
            StudioTool("stroke_arc", Icons.Default.Gesture, "Arco", R.drawable.ic_tabler_stroke_arc, isPlaceholder = false, parentGroupId = "stroke_freehand"),
            StudioTool("stroke_ellipse", Icons.Default.Adjust, "Elipse", R.drawable.ic_tabler_stroke_ellipse, isPlaceholder = false, parentGroupId = "stroke_freehand"),
            StudioTool("stroke_spline", Icons.Default.Gesture, "Spline", R.drawable.ic_tabler_stroke_spline, isPlaceholder = false, parentGroupId = "stroke_freehand"),
            StudioTool("stroke_bezier", Icons.Default.FormatShapes, "Bézier", R.drawable.ic_tabler_stroke_bezier, isPlaceholder = false, parentGroupId = "stroke_freehand")
        )),
        StudioTool("stroke_line", Icons.Default.Timeline, "Línea", R.drawable.ic_tabler_stroke_line, isPlaceholder = false, parentGroupId = "stroke_freehand"),
        StudioTool("stroke_circle", Icons.Default.RadioButtonUnchecked, "Círculo", R.drawable.ic_tabler_stroke_circle, isPlaceholder = false, parentGroupId = "stroke_freehand"),
        StudioTool("stroke_polyline", Icons.Default.Polyline, "Polilínea", R.drawable.ic_tabler_stroke_polyline, isPlaceholder = false, parentGroupId = "stroke_freehand"),
        StudioTool("stroke_arc", Icons.Default.Gesture, "Arco", R.drawable.ic_tabler_stroke_arc, isPlaceholder = false, parentGroupId = "stroke_freehand"),
        StudioTool("stroke_ellipse", Icons.Default.Adjust, "Elipse", R.drawable.ic_tabler_stroke_ellipse, isPlaceholder = false, parentGroupId = "stroke_freehand"),
        StudioTool("stroke_spline", Icons.Default.Gesture, "Spline", R.drawable.ic_tabler_stroke_spline, isPlaceholder = false, parentGroupId = "stroke_freehand"),
        StudioTool("stroke_bezier", Icons.Default.FormatShapes, "Bézier", R.drawable.ic_tabler_stroke_bezier, isPlaceholder = false, parentGroupId = "stroke_freehand"),

        StudioTool("trim", Icons.Default.ContentCut, "Trim", R.drawable.ic_tabler_trim, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("extend", Icons.Default.TrendingFlat, "Extend", R.drawable.ic_tabler_extend, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("orto", Icons.Default.SquareFoot, "Ortho Mode", R.drawable.ic_tabler_orto, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("mirror", Icons.Default.Compare, "Mirror", R.drawable.ic_tabler_mirror, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("mover_pt_pt", Icons.Default.OpenWith, "Move Point to Point", R.drawable.ic_tabler_mover_pt_pt, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("align_2_pt", Icons.Default.AlignHorizontalLeft, "Align 2 Points", R.drawable.ic_tabler_align_2_pt, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("offset", Icons.Default.CopyAll, "Offset", R.drawable.ic_tabler_offset, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("fillet", Icons.Default.RoundedCorner, "Fillet", R.drawable.ic_tabler_fillet, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("chamfer", Icons.Default.Architecture, "Chamfer", R.drawable.ic_tabler_chamfer, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
        StudioTool("edit_points", Icons.Default.Build, "Edit Points", R.drawable.ic_tabler_edit_points, isPlaceholder = false, isExperimental = true, subTools = listOf(
            StudioTool("orto", Icons.Default.SquareFoot, "Ortho Mode", R.drawable.ic_tabler_orto, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
            StudioTool("mirror", Icons.Default.Compare, "Mirror", R.drawable.ic_tabler_mirror, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
            StudioTool("mover_pt_pt", Icons.Default.OpenWith, "Move Point to Point", R.drawable.ic_tabler_mover_pt_pt, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
            StudioTool("align_2_pt", Icons.Default.AlignHorizontalLeft, "Align 2 Points", R.drawable.ic_tabler_align_2_pt, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
            StudioTool("offset", Icons.Default.CopyAll, "Offset", R.drawable.ic_tabler_offset, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
            StudioTool("fillet", Icons.Default.RoundedCorner, "Fillet", R.drawable.ic_tabler_fillet, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
            StudioTool("chamfer", Icons.Default.Architecture, "Chamfer", R.drawable.ic_tabler_chamfer, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
            StudioTool("trim", Icons.Default.ContentCut, "Trim", R.drawable.ic_tabler_trim, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true),
            StudioTool("extend", Icons.Default.TrendingFlat, "Extend", R.drawable.ic_tabler_extend, isPlaceholder = false, parentGroupId = "edit_points", isExperimental = true)
        )),
        StudioTool("toggle_snap", Icons.Default.FilterCenterFocus, "Toggle Snap", R.drawable.ic_tabler_toggle_snap, isPlaceholder = false, isExperimental = true),
        StudioTool("undo", Icons.Default.Undo, "Undo", R.drawable.ic_tabler_undo, isPlaceholder = false),
        StudioTool("redo", Icons.Default.Redo, "Redo", R.drawable.ic_tabler_redo, isPlaceholder = false),
        // Selection tools
        StudioTool("tool_selection", Icons.Outlined.AllOut, "Selection", R.drawable.ic_tabler_tool_selection, isPlaceholder = false),
        StudioTool("tool_selection_freehand", Icons.Outlined.AllOut, "Lasso Selection", R.drawable.ic_tabler_tool_selection_freehand, isPlaceholder = false, parentGroupId = "tool_selection"),
        StudioTool("tool_selection_polygon", Icons.Outlined.Timeline, "Polygon Selection", R.drawable.ic_tabler_tool_selection_polygon, isPlaceholder = false, parentGroupId = "tool_selection"),
        StudioTool("tool_selection_rect", Icons.Outlined.Crop, "Rect Selection", R.drawable.ic_tabler_tool_selection_rect, isPlaceholder = false, parentGroupId = "tool_selection"),
        
        // Contextual Selection Actions
        StudioTool("context_deselect", Icons.Outlined.Deselect, "Deselect", R.drawable.ic_tabler_context_deselect, isPlaceholder = false, isContextual = true),
        StudioTool("context_transform", Icons.Outlined.OpenWith, "Transform", R.drawable.ic_tabler_context_transform, isPlaceholder = false, isContextual = true),
        StudioTool("context_copy", Icons.Outlined.ContentCopy, "Duplicate", R.drawable.ic_tabler_context_copy, isPlaceholder = false, isContextual = true),
        StudioTool("context_delete", Icons.Outlined.Delete, "Delete", R.drawable.ic_tabler_context_delete, isPlaceholder = false, isContextual = true),
        StudioTool("context_flip_horizontal", Icons.Outlined.Flip, "Flip Horizontal", R.drawable.ic_tabler_context_flip_horizontal, isPlaceholder = false, isContextual = true),
        StudioTool("context_flip_vertical", Icons.Outlined.Flip, "Flip Vertical", R.drawable.ic_tabler_context_flip_vertical, isPlaceholder = false, isContextual = true, parentGroupId = "context_flip_horizontal"),
        StudioTool("context_edit_image", Icons.Default.Edit, "Edit Image", R.drawable.ic_tabler_context_edit_image, isPlaceholder = false, isContextual = true),
        StudioTool("context_group", Icons.Outlined.GroupWork, "Grupo", R.drawable.ic_tabler_context_group, isPlaceholder = false, isContextual = true),
        StudioTool("context_component", Icons.Outlined.Widgets, "Componente", R.drawable.ic_tabler_context_component, isPlaceholder = false, isContextual = true, parentGroupId = "context_group"),
        StudioTool("context_ungroup", Icons.Outlined.LinkOff, "Desagrupar", R.drawable.ic_tabler_context_ungroup, isPlaceholder = false, isContextual = true, parentGroupId = "context_group"),
        StudioTool("context_make_unique", Icons.Outlined.AutoAwesome, "Hacer Único", R.drawable.ic_tabler_context_make_unique, isPlaceholder = false, isContextual = true, parentGroupId = "context_group"),
        StudioTool("context_edit", Icons.Outlined.Edit, "Editar", R.drawable.ic_tabler_context_edit, isPlaceholder = false, isContextual = true),
        StudioTool("context_lock_scale", Icons.Outlined.Lock, "Lock Scale", null, isPlaceholder = false, isContextual = true),
        
        StudioTool("action_copy", Icons.Outlined.ContentCopy, "Copiar", R.drawable.ic_tabler_action_copy, isPlaceholder = false),
        StudioTool("action_cut", Icons.Outlined.ContentCut, "Cortar", R.drawable.ic_tabler_action_cut, isPlaceholder = false),
        StudioTool("action_paste", Icons.Outlined.ContentPaste, "Pegar", R.drawable.ic_tabler_action_paste, isPlaceholder = false),
        StudioTool("grid_menu", Icons.Default.GridOn, "Grid Menu", R.drawable.ic_tabler_grid_menu, isPlaceholder = false),
        
        // Existing tools...
        StudioTool("layers", Icons.Default.Layers, "Layers", R.drawable.ic_tabler_layers, isPlaceholder = true),
        StudioTool("palette", Icons.Default.Palette, "Palette", R.drawable.ic_tabler_palette, isPlaceholder = true),
        StudioTool("opacity", Icons.Default.Opacity, "Opacity", R.drawable.ic_tabler_opacity, isPlaceholder = true),
        // StudioTool("settings", Icons.Default.Settings, "Settings", R.drawable.ic_tabler_settings, isPlaceholder = false),
        StudioTool("save", Icons.Default.Save, "Save", R.drawable.ic_tabler_save, isPlaceholder = true),
        StudioTool("play", Icons.Default.PlayArrow, "Play", R.drawable.ic_tabler_play, isPlaceholder = true),
        StudioTool("pause", Icons.Default.Pause, "Pause", R.drawable.ic_tabler_pause, isPlaceholder = true),
        StudioTool("zoom_in", Icons.Default.ZoomIn, "Zoom In", R.drawable.ic_tabler_zoom_in, isPlaceholder = false),
        StudioTool("zoom_out", Icons.Default.ZoomOut, "Zoom Out", R.drawable.ic_tabler_zoom_out, isPlaceholder = false),
        StudioTool("zoom_fit", Icons.Default.FitScreen, "Zoom Extends", R.drawable.ic_tabler_zoom_fit, isPlaceholder = false),
        StudioTool("home_view", Icons.Default.Home, "Reset View", R.drawable.ic_tabler_home_view, isPlaceholder = false),
        StudioTool("menu", Icons.Default.Menu, "Menu", R.drawable.ic_tabler_menu, isPlaceholder = false),
        StudioTool(StudioTool.PROPERTIES_TOOL_ID, Icons.Default.Tune, "Properties", isPlaceholder = false),
        StudioTool(StudioTool.STABILIZATION_TOOL_ID, Icons.Default.Timeline, "Stabilization", R.drawable.ic_tabler_stabilization, isPlaceholder = false),
        StudioTool(StudioTool.SIZE_OPACITY_TOOL_ID, Icons.Default.Lens, "Brush Settings", isPlaceholder = false),
        StudioTool("divider", Icons.Default.Remove, "Divider", R.drawable.ic_tabler_divider, isPlaceholder = false),
        StudioTool("edit", Icons.Default.Edit, "Edit", isPlaceholder = true),
        StudioTool("create", Icons.Default.Add, "Create", isPlaceholder = true),
        StudioTool("stroke_color", Icons.Default.BorderColor, "Stroke Color", R.drawable.ic_tabler_stroke_color, isPlaceholder = false),
        StudioTool("fill_color", Icons.Default.FormatColorFill, "Fill Color", R.drawable.ic_tabler_fill_color, isPlaceholder = false)
    )

    fun getToolById(id: String): StudioTool? = allTools.find { it.id == id }

    fun getSubToolsFor(registryId: String): List<StudioTool> {
        val tool = allTools.find { it.id == registryId } ?: return emptyList()
        val groupId = tool.parentGroupId ?: tool.id
        return allTools.filter { !it.isPlaceholder && (it.parentGroupId == groupId || it.id == groupId) && it.id != registryId }
    }
}

package com.sketcher.sketchercompanionv1.ui.model

import com.sketcher.sketchercompanionv1.data.ToolbarStateResult
import com.sketcher.sketchercompanionv1.ui.components.ToolPayload
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit

object ToolbarDefaultFactory {

    fun createDefaultAssignedTools(): Map<String, ToolPayload> {
        return mapOf(
            "default_pencil" to ToolPayload.PENCIL,
            "default_pen" to ToolPayload.PEN,
            "default_paint" to ToolPayload.PAINT,
            "default_watercolor" to ToolPayload.WATERCOLOR,
            "default_pluma" to ToolPayload.PLUMA,
            "eraser" to ToolPayload.ERASER,
            "stroke_color" to ToolPayload.STROKE_COLOR,
            "fill_color" to ToolPayload.FILL_COLOR
        )
    }

    fun createDefaultToolColors(getDefaultStrokeColor: () -> Int, getDefaultFillColor: () -> Int): Map<String, Int> {
        return mapOf(
            "stroke_color" to getDefaultStrokeColor(),
            "fill_color" to getDefaultFillColor()
        )
    }

    fun createDefaultToolbarState(): Map<ToolLocation, List<StudioTool>> {
        return mapOf(
            ToolLocation.LeftBar to listOf(),
            ToolLocation.RightBar to listOfNotNull(
                com.sketcher.sketchercompanionv1.ui.model.StudioTool(
                    id = "default_pencil",
                    icon = Icons.Default.Edit,
                    contentDescription = "Lápiz Básico",
                    iconResId = com.sketcher.sketchercompanionv1.R.drawable.ic_tabler_pencil,
                    isPlaceholder = false,
                    subTools = listOf(
                        com.sketcher.sketchercompanionv1.ui.model.StudioTool("default_pencil", Icons.Default.Edit, "Lápiz Básico", com.sketcher.sketchercompanionv1.R.drawable.ic_tabler_pencil, isPlaceholder = false, parentGroupId = "default_pencil"),
                        com.sketcher.sketchercompanionv1.ui.model.StudioTool("default_pen", Icons.Default.Edit, "Bolígrafo Sólido", com.sketcher.sketchercompanionv1.R.drawable.ic_tabler_pen, isPlaceholder = false, parentGroupId = "default_pencil"),
                        com.sketcher.sketchercompanionv1.ui.model.StudioTool("default_pluma", Icons.Default.Edit, "Pluma Caligráfica", com.sketcher.sketchercompanionv1.R.drawable.ic_tabler_pluma, isPlaceholder = false, parentGroupId = "default_pencil"),
                        com.sketcher.sketchercompanionv1.ui.model.StudioTool("default_paint", Icons.Default.Edit, "Pincel Acrílico", com.sketcher.sketchercompanionv1.R.drawable.ic_tabler_paint, isPlaceholder = false, parentGroupId = "default_pencil"),
                        com.sketcher.sketchercompanionv1.ui.model.StudioTool("default_watercolor", Icons.Default.Edit, "Acuarela Suave", com.sketcher.sketchercompanionv1.R.drawable.ic_tabler_watercolor, isPlaceholder = false, parentGroupId = "default_pencil")
                    )
                ),
                ToolRegistry.getToolById(StudioTool.SIZE_OPACITY_TOOL_ID),
                ToolRegistry.getToolById("stroke_freehand"),
                ToolRegistry.getToolById(StudioTool.STABILIZATION_TOOL_ID),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("stroke_color"),
                ToolRegistry.getToolById("fill_color"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("eraser"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("text")
            ),
            ToolLocation.TopBar to listOfNotNull(
                ToolRegistry.getToolById("zoom_fit"),
                ToolRegistry.getToolById("zoom_in"),
                ToolRegistry.getToolById("zoom_out"),
                ToolRegistry.getToolById("home_view")
            ),
            ToolLocation.BottomBar to listOfNotNull(
                ToolRegistry.getToolById("tool_selection"),
                ToolRegistry.getToolById("action_paste"),
                ToolRegistry.getToolById("grid_menu")
            ),
            ToolLocation.TopLeftCorner to listOfNotNull(
                ToolRegistry.getToolById("menu")
            ),
            ToolLocation.TopRightCorner to emptyList(),
            ToolLocation.BottomLeftCorner to listOfNotNull(
                ToolRegistry.getToolById("undo")
            ),
            ToolLocation.BottomRightCorner to listOfNotNull(
                ToolRegistry.getToolById("redo")
            )
        )
    }

    fun createDefaultContextualToolbar(): List<StudioTool> {
        return listOfNotNull(
            ToolRegistry.getToolById("context_edit"),
            ToolRegistry.getToolById("context_transform"),
            ToolRegistry.getToolById("context_lock_scale"),
            ToolRegistry.getToolById("context_copy"),
            ToolRegistry.getToolById("context_delete"),
            ToolRegistry.getToolById("context_deselect"),
            ToolRegistry.getToolById("context_flip_horizontal"),
            ToolRegistry.getToolById("context_flip_vertical"),
            ToolRegistry.getToolById("context_group"),
            ToolRegistry.getToolById("context_component"),
            ToolRegistry.getToolById("context_ungroup"),
            ToolRegistry.getToolById("context_make_unique")
        )
    }
}

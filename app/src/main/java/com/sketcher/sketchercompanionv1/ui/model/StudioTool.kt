package com.sketcher.sketchercompanionv1.ui.model

import androidx.compose.ui.graphics.vector.ImageVector

data class StudioTool(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String,
    val isActive: Boolean = false,
    val isPlaceholder: Boolean = false,
    val isContextual: Boolean = false,
    val parentGroupId: String? = null,
    val registryId: String = id,
    val subTools: List<StudioTool> = emptyList(),
    val onClick: () -> Unit = {}
) {
    companion object {
        const val PROPERTIES_TOOL_ID = "tool_properties_inspector"
        const val STABILIZATION_TOOL_ID = "quick_stabilization"
        const val SIZE_OPACITY_TOOL_ID = "tool_size_opacity"
    }
}

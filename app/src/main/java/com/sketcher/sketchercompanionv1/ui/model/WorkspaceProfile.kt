package com.sketcher.sketchercompanionv1.ui.model

import com.sketcher.sketchercompanionv1.data.ToolbarStateResult
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import java.util.UUID

import com.sketcher.sketchercompanionv1.ui.theme.toThemeJson
import com.sketcher.sketchercompanionv1.ui.theme.toDomain

data class WorkspaceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Custom Profile",
    val isDefault: Boolean = false,
    val isReadOnly: Boolean = false,
    val layout: ToolbarStateResult,
    val theme: UiThemeConfig
)

fun WorkspaceProfile.toWorkspaceProfileJson(gson: com.google.gson.Gson, toolbarRepository: com.sketcher.sketchercompanionv1.data.ToolbarRepository): com.sketcher.sketchercompanionv1.dto.WorkspaceProfileJson {
    val savedLayout = toolbarRepository.createSavedLayout(
        toolbarState = this.layout.tools,
        assignedMap = this.layout.assignedMap,
        toolColors = this.layout.toolColors,
        toolStabilization = this.layout.toolStabilization,
        toolOpacity = this.layout.toolOpacity,
        contextualToolbar = this.layout.contextualTools
    )
    return com.sketcher.sketchercompanionv1.dto.WorkspaceProfileJson(
        id = this.id,
        name = this.name,
        isDefault = this.isDefault,
        layoutJson = gson.toJson(savedLayout),
        theme = this.theme.toThemeJson()
    )
}

fun com.sketcher.sketchercompanionv1.dto.WorkspaceProfileJson.toWorkspaceProfile(gson: com.google.gson.Gson, toolbarRepository: com.sketcher.sketchercompanionv1.data.ToolbarRepository): WorkspaceProfile {
    val parsedLayout = toolbarRepository.parseLayoutJson(this.layoutJson)
    return WorkspaceProfile(
        id = this.id,
        name = this.name,
        isDefault = this.isDefault,
        isReadOnly = false,
        layout = parsedLayout ?: com.sketcher.sketchercompanionv1.data.ToolbarStateResult(
            tools = emptyMap(),
            assignedMap = emptyMap(),
            toolColors = emptyMap(),
            contextualTools = emptyList(),
            toolStabilization = emptyMap(),
            toolOpacity = emptyMap()
        ),
        theme = this.theme.toDomain()
    )
}

package com.sketcher.sketchercompanionv1.dto

import com.sketcher.sketchercompanionv1.ComponentDefinition


data class ProjectData(
    val id: String,
    val layers: List<LayerJson>,
    val backgroundConfig: BackgroundConfig,
    val paletteColors: List<Int>,
    val toolConfigs: Map<ToolType, ToolConfig>,
    val canvasMetadata: CanvasMetadata,
    val componentLibrary: Map<String, ComponentDefinitionJson> = emptyMap(),
    val canvasSizeConfig: CanvasSizeConfig? = null, // null = infinite canvas
    val workspaceProfile: WorkspaceProfileJson? = null,
    val pages: List<PageJson>? = null,
    val activePageIndex: Int = 0
)

data class PageJson(
    val id: String,
    val name: String,
    val layers: List<LayerJson>,
    val backgroundConfig: BackgroundConfig,
    val canvasMetadata: CanvasMetadata,
    val canvasSizeConfig: CanvasSizeConfig? = null
)

// data class LayerData ... (Removing Custom LayerData, utilizing LayerJson)


data class BackgroundConfig(
    val color: Int,
    val gridConfig: GridConfig?,
    val fillStyle: FillStyleJson? = null
)

data class CanvasMetadata(
    val width: Float,
    val height: Float,
    val cameraMatrix: List<Float>,
    val scaleConfig: ScaleConfig?
)


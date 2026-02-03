package com.skecher.sketchercompanionv1.dto

import com.skecher.sketchercompanionv1.ToolType
import com.skecher.sketchercompanionv1.ToolConfig

import com.skecher.sketchercompanionv1.dto.LayerJson
import com.skecher.sketchercompanionv1.dto.GridConfig
import com.skecher.sketchercompanionv1.dto.ScaleConfig
import com.skecher.sketchercompanionv1.ComponentDefinition

data class ProjectData(
    val id: String,
    val layers: List<LayerJson>,
    val backgroundConfig: BackgroundConfig,
    val paletteColors: List<Int>,
    val toolConfigs: Map<ToolType, ToolConfig>,
    val canvasMetadata: CanvasMetadata,
    val componentLibrary: Map<String, ComponentDefinitionJson> = emptyMap()
)

// data class LayerData ... (Removing Custom LayerData, utilizing LayerJson)


data class BackgroundConfig(
    val color: Int,
    val gridConfig: GridConfig?
)

data class CanvasMetadata(
    val width: Float,
    val height: Float,
    val cameraMatrix: List<Float>,
    val scaleConfig: ScaleConfig?
)

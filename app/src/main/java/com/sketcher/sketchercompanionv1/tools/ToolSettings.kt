package com.sketcher.sketchercompanionv1.tools

import com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode

/**
 * Interface representing the independent settings of any drawing tool.
 */
interface ToolSettings {
    val size: Float
    val opacity: Float
    val thinning: Float
    val velocityThinning: Float
}

/**
 * Settings for the Pencil (Lápiz) tool.
 */
data class PencilSettings(
    override val size: Float = 9f,
    override val opacity: Float = 1f,
    override val thinning: Float = 0.5f,
    val smoothing: Float = 0.5f,
    val streamline: Float = 0.5f,
    val simulatePressure: Boolean = false,
    val taperStart: Float = 0.0f,
    val taperEnd: Float = 0.0f,
    val capStart: Boolean = true,
    val capEnd: Boolean = true,
    val isComplete: Boolean = false,
    val predictionLatency: Long = 15L,
    val simplificationTolerance: Float = 0.0f,
    override val velocityThinning: Float = 0.0f,
    val velocityMaxInput: Float = 1.0f,
    val useCurveForPolygon: Boolean = true,
    val isSimplificationEnabled: Boolean = false,
    val minWidthRatio: Float = 0.1f,
    val isCumulativeOpacity: Boolean = false
) : ToolSettings

/**
 * Settings for the Pen (Bolígrafo) tool.
 */
data class PenSettings(
    override val size: Float = 2f,
    override val opacity: Float = 1f,
    override val thinning: Float = 0f,
    override val velocityThinning: Float = 0f,
    val smoothing: Float = 0f
) : ToolSettings

/**
 * Settings for the Pluma (Caligrafía) tool.
 */
data class PlumaSettings(
    override val size: Float = 2.5f,
    override val opacity: Float = 1f,
    override val thinning: Float = 0.3f,
    override val velocityThinning: Float = 0.0f,
    val smoothing: Float = 0.5f,
    val taperStart: Float = 0.0f,
    val taperEnd: Float = 0.0f,
    val capStart: Boolean = true,
    val capEnd: Boolean = true,
    val useCurveForPolygon: Boolean = true,
    val simplificationTolerance: Float = 0.0f,
    val isSimplificationEnabled: Boolean = false,
    val minWidthRatio: Float = 0.1f
) : ToolSettings

/**
 * Settings for the Paint (Pintura) tool.
 */
data class PaintSettings(
    override val size: Float = 10f,
    override val opacity: Float = 1f,
    override val thinning: Float = 0.5f,
    override val velocityThinning: Float = 0.0f,
    val smoothing: Float = 0.5f,
    val paintOutlineWidth: Float = 2.0f,
    val paintJoinPrevious: Boolean = true
) : ToolSettings

/**
 * Settings for the Watercolor (Acuarela) tool.
 */
data class WatercolorSettings(
    override val size: Float = 20f,
    override val opacity: Float = 0.4f,
    override val thinning: Float = 0.5f,
    override val velocityThinning: Float = 0.0f,
    val smoothing: Float = 0.5f,
    val paintOutlineWidth: Float = 2.5f,
    val watercolorJitterSegment: Float = 12.0f,
    val watercolorJitterDeviation: Float = 3.5f,
    val watercolorBlurRadius: Float = 5.0f,
    val watercolorEdgeMode: WatercolorEdgeMode = WatercolorEdgeMode.BOTH,
    val watercolorCenterOpacity: Float = 0.8f,
    val watercolorEdgeRingOpacity: Float = 1.0f,
    val watercolorEdgeRingWidth: Float = 2.0f,
    val paintJoinPrevious: Boolean = true
) : ToolSettings

fun com.sketcher.sketchercompanionv1.dto.FreehandSettings.toToolSettings(toolType: com.sketcher.sketchercompanionv1.dto.ToolType): ToolSettings {
    return when (toolType) {
        com.sketcher.sketchercompanionv1.dto.ToolType.FREEHAND -> PencilSettings(
            size = 9f,
            opacity = 1f,
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            velocityMaxInput = this.velocityMaxInput,
            smoothing = this.smoothing,
            minWidthRatio = this.minWidthRatio,
            simulatePressure = this.simulatePressure,
            taperStart = this.taperStart,
            taperEnd = this.taperEnd,
            capStart = this.capStart,
            capEnd = this.capEnd,
            useCurveForPolygon = this.useCurveForPolygon,
            simplificationTolerance = this.simplificationTolerance,
            isSimplificationEnabled = this.isSimplificationEnabled,
            predictionLatency = this.predictionLatency,
            isCumulativeOpacity = this.isCumulativeOpacity
        )
        com.sketcher.sketchercompanionv1.dto.ToolType.PENCIL_CUMULATIVE -> PencilSettings(
            size = 9f,
            opacity = 1f,
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            velocityMaxInput = this.velocityMaxInput,
            smoothing = this.smoothing,
            minWidthRatio = this.minWidthRatio,
            simulatePressure = this.simulatePressure,
            taperStart = this.taperStart,
            taperEnd = this.taperEnd,
            capStart = this.capStart,
            capEnd = this.capEnd,
            useCurveForPolygon = this.useCurveForPolygon,
            simplificationTolerance = this.simplificationTolerance,
            isSimplificationEnabled = this.isSimplificationEnabled,
            predictionLatency = this.predictionLatency,
            isCumulativeOpacity = true
        )
        com.sketcher.sketchercompanionv1.dto.ToolType.PEN -> PenSettings(
            size = 3.5f,
            opacity = 1f,
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            smoothing = this.smoothing
        )
        com.sketcher.sketchercompanionv1.dto.ToolType.PLUMA -> PlumaSettings(
            size = 2.5f,
            opacity = 1f,
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            smoothing = this.smoothing,
            taperStart = this.taperStart,
            taperEnd = this.taperEnd,
            capStart = this.capStart,
            capEnd = this.capEnd,
            useCurveForPolygon = this.useCurveForPolygon,
            simplificationTolerance = this.simplificationTolerance,
            isSimplificationEnabled = this.isSimplificationEnabled,
            minWidthRatio = this.minWidthRatio
        )
        com.sketcher.sketchercompanionv1.dto.ToolType.PAINT -> PaintSettings(
            size = 10f,
            opacity = 1f,
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            smoothing = this.smoothing,
            paintOutlineWidth = this.paintOutlineWidth,
            paintJoinPrevious = this.paintJoinPrevious
        )
        com.sketcher.sketchercompanionv1.dto.ToolType.WATERCOLOR -> WatercolorSettings(
            size = 20f,
            opacity = 0.4f,
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            smoothing = this.smoothing,
            paintOutlineWidth = this.paintOutlineWidth,
            watercolorJitterSegment = this.watercolorJitterSegment,
            watercolorJitterDeviation = this.watercolorJitterDeviation,
            watercolorBlurRadius = this.watercolorBlurRadius,
            watercolorEdgeMode = this.watercolorEdgeMode,
            watercolorCenterOpacity = this.watercolorCenterOpacity,
            watercolorEdgeRingOpacity = this.watercolorEdgeRingOpacity,
            watercolorEdgeRingWidth = this.watercolorEdgeRingWidth,
            paintJoinPrevious = this.paintJoinPrevious
        )
        else -> PencilSettings()
    }
}

fun ToolSettings.toFreehandSettings(toolType: com.sketcher.sketchercompanionv1.dto.ToolType): com.sketcher.sketchercompanionv1.dto.FreehandSettings {
    val base = com.sketcher.sketchercompanionv1.dto.FreehandSettings()
    return when (this) {
        is PencilSettings -> base.copy(
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            velocityMaxInput = this.velocityMaxInput,
            smoothing = this.smoothing,
            minWidthRatio = this.minWidthRatio,
            simulatePressure = this.simulatePressure,
            taperStart = this.taperStart,
            taperEnd = this.taperEnd,
            capStart = this.capStart,
            capEnd = this.capEnd,
            useCurveForPolygon = this.useCurveForPolygon,
            simplificationTolerance = this.simplificationTolerance,
            isSimplificationEnabled = this.isSimplificationEnabled,
            predictionLatency = this.predictionLatency,
            isCumulativeOpacity = this.isCumulativeOpacity
        )
        is PenSettings -> base.copy(
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            smoothing = this.smoothing
        )
        is PlumaSettings -> base.copy(
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            smoothing = this.smoothing,
            taperStart = this.taperStart,
            taperEnd = this.taperEnd,
            capStart = this.capStart,
            capEnd = this.capEnd,
            useCurveForPolygon = this.useCurveForPolygon
        )
        is PaintSettings -> base.copy(
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            smoothing = this.smoothing,
            paintOutlineWidth = this.paintOutlineWidth,
            paintJoinPrevious = this.paintJoinPrevious
        )
        is WatercolorSettings -> base.copy(
            thinning = this.thinning,
            velocityThinning = this.velocityThinning,
            smoothing = this.smoothing,
            paintOutlineWidth = this.paintOutlineWidth,
            watercolorJitterSegment = this.watercolorJitterSegment,
            watercolorJitterDeviation = this.watercolorJitterDeviation,
            watercolorBlurRadius = this.watercolorBlurRadius,
            watercolorEdgeMode = this.watercolorEdgeMode,
            watercolorCenterOpacity = this.watercolorCenterOpacity,
            watercolorEdgeRingOpacity = this.watercolorEdgeRingOpacity,
            watercolorEdgeRingWidth = this.watercolorEdgeRingWidth,
            paintJoinPrevious = this.paintJoinPrevious
        )
        else -> base
    }
}

package com.sketcher.sketchercompanionv1.tools

import android.graphics.Path
import com.sketcher.sketchercompanionv1.PerfectFreehandGenerator
import com.sketcher.sketchercompanionv1.StrokePoint
import com.sketcher.sketchercompanionv1.dto.FreehandSettings

/**
 * Interface representing a modular brush tool.
 */
interface BrushTool {
    val toolId: String
    val templateId: String
    val displayName: String
    var settings: ToolSettings

    /**
     * Converts specific settings to the intermediate [FreehandSettings] 
     * required by the Perfect Freehand math engine.
     */
    fun toFreehandSettings(zoom: Float): FreehandSettings

    /**
     * Processes raw touch points to generate the stroke path/mesh.
     */
    fun processPoints(
        rawPoints: List<StrokePoint>,
        zoom: Float,
        outPath: Path = Path()
    ): PerfectFreehandGenerator.FreehandResult {
        val fs = toFreehandSettings(zoom)
        return PerfectFreehandGenerator.generate(rawPoints, fs, zoom, outPath)
    }

    /**
     * Returns the polymorphic renderer associated with this tool.
     */
    fun getRenderer(): BrushRenderer
}

/**
 * Pencil (Lápiz) tool.
 */
class PencilTool(
    override val toolId: String = "pencil",
    override val displayName: String = "Pencil",
    override var settings: ToolSettings = PencilSettings()
) : BrushTool {
    override val templateId: String = "pencil"

    override fun toFreehandSettings(zoom: Float): FreehandSettings {
        val s = settings as PencilSettings
        return FreehandSettings(
            size = s.size,
            thinning = s.thinning,
            smoothing = s.smoothing,
            streamline = s.streamline,
            simulatePressure = s.simulatePressure,
            taperStart = s.taperStart,
            taperEnd = s.taperEnd,
            capStart = s.capStart,
            capEnd = s.capEnd,
            isComplete = s.isComplete,
            predictionLatency = s.predictionLatency,
            simplificationTolerance = s.simplificationTolerance,
            velocityThinning = s.velocityThinning,
            velocityMaxInput = s.velocityMaxInput,
            useCurveForPolygon = s.useCurveForPolygon,
            isSimplificationEnabled = s.isSimplificationEnabled,
            minWidthRatio = s.minWidthRatio,
            isCumulativeOpacity = s.isCumulativeOpacity
        )
    }

    override fun getRenderer(): BrushRenderer = MeshBrushRenderer()
}

/**
 * Pen (Bolígrafo) tool.
 */
class PenTool(
    override val toolId: String = "pen",
    override val displayName: String = "Pen",
    override var settings: ToolSettings = PenSettings()
) : BrushTool {
    override val templateId: String = "pen"

    override fun toFreehandSettings(zoom: Float): FreehandSettings {
        val s = settings as PenSettings
        return FreehandSettings(
            size = s.size,
            thinning = s.thinning,
            smoothing = s.smoothing,
            streamline = 0f,
            simulatePressure = false,
            taperStart = 0f,
            taperEnd = 0f,
            capStart = true,
            capEnd = true,
            isComplete = false,
            useCurveForPolygon = true,
            isSimplificationEnabled = false,
            velocityThinning = s.velocityThinning
        )
    }

    override fun getRenderer(): BrushRenderer = MeshBrushRenderer()
}

/**
 * Pluma (Caligrafía) tool.
 */
class PlumaTool(
    override val toolId: String = "pluma",
    override val displayName: String = "Pluma",
    override var settings: ToolSettings = PlumaSettings()
) : BrushTool {
    override val templateId: String = "pluma"

    override fun toFreehandSettings(zoom: Float): FreehandSettings {
        val s = settings as PlumaSettings
        return FreehandSettings(
            size = s.size,
            thinning = s.thinning,
            smoothing = s.smoothing,
            streamline = s.smoothing * 0.8f,
            simulatePressure = s.simulatePressure,
            taperStart = s.taperStart,
            taperEnd = s.taperEnd,
            capStart = s.capStart,
            capEnd = s.capEnd,
            isComplete = false,
            useCurveForPolygon = s.useCurveForPolygon,
            simplificationTolerance = s.simplificationTolerance,
            isSimplificationEnabled = s.isSimplificationEnabled,
            minWidthRatio = s.minWidthRatio,
            velocityThinning = s.velocityThinning
        )
    }

    override fun getRenderer(): BrushRenderer = MeshBrushRenderer()
}

/**
 * Paint (Pintura) tool.
 */
class PaintTool(
    override val toolId: String = "paint",
    override val displayName: String = "Paint",
    override var settings: ToolSettings = PaintSettings()
) : BrushTool {
    override val templateId: String = "paint"

    override fun toFreehandSettings(zoom: Float): FreehandSettings {
        val s = settings as PaintSettings
        return FreehandSettings(
            size = s.size,
            thinning = s.thinning,
            smoothing = s.smoothing,
            streamline = s.smoothing * 0.8f,
            simulatePressure = false,
            capStart = false,
            capEnd = false,
            paintOutlineWidth = s.paintOutlineWidth,
            paintJoinPrevious = s.paintJoinPrevious,
            velocityThinning = s.velocityThinning
        )
    }

    override fun getRenderer(): BrushRenderer = OutlineBrushRenderer()
}

/**
 * Watercolor (Acuarela) tool.
 */
class WatercolorTool(
    override val toolId: String = "watercolor",
    override val displayName: String = "Watercolor",
    override var settings: ToolSettings = WatercolorSettings()
) : BrushTool {
    override val templateId: String = "watercolor"

    override fun toFreehandSettings(zoom: Float): FreehandSettings {
        val s = settings as WatercolorSettings
        return FreehandSettings(
            size = s.size,
            thinning = s.thinning,
            smoothing = s.smoothing,
            streamline = s.smoothing * 0.8f,
            simulatePressure = false,
            capStart = false,
            capEnd = false,
            paintOutlineWidth = s.paintOutlineWidth,
            watercolorJitterSegment = s.watercolorJitterSegment,
            watercolorJitterDeviation = s.watercolorJitterDeviation,
            watercolorBlurRadius = s.watercolorBlurRadius,
            watercolorEdgeMode = s.watercolorEdgeMode,
            watercolorCenterOpacity = s.watercolorCenterOpacity,
            watercolorEdgeRingOpacity = s.watercolorEdgeRingOpacity,
            watercolorEdgeRingWidth = s.watercolorEdgeRingWidth,
            paintJoinPrevious = s.paintJoinPrevious,
            velocityThinning = s.velocityThinning
        )
    }

    override fun getRenderer(): BrushRenderer = WatercolorBrushRenderer()
}

/**
 * Cumulative Pencil (Lápiz Acumulativo) tool.
 */
class PencilCumulativeTool(
    override val toolId: String = "pencil_cumulative",
    override val displayName: String = "Pencil Cumulative",
    override var settings: ToolSettings = PencilSettings(isCumulativeOpacity = true)
) : BrushTool {
    override val templateId: String = "pencil_cumulative"

    override fun toFreehandSettings(zoom: Float): FreehandSettings {
        val s = settings as PencilSettings
        return FreehandSettings(
            size = s.size,
            thinning = s.thinning,
            smoothing = s.smoothing,
            streamline = s.streamline,
            simulatePressure = s.simulatePressure,
            taperStart = s.taperStart,
            taperEnd = s.taperEnd,
            capStart = s.capStart,
            capEnd = s.capEnd,
            isComplete = s.isComplete,
            predictionLatency = s.predictionLatency,
            simplificationTolerance = s.simplificationTolerance,
            velocityThinning = s.velocityThinning,
            velocityMaxInput = s.velocityMaxInput,
            useCurveForPolygon = s.useCurveForPolygon,
            isSimplificationEnabled = s.isSimplificationEnabled,
            minWidthRatio = s.minWidthRatio,
            isCumulativeOpacity = true
        )
    }

    override fun getRenderer(): BrushRenderer = MeshBrushRenderer()
}

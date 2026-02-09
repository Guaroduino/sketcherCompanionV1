package com.skecher.sketchercompanionv1.dto

/**
 * Main project Data Transfer Object.
 * Serializes the entire state of the canvas including layers, camera, and dimensions.
 */
data class ProjectJson(
    val version: Int = 2, // Bump version
    val canvasWidth: Float,
    val canvasHeight: Float,
    val cameraMatrix: List<Float>,
    val layers: List<LayerJson>,
    val backgroundColor: Int = android.graphics.Color.WHITE,
    val scaleConfig: ScaleConfig? = ScaleConfig(), // Default if missing
    val gridConfig: GridConfig? = GridConfig(),
    val canvasSizeConfig: CanvasSizeConfigJson? = null // null = infinite canvas
)

data class ScaleConfig(
    val unitName: String = "mm",
    val basePixelsPerMillimeter: Float = 5.0f // Manual calibration
)

data class GridConfig(
    val isVisible: Boolean = false,
    val spacing: Float = 1.0f, // In project units
    val color: Int = android.graphics.Color.parseColor("#44888888"), // Light Gray semi-transparent defaults
    val secondaryColor: Int = android.graphics.Color.parseColor("#22888888"), // Fainter
    val tertiaryColor: Int = android.graphics.Color.parseColor("#11888888")  // Faintest
)

enum class DistanceUnit(val symbol: String, val toMillimeters: Float) {
    MM("mm", 1.0f),
    CM("cm", 10.0f),
    M("m", 1000.0f);

    companion object {
        fun fromSymbol(symbol: String): DistanceUnit = entries.find { it.symbol == symbol } ?: MM
    }
}

data class LayerJson(
    val id: String,
    val name: String,
    val isVisible: Boolean,
    val opacity: Float,
    val elements: List<LayerElementJson>
)

data class LayerElementJson(
    val type: String, // "INK", "VECTOR", "FILL", "IMAGE", "SVG", "GROUP"
    val inkStroke: StrokeJson? = null,
    val vectorStroke: VectorStrokeJson? = null,
    val fill: FillJson? = null,
    val image: ImageElementJson? = null,
    val svg: SvgElementJson? = null,
    val group: GroupElementJson? = null,
    val componentInstance: ComponentInstanceJson? = null
)

data class ComponentInstanceJson(
    val id: String,
    val definitionId: String,
    val matrixValues: List<Float>
)

data class GroupElementJson(
    val id: String,
    val elements: List<LayerElementJson>,
    val matrixValues: List<Float>
)

data class ComponentDefinitionJson(
    val id: String,
    val elements: List<LayerElementJson>
)

data class SvgElementJson(
    val fileName: String,
    val id: String,
    val matrixValues: List<Float>
)

data class ImageElementJson(
    val fileName: String,
    val matrixValues: List<Float>
)

data class VectorStrokeJson(
    val points: List<StrokePointJson>,
    val color: Int,
    val maxWidth: Float,
    val brushType: String = "FREEHAND",
    val strokeType: StrokeType = StrokeType.FREEHAND
)

data class StrokePointJson(
    val x: Float,
    val y: Float,
    val pressure: Float,
    val timestamp: Long = 0L
)

data class StrokeJson(
    val brushFamily: String? = null, // e.g. "marker", "pen"
    val brushColor: Long,       // ARGB Long
    val brushSize: Float,
    val brushEpsilon: Float,
    val inputs: List<StrokeInputJson>,
    val matrixValues: List<Float>? = null // Local transform for isolation mode support
)

data class StrokeInputJson(
    val x: Float,
    val y: Float,
    val time: Long,
    val pressure: Float,
    val tilt: Float,
    val orientation: Float
)

data class FillJson(
    val color: Int,
    // Store as list of commands to support curves and moves
    val commands: List<PathCommandJson>
)

data class PathCommandJson(
    val type: String, // "MOVE", "LINE", "QUAD", "CUBIC", "CLOSE"
    val params: List<Float> // Coordinates
)

data class FreehandSettings(
    // New Params matching Perfect Freehand
    val thinning: Float = 0.5f,
    val velocityThinning: Float = 0.0f, // New: Thinning based on speed
    val velocityMaxInput: Float = 1.0f, // New: Speed sensitivity (px/ms)
    val smoothing: Float = 0.5f,
    val streamline: Float = 0.5f,
    val simulatePressure: Boolean = true,
    
    // Tapering (Lengths) - Negative values will simulate widening
    val taperStart: Float = 0.0f,
    val taperEnd: Float = 0.0f,
    
    // Tapering (Intensity/Limits)
    val taperStartTipRatio: Float = 0.0f, // 0 = sharp, 1 = no taper
    val taperEndTipRatio: Float = 0.0f,
    val wideningStartRatio: Float = 2.0f, // Multiplier for thickness if taper < 0
    val wideningEndRatio: Float = 2.0f,
    
    // Caps
    val capStart: Boolean = true, 
    val capEnd: Boolean = true,
    val useCurveForPolygon: Boolean = false, // New Toggle: Flat (false) or Curved (true) polygon

    // Legacy / Other
    val predictionLatency: Float = 20.0f,
    val tolerance: Float = 1.0f,
    val isSimplificationEnabled: Boolean = true,
    val minPredictionVelocity: Float = 0.5f,
    val maxPredictionVelocity: Float = 3.5f,
    val minWidthRatio: Float = 0.1f, // Kept for compat/UI
    val useSplines: Boolean = true, // Kept for generator logic
    val isMonoline: Boolean = false, // Kept if needed later

    // Deprecating old influence fields
    val pressureInfluence: Float = 0.0f, // Deprecated
    val velocityInfluence: Float = 0.0f  // Deprecated
)

enum class ToolType { FREEHAND, FILL, ERASER, SELECTION, ANDROID_INK }

enum class StrokeType { FREEHAND, LINE, POLYLINE, CIRCLE, ARC }

data class FillSettings(val tolerance: Float = 0.1f)

data class ToolConfig(
    val size: Float = 9f,
    val opacity: Float = 1f,
    val freehandSettings: FreehandSettings = FreehandSettings(),
    val fillSettings: FillSettings = FillSettings(),
    // Global Input Settings
    val isFingerMode: Boolean = false,
    val fingerOffsetX: Float = 0f,
    val fingerOffsetY: Float = 50f
)

/**
 * JSON representation of canvas size configuration.
 */
data class CanvasSizeConfigJson(
    val widthInPixels: Float,
    val heightInPixels: Float,
    val presetName: String? = null, // Name of PaperSizePreset enum, null if custom
    val orientation: String = "PORTRAIT" // "PORTRAIT" or "LANDSCAPE"
)

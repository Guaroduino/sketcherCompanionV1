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
    val gridConfig: GridConfig? = GridConfig()
)

data class ScaleConfig(
    val unitName: String = "m",
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
    val group: GroupElementJson? = null
)

data class GroupElementJson(
    val id: String,
    val elements: List<LayerElementJson>,
    val matrixValues: List<Float>
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
    val maxWidth: Float
)

data class StrokePointJson(
    val x: Float,
    val y: Float,
    val pressure: Float
)

data class StrokeJson(
    val brushFamily: String? = null, // e.g. "marker", "pen"
    val brushColor: Long,       // ARGB Long
    val brushSize: Float,
    val brushEpsilon: Float,
    val inputs: List<StrokeInputJson>
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

package com.sketcher.sketchercompanionv1.dto

/**
 * Represents a canvas size configuration.
 * If null, the canvas is infinite (default behavior).
 */
data class CanvasSizeConfig(
    val widthInPixels: Float,
    val heightInPixels: Float,
    val preset: PaperSizePreset? = null, // null if custom
    val orientation: PaperOrientation = PaperOrientation.PORTRAIT
)

enum class PaperOrientation {
    PORTRAIT,
    LANDSCAPE
}

/**
 * Predefined paper size presets with dimensions in millimeters.
 */
enum class PaperSizePreset(
    val displayName: String,
    val widthMm: Float,
    val heightMm: Float
) {
    // North American sizes
    LETTER("Carta (Letter)", 215.9f, 279.4f),
    LEGAL("Oficio (Legal)", 215.9f, 355.6f),
    TABLOID("Tabloide", 279.4f, 431.8f),
    
    // ISO A series
    A4("A4", 210f, 297f),
    A3("A3", 297f, 420f),
    A5("A5", 148f, 210f),
    
    // ISO B series
    B4("B4", 250f, 353f),
    B5("B5", 176f, 250f),
    
    // Digital
    ICON_100("Icono (100px)", 20f, 20f);

    /**
     * Get dimensions in pixels based on Pixels Per Millimeter.
     * @param pixelsPerMm The project's scale factor (default 5.0 for screen).
     * @param orientation Portrait or Landscape
     */
    fun getPixelDimensions(
        pixelsPerMm: Float = 5.0f,
        orientation: PaperOrientation = PaperOrientation.PORTRAIT
    ): Pair<Float, Float> {
        val widthPx = widthMm * pixelsPerMm
        val heightPx = heightMm * pixelsPerMm
        
        return when (orientation) {
            PaperOrientation.PORTRAIT -> Pair(widthPx, heightPx)
            PaperOrientation.LANDSCAPE -> Pair(heightPx, widthPx)
        }
    }

    /**
     * Get dimensions in project units (mm, cm, etc.)
     */
    fun getUnitDimensions(
        unit: DistanceUnit = DistanceUnit.MM,
        orientation: PaperOrientation = PaperOrientation.PORTRAIT
    ): Pair<Float, Float> {
        val widthInUnit = widthMm / unit.toMillimeters
        val heightInUnit = heightMm / unit.toMillimeters
        
        return when (orientation) {
            PaperOrientation.PORTRAIT -> Pair(widthInUnit, heightInUnit)
            PaperOrientation.LANDSCAPE -> Pair(heightInUnit, widthInUnit)
        }
    }
}

/**
 * Helper object for creating canvas size configurations.
 */
object CanvasSizeHelper {
    /**
     * Create a canvas size from a preset.
     */
    fun fromPreset(
        preset: PaperSizePreset,
        orientation: PaperOrientation = PaperOrientation.PORTRAIT,
        pixelsPerMm: Float = 5.0f
    ): CanvasSizeConfig {
        val (width, height) = preset.getPixelDimensions(pixelsPerMm, orientation)
        return CanvasSizeConfig(
            widthInPixels = width,
            heightInPixels = height,
            preset = preset,
            orientation = orientation
        )
    }

    /**
     * Create a custom canvas size in pixels.
     */
    fun fromPixels(
        widthPx: Float,
        heightPx: Float
    ): CanvasSizeConfig {
        return CanvasSizeConfig(
            widthInPixels = widthPx,
            heightInPixels = heightPx,
            preset = null,
            orientation = PaperOrientation.PORTRAIT
        )
    }

    /**
     * Create a custom canvas size from real-world units.
     */
    fun fromUnits(
        width: Float,
        height: Float,
        unit: DistanceUnit,
        pixelsPerMm: Float
    ): CanvasSizeConfig {
        val widthMm = width * unit.toMillimeters
        val heightMm = height * unit.toMillimeters
        val widthPx = widthMm * pixelsPerMm
        val heightPx = heightMm * pixelsPerMm
        
        return CanvasSizeConfig(
            widthInPixels = widthPx,
            heightInPixels = heightPx,
            preset = null,
            orientation = PaperOrientation.PORTRAIT
        )
    }
}


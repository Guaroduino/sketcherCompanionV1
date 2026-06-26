package com.sketcher.sketchercompanionv1.utils

import com.sketcher.sketchercompanionv1.dto.DistanceUnit
import kotlin.math.pow

/**
 * Master utility for coordinate and unit conversions.
 * Ensures strictly consistent logic across Grid, Scale Indicator, and other tools.
 */
object UnitUtils {

    /**
     * Converts a value in Project Units (e.g., 1.5 Meters) to Screen Pixels.
     * 
     * Formula:
     * 1. Project Units -> Millimeters (Real Size)
     * 2. Millimeters / ScaleRatio = Physical Paper Millimeters
     * 3. Physical Paper Mm * BasePixelsPerMm = Screen Pixels
     *
     * @param value The value in project units (e.g., 1.0 for 1 Meter)
     * @param unit The unit of the value (e.g., DistanceUnit.M)
     * @param scaleRatio The scale ratio (e.g., 100.0 for 1:100)
     * @param basePxPerMm The manual base resolution (e.g., 5.0 px/mm)
     */
    /**
     * Converts a value in Project Units (e.g., 1.5 Meters) to Screen Pixels.
     * 
     * Formula:
     * 1. Project Units -> Millimeters (Real Size)
     * 2. Millimeters * BasePixelsPerMm = Screen Pixels (1:1 Scale)
     *
     * @param value The value in project units (e.g., 1.0 for 1 Meter)
     * @param unit The unit of the value (e.g., DistanceUnit.M)
     * @param basePxPerMm The manual base resolution (e.g., 5.0 px/mm)
     */
    /**
     * Converts a value in Project Units (e.g., 1.5 Meters) to Screen Pixels.
     * 
     * Formula:
     * 1. Project Units -> Millimeters (Real Size)
     * 2. Millimeters * BasePixelsPerMm = Screen Pixels (1:1 Scale)
     *
     * @param value The value in project units (e.g., 1.0 for 1 Meter)
     * @param unit The unit of the value (e.g., DistanceUnit.M)
     * @param basePxPerMm The manual base resolution (e.g., 5.0 px/mm)
     */
    fun projectUnitsToPixels(
        value: Float,
        unit: DistanceUnit,
        basePxPerMm: Float
    ): Float {
        // 1. Convert to Real World Millimeters
        val realMm = value * unit.toMillimeters
        
        // 2. Convert to Screen Pixels using Base Resolution (Direct 1:1 mapping)
        return realMm * basePxPerMm
    }

    /**
     * Converts Screen Pixels to Project Units.
     * Reverse of [projectUnitsToPixels].
     */
    fun pixelsToProjectUnits(
        pixels: Float,
        unit: DistanceUnit,
        basePxPerMm: Float
    ): Float {
        val safeBasePx = if (basePxPerMm == 0f) 5f else basePxPerMm // Avoid div/0
        
        // 1. Convert Pixels to Physical Millimeters
        val physicalMm = pixels / safeBasePx
        
        // 2. Convert to Target Unit
        return physicalMm / unit.toMillimeters
    }

    /**
     * Snap a value to the nearest "readable" interval.
     * Intervals: 1, 2, 5, 10, 20, 50, 0.1, 0.2, 0.5, etc.
     */
    fun getClosestNiceNumber(value: Float): Float {
        if (value <= 0) return 1f
        
        // Find magnitude (power of 10)
        val magnitude = 10.0.pow(kotlin.math.floor(kotlin.math.log10(value.toDouble()))).toFloat()
        val normalized = value / magnitude
        
        // Round to 1, 2, 5
        val niceNormalized = when {
            normalized >= 5 -> 5f
            normalized >= 2 -> 2f
            else -> 1f
        }
        
        return niceNormalized * magnitude
    }

    /**
     * Gets the screen density in physical pixels per millimeter.
     */
    fun getScreenPxPerMm(context: android.content.Context): Float {
        val dm = context.resources.displayMetrics
        val xdpi = dm.xdpi
        val dpi = if (xdpi > 50f && xdpi < 1000f) xdpi else dm.densityDpi.toFloat()
        return dpi / 25.4f
    }
}



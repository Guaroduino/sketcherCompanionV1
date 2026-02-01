package com.skecher.sketchercompanionv1.utils

import android.graphics.PointF
import com.skecher.sketchercompanionv1.StrokePoint

/**
 * Utility functions for vector stroke processing and prediction.
 */
object VectorUtils {
    
    /**
     * Calculates a predicted point based on quadratic extrapolation (physics-based).
     * Accounts for velocity and acceleration to follow arcs more accurately.
     * 
     * @param points List of recent stroke points
     * @param factor Prediction strength. Higher values predict further ahead.
     * @return The predicted StrokePoint with position and pressure
     */
    fun calculateQuadraticPrediction(
        points: List<StrokePoint>,
        factor: Float = 1.0f
    ): StrokePoint {
        // Fallback for insufficient points for quadratic
        if (points.size < 3) {
            return calculateLinearPrediction(points, factor)
        }

        // Logic for >= 3 points
        val pCurr = points[points.size - 1]
        val pPrev = points[points.size - 2]
        val pPrev2 = points[points.size - 3]

        // Calculate Velocity 1 (Current)
        val v1x = pCurr.x - pPrev.x
        val v1y = pCurr.y - pPrev.y

        // Calculate Velocity 0 (Previous)
        val v0x = pPrev.x - pPrev2.x
        val v0y = pPrev.y - pPrev2.y

        // Calculate Acceleration
        val accX = v1x - v0x
        val accY = v1y - v0y

        // Stabilization: Dampen sensor noise (jitter)
        val dampAccX = accX * 0.5f
        val dampAccY = accY * 0.5f

        // Formula: Predicted = P_curr + (v1 * factor) + (0.5 * dampAcc * factor^2)
        val predictedX = pCurr.x + (v1x * factor) + (0.5f * dampAccX * factor * factor)
        val predictedY = pCurr.y + (v1y * factor) + (0.5f * dampAccY * factor * factor)

        // Pressure: Linearly extrapolate based on last change
        val pressureChange = pCurr.pressure - pPrev.pressure
        val predictedPressure = (pCurr.pressure + pressureChange * factor).coerceIn(0f, 1f)

        return StrokePoint(predictedX, predictedY, predictedPressure)
    }

    /**
     * Simple linear fallback for prediction when less than 3 points are available.
     */
    private fun calculateLinearPrediction(
        points: List<StrokePoint>,
        factor: Float
    ): StrokePoint {
        if (points.size < 2) return points.lastOrNull() ?: StrokePoint(0f, 0f, 0f)
        
        val current = points.last()
        val previous = points[points.size - 2]
        
        val velocityX = current.x - previous.x
        val velocityY = current.y - previous.y
        val pressureChange = current.pressure - previous.pressure
        
        return StrokePoint(
            x = current.x + (velocityX * factor),
            y = current.y + (velocityY * factor),
            pressure = (current.pressure + pressureChange * factor).coerceIn(0f, 1f)
        )
    }
    
    /**
     * Calculates the perpendicular distance from a point to a line segment.
     * Used by the RDP algorithm to determine which points to keep.
     * 
     * @param point The point to measure distance from
     * @param lineStart Start point of the line segment
     * @param lineEnd End point of the line segment
     * @return Perpendicular distance in pixels
     */
    private fun perpendicularDistance(
        point: StrokePoint,
        lineStart: StrokePoint,
        lineEnd: StrokePoint
    ): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        
        // Handle degenerate case where start and end are the same point
        val lineLengthSquared = dx * dx + dy * dy
        if (lineLengthSquared == 0f) {
            return kotlin.math.hypot(point.x - lineStart.x, point.y - lineStart.y)
        }
        
        // Calculate perpendicular distance using cross product formula
        val numerator = kotlin.math.abs(
            dy * point.x - dx * point.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x
        )
        return numerator / kotlin.math.sqrt(lineLengthSquared)
    }
    
    /**
     * Calculates the pressure deviation from linear interpolation.
     * Used by the pressure-aware RDP algorithm to preserve pressure variations.
     * 
     * @param point The point to check
     * @param lineStart Start point of the line segment
     * @param lineEnd End point of the line segment
     * @return Absolute difference between actual and interpolated pressure
     */
    private fun pressureDeviation(
        point: StrokePoint,
        lineStart: StrokePoint,
        lineEnd: StrokePoint
    ): Float {
        // Calculate the position of the point along the line segment (0.0 to 1.0)
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        val lineLengthSquared = dx * dx + dy * dy
        
        if (lineLengthSquared == 0f) {
            // Degenerate case: start and end are the same point
            return kotlin.math.abs(point.pressure - lineStart.pressure)
        }
        
        // Project point onto the line to find interpolation factor
        val t = ((point.x - lineStart.x) * dx + (point.y - lineStart.y) * dy) / lineLengthSquared
        val clampedT = t.coerceIn(0f, 1f)
        
        // Calculate what the pressure SHOULD be at this position (linear interpolation)
        val interpolatedPressure = lineStart.pressure + (lineEnd.pressure - lineStart.pressure) * clampedT
        
        // Return the deviation from expected pressure
        return kotlin.math.abs(point.pressure - interpolatedPressure)
    }
    
    /**
     * Recursive helper for Smart Ramer-Douglas-Peucker simplification.
     * Incorporates Distance, Pressure, and Force-Keep (Corners) logic.
     */
    private fun smartRDPRecursive(
        points: List<StrokePoint>,
        startIndex: Int,
        endIndex: Int,
        tolerance: Float,
        pressureTolerance: Float,
        forceKeep: List<Boolean>,
        result: MutableList<Int> // Store indices to avoid duplication and maintain order
    ) {
        if (endIndex <= startIndex + 1) return

        var maxError = 0f
        var maxIndex = startIndex

        val start = points[startIndex]
        val end = points[endIndex]

        for (i in (startIndex + 1) until endIndex) {
            val point = points[i]
            
            // 1. Distance Error (Perpendicular)
            val distError = perpendicularDistance(point, start, end)
            
            // 2. Pressure Error (LERP Deviation)
            val pError = pressureDeviation(point, start, end)
            
            // 3. Force Keep (Corners)
            val isForceKeep = forceKeep[i]

            // Error Calculation: Normalized by tolerance
            val normDist = if (tolerance > 0f) distError / tolerance else 0f
            val normPress = if (pressureTolerance > 0f) pError / pressureTolerance else 0f
            
            // A point is significant if it exceeds either tolerance OR is a corner
            val currentError = if (isForceKeep) Float.MAX_VALUE else kotlin.math.max(normDist, normPress)

            if (currentError > maxError) {
                maxError = currentError
                maxIndex = i
            }
        }

        if (maxError > 1.0f) {
            // Split and recurse
            smartRDPRecursive(points, startIndex, maxIndex, tolerance, pressureTolerance, forceKeep, result)
            result.add(maxIndex)
            smartRDPRecursive(points, maxIndex, endIndex, tolerance, pressureTolerance, forceKeep, result)
        }
    }

    /**
     * Calculates the angle between three points (i-1, i, i+1).
     * Returns the absolute deviation from a straight line in degrees.
     */
    private fun calculateAngleDeviation(p1: StrokePoint, p2: StrokePoint, p3: StrokePoint): Float {
        val v1x = p2.x - p1.x
        val v1y = p2.y - p1.y
        val v2x = p3.x - p2.x
        val v2y = p3.y - p2.y

        val dot = v1x * v2x + v1y * v2y
        val mag1 = kotlin.math.sqrt(v1x * v1x + v1y * v1y)
        val mag2 = kotlin.math.sqrt(v2x * v2x + v2y * v2y)

        if (mag1 == 0f || mag2 == 0f) return 0f

        val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1f, 1f)
        val angleRad = kotlin.math.acos(cosTheta)
        return Math.toDegrees(angleRad.toDouble()).toFloat()
    }

    /**
     * Smart Ramer-Douglas-Peucker Simplification.
     * Respects Distance, Pressure, Corner Angles, and Zoom level.
     * 
     * @param points Raw stroke points
     * @param zoomScale Current camera scale (zoom)
     * @param cornerThresholdDegrees Angle deviation (0-90) to force-keep points
     * @param pressureTolerance Deviation from lerp pressure to force-keep points (default 0.05)
     */
    fun simplifyPoints(
        points: List<StrokePoint>,
        zoomScale: Float,
        cornerThresholdDegrees: Float,
        pressureTolerance: Float = 0.15f
    ): List<StrokePoint> {
        if (points.size <= 2) return points

        // Step A: Pre-Processing (Angle Detection)
        val forceKeep = MutableList(points.size) { false }
        forceKeep[0] = true
        forceKeep[points.lastIndex] = true

        for (i in 1 until points.size - 1) {
            val angleDeviation = calculateAngleDeviation(points[i - 1], points[i], points[i + 1])
            if (angleDeviation > cornerThresholdDegrees) {
                forceKeep[i] = true
            }
        }

        // Step B: Recursive Smart RDP
        val tolerance = 1.0f / zoomScale
        val resultIndices = mutableListOf<Int>()
        resultIndices.add(0) // Start point
        
        smartRDPRecursive(
            points = points,
            startIndex = 0,
            endIndex = points.size - 1,
            tolerance = tolerance,
            pressureTolerance = pressureTolerance,
            forceKeep = forceKeep,
            result = resultIndices
        )
        
        resultIndices.add(points.size - 1) // End point
        
        // Sort indices and map back to points (ensures order)
        return resultIndices.distinct().sorted().map { points[it] }
    }



    /**
     * Checks if a point with a given tolerance (radius) hits a FillData element.
     */
    fun isFillHit(fill: com.skecher.sketchercompanionv1.FillData, x: Float, y: Float, tolerance: Float): Boolean {
        val rect = android.graphics.RectF()
        fill.path.computeBounds(rect, true)
        
        // Expand bounds by tolerance for preliminary check
        val expandedRect = android.graphics.RectF(rect)
        expandedRect.inset(-tolerance, -tolerance)
        
        if (!expandedRect.contains(x, y)) return false
        
        // Precise intersection: Check if eraser circle (approximated by expanded rect or sampling) 
        // intersects the path. 
        // For simplicity and performance, we'll check if the center is in path 
        // OR if the center is very close to the bounding box (already checked by expandedRect).
        // A better way is to check if a small region around the eraser center intersects the path.
        return isAreaInPath(fill.path, x, y, tolerance)
    }

    // Helper for area-in-path using Region
    private fun isAreaInPath(path: android.graphics.Path, x: Float, y: Float, radius: Float): Boolean {
         val rectF = android.graphics.RectF()
         path.computeBounds(rectF, true)
         
         // Use a clipping region that covers the path + the eraser
         val clipRegion = android.graphics.Region(
             (kotlin.math.min(rectF.left, x - radius)).toInt() - 1,
             (kotlin.math.min(rectF.top, y - radius)).toInt() - 1,
             (kotlin.math.max(rectF.right, x + radius)).toInt() + 1,
             (kotlin.math.max(rectF.bottom, y + radius)).toInt() + 1
         )
         
         val pathRegion = android.graphics.Region()
         pathRegion.setPath(path, clipRegion)
         
         // Check if eraser box intersects the path region
         val eraserBox = android.graphics.Region(
             (x - radius).toInt(),
             (y - radius).toInt(),
             (x + radius).toInt(),
             (y + radius).toInt()
         )
         
         // op returns true if the resulting region is non-empty
         return pathRegion.op(eraserBox, android.graphics.Region.Op.INTERSECT)
    }
}


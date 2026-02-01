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
     * Calculates the extreme points of a polygon when projected onto a normal vector.
     * Returns the vertices with maximum and minimum projection values.
     * 
     * This is used by the Polygon Sweeper tool to find the leftmost and rightmost
     * vertices of a rotating polygon as it sweeps along a stroke path.
     * 
     * @param center Center point of the polygon
     * @param radius Distance from center to vertices
     * @param sides Number of polygon sides (3-10)
     * @param rotationRad Current rotation angle in radians
     * @param normalVector Direction vector to project onto (should be normalized)
     * @return Pair of (leftmost point, rightmost point) relative to the normal
     */
    fun getExtremePointsOfPolygon(
        center: PointF,
        radius: Float,
        sides: Int,
        rotationRad: Float,
        normalVector: PointF
    ): Pair<PointF, PointF> {
        // Step 1: Generate polygon vertices using polar coordinates
        val vertices = mutableListOf<PointF>()
        val angleStep = (2.0 * Math.PI / sides).toFloat()
        
        for (i in 0 until sides) {
            val angle = rotationRad + (i * angleStep)
            val x = center.x + radius * kotlin.math.cos(angle)
            val y = center.y + radius * kotlin.math.sin(angle)
            vertices.add(PointF(x, y))
        }
        
        // Step 2: Project each vertex onto the normal vector using dot product
        var maxProjection = Float.NEGATIVE_INFINITY
        var minProjection = Float.POSITIVE_INFINITY
        var maxVertex = vertices[0]
        var minVertex = vertices[0]
        
        for (vertex in vertices) {
            // Dot product: (vertex - center) · normalVector
            // Since we want projection relative to the stroke direction,
            // we calculate: vertex · normalVector
            val projection = vertex.x * normalVector.x + vertex.y * normalVector.y
            
            // Step 3: Track maximum projection (leftmost point)
            if (projection > maxProjection) {
                maxProjection = projection
                maxVertex = vertex
            }
            
            // Step 4: Track minimum projection (rightmost point)
            if (projection < minProjection) {
                minProjection = projection
                minVertex = vertex
            }
        }
        
        // Step 5: Return the extreme points
        return Pair(maxVertex, minVertex)
    }

    /**
     * Generates a smooth closed path from a list of points using Cubic Bézier interpolation.
     * Ideal for organic blobs and closed shapes.
     * 
     * @param points Ordered list of points defining the perimeter
     * @param tension Tension factor for the spline (default 0.5 leads to standard Catmull-Rom)
     * @return Closed Path with smooth transitions
     */
    fun generateSmoothClosedPath(points: List<PointF>, tension: Float = 0.5f): android.graphics.Path {
        val path = android.graphics.Path()
        if (points.size < 3) return path

        // 1. Move to the first point
        path.moveTo(points[0].x, points[0].y)

        val n = points.size
        for (i in 0 until n) {
            val p0 = points[(i - 1 + n) % n] // Previous
            val p1 = points[i]               // Current
            val p2 = points[(i + 1) % n]     // Next
            val p3 = points[(i + 2) % n]     // Next-Next

            // Calculate Control Points using Catmull-Rom logic
            // The factor determines how "tight" the curve is.
            // Using user requested formula: (tension / 6f)
            val factor = tension / 6f

            // CP1 = p1 + (p2 - p0) * factor
            val cp1x = p1.x + (p2.x - p0.x) * factor
            val cp1y = p1.y + (p2.y - p0.y) * factor

            // CP2 = p2 - (p3 - p1) * factor
            val cp2x = p2.x - (p3.x - p1.x) * factor
            val cp2y = p2.y - (p3.y - p1.y) * factor
            
            // Draw Cubic Bezier to the NEXT point (p2)
            path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
        }
        
        path.close()
        return path
    }
}


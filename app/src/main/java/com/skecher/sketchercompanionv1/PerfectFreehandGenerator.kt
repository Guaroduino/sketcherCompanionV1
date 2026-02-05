package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

object PerfectFreehandGenerator {

    // --- CONFIGURATION ---
    // Removed hardcoded constants in favor of dynamic settings passed to generate()

    // Internal data structure for calculations
    private data class StrokePointInternal(
        val x: Float,
        val y: Float,
        var pressure: Float = 0.5f, // 0..1
        val distance: Float = 0f, // Distance from start
        val vector: PointF = PointF(0f, 0f), // Tangent vector
        val runningLength: Float = 0f // Accum length
    )

    // --- PUBLIC API ---

    fun generate(
        rawPoints: List<StrokePoint>,
        maxWidth: Float,
        settings: com.skecher.sketchercompanionv1.dto.FreehandSettings = com.skecher.sketchercompanionv1.dto.FreehandSettings(), // Default settings
        simulatePressure: Boolean = true
    ): Triple<Path, List<PointF>, List<PointF>> {
        val path = Path()
        if (rawPoints.size < 2) return Triple(path, emptyList(), emptyList())

        // 1. Process Points (Clean, Simulate Pressure, Calculate Distances)
        val processedPoints = processPoints(rawPoints, simulatePressure, settings)

        // 2. Generate Ribbon Points (Left/Right)
        val (leftPts, rightPts) = getStrokeOutlinePoints(processedPoints, maxWidth, settings)

        // 3. Build Path
        if (leftPts.isNotEmpty() && rightPts.isNotEmpty()) {
            buildPath(path, leftPts, rightPts, settings)
        }

        return Triple(path, leftPts, rightPts)
    }

    // --- STEPS ---

    private fun processPoints(
        input: List<StrokePoint>,
        simulatePressure: Boolean,
        settings: com.skecher.sketchercompanionv1.dto.FreehandSettings
    ): List<StrokePointInternal> {
        if (input.isEmpty()) return emptyList()

        // 1. Decimation (Dynamic Tolerance)
        val decimated = mutableListOf<StrokePoint>()
        decimated.add(input.first())
        
        // Tolerance determines min distance. Default 1.0px.
        val minDistance = settings.tolerance.coerceAtLeast(0.1f)
        val minDistSq = minDistance * minDistance
        
        for (i in 1 until input.size - 1) {
            val prev = decimated.last()
            val curr = input[i]
            if (distSq(prev, curr) >= minDistSq) {
                decimated.add(curr)
            }
        }
        if (input.size > 1) {
            val last = input.last()
            if (decimated.size == 1 || distSq(decimated.last(), last) > 0.1f) {
                decimated.add(last)
            }
        }
        
        // Step Pre-A: Streamline / Smoothing (Laplacian Smoothing)
        // If streamline > 1.0, we apply multiple passes of smoothing
        // 0.0 -> 0 passes
        // 0.5 -> 1 pass at 0.25 strength
        // 1.0 -> 1 pass at 0.5 strength
        // 2.0 -> 2 passes at 0.5 strength
        // Step Pre-A: Streamline / Smoothing (Laplacian Smoothing) - REMOVED (Legacy)
        val smoothedPoints = decimated
        
        val points = mutableListOf<StrokePointInternal>()
        var totalDist = 0f
        
        for (i in smoothedPoints.indices) {
            val curr = smoothedPoints[i]
            
            // Influence 0 -> Factor 1
            // Influence 1 -> Factor = Real Pressure
            val realPressure = curr.pressure.coerceIn(0f, 1f)
            val pFactor = 1.0f - (settings.pressureInfluence * (1.0f - realPressure))
            
            var vFactor = 1.0f
            if (i > 0) {
                // ROLLING AVERAGE VELOCITY (Smoothed)
                val windowSize = 5
                val startIdx = max(0, i - windowSize)
                
                var totalDist = 0f
                var totalTime = 0f
                
                // Calculate average over the window ending at 'i'
                // We restart from startIdx+1 up to i
                for (k in i downTo startIdx + 1) {
                    val p2 = smoothedPoints[k]
                    val p1 = smoothedPoints[k - 1]
                    val d = dist(p1, p2)
                    var dt = (p2.timestamp - p1.timestamp).toFloat()
                    if (dt <= 0) dt = 16f
                    
                    totalDist += d
                    totalTime += dt
                }

                val velocity = if (totalTime > 0) totalDist / totalTime else 0f
                val maxSpeed = settings.maxPredictionVelocity.coerceAtLeast(1f) // Use setting instead of hardcoded 3.0f
                
                val normalizedVel = (velocity / maxSpeed).coerceIn(0f, 1f)
                val simPressure = (1f - normalizedVel).coerceIn(0f, 1f) // Fast = Thin
                
                vFactor = 1.0f - (settings.velocityInfluence * (1.0f - simPressure))
            }
            
            val rawPressure = pFactor * vFactor
            
            points.add(StrokePointInternal(
                x = curr.x,
                y = curr.y,
                pressure = rawPressure,
                runningLength = totalDist
            ))

            if (i < smoothedPoints.size - 1) {
                totalDist += dist(smoothedPoints[i], smoothedPoints[i+1])
            }
        }
        
        val totalLength = totalDist

        // 3. Pressure Smoothing (EMA)
        // New Formula for Extended Range (0.0 to 3.0)
        // 0.0 -> Alpha 0.7 (Fast code)
        // 1.0 -> Alpha 0.175
        // 3.0 -> Alpha 0.07 (Trace changes very slowly)
        val alpha = 0.7f / (1.0f + settings.smoothing * 3.0f)
        
        if (points.isNotEmpty()) {
            var smoothedP = points.first().pressure
            for (i in 1 until points.size) {
                val currP = points[i].pressure
                smoothedP = smoothedP + (currP - smoothedP) * alpha
                points[i].pressure = smoothedP
            }
        }
        
        // 4. Tapering
        val MAX_TAPER_PX = 100f
        val taperLenStart = min(totalLength * 0.5f, settings.taperStart * MAX_TAPER_PX)
        val taperLenEnd = min(totalLength * 0.5f, settings.taperEnd * MAX_TAPER_PX)
        
        if (taperLenStart > 1f || taperLenEnd > 1f) {
            for (pt in points) {
                val distFromStart = pt.runningLength
                val distFromEnd = totalLength - pt.runningLength
                var taperFactor = 1.0f
                if (distFromStart < taperLenStart) {
                    val t = distFromStart / taperLenStart
                    taperFactor *= (t * (2 - t))
                }
                if (distFromEnd < taperLenEnd) {
                    val t = distFromEnd / taperLenEnd
                    taperFactor *= (t * (2 - t))
                }
                pt.pressure *= taperFactor
            }
        }

        return points
    }

    private fun getStrokeOutlinePoints(
        points: List<StrokePointInternal>,
        maxWidth: Float,
        settings: com.skecher.sketchercompanionv1.dto.FreehandSettings 
    ): Pair<List<PointF>, List<PointF>> {
        val leftPts = mutableListOf<PointF>()
        val rightPts = mutableListOf<PointF>()
        
        if (points.size < 2) return Pair(emptyList(), emptyList())
        
        for (i in points.indices) {
            val curr = points[i]
            
            // Step C: Geometry Construction (Normals from Decimated Points)
            val prev = if (i > 0) points[i - 1] else null
            val next = if (i < points.size - 1) points[i + 1] else null
            
            val tangent: PointF
            if (prev != null && next != null) {
                val vectorFromPrev = PointF(curr.x - prev.x, curr.y - prev.y)
                val vectorToNext = PointF(next.x - curr.x, next.y - curr.y)
                val v1 = normalize(vectorFromPrev)
                val v2 = normalize(vectorToNext)
                val tx = v1.x + v2.x
                val ty = v1.y + v2.y
                tangent = PointF(tx, ty)
            } else if (prev == null && next != null) {
                 tangent = PointF(next.x - curr.x, next.y - curr.y)
            } else if (prev != null && next == null) {
                tangent = PointF(curr.x - prev.x, curr.y - prev.y)
            } else {
                tangent = PointF(1f, 0f)
            }
            
            var normal = PointF(-tangent.y, tangent.x)
            normal = normalize(normal)
            
            // Dynamic width based on influence factors
            val dynamicWidth = maxWidth * curr.pressure
            
            // Absolute Minimum based on ratio
            val absoluteMin = maxWidth * settings.minWidthRatio
            
            // Clamp
            val w = kotlin.math.max(dynamicWidth, absoluteMin)
            val halfW = w / 2f
            
            leftPts.add(PointF(curr.x + normal.x * halfW, curr.y + normal.y * halfW))
            rightPts.add(PointF(curr.x - normal.x * halfW, curr.y - normal.y * halfW))
        }
        
        return Pair(leftPts, rightPts)
    }

    private fun buildPath(
        path: Path, 
        left: List<PointF>, 
        right: List<PointF>,
        settings: com.skecher.sketchercompanionv1.dto.FreehandSettings
    ) {
        // Step D: Path Building
        path.moveTo(left[0].x, left[0].y)
        
        // Connect Left Side
        connectPoints(path, left, settings.useSplines)
        
        // Cap End
        if (settings.capEnd) {
            val lastL = left.last()
            val lastR = right.last()
            
            // Tangent at End
            val dx = lastR.x - lastL.x
            val dy = lastR.y - lastL.y
            val wVec = PointF(dx, dy)
            val tipTangent = normalize(PointF(-wVec.y, wVec.x))
            
            val width = hypot(dx, dy)
            val radius = width / 2f
            val k = radius * 0.55228f 
            
            val c1 = PointF(lastL.x + tipTangent.x * k, lastL.y + tipTangent.y * k)
            val c2 = PointF(lastR.x + tipTangent.x * k, lastR.y + tipTangent.y * k)
            
            path.cubicTo(c1.x, c1.y, c2.x, c2.y, lastR.x, lastR.y)
        } else {
             path.lineTo(right.last().x, right.last().y)
        }
        
        // Connect Right Side (Backwards)
        // Note: right list is Start->End order. We need to draw End->Start.
        // connectPoints handles forward connection.
        // So we reverse the list first.
        val reversedRight = right.reversed()
        connectPoints(path, reversedRight, settings.useSplines)
        
        // Cap Start
        if (settings.capStart) {
            val firstL = left[0]
            val firstR = right[0]
            
            val dxS = firstL.x - firstR.x 
            val dyS = firstL.y - firstR.y
            val wVecS = PointF(dxS, dyS)
            val startTangent = normalize(PointF(-wVecS.y, wVecS.x))
            
            val widthS = hypot(dxS, dyS)
            val radiusS = widthS / 2f
            val kS = radiusS * 0.55228f
            
            val c3 = PointF(firstR.x + startTangent.x * kS, firstR.y + startTangent.y * kS)
            val c4 = PointF(firstL.x + startTangent.x * kS, firstL.y + startTangent.y * kS)
            
            path.cubicTo(c3.x, c3.y, c4.x, c4.y, firstL.x, firstL.y)
        } else {
             path.lineTo(left[0].x, left[0].y)
        }
        
        path.close()
    }
    
    // Helper to connect points with either straight lines or splines
    private fun connectPoints(path: Path, points: List<PointF>, useSplines: Boolean) {
        if (points.isEmpty()) return
        
        // Ideally we assume path is already at points[0], but let's be safe for first segment
        // Actually, if we just did moveTo outside, we can just lineTo the first point if we aren't there?
        // But for safety of a general 'connector', we assume we proceed FROM current pos TO points.
        
        // However, standard use is: we are AT points[0] already.
        // So we iterate from 1..end.
        
        if (!useSplines || points.size < 3) {
            for (i in 1 until points.size) {
                path.lineTo(points[i].x, points[i].y)
            }
        } else {
            // Quadratic Bezier (Midpoint Strategy)
            for (i in 1 until points.size - 1) {
                val current = points[i]
                val next = points[i + 1]
                val midX = (current.x + next.x) / 2f
                val midY = (current.y + next.y) / 2f
                
                // Curve from [previous] through [current] to [midpoint]
                // Wait, midpoint strategy usually goes:
                // Start -> (Control: P1) -> End: Midpoint(P1, P2)
                // Here: Start is P[i-1] (or previous anchor). 
                // Control is P[i].
                // Anchor is Mid(P[i], P[i+1]).
                
                path.quadTo(current.x, current.y, midX, midY)
            }
            // Connect last
            path.lineTo(points.last().x, points.last().y)
        }
    }

    // --- MATH HELPERS ---

    private fun distSq(p1: StrokePoint, p2: StrokePoint): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return dx * dx + dy * dy
    }

    private fun dist(p1: StrokePoint, p2: StrokePoint) = kotlin.math.sqrt(distSq(p1, p2))
    
    // Dist for internal points
    private fun dist(p1: StrokePointInternal, p2: StrokePointInternal): Float {
         return hypot(p2.x - p1.x, p2.y - p1.y)
    }

    private fun getTangent(p1: StrokePointInternal, p2: StrokePointInternal): PointF {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return normalize(PointF(dx, dy))
    }

    private fun normalize(p: PointF): PointF {
        val len = hypot(p.x, p.y)
        return if (len > 0.0001f) PointF(p.x / len, p.y / len) else PointF(0f, 0f)
    }
}

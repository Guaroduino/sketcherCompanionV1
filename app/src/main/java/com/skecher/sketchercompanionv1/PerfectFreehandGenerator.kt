package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.abs

object PerfectFreehandGenerator {

    private data class StrokePointInternal(
        val x: Float,
        val y: Float,
        var width: Float = 0f, // Store computed WIDTH, not pressure
        val runningLength: Float = 0f
    )

    fun generate(
        rawPoints: List<StrokePoint>,
        baseWidth: Float,
        settings: com.skecher.sketchercompanionv1.dto.FreehandSettings = com.skecher.sketchercompanionv1.dto.FreehandSettings(),
        simulatePressure: Boolean = false // Ignored now, we use dynamics logic
    ): Triple<Path, List<PointF>, List<PointF>> {
        val path = Path()
        if (rawPoints.size < 2) return Triple(path, emptyList(), emptyList())

        // 1. Process: Decimate -> Calculate Dynamics -> Smooth Width
        val processedPoints = processPoints(rawPoints, baseWidth, settings)
        
        if (processedPoints.size < 2) return Triple(path, emptyList(), emptyList())

        // 2. Geometry
        val (leftPts, rightPts) = getStrokeOutlinePoints(processedPoints)

        // 3. Caps (Start/End)
        // Check if we have enough points for caps
        val startCap = if (settings.capStart && leftPts.isNotEmpty()) getCapPoints(leftPts.first(), rightPts.first(), true) else emptyList()
        val endCap = if (settings.capEnd && leftPts.isNotEmpty()) getCapPoints(leftPts.last(), rightPts.last(), false) else emptyList()

        // 4. Polygon
        val polygon = mutableListOf<PointF>()
        polygon.addAll(startCap)
        polygon.addAll(leftPts)
        polygon.addAll(endCap)
        polygon.addAll(rightPts.reversed())

        return Triple(createPathFromPolygon(polygon), leftPts, rightPts)
    }

    private fun processPoints(
        input: List<StrokePoint>,
        baseWidth: Float,
        settings: com.skecher.sketchercompanionv1.dto.FreehandSettings
    ): List<StrokePointInternal> {
        if (input.isEmpty()) return emptyList()

        // A. Decimation (Conservative)
        // We use a tighter tolerance to keep curves smooth, relying on width-smoothing to fix pearls.
        val decimated = mutableListOf<StrokePoint>()
        decimated.add(input.first())
        val tolSq = settings.tolerance.coerceAtLeast(0.1f).times(settings.tolerance.coerceAtLeast(0.1f)) // tolerance^2
        
        for (i in 1 until input.size - 1) {
            val prev = decimated.last()
            val curr = input[i]
            if (distSq(prev, curr) >= tolSq) decimated.add(curr)
        }
        decimated.add(input.last())

        // B. Dynamics & Width Calculation
        val internalPoints = ArrayList<StrokePointInternal>(decimated.size)
        var runningLen = 0f
        
        // Window for smooth velocity calculation
        val velocityWindow = 4 

        for (i in decimated.indices) {
            val curr = decimated[i]
            
            // 1. Calculate Real Velocity (Smoothed over window)
            var velocity01 = 0f
            if (i > 0) {
                val startIdx = max(0, i - velocityWindow)
                val pStart = decimated[startIdx]
                val d = hypot(curr.x - pStart.x, curr.y - pStart.y)
                var dt = (curr.timestamp - pStart.timestamp).toFloat()
                if (dt <= 0) dt = 16f // Fallback to ~60fps frame time
                
                // Max speed heuristic: 3.0 px/ms is very fast
                val rawVelocity = d / dt 
                velocity01 = (rawVelocity / 3.0f).coerceIn(0f, 1f)
            }

            // 2. Normalize Pressure
            val pressure01 = curr.pressure.coerceIn(0f, 1f)

            // 3. Apply Signed Influence
            // Pressure: + (More=Thick), - (More=Thin)
            val pSign = settings.pressureInfluence
            val pAmount = abs(pSign)
            // If +: target = pressure. If -: target = 1-pressure.
            val effectiveP = if (pSign >= 0) pressure01 else (1f - pressure01)
            // Interpolate: 0 (Base Width) -> 1 (Max Effect based on effectiveP) 
            // Wait, standard logic is: Width = Base * (1 - Influence * (1 - P))
            // If Influence=1, P=1 -> Width=Base. P=0 -> Width=0.
            // So factor = 1 - (Amount * (1 - effectiveP)) 
            val pFactor = 1f - (pAmount * (1f - effectiveP))

            // Velocity: + (Fast=Thin), - (Fast=Thick)
            val vSign = settings.velocityInfluence
            val vAmount = abs(vSign)
            // If +: Fast(1) should be Thin. So target is Low(0). 
            // If -: Fast(1) should be Thick. So target is High(1).
            val effectiveV = if (vSign >= 0) (1f - velocity01) else velocity01
            val vFactor = 1f - (vAmount * (1f - effectiveV))

            // 5. Calculate Target Width
            var targetWidth = baseWidth * pFactor * vFactor
            targetWidth = max(targetWidth, baseWidth * settings.minWidthRatio)

            // Track Length
            if (i > 0) {
                val prev = decimated[i - 1]
                runningLen += hypot(curr.x - prev.x, curr.y - prev.y)
            }

            internalPoints.add(StrokePointInternal(curr.x, curr.y, targetWidth, runningLen))
        }

        // C. Width Smoothing (The Anti-Pearl Filter)
        // Instead of calculating width independently per point, we smooth the widths.
        // Heavy smoothing (0.8+) eliminates pearls but lags thickness changes.
        // We link it to the 'smoothing' setting.
        
        val alpha = 0.6f + (settings.smoothing * 0.35f) // Range 0.6 to 0.95
        
        // Pass 1: Forward Smoothing
        if (internalPoints.isNotEmpty()) {
            var smoothW = internalPoints.first().width
            for (p in internalPoints) {
                smoothW = smoothW * alpha + p.width * (1f - alpha)
                p.width = smoothW
            }
            
            // Pass 2: Backward Smoothing (Fixes lag caused by Pass 1, keeps peaks centered)
            smoothW = internalPoints.last().width
            for (i in internalPoints.indices.reversed()) {
                val p = internalPoints[i]
                smoothW = smoothW * alpha + p.width * (1f - alpha)
                // Blend forward and backward passes
                p.width = (p.width + smoothW) / 2f
            }
        }

        return internalPoints
    }

    private fun getStrokeOutlinePoints(points: List<StrokePointInternal>): Pair<List<PointF>, List<PointF>> {
        val leftPts = mutableListOf<PointF>()
        val rightPts = mutableListOf<PointF>()
        val count = points.size
        
        if (count == 0) return Pair(leftPts, rightPts)

        // Start Glitch Fix: Threshold
        val startThreshold = max(points.first().width / 2f, 2f)

        for (i in points.indices) {
            val curr = points[i]
            var tangent = PointF(1f, 0f)

            if (i == 0) {
                // Look ahead for stable vector
                for (k in 1 until count) {
                    val d = hypot(points[k].x - curr.x, points[k].y - curr.y)
                    if (d > startThreshold) {
                        tangent = normalize(points[k].x - curr.x, points[k].y - curr.y)
                        break
                    }
                }
            } else if (i == count - 1) {
                tangent = normalize(curr.x - points[i-1].x, curr.y - points[i-1].y)
            } else {
                val prev = points[i-1]; val next = points[i+1]
                val v1 = normalize(curr.x - prev.x, curr.y - prev.y)
                val v2 = normalize(next.x - curr.x, next.y - curr.y)
                tangent = normalize(v1.x + v2.x, v1.y + v2.y)
            }

            val normal = PointF(-tangent.y, tangent.x)
            val halfW = curr.width / 2f // Width is already computed and smoothed!

            leftPts.add(PointF(curr.x + normal.x * halfW, curr.y + normal.y * halfW))
            rightPts.add(PointF(curr.x - normal.x * halfW, curr.y - normal.y * halfW))
        }
        return Pair(leftPts, rightPts)
    }

    // --- HELPERS ---
    private fun getCapPoints(p1: PointF, p2: PointF, isStart: Boolean): List<PointF> {
        val midX = (p1.x + p2.x) / 2f; val midY = (p1.y + p2.y) / 2f
        val dx = p2.x - p1.x; val dy = p2.y - p1.y
        
        // Normal pointing OUT
        var nx = -dy; var ny = dx
        if (!isStart) { nx = dy; ny = -dx } // Invert for end cap? 
        // Logic: Start Cap connects Right->Left. Vector is P2-P1. Normal is (-dy, dx).
        // End Cap connects Left->Right. Vector is P2-P1. 
        // Let's rely on simple outward projection.
        
        val radius = hypot(dx, dy) / 2f
        val len = hypot(nx, ny)
        if (len > 0) { nx/=len; ny/=len }
        
        // If End Cap, flip normal to point forward
        if (!isStart) { nx = -nx; ny = -ny }

        return listOf(PointF(midX + nx * radius, midY + ny * radius))
    }

    private fun createPathFromPolygon(points: List<PointF>): Path {
        val path = Path()
        if (points.size < 3) return path
        val p0 = points[0]; val pLast = points.last()
        path.moveTo((p0.x + pLast.x) / 2f, (p0.y + pLast.y) / 2f)
        for (i in points.indices) {
            val curr = points[i]
            val next = points[(i + 1) % points.size]
            path.quadTo(curr.x, curr.y, (curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
        }
        path.close()
        return path
    }

    private fun normalize(x: Float, y: Float): PointF {
        val l = hypot(x, y)
        return if (l > 0.001f) PointF(x/l, y/l) else PointF(0f, 0f)
    }
    private fun distSq(p1: StrokePoint, p2: StrokePoint): Float {
        val dx = p1.x - p2.x; val dy = p1.y - p2.y
        return dx*dx + dy*dy
    }
}
package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import com.skecher.sketchercompanionv1.dto.FreehandSettings
import com.skecher.sketchercompanionv1.StrokePoint

import com.skecher.sketchercompanionv1.PerfectFreehandUtils.add
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.sub
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.mul
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.div
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.per
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.dpr
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.uni
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.dist
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.dist2
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.lrp
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.prj
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.rotAround
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.simulatePressure
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.getStrokeRadius
import com.skecher.sketchercompanionv1.PerfectFreehandUtils.neg
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.abs

object PerfectFreehandGenerator {

    // Constants from reference
    private const val MIN_STREAMLINE_T = 0.15f
    private const val STREAMLINE_T_RANGE = 0.85f
    private const val END_NOISE_THRESHOLD = 0.85f // Approximate reference (checked TS usually 0.95 or similar based on unit)
    private const val STOP = 0.001f // Epsilon
    private const val PI = Math.PI.toFloat()
    private const val FIXED_PI = PI + 0.0001f // To avoid floating point issues with perfect PI 
    
    private const val START_CAP_SEGMENTS = 12
    private const val END_CAP_SEGMENTS = 12
    private const val CORNER_CAP_SEGMENTS = 12

    // Internal data structure for processed points
    private data class StrokePointInternal(
        val point: Vec2,
        val pressure: Float,
        val distance: Float,
        var vector: Vec2 = Vec2(0f, 0f),
        val runningLength: Float
    )

    private data class OutlineResult(
        val left: List<Vec2>,
        val right: List<Vec2>,
        val polygon: List<Vec2>
    )

    // Public Result Data Class
    data class FreehandResult(
        val path: Path,
        val left: List<PointF>,
        val right: List<PointF>,
        val center: List<PointF>
    )

    fun generate(
        rawPoints: List<StrokePoint>,
        baseWidth: Float, // Used as 'size'
        settings: FreehandSettings = FreehandSettings()
    ): FreehandResult {
        val path = Path()
        if (rawPoints.isEmpty()) return FreehandResult(path, emptyList(), emptyList(), emptyList())

        // 1. Get Stroke Points
        val strokePoints = getStrokePoints(rawPoints, baseWidth, settings)

        // 2. Get Outline Points
        val outline = getStrokeOutlinePoints(strokePoints, baseWidth, settings)

        if (outline.polygon.size < 3) return FreehandResult(path, emptyList(), emptyList(), emptyList())

        val polygonPoints = outline.polygon
        
        // Strict Polygon Construction (lineTo)
        path.moveTo(polygonPoints[0].x, polygonPoints[0].y)
        for (i in 1 until polygonPoints.size) {
            path.lineTo(polygonPoints[i].x, polygonPoints[i].y)
        }
        path.close()

        val leftConv = outline.left.map { PointF(it.x, it.y) }
        val rightConv = outline.right.map { PointF(it.x, it.y) }
        val centerConv = strokePoints.map { PointF(it.point.x, it.point.y) }
        
        return FreehandResult(path, leftConv, rightConv, centerConv)
    }

    // --- Strict Implementation ---

    private fun getStrokePoints(
        input: List<StrokePoint>,
        size: Float,
        settings: FreehandSettings
    ): List<StrokePointInternal> {
        if (input.isEmpty()) return emptyList()

        val streamline = settings.streamline
        val t = MIN_STREAMLINE_T + (1f - streamline) * STREAMLINE_T_RANGE

        var pts = input.toMutableList()

        // Handle "dash" lines (2 points) -> interpolate
        if (pts.size == 2) {
            val last = pts[1]
            pts = pts.subList(0, 1) // Slice 0,-1 equivalent
            for (i in 1 until 5) {
                val lerped = lrp(
                     Vec2(pts[0].x, pts[0].y),
                     Vec2(last.x, last.y),
                     i / 4f
                )
                pts.add(StrokePoint(lerped.x, lerped.y, last.pressure, last.timestamp))
            }
        }
        
        // 1pt case: Add offset
        if (pts.size == 1) {
            val p = pts[0]
            pts.add(StrokePoint(p.x + 1f, p.y + 1f, p.pressure, p.timestamp))
        }

        val strokePoints = ArrayList<StrokePointInternal>()
        
        // First point
        val p0 = pts[0]
        strokePoints.add(StrokePointInternal(
            point = Vec2(p0.x, p0.y),
            pressure = if (p0.pressure >= 0) p0.pressure else 0.5f,
            distance = 0f,
            vector = Vec2(1f, 1f), // Placeholder
            runningLength = 0f
        ))

        var runningLength = 0f
        var prev = strokePoints[0]
        var hasReachedMinimumLength = false
        
        for (i in 1 until pts.size) {
            // Logic for 'isComplete' omitted (assumed live/complete handled same for now)
            // Interpolation
            val point = lrp(prev.point, Vec2(pts[i].x, pts[i].y), t)
            
            if (point == prev.point) continue // Exact equality check (Vec2 data class)
            
            val d = dist(point, prev.point)
            runningLength += d
            
            // Noise filter at start
            if (i < pts.size - 1 && !hasReachedMinimumLength) {
                if (runningLength < size) continue
                hasReachedMinimumLength = true
            }
            
            val vector = uni(sub(point, prev.point))
            
            val newPoint = StrokePointInternal(
                point = point,
                pressure = if (pts[i].pressure >= 0) pts[i].pressure else 0.5f,
                distance = d,
                vector = vector,
                runningLength = runningLength
            )
            
            strokePoints.add(newPoint)
            prev = newPoint
        }
        
        // Fix first vector
        if (strokePoints.size > 1) {
            strokePoints[0].vector = strokePoints[1].vector
        }
        
        return strokePoints
    }

    private fun getStrokeOutlinePoints(
        points: List<StrokePointInternal>,
        size: Float,
        settings: FreehandSettings
    ): OutlineResult {
        if (points.isEmpty() || size <= 0) return OutlineResult(emptyList(), emptyList(), emptyList())

        val smoothing = settings.smoothing
        val thinning = settings.thinning
        val simulatePressure = settings.simulatePressure
        val easing: (Float) -> Float = { t -> t } // Linear pressure easing

        val capStart = settings.capStart
        val capEnd = settings.capEnd
        
        // Correct Easing for Taper (Quad/Cubic approximations from reference)
        val taperStartEase: (Float) -> Float = { t -> t * (2 - t) } 
        val taperEndEase: (Float) -> Float = { t -> val tm = t - 1; tm * tm * tm + 1 }

        val totalLength = points.last().runningLength
        val taperStart = computeTaperDistance(settings.taperStart, size, totalLength)
        val taperEnd = computeTaperDistance(settings.taperEnd, size, totalLength)

        val minDistance = (size * smoothing).pow(2)
        
        val leftPts = ArrayList<Vec2>()
        val rightPts = ArrayList<Vec2>()
        
        var prevPressure = computeInitialPressure(points, simulatePressure, size)
        var radius = getStrokeRadius(size, thinning, points.last().pressure, easing)
        var firstRadius: Float? = null
        
        var prevVector = points[0].vector
        var prevLeft = points[0].point
        var prevRight = prevLeft
        var isPrevPointSharpCorner = false

        for (i in points.indices) {
            val curr = points[i]
            var pressure = curr.pressure
            val isLastPoint = i == points.size - 1

            if (!isLastPoint && (totalLength - curr.runningLength) < 3f /* END_NOISE_THRESHOLD approx 3px or similar? Reference uses constant */) {
                // strict reference check:
                // if (!isLastPoint && totalLength - runningLength < END_NOISE_THRESHOLD) continue
            }

            // Calculate Radius
            if (thinning > 0) {
                 if (simulatePressure) {
                     pressure = simulatePressure(prevPressure, curr.distance, size)
                 }
                 radius = getStrokeRadius(size, thinning, pressure, easing)
            } else {
                 radius = size / 2f
            }
            
            if (firstRadius == null) firstRadius = radius
            
            // Tapering
            val ts = if (curr.runningLength < taperStart) taperStartEase(curr.runningLength / taperStart) else 1f
            val te = if (totalLength - curr.runningLength < taperEnd) taperEndEase((totalLength - curr.runningLength) / taperEnd) else 1f
            
            radius = max(0.01f, radius * min(ts, te))

            // Sharp Corners
            val nextVector = if (i < points.size - 1) points[i + 1].vector else points[i].vector
            val nextDpr = if (i < points.size - 1) dpr(curr.vector, nextVector) else 1.0f
            val prevDpr = dpr(curr.vector, prevVector)

            val isPointSharpCorner = prevDpr < 0 && !isPrevPointSharpCorner
            val isNextPointSharpCorner = nextDpr < 0

            if (isPointSharpCorner || isNextPointSharpCorner) {
                val offset = mul(per(prevVector), radius)
                
                // Draw Round Cap at Corner
                val step = 1f / CORNER_CAP_SEGMENTS
                for (k in 0..CORNER_CAP_SEGMENTS) { // <= 1
                    val t = k * step
                    val tl = rotAround(sub(curr.point, offset), curr.point, FIXED_PI * t)
                    val tr = rotAround(add(curr.point, offset), curr.point, FIXED_PI * -t)
                    
                    leftPts.add(tl)
                    rightPts.add(tr)
                    
                    // Update temp/prev
                    prevLeft = tl
                    prevRight = tr
                }
                
                if (isNextPointSharpCorner) isPrevPointSharpCorner = true
                continue
            }

            isPrevPointSharpCorner = false

            // Last Point
            if (isLastPoint) {
                val offset = mul(per(curr.vector), radius)
                leftPts.add(sub(curr.point, offset))
                rightPts.add(add(curr.point, offset))
                continue
            }

            // Regular Points
            val offset = mul(per(lrp(nextVector, curr.vector, nextDpr)), radius)
            
            val tl = sub(curr.point, offset)
            if (i <= 1 || dist2(prevLeft, tl) > minDistance) {
                leftPts.add(tl)
                prevLeft = tl
            }

            val tr = add(curr.point, offset)
            if (i <= 1 || dist2(prevRight, tr) > minDistance) {
                rightPts.add(tr)
                prevRight = tr
            }

            prevPressure = pressure
            prevVector = curr.vector
        }
        
        // Construct Caps
        val firstPoint = points[0].point
        val lastPoint = if (points.size > 1) points.last().point else add(points[0].point, Vec2(1f, 1f))
        
        val startCap = ArrayList<Vec2>()
        val endCap = ArrayList<Vec2>()

        // 1pt / Dot
        if (points.size == 1) {
            val r = firstRadius ?: radius
            return if (!(taperStart > 0 || taperEnd > 0)) { // isComplete assumed false or irrel
                 val dot = drawDot(firstPoint, r)
                 OutlineResult(dot, emptyList(), dot) // Roughly correct return structure
            } else {
                 OutlineResult(emptyList(), emptyList(), emptyList())
            }
        }
        
        // Start Cap
        if (taperStart > 0 || (taperEnd > 0 && points.size == 1)) {
            // No cap
        } else if (capStart) {
            val firstRight = rightPts.firstOrNull() ?: firstPoint
            startCap.addAll(drawRoundStartCap(firstPoint, firstRight, START_CAP_SEGMENTS))
        } else {
            val firstLeft = leftPts.firstOrNull() ?: firstPoint
            val firstRight = rightPts.firstOrNull() ?: firstPoint
            startCap.addAll(drawFlatStartCap(firstPoint, firstLeft, firstRight))
        }

        // End Cap
        val direction = per(neg(points.last().vector))
        if (taperEnd > 0 || (taperStart > 0 && points.size == 1)) {
            endCap.add(lastPoint)
        } else if (capEnd) {
             endCap.addAll(drawRoundEndCap(lastPoint, direction, radius, END_CAP_SEGMENTS))
        } else {
             endCap.addAll(drawFlatEndCap(lastPoint, direction, radius))
        }

        val polygon = ArrayList<Vec2>()
        polygon.addAll(leftPts)
        polygon.addAll(endCap)
        polygon.addAll(rightPts.reversed())
        polygon.addAll(startCap)
        
        return OutlineResult(leftPts, rightPts, polygon)
    }

    // --- Helpers ---

    private fun computeTaperDistance(taper: Float, size: Float, totalLength: Float): Float {
        // Taper is float in settings (px)
        // If 0 -> 0
        // If we want to simulate "true" (max taper), we'd need a flag or special value.
        // For now trusting the float value.
        // Ref: if (taper === true) return Math.max(size, totalLength)
        return taper
    }

    private fun computeInitialPressure(points: List<StrokePointInternal>, simulate: Boolean, size: Float): Float {
        if (points.isEmpty()) return 0.5f
        var acc = points[0].pressure
        val count = min(10, points.size)
        for (i in 0 until count) {
            var p = points[i].pressure
            if (simulate) {
                p = simulatePressure(acc, points[i].distance, size)
            }
            acc = (acc + p) / 2f
        }
        return acc
    }

    private fun drawDot(center: Vec2, radius: Float): List<Vec2> {
        val offsetPoint = add(center, Vec2(1f, 1f))
        val start = prj(center, uni(per(sub(center, offsetPoint))), -radius)
        val dotPts = ArrayList<Vec2>()
        val step = 1f / START_CAP_SEGMENTS
        for (k in 1..START_CAP_SEGMENTS) {
             val t = k * step 
             // <= 1 implied by range
             dotPts.add(rotAround(start, center, FIXED_PI * 2 * t))
        }
        return dotPts
    }

    private fun drawRoundStartCap(center: Vec2, rightPoint: Vec2, segments: Int): List<Vec2> {
        val cap = ArrayList<Vec2>()
        val step = 1f / segments
        for (k in 1..segments) {
            val t = k * step
            cap.add(rotAround(rightPoint, center, FIXED_PI * t))
        }
        return cap
    }

    private fun drawFlatStartCap(center: Vec2, leftPoint: Vec2, rightPoint: Vec2): List<Vec2> {
        val cornersVector = sub(leftPoint, rightPoint)
        val offsetA = mul(cornersVector, 0.5f)
        val offsetB = mul(cornersVector, 0.51f)
        return listOf(
            sub(center, offsetA),
            sub(center, offsetB),
            add(center, offsetB),
            add(center, offsetA)
        )
    }

    private fun drawRoundEndCap(center: Vec2, direction: Vec2, radius: Float, segments: Int): List<Vec2> {
        val cap = ArrayList<Vec2>()
        val start = prj(center, direction, radius)
        val step = 1f / segments
        for (k in 1 until segments) { // < 1
            val t = k * step
            cap.add(rotAround(start, center, FIXED_PI * 3 * t))
        }
        // Explicitly close or rely on polygon? Ref uses < 1
        return cap
    }

    private fun drawFlatEndCap(center: Vec2, direction: Vec2, radius: Float): List<Vec2> {
        return listOf(
            add(center, mul(direction, radius)),
            add(center, mul(direction, radius * 0.99f)),
            sub(center, mul(direction, radius * 0.99f)),
            sub(center, mul(direction, radius))
        )
    }
}

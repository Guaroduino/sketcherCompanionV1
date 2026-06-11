package com.sketcher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import com.sketcher.sketchercompanionv1.dto.FreehandSettings
import com.sketcher.sketchercompanionv1.StrokePoint

import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.add
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.sub
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.mul
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.div
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.per
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.dpr
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.uni
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.dist
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.dist2
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.lrp
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.prj
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.rotAround
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.simulatePressure
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.getStrokeRadius
import com.sketcher.sketchercompanionv1.PerfectFreehandUtils.neg
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.abs

object PerfectFreehandGenerator {

    // Constants from reference
    private const val MIN_STREAMLINE_T = 0.15f
    private const val STREAMLINE_T_RANGE = 0.85f
    private const val END_NOISE_THRESHOLD = 0.99f // Approximate reference (checked TS usually 0.95 or similar based on unit)
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
        val normalizedVelocity: Float, // New: 0..1
        var vector: Vec2 = Vec2(0f, 0f),
        val runningLength: Float
    )

    private data class OutlineResult(
        val left: List<Vec2>,
        val right: List<Vec2>,
        val polygon: List<Vec2>,
        val lastRadius: Float
    )

    // Public Result Data Class
    data class FreehandResult(
        val path: Path,
        val left: List<PointF>,
        val right: List<PointF>,
        val center: List<PointF>,
        val lastRadius: Float
    )

    fun generate(
        rawPoints: List<StrokePoint>,
        settings: FreehandSettings = FreehandSettings(),
        zoom: Float = 1.0f, // Current viewport scale
        outPath: Path = Path() // Reusable path
    ): FreehandResult {
        val path = outPath.apply { rewind() } // Rewind keeps interior structures
        val size = settings.size
        val isComplete = settings.isComplete
        
        if (rawPoints.isEmpty()) return FreehandResult(path, emptyList(), emptyList(), emptyList(), 0f)

        // 1. Get Stroke Points
        val strokePoints = getStrokePoints(rawPoints, size, settings, isComplete, zoom)

        // 2. Get Outline Points
        val outline = getStrokeOutlinePoints(strokePoints, size, settings)

        if (outline.polygon.size < 3) return FreehandResult(path, emptyList(), emptyList(), emptyList(), 0f)

        val polygonPoints = outline.polygon
        
        // Polygonal Construction
        if (settings.useCurveForPolygon) {
            // Curve Strategy
            val p0 = polygonPoints[0]
            val pLast = polygonPoints.last()
            
            path.moveTo((p0.x + pLast.x) / 2f, (p0.y + pLast.y) / 2f)
            for (i in polygonPoints.indices) {
                val curr = polygonPoints[i]
                val next = polygonPoints[(i + 1) % polygonPoints.size]
                path.quadTo(curr.x, curr.y, (curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
            }
        } else {
             // Strict Linear Strategy
             path.moveTo(polygonPoints[0].x, polygonPoints[0].y)
             for (i in 1 until polygonPoints.size) {
                 path.lineTo(polygonPoints[i].x, polygonPoints[i].y)
             }
        }
        path.close()

        val leftConv = outline.left.map { PointF(it.x, it.y) }
        val rightConv = outline.right.map { PointF(it.x, it.y) }
        val centerConv = strokePoints.map { PointF(it.point.x, it.point.y) }
        
        return FreehandResult(path, leftConv, rightConv, centerConv, outline.lastRadius)
    }

    // --- Strict Implementation ---

    private fun getStrokePoints(
        input: List<StrokePoint>,
        size: Float,
        settings: FreehandSettings,
        isComplete: Boolean,
        zoom: Float = 1.0f
    ): List<StrokePointInternal> {
        if (input.isEmpty()) return emptyList()

        val streamline = settings.streamline
        val t = MIN_STREAMLINE_T + (1f - streamline) * STREAMLINE_T_RANGE
        val maxVel = max(0.1f, settings.velocityMaxInput) // User controlled max velocity (px/ms)

        var pts = input.toMutableList()

        // Handle "dash" lines (2 points) -> interpolate
        if (pts.size == 2) {
            val last = pts[1]
            pts = pts.subList(0, 1) // Slice 0,-1 equivalent
            for (i in 1 until 5) {
                val factor = i / 4f
                val lerped = lrp(
                     Vec2(pts[0].x, pts[0].y),
                     Vec2(last.x, last.y),
                     factor
                )
                // Interpolate Time
                val lerpTime = (pts[0].timestamp + (last.timestamp - pts[0].timestamp) * factor).toLong()
                
                pts.add(StrokePoint(lerped.x, lerped.y, last.pressure, lerpTime))
            }
        }
        
        // 1pt case: Add offset
        if (pts.size == 1) {
            val p = pts[0]
            pts.add(StrokePoint(p.x + 1f, p.y + 1f, p.pressure, p.timestamp))
        }

        // PRE-CALCULATE VELOCITIES (Raw Input Dynamics)
        val velocities = computeSmoothedVelocities(pts)

        val strokePoints = ArrayList<StrokePointInternal>()
        
        // First point
        val p0 = pts[0]
        strokePoints.add(StrokePointInternal(
            point = Vec2(p0.x, p0.y),
            pressure = if (p0.pressure >= 0) p0.pressure else 0.5f,
            distance = 0f,
            normalizedVelocity = 0f,
            vector = Vec2(1f, 1f), // Placeholder
            runningLength = 0f
        ))
        
        var runningLength = 0f
        var prev = strokePoints[0]
        
        var hasReachedMinimumLength = false
        
        for (i in 1 until pts.size) {
            val rawP = pts[i]
            val isLastPoint = i == pts.size - 1
            
            val point = if (isComplete && isLastPoint) {
                Vec2(rawP.x, rawP.y)
            } else {
                lrp(prev.point, Vec2(rawP.x, rawP.y), t)
            }
            
            if (point == prev.point) continue 
            
            val d = dist(point, prev.point)
            runningLength += d
            
            // Noise filter at start
            if (i < pts.size - 1 && !hasReachedMinimumLength) {
                if (runningLength < size) continue
                hasReachedMinimumLength = true
            }
            
            val vector = uni(sub(point, prev.point))
            
            // Velocity Lookup (Smoothed from Raw)
            // MULTIPLY BY ZOOM: This normalizes world-speed back to screen-speed (hand effort)
            val velocity = velocities.getOrElse(i) { 0f } * zoom
            val normVel = min(1f, velocity / maxVel)

            val newPoint = StrokePointInternal(
                point = point,
                pressure = if (rawP.pressure >= 0) rawP.pressure else 0.5f,
                distance = d,
                normalizedVelocity = normVel,
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

    private fun computeSmoothedVelocities(points: List<StrokePoint>): List<Float> {
        val vels = ArrayList<Float>()
        var lastVel = 0f
        
        // Loop over the list
        for (i in points.indices) {
            if (i == 0) {
                vels.add(0f)
                continue
            }
            
            val curr = points[i]
            val prev = points[i-1]
            
            val d = dist(Vec2(curr.x, curr.y), Vec2(prev.x, prev.y))
            val dt = max(1L, curr.timestamp - prev.timestamp).toFloat()
            val instantVel = d / dt
            
            // EMWA Smoothing (Strong Smoothing to prevent pearls)
            // If instantVel spikes (e.g. 1ms dt), dampen it.
            // If dt < 5ms (very fast input event), instantVel is unreliable.
            
            // Smooth Factor
            val alpha = 0.2f // 20% influence from new sample
            
            val smoothVel = lastVel * (1f - alpha) + instantVel * alpha
            
            vels.add(smoothVel)
            lastVel = smoothVel
        }
        
        return vels
    }

    private fun getStrokeOutlinePoints(
        points: List<StrokePointInternal>,
        size: Float,
        settings: FreehandSettings
    ): OutlineResult {
        if (points.isEmpty() || size <= 0) return OutlineResult(emptyList(), emptyList(), emptyList(), 0f)

        val smoothing = settings.smoothing
        val thinning = settings.thinning
        val velocityThinning = settings.velocityThinning
        val simulatePressure = settings.simulatePressure
        val minWidthRatio = settings.minWidthRatio
        
        val easing: (Float) -> Float = { t -> t } // Linear pressure easing

        val capStart = settings.capStart
        val capEnd = settings.capEnd
        
        // Easing (Hermite curve) for tapering
        val hermiteEase: (Float) -> Float = { t -> t * t * (3 - 2 * t) }

        val totalLength = points.last().runningLength
        val taperStart = settings.taperStart
        val taperEnd = settings.taperEnd

        val minDistance = (size * smoothing).pow(2)
        val minWidth = size * minWidthRatio
        
        val leftPts = ArrayList<Vec2>()
        val rightPts = ArrayList<Vec2>()
        
        var prevPressure = computeInitialPressure(points, simulatePressure, size)
        
        // Initial Radius Calculation
        var baseRadius = getStrokeRadius(size, thinning, points.last().pressure, easing)
        if (velocityThinning > 0) {
            baseRadius *= (1f - velocityThinning * points.last().normalizedVelocity)
        }
        var radius = max(minWidth / 2f, baseRadius)
        
        var firstRadius: Float? = null
        
        var prevVector = points[0].vector
        var prevLeft = points[0].point
        var prevRight = prevLeft
        var isPrevPointSharpCorner = false

        for (i in points.indices) {
            val curr = points[i]
            var pressure = curr.pressure
            val isLastPoint = i == points.size - 1

            if (!isLastPoint && (totalLength - curr.runningLength) < END_NOISE_THRESHOLD) {
                continue
            }

            // Calculate Base Radius via Pressure/Velocity
            if (thinning > 0) {
                 if (simulatePressure) {
                     pressure = simulatePressure(prevPressure, curr.distance, size)
                 }
                 radius = getStrokeRadius(size, thinning, pressure, easing)
            } else {
                 radius = size / 2f
            }
            
            // Apply Velocity Thinning
            if (velocityThinning > 0) {
                radius *= (1f - velocityThinning * curr.normalizedVelocity)
            }

            // Apply Min Width (Before Taper to allow Taper to sharpen tip if needed? 
            // User request: "Thickness must not go below min". Usually means body. 
            // If Taper forces 0, it violates min width. But Taper is specific.
            // Let's enforce min width here on the BODY radius.
            radius = max(minWidth / 2f, radius)

            if (firstRadius == null) firstRadius = radius
            
            // Tapering
            if (taperStart > 0f) {
                val dist = curr.runningLength
                if (dist < taperStart) {
                    val tf = hermiteEase(dist / taperStart)
                    radius *= tf
                }
            }
            if (taperEnd > 0f) {
                val distFromEnd = totalLength - curr.runningLength
                if (distFromEnd < taperEnd) {
                    // Only apply end taper if we are close to the end
                    val tf = hermiteEase(distFromEnd / taperEnd)
                    radius *= tf
                }
            }
            
            // Fuse Taper/Widening (Multiply to allow both to coexist)
            radius = max(0.01f, radius)

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
        
        // Caps
        val firstPoint = points[0].point
        val lastPoint = if (points.size > 1) points.last().point else add(points[0].point, Vec2(1f, 1f))
        
        val startCap = ArrayList<Vec2>()
        val endCap = ArrayList<Vec2>()

        // 1pt / Dot
        if (points.size == 1) {
            val r = firstRadius ?: radius
            val dot = drawDot(firstPoint, r)
            return OutlineResult(dot, emptyList(), dot, r)
        }
        
        // --- Lógica Estricta de la Tapa Final (End Cap) ---
        // taperEnd > 0: point
        // taperEnd <= 0 && capEnd == true: arc right to left
        // taperEnd <= 0 && capEnd == false: nothing (flat line from right to left naturally formed)
        val endTaperingActive = taperEnd > 0f
        if (endTaperingActive) {
            endCap.add(lastPoint)
        } else if (capEnd) {
            val direction = points.last().vector // Outward direction is continuing the stroke
            endCap.addAll(drawRoundCap(lastPoint, direction, radius, END_CAP_SEGMENTS))
        }

        // --- Lógica Estricta de la Tapa Inicial (Start Cap) ---
        // taperStart > 0: point
        // taperStart <= 0 && capStart == true: arc left to right
        val startTaperingActive = taperStart > 0f
        if (startTaperingActive) {
            startCap.add(firstPoint)
        } else if (capStart) {
            val direction = neg(points[0].vector) // Outward direction is opposite of stroke
            startCap.addAll(drawRoundCap(firstPoint, direction, firstRadius ?: radius, START_CAP_SEGMENTS))
        }

        // Ensamblaje Estricto del Polígono: Right (inicio a fin) -> End Cap -> Left (fin a inicio) -> Start Cap
        val polygon = ArrayList<Vec2>()
        polygon.addAll(rightPts)
        polygon.addAll(endCap)
        polygon.addAll(leftPts.reversed())
        polygon.addAll(startCap)
        
        return OutlineResult(leftPts, rightPts, polygon, radius)
    }

    // --- Helpers ---

    private fun computeTaperDistance(taper: Float, size: Float, totalLength: Float): Float {
        // Updated to handle negative values logic inside main loop, this helper was for simple positive case
        // We use settings.taperStart directly now
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

    private fun drawRoundCap(center: Vec2, outwardDirection: Vec2, radius: Float, segments: Int): List<Vec2> {
        val cap = ArrayList<Vec2>()
        val dir = uni(outwardDirection)
        val centerAngle = kotlin.math.atan2(dir.y, dir.x)

        // Starting point of the sweep is always +90 degrees from the outward direction
        val startAngle = centerAngle + kotlin.math.PI / 2
        // We sweep 180 degrees (-PI) from +90 to -90
        val step = -kotlin.math.PI / segments
        
        for (i in 1 until segments) { 
            val theta = startAngle + i * step
            cap.add(Vec2(
                center.x + kotlin.math.cos(theta).toFloat() * radius,
                center.y + kotlin.math.sin(theta).toFloat() * radius
            ))
        }
        return cap
    }

    fun generateCumulativeChunks(
        points: List<StrokePoint>,
        settings: FreehandSettings,
        zoom: Float
    ): List<Path> {
        if (points.isEmpty()) return emptyList()
        val paths = mutableListOf<Path>()
        val chunkSize = 24
        val overlap = 1
        var start = 0
        while (start < points.size) {
            val end = (start + chunkSize).coerceAtMost(points.size)
            if (end - start < 2) {
                break
            }
            val chunkPoints = points.subList(start, end)
            
            val capStart = (start == 0) && settings.capStart
            val capEnd = (end == points.size) && settings.capEnd
            
            val chunkSettings = settings.copy(
                capStart = capStart,
                capEnd = capEnd,
                isComplete = (end == points.size)
            )
            
            val chunkPath = Path()
            generate(
                chunkPoints,
                chunkSettings,
                zoom,
                chunkPath
            )
            paths.add(chunkPath)
            
            if (end == points.size) {
                break
            }
            start = end - overlap
        }
        return paths
    }
}


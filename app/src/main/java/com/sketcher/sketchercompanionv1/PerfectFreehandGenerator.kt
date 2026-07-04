package com.sketcher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import com.sketcher.sketchercompanionv1.dto.FreehandSettings
import com.sketcher.sketchercompanionv1.StrokePoint

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.abs
import kotlin.math.hypot

object PerfectFreehandGenerator {

    // Constants from reference
    private const val MIN_STREAMLINE_T = 0.15f
    private const val STREAMLINE_T_RANGE = 0.85f
    private const val END_NOISE_THRESHOLD = 0.99f 
    private const val STOP = 0.001f 
    private const val PI = Math.PI.toFloat()
    private const val FIXED_PI = PI + 0.0001f 
    
    private const val START_CAP_SEGMENTS = 12
    private const val END_CAP_SEGMENTS = 12
    private const val CORNER_CAP_SEGMENTS = 12

    private val strokePointsLocal = ThreadLocal.withInitial { ArrayList<StrokePointInternal>() }
    private val leftPtsLocal = ThreadLocal.withInitial { ArrayList<Vec2>() }
    private val rightPtsLocal = ThreadLocal.withInitial { ArrayList<Vec2>() }
    private val velocitiesLocal = ThreadLocal.withInitial { ArrayList<Float>() }

    private val vec2PoolLocal = ThreadLocal.withInitial { Vec2Pool(5000) }
    
    class StrokePointInternal {
        val point: Vec2 = Vec2()
        var pressure: Float = 0f
        var distance: Float = 0f
        var normalizedVelocity: Float = 0f
        val vector: Vec2 = Vec2()
        var runningLength: Float = 0f
        
        fun set(p: Vec2, pr: Float, d: Float, nv: Float, v: Vec2, rl: Float): StrokePointInternal {
            point.set(p)
            pressure = pr
            distance = d
            normalizedVelocity = nv
            vector.set(v)
            runningLength = rl
            return this
        }
    }
    
    class StrokePointInternalPool(private val initialCapacity: Int = 2000) {
        private val pool = ArrayList<StrokePointInternal>(initialCapacity)
        private var index = 0
        init { for (i in 0 until initialCapacity) pool.add(StrokePointInternal()) }
        fun obtain(): StrokePointInternal {
            if (index >= pool.size) pool.add(StrokePointInternal())
            return pool[index++]
        }
        fun reset() { index = 0 }
    }
    private val spiPoolLocal = ThreadLocal.withInitial { StrokePointInternalPool() }

    private data class OutlineResult(
        val left: List<Vec2>,
        val right: List<Vec2>,
        val polygon: List<Vec2>,
        val lastRadius: Float
    )

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
        zoom: Float = 1.0f,
        outPath: Path = Path()
    ): FreehandResult {
        val path = outPath.apply { rewind() }
        val size = settings.size
        val isComplete = settings.isComplete
        
        if (rawPoints.isEmpty()) return FreehandResult(path, emptyList(), emptyList(), emptyList(), 0f)

        val pool = vec2PoolLocal.get()!!
        pool.reset()
        val spiPool = spiPoolLocal.get()!!
        spiPool.reset()

        // 1. Get Stroke Points
        val strokePoints = getStrokePoints(pool, spiPool, rawPoints, size, settings, isComplete, zoom)

        // 2. Get Outline Points
        val outline = getStrokeOutlinePoints(pool, strokePoints, size, settings)

        if (outline.polygon.size < 3) return FreehandResult(path, emptyList(), emptyList(), emptyList(), 0f)

        val polygonPoints = outline.polygon
        
        // Polygonal Construction
        if (settings.useCurveForPolygon) {
            val p0 = polygonPoints[0]
            val pLast = polygonPoints.last()
            
            path.moveTo((p0.x + pLast.x) / 2f, (p0.y + pLast.y) / 2f)
            for (i in polygonPoints.indices) {
                val curr = polygonPoints[i]
                val next = polygonPoints[(i + 1) % polygonPoints.size]
                path.quadTo(curr.x, curr.y, (curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
            }
        } else {
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

    private fun getStrokePoints(
        pool: Vec2Pool,
        spiPool: StrokePointInternalPool,
        input: List<StrokePoint>,
        size: Float,
        settings: FreehandSettings,
        isComplete: Boolean,
        zoom: Float = 1.0f
    ): List<StrokePointInternal> {
        if (input.isEmpty()) return emptyList()

        val streamline = settings.streamline
        val t = MIN_STREAMLINE_T + (1f - streamline) * STREAMLINE_T_RANGE
        val maxVel = max(0.1f, settings.velocityMaxInput)

        var pts = input.toMutableList()

        if (pts.size == 2) {
            val last = pts[1]
            pts = pts.subList(0, 1)
            for (i in 1 until 5) {
                val factor = i / 4f
                val lx = pts[0].x + (last.x - pts[0].x) * factor
                val ly = pts[0].y + (last.y - pts[0].y) * factor
                val lerpTime = (pts[0].timestamp + (last.timestamp - pts[0].timestamp) * factor).toLong()
                pts.add(StrokePoint(lx, ly, last.pressure, lerpTime))
            }
        }
        
        if (pts.size == 1) {
            val p = pts[0]
            pts.add(StrokePoint(p.x + 1f, p.y + 1f, p.pressure, p.timestamp))
        }

        val velocities = computeSmoothedVelocities(pts)

        val strokePoints = strokePointsLocal.get()!!.apply { clear() }
        
        val p0 = pts[0]
        strokePoints.add(spiPool.obtain().set(
            p = pool.obtain(p0.x, p0.y),
            pr = if (p0.pressure >= 0) p0.pressure else 0.5f,
            d = 0f,
            nv = 0f,
            v = pool.obtain(1f, 1f),
            rl = 0f
        ))
        
        var runningLength = 0f
        var prev = strokePoints[0]
        var hasReachedMinimumLength = false
        
        for (i in 1 until pts.size) {
            val rawP = pts[i]
            val isLastPoint = i == pts.size - 1
            
            val point = pool.obtain()
            if (isComplete && isLastPoint) {
                point.set(rawP.x, rawP.y)
            } else {
                PerfectFreehandUtils.lrp(prev.point, pool.obtain(rawP.x, rawP.y), t, point)
            }
            
            if (point.x == prev.point.x && point.y == prev.point.y) continue 
            
            val d = PerfectFreehandUtils.dist(point, prev.point)
            runningLength += d
            
            if (i < pts.size - 1 && !hasReachedMinimumLength) {
                if (runningLength < size) continue
                hasReachedMinimumLength = true
            }
            
            val vector = pool.obtain().set(point).sub(prev.point).uni()
            
            val velocity = velocities.getOrElse(i) { 0f } * zoom
            val normVel = min(1f, velocity / maxVel)

            val newPoint = spiPool.obtain().set(
                p = point,
                pr = if (rawP.pressure >= 0) rawP.pressure else 0.5f,
                d = d,
                nv = normVel,
                v = vector,
                rl = runningLength
            )
            
            strokePoints.add(newPoint)
            prev = newPoint
        }
        
        if (strokePoints.size > 1) {
            strokePoints[0].vector.set(strokePoints[1].vector)
        }
        
        return strokePoints
    }

    private fun computeSmoothedVelocities(points: List<StrokePoint>): List<Float> {
        val vels = velocitiesLocal.get().apply { clear() }
        var lastVel = 0f
        
        for (i in points.indices) {
            if (i == 0) {
                vels.add(0f)
                continue
            }
            
            val curr = points[i]
            val prev = points[i-1]
            
            val d = hypot(curr.x - prev.x, curr.y - prev.y)
            val dt = max(1L, curr.timestamp - prev.timestamp).toFloat()
            val instantVel = d / dt
            
            val alpha = 0.2f
            val smoothVel = lastVel * (1f - alpha) + instantVel * alpha
            
            vels.add(smoothVel)
            lastVel = smoothVel
        }
        
        return vels
    }

    private fun getStrokeOutlinePoints(
        pool: Vec2Pool,
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
        
        val easing: (Float) -> Float = { t -> t }

        val capStart = settings.capStart
        val capEnd = settings.capEnd
        
        val hermiteEase: (Float) -> Float = { t -> t * t * (3 - 2 * t) }

        val totalLength = points.last().runningLength
        val taperStart = settings.taperStart
        val taperEnd = settings.taperEnd

        val minDistance = (size * smoothing).pow(2)
        val minWidth = size * minWidthRatio
        
        val leftPts = leftPtsLocal.get().apply { clear() }
        val rightPts = rightPtsLocal.get().apply { clear() }
        
        var prevPressure = computeInitialPressure(points, simulatePressure, size)
        
        var baseRadius = PerfectFreehandUtils.getStrokeRadius(size, thinning, points.last().pressure, easing)
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

            if (thinning > 0) {
                 if (simulatePressure) {
                     pressure = PerfectFreehandUtils.simulatePressure(prevPressure, curr.distance, size)
                 }
                 radius = PerfectFreehandUtils.getStrokeRadius(size, thinning, pressure, easing)
            } else {
                 radius = size / 2f
            }
            
            if (velocityThinning > 0) {
                radius *= (1f - velocityThinning * curr.normalizedVelocity)
            }

            radius = max(minWidth / 2f, radius)

            if (firstRadius == null) firstRadius = radius
            
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
                    val tf = hermiteEase(distFromEnd / taperEnd)
                    radius *= tf
                }
            }
            
            radius = max(0.01f, radius)

            val nextVector = if (i < points.size - 1) points[i + 1].vector else points[i].vector
            val nextDpr = if (i < points.size - 1) PerfectFreehandUtils.dpr(curr.vector, nextVector) else 1.0f
            val prevDpr = PerfectFreehandUtils.dpr(curr.vector, prevVector)

            val isPointSharpCorner = prevDpr < 0 && !isPrevPointSharpCorner
            val isNextPointSharpCorner = nextDpr < 0

            if (isPointSharpCorner || isNextPointSharpCorner) {
                val offset = pool.obtain().set(prevVector).per().mul(radius)
                
                val step = 1f / CORNER_CAP_SEGMENTS
                for (k in 0..CORNER_CAP_SEGMENTS) {
                    val t = k * step
                    val tl = PerfectFreehandUtils.rotAround(pool.obtain().set(curr.point).sub(offset), curr.point, FIXED_PI * t, pool.obtain())
                    val tr = PerfectFreehandUtils.rotAround(pool.obtain().set(curr.point).add(offset), curr.point, FIXED_PI * -t, pool.obtain())
                    
                    leftPts.add(tl)
                    rightPts.add(tr)
                    
                    prevLeft = tl
                    prevRight = tr
                }
                
                if (isNextPointSharpCorner) isPrevPointSharpCorner = true
                continue
            }

            isPrevPointSharpCorner = false

            if (isLastPoint) {
                val offset = pool.obtain().set(curr.vector).per().mul(radius)
                leftPts.add(pool.obtain().set(curr.point).sub(offset))
                rightPts.add(pool.obtain().set(curr.point).add(offset))
                continue
            }

            val lrpVec = PerfectFreehandUtils.lrp(nextVector, curr.vector, nextDpr, pool.obtain())
            val offset = lrpVec.per().mul(radius)
            
            val tl = pool.obtain().set(curr.point).sub(offset)
            if (i <= 1 || PerfectFreehandUtils.dist2(prevLeft, tl) > minDistance) {
                leftPts.add(tl)
                prevLeft = tl
            }

            val tr = pool.obtain().set(curr.point).add(offset)
            if (i <= 1 || PerfectFreehandUtils.dist2(prevRight, tr) > minDistance) {
                rightPts.add(tr)
                prevRight = tr
            }

            prevPressure = pressure
            prevVector = curr.vector
        }
        
        val firstPoint = points[0].point
        val lastPoint = if (points.size > 1) points.last().point else pool.obtain().set(points[0].point).add(pool.obtain(1f, 1f))
        
        val startCap = ArrayList<Vec2>()
        val endCap = ArrayList<Vec2>()

        if (points.size == 1) {
            val r = firstRadius ?: radius
            val dot = drawDot(pool, firstPoint, r)
            return OutlineResult(dot, emptyList(), dot, r)
        }
        
        val endTaperingActive = taperEnd > 0f
        if (endTaperingActive) {
            endCap.add(lastPoint)
        } else if (capEnd) {
            val direction = points.last().vector
            endCap.addAll(drawRoundCap(pool, lastPoint, direction, radius, END_CAP_SEGMENTS))
        }

        val startTaperingActive = taperStart > 0f
        if (startTaperingActive) {
            startCap.add(firstPoint)
        } else if (capStart) {
            val direction = pool.obtain().set(points[0].vector).neg()
            startCap.addAll(drawRoundCap(pool, firstPoint, direction, firstRadius ?: radius, START_CAP_SEGMENTS))
        }

        val polygon = ArrayList<Vec2>()
        polygon.addAll(rightPts)
        polygon.addAll(endCap)
        polygon.addAll(leftPts.reversed())
        polygon.addAll(startCap)
        
        return OutlineResult(leftPts, rightPts, polygon, radius)
    }

    private fun computeTaperDistance(taper: Float, size: Float, totalLength: Float): Float {
        return taper
    }

    private fun computeInitialPressure(points: List<StrokePointInternal>, simulate: Boolean, size: Float): Float {
        if (points.isEmpty()) return 0.5f
        var acc = points[0].pressure
        val count = min(10, points.size)
        for (i in 0 until count) {
            var p = points[i].pressure
            if (simulate) {
                p = PerfectFreehandUtils.simulatePressure(acc, points[i].distance, size)
            }
            acc = (acc + p) / 2f
        }
        return acc
    }

    private fun drawDot(pool: Vec2Pool, center: Vec2, radius: Float): List<Vec2> {
        val offsetPoint = pool.obtain().set(center).add(pool.obtain(1f, 1f))
        val subVec = pool.obtain().set(center).sub(offsetPoint)
        val start = PerfectFreehandUtils.prj(center, subVec.per().uni(), -radius, pool.obtain())
        val dotPts = ArrayList<Vec2>()
        val step = 1f / START_CAP_SEGMENTS
        for (k in 1..START_CAP_SEGMENTS) {
             val t = k * step 
             dotPts.add(PerfectFreehandUtils.rotAround(start, center, FIXED_PI * 2 * t, pool.obtain()))
        }
        return dotPts
    }

    private fun drawRoundCap(pool: Vec2Pool, center: Vec2, outwardDirection: Vec2, radius: Float, segments: Int): List<Vec2> {
        val cap = ArrayList<Vec2>()
        val dir = pool.obtain().set(outwardDirection).uni()
        val centerAngle = kotlin.math.atan2(dir.y, dir.x)

        val startAngle = centerAngle + kotlin.math.PI / 2
        val step = -kotlin.math.PI / segments
        
        for (i in 1 until segments) { 
            val theta = startAngle + i * step
            cap.add(pool.obtain(
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
        val intersections = mutableListOf<Path>()
        val n = points.size
        if (n < 40) return intersections

        val chunkSize = 20
        val paths = mutableListOf<Path>()
        
        var start = 0
        while (start < n) {
            val end = (start + chunkSize).coerceAtMost(n)
            if (end - start < 2) {
                break
            }
            val chunkPoints = points.subList(start, end)
            val chunkPath = Path()
            generate(
                chunkPoints,
                settings.copy(isComplete = (end == n)),
                zoom,
                chunkPath
            )
            paths.add(chunkPath)
            if (end == n) {
                break
            }
            start = end - 1 
        }

        val numChunks = paths.size
        val bounds = Array(numChunks) { RectF() }
        for (i in 0 until numChunks) {
            paths[i].computeBounds(bounds[i], true)
        }

        for (i in 0 until numChunks - 3) {
            val boundsI = bounds[i]
            for (j in i + 3 until numChunks) {
                if (RectF.intersects(boundsI, bounds[j])) {
                    val pathI = paths[i]
                    val pathJ = paths[j]
                    
                    val intersect = Path()
                    if (intersect.op(pathI, pathJ, Path.Op.INTERSECT)) {
                        if (!intersect.isEmpty) {
                            intersections.add(intersect)
                        }
                    }
                }
            }
        }
        return intersections
    }
}

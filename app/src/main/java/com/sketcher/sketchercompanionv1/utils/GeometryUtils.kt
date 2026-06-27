package com.sketcher.sketchercompanionv1.utils

import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Path
import com.sketcher.sketchercompanionv1.StrokePoint
import com.sketcher.sketchercompanionv1.VectorStroke
import com.sketcher.sketchercompanionv1.dto.StrokeType
import kotlin.math.*

object GeometryUtils {

    /**
     * Finds the intersection point between two line segments AB and CD.
     * Returns null if they do not intersect or are parallel.
     */
    fun findLineLineIntersection(
        a: PointF, b: PointF, c: PointF, d: PointF
    ): PointF? {
        val dX1 = b.x - a.x
        val dY1 = b.y - a.y
        val dX2 = d.x - c.x
        val dY2 = d.y - c.y

        val det = dX1 * dY2 - dY1 * dX2
        if (abs(det) < 1e-5f) return null // Parallel

        val t = ((c.x - a.x) * dY2 - (c.y - a.y) * dX2) / det
        val u = ((c.x - a.x) * dY1 - (c.y - a.y) * dX1) / det

        return if (t in 0f..1f && u in 0f..1f) {
            PointF(a.x + t * dX1, a.y + t * dY1)
        } else {
            null
        }
    }

    /**
     * Finds intersections between line segment AB and Circle (center C, radius R).
     * Returns a list of 0, 1, or 2 intersection points.
     */
    fun findLineCircleIntersections(
        a: PointF, b: PointF, c: PointF, r: Float
    ): List<PointF> {
        val intersections = mutableListOf<PointF>()
        val dX = b.x - a.x
        val dY = b.y - a.y

        val uX = a.x - c.x
        val uY = a.y - c.y

        val quadA = dX * dX + dY * dY
        if (quadA < 1e-5f) return emptyList()

        val quadB = 2 * (uX * dX + uY * dY)
        val quadC = uX * uX + uY * uY - r * r

        val disc = quadB * quadB - 4 * quadA * quadC
        if (disc < 0) return emptyList()

        val discSqrt = sqrt(disc)
        val t1 = (-quadB + discSqrt) / (2 * quadA)
        val t2 = (-quadB - discSqrt) / (2 * quadA)

        if (t1 in 0f..1f) {
            intersections.add(PointF(a.x + t1 * dX, a.y + t1 * dY))
        }
        if (t2 in 0f..1f && abs(t1 - t2) > 1e-4f) {
            intersections.add(PointF(a.x + t2 * dX, a.y + t2 * dY))
        }

        return intersections
    }

    /**
     * Finds intersections between Circle 1 (c1, r1) and Circle 2 (c2, r2).
     * Returns a list of 0, 1, or 2 intersection points.
     */
    fun findCircleCircleIntersections(
        c1: PointF, r1: Float, c2: PointF, r2: Float
    ): List<PointF> {
        val dx = c2.x - c1.x
        val dy = c2.y - c1.y
        val d = hypot(dx, dy)

        if (d > r1 + r2 || d < abs(r1 - r2) || d < 1e-4f) {
            return emptyList()
        }

        val a = (r1 * r1 - r2 * r2 + d * d) / (2 * d)
        val hSq = r1 * r1 - a * a
        val h = if (hSq < 0f) 0f else sqrt(hSq)

        // Point P2 = P0 + a*(P1 - P0)/d
        val p2x = c1.x + a * dx / d
        val p2y = c1.y + a * dy / d

        if (h == 0f) {
            return listOf(PointF(p2x, p2y))
        }

        val rx = -dy * (h / d)
        val ry = dx * (h / d)

        return listOf(
            PointF(p2x + rx, p2y + ry),
            PointF(p2x - rx, p2y - ry)
        )
    }

    /**
     * Represents the geometric parameters of an Arc.
     */
    data class ArcParams(
        val center: PointF,
        val radius: Float,
        val startAngleDeg: Float,
        val sweepAngleDeg: Float
    )

    /**
     * Reconstructs an arc's center, radius, start angle, and sweep angle from 3 points.
     * Returns null if the points are collinear.
     */
    fun getArcParams(p1: PointF, p2: PointF, p3: PointF): ArcParams? {
        val x1 = p1.x; val y1 = p1.y
        val x2 = p2.x; val y2 = p2.y
        val x3 = p3.x; val y3 = p3.y

        val D = 2 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
        if (abs(D) < 0.001f) return null // Collinear points

        val Ux = ((x1 * x1 + y1 * y1) * (y2 - y3) + (x2 * x2 + y2 * y2) * (y3 - y1) + (x3 * x3 + y3 * y3) * (y1 - y2)) / D
        val Uy = ((x1 * x1 + y1 * y1) * (x3 - x2) + (x2 * x2 + y2 * y2) * (x1 - x3) + (x3 * x3 + y3 * y3) * (x2 - x1)) / D

        val center = PointF(Ux, Uy)
        val radius = hypot(x1 - Ux, y1 - Uy)

        val angle1 = atan2(y1 - Uy, x1 - Ux)
        val angle2 = atan2(y2 - Uy, x2 - Ux)
        val angle3 = atan2(y3 - Uy, x3 - Ux)

        val PI_F = PI.toFloat()
        var sweep = angle3 - angle1
        while (sweep < -PI_F) sweep += 2 * PI_F
        while (sweep > PI_F) sweep -= 2 * PI_F

        var diffMid = angle2 - angle1
        while (diffMid < -PI_F) diffMid += 2 * PI_F
        while (diffMid > PI_F) diffMid -= 2 * PI_F

        if (sign(diffMid) != sign(sweep)) {
            sweep = if (sweep > 0) sweep - (2 * PI).toFloat() else sweep + (2 * PI).toFloat()
        }

        return ArcParams(
            center = center,
            radius = radius,
            startAngleDeg = Math.toDegrees(angle1.toDouble()).toFloat(),
            sweepAngleDeg = Math.toDegrees(sweep.toDouble()).toFloat()
        )
    }

    /**
     * Determines if a point is close to an arc segment.
     */
    fun isPointNearArc(p: PointF, arc: ArcParams, tolerance: Float): Boolean {
        val dist = hypot(p.x - arc.center.x, p.y - arc.center.y)
        if (abs(dist - arc.radius) > tolerance) return false

        val angle = atan2(p.y - arc.center.y, p.x - arc.center.x)
        var angleDeg = Math.toDegrees(angle.toDouble()).toFloat()
        
        // Normalize angles to 0..360 range
        fun normalizeAngle(a: Float): Float {
            var norm = a % 360f
            if (norm < 0) norm += 360f
            return norm
        }

        val start = normalizeAngle(arc.startAngleDeg)
        val end = normalizeAngle(arc.startAngleDeg + arc.sweepAngleDeg)
        val target = normalizeAngle(angleDeg)

        return if (arc.sweepAngleDeg >= 0) {
            if (start <= end) {
                target in start..end
            } else {
                target >= start || target <= end
            }
        } else {
            // Negative sweep (clockwise)
            if (end <= start) {
                target in end..start
            } else {
                target >= end || target <= start
            }
        }
    }

    /**
     * Calculates the perpendicular distance from point P to line segment AB.
     */
    fun distanceToSegment(p: PointF, a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0f && dy == 0f) return hypot(p.x - a.x, p.y - a.y)

        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
        return if (t < 0f) {
            hypot(p.x - a.x, p.y - a.y)
        } else if (t > 1f) {
            hypot(p.x - b.x, p.y - b.y)
        } else {
            val projX = a.x + t * dx
            val projY = a.y + t * dy
            hypot(p.x - projX, p.y - projY)
        }
    }

    /**
     * Finds the closest point on line segment AB to point P.
     */
    fun closestPointOnSegment(p: PointF, a: PointF, b: PointF): PointF {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0f && dy == 0f) return PointF(a.x, a.y)

        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
        return if (t < 0f) {
            PointF(a.x, a.y)
        } else if (t > 1f) {
            PointF(b.x, b.y)
        } else {
            PointF(a.x + t * dx, a.y + t * dy)
        }
    }

    fun buildCenterlinePath(strokeType: StrokeType, points: List<StrokePoint>): Path {
        val path = Path()
        if (points.isEmpty()) return path
        when (strokeType) {
            StrokeType.LINE -> {
                if (points.size >= 2) {
                    path.moveTo(points.first().x, points.first().y)
                    path.lineTo(points.last().x, points.last().y)
                }
            }
            StrokeType.POLYLINE -> {
                if (points.isNotEmpty()) {
                    val unique = points.filterIndexed { index, curr ->
                        index == 0 || hypot(curr.x - points[index - 1].x, curr.y - points[index - 1].y) > 0.01f
                    }
                    if (unique.isNotEmpty()) {
                        path.moveTo(unique.first().x, unique.first().y)
                        for (i in 1 until unique.size) {
                            path.lineTo(unique[i].x, unique[i].y)
                        }
                    }
                }
            }
            StrokeType.CIRCLE -> {
                if (points.size >= 2) {
                    val center = points[0]
                    val edge = points[1]
                    val r = hypot(edge.x - center.x, edge.y - center.y)
                    path.addCircle(center.x, center.y, r, Path.Direction.CW)
                }
            }
            StrokeType.ARC -> {
                if (points.size >= 3) {
                    val p1 = PointF(points[0].x, points[0].y)
                    val p2 = PointF(points[1].x, points[1].y)
                    val p3 = PointF(points[2].x, points[2].y)
                    val arc = getArcParams(p1, p2, p3)
                    if (arc != null) {
                        val rect = RectF(
                            arc.center.x - arc.radius,
                            arc.center.y - arc.radius,
                            arc.center.x + arc.radius,
                            arc.center.y + arc.radius
                        )
                        path.arcTo(rect, arc.startAngleDeg, arc.sweepAngleDeg, true)
                    } else {
                        path.moveTo(p1.x, p1.y)
                        path.lineTo(p3.x, p3.y)
                    }
                } else if (points.size == 2) {
                    path.moveTo(points[0].x, points[0].y)
                    path.lineTo(points[1].x, points[1].y)
                }
            }
            StrokeType.ELLIPSE -> {
                if (points.size >= 3) {
                    val center = points[0]
                    val pX = points[1]
                    val pY = points[2]
                    val rX = hypot(pX.x - center.x, pX.y - center.y)
                    val rY = hypot(pY.x - center.x, pY.y - center.y)
                    val rect = RectF(center.x - rX, center.y - rY, center.x + rX, center.y + rY)
                    path.addOval(rect, Path.Direction.CW)
                } else if (points.size == 2) {
                    val center = points[0]
                    val edge = points[1]
                    val r = hypot(edge.x - center.x, edge.y - center.y)
                    path.addCircle(center.x, center.y, r, Path.Direction.CW)
                }
            }
            StrokeType.SPLINE -> {
                if (points.isNotEmpty()) {
                    val unique = points.filterIndexed { index, curr ->
                        index == 0 || hypot(curr.x - points[index - 1].x, curr.y - points[index - 1].y) > 0.01f
                    }
                    if (unique.isNotEmpty()) {
                        path.moveTo(unique[0].x, unique[0].y)
                        if (unique.size == 2) {
                            path.lineTo(unique[1].x, unique[1].y)
                        } else if (unique.size > 2) {
                            for (i in 0 until unique.size - 1) {
                                val p0 = unique[max(0, i - 1)]
                                val p1 = unique[i]
                                val p2 = unique[i + 1]
                                val p3 = unique[min(unique.size - 1, i + 2)]
                                val cp1x = p1.x + (p2.x - p0.x) / 6f
                                val cp1y = p1.y + (p2.y - p0.y) / 6f
                                val cp2x = p2.x - (p3.x - p1.x) / 6f
                                val cp2y = p2.y - (p3.y - p1.y) / 6f
                                path.cubicTo(cp1x, cp1y, cp2x, cp2y, p2.x, p2.y)
                            }
                        }
                    }
                }
            }
            else -> {}
        }
        return path
    }

    fun flattenPath(path: Path, step: Float = 5f): List<PointF> {
        val pm = android.graphics.PathMeasure(path, false)
        val length = pm.length
        val points = mutableListOf<PointF>()
        val coords = floatArrayOf(0f, 0f)
        var distance = 0f
        while (distance < length) {
            pm.getPosTan(distance, coords, null)
            points.add(PointF(coords[0], coords[1]))
            distance += step
        }
        pm.getPosTan(length, coords, null)
        points.add(PointF(coords[0], coords[1]))
        return points
    }

    fun trimStroke(
        target: VectorStroke,
        allStrokesInLayer: List<VectorStroke>,
        tapX: Float,
        tapY: Float
    ): List<VectorStroke>? {
        val intersections = mutableListOf<PointF>()
        for (other in allStrokesInLayer) {
            if (other === target) continue
            val strokeIntersections = findStrokeIntersections(target, other)
            intersections.addAll(strokeIntersections)
        }

        if (intersections.isEmpty()) return null

        val tap = PointF(tapX, tapY)

        when (target.strokeType) {
            StrokeType.LINE -> {
                val p1 = PointF(target.points.first().x, target.points.first().y)
                val p2 = PointF(target.points.last().x, target.points.last().y)

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val lenSq = dx * dx + dy * dy
                if (lenSq < 1e-4f) return null

                val tList = mutableListOf<Float>()
                for (ip in intersections) {
                    val t = ((ip.x - p1.x) * dx + (ip.y - p1.y) * dy) / lenSq
                    if (t in 0.01f..0.99f) {
                        tList.add(t)
                    }
                }
                tList.sort()

                val splits = mutableListOf(0f)
                splits.addAll(tList)
                splits.add(1f)

                val tapT = (((tap.x - p1.x) * dx + (tap.y - p1.y) * dy) / lenSq).coerceIn(0f, 1f)

                var tapSegmentIdx = -1
                for (i in 0 until splits.size - 1) {
                    if (tapT >= splits[i] && tapT <= splits[i + 1]) {
                        tapSegmentIdx = i
                        break
                    }
                }
                if (tapSegmentIdx == -1) return null

                val remainingStrokes = mutableListOf<VectorStroke>()
                for (i in 0 until splits.size - 1) {
                    if (i == tapSegmentIdx) continue
                    val tStart = splits[i]
                    val tEnd = splits[i + 1]

                    val sp1 = StrokePoint(p1.x + tStart * dx, p1.y + tStart * dy, target.points.first().pressure)
                    val sp2 = StrokePoint(p1.x + tEnd * dx, p1.y + tEnd * dy, target.points.last().pressure)

                    val newStrokePoints = listOf(sp1, sp2)
                    val newPath = buildCenterlinePath(StrokeType.LINE, newStrokePoints)

                    remainingStrokes.add(
                        target.copy(
                            points = newStrokePoints,
                            path = newPath,
                            fillPath = null
                        )
                    )
                }
                return remainingStrokes
            }
            StrokeType.CIRCLE -> {
                val center = PointF(target.points[0].x, target.points[0].y)
                val radius = hypot(target.points[1].x - center.x, target.points[1].y - center.y)

                val angles = mutableListOf<Float>()
                for (ip in intersections) {
                    val dist = hypot(ip.x - center.x, ip.y - center.y)
                    if (abs(dist - radius) < 5f) {
                        var angle = Math.toDegrees(atan2(ip.y - center.y, ip.x - center.x).toDouble()).toFloat()
                        if (angle < 0) angle += 360f
                        angles.add(angle)
                    }
                }
                if (angles.isEmpty()) return null
                angles.sort()

                val segments = mutableListOf<Pair<Float, Float>>()
                for (i in 0 until angles.size - 1) {
                    segments.add(Pair(angles[i], angles[i + 1]))
                }
                segments.add(Pair(angles.last(), angles.first()))

                var tapAngle = Math.toDegrees(atan2(tap.y - center.y, tap.x - center.x).toDouble()).toFloat()
                if (tapAngle < 0) tapAngle += 360f

                var tapSegmentIdx = -1
                for (i in segments.indices) {
                    val seg = segments[i]
                    val s = seg.first
                    val e = seg.second
                    val contains = if (s < e) {
                        tapAngle in s..e
                    } else {
                        tapAngle >= s || tapAngle <= e
                    }
                    if (contains) {
                        tapSegmentIdx = i
                        break
                    }
                }
                if (tapSegmentIdx == -1) return null

                val remainingStrokes = mutableListOf<VectorStroke>()
                for (i in segments.indices) {
                    if (i == tapSegmentIdx) continue
                    val seg = segments[i]
                    val startAngle = seg.first
                    val endAngle = seg.second

                    var sweep = endAngle - startAngle
                    if (sweep < 0) sweep += 360f
                    if (sweep > 360f) sweep -= 360f

                    val startRad = Math.toRadians(startAngle.toDouble())
                    val midRad = Math.toRadians((startAngle + sweep / 2).toDouble())
                    val endRad = Math.toRadians(endAngle.toDouble())

                    val pArc1 = StrokePoint(center.x + radius * cos(startRad).toFloat(), center.y + radius * sin(startRad).toFloat(), 1f)
                    val pArc2 = StrokePoint(center.x + radius * cos(midRad).toFloat(), center.y + radius * sin(midRad).toFloat(), 1f)
                    val pArc3 = StrokePoint(center.x + radius * cos(endRad).toFloat(), center.y + radius * sin(endRad).toFloat(), 1f)

                    val newPoints = listOf(pArc1, pArc2, pArc3)
                    val newPath = buildCenterlinePath(StrokeType.ARC, newPoints)

                    remainingStrokes.add(
                        target.copy(
                            points = newPoints,
                            strokeType = StrokeType.ARC,
                            path = newPath,
                            fillPath = null
                        )
                    )
                }
                return remainingStrokes
            }
            else -> return null
        }
    }

    fun extendStroke(
        target: VectorStroke,
        allStrokesInLayer: List<VectorStroke>,
        tapX: Float,
        tapY: Float
    ): VectorStroke? {
        if (target.strokeType != StrokeType.LINE) return null
        val pts = target.points
        if (pts.size < 2) return null

        val p1 = PointF(pts.first().x, pts.first().y)
        val p2 = PointF(pts.last().x, pts.last().y)

        val distToStart = hypot(tapX - p1.x, tapY - p1.y)
        val distToEnd = hypot(tapX - p2.x, tapY - p2.y)

        val (extStart, extEnd) = if (distToStart < distToEnd) {
            Pair(p2, p1)
        } else {
            Pair(p1, p2)
        }

        val dx = extEnd.x - extStart.x
        val dy = extEnd.y - extStart.y
        val len = hypot(dx, dy)
        if (len < 1e-4f) return null

        val ux = dx / len
        val uy = dy / len

        val rayStart = extEnd
        val rayEnd = PointF(extEnd.x + ux * 10000f, extEnd.y + uy * 10000f)

        val rayStroke = VectorStroke(
            points = listOf(StrokePoint(rayStart.x, rayStart.y, 1f), StrokePoint(rayEnd.x, rayEnd.y, 1f)),
            strokeColor = 0,
            maxWidth = 1f,
            path = android.graphics.Path().apply {
                moveTo(rayStart.x, rayStart.y)
                lineTo(rayEnd.x, rayEnd.y)
            },
            strokeType = StrokeType.LINE,
            isCadGeometry = true
        )

        val intersections = mutableListOf<PointF>()
        for (other in allStrokesInLayer) {
            if (other === target) continue
            val strokeIntersections = findStrokeIntersections(rayStroke, other)
            intersections.addAll(strokeIntersections)
        }

        if (intersections.isEmpty()) return null

        var closestIntersection: PointF? = null
        var minDistance = Float.MAX_VALUE
        for (ip in intersections) {
            val d = hypot(ip.x - rayStart.x, ip.y - rayStart.y)
            if (d > 1f && d < minDistance) {
                minDistance = d
                closestIntersection = ip
            }
        }

        if (closestIntersection == null) return null

        val newPoints = if (distToStart < distToEnd) {
            listOf(
                StrokePoint(closestIntersection.x, closestIntersection.y, pts.first().pressure),
                pts.last()
            )
        } else {
            listOf(
                pts.first(),
                StrokePoint(closestIntersection.x, closestIntersection.y, pts.last().pressure)
            )
        }

        val newPath = buildCenterlinePath(StrokeType.LINE, newPoints)
        return target.copy(
            points = newPoints,
            path = newPath,
            fillPath = null
        )
    }

    private fun findStrokeIntersections(s1: VectorStroke, s2: VectorStroke): List<PointF> {
        val results = mutableListOf<PointF>()
        val segs1 = getSegments(s1)
        val segs2 = getSegments(s2)

        for (seg1 in segs1) {
            for (seg2 in segs2) {
                val p = findLineLineIntersection(seg1.first, seg1.second, seg2.first, seg2.second)
                if (p != null) results.add(p)
            }
        }

        val isCircle1 = s1.strokeType == StrokeType.CIRCLE || s1.strokeType == StrokeType.ARC
        val isCircle2 = s2.strokeType == StrokeType.CIRCLE || s2.strokeType == StrokeType.ARC

        if (isCircle1 || isCircle2) {
            val c1Params = getCircleParams(s1)
            val c2Params = getCircleParams(s2)

            if (isCircle1 && !isCircle2) {
                if (c1Params != null) {
                    for (seg in segs2) {
                        val pts = findLineCircleIntersections(seg.first, seg.second, c1Params.first, c1Params.second)
                        for (pt in pts) {
                            if (s1.strokeType != StrokeType.ARC || isPointNearArc(pt, getArcParams(PointF(s1.points[0].x, s1.points[0].y), PointF(s1.points[1].x, s1.points[1].y), PointF(s1.points[2].x, s1.points[2].y))!!, 1.0f)) {
                                results.add(pt)
                            }
                        }
                    }
                }
            } else if (!isCircle1 && isCircle2) {
                if (c2Params != null) {
                    for (seg in segs1) {
                        val pts = findLineCircleIntersections(seg.first, seg.second, c2Params.first, c2Params.second)
                        for (pt in pts) {
                            if (s2.strokeType != StrokeType.ARC || isPointNearArc(pt, getArcParams(PointF(s2.points[0].x, s2.points[0].y), PointF(s2.points[1].x, s2.points[1].y), PointF(s2.points[2].x, s2.points[2].y))!!, 1.0f)) {
                                results.add(pt)
                            }
                        }
                    }
                }
            } else {
                if (c1Params != null && c2Params != null) {
                    val pts = findCircleCircleIntersections(c1Params.first, c1Params.second, c2Params.first, c2Params.second)
                    for (pt in pts) {
                        val onArc1 = s1.strokeType != StrokeType.ARC || isPointNearArc(pt, getArcParams(PointF(s1.points[0].x, s1.points[0].y), PointF(s1.points[1].x, s1.points[1].y), PointF(s1.points[2].x, s1.points[2].y))!!, 1.0f)
                        val onArc2 = s2.strokeType != StrokeType.ARC || isPointNearArc(pt, getArcParams(PointF(s2.points[0].x, s2.points[0].y), PointF(s2.points[1].x, s2.points[1].y), PointF(s2.points[2].x, s2.points[2].y))!!, 1.0f)
                        if (onArc1 && onArc2) {
                            results.add(pt)
                        }
                    }
                }
            }
        }
        return results
    }

    private fun getSegments(stroke: VectorStroke): List<Pair<PointF, PointF>> {
        val segments = mutableListOf<Pair<PointF, PointF>>()
        val pts = stroke.points
        if (pts.size < 2) return emptyList()
        if (stroke.strokeType == StrokeType.LINE || stroke.strokeType == StrokeType.POLYLINE || stroke.strokeType == StrokeType.FREEHAND) {
            for (i in 0 until pts.size - 1) {
                segments.add(Pair(PointF(pts[i].x, pts[i].y), PointF(pts[i + 1].x, pts[i + 1].y)))
            }
        }
        return segments
    }

    private fun getCircleParams(stroke: VectorStroke): Pair<PointF, Float>? {
        val pts = stroke.points
        if (pts.isEmpty()) return null
        return when (stroke.strokeType) {
            StrokeType.CIRCLE -> {
                if (pts.size >= 2) {
                    val center = PointF(pts[0].x, pts[0].y)
                    val r = hypot(pts[1].x - center.x, pts[1].y - center.y)
                    Pair(center, r)
                } else null
            }
            StrokeType.ARC -> {
                if (pts.size >= 3) {
                    val p1 = PointF(pts[0].x, pts[0].y)
                    val p2 = PointF(pts[1].x, pts[1].y)
                    val p3 = PointF(pts[2].x, pts[2].y)
                    val arc = getArcParams(p1, p2, p3)
                    if (arc != null) Pair(arc.center, arc.radius) else null
                } else null
            }
            else -> null
        }
    }
}

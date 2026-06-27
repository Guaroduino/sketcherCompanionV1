package com.sketcher.sketchercompanionv1.managers

import android.graphics.PointF
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.LayerElement
import com.sketcher.sketchercompanionv1.VectorStroke
import com.sketcher.sketchercompanionv1.StrokePoint
import com.sketcher.sketchercompanionv1.dto.StrokeType
import com.sketcher.sketchercompanionv1.utils.GeometryUtils
import kotlin.math.hypot

enum class SnapType {
    ENDPOINT,
    MIDPOINT,
    INTERSECTION,
    CENTER
}

data class SnapPoint(
    val point: PointF,
    val type: SnapType
)

object SnapEngine {

    /**
     * Extracts all snapping points from the visible layers.
     */
    fun getSnapPoints(layers: List<Layer>): List<SnapPoint> {
        val snapPoints = mutableListOf<SnapPoint>()
        val visibleStrokes = layers.filter { it.isVisible }
            .flatMap { it.elements }
            .filterIsInstance<VectorStroke>()

        // 1. Extract Endpoints, Midpoints, and Centers
        for (stroke in visibleStrokes) {
            val pts = stroke.points
            if (pts.isEmpty()) continue

            when (stroke.strokeType) {
                StrokeType.LINE -> {
                    if (pts.size >= 2) {
                        val p1 = PointF(pts.first().x, pts.first().y)
                        val p2 = PointF(pts.last().x, pts.last().y)
                        snapPoints.add(SnapPoint(p1, SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(p2, SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(PointF((p1.x + p2.x) / 2, (p1.y + p2.y) / 2), SnapType.MIDPOINT))
                    }
                }
                StrokeType.POLYLINE, StrokeType.FREEHAND, StrokeType.PEN -> {
                    // For polylines/freehand, endpoints of the entire stroke
                    val pStart = PointF(pts.first().x, pts.first().y)
                    val pEnd = PointF(pts.last().x, pts.last().y)
                    snapPoints.add(SnapPoint(pStart, SnapType.ENDPOINT))
                    snapPoints.add(SnapPoint(pEnd, SnapType.ENDPOINT))

                    // Intermediate vertices are also endpoints
                    for (i in 1 until pts.size - 1) {
                        snapPoints.add(SnapPoint(PointF(pts[i].x, pts[i].y), SnapType.ENDPOINT))
                    }

                    // Midpoints of segments
                    for (i in 0 until pts.size - 1) {
                        val p1 = pts[i]
                        val p2 = pts[i + 1]
                        snapPoints.add(SnapPoint(PointF((p1.x + p2.x) / 2, (p1.y + p2.y) / 2), SnapType.MIDPOINT))
                    }
                }
                StrokeType.CIRCLE -> {
                    if (pts.size >= 2) {
                        val center = PointF(pts[0].x, pts[0].y)
                        val radius = hypot(pts[1].x - center.x, pts[1].y - center.y)
                        snapPoints.add(SnapPoint(center, SnapType.CENTER))

                        // Quadrants as endpoints
                        snapPoints.add(SnapPoint(PointF(center.x - radius, center.y), SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(PointF(center.x + radius, center.y), SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(PointF(center.x, center.y - radius), SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(PointF(center.x, center.y + radius), SnapType.ENDPOINT))
                    }
                }
                StrokeType.ARC -> {
                    if (pts.size >= 3) {
                        val p1 = PointF(pts[0].x, pts[0].y)
                        val p2 = PointF(pts[1].x, pts[1].y)
                        val p3 = PointF(pts[2].x, pts[2].y)

                        snapPoints.add(SnapPoint(p1, SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(p3, SnapType.ENDPOINT))

                        val arcParams = GeometryUtils.getArcParams(p1, p2, p3)
                        if (arcParams != null) {
                            snapPoints.add(SnapPoint(arcParams.center, SnapType.CENTER))

                            // Midpoint along the arc
                            val midAngleRad = Math.toRadians((arcParams.startAngleDeg + arcParams.sweepAngleDeg / 2).toDouble())
                            val midX = arcParams.center.x + arcParams.radius * Math.cos(midAngleRad).toFloat()
                            val midY = arcParams.center.y + arcParams.radius * Math.sin(midAngleRad).toFloat()
                            snapPoints.add(SnapPoint(PointF(midX, midY), SnapType.MIDPOINT))
                        }
                    }
                }
                StrokeType.ELLIPSE -> {
                    if (pts.size >= 3) {
                        val center = PointF(pts[0].x, pts[0].y)
                        snapPoints.add(SnapPoint(center, SnapType.CENTER))

                        val rX = hypot(pts[1].x - center.x, pts[1].y - center.y)
                        val rY = hypot(pts[2].x - center.x, pts[2].y - center.y)

                        // Axis ends
                        snapPoints.add(SnapPoint(PointF(center.x - rX, center.y), SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(PointF(center.x + rX, center.y), SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(PointF(center.x, center.y - rY), SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(PointF(center.x, center.y + rY), SnapType.ENDPOINT))
                    }
                }
                StrokeType.SPLINE -> {
                    if (pts.isNotEmpty()) {
                        val pStart = PointF(pts.first().x, pts.first().y)
                        val pEnd = PointF(pts.last().x, pts.last().y)
                        snapPoints.add(SnapPoint(pStart, SnapType.ENDPOINT))
                        snapPoints.add(SnapPoint(pEnd, SnapType.ENDPOINT))
                        for (i in 1 until pts.size - 1) {
                            snapPoints.add(SnapPoint(PointF(pts[i].x, pts[i].y), SnapType.ENDPOINT))
                        }
                    }
                }
                StrokeType.BEZIER -> {
                    if (pts.isNotEmpty()) {
                        val numNodes = (pts.size + 2) / 3
                        for (i in 0 until numNodes) {
                            val idx = 3 * i
                            if (idx < pts.size) {
                                val anchor = pts[idx]
                                snapPoints.add(SnapPoint(PointF(anchor.x, anchor.y), SnapType.ENDPOINT))
                            }
                        }
                    }
                }
            }
        }

        // 2. Compute Intersections
        for (i in visibleStrokes.indices) {
            for (j in i + 1 until visibleStrokes.size) {
                val s1 = visibleStrokes[i]
                val s2 = visibleStrokes[j]
                
                // Simple Bounding Box check first
                val b1 = s1.getBoundingBox()
                val b2 = s2.getBoundingBox()
                if (!android.graphics.RectF.intersects(b1, b2)) continue

                val intersects = findStrokeIntersections(s1, s2)
                for (pt in intersects) {
                    snapPoints.add(SnapPoint(pt, SnapType.INTERSECTION))
                }
            }
        }

        return snapPoints
    }

    /**
     * Resolves snapping against a list of SnapPoints.
     * Returns the closest SnapPoint if within threshold, otherwise null.
     */
    fun resolveSnap(
        worldX: Float,
        worldY: Float,
        snapPoints: List<SnapPoint>,
        zoom: Float,
        snapRadiusDp: Float = 16f
    ): SnapPoint? {
        if (snapPoints.isEmpty()) return null

        // Convert threshold from Dp to World coordinates
        val thresholdWorld = (snapRadiusDp / zoom)
        val thresholdWorldSq = thresholdWorld * thresholdWorld

        var closestPoint: SnapPoint? = null
        var minDistanceSq = Float.MAX_VALUE

        for (sp in snapPoints) {
            val dx = worldX - sp.point.x
            val dy = worldY - sp.point.y
            val distSq = dx * dx + dy * dy
            if (distSq < thresholdWorldSq && distSq < minDistanceSq) {
                minDistanceSq = distSq
                closestPoint = sp
            }
        }

        return closestPoint
    }

    private fun findStrokeIntersections(s1: VectorStroke, s2: VectorStroke): List<PointF> {
        val results = mutableListOf<PointF>()

        // 1. Line / Polyline segments
        val segs1 = getSegments(s1)
        val segs2 = getSegments(s2)

        // Segment-Segment intersections
        for (seg1 in segs1) {
            for (seg2 in segs2) {
                val p = GeometryUtils.findLineLineIntersection(seg1.first, seg1.second, seg2.first, seg2.second)
                if (p != null) results.add(p)
            }
        }

        // 2. Handle Circle / Arc geometries
        val isCircle1 = s1.strokeType == StrokeType.CIRCLE || s1.strokeType == StrokeType.ARC
        val isCircle2 = s2.strokeType == StrokeType.CIRCLE || s2.strokeType == StrokeType.ARC

        if (isCircle1 || isCircle2) {
            val c1Params = getCircleParams(s1)
            val c2Params = getCircleParams(s2)

            if (isCircle1 && !isCircle2) {
                // Circle-Line intersections
                if (c1Params != null) {
                    for (seg in segs2) {
                        val pts = GeometryUtils.findLineCircleIntersections(seg.first, seg.second, c1Params.first, c1Params.second)
                        for (pt in pts) {
                            if (s1.strokeType != StrokeType.ARC || GeometryUtils.isPointNearArc(pt, GeometryUtils.getArcParams(PointF(s1.points[0].x, s1.points[0].y), PointF(s1.points[1].x, s1.points[1].y), PointF(s1.points[2].x, s1.points[2].y))!!, 1.0f)) {
                                results.add(pt)
                            }
                        }
                    }
                }
            } else if (!isCircle1 && isCircle2) {
                // Line-Circle intersections
                if (c2Params != null) {
                    for (seg in segs1) {
                        val pts = GeometryUtils.findLineCircleIntersections(seg.first, seg.second, c2Params.first, c2Params.second)
                        for (pt in pts) {
                            if (s2.strokeType != StrokeType.ARC || GeometryUtils.isPointNearArc(pt, GeometryUtils.getArcParams(PointF(s2.points[0].x, s2.points[0].y), PointF(s2.points[1].x, s2.points[1].y), PointF(s2.points[2].x, s2.points[2].y))!!, 1.0f)) {
                                results.add(pt)
                            }
                        }
                    }
                }
            } else {
                // Circle-Circle intersections
                if (c1Params != null && c2Params != null) {
                    val pts = GeometryUtils.findCircleCircleIntersections(c1Params.first, c1Params.second, c2Params.first, c2Params.second)
                    for (pt in pts) {
                        val onArc1 = s1.strokeType != StrokeType.ARC || GeometryUtils.isPointNearArc(pt, GeometryUtils.getArcParams(PointF(s1.points[0].x, s1.points[0].y), PointF(s1.points[1].x, s1.points[1].y), PointF(s1.points[2].x, s1.points[2].y))!!, 1.0f)
                        val onArc2 = s2.strokeType != StrokeType.ARC || GeometryUtils.isPointNearArc(pt, GeometryUtils.getArcParams(PointF(s2.points[0].x, s2.points[0].y), PointF(s2.points[1].x, s2.points[1].y), PointF(s2.points[2].x, s2.points[2].y))!!, 1.0f)
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

        if (stroke.strokeType == StrokeType.LINE || stroke.strokeType == StrokeType.POLYLINE || stroke.strokeType == StrokeType.FREEHAND || stroke.strokeType == StrokeType.PEN) {
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
                    val arc = GeometryUtils.getArcParams(p1, p2, p3)
                    if (arc != null) Pair(arc.center, arc.radius) else null
                } else null
            }
            else -> null
        }
    }
}

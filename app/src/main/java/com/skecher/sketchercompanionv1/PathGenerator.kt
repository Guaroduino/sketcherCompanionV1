package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.sqrt

object PathGenerator {
    /**
     * Calculate a control point for a rounded cap.
     * Projects outwards from the given point along the tangent direction.
     * 
     * @param point The stroke point at the cap
     * @param tangent Normalized tangent direction (should be unit vector)
     * @param width The half-width of the stroke at this point
     * @return Control point for quadratic Bézier curve
     */
    private fun getCapControlPoint(point: StrokePoint, tangent: PointF, width: Float): PointF {
        val projectedX = point.x + (tangent.x * width)
        val projectedY = point.y + (tangent.y * width)
        return PointF(projectedX, projectedY)
    }
    
    fun generateStrokePath(points: List<StrokePoint>, maxWidth: Float, minSizeFactor: Float = 0.0f): Triple<Path, List<android.graphics.PointF>, List<android.graphics.PointF>> {
        val path = Path()
        
        // Internal helper to track if a point is a Bezier Anchor
        class EdgePt(val x: Float, val y: Float, val isAnchor: Boolean = false)
        
        val leftEdge = mutableListOf<EdgePt>()
        val rightEdge = mutableListOf<EdgePt>()
        
        if (points.size < 2) return Triple(path, emptyList(), emptyList())
        if (points.size < 3) return generateLinearPathWithPoints(points, maxWidth, minSizeFactor)

        fun getHW(p: StrokePoint): Float = (maxWidth * (minSizeFactor + (1.0f - minSizeFactor) * p.pressure)) / 2f
        
        fun getNormal(p1: StrokePoint, p2: StrokePoint): Pair<Float, Float> {
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val length = sqrt(dx * dx + dy * dy)
            // Return LEFT normal for screen coordinates: (dy/length, -dx/length)
            return if (length > 0) Pair(dy / length, -dx / length) else Pair(0f, 0f)
        }
        
        fun crossProduct(ax: Float, ay: Float, bx: Float, by: Float): Float = ax * by - ay * bx

        // --- 1. START CAP (Rounded) ---
        val nStart = getNormal(points[0], points[1])
        val hw0 = getHW(points[0])
        
        // Calculate tangent at start (pointing backwards from first segment)
        val dx0 = points[1].x - points[0].x
        val dy0 = points[1].y - points[0].y
        val len0 = sqrt(dx0 * dx0 + dy0 * dy0)
        val tangentStart = if (len0 > 0) PointF(-dx0 / len0, -dy0 / len0) else PointF(0f, 0f)
        
        // Start cap control point (projects backwards)
        val startCapControl = getCapControlPoint(points[0], tangentStart, hw0)
        
        // Add start cap edges
        leftEdge.add(EdgePt(points[0].x + nStart.first * hw0, points[0].y + nStart.second * hw0))
        rightEdge.add(EdgePt(points[0].x - nStart.first * hw0, points[0].y - nStart.second * hw0))
        
        // Mark the control point for the start cap (will be used during path construction)
        val startCapControlPt = EdgePt(startCapControl.x, startCapControl.y, isAnchor = true)

        // --- 2. INTERNAL VERTICES (BEVEL INNER, ROUND OUTER) ---
        for (i in 1 until points.size - 1) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val pNext = points[i + 1]
            
            // Safety check: Skip zero-length segments to prevent NaN normals
            val dx1 = pCurr.x - pPrev.x
            val dy1 = pCurr.y - pPrev.y
            val dx2 = pNext.x - pCurr.x
            val dy2 = pNext.y - pCurr.y
            val len1 = sqrt(dx1 * dx1 + dy1 * dy1)
            val len2 = sqrt(dx2 * dx2 + dy2 * dy2)
            
            if (len1 < 0.001f || len2 < 0.001f) {
                continue // Skip degenerate segments
            }
            
            val nIn = getNormal(pPrev, pCurr)
            val nOut = getNormal(pCurr, pNext)
            val hw = getHW(pCurr)

            // Calculate Cross Product (detects turn direction: left/right)
            val cross = nIn.first * nOut.second - nIn.second * nOut.first
            
            // Calculate Dot Product (detects sharp turns: angles > 90°)
            val dot = nIn.first * nOut.first + nIn.second * nOut.second
            
            // Define sharp turn: dot < 0 means angle > 90 degrees
            val isSharpTurn = dot < 0.0f

            // Calculate offset points (NEVER add center skeleton point pCurr!)
            val lIn = EdgePt(pCurr.x + nIn.first * hw, pCurr.y + nIn.second * hw)
            val lOut = EdgePt(pCurr.x + nOut.first * hw, pCurr.y + nOut.second * hw)
            val rIn = EdgePt(pCurr.x - nIn.first * hw, pCurr.y - nIn.second * hw)
            val rOut = EdgePt(pCurr.x - nOut.first * hw, pCurr.y - nOut.second * hw)

            // REFACTORED LOGIC: Bevel for inner, Round for outer
            if (cross > 0.001f || (isSharpTurn && cross >= 0)) {
                // TURNING LEFT
                // Left Side (Inner): Bevel Join - direct connection between offset points
                leftEdge.add(lIn)
                leftEdge.add(lOut)
                
                // Right Side (Outer): Round Join - use midpoint of offset points as control
                rightEdge.add(rIn)
                val rControl = EdgePt((rIn.x + rOut.x) / 2f, (rIn.y + rOut.y) / 2f, isAnchor = true)
                rightEdge.add(rControl)
                rightEdge.add(rOut)

            } else if (cross < -0.001f || isSharpTurn) {
                // TURNING RIGHT (includes 180° flips)
                // Left Side (Outer): Round Join - use midpoint of offset points as control
                leftEdge.add(lIn)
                val lControl = EdgePt((lIn.x + lOut.x) / 2f, (lIn.y + lOut.y) / 2f, isAnchor = true)
                leftEdge.add(lControl)
                leftEdge.add(lOut)
                
                // Right Side (Inner): Bevel Join - direct connection between offset points
                rightEdge.add(rIn)
                rightEdge.add(rOut)

            } else {
                // STRAIGHT LINE (only when vectors are nearly aligned)
                leftEdge.add(lIn)
                rightEdge.add(rIn)
            }
        }

        // --- 3. END CAP (Rounded) ---
        val nEnd = getNormal(points[points.size - 2], points.last())
        val hwEnd = getHW(points.last())
        
        // Calculate tangent at end (pointing forwards from last segment)
        val dxEnd = points.last().x - points[points.size - 2].x
        val dyEnd = points.last().y - points[points.size - 2].y
        val lenEnd = sqrt(dxEnd * dxEnd + dyEnd * dyEnd)
        val tangentEnd = if (lenEnd > 0) PointF(dxEnd / lenEnd, dyEnd / lenEnd) else PointF(0f, 0f)
        
        // End cap control point (projects forwards)
        val endCapControl = getCapControlPoint(points.last(), tangentEnd, hwEnd)
        
        // Add end cap edges
        val lEnd = EdgePt(points.last().x + nEnd.first * hwEnd, points.last().y + nEnd.second * hwEnd)
        val rEnd = EdgePt(points.last().x - nEnd.first * hwEnd, points.last().y - nEnd.second * hwEnd)
        leftEdge.add(lEnd)
        rightEdge.add(rEnd)
        
        // Mark the control point for the end cap
        val endCapControlPt = EdgePt(endCapControl.x, endCapControl.y, isAnchor = true)

        // --- 4. CONSTRUCT PATH WITH ROUNDED CAPS ---
        
        // Start from right edge first point
        path.moveTo(rightEdge[0].x, rightEdge[0].y)
        
        // Draw START CAP (rounded): right edge -> control point -> left edge
        path.quadTo(startCapControlPt.x, startCapControlPt.y, leftEdge[0].x, leftEdge[0].y)
        
        // Draw LEFT EDGE (forward)
        var k = 1
        while (k < leftEdge.size) {
            val p = leftEdge[k]
            if (p.isAnchor && k + 1 < leftEdge.size) {
                path.quadTo(p.x, p.y, leftEdge[k + 1].x, leftEdge[k + 1].y)
                k += 2
            } else {
                path.lineTo(p.x, p.y)
                k++
            }
        }
        
        // Draw END CAP (rounded): left edge -> control point -> right edge
        path.quadTo(endCapControlPt.x, endCapControlPt.y, rightEdge.last().x, rightEdge.last().y)
        
        // Draw RIGHT EDGE (backwards)
        k = rightEdge.size - 2
        while (k >= 0) {
            val p = rightEdge[k]
            if (p.isAnchor && k - 1 >= 0) {
                path.quadTo(p.x, p.y, rightEdge[k - 1].x, rightEdge[k - 1].y)
                k -= 2
            } else {
                path.lineTo(p.x, p.y)
                k--
            }
        }
        
        path.close()

        // Convert EdgePt back to PointF for the result lists (Debug)
        val finalLeft = leftEdge.map { android.graphics.PointF(it.x, it.y) }
        val finalRight = rightEdge.map { android.graphics.PointF(it.x, it.y) }

        return Triple(path, finalLeft, finalRight)
    }

    private fun generateLinearPathWithPoints(points: List<StrokePoint>, maxWidth: Float, minSizeFactor: Float): Triple<Path, List<android.graphics.PointF>, List<android.graphics.PointF>> {
        val path = Path()
        val left = mutableListOf<android.graphics.PointF>()
        val right = mutableListOf<android.graphics.PointF>()
        
        val p1 = points[0]
        val p2 = points[1]
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val length = sqrt(dx * dx + dy * dy)
        val (nX, nY) = if (length > 0) Pair(-dy / length, dx / length) else Pair(0f, 0f)

        fun getHW(p: StrokePoint): Float {
            val scale = minSizeFactor + (1.0f - minSizeFactor) * p.pressure
            return (maxWidth * scale) / 2f
        }

        val hw1 = getHW(p1)
        val hw2 = getHW(p2)

        val l1 = android.graphics.PointF(p1.x + nX * hw1, p1.y + nY * hw1)
        val r1 = android.graphics.PointF(p1.x - nX * hw1, p1.y - nY * hw1)
        val l2 = android.graphics.PointF(p2.x + nX * hw2, p2.y + nY * hw2)
        val r2 = android.graphics.PointF(p2.x - nX * hw2, p2.y - nY * hw2)

        left.add(l1); left.add(l2)
        right.add(r1); right.add(r2)

        // Calculate tangents for rounded caps
        val tangentX = if (length > 0) dx / length else 0f
        val tangentY = if (length > 0) dy / length else 0f
        
        // Start cap control point (projects backwards)
        val startCapControl = getCapControlPoint(p1, PointF(-tangentX, -tangentY), hw1)
        
        // End cap control point (projects forwards)
        val endCapControl = getCapControlPoint(p2, PointF(tangentX, tangentY), hw2)

        // Draw path with rounded caps
        path.moveTo(r1.x, r1.y)
        path.quadTo(startCapControl.x, startCapControl.y, l1.x, l1.y)  // Start cap
        path.lineTo(l2.x, l2.y)  // Left edge
        path.quadTo(endCapControl.x, endCapControl.y, r2.x, r2.y)  // End cap
        path.lineTo(r1.x, r1.y)  // Right edge
        path.close()

        return Triple(path, left, right)
    }

    /**
     * Generates a path by sweeping a rotating polygon along the stroke.
     * Creates organic, ribbon-like fills with the outer silhouette of the polygon movement.
     * 
     * @param points Stroke points to sweep along
     * @param maxWidth Maximum width of the stroke
     * @param minSizeFactor Minimum size factor (0.0 to 1.0)
     * @param polygonSides Number of polygon sides (3-10)
     * @param rotationSpeed Rotation speed in radians per pixel traveled
     * @param randomRotation Whether to add random jitter to rotation
     * @return Triple of (Path, left edge points, right edge points)
     */
    fun generatePolygonSweepPath(
        points: List<StrokePoint>,
        maxWidth: Float,
        minSizeFactor: Float = 0.0f,
        polygonSides: Int,
        rotationSpeed: Float,
        randomRotation: Boolean
    ): Triple<Path, List<android.graphics.PointF>, List<android.graphics.PointF>> {
        val path = Path()
        val leftEdgeList = mutableListOf<android.graphics.PointF>()
        val rightEdgeList = mutableListOf<android.graphics.PointF>()
        
        if (points.size < 2) return Triple(path, emptyList(), emptyList())
        
        // Helper function to calculate radius based on pressure
        fun getRadius(p: StrokePoint): Float {
            val scale = minSizeFactor + (1.0f - minSizeFactor) * p.pressure
            return (maxWidth * scale) / 2f
        }
        
        // Helper function to calculate normal vector for a segment
        fun getNormalVector(p1: StrokePoint, p2: StrokePoint): PointF {
            val dx = p2.x - p1.x
            val dy = p2.y - p1.y
            val length = sqrt(dx * dx + dy * dy)
            return if (length > 0) {
                PointF(-dy / length, dx / length)
            } else {
                PointF(0f, 1f) // Default normal if points are coincident
            }
        }
        
        // Tracking variables
        var baseRotation = 0.0f
        var distanceTravelled = 0.0f
        
        // Iterate through stroke points
        for (i in points.indices) {
            val point = points[i]
            
            // Calculate radius based on pressure
            val radius = getRadius(point)
            
            // Update distance traveled and rotation
            if (i > 0) {
                val dx = point.x - points[i - 1].x
                val dy = point.y - points[i - 1].y
                val segmentDist = sqrt(dx * dx + dy * dy)
                distanceTravelled += segmentDist
            }
            baseRotation = distanceTravelled * rotationSpeed
            
            // Add random jitter if enabled
            val finalRotation = if (randomRotation) {
                baseRotation + (kotlin.random.Random.nextFloat() * 0.5f - 0.25f)
            } else {
                baseRotation
            }
            
            // Calculate normal vector for this segment
            val normalVector = when {
                i == 0 && points.size > 1 -> {
                    // First point: use direction to next point
                    getNormalVector(points[0], points[1])
                }
                i == points.size - 1 -> {
                    // Last point: use direction from previous point
                    getNormalVector(points[i - 1], points[i])
                }
                else -> {
                    // Middle points: average of incoming and outgoing normals
                    val n1 = getNormalVector(points[i - 1], points[i])
                    val n2 = getNormalVector(points[i], points[i + 1])
                    val avgX = (n1.x + n2.x) / 2f
                    val avgY = (n1.y + n2.y) / 2f
                    val avgLen = sqrt(avgX * avgX + avgY * avgY)
                    if (avgLen > 0) {
                        PointF(avgX / avgLen, avgY / avgLen)
                    } else {
                        n1
                    }
                }
            }
            
            // Get extreme points using VectorUtils
            val (leftPt, rightPt) = com.skecher.sketchercompanionv1.utils.VectorUtils.getExtremePointsOfPolygon(
                center = PointF(point.x, point.y),
                radius = radius,
                sides = polygonSides,
                rotationRad = finalRotation,
                normalVector = normalVector
            )
            
            leftEdgeList.add(leftPt)
            rightEdgeList.add(rightPt)
        }
        
        // Construct the path
        if (leftEdgeList.isEmpty() || rightEdgeList.isEmpty()) {
            return Triple(path, emptyList(), emptyList())
        }
        
        // Start from the first left edge point
        path.moveTo(leftEdgeList[0].x, leftEdgeList[0].y)
        
        // Draw left edge (forward)
        for (i in 1 until leftEdgeList.size) {
            path.lineTo(leftEdgeList[i].x, leftEdgeList[i].y)
        }
        
        // Connect to right edge
        path.lineTo(rightEdgeList.last().x, rightEdgeList.last().y)
        
        // Draw right edge (backward)
        for (i in rightEdgeList.size - 2 downTo 0) {
            path.lineTo(rightEdgeList[i].x, rightEdgeList[i].y)
        }
        
        // Close the path
        path.close()
        
        return Triple(path, leftEdgeList, rightEdgeList)
    }

}

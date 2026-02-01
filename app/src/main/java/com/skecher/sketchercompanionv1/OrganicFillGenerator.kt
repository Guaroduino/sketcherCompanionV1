package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import com.skecher.sketchercompanionv1.utils.VectorUtils
import kotlin.math.sqrt
import kotlin.math.cos
import kotlin.math.sin

/**
 * Strategy class for generating ORGANIC_FILL vector paths.
 * Generates a closed, smooth blob by constructing a perimeter from edges and semi-circle caps.
 */
object OrganicFillGenerator {

    // --- HELPERS ---

    private fun getNormal(p1: PointF, p2: PointF): PointF {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val len = sqrt(dx * dx + dy * dy)
        return if (len > 0) PointF(dy / len, -dx / len) else PointF(0f, 0f)
    }

    private fun normalize(p: PointF): PointF {
        val len = sqrt(p.x * p.x + p.y * p.y)
        return if (len > 0) PointF(p.x / len, p.y / len) else PointF(0f, 0f)
    }

    /**
     * Generates a list of points representing a semi-circle arc.
     * Used to close the tip and start of the organic blob smoothly.
     */
    private fun generateSemiCirclePoints(
        center: PointF,
        radius: Float,
        startAngleRad: Float,
        endAngleRad: Float,
        steps: Int = 10
    ): List<PointF> {
        val points = mutableListOf<PointF>()
        val totalAngle = endAngleRad - startAngleRad
        
        // Ensure we go the "short way" or valid arc direction
        // For a simple semi-circle cap, we usually sweep ~PI radians.
        
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = startAngleRad + t * totalAngle
            points.add(
                PointF(
                    center.x + radius * cos(angle),
                    center.y + radius * sin(angle)
                )
            )
        }
        return points
    }

    // --- GENERATE ---
    fun generate(
        points: List<StrokePoint>, 
        maxWidth: Float, 
        minSizeFactor: Float = 0.0f
    ): Triple<Path, List<PointF>, List<PointF>> {
        if (points.size < 2) return Triple(Path(), emptyList(), emptyList())
        
        val leftPoints = mutableListOf<PointF>()
        val rightPoints = mutableListOf<PointF>()

        // 1. Collect Edges
        for (i in points.indices) {
            val pCurr = points[i]
            val w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * pCurr.pressure)) / 2f
            
            // Calculate Normal
            // If internal point, average neighbors? Or just segment logic?
            // "Skeleton: Iterate points. Calculate Normal."
            var dx = 0f
            var dy = 0f
            
            if (i < points.size - 1) {
                dx = points[i+1].x - pCurr.x
                dy = points[i+1].y - pCurr.y
            } else {
                dx = pCurr.x - points[i-1].x
                dy = pCurr.y - points[i-1].y
            }
            
            // If internal (better normal): average prev->curr and curr->next
            if (i > 0 && i < points.size - 1) {
                 val prevDx = pCurr.x - points[i-1].x
                 val prevDy = pCurr.y - points[i-1].y
                 val nextDx = points[i+1].x - pCurr.x
                 val nextDy = points[i+1].y - pCurr.y
                 dx = (prevDx + nextDx) / 2f
                 dy = (prevDy + nextDy) / 2f
            }

            val len = sqrt(dx * dx + dy * dy)
            val nx = if (len > 0) -dy / len else 0f
            val ny = if (len > 0) dx / len else 0f
            
            // Note: Normal Vector direction logic.
            // In Tech Pen we used (dy, -dx) as "Left". Here (-dy, dx) is also Left??
            // Let's stick to standard: Vector(dx, dy). Left Normal is (-dy, dx) if Y-down?
            // V=(1,0) -> N=(0,1) Down. Down is Right in standard math but Left in Screen Y-Down?
            // Let's trust Tech Pen's (dy, -dx) which works.
            // Tech Pen: getNormal(p1, p2) { PointF(dy / len, -dx / len) }
            // So let's align.
            
            val n = if (len > 0) PointF(dy / len, -dx / len) else PointF(0f, 0f)

            leftPoints.add(PointF(pCurr.x + n.x * w, pCurr.y + n.y * w))
            rightPoints.add(PointF(pCurr.x - n.x * w, pCurr.y - n.y * w))
        }

        // 2. Construct Perimeter
        val perimeter = mutableListOf<PointF>()
        
        // A. Left Side (Forward)
        perimeter.addAll(leftPoints)
        
        // B. Tip Cap (Semi-Circle from Left-Last to Right-Last)
        val pLast = points.last()
        val wLast = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * pLast.pressure)) / 2f
        val vLast = if (points.size >= 2) PointF(points.last().x - points[points.size-2].x, points.last().y - points[points.size-2].y) else PointF(1f, 0f)
        val tLast = normalize(vLast)
        
        // Angle of tangent
        val angleLast = kotlin.math.atan2(tLast.y, tLast.x)
        // Left Normal Angle is angleLast - PI/2
        // We sweep from Left (-PI/2 relative) to Right (+PI/2 relative)?
        // Wait, Left is P + N. Right is P - N.
        // We want to go from Left to Right around the tip.
        // That is a Clockwise sweep relative to the stroke direction?
        // Standard Normal on Screen: (dy, -dx).
        // If V=(1,0), N=(0,-1) Up. Left Point is above. Right Point is below.
        // To go from Top to Bottom around the tip (Right side), we sweep Clockwise.
        
        val startAngle = angleLast - (Math.PI / 2).toFloat() // Normal Direction
        val endAngle = angleLast + (Math.PI / 2).toFloat()
        
        val tipCap = generateSemiCirclePoints(
            center = PointF(pLast.x, pLast.y),
            radius = wLast,
            startAngleRad = startAngle,
            endAngleRad = endAngle, 
            steps = 8
        )
        // Don't duplicate points if they match exactly logic, but simple append is fine for splines
        perimeter.addAll(tipCap)
        
        // C. Right Side (Reversed)
        perimeter.addAll(rightPoints.reversed())
        
        // D. Start Cap (Semi-Circle from Right-First to Left-First)
        // We are at Right-First. We need to go around the back to Left-First.
        val pFirst = points.first()
        val wFirst = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * pFirst.pressure)) / 2f
        val vFirst = if (points.size >= 2) PointF(points[1].x - points[0].x, points[1].y - points[0].y) else PointF(1f, 0f)
        val tFirst = normalize(vFirst)
        val angleFirst = kotlin.math.atan2(tFirst.y, tFirst.x)
        
        // At start, we are "backing up".
        // Right is P - N. Left is P + N.
        // Vector is forward.
        // Right Normal angle is angleFirst + PI/2.
        // Left Normal angle is angleFirst - PI/2.
        // We want to sweep from Right (+PI/2) -> Back (PI) -> Left (-PI/2).
        // This is continued Clockwise rotation?
        // Right (+90) -> Left (-90 is 270).
        
        val startAngle2 = angleFirst + (Math.PI / 2).toFloat()
        val endAngle2 = angleFirst + (Math.PI * 1.5).toFloat() // +270 deg
        
        val startCap = generateSemiCirclePoints(
            center = PointF(pFirst.x, pFirst.y),
            radius = wFirst,
            startAngleRad = startAngle2,
            endAngleRad = endAngle2,
            steps = 8
        )
        perimeter.addAll(startCap)
        
        // 3. Generate Smooth Closed Path
        // User explicitly asked for generated smooth closed path
        val smoothPath = VectorUtils.generateSmoothClosedPath(perimeter)
        
        // Return blank edges because this generator doesn't use the simple Ribbon left/right structure 
        // in the same way for debug (perimeter is the key). 
        // Or we can return the raw left/right lists for debug visualization if desired.
        // User said: "Return: Triple(smoothPath, emptyList(), emptyList())"
        
        return Triple(smoothPath, emptyList(), emptyList())
    }
}

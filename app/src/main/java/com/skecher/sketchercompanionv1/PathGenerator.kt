package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import com.skecher.sketchercompanionv1.utils.VectorUtils
import kotlin.math.sqrt

object PathGenerator {

    // --- HELPERS ---
    
    /**
     * Returns the 2D cross product of two vectors.
     * v1.x * v2.y - v1.y * v2.x
     * In Screen Coordinates (Y-Down):
     * > 0 : Right Turn
     * < 0 : Left Turn
     */
    private fun crossProduct(v1: PointF, v2: PointF): Float {
        return v1.x * v2.y - v1.y * v2.x
    }

    private fun normalize(p: PointF): PointF {
        val len = sqrt(p.x * p.x + p.y * p.y)
        return if (len > 0) PointF(p.x / len, p.y / len) else PointF(0f, 0f)
    }

    /**
     * Returns the Left Normal (dy, -dx) for the vector p1->p2.
     * In Y-Down system:
     * Vector (1,0) [Right] -> Normal (0, -1) [Up] (Left of vector)
     */
    private fun getNormal(p1: PointF, p2: PointF): PointF {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val len = sqrt(dx * dx + dy * dy)
        return if (len > 0) PointF(dy / len, -dx / len) else PointF(0f, 0f)
    }
    
    /**
     * Calculates a generic Control Point for a Round Cap.
     * Projects out from 'point' in direction 'tangent' by 'width'.
     */
    private fun getCapControlPoint(point: PointF, tangent: PointF, width: Float): PointF {
        return PointF(
            point.x + tangent.x * width, 
            point.y + tangent.y * width
        )
    }

    private fun getMiterPoint(p: PointF, n1: PointF, n2: PointF, width: Float): PointF {
        // Average the normals to find the corner bisector
        val sumX = n1.x + n2.x
        val sumY = n1.y + n2.y
        val len = sqrt(sumX * sumX + sumY * sumY)
        
        if (len < 0.001f) return PointF(p.x + n1.x * width, p.y + n1.y * width) // Parallel fallback
        
        // Miter Length Logic:
        // width / sin(alpha/2) ? No, width / cos(theta/2) where theta is half-angle between normals?
        // Let's rely on vector addition property: |n1+n2| = 2*cos(angle_diff/2).
        // Desired length L = width / cos(angle_diff/2).
        // So L = width * 2 / |n1+n2|.
        val miterLen = (width * 2f) / len
        val scale = miterLen / len // Normalize sum vector then scale
        
        // Clamp to avoid spikes on sharp angles
        val finalScale = scale.coerceAtMost(width * 3.0f) 
        
        return PointF(p.x + sumX * finalScale, p.y + sumY * finalScale)
    }

    // --- 1. TECHNICAL PEN (ROBUST JOIN & CAPS) ---
    fun generateStrokePath(
        points: List<StrokePoint>, 
        maxWidth: Float, 
        minSizeFactor: Float = 0.0f
    ): Triple<Path, List<PointF>, List<PointF>> {
        val path = Path()
        val leftEdge = mutableListOf<PointF>()
        val rightEdge = mutableListOf<PointF>()

        if (points.size < 2) return Triple(path, emptyList(), emptyList())

        // --- STEP A: GENERATE EDGES (OFFSET LOGIC) ---
        
        // 1. Start Point
        val p0 = points[0]
        val p1 = points[1]
        var nNext = getNormal(PointF(p0.x, p0.y), PointF(p1.x, p1.y))
        var w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * p0.pressure)) / 2f
        
        // Just add simple offsets for start
        leftEdge.add(PointF(p0.x + nNext.x * w, p0.y + nNext.y * w))
        rightEdge.add(PointF(p0.x - nNext.x * w, p0.y - nNext.y * w))

        // 2. Internal Points
        for (i in 1 until points.size - 1) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val pNext = points[i + 1]
            
            // Current Normal (In) and Next Normal (Out)
            val nIn = nNext // Reuse previous next as current in
            nNext = getNormal(PointF(pCurr.x, pCurr.y), PointF(pNext.x, pNext.y))
            
            val vIn = PointF(pCurr.x - pPrev.x, pCurr.y - pPrev.y)
            val vOut = PointF(pNext.x - pCurr.x, pNext.y - pCurr.y)
            
            // Turn Detection
            val turn = crossProduct(vIn, vOut)
            
            w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * pCurr.pressure)) / 2f
            
            // Basic Offsets for current point based on In/Out normals
            val lIn = PointF(pCurr.x + nIn.x * w, pCurr.y + nIn.y * w)
            val rIn = PointF(pCurr.x - nIn.x * w, pCurr.y - nIn.y * w)
            
            val lOut = PointF(pCurr.x + nNext.x * w, pCurr.y + nNext.y * w)
            val rOut = PointF(pCurr.x - nNext.x * w, pCurr.y - nNext.y * w)

            // Join Logic
            // In Screen Coords: Turn > 0 is Right Turn. Turn < 0 is Left Turn.
            if (turn > 0) {
                // RIGHT TURN
                // Right Side is INNER -> Bevel (Add both In and Out points)
                rightEdge.add(rIn)
                rightEdge.add(rOut)
                
                // Left Side is OUTER -> Round (Miter/Control Point)
                // Use Miter helper to find the corner point. 
                // We add 3 points: In, Miter, Out. 
                // In Step B, 'lineTo' these will create a Chamfer (Bevel), effectively "Round-ish".
                val miterPt = getMiterPoint(PointF(pCurr.x, pCurr.y), nIn, nNext, w)
                
                leftEdge.add(lIn)
                leftEdge.add(miterPt) 
                leftEdge.add(lOut)
                
            } else {
                // LEFT TURN
                // Left Side is INNER -> Bevel
                leftEdge.add(lIn)
                leftEdge.add(lOut)
                
                // Right Side is OUTER -> Round
                val miterPt = getMiterPoint(PointF(pCurr.x, pCurr.y), PointF(-nIn.x, -nIn.y), PointF(-nNext.x, -nNext.y), w)
                
                rightEdge.add(rIn)
                rightEdge.add(miterPt)
                rightEdge.add(rOut)
            }
        }

        // 3. End Point
        val pLast = points.last()
        val pBefore = points[points.size - 2]
        // nNext is already valid from loop or init (if size=2 loop skipped)
        if (points.size == 2) {
             // Loop didn't run, calculate nNext for end
             nNext = getNormal(PointF(pBefore.x, pBefore.y), PointF(pLast.x, pLast.y))
        }
        
        w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * pLast.pressure)) / 2f
        
        leftEdge.add(PointF(pLast.x + nNext.x * w, pLast.y + nNext.y * w))
        rightEdge.add(PointF(pLast.x - nNext.x * w, pLast.y - nNext.y * w))


        // --- STEP B: CONSTRUCT PATH (Explicit Caps) ---
        
        // 1. Trace Right Edge (Forward)
        if (rightEdge.isNotEmpty()) {
            path.moveTo(rightEdge[0].x, rightEdge[0].y)
            for (i in 1 until rightEdge.size) {
                path.lineTo(rightEdge[i].x, rightEdge[i].y)
            }
        }

        // 2. End Cap (Round)
        // Tangent is direction of last segment: P_last - P_before
        val vEnd = PointF(pLast.x - pBefore.x, pLast.y - pBefore.y)
        val tEnd = normalize(vEnd)
        // Control Point projects OUT from P_last
        val capControlEnd = getCapControlPoint(PointF(pLast.x, pLast.y), tEnd, w) // w is radius using pLast width
        
        // Curve from Right_Last to Left_Last
        path.quadTo(
            capControlEnd.x, capControlEnd.y,
            leftEdge.last().x, leftEdge.last().y
        )

        // 3. Trace Left Edge (Backward)
        if (leftEdge.isNotEmpty()) {
            for (i in leftEdge.size - 2 downTo 0) {
                path.lineTo(leftEdge[i].x, leftEdge[i].y)
            }
        }

        // 4. Start Cap (Round)
        // Tangent is direction BACK from start: P_0 - P_1  (= -vStart)
        val vStart = PointF(p0.x - p1.x, p0.y - p1.y)
        val tStart = normalize(vStart)
        val wStart = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * p0.pressure)) / 2f
        val capControlStart = getCapControlPoint(PointF(p0.x, p0.y), tStart, wStart)
        
        // Curve from Left_0 to Right_0
        path.quadTo(
            capControlStart.x, capControlStart.y,
            rightEdge[0].x, rightEdge[0].y
        )
        
        path.close()

        return Triple(path, leftEdge, rightEdge)
    }

    // --- 2. ORGANIC FILL (SAME LOGIC) ---
    fun generateOrganicFillPath(
        points: List<StrokePoint>, 
        maxWidth: Float, 
        minSizeFactor: Float = 0.0f
    ): Triple<Path, List<PointF>, List<PointF>> {
        if (points.size < 2) return Triple(Path(), emptyList(), emptyList())
        
        val leftEdge = mutableListOf<PointF>()
        val rightEdge = mutableListOf<PointF>()

        // Copy-Paste Logic for consistent offset generation
        // 1. Start
        val p0 = points[0]
        val p1 = points[1]
        var nNext = getNormal(PointF(p0.x, p0.y), PointF(p1.x, p1.y))
        var w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * p0.pressure)) / 2f
        leftEdge.add(PointF(p0.x + nNext.x * w, p0.y + nNext.y * w))
        rightEdge.add(PointF(p0.x - nNext.x * w, p0.y - nNext.y * w))
        
        // 2. Loop
        for (i in 1 until points.size - 1) {
            val pPrev = points[i - 1]
            val pCurr = points[i]
            val pNext = points[i + 1]
            
            val nIn = nNext
            nNext = getNormal(PointF(pCurr.x, pCurr.y), PointF(pNext.x, pNext.y))
            
            val vIn = PointF(pCurr.x - pPrev.x, pCurr.y - pPrev.y)
            val vOut = PointF(pNext.x - pCurr.x, pNext.y - pCurr.y)
            val turn = crossProduct(vIn, vOut)
            
            w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * pCurr.pressure)) / 2f
            
            val lIn = PointF(pCurr.x + nIn.x * w, pCurr.y + nIn.y * w)
            val rIn = PointF(pCurr.x - nIn.x * w, pCurr.y - nIn.y * w)
            val lOut = PointF(pCurr.x + nNext.x * w, pCurr.y + nNext.y * w)
            val rOut = PointF(pCurr.x - nNext.x * w, pCurr.y - nNext.y * w)
            
            if (turn > 0) { // Right Turn
                rightEdge.add(rIn); rightEdge.add(rOut) // Inner Bevel
                val miterPt = getMiterPoint(PointF(pCurr.x, pCurr.y), nIn, nNext, w) // Outer Round
                leftEdge.add(lIn); leftEdge.add(miterPt); leftEdge.add(lOut)
            } else { // Left Turn
                leftEdge.add(lIn); leftEdge.add(lOut) // Inner Bevel
                val miterPt = getMiterPoint(PointF(pCurr.x, pCurr.y), PointF(-nIn.x, -nIn.y), PointF(-nNext.x, -nNext.y), w) // Outer Round
                rightEdge.add(rIn); rightEdge.add(miterPt); rightEdge.add(rOut)
            }
        }
        
        // 3. End
        val pLast = points.last()
        val pBefore = points[points.size - 2]
        if (points.size == 2) nNext = getNormal(PointF(pBefore.x, pBefore.y), PointF(pLast.x, pLast.y))
        w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * pLast.pressure)) / 2f
        leftEdge.add(PointF(pLast.x + nNext.x * w, pLast.y + nNext.y * w))
        rightEdge.add(PointF(pLast.x - nNext.x * w, pLast.y - nNext.y * w))

        // --- MERGE & SMOOTH ---
        val perimeter = mutableListOf<PointF>()
        perimeter.addAll(leftEdge)
        perimeter.addAll(rightEdge.reversed())

        val smoothPath = VectorUtils.generateSmoothClosedPath(perimeter)

        return Triple(smoothPath, leftEdge, rightEdge)
    }
}

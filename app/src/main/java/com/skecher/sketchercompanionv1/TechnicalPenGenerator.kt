package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.sqrt

object TechnicalPenGenerator {

    private data class EdgePt(val x: Float, val y: Float)
    private const val K_CIRCLE = 0.55228475f // Constant for cubic bezier circle approximation

    // --- HELPERS ---
    private fun crossProduct(v1: PointF, v2: PointF): Float = v1.x * v2.y - v1.y * v2.x

    private fun normalize(p: PointF): PointF {
        val len = sqrt(p.x * p.x + p.y * p.y)
        return if (len > 0.0001f) PointF(p.x / len, p.y / len) else PointF(0f, 0f)
    }

    private fun getNormal(p1: PointF, p2: PointF): PointF {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val len = sqrt(dx * dx + dy * dy)
        return if (len > 0.0001f) PointF(dy / len, -dx / len) else PointF(0f, 0f)
    }

    private fun getMiterPoint(p: PointF, n1: PointF, n2: PointF, width: Float): PointF {
        val sumX = n1.x + n2.x
        val sumY = n1.y + n2.y
        val len = sqrt(sumX * sumX + sumY * sumY)
        
        if (len < 0.001f) return PointF(p.x + n1.x * width, p.y + n1.y * width)
        
        val miterLen = (width * 2f) / len
        val scale = miterLen / len
        val finalScale = scale.coerceAtMost(width * 3.0f) 
        
        return PointF(p.x + sumX * finalScale, p.y + sumY * finalScale)
    }

    // --- MAIN GENERATE ---
    fun generate(
        rawPoints: List<StrokePoint>, 
        maxWidth: Float, 
        minSizeFactor: Float = 0.0f,
        smoothness: Float = 0.0f
    ): Triple<Path, List<PointF>, List<PointF>> {
        val path = Path()
        
        if (rawPoints.size < 2) return Triple(path, emptyList(), emptyList())

        // Filter Micro-segments
        val points = mutableListOf<StrokePoint>()
        points.add(rawPoints.first())
        val MIN_DIST_SQ = 4.0f // 2.0px distance
        for (i in 1 until rawPoints.size - 1) {
            val prev = points.last()
            val curr = rawPoints[i]
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            if (dx*dx + dy*dy > MIN_DIST_SQ) points.add(curr)
        }
        points.add(rawPoints.last())

        if (points.size < 2) return Triple(path, emptyList(), emptyList())


        // --- GEOMETRY CALCULATION (MITER LOGIC) ---
        val leftEdge = mutableListOf<EdgePt>()
        val rightEdge = mutableListOf<EdgePt>()
        
        // Start
        val p0 = points[0]
        val p1 = points[1]
        var nNext = getNormal(PointF(p0.x, p0.y), PointF(p1.x, p1.y))
        var w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * p0.pressure)) / 2f
        
        leftEdge.add(EdgePt(p0.x + nNext.x * w, p0.y + nNext.y * w))
        rightEdge.add(EdgePt(p0.x - nNext.x * w, p0.y - nNext.y * w))

        // Loop
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

            if (turn > 0) { // RIGHT
                rightEdge.add(EdgePt(rIn.x, rIn.y))
                rightEdge.add(EdgePt(rOut.x, rOut.y))
                
                val m = getMiterPoint(PointF(pCurr.x, pCurr.y), nIn, nNext, w)
                leftEdge.add(EdgePt(lIn.x, lIn.y))
                leftEdge.add(EdgePt(m.x, m.y)) 
                leftEdge.add(EdgePt(lOut.x, lOut.y))
            } else { // LEFT
                leftEdge.add(EdgePt(lIn.x, lIn.y))
                leftEdge.add(EdgePt(lOut.x, lOut.y))
                
                val m = getMiterPoint(PointF(pCurr.x, pCurr.y), PointF(-nIn.x, -nIn.y), PointF(-nNext.x, -nNext.y), w)
                rightEdge.add(EdgePt(rIn.x, rIn.y))
                rightEdge.add(EdgePt(m.x, m.y))
                rightEdge.add(EdgePt(rOut.x, rOut.y))
            }
        }

        // End
        val pLast = points.last()
        val pBefore = points[points.size - 2]
        if (points.size <= 2) nNext = getNormal(PointF(pBefore.x, pBefore.y), PointF(pLast.x, pLast.y))
        w = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * pLast.pressure)) / 2f
        leftEdge.add(EdgePt(pLast.x + nNext.x * w, pLast.y + nNext.y * w))
        rightEdge.add(EdgePt(pLast.x - nNext.x * w, pLast.y - nNext.y * w))


        // --- PATH CONSTRUCTION (PERFECT ROUND CAPS) ---
        path.moveTo(rightEdge[0].x, rightEdge[0].y)
        for (i in 1 until rightEdge.size) path.lineTo(rightEdge[i].x, rightEdge[i].y)

        // 1. End Cap (Cubic Bezier)
        val vEnd = PointF(pLast.x - pBefore.x, pLast.y - pBefore.y)
        val tEnd = normalize(vEnd)
        val rLastX = rightEdge.last().x
        val rLastY = rightEdge.last().y
        val lLastX = leftEdge.last().x
        val lLastY = leftEdge.last().y
        
        val cp1x = rLastX + tEnd.x * w * K_CIRCLE
        val cp1y = rLastY + tEnd.y * w * K_CIRCLE
        val cp2x = lLastX + tEnd.x * w * K_CIRCLE
        val cp2y = lLastY + tEnd.y * w * K_CIRCLE
        
        path.cubicTo(cp1x, cp1y, cp2x, cp2y, lLastX, lLastY)

        // Left Side
        for (i in leftEdge.size - 2 downTo 0) path.lineTo(leftEdge[i].x, leftEdge[i].y)

        // 2. Start Cap (Cubic Bezier)
        val vStart = PointF(p0.x - p1.x, p0.y - p1.y)
        val tStart = normalize(vStart)
        val lFirstX = leftEdge[0].x
        val lFirstY = leftEdge[0].y
        val rFirstX = rightEdge[0].x
        val rFirstY = rightEdge[0].y
        val wStart = (maxWidth * (minSizeFactor + (1f - minSizeFactor) * p0.pressure)) / 2f

        val cp3x = lFirstX + tStart.x * wStart * K_CIRCLE
        val cp3y = lFirstY + tStart.y * wStart * K_CIRCLE
        val cp4x = rFirstX + tStart.x * wStart * K_CIRCLE
        val cp4y = rFirstY + tStart.y * wStart * K_CIRCLE

        path.cubicTo(cp3x, cp3y, cp4x, cp4y, rFirstX, rFirstY)
        
        path.close()

        val dLeft = leftEdge.map { PointF(it.x, it.y) }
        val dRight = rightEdge.map { PointF(it.x, it.y) }

        return Triple(path, dLeft, dRight)
    }
}

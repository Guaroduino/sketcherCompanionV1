package com.sketcher.sketchercompanionv1.utils

import com.sketcher.sketchercompanionv1.StrokePoint
import kotlin.math.sqrt

object StrokeSimplifier {
    fun simplify(points: List<StrokePoint>, epsilon: Float, pressureWeight: Float = 0f): List<StrokePoint> {
        if (points.size < 3) return points

        // 1. Get Indices of Points to Keep (RDP)
        val keptIndices = simplifyIndices(points, 0, points.lastIndex, epsilon, pressureWeight)
        
        // 2. Reconstruct Points with Pressure Averaging
        return keptIndices.map { index ->
            val original = points[index]
            val averagedPressure = getAveragePressure(points, index, windowSize = 2)
            original.copy(pressure = averagedPressure)
        }
    }

    private fun simplifyIndices(
        points: List<StrokePoint>, 
        startIndex: Int, 
        endIndex: Int, 
        epsilon: Float, 
        pressureWeight: Float
    ): List<Int> {
        if (endIndex - startIndex < 2) {
             return listOf(startIndex, endIndex)
        }
        
        val firstPoint = points[startIndex]
        val lastPoint = points[endIndex]
        var dmax = 0f
        var index = 0
        
        for (i in (startIndex + 1) until endIndex) {
            val d = perpendicularDistance(points[i], firstPoint, lastPoint, pressureWeight)
            if (d > dmax) { index = i; dmax = d }
        }
        
        return if (dmax > epsilon) {
            val left = simplifyIndices(points, startIndex, index, epsilon, pressureWeight)
            val right = simplifyIndices(points, index, endIndex, epsilon, pressureWeight)
            left.dropLast(1) + right
        } else {
            listOf(startIndex, endIndex)
        }
    }

    private fun getAveragePressure(points: List<StrokePoint>, centerIndex: Int, windowSize: Int): Float {
        var sum = 0f
        var count = 0
        val start = (centerIndex - windowSize).coerceAtLeast(0)
        val end = (centerIndex + windowSize).coerceAtMost(points.lastIndex)
        
        for (i in start..end) {
            sum += points[i].pressure
            count++
        }
        return if (count > 0) sum / count else points[centerIndex].pressure
    }

    private fun perpendicularDistance(p: StrokePoint, start: StrokePoint, end: StrokePoint, pressureWeight: Float): Float {
        var x = p.x
        var y = p.y
        var x1 = start.x
        var y1 = start.y
        var x2 = end.x
        var y2 = end.y
        
        // Geometric Distance
        val geometricDist: Float
        val u: Float
        
        if (x1 == x2 && y1 == y2) {
             geometricDist = sqrt(((x - x1) * (x - x1) + (y - y1) * (y - y1)).toDouble()).toFloat()
             u = 0f
        } else {
            val px = x2 - x1
            val py = y2 - y1
            val lenSq = px * px + py * py
            val rawU = ((x - x1) * px + (y - y1) * py) / lenSq
            
            u = rawU.coerceIn(0f, 1f)
            
            if (u > 1) { x1 = x2; y1 = y2 }
            else if (u > 0) { x1 += u * px; y1 += u * py }
            
            val dx = x - x1
            val dy = y - y1
            geometricDist = sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        }
        
        if (pressureWeight <= 0f) return geometricDist
        
        // Pressure Distance
        val interpolatedPressure = start.pressure + u * (end.pressure - start.pressure)
        val pressureDiff = kotlin.math.abs(p.pressure - interpolatedPressure)
        val pressureDist = pressureDiff * pressureWeight
        
        return kotlin.math.max(geometricDist, pressureDist)
    }
}


package com.skecher.sketchercompanionv1

import androidx.ink.strokes.Stroke
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

object StrokeGeometry {

    /**
     * Devuelve true si el punto (x, y) toca el trazo, considerando un margen de tolerancia.
     */
    /**
     * Devuelve true si el punto (x, y) toca el trazo, considerando un margen de tolerancia (Eraser Radius).
     */
    fun isStrokeTouched(stroke: Stroke, x: Float, y: Float, tolerance: Float = 20f): Boolean {
        val inputs = stroke.inputs
        if (inputs.size < 2) return false

        // ConsiderStroke Width in Hit Test
        // Total Threshold = Eraser Radius (tolerance) + Stroke Radius (size / 2)
        val strokeRadius = stroke.brush.size / 2f
        val totalThreshold = tolerance + strokeRadius

        // 1. OPTIMIZACIÓN (Bounding Box):
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE // Fix: Correct initialization for Max
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        for (i in 0 until inputs.size) {
            val inp = inputs.get(i)
            if (inp.x < minX) minX = inp.x
            if (inp.x > maxX) maxX = inp.x
            if (inp.y < minY) minY = inp.y
            if (inp.y > maxY) maxY = inp.y
        }

        // Expanded bounds check
        if (x < minX - totalThreshold || x > maxX + totalThreshold ||
            y < minY - totalThreshold || y > maxY + totalThreshold) {
            return false
        }

        // 2. PRECISIÓN (Distancia a Segmentos):
        for (i in 0 until inputs.size - 1) {
            val p1 = inputs.get(i)
            val p2 = inputs.get(i + 1)
            
            val dist = distanceToSegment(x, y, p1.x, p1.y, p2.x, p2.y)
            if (dist <= totalThreshold) { // Use combined threshold
                return true
            }
        }

        return false
    }

    /**
     * Matemática: Calcula la distancia mínima de un punto (px, py) a un segmento de línea (x1,y1)-(x2,y2).
     */
    private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) return hypot(px - x1, py - y1)

        // Proyección del punto sobre la línea
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)

        // Clampeamos t al segmento [0, 1]
        val tClamped = max(0f, min(1f, t))
        
        val nearestX = x1 + tClamped * dx
        val nearestY = y1 + tClamped * dy

        return hypot(px - nearestX, py - nearestY)
    }
}
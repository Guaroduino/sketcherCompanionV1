package com.sketcher.sketchercompanionv1

import kotlin.math.sqrt

object StrokePredictor {
    
    /**
     * Calculates a predicted point based on a smoothed velocity history.
     * Uses linear extrapolation with a damping factor over a constant time window.
     */
    fun getPredictedPoint(
        points: List<StrokePoint>, 
        maxPredictionMillis: Long = 20
    ): StrokePoint? {
        if (points.size < 3) return null
        
        val curr = points.last()
        var prev = points.first()

        // Seek backwards to find a point with a delta t of >= 15ms
        for (i in points.size - 2 downTo 0) {
            val p = points[i]
            if (curr.timestamp - p.timestamp >= 15) {
                prev = p
                break
            }
        }
        
        val dt = (curr.timestamp - prev.timestamp).toFloat()
        
        // If updates are instantaneous or invalid, avoid division by zero
        if (dt <= 0) return null
        
        // Calculate Velocity (px/ms)
        val vx = (curr.x - prev.x) / dt
        val vy = (curr.y - prev.y) / dt
        val speed = sqrt(vx * vx + vy * vy)
        
        // Deadzone: ignore very slow movements to prevent jitter
        if (speed < 0.05f) return null
        
        val effectiveMillis = maxPredictionMillis.toFloat()
        val damping = 0.85f
        
        // Extrapolate with damping
        val predX = curr.x + (vx * effectiveMillis * damping)
        val predY = curr.y + (vy * effectiveMillis * damping)
        
        val predPressure = curr.pressure
        val predTime = curr.timestamp + maxPredictionMillis
        
        return StrokePoint(predX, predY, predPressure, predTime)
    }
}

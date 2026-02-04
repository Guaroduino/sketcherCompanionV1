package com.skecher.sketchercompanionv1

import kotlin.math.sqrt

object StrokePredictor {
    
    /**
     * Calculates a predicted point based on the last two points of the stroke.
     * Uses linear extrapolation: Predicted = Current + Velocity * predictionMillis
     */
    fun getPredictedPoint(
        points: List<StrokePoint>, 
        maxPredictionMillis: Long = 20,
        minSpeed: Float = 0.5f,
        maxSpeed: Float = 4.0f
    ): StrokePoint? {
        if (points.size < 2) return null
        
        val curr = points.last()
        val prev = points[points.size - 2]
        
        val dt = (curr.timestamp - prev.timestamp).toFloat()
        
        // If updates are instantaneous or invalid, avoid division by zero
        if (dt <= 0) return null
        
        // Calculate Velocity (px/ms)
        val dx = curr.x - prev.x
        val dy = curr.y - prev.y
        val dist = sqrt(dx * dx + dy * dy)
        val speed = dist / dt
        
        // Velocity Factor (0.0 to 1.0)
        // If speed < minSpeed, factor = 0
        // If speed > maxSpeed, factor = 1
        val velocityFactor = ((speed - minSpeed) / (maxSpeed - minSpeed)).coerceIn(0f, 1f)
        
        val effectivePredictionMillis = (maxPredictionMillis * velocityFactor).toLong()
        
        if (effectivePredictionMillis <= 0) return null
        
        val vx = dx / dt
        val vy = dy / dt
        
        // Extrapolate
        val predX = curr.x + vx * effectivePredictionMillis
        val predY = curr.y + vy * effectivePredictionMillis
        
        val predPressure = curr.pressure
        val predTime = curr.timestamp + effectivePredictionMillis
        
        return StrokePoint(predX, predY, predPressure, predTime)
    }
}

package com.skecher.sketchercompanionv1

import android.graphics.PointF

class StrokeStabilizer {
    private var currentX: Float = 0f
    private var currentY: Float = 0f

    /**
     * Resets the stabilizer to a specific point.
     * Should be called on ACTION_DOWN.
     */
    fun reset(x: Float, y: Float) {
        currentX = x
        currentY = y
    }

    /**
     * Updates the stabilized position based on the target position and stabilization level.
     * @param targetX The actual X coordinate from touch event.
     * @param targetY The actual Y coordinate from touch event.
     * @param level Stabilization level from 0 to 100.
     * @return PointF containing the new smoothed coordinates.
     */
    fun update(targetX: Float, targetY: Float, level: Float): PointF {
        // Validation of level is implicitly handled by math, but clamping is good practice
        val clampedLevel = level.coerceIn(0f, 100f)

        // Calculate weight:
        // Level 0   -> Weight 1.0 (Instant)
        // Level 100 -> Weight ~0.05 (Slow catching up)
        // We can use a simple linear mapping or something more exponential for "feel".
        // Let's try a simple mapping first that allows for strong stabilization at 100.
        // weight = 1.0 - (level / 100 * 0.95)
        // if level 0 -> 1.0 - 0 = 1.0
        // if level 100 -> 1.0 - 0.95 = 0.05
        
        val weight = 1.0f - (clampedLevel / 100f * 0.95f)

        currentX += (targetX - currentX) * weight
        currentY += (targetY - currentY) * weight

        return PointF(currentX, currentY)
    }
}

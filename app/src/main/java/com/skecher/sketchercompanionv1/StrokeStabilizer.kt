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
        val clampedLevel = level.coerceIn(0f, 300f)

        // Calculate weight:
        // Level 0-100: Legacy linear mapping (1.0 -> 0.05)
        // Level 100-300: Extended mapping (0.05 -> 0.005)
        
        val weight = if (clampedLevel <= 100f) {
            1.0f - (clampedLevel / 100f * 0.95f)
        } else {
            // Map 100..300 to 0.05..0.005
            val ratio = (clampedLevel - 100f) / 200f // 0..1
            0.05f * (1.0f - ratio * 0.9f) // Decays to 10% of 0.05 = 0.005
        }

        currentX += (targetX - currentX) * weight
        currentY += (targetY - currentY) * weight

        return PointF(currentX, currentY)
    }
}

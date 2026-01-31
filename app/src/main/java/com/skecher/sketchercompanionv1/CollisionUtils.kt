package com.skecher.sketchercompanionv1

import kotlin.math.hypot

object CollisionUtils {
    /**
     * Checks if a touch point is close enough to any point in the stroke.
     * @param strokePoints List of points in the stroke.
     * @param touchX X coordinate of the touch in World space.
     * @param touchY Y coordinate of the touch in World space.
     * @param threshold Max distance to consider a hit (default 40.0f).
     */
    fun isTouchingStroke(
        strokePoints: List<StrokePoint>, 
        touchX: Float, 
        touchY: Float, 
        threshold: Float = 40f
    ): Boolean {
        val threshSq = threshold * threshold
        for (point in strokePoints) {
            val dx = touchX - point.x
            val dy = touchY - point.y
            if (dx * dx + dy * dy < threshSq) {
                return true
            }
        }
        return false
    }

    /**
     * Checks collision for Android Ink Stroke.
     */
    fun isTouchingStroke(
        stroke: androidx.ink.strokes.Stroke,
        touchX: Float,
        touchY: Float,
        threshold: Float = 40f
    ): Boolean {
        val inputs = stroke.inputs
        val threshSq = threshold * threshold
        // Iterate inputs directly
        for (i in 0 until inputs.size) {
            val input = inputs.get(i)
            // Stroke inputs are stored in the coordinate space they were created/transformed into.
            // Since we transform them to World in 'addToStroke', they should be World coords.
            // But let's verify usage. 'state.vectorPoints' are world. Ink strokes are transformed?
            val dx = touchX - input.x
            val dy = touchY - input.y
            if (dx * dx + dy * dy < threshSq) {
                return true
            }
        }
        return false
    }
}

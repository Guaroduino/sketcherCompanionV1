package com.skecher.sketchercompanionv1

import android.view.MotionEvent

/**
 * Handles converting raw Android MotionEvents into a list of high-quality StrokePoints,
 * creating a point for every historical event in the batch.
 */
object StrokeInputHandler {

    fun processEvent(event: MotionEvent): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val historySize = event.historySize
        val pointerCount = event.pointerCount
        
        // We only care about the primary pointer (index 0) for single-finger drawing for now.
        // If multi-touch is needed later, we'd need to iterate pointer indices.
        // Assuming single stroke drawing.
        
        // 1. Process Historical Points (Batched events)
        for (h in 0 until historySize) {
            val hx = event.getHistoricalX(h)
            val hy = event.getHistoricalY(h)
            val hp = event.getHistoricalPressure(h)
            val ht = event.getHistoricalEventTime(h)
            
            points.add(StrokePoint(hx, hy, hp, ht))
        }
        
        // 2. Process Current Point
        points.add(StrokePoint(event.x, event.y, event.pressure, event.eventTime))
        
        return points
    }
}

package com.skecher.sketchercompanionv1

import android.view.MotionEvent

/**
 * Handles converting raw Android MotionEvents into a list of high-quality StrokePoints,
 * creating a point for every historical event in the batch.
 */
object StrokeInputHandler {

    fun processEvent(event: MotionEvent, offsetX: Float = 0f, offsetY: Float = 0f): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val historySize = event.historySize
        
        // 1. Process Historical Points (Batched events)
        for (h in 0 until historySize) {
            val hx = event.getHistoricalX(h) - offsetX
            val hy = event.getHistoricalY(h) - offsetY
            val hp = event.getHistoricalPressure(h)
            val ht = event.getHistoricalEventTime(h)
            
            points.add(StrokePoint(hx, hy, hp, ht))
        }
        
        // 2. Process Current Point
        points.add(StrokePoint(event.x - offsetX, event.y - offsetY, event.pressure, event.eventTime))
        
        return points
    }
}

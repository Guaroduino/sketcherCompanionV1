package com.sketcher.sketchercompanionv1

import android.view.MotionEvent

/**
 * Handles converting raw Android MotionEvents into raw data points without allocation.
 */
object StrokeInputHandler {

    inline fun processEvent(event: MotionEvent, action: (x: Float, y: Float, pressure: Float, timestamp: Long) -> Unit) {
        val historySize = event.historySize
        
        // 1. Process Historical Points (Batched events)
        for (h in 0 until historySize) {
            action(
                event.getHistoricalX(h),
                event.getHistoricalY(h),
                event.getHistoricalPressure(h),
                event.getHistoricalEventTime(h)
            )
        }
        
        // 2. Process Current Point
        action(
            event.x,
            event.y,
            event.pressure,
            event.eventTime
        )
    }
}


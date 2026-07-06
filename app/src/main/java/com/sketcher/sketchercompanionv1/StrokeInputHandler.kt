package com.sketcher.sketchercompanionv1

import android.view.MotionEvent

/**
 * Handles converting raw Android MotionEvents into raw data points without allocation.
 */
object StrokeInputHandler {

    inline fun processEvent(event: MotionEvent, action: (x: Float, y: Float, pressure: Float, timestamp: Long) -> Unit) {
        var pointerIndex = 0
        for (i in 0 until event.pointerCount) {
            val tool = event.getToolType(i)
            if (tool == MotionEvent.TOOL_TYPE_STYLUS || tool == MotionEvent.TOOL_TYPE_ERASER) {
                pointerIndex = i
                break
            }
        }

        val historySize = event.historySize
        
        // 1. Process Historical Points (Batched events)
        for (h in 0 until historySize) {
            action(
                event.getHistoricalX(pointerIndex, h),
                event.getHistoricalY(pointerIndex, h),
                event.getHistoricalPressure(pointerIndex, h),
                event.getHistoricalEventTime(h)
            )
        }
        
        // 2. Process Current Point
        action(
            event.getX(pointerIndex),
            event.getY(pointerIndex),
            event.getPressure(pointerIndex),
            event.eventTime
        )
    }
}


package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF

object PathGenerator {

    fun generateStrokePath(
        points: List<StrokePoint>, 
        maxWidth: Float, 
        minSizeFactor: Float = 0.0f,
        smoothness: Float = 0.5f,
        brushType: String = "TECH_PEN"
    ): Triple<Path, List<PointF>, List<PointF>> {
        return if (brushType == "PERFECT_FREEHAND") {
            PerfectFreehandGenerator.generate(points, maxWidth, minSizeFactor)
        } else {
            TechnicalPenGenerator.generate(points, maxWidth, minSizeFactor, smoothness)
        }
    }
}

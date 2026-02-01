package com.skecher.sketchercompanionv1

import android.graphics.Path
import android.graphics.PointF

object PathGenerator {

    fun generateStrokePath(
        points: List<StrokePoint>, 
        maxWidth: Float, 
        minSizeFactor: Float = 0.0f,
        smoothness: Float = 0.5f
    ): Triple<Path, List<PointF>, List<PointF>> {
        return TechnicalPenGenerator.generate(points, maxWidth, minSizeFactor, smoothness)
    }
}

package com.skecher.sketchercompanionv1.utils

import android.graphics.Path
import android.graphics.PathMeasure
import com.skecher.sketchercompanionv1.StrokePoint

object PathUtils {

    fun samplePath(path: Path, spacing: Float = 2f): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val pm = PathMeasure(path, false)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        
        do {
            var dist = 0f
            val length = pm.length
            
            while (dist < length) {
                pm.getPosTan(dist, pos, tan)
                points.add(StrokePoint(pos[0], pos[1], 0.5f, 0L))
                dist += spacing
            }
            // Ensure last point is included
            pm.getPosTan(length, pos, tan)
            if (points.isNotEmpty()) {
                val last = points.last()
                if (kotlin.math.hypot(pos[0] - last.x, pos[1] - last.y) > 0.1f) {
                    points.add(StrokePoint(pos[0], pos[1], 0.5f, 0L))
                }
            } else {
                 points.add(StrokePoint(pos[0], pos[1], 0.5f, 0L))
            }

        } while (pm.nextContour())
        
        return points
    }
}

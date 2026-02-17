package com.sketcher.sketchercompanionv1

import android.graphics.Path

import android.graphics.Matrix
import android.graphics.RectF

import com.sketcher.sketchercompanionv1.dto.StrokeType

data class StrokePoint(var x: Float, var y: Float, val pressure: Float, val timestamp: Long = 0L)

data class VectorStroke(
    val points: List<StrokePoint>,
    val color: Int, // Deprecated, but keeping for compatibility if needed elsewhere
    val strokeColor: Int = color,
    val fillColor: Int = android.graphics.Color.TRANSPARENT,
    val isStrokeEnabled: Boolean = true,
    val isFillEnabled: Boolean = false,
    val maxWidth: Float,
    val path: Path,
    val fillPath: Path? = null,
    val brushType: String = "FREEHAND",
    val strokeType: StrokeType = StrokeType.FREEHAND,
    val leftPoints: List<android.graphics.PointF> = emptyList(),
    val rightPoints: List<android.graphics.PointF> = emptyList()
) : LayerElement {
    override fun getBounds(library: Map<String, ComponentDefinition>): RectF {
        val rect = RectF()
        path.computeBounds(rect, true)
        return rect
    }

    override fun transform(matrix: Matrix) {
        path.transform(matrix)
        val pts = FloatArray(2)
        points.forEach { p ->
            pts[0] = p.x
            pts[1] = p.y
            matrix.mapPoints(pts)
            p.x = pts[0]
            p.y = pts[1]
        }
    }

    override fun copyElement(): LayerElement {
        return VectorStroke(
            points = points.map { it.copy() },
            color = color,
            strokeColor = strokeColor,
            fillColor = fillColor,
            isStrokeEnabled = isStrokeEnabled,
            isFillEnabled = isFillEnabled,
            maxWidth = maxWidth,
            path = Path(path),
            fillPath = fillPath?.let { Path(it) },
            brushType = brushType,
            strokeType = strokeType,
            leftPoints = leftPoints.map { android.graphics.PointF(it.x, it.y) },
            rightPoints = rightPoints.map { android.graphics.PointF(it.x, it.y) }
        )
    }
}



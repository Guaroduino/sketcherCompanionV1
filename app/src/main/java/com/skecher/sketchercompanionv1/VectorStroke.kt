package com.skecher.sketchercompanionv1

import android.graphics.Path

import android.graphics.Matrix
import android.graphics.RectF

data class StrokePoint(var x: Float, var y: Float, val pressure: Float, val timestamp: Long = 0L)

data class VectorStroke(
    val points: List<StrokePoint>,
    val color: Int,
    val maxWidth: Float,
    val path: Path,
    val brushType: String = "TECH_PEN",
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
            maxWidth = maxWidth,
            path = Path(path),
            brushType = brushType,
            leftPoints = leftPoints.map { android.graphics.PointF(it.x, it.y) },
            rightPoints = rightPoints.map { android.graphics.PointF(it.x, it.y) }
        )
    }
}


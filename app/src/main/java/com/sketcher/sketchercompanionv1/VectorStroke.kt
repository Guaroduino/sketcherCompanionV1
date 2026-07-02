package com.sketcher.sketchercompanionv1

import android.graphics.Path

import android.graphics.Matrix
import android.graphics.RectF

import com.sketcher.sketchercompanionv1.dto.StrokeType

import com.sketcher.sketchercompanionv1.dto.FillStyle

data class StrokePoint(val x: Float, val y: Float, val pressure: Float, val timestamp: Long = 0L)

data class VectorStroke(
    var points: List<StrokePoint>,
    val strokeColor: Int,
    val fillColor: Int = android.graphics.Color.TRANSPARENT,
    val isStrokeEnabled: Boolean = true,
    val isFillEnabled: Boolean = false,
    val maxWidth: Float,
    val path: Path,
    val fillPath: Path? = null,
    val brushType: String = "FREEHAND",
    val strokeType: StrokeType = StrokeType.FREEHAND,
    val leftPoints: List<android.graphics.PointF> = emptyList(),
    val rightPoints: List<android.graphics.PointF> = emptyList(),
    val paths: List<Path> = emptyList(),
    val isFlattened: Boolean = false,
    val lineStyle: String = "SOLID",
    val isCadGeometry: Boolean = false,
    val isScreenSpaceWidth: Boolean = false,
    val paintOutlineWidth: Float = 2.0f,
    val fillStyle: FillStyle = FillStyle.Solid(fillColor),
    val watercolorJitterSegment: Float = 12.0f,
    val watercolorJitterDeviation: Float = 3.5f,
    val watercolorBlurRadius: Float = 5.0f,
    val watercolorEdgeMode: com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode = com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.BOTH,
    val watercolorCenterOpacity: Float = 0.8f,
    val watercolorEdgeRingOpacity: Float = 1.0f,
    val watercolorEdgeRingWidth: Float = 2.0f
) : LayerElement {
    @kotlin.jvm.Transient
    private var cachedJitteredPath: android.graphics.Path? = null
    @kotlin.jvm.Transient
    private var cachedJitteredSeed: Long? = null

    fun getJitteredPath(seed: Long): android.graphics.Path {
        val currentPath = cachedJitteredPath
        if (currentPath != null && cachedJitteredSeed == seed) {
            return currentPath
        }
        val newPath = com.sketcher.sketchercompanionv1.utils.JitterPathHelper.createJitterPath(
            path,
            watercolorJitterSegment,
            watercolorJitterDeviation,
            seed = seed
        )
        cachedJitteredPath = newPath
        cachedJitteredSeed = seed
        return newPath
    }

    private val cachedBounds = RectF().apply { path.computeBounds(this, true) }

    override fun getBoundingBox(library: Map<String, ComponentDefinition>): RectF {
        return cachedBounds
    }

    override fun transform(matrix: Matrix) {
        path.transform(matrix)
        fillPath?.transform(matrix)
        path.computeBounds(cachedBounds, true)
        val pts = FloatArray(2)
        points = points.map { p ->
            pts[0] = p.x
            pts[1] = p.y
            matrix.mapPoints(pts)
            p.copy(x = pts[0], y = pts[1])
        }
    }

    override fun copyElement(): LayerElement {
        return VectorStroke(
            points = points.map { it.copy() },
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
            rightPoints = rightPoints.map { android.graphics.PointF(it.x, it.y) },
            paths = paths.map { Path(it) },
            isFlattened = isFlattened,
            lineStyle = lineStyle,
            isCadGeometry = isCadGeometry,
            isScreenSpaceWidth = isScreenSpaceWidth,
            paintOutlineWidth = paintOutlineWidth,
            fillStyle = fillStyle,
            watercolorJitterSegment = watercolorJitterSegment,
            watercolorJitterDeviation = watercolorJitterDeviation,
            watercolorBlurRadius = watercolorBlurRadius,
            watercolorEdgeMode = watercolorEdgeMode,
            watercolorCenterOpacity = watercolorCenterOpacity,
            watercolorEdgeRingOpacity = watercolorEdgeRingOpacity,
            watercolorEdgeRingWidth = watercolorEdgeRingWidth
        )
    }
}



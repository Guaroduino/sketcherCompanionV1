package com.skecher.sketchercompanionv1

import android.graphics.Path

data class StrokePoint(val x: Float, val y: Float, val pressure: Float)

data class VectorStroke(
    val points: List<StrokePoint>,
    val color: Int,
    val maxWidth: Float,
    val path: Path,
    val leftPoints: List<android.graphics.PointF> = emptyList(),
    val rightPoints: List<android.graphics.PointF> = emptyList()
) : LayerElement


package com.sketcher.sketchercompanionv1.utils

import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import java.util.Random

object JitterPathHelper {
    /**
     * Generates a deterministic jittered path along the boundary of the source path.
     * Uses a fixed seed to ensure the jitter shape is completely static and never vibrates.
     * Smooths the resulting path using quadratic curves to avoid polygonal/sharp edges.
     */
    fun createJitterPath(
        sourcePath: Path,
        segmentLength: Float,
        deviation: Float,
        seed: Long = 42L
    ): Path {
        if (deviation <= 0f) {
            return Path(sourcePath)
        }

        val jitteredPath = Path()
        val pm = PathMeasure(sourcePath, false)
        val pos = FloatArray(2)
        val tan = FloatArray(2)
        
        val rand = Random(seed)
        val segLen = segmentLength.coerceAtLeast(1f)

        do {
            val length = pm.length
            if (length <= 0) continue

            val points = mutableListOf<PointF>()
            var distance = 0f

            while (distance < length) {
                if (pm.getPosTan(distance, pos, tan)) {
                    // Normal vector is perpendicular to the tangent (-tan[1], tan[0])
                    val nx = -tan[1]
                    val ny = tan[0]

                    // Deterministic perturbation along the normal vector
                    val dev = (rand.nextFloat() * 2f - 1f) * deviation
                    val px = pos[0] + nx * dev
                    val py = pos[1] + ny * dev

                    points.add(PointF(px, py))
                }
                distance += segLen
            }

            if (points.isNotEmpty()) {
                if (pm.isClosed) {
                    if (points.size >= 3) {
                        val p0 = points[0]
                        val pLast = points.last()
                        jitteredPath.moveTo((p0.x + pLast.x) / 2f, (p0.y + pLast.y) / 2f)
                        for (i in points.indices) {
                            val curr = points[i]
                            val next = points[(i + 1) % points.size]
                            jitteredPath.quadTo(curr.x, curr.y, (curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
                        }
                        jitteredPath.close()
                    } else {
                        jitteredPath.moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            jitteredPath.lineTo(points[i].x, points[i].y)
                        }
                        jitteredPath.close()
                    }
                } else {
                    if (points.size >= 3) {
                        jitteredPath.moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size - 1) {
                            val curr = points[i]
                            val next = points[i + 1]
                            jitteredPath.quadTo(curr.x, curr.y, (curr.x + next.x) / 2f, (curr.y + next.y) / 2f)
                        }
                        jitteredPath.lineTo(points.last().x, points.last().y)
                    } else {
                        jitteredPath.moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            jitteredPath.lineTo(points[i].x, points[i].y)
                        }
                    }
                }
            }
        } while (pm.nextContour())

        return jitteredPath
    }
}

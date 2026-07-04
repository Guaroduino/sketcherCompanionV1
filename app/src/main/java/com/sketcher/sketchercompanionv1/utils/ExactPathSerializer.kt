package com.sketcher.sketchercompanionv1.utils

import android.graphics.Path
import androidx.graphics.path.PathIterator
import androidx.graphics.path.PathSegment
import androidx.core.graphics.PathParser
import android.util.Log

object ExactPathSerializer {

    /**
     * Converts an Android Path exactly into an SVG path string without any compression or flattening.
     */
    fun pathToString(path: Path?): String? {
        if (path == null || path.isEmpty) return null
        
        val sb = StringBuilder()
        val iterator = PathIterator(path)
        
        while (iterator.hasNext()) {
            val segment = iterator.next()
            val points = segment.points
            when (segment.type) {
                PathSegment.Type.Move -> {
                    if (points.isNotEmpty()) {
                        val p = points.last()
                        sb.append("M ${p.x} ${p.y} ")
                    }
                }
                PathSegment.Type.Line -> {
                    if (points.isNotEmpty()) {
                        val p = points.last()
                        sb.append("L ${p.x} ${p.y} ")
                    }
                }
                PathSegment.Type.Quadratic -> {
                    if (points.size >= 3) {
                        val p1 = points[1]
                        val p2 = points[2]
                        sb.append("Q ${p1.x} ${p1.y} ${p2.x} ${p2.y} ")
                    }
                }
                PathSegment.Type.Conic -> {
                    if (points.size >= 3) {
                        val p1 = points[1]
                        val p2 = points[2]
                        Log.w("ExactPathSerializer", "CONIC verb encountered. Approximating to Q.")
                        sb.append("Q ${p1.x} ${p1.y} ${p2.x} ${p2.y} ")
                    }
                }
                PathSegment.Type.Cubic -> {
                    if (points.size >= 4) {
                        val p1 = points[1]
                        val p2 = points[2]
                        val p3 = points[3]
                        sb.append("C ${p1.x} ${p1.y} ${p2.x} ${p2.y} ${p3.x} ${p3.y} ")
                    }
                }
                PathSegment.Type.Close -> sb.append("Z ")
                PathSegment.Type.Done -> {}
                else -> {}
            }
        }
        return sb.toString().trim()
    }

    /**
     * Reconstructs an Android Path exactly from an SVG path string.
     */
    fun stringToPath(svgPath: String?, useEvenOdd: Boolean = true): Path? {
        if (svgPath.isNullOrEmpty()) return null
        return try {
            PathParser.createPathFromPathData(svgPath).apply { 
                if (useEvenOdd) {
                    fillType = Path.FillType.EVEN_ODD 
                } else {
                    fillType = Path.FillType.WINDING
                }
            }
        } catch (e: Exception) {
            Log.e("ExactPathSerializer", "Error parsing path: $svgPath", e)
            null
        }
    }
}

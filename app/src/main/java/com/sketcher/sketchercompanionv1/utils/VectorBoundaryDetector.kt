package com.sketcher.sketchercompanionv1.utils

import android.graphics.Path
import android.graphics.RectF
import android.graphics.Region
import com.sketcher.sketchercompanionv1.LayerElement
import com.sketcher.sketchercompanionv1.VectorStroke

object VectorBoundaryDetector {

    /**
     * Checks if a point (x, y) hits an existing fill stroke or filled region.
     */
    fun findExistingFillAt(
        x: Float,
        y: Float,
        elements: List<LayerElement>
    ): VectorStroke? {
        val testRegion = Region()
        val clipRegion = Region(-10000, -10000, 10000, 10000)
        
        for (element in elements.reversed()) {
            if (element is VectorStroke) {
                if (element.isFillEnabled || element.brushType == "FILL") {
                    testRegion.setPath(element.path, clipRegion)
                    if (testRegion.contains(x.toInt(), y.toInt())) {
                        return element
                    }
                }
            }
        }
        return null
    }

    /**
     * Detects or constructs a boundary path around a tap point (x, y).
     * If no enclosing stroke geometry is hit, creates a default localized fill circle/rect.
     */
    fun detectBoundaryPath(
        tapX: Float,
        tapY: Float,
        strokes: List<VectorStroke>,
        canvasBounds: RectF
    ): Path {
        val path = Path()
        val clipRegion = Region(
            canvasBounds.left.toInt(),
            canvasBounds.top.toInt(),
            canvasBounds.right.toInt(),
            canvasBounds.bottom.toInt()
        )
        val strokeRegion = Region()

        // Check if tap point falls inside any closed stroke path
        for (stroke in strokes.reversed()) {
            strokeRegion.setPath(stroke.path, clipRegion)
            if (strokeRegion.contains(tapX.toInt(), tapY.toInt())) {
                path.addPath(stroke.path)
                return path
            }
        }

        // Fallback: Generate a clean circular fill area around tap point if open space
        val radius = 50f
        path.addCircle(tapX, tapY, radius, Path.Direction.CW)
        return path
    }
}

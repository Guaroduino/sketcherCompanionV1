package com.sketcher.sketchercompanionv1.utils

import android.graphics.Path
import android.os.Build

object PathToSvgHelper {

    /**
     * Converts an Android Path to an SVG path data string (d attribute).
     * Uses Path.approximate to flatten the path into line segments.
     */
    fun pathToString(path: Path): String {
        if (path.isEmpty) return ""

        // approximate returns [fraction, x, y, fraction, x, y, ...]
        // error = 0.5 pixels
        val approx = path.approximate(0.5f)
        val sb = StringBuilder()

        if (approx.isNotEmpty()) {
            // First point: Move To
            val x0 = approx[1]
            val y0 = approx[2]
            sb.append("M$x0,$y0")

            // Subsequent points: Line To
            for (i in 3 until approx.size step 3) {
                val x = approx[i + 1]
                val y = approx[i + 2]
                sb.append(" L$x,$y")
            }
        }
        return sb.toString()
    }
}


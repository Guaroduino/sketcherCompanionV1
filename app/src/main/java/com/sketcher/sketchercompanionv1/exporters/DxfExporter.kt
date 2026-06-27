package com.sketcher.sketchercompanionv1.exporters

import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.VectorStroke
import com.sketcher.sketchercompanionv1.GroupElement
import com.sketcher.sketchercompanionv1.dto.StrokeType
import com.sketcher.sketchercompanionv1.utils.GeometryUtils
import android.graphics.PointF
import android.graphics.Matrix
import java.io.File
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object DxfExporter {

    fun export(layers: List<Layer>, file: File) {
        export(layers, java.io.FileOutputStream(file))
    }

    fun export(layers: List<Layer>, outputStream: OutputStream) {
        val writer = OutputStreamWriter(outputStream)
        
        // Use dot separator for decimals
        val symbols = DecimalFormatSymbols(Locale.US)
        val df = DecimalFormat("0.####", symbols)

        try {
            // HEADER
            writeCode(writer, 0, "SECTION")
            writeCode(writer, 2, "HEADER")
            writeCode(writer, 9, "\$ACADVER")
            writeCode(writer, 1, "AC1015") // AutoCAD 2000
            writeCode(writer, 9, "\$INSUNITS")
            writeCode(writer, 70, "4") // Millimeters (common standard)
            writeCode(writer, 0, "ENDSEC")

            // TABLES (Layer Table)
            writeCode(writer, 0, "SECTION")
            writeCode(writer, 2, "TABLES")
            writeCode(writer, 0, "TABLE")
            writeCode(writer, 2, "LAYER")
            
            // Count layers + 1 (Layer 0)
            val exportableLayers = layers.filter { it.elements.isNotEmpty() }
            writeCode(writer, 70, "${exportableLayers.size + 1}")
            
            // Layer 0 (Default)
            writeLayer(writer, "0", 7)
            
            // Custom Layers
            exportableLayers.forEach { layer ->
                // Map layer name (sanitize)
                val safeName = sanitizeLayerName(layer.name)
                writeLayer(writer, safeName, 7) // Default to white for layer, entities will override if needed
            }
            
            writeCode(writer, 0, "ENDTAB")
            writeCode(writer, 0, "ENDSEC")

            // ENTITIES
            writeCode(writer, 0, "SECTION")
            writeCode(writer, 2, "ENTITIES")

            for (layer in exportableLayers) {
                val layerName = sanitizeLayerName(layer.name)
                val strokes = mutableListOf<VectorStroke>()
                collectStrokes(layer.elements, strokes, Matrix())

                for (stroke in strokes) {
                    when (stroke.strokeType) {
                        StrokeType.LINE -> writeLine(writer, stroke, layerName, df)
                        StrokeType.CIRCLE -> writeCircle(writer, stroke, layerName, df)
                        StrokeType.ARC -> writeArc(writer, stroke, layerName, df)
                        StrokeType.ELLIPSE -> writeEllipse(writer, stroke, layerName, df)
                        StrokeType.SPLINE, StrokeType.BEZIER -> writeSpline(writer, stroke, layerName, df)
                        else -> writeLwPolyline(writer, stroke, layerName, df)
                    }
                }
            }

            writeCode(writer, 0, "ENDSEC")

            // EOF
            writeCode(writer, 0, "EOF")

        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            writer.flush()
            writer.close()
        }
    }
    
    private fun collectStrokes(
        elements: List<com.sketcher.sketchercompanionv1.LayerElement>,
        target: MutableList<VectorStroke>,
        parentMatrix: Matrix
    ) {
        elements.forEach { element ->
            if (element is VectorStroke) {
                val m = Matrix(parentMatrix) 
                val copy = element.copyElement() as VectorStroke
                copy.transform(m)
                target.add(copy)
            } else if (element is GroupElement) {
                val newMatrix = Matrix(parentMatrix)
                newMatrix.preConcat(element.matrix)
                collectStrokes(element.elements, target, newMatrix)
            }
        }
    }

    private fun writeLayer(writer: java.io.Writer, name: String, color: Int) {
        writeCode(writer, 0, "LAYER")
        writeCode(writer, 2, name)
        writeCode(writer, 70, "0")
        writeCode(writer, 62, color.toString())
        writeCode(writer, 6, "CONTINUOUS")
    }

    private fun writeLine(writer: java.io.Writer, stroke: VectorStroke, layerName: String, df: DecimalFormat) {
        if (stroke.points.size < 2) return
        val p1 = stroke.points.first()
        val p2 = stroke.points.last()

        writeCode(writer, 0, "LINE")
        writeCode(writer, 8, layerName)
        val aci = mapColorToAci(stroke.strokeColor)
        writeCode(writer, 62, aci.toString())
        writeCode(writer, 10, df.format(p1.x))
        writeCode(writer, 20, df.format(-p1.y))
        writeCode(writer, 30, "0.0")
        writeCode(writer, 11, df.format(p2.x))
        writeCode(writer, 21, df.format(-p2.y))
        writeCode(writer, 31, "0.0")
    }

    private fun writeCircle(writer: java.io.Writer, stroke: VectorStroke, layerName: String, df: DecimalFormat) {
        if (stroke.points.size < 2) return
        val center = stroke.points[0]
        val edge = stroke.points[1]
        val r = kotlin.math.hypot(edge.x - center.x, edge.y - center.y)

        writeCode(writer, 0, "CIRCLE")
        writeCode(writer, 8, layerName)
        val aci = mapColorToAci(stroke.strokeColor)
        writeCode(writer, 62, aci.toString())
        writeCode(writer, 10, df.format(center.x))
        writeCode(writer, 20, df.format(-center.y))
        writeCode(writer, 30, "0.0")
        writeCode(writer, 40, df.format(r))
    }

    private fun writeArc(writer: java.io.Writer, stroke: VectorStroke, layerName: String, df: DecimalFormat) {
        if (stroke.points.size < 3) return
        val p1 = PointF(stroke.points[0].x, -stroke.points[0].y)
        val p2 = PointF(stroke.points[1].x, -stroke.points[1].y)
        val p3 = PointF(stroke.points[2].x, -stroke.points[2].y)
        val arc = GeometryUtils.getArcParams(p1, p2, p3)
        if (arc != null) {
            writeCode(writer, 0, "ARC")
            writeCode(writer, 8, layerName)
            val aci = mapColorToAci(stroke.strokeColor)
            writeCode(writer, 62, aci.toString())
            writeCode(writer, 10, df.format(arc.center.x))
            writeCode(writer, 20, df.format(arc.center.y))
            writeCode(writer, 30, "0.0")
            writeCode(writer, 40, df.format(arc.radius))
            
            val startAngle: Float
            val endAngle: Float
            if (arc.sweepAngleDeg >= 0) {
                startAngle = normalizeAngle(arc.startAngleDeg)
                endAngle = normalizeAngle(arc.startAngleDeg + arc.sweepAngleDeg)
            } else {
                startAngle = normalizeAngle(arc.startAngleDeg + arc.sweepAngleDeg)
                endAngle = normalizeAngle(arc.startAngleDeg)
            }
            writeCode(writer, 50, df.format(startAngle))
            writeCode(writer, 51, df.format(endAngle))
        } else {
            // Fallback to LINE from p1 to p3
            writeCode(writer, 0, "LINE")
            writeCode(writer, 8, layerName)
            val aci = mapColorToAci(stroke.strokeColor)
            writeCode(writer, 62, aci.toString())
            writeCode(writer, 10, df.format(stroke.points[0].x))
            writeCode(writer, 20, df.format(-stroke.points[0].y))
            writeCode(writer, 30, "0.0")
            writeCode(writer, 11, df.format(stroke.points[2].x))
            writeCode(writer, 21, df.format(-stroke.points[2].y))
            writeCode(writer, 31, "0.0")
        }
    }

    private fun writeEllipse(writer: java.io.Writer, stroke: VectorStroke, layerName: String, df: DecimalFormat) {
        if (stroke.points.size < 3) {
            if (stroke.points.size == 2) {
                writeCircle(writer, stroke, layerName, df)
            }
            return
        }
        val center = stroke.points[0]
        val pX = stroke.points[1]
        val pY = stroke.points[2]
        val rX = kotlin.math.hypot(pX.x - center.x, pX.y - center.y)
        val rY = kotlin.math.hypot(pY.x - center.x, pY.y - center.y)

        writeCode(writer, 0, "ELLIPSE")
        writeCode(writer, 8, layerName)
        val aci = mapColorToAci(stroke.strokeColor)
        writeCode(writer, 62, aci.toString())
        writeCode(writer, 10, df.format(center.x))
        writeCode(writer, 20, df.format(-center.y))
        writeCode(writer, 30, "0.0")

        if (rX >= rY) {
            writeCode(writer, 11, df.format(rX))
            writeCode(writer, 21, "0.0")
            writeCode(writer, 31, "0.0")
            val ratio = if (rX > 0f) rY / rX else 1.0f
            writeCode(writer, 40, df.format(ratio))
        } else {
            writeCode(writer, 11, "0.0")
            writeCode(writer, 21, df.format(rY))
            writeCode(writer, 31, "0.0")
            val ratio = if (rY > 0f) rX / rY else 1.0f
            writeCode(writer, 40, df.format(ratio))
        }

        writeCode(writer, 41, "0.0")
        writeCode(writer, 42, "6.283185307179586") // 2 * PI
    }

    private fun writeSpline(writer: java.io.Writer, stroke: VectorStroke, layerName: String, df: DecimalFormat) {
        val segments = collectBezierSegments(stroke)
        if (segments.isEmpty()) return

        val controlPoints = mutableListOf<PointF>()
        controlPoints.add(segments[0][0])
        controlPoints.add(segments[0][1])
        controlPoints.add(segments[0][2])
        controlPoints.add(segments[0][3])
        for (i in 1 until segments.size) {
            controlPoints.add(segments[i][1])
            controlPoints.add(segments[i][2])
            controlPoints.add(segments[i][3])
        }

        val knots = mutableListOf<Float>()
        val n = segments.size
        for (k in 0..3) knots.add(0.0f)
        for (i in 1 until n) {
            val valK = i.toFloat()
            for (k in 0..2) knots.add(valK)
        }
        val valN = n.toFloat()
        for (k in 0..3) knots.add(valN)

        writeCode(writer, 0, "SPLINE")
        writeCode(writer, 8, layerName)
        val aci = mapColorToAci(stroke.strokeColor)
        writeCode(writer, 62, aci.toString())
        writeCode(writer, 70, "8") // Planar spline
        writeCode(writer, 71, "3") // Degree = 3 (cubic)
        writeCode(writer, 72, knots.size.toString()) // Number of knots
        writeCode(writer, 73, controlPoints.size.toString()) // Number of control points
        writeCode(writer, 74, "0") // Fit points count (none)

        // Write Knots
        for (knot in knots) {
            writeCode(writer, 40, df.format(knot))
        }

        // Write Control Points
        for (cp in controlPoints) {
            writeCode(writer, 10, df.format(cp.x))
            writeCode(writer, 20, df.format(-cp.y))
            writeCode(writer, 30, "0.0")
        }
    }

    private fun collectBezierSegments(stroke: VectorStroke): List<List<PointF>> {
        val segments = mutableListOf<List<PointF>>()
        val pts = stroke.points
        if (pts.isEmpty()) return segments

        if (stroke.strokeType == StrokeType.BEZIER) {
            val numSegments = (pts.size - 1) / 3
            for (i in 0 until numSegments) {
                val p0 = PointF(pts[3 * i].x, pts[3 * i].y)
                val p1 = PointF(pts[3 * i + 1].x, pts[3 * i + 1].y)
                val p2 = PointF(pts[3 * i + 2].x, pts[3 * i + 2].y)
                val p3 = PointF(pts[3 * i + 3].x, pts[3 * i + 3].y)
                segments.add(listOf(p0, p1, p2, p3))
            }
            val rem = (pts.size - 1) % 3
            if (rem > 0) {
                val lastAnchor = pts[pts.size - 1 - rem]
                val lastPt = pts[pts.size - 1]
                val p0 = PointF(lastAnchor.x, lastAnchor.y)
                val p1 = PointF(lastAnchor.x + (lastPt.x - lastAnchor.x) / 3f, lastAnchor.y + (lastPt.y - lastAnchor.y) / 3f)
                val p2 = PointF(lastAnchor.x + 2 * (lastPt.x - lastAnchor.x) / 3f, lastAnchor.y + 2 * (lastPt.y - lastAnchor.y) / 3f)
                val p3 = PointF(lastPt.x, lastPt.y)
                segments.add(listOf(p0, p1, p2, p3))
            }
        } else if (stroke.strokeType == StrokeType.SPLINE) {
            val unique = pts.filterIndexed { index, curr ->
                index == 0 || kotlin.math.hypot(curr.x - pts[index - 1].x, curr.y - pts[index - 1].y) > 0.01f
            }
            if (unique.size == 2) {
                val p0 = PointF(unique[0].x, unique[0].y)
                val p3 = PointF(unique[1].x, unique[1].y)
                val p1 = PointF(p0.x + (p3.x - p0.x) / 3f, p0.y + (p3.y - p0.y) / 3f)
                val p2 = PointF(p0.x + 2 * (p3.x - p0.x) / 3f, p0.y + 2 * (p3.y - p0.y) / 3f)
                segments.add(listOf(p0, p1, p2, p3))
            } else if (unique.size > 2) {
                for (i in 0 until unique.size - 1) {
                    val p0 = unique[kotlin.math.max(0, i - 1)]
                    val p1 = unique[i]
                    val p2 = unique[i + 1]
                    val p3 = unique[kotlin.math.min(unique.size - 1, i + 2)]
                    
                    val cp1x = p1.x + (p2.x - p0.x) / 6f
                    val cp1y = p1.y + (p2.y - p0.y) / 6f
                    val cp2x = p2.x - (p3.x - p1.x) / 6f
                    val cp2y = p2.y - (p3.y - p1.y) / 6f

                    segments.add(listOf(
                        PointF(p1.x, p1.y),
                        PointF(cp1x, cp1y),
                        PointF(cp2x, cp2y),
                        PointF(p2.x, p2.y)
                    ))
                }
            }
        }
        return segments
    }

    private fun normalizeAngle(a: Float): Float {
        var norm = a % 360f
        if (norm < 0) norm += 360f
        return norm
    }

    private fun writeLwPolyline(writer: java.io.Writer, stroke: VectorStroke, layerExample: String, df: DecimalFormat) {
        if (stroke.points.isEmpty()) return

        writeCode(writer, 0, "LWPOLYLINE")
        writeCode(writer, 8, layerExample) 
        writeCode(writer, 90, stroke.points.size.toString()) // Vertex count
        writeCode(writer, 70, "0") // Open polyline
        
        // Write Color Overrides if transparent or special
        // Map Android Color to ACI
        val aci = mapColorToAci(stroke.strokeColor)
        writeCode(writer, 62, aci.toString())

        for (p in stroke.points) {
            writeCode(writer, 10, df.format(p.x))
            // Invert Y for CAD (Y-Up)
            writeCode(writer, 20, df.format(-p.y))
        }
    }

    private fun writeCode(writer: java.io.Writer, code: Int, value: String) {
        writer.write("$code\n")
        writer.write("$value\n")
    }
    
    private fun sanitizeLayerName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9_ -]"), "_").take(255)
    }
    
    // Simple Inverse Color Mapping
    private fun mapColorToAci(color: Int): Int {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        
        // Exact matches
        if (r > 200 && g < 50 && b < 50) return 1 // Red
        if (r > 200 && g > 200 && b < 50) return 2 // Yellow
        if (r < 50 && g > 200 && b < 50) return 3 // Green
        if (r < 50 && g > 200 && b > 200) return 4 // Cyan
        if (r < 50 && g < 50 && b > 200) return 5 // Blue
        if (r > 200 && g < 50 && b > 200) return 6 // Magenta
        if (r < 50 && g < 50 && b < 50) return 7 // Black (on white) -> 7 in DXF is White/Black
        if (r > 200 && g > 200 && b > 200) return 7 // White -> 7
        
        return 7 // Default
    }
}


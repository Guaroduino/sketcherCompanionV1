package com.sketcher.sketchercompanionv1.exporters

import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.VectorStroke
import java.io.File
import java.io.FileWriter
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
                for (element in layer.elements) {
                    if (element is VectorStroke) {
                        writeLwPolyline(writer, element, layerName, df)
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
    
    private fun writeLayer(writer: java.io.Writer, name: String, color: Int) {
        writeCode(writer, 0, "LAYER")
        writeCode(writer, 2, name)
        writeCode(writer, 70, "0")
        writeCode(writer, 62, color.toString())
        writeCode(writer, 6, "CONTINUOUS")
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


package com.skecher.sketchercompanionv1.importers

import android.graphics.Path
import android.graphics.RectF
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.Scanner
import java.io.IOException
import kotlin.math.min
import kotlin.math.max

data class DxfPathData(
    val path: Path,
    val bounds: RectF,
    val layerName: String = "0",
    val color: Int? = null, // Android Color Int
    val strokeWidth: Float? = null, // in pixels/units
    val isClosed: Boolean = false
)

data class DxfImportData(
    val paths: List<DxfPathData>,
    val totalBounds: RectF
)

object DxfImporter {

    fun parse(file: File): DxfImportData {
        return parse(file.inputStream())
    }

    fun parse(inputStream: java.io.InputStream): DxfImportData {
        val paths = mutableListOf<DxfPathData>()
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

        val reader = BufferedReader(java.io.InputStreamReader(inputStream))
        var line: String? = reader.readLine()
        
        var currentSection = ""
        var currentEntity = ""
        
        // Entity State
        var currentLayer = "0"
        var currentColor: Int? = null
        var currentLineweight: Float? = null // stored as 1/100 mm in DXF (e.g. 50 = 0.5mm)
        
        // LWPOLYLINE State
        var polylinePoints = mutableListOf<Pair<Float, Float>>()
        var isPolylineClosed = false
        
        // LINE State
        var lineX1: Float? = null
        var lineY1: Float? = null
        var lineX2: Float? = null
        var lineY2: Float? = null

        var currentCode = -1

        try {
            while (line != null) {
                // Parse Code
                val codeStr = line.trim()
                val code = codeStr.toIntOrNull()
                
                // Read Value
                val value = reader.readLine()?.trim()
                if (value == null) break

                if (code == null) {
                    line = reader.readLine()
                    continue
                }

                when (code) {
                    0 -> {
                        // New Entity or Section Start - Finish previous entity
                        if (currentEntity == "LWPOLYLINE" && polylinePoints.isNotEmpty()) {
                            val path = createPathFromPoints(polylinePoints, isPolylineClosed)
                            val pathBounds = RectF()
                            path.computeBounds(pathBounds, true)
                            
                            // Convert Lineweight (1/100mm) to width units (approx)
                            // If lineweight is present (e.g. 50), it means 0.5mm.
                            // We'll store it raw or scaled? Let's just store the parsed value 
                            // and let ViewModel decide based on canvas scale. 
                            // But usually strokeWidth in apps is in pixels. 
                            // Let's store a normalized value if possible, or just the raw unit value.
                            // Since we don't know the DPI yet, let's just store it as is (units).
                            // ACI Colors need mapping.
                            
                            paths.add(DxfPathData(path, pathBounds, currentLayer, currentColor, currentLineweight?.div(100f), isPolylineClosed)) // convert to mm
                            
                            minX = min(minX, pathBounds.left)
                            minY = min(minY, pathBounds.top)
                            maxX = max(maxX, pathBounds.right)
                            maxY = max(maxY, pathBounds.bottom)
                        } else if (currentEntity == "LINE" && lineX1 != null && lineY1 != null && lineX2 != null && lineY2 != null) {
                            val path = Path()
                            path.moveTo(lineX1!!, -lineY1!!) 
                            path.lineTo(lineX2!!, -lineY2!!)
                            
                            val pathBounds = RectF()
                            path.computeBounds(pathBounds, true)
                            paths.add(DxfPathData(path, pathBounds, currentLayer, currentColor, currentLineweight?.div(100f), false)) // Lines are open

                            minX = min(minX, pathBounds.left)
                            minY = min(minY, pathBounds.top)
                            maxX = max(maxX, pathBounds.right)
                            maxY = max(maxY, pathBounds.bottom)
                        }

                        // Reset Entity State
                        polylinePoints.clear()
                        isPolylineClosed = false
                        lineX1 = null
                        lineY1 = null
                        lineX2 = null
                        lineY2 = null
                        currentLayer = "0"
                        currentColor = null
                        currentLineweight = null

                        if (value == "SECTION") {
                            currentSection = "SECTION" 
                        } else if (value == "ENDSEC") {
                            currentSection = ""
                        } else if (value == "LWPOLYLINE") {
                           currentEntity = "LWPOLYLINE"
                        } else if (value == "LINE") {
                           currentEntity = "LINE"
                        } else {
                           currentEntity = value
                        }
                    }
                    2 -> {
                        if (currentSection == "SECTION") {
                            currentSection = value 
                        }
                    }
                    8 -> { // Layer Name
                         if (currentSection == "ENTITIES") {
                             currentLayer = value
                         }
                    }
                    62 -> { // Color Index (ACI)
                         if (currentSection == "ENTITIES") {
                             val aci = value.toIntOrNull()
                             if (aci != null) {
                                 currentColor = mapAciToColor(aci)
                             }
                         }
                    }
                    370 -> { // Lineweight Enum (1/100 mm)
                         if (currentSection == "ENTITIES") {
                             currentLineweight = value.toFloatOrNull()
                         }
                    }
                    10 -> { // X
                        if (currentSection == "ENTITIES") {
                            if (currentEntity == "LWPOLYLINE") {
                                currentCode = 10
                                polylinePoints.add(value.toFloat() to 0f) 
                            } else if (currentEntity == "LINE") {
                                lineX1 = value.toFloat()
                            }
                        }
                    }
                    20 -> { // Y
                        if (currentSection == "ENTITIES") {
                            if (currentEntity == "LWPOLYLINE") {
                                if (polylinePoints.isNotEmpty()) {
                                    val lastIndex = polylinePoints.lastIndex
                                    val (x, _) = polylinePoints[lastIndex]
                                    polylinePoints[lastIndex] = x to -value.toFloat()
                                }
                            } else if (currentEntity == "LINE") {
                                lineY1 = value.toFloat()
                            }
                        }
                    }
                    11 -> { 
                         if (currentSection == "ENTITIES" && currentEntity == "LINE") {
                             lineX2 = value.toFloat()
                         }
                    }
                    21 -> { 
                         if (currentSection == "ENTITIES" && currentEntity == "LINE") {
                             lineY2 = value.toFloat()
                         }
                    }
                    70 -> {
                        if (currentSection == "ENTITIES" && currentEntity == "LWPOLYLINE") {
                            val flags = value.toInt()
                            if ((flags and 1) != 0) {
                                isPolylineClosed = true
                            }
                        }
                    }
                }
                
                line = reader.readLine()
            }
            
            // Handle last entity
            if (currentEntity == "LWPOLYLINE" && polylinePoints.isNotEmpty()) {
                val path = createPathFromPoints(polylinePoints, isPolylineClosed)
                val pathBounds = RectF()
                path.computeBounds(pathBounds, true)
                paths.add(DxfPathData(path, pathBounds, currentLayer, currentColor, currentLineweight?.div(100f), isPolylineClosed))
                minX = min(minX, pathBounds.left)
                minY = min(minY, pathBounds.top)
                maxX = max(maxX, pathBounds.right)
                maxY = max(maxY, pathBounds.bottom)
            } else if (currentEntity == "LINE" && lineX1 != null && lineY1 != null && lineX2 != null && lineY2 != null) {
                val path = Path()
                path.moveTo(lineX1!!, -lineY1!!)
                path.lineTo(lineX2!!, -lineY2!!)
                val pathBounds = RectF()
                path.computeBounds(pathBounds, true)
                paths.add(DxfPathData(path, pathBounds, currentLayer, currentColor, currentLineweight?.div(100f), false))
                minX = min(minX, pathBounds.left)
                minY = min(minY, pathBounds.top)
                maxX = max(maxX, pathBounds.right)
                maxY = max(maxY, pathBounds.bottom)
            }


        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                reader.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        val totalBounds = if (paths.isNotEmpty()) {
            RectF(minX, minY, maxX, maxY)
        } else {
            RectF(0f, 0f, 0f, 0f)
        }

        return DxfImportData(paths, totalBounds)
    }

    private fun createPathFromPoints(points: List<Pair<Float, Float>>, closed: Boolean): Path {
        val path = Path()
        if (points.isNotEmpty()) {
            path.moveTo(points[0].first, points[0].second)
            for (i in 1 until points.size) {
                path.lineTo(points[i].first, points[i].second)
            }
            if (closed) {
                path.close()
            }
        }
        return path
    }
    
    // Simple ACI Mapping (Approximate standard colors)
    private fun mapAciToColor(aci: Int): Int {
        return when (aci) {
            1 -> android.graphics.Color.RED
            2 -> android.graphics.Color.YELLOW
            3 -> android.graphics.Color.GREEN
            4 -> android.graphics.Color.CYAN
            5 -> android.graphics.Color.BLUE
            6 -> android.graphics.Color.MAGENTA
            7 -> android.graphics.Color.BLACK // White on Black screen, Black on White. Use Black for now.
            8 -> android.graphics.Color.DKGRAY
            9 -> android.graphics.Color.LTGRAY
            else -> android.graphics.Color.BLACK // Default
        }
    }
}

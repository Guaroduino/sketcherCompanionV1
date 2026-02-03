package com.skecher.sketchercompanionv1.utils

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInput
import androidx.ink.strokes.StrokeInputBatch
import com.skecher.sketchercompanionv1.FillData
import com.skecher.sketchercompanionv1.Layer
import com.skecher.sketchercompanionv1.VectorStroke
import com.skecher.sketchercompanionv1.StrokePoint
import com.skecher.sketchercompanionv1.GroupElement
import com.skecher.sketchercompanionv1.dto.*

// --- PROJECT LEVEL ---
// Assumed to be called from ViewModel context where we have the state

// --- LAYER MAPPERS ---

fun Layer.toLayerJson(): LayerJson {
    val elementsJson = this.elements.map { it.toLayerElementJson() }
    
    return LayerJson(
        id = this.id,
        name = this.name,
        isVisible = this.isVisible,
        opacity = this.opacity,
        elements = elementsJson
    )
}

fun com.skecher.sketchercompanionv1.LayerElement.toLayerElementJson(): LayerElementJson {
    return when (this) {
        is com.skecher.sketchercompanionv1.AndroidInkElement -> LayerElementJson(
            type = "INK",
            inkStroke = this.stroke.toStrokeJson()
        )
        is com.skecher.sketchercompanionv1.VectorStroke -> LayerElementJson(
            type = "VECTOR",
            vectorStroke = this.toVectorStrokeJson()
        )
        is com.skecher.sketchercompanionv1.FillData -> LayerElementJson(
            type = "FILL",
            fill = this.toFillDataJson()
        )
        is com.skecher.sketchercompanionv1.ImageElement -> LayerElementJson(
            type = "IMAGE",
            image = ImageElementJson(
                fileName = this.imageFileName,
                matrixValues = this.matrixValues.toList()
            )
        )
        is com.skecher.sketchercompanionv1.SvgElement -> LayerElementJson(
            type = "SVG",
            svg = SvgElementJson(
                fileName = this.svgFileName,
                id = this.id,
                matrixValues = this.matrixValues.toList()
            )
        )
        is com.skecher.sketchercompanionv1.GroupElement -> {
            val mValues = FloatArray(9)
            this.matrix.getValues(mValues)
            LayerElementJson(
                type = "GROUP",
                group = GroupElementJson(
                    id = this.id,
                    elements = this.elements.map { it.toLayerElementJson() },
                    matrixValues = mValues.toList()
                )
            )
        }
    }
}

fun LayerElementJson.toLayerElement(
    bitmapLoader: (String) -> android.graphics.Bitmap?,
    svgLoader: (String) -> String?
): com.skecher.sketchercompanionv1.LayerElement {
    return when (this.type) {
        "INK" -> com.skecher.sketchercompanionv1.AndroidInkElement(this.inkStroke!!.toStroke())
        "VECTOR" -> this.vectorStroke!!.toVectorStroke()
        "FILL" -> this.fill!!.toFillData()
        "IMAGE" -> {
            val imgJson = this.image!!
            val matrix = Matrix()
            matrix.setValues(imgJson.matrixValues.toFloatArray())
            com.skecher.sketchercompanionv1.ImageElement(
                bitmap = bitmapLoader(imgJson.fileName)!!,
                imageFileName = imgJson.fileName,
                matrix = matrix
            )
        }
        "SVG" -> {
            val svgJson = this.svg!!
            com.skecher.sketchercompanionv1.SvgElement(
                id = svgJson.id,
                svgFileName = svgJson.fileName,
                svgContent = svgLoader(svgJson.fileName)!!,
                matrixValues = svgJson.matrixValues.toFloatArray()
            )
        }
        "GROUP" -> {
            val groupJson = this.group!!
            val matrix = Matrix()
            matrix.setValues(groupJson.matrixValues.toFloatArray())
            com.skecher.sketchercompanionv1.GroupElement(
                id = groupJson.id,
                elements = groupJson.elements.map { it.toLayerElement(bitmapLoader, svgLoader) },
                matrix = matrix
            )
        }
        else -> throw IllegalArgumentException("Unknown type: ${this.type}")
    }
}


fun VectorStroke.toVectorStrokeJson(): VectorStrokeJson {
    // Basic mapping
    return VectorStrokeJson(
        points = this.points.map { StrokePointJson(it.x, it.y, it.pressure) },
        color = this.color,
        maxWidth = this.maxWidth
    )
}

fun LayerJson.toLayer(
    bitmapLoader: (String) -> android.graphics.Bitmap?,
    svgLoader: (String) -> String?
): Layer {
    val customElements = mutableListOf<com.skecher.sketchercompanionv1.LayerElement>()
    
    // Map unified elements
    this.elements.forEach { elJson ->
        when (elJson.type) {
            "INK" -> {
                elJson.inkStroke?.let {
                    customElements.add(com.skecher.sketchercompanionv1.AndroidInkElement(it.toStroke()))
                }
            }
            "VECTOR" -> {
                elJson.vectorStroke?.let {
                    customElements.add(it.toVectorStroke())
                }
            }
            "FILL" -> {
                elJson.fill?.let {
                    customElements.add(it.toFillData())
                }
            }
            "IMAGE" -> {
                elJson.image?.let { imgJson ->
                    val bitmap = bitmapLoader(imgJson.fileName)
                    if (bitmap != null) {
                        val matrix = Matrix()
                        matrix.setValues(imgJson.matrixValues.toFloatArray())
                        customElements.add(com.skecher.sketchercompanionv1.ImageElement(
                            bitmap = bitmap,
                            imageFileName = imgJson.fileName,
                            matrix = matrix
                        ))
                    }
                }
            }
            "SVG" -> {
                elJson.svg?.let { svgJson ->
                    val content = svgLoader(svgJson.fileName)
                    if (content != null) {
                        customElements.add(com.skecher.sketchercompanionv1.SvgElement(
                            id = svgJson.id,
                            svgFileName = svgJson.fileName,
                            svgContent = content,
                            matrixValues = svgJson.matrixValues.toFloatArray()
                        ))
                    }
                }
            }
            "GROUP" -> {
                elJson.group?.let { groupJson ->
                    val children = groupJson.elements.map { childJson ->
                        childJson.toLayerElement(bitmapLoader, svgLoader)
                    }
                    val matrix = Matrix()
                    matrix.setValues(groupJson.matrixValues.toFloatArray())
                    customElements.add(com.skecher.sketchercompanionv1.GroupElement(
                        id = groupJson.id,
                        elements = children,
                        matrix = matrix
                    ))
                }
            }
        }
    }
    
    return Layer(
        id = this.id,
        name = this.name,
        elements = customElements,
        isVisible = this.isVisible,
        opacity = this.opacity
    )
}


fun VectorStrokeJson.toVectorStroke(): VectorStroke {
    val pts = this.points.map { StrokePoint(it.x, it.y, it.pressure) }
    // Reconstruct Path? 
    // We need to regenerate the Path object from points since JSON doesn't store Path commands directly/easily
    // and we want it editable/re-generatable.
    // BUT VectorStroke has a immutable 'path' property.
    // We need to use PathGenerator here to recreate it!
    
    // We assume default generator logic for now.
    // Ideally we should save the 'type' of stroke (Tech Pen vs Organic).
    // For now assuming Tech Pen unless we store metadata.
    // Or we use a generic path generator.
    
    // WARNING: This requires PathGenerator dependency here.
    // If not available, we might return empty path?
    // Let's import PathGenerator if needed.
    // Assuming com.skecher.sketchercompanionv1.PathGenerator is accessible.
    
    val (path, _, _) = com.skecher.sketchercompanionv1.PathGenerator.generateStrokePath(pts, this.maxWidth)
    
    return VectorStroke(
        points = pts,
        color = this.color,
        maxWidth = this.maxWidth,
        path = path
    )
}

// --- STROKE MAPPERS ---

fun Stroke.toStrokeJson(): StrokeJson {
    val inputsJson = mutableListOf<StrokeInputJson>()
    val inputs = this.inputs
    // Iterate manually since StrokeInputBatch is not a simple list
    for (i in 0 until inputs.size) {
        val input = inputs.get(i)
         inputsJson.add(
             StrokeInputJson(
                 x = input.x,
                 y = input.y,
                 time = input.elapsedTimeMillis,
                 pressure = input.pressure,
                 tilt = input.tiltRadians,
                 orientation = input.orientationRadians
             )
         )
    }

    return StrokeJson(
        brushFamily = null, // TODO: If Brush family is available, save it. Using default for now.
        brushColor = this.brush.colorLong,
        brushSize = this.brush.size,
        brushEpsilon = this.brush.epsilon,
        inputs = inputsJson
    )
}

fun StrokeJson.toStroke(): Stroke {
    val inputBatch = MutableStrokeInputBatch()
    this.inputs.forEach { input ->
        inputBatch.add(
            type = InputToolType.STYLUS, // Defaulting to Stylus to respect pressure/tilt
            x = input.x,
            y = input.y,
            elapsedTimeMillis = input.time,
            pressure = input.pressure,
            tiltRadians = input.tilt,
            orientationRadians = input.orientation
        )
    }

    // Create default brush (Assuming StockBrush behavior for now since Family isn't fully exposed/managed yet)
    // If you have specific families (Marker, etc), you'd select them here.
    // Create default brush (Assuming StockBrush behavior for now since Family isn't fully exposed/managed yet)
    // If you have specific families (Marker, etc), you'd select them here.
    val brush = Brush.createWithColorLong(
        family = androidx.ink.brush.StockBrushes.pressurePen(),
        colorLong = this.brushColor,
        size = this.brushSize,
        epsilon = this.brushEpsilon
    )
    
    return Stroke(brush, inputBatch)
}

// --- FILL / PATH MAPPERS ---

fun FillData.toFillDataJson(): FillJson {

    // Attempt to extract commands.
    // Since Android Path doesn't expose commands easily, and we likely generate these from Lasso (Polygon),
    // we might need a workaround.
    // However, if we assume they are simple polygons from the Lasso tool, we can try to approximate or use a wrapper.
    // REQUIRED: Ideally, FillData should preserve the original points if it was a polygon.
    // For now, we will use a flattening strategy as a fallback which generates MANY lineTo commands.
    // Ideally, we'd use proper verb extraction if available.
    
    val commands = mutableListOf<PathCommandJson>()
    
    // We can use PathMeasure to get points, but that loses "MoveTo" vs "LineTo" distinction for sub-paths if not careful.
    // Better approach: Flattening to small line segments.
    // Note: This is an approximation if the original path had curves.
    // If the User demands CURVES, we are in a tight spot without API 34+ PathIterator.
    
    val approxPoints = 0.5f // Precision
    val flattened = this.path.flatten(approxPoints) 
    
    // Default fallback: Move to first, Line to rest.
    // Note: This is NOT reconstructing curves perfectly, but given the constraints (API < 34), 
    // retrieving verbs from an arbitrary Path object is hard.
    // BUT the user said: "The DTO must save list of commands". 
    // If I cannot get them, I must simulate them as LINEs.
    // If the app has a specific way of Creating these paths (e.g. valid specific Polygon points), we should've saved THAT.
    // Assuming we only have 'Path' object:
    
    if (flattened.isNotEmpty()) {
        commands.add(PathCommandJson("MOVE", listOf(flattened[0].x, flattened[0].y)))
        for (i in 1 until flattened.size) {
            commands.add(PathCommandJson("LINE", listOf(flattened[i].x, flattened[i].y)))
        }
    }
    commands.add(PathCommandJson("CLOSE", emptyList()))

    return FillJson(
        color = this.color,
        commands = commands
    )
}

fun FillJson.toFillData(): FillData {
    val path = Path()
    if (this.commands.isNotEmpty()) {
        val first = this.commands.first()
        if (first.type == "MOVE" && first.params.size >= 2) {
            path.moveTo(first.params[0], first.params[1])
        }
        
        for (i in 1 until this.commands.size) {
            val cmd = this.commands[i]
            when (cmd.type) {
                "MOVE" -> if (cmd.params.size >= 2) path.moveTo(cmd.params[0], cmd.params[1])
                "LINE" -> if (cmd.params.size >= 2) path.lineTo(cmd.params[0], cmd.params[1])
                "QUAD" -> if (cmd.params.size >= 4) path.quadTo(cmd.params[0], cmd.params[1], cmd.params[2], cmd.params[3])
                "CUBIC" -> if (cmd.params.size >= 6) path.cubicTo(cmd.params[0], cmd.params[1], cmd.params[2], cmd.params[3], cmd.params[4], cmd.params[5])
                "CLOSE" -> path.close()
            }
        }
    }
    return FillData(path, this.color)
}

// Helper to flatten path (simplified version for context)
// In a real app we might use PathMeasure
private data class Point(val x: Float, val y: Float)

private fun Path.flatten(precision: Float): List<Point> {
    val pm = android.graphics.PathMeasure(this, false)
    val length = pm.length
    val points = mutableListOf<Point>()
    val coords = floatArrayOf(0f, 0f)
    var distance = 0f
    
    // Sample points
    // This is "heavy", but standard for serializing arbitrary paths without Iterator
    // If typical paths are short, this is fine.
    val step = 2f // Sample every 2 pixels? or precision
    
    while (distance < length) {
        pm.getPosTan(distance, coords, null)
        points.add(Point(coords[0], coords[1]))
        distance += step
    }
    // Add end
    pm.getPosTan(length, coords, null)
    points.add(Point(coords[0], coords[1]))
    
    return points
}

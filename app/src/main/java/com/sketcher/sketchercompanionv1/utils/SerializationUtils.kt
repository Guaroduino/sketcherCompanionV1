package com.sketcher.sketchercompanionv1.utils

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.PointF

import com.sketcher.sketchercompanionv1.FillData
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.VectorStroke
import com.sketcher.sketchercompanionv1.StrokePoint
import com.sketcher.sketchercompanionv1.GroupElement
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.ComponentInstance
import com.sketcher.sketchercompanionv1.LayerElement
// import com.sketcher.sketchercompanionv1.AndroidInkElement // Removed
import com.sketcher.sketchercompanionv1.SvgElement
import com.sketcher.sketchercompanionv1.ImageElement
import com.sketcher.sketchercompanionv1.PerfectFreehandGenerator
import com.sketcher.sketchercompanionv1.dto.*

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

fun LayerElement.toLayerElementJson(): LayerElementJson {
    return when (this) {

        is VectorStroke -> LayerElementJson(
            type = "VECTOR",
            vectorStroke = this.toVectorStrokeJson()
        )
        is FillData -> LayerElementJson(
            type = "FILL",
            fill = this.toFillDataJson()
        )
        is ImageElement -> LayerElementJson(
            type = "IMAGE",
            image = ImageElementJson(
                fileName = this.imageFileName,
                matrixValues = this.matrixValues.toList()
            )
        )
        is SvgElement -> LayerElementJson(
            type = "SVG",
            svg = SvgElementJson(
                fileName = this.svgFileName,
                id = this.id,
                matrixValues = this.matrixValues.toList()
            )
        )
        is GroupElement -> {
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
        is ComponentInstance -> {
            val mValues = FloatArray(9)
            this.matrix.getValues(mValues)
            LayerElementJson(
                type = "COMPONENT_INSTANCE",
                componentInstance = ComponentInstanceJson(
                    id = this.id,
                    definitionId = this.definitionId,
                    matrixValues = mValues.toList()
                )
            )
        }
        else -> throw IllegalArgumentException("Unknown LayerElement type: ${this.javaClass.simpleName}")
    }
}

fun LayerElementJson.toLayerElement(
    bitmapLoader: (String) -> android.graphics.Bitmap?,
    svgLoader: (String) -> String?
): LayerElement {
    return when (this.type) {
        "INK" -> throw IllegalArgumentException("Legacy Ink elements are no longer supported") // this.inkStroke!!.toAndroidInkElement()
        "VECTOR" -> this.vectorStroke!!.toVectorStroke()
        "FILL" -> this.fill!!.toFillData()
        "IMAGE" -> {
            val imgJson = this.image!!
            val matrix = Matrix()
            matrix.setValues(imgJson.matrixValues.toFloatArray())
            ImageElement(
                bitmap = bitmapLoader(imgJson.fileName)!!,
                imageFileName = imgJson.fileName,
                matrix = matrix
            )
        }
        "SVG" -> {
            val svgJson = this.svg!!
            SvgElement(
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
            GroupElement(
                id = groupJson.id,
                elements = groupJson.elements.map { it.toLayerElement(bitmapLoader, svgLoader) }.toMutableList(),
                matrix = matrix
            )
        }
        "COMPONENT_INSTANCE" -> {
            val instJson = this.componentInstance!!
            val matrix = Matrix()
            matrix.setValues(instJson.matrixValues.toFloatArray())
            ComponentInstance(
                id = instJson.id,
                definitionId = instJson.definitionId,
                matrix = matrix
            )
        }
        else -> throw IllegalArgumentException("Unknown type: ${this.type}")
    }
}

fun ComponentDefinition.toComponentDefinitionJson(): ComponentDefinitionJson {
    return ComponentDefinitionJson(
        id = this.id,
        elements = this.elements.map { it.toLayerElementJson() }
    )
}

fun ComponentDefinitionJson.toComponentDefinition(
    bitmapLoader: (String) -> android.graphics.Bitmap?,
    svgLoader: (String) -> String?
): ComponentDefinition {
    return ComponentDefinition(
        id = this.id,
        elements = this.elements.map { it.toLayerElement(bitmapLoader, svgLoader) }.toMutableList()
    )
}


fun VectorStroke.toVectorStrokeJson(): VectorStrokeJson {
    // Basic mapping
    return VectorStrokeJson(
        points = this.points.map { StrokePointJson(it.x, it.y, it.pressure, it.timestamp) },
        color = this.strokeColor,
        maxWidth = this.maxWidth,
        brushType = this.brushType,
        strokeType = this.strokeType,
        strokeColor = this.strokeColor,
        fillColor = this.fillColor,
        isStrokeEnabled = this.isStrokeEnabled,
        isFillEnabled = this.isFillEnabled
    )
}

fun LayerJson.toLayer(
    bitmapLoader: (String) -> android.graphics.Bitmap?,
    svgLoader: (String) -> String?
): Layer {
    val customElements = this.elements.map { elJson ->
        elJson.toLayerElement(bitmapLoader, svgLoader)
    }.toMutableList()
    
    return Layer(
        id = this.id,
        name = this.name,
        elements = customElements,
        isVisible = this.isVisible,
        opacity = this.opacity
    )
}


fun VectorStrokeJson.toVectorStroke(): VectorStroke {
    val pts = this.points.map { StrokePoint(it.x, it.y, it.pressure, it.timestamp) }
    // We use PerfectFreehandGenerator for all vector strokes now.
    // Legacy strokes will be reconstructed using the new engine.
    val result = PerfectFreehandGenerator.generate(
        pts, 
        this.maxWidth
    )
    
    val sColor = this.strokeColor ?: this.color
    val fColor = this.fillColor ?: android.graphics.Color.TRANSPARENT
    val sEnabled = this.isStrokeEnabled ?: true
    val fEnabled = this.isFillEnabled ?: false

    var fPath: android.graphics.Path? = null
    if (fEnabled && pts.size >= 3) {
        fPath = android.graphics.Path()
        fPath.moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) {
            fPath.lineTo(pts[i].x, pts[i].y)
        }
        fPath.close()
    }
    
    return VectorStroke(
        points = pts,
        strokeColor = sColor,
        fillColor = fColor,
        isStrokeEnabled = sEnabled,
        isFillEnabled = fEnabled,
        maxWidth = this.maxWidth,
        path = result.path,
        fillPath = fPath,
        brushType = this.brushType,
        strokeType = this.strokeType,
        leftPoints = result.left,
        rightPoints = result.right
    )
}

// --- STROKE MAPPERS ---



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


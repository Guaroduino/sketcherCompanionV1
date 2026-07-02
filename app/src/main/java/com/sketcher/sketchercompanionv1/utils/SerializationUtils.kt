package com.sketcher.sketchercompanionv1.utils

import android.graphics.Matrix
import androidx.compose.runtime.toMutableStateList
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

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
        elements = elementsJson,
        isVisibleOnClient = this.isVisibleOnClient
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
        is ImageElement -> {
            val cropRectVal = this.cropRect
            val cropPathVal = this.cropPath
            LayerElementJson(
                type = "IMAGE",
                image = ImageElementJson(
                    fileName = this.imageFileName,
                    matrixValues = this.matrixValues.toList(),
                    originalFileName = this.originalImageFileName,
                    transparentColors = this.transparentColors,
                    tolerance = this.tolerance,
                    transparentColorTolerances = this.transparentColorTolerances,
                    rotation = this.rotation,
                    flipHorizontal = this.flipHorizontal,
                    flipVertical = this.flipVertical,
                    cropRectLeft = cropRectVal?.left,
                    cropRectTop = cropRectVal?.top,
                    cropRectRight = cropRectVal?.right,
                    cropRectBottom = cropRectVal?.bottom,
                    cropPathPointsX = cropPathVal?.map { it.x },
                    cropPathPointsY = cropPathVal?.map { it.y }
                )
            )
        }
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
            val bitmap = bitmapLoader(imgJson.fileName) ?: android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
            
            val originalBitmap = imgJson.originalFileName?.let { bitmapLoader(it) }
            val cropRect = if (imgJson.cropRectLeft != null && imgJson.cropRectTop != null && imgJson.cropRectRight != null && imgJson.cropRectBottom != null) {
                RectF(imgJson.cropRectLeft, imgJson.cropRectTop, imgJson.cropRectRight, imgJson.cropRectBottom)
            } else null
            
            val cropPath = if (imgJson.cropPathPointsX != null && imgJson.cropPathPointsY != null && imgJson.cropPathPointsX.size == imgJson.cropPathPointsY.size) {
                imgJson.cropPathPointsX.indices.map { idx ->
                    PointF(imgJson.cropPathPointsX[idx], imgJson.cropPathPointsY[idx])
                }
            } else null
            
            val rectF = if (cropRect != null) RectF(cropRect) else null
            val transColors = imgJson.transparentColors ?: emptyList()
            val singleTol = imgJson.tolerance ?: 10f
            val transTols = imgJson.transparentColorTolerances ?: transColors.map { singleTol }
            
            ImageElement(
                bitmap = bitmap,
                imageFileName = imgJson.fileName,
                matrix = matrix,
                originalBitmap = originalBitmap,
                originalImageFileName = imgJson.originalFileName,
                transparentColors = transColors,
                tolerance = singleTol,
                cropRect = rectF,
                cropPath = cropPath,
                transparentColorTolerances = transTols,
                rotation = imgJson.rotation ?: 0f,
                flipHorizontal = imgJson.flipHorizontal ?: false,
                flipVertical = imgJson.flipVertical ?: false
            )
        }
        "SVG" -> {
            val svgJson = this.svg!!
            val content = svgLoader(svgJson.fileName) ?: ""
            SvgElement(
                id = svgJson.id,
                svgFileName = svgJson.fileName,
                svgContent = content,
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
        isFillEnabled = this.isFillEnabled,
        isCumulative = this.paths.isNotEmpty(),
        isFlattened = this.isFlattened,
        lineStyle = this.lineStyle,
        isCadGeometry = this.isCadGeometry,
        isScreenSpaceWidth = this.isScreenSpaceWidth,
        paintOutlineWidth = this.paintOutlineWidth,
        fillStyle = this.fillStyle.toFillStyleJson(),
        watercolorJitterSegment = this.watercolorJitterSegment,
        watercolorJitterDeviation = this.watercolorJitterDeviation,
        watercolorBlurRadius = this.watercolorBlurRadius,
        watercolorEdgeMode = this.watercolorEdgeMode.name,
        watercolorCenterOpacity = this.watercolorCenterOpacity,
        watercolorEdgeRingOpacity = this.watercolorEdgeRingOpacity,
        watercolorEdgeRingWidth = this.watercolorEdgeRingWidth
    )
}

fun LayerJson.toLayer(
    bitmapLoader: (String) -> android.graphics.Bitmap?,
    svgLoader: (String) -> String?
): Layer {
    val customElements = this.elements.map { elJson ->
        elJson.toLayerElement(bitmapLoader, svgLoader)
    }.toMutableStateList()
    
    return Layer(
        id = this.id,
        name = this.name,
        elements = customElements,
        isVisible = this.isVisible,
        opacity = this.opacity,
        isVisibleOnClient = this.isVisibleOnClient ?: false
    )
}


fun VectorStrokeJson.toVectorStroke(): VectorStroke {
    val pts = this.points.map { StrokePoint(it.x, it.y, it.pressure, it.timestamp) }
    val isCumul = this.isCumulative
    val settings = FreehandSettings(size = this.maxWidth, isComplete = true, simulatePressure = false)
    
    val strokeTypeVal = this.strokeType ?: StrokeType.FREEHAND
    val isCad = (this.isCadGeometry ?: false) || (strokeTypeVal != StrokeType.FREEHAND)
    val isMeshBrush = this.brushType == "FREEHAND" || this.brushType == "PLUMA" || this.brushType == "PENCIL_CUMULATIVE" || this.brushType == "PAINT" || this.brushType == "WATERCOLOR"
    
    val resultPath = if (isCad) {
        val centerline = com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(strokeTypeVal, pts)
        if (isMeshBrush) {
            val pm = android.graphics.PathMeasure(centerline, false)
            val densePoints = mutableListOf<StrokePoint>()
            val pos = FloatArray(2)
            val length = pm.length
            if (length > 0f) {
                val steps = (length / 2f).toInt().coerceIn(10, 1000)
                for (i in 0..steps) {
                    val distance = (i.toFloat() / steps) * length
                    pm.getPosTan(distance, pos, null)
                    densePoints.add(StrokePoint(pos[0], pos[1], 1.0f, 0L))
                }
            }
            val meshPath = android.graphics.Path()
            if (densePoints.isNotEmpty()) {
                PerfectFreehandGenerator.generate(densePoints, settings, 1.0f, meshPath)
            }
            meshPath
        } else {
            centerline
        }
    } else {
        if (isMeshBrush) {
            PerfectFreehandGenerator.generate(rawPoints = pts, settings = settings).path
        } else {
            val path = android.graphics.Path()
            if (pts.isNotEmpty()) {
                path.moveTo(pts[0].x, pts[0].y)
                for (i in 1 until pts.size) {
                    path.lineTo(pts[i].x, pts[i].y)
                }
                if (this.isFlattened) {
                    path.close()
                }
            }
            path
        }
    }
    
    val chunkPaths = if (isCumul && strokeTypeVal == StrokeType.FREEHAND && !this.isFlattened) {
        PerfectFreehandGenerator.generateCumulativeChunks(pts, settings, 1.0f)
    } else {
        emptyList()
    }
    
    val sColor = this.strokeColor ?: this.color
    val fColor = this.fillColor ?: android.graphics.Color.TRANSPARENT
    val sEnabled = this.isStrokeEnabled ?: true
    val fEnabled = this.isFillEnabled ?: false

    var fPath: android.graphics.Path? = null
    if (fEnabled) {
        if (isCad) {
            val isPaintOrWatercolor = this.brushType == "PAINT" || this.brushType == "WATERCOLOR"
            fPath = if (isPaintOrWatercolor) {
                android.graphics.Path(resultPath)
            } else {
                com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(strokeTypeVal, pts)
            }
        } else if (pts.size >= 3) {
            fPath = android.graphics.Path()
            fPath.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                fPath.lineTo(pts[i].x, pts[i].y)
            }
            fPath.close()
        }
    }
    
    return VectorStroke(
        points = pts,
        strokeColor = sColor,
        fillColor = fColor,
        isStrokeEnabled = sEnabled,
        isFillEnabled = fEnabled,
        maxWidth = this.maxWidth,
        path = resultPath,
        fillPath = fPath,
        brushType = this.brushType,
        strokeType = strokeTypeVal,
        leftPoints = emptyList(), // Not perfectly restorable, but usually ok
        rightPoints = emptyList(),
        paths = chunkPaths,
        isFlattened = this.isFlattened,
        lineStyle = this.lineStyle ?: "SOLID",
        isCadGeometry = isCad,
        isScreenSpaceWidth = this.isScreenSpaceWidth ?: false,
        paintOutlineWidth = this.paintOutlineWidth ?: 2.0f,
        fillStyle = this.fillStyle.toFillStyle(fColor),
        watercolorJitterSegment = this.watercolorJitterSegment ?: 12.0f,
        watercolorJitterDeviation = this.watercolorJitterDeviation ?: 3.5f,
        watercolorBlurRadius = this.watercolorBlurRadius ?: 5.0f,
        watercolorEdgeMode = try {
            com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.valueOf(this.watercolorEdgeMode ?: "BOTH")
        } catch (e: Exception) {
            com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.BOTH
        },
        watercolorCenterOpacity = this.watercolorCenterOpacity ?: 0.8f,
        watercolorEdgeRingOpacity = this.watercolorEdgeRingOpacity ?: 1.0f,
        watercolorEdgeRingWidth = this.watercolorEdgeRingWidth ?: 2.0f
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
        commands = commands,
        fillStyle = this.fillStyle.toFillStyleJson()
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
    return FillData(path, this.fillStyle.toFillStyle(this.color))
}

// --- FILLSTYLE MAPPER HELPERS ---

fun FillStyle.toFillStyleJson(): FillStyleJson {
    return when (this) {
        is FillStyle.Solid -> FillStyleJson(type = "SOLID", color = this.color)
        is FillStyle.SvgPattern -> FillStyleJson(
            type = "SVG_PATTERN",
            svgContent = this.svgContent,
            scaleX = this.scaleX,
            scaleY = this.scaleY,
            rotation = this.rotation,
            offsetX = this.offsetX,
            offsetY = this.offsetY,
            opacity = this.opacity
        )
        is FillStyle.MathTexture -> FillStyleJson(
            type = "MATH_TEXTURE",
            patternName = this.patternName,
            primaryColor = this.primaryColor,
            secondaryColor = this.secondaryColor,
            spacing = this.spacing,
            thickness = this.thickness,
            angle = this.angle,
            opacity = this.opacity
        )
        is FillStyle.ImageTexture -> FillStyleJson(
            type = "IMAGE_TEXTURE",
            imagePath = this.imagePath,
            scaleX = this.scaleX,
            scaleY = this.scaleY,
            rotation = this.rotation,
            offsetX = this.offsetX,
            offsetY = this.offsetY,
            opacity = this.opacity,
            tintColor = this.tintColor,
            tintMix = this.tintMix
        )
    }
}

fun FillStyleJson?.toFillStyle(fallbackColor: Int): FillStyle {
    if (this == null) return FillStyle.Solid(fallbackColor)
    return when (this.type) {
        "SOLID" -> FillStyle.Solid(this.color ?: fallbackColor)
        "SVG_PATTERN" -> FillStyle.SvgPattern(
            svgContent = this.svgContent ?: "",
            scaleX = this.scaleX ?: 1f,
            scaleY = this.scaleY ?: 1f,
            rotation = this.rotation ?: 0f,
            offsetX = this.offsetX ?: 0f,
            offsetY = this.offsetY ?: 0f,
            opacity = this.opacity ?: 1f
        )
        "MATH_TEXTURE" -> FillStyle.MathTexture(
            patternName = this.patternName ?: "GRID",
            primaryColor = this.primaryColor ?: android.graphics.Color.BLACK,
            secondaryColor = this.secondaryColor ?: android.graphics.Color.TRANSPARENT,
            spacing = this.spacing ?: 20f,
            thickness = this.thickness ?: 2f,
            angle = this.angle ?: 0f,
            opacity = this.opacity ?: 1f
        )
        "IMAGE_TEXTURE" -> FillStyle.ImageTexture(
            imagePath = this.imagePath ?: "",
            scaleX = this.scaleX ?: 1f,
            scaleY = this.scaleY ?: 1f,
            rotation = this.rotation ?: 0f,
            offsetX = this.offsetX ?: 0f,
            offsetY = this.offsetY ?: 0f,
            opacity = this.opacity ?: 1f,
            tintColor = this.tintColor ?: android.graphics.Color.TRANSPARENT,
            tintMix = this.tintMix ?: 0f
        )
        else -> FillStyle.Solid(fallbackColor)
    }
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


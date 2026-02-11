package com.sketcher.sketchercompanionv1

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.UnitUtils

/**
 * Handles all direct Canvas drawing operations.
 * Holds the Paint state and configuration for rendering.
 */
class RenderEngine {

    // --- PAINTS ---
    private val vectorPaint = Paint().apply {
        style = Paint.Style.STROKE // Default, changed dynamically
        isAntiAlias = true
        isDither = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    // For Image/Bitmap Support
    private val imagePaint = Paint().apply {
        isFilterBitmap = true
        isAntiAlias = true
        isDither = true
    }

    private val gridPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 0f // Hairline
    }
    
    private val debugPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f 
        color = Color.GREEN
    }
    
    private val debugPointPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    // Selection Paints
    private val selectionBoxPaint = Paint().apply {
        color = Color.parseColor("#44007AFF") // Translucent Apple Blue
        style = Paint.Style.FILL
    }
    private val selectionBorderPaint = Paint().apply {
        color = Color.parseColor("#FF007AFF")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val selectionHandlePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(5f, 0f, 2f, 0x44000000)
    }

    // --- CONFIGURATION ---
    var gridConfig: GridConfig = GridConfig()
    var scaleConfig: ScaleConfig = ScaleConfig()
    var currentUnit: DistanceUnit = DistanceUnit.M
    var canvasSizeConfig: CanvasSizeConfig? = null // For bounds/grid centering
    var canvasBackgroundColor: Int = Color.WHITE
    
    var isDebugWireframe: Boolean = false

    // --- PUBLIC DRAWING METHODS ---

    fun drawLayers(
        canvas: Canvas, 
        layers: List<Layer>, 
        viewMatrix: Matrix,
        componentLibrary: Map<String, ComponentDefinition>,
        selectionManager: SelectionManager?,
        isSelectionDragging: Boolean
    ) {
        // Draw Background
        canvas.drawColor(canvasBackgroundColor)
        
        // Draw Grid
        drawGrid(canvas, viewMatrix)
        
        // Draw Layers
        for (layer in layers) {
             if (!layer.isVisible) continue
             
             // Setup Layer Paint/Alpha
             val layerAlpha = if (layer.opacity < 1f) (layer.opacity * 255).toInt() else 255
             val saveCount = if (layerAlpha < 255) {
                 canvas.saveLayerAlpha(null, layerAlpha) 
             } else {
                 canvas.save()
             }
             
             canvas.concat(viewMatrix)
             
             for (element in layer.elements) {
                  val isSelected = selectionManager?.selectedElements?.contains(element) == true
                  if (isSelected && isSelectionDragging) continue 

                  drawElementRecursive(canvas, element, componentLibrary)
             }
             
             canvas.restoreToCount(saveCount)
        }
    }
    
    // Recursive drawing for groups/components
    fun drawElementRecursive(canvas: Canvas, element: LayerElement, library: Map<String, ComponentDefinition>) {
         when (element) {
             is VectorStroke -> drawVectorStroke(canvas, element)
             is FillData -> drawFill(canvas, element)
             is GroupElement -> {
                 canvas.save()
                 canvas.concat(element.matrix)
                 element.elements.forEach { drawElementRecursive(canvas, it, library) }
                 canvas.restore()
             }
             is ComponentInstance -> {
                 val def = library[element.definitionId]
                 if (def != null) {
                     canvas.save()
                     canvas.concat(element.matrix)
                     def.elements.forEach { drawElementRecursive(canvas, it, library) }
                     canvas.restore()
                 }
             }
             is ImageElement -> {
                 canvas.drawBitmap(element.bitmap, element.matrix, imagePaint)
             }
             is SvgElement -> element.render(canvas)
             else -> {} // Unknown
         }
    }

    private fun drawVectorStroke(canvas: Canvas, stroke: VectorStroke) {
        if (stroke.strokeType == StrokeType.FREEHAND) {
            vectorPaint.style = Paint.Style.FILL
            vectorPaint.color = stroke.color
            // Check width if needed, but path usually defines shape for freehand
            canvas.drawPath(stroke.path, vectorPaint)
        } else {
            vectorPaint.style = Paint.Style.STROKE
            vectorPaint.color = stroke.color
            vectorPaint.strokeWidth = if(stroke.maxWidth > 0) stroke.maxWidth else 0f
            canvas.drawPath(stroke.path, vectorPaint)
        }
    }
    
    fun drawFill(canvas: Canvas, fill: FillData) {
        fillPaint.color = fill.color
        canvas.drawPath(fill.path, fillPaint)
    }

    fun drawLiveStroke(
        canvas: Canvas, 
        previewPoints: List<StrokePoint>?, 
        previewPath: Path?, 
        previewColor: Int, 
        currentLiveGeneratedRadius: Float,
        viewMatrix: Matrix,
        isDrawing: Boolean
    ) {
         if (!isDrawing) return
         
         canvas.save()
         canvas.concat(viewMatrix)

         // Draw Stroke
         if (previewPath != null) {
             // For simplicity, we use FILL if it looks like a mesh, or STROKE if it's geometric
             // But previewPath from Pipeline for freehand IS the mesh.
             // For Geometric, it's the skeleton.
             // We can use a heuristic or pass the type.
             // Let's assume mesh for now if style is FILL.
             
             vectorPaint.color = previewColor
             vectorPaint.style = if (previewPoints != null) Paint.Style.FILL else Paint.Style.STROKE
             vectorPaint.strokeWidth = if (previewPoints == null) currentLiveGeneratedRadius * 2 else 0f
             
             canvas.drawPath(previewPath, vectorPaint)
         }
         
         // Debug Wireframe
         if (isDebugWireframe && previewPoints != null) {
             drawDebugWireframe(canvas, previewPoints, viewMatrix)
         }
         
         canvas.restore()
    }
    
    private fun drawDebugWireframe(canvas: Canvas, points: List<StrokePoint>, viewMatrix: Matrix) {
        // Calculate zoom for consistent hairline
        val mValues = FloatArray(9)
        viewMatrix.getValues(mValues)
        val zoom = kotlin.math.sqrt(mValues[Matrix.MSCALE_X] * mValues[Matrix.MSCALE_X] + mValues[Matrix.MSKEW_X] * mValues[Matrix.MSKEW_X])
        
        debugPaint.strokeWidth = 2f / zoom
        val pRadius = 4f / zoom
        
        val path = Path()
        if (points.isNotEmpty()) {
            path.moveTo(points.first().x, points.first().y)
            for (p in points) {
                path.lineTo(p.x, p.y)
                canvas.drawCircle(p.x, p.y, pRadius, debugPointPaint)
            }
        }
        canvas.drawPath(path, debugPaint)
    }

    fun drawGrid(canvas: Canvas, viewMatrix: Matrix) {
        if (!gridConfig.isVisible || gridConfig.spacing <= 0f) return

        val stepPx = UnitUtils.projectUnitsToPixels(
            value = gridConfig.spacing, 
            unit = currentUnit, 
            basePxPerMm = scaleConfig.basePixelsPerMillimeter
        )
        
        if (stepPx <= 0f) return

        // Calculate zoom for visibility thresholds
        val transformValues = FloatArray(9)
        viewMatrix.getValues(transformValues)
        val zoom = kotlin.math.sqrt(transformValues[Matrix.MSCALE_X] * transformValues[Matrix.MSCALE_X] + transformValues[Matrix.MSKEW_X] * transformValues[Matrix.MSKEW_X])
        val screenStep = stepPx * zoom

        // Invert Matrix to map screen bounds to world
        val inverse = Matrix()
        viewMatrix.invert(inverse)
        
        val screenBounds = floatArrayOf(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
        val worldBounds = floatArrayOf(0f, 0f, 0f, 0f)
        inverse.mapPoints(worldBounds, screenBounds)
        val left = worldBounds[0]
        val top = worldBounds[1]
        val right = worldBounds[2]
        val bottom = worldBounds[3]
        
        val wMinX = kotlin.math.min(left, right)
        val wMaxX = kotlin.math.max(left, right)
        val wMinY = kotlin.math.min(top, bottom)
        val wMaxY = kotlin.math.max(top, bottom)

        // Center Offset
        val offsetX = canvasSizeConfig?.let { it.widthInPixels / 2f } ?: 0f
        val offsetY = canvasSizeConfig?.let { it.heightInPixels / 2f } ?: 0f

        val startXIndex = kotlin.math.floor((wMinX - offsetX) / stepPx).toInt()
        val endXIndex = kotlin.math.ceil((wMaxX - offsetX) / stepPx).toInt()
        
        val startYIndex = kotlin.math.floor((wMinY - offsetY) / stepPx).toInt()
        val endYIndex = kotlin.math.ceil((wMaxY - offsetY) / stepPx).toInt()
        
        if ((endXIndex - startXIndex) > 2000 || (endYIndex - startYIndex) > 2000) return 

        canvas.save()
        canvas.concat(viewMatrix)

        for (i in startXIndex..endXIndex) {
            val x = offsetX + i * stepPx
            var drawLine = false
            var lineColor = gridConfig.color 
            var thicknessScale = 1.0f

            if (i % 10 == 0) {
                drawLine = true
                thicknessScale = 2.0f
                lineColor = gridConfig.color
            } else if (i % 5 == 0) {
                if (screenStep >= 3f) { 
                    drawLine = true
                    thicknessScale = 1.5f
                    lineColor = gridConfig.secondaryColor
                }
            } else if (screenStep >= 8f) { 
                drawLine = true
                lineColor = gridConfig.tertiaryColor
            }
            
            if (drawLine) {
                gridPaint.color = lineColor
                gridPaint.strokeWidth = if (thicknessScale > 1.0f) (thicknessScale / zoom) else 0f
                canvas.drawLine(x, wMinY, x, wMaxY, gridPaint)
            }
        }

        for (i in startYIndex..endYIndex) {
            val y = offsetY + i * stepPx
            var drawLine = false
            var lineColor = gridConfig.color
            var thicknessScale = 1.0f

            if (i % 10 == 0) {
                drawLine = true
                thicknessScale = 2.0f
                lineColor = gridConfig.color
            } else if (i % 5 == 0) {
                if (screenStep >= 3f) {
                    drawLine = true
                    thicknessScale = 1.5f
                    lineColor = gridConfig.secondaryColor
                }
            } else if (screenStep >= 8f) {
                drawLine = true
                lineColor = gridConfig.tertiaryColor
            }
            
            if (drawLine) {
                gridPaint.color = lineColor
                gridPaint.strokeWidth = if (thicknessScale > 1.0f) (thicknessScale / zoom) else 0f
                canvas.drawLine(wMinX, y, wMaxX, y, gridPaint)
            }
        }
        
        canvas.restore()
    }

    fun drawSelectionOverlay(canvas: Canvas, manager: SelectionManager, viewMatrix: Matrix) {
        if (manager.selectedElements.isEmpty()) return
        val bounds = manager.baseBounds
        if (bounds.isEmpty) return

        canvas.save()
        canvas.concat(viewMatrix)
        canvas.concat(manager.selectionMatrix)

        // Draw Bounding Box
        canvas.drawRect(bounds, selectionBoxPaint)
        canvas.drawRect(bounds, selectionBorderPaint)

        // Draw Handles
        val transformValues = FloatArray(9)
        viewMatrix.getValues(transformValues)
        val zoom = kotlin.math.sqrt(transformValues[Matrix.MSCALE_X] * transformValues[Matrix.MSCALE_X] + transformValues[Matrix.MSKEW_X] * transformValues[Matrix.MSKEW_X])
        
        val handleSize = 10f / zoom
        
        // Draw 8 handles + rotation... (Simplified for brevity, or full implementation needed?)
        // Let's implement full based on View logic
        
        // Corners
        drawHandle(canvas, bounds.left, bounds.top, handleSize)
        drawHandle(canvas, bounds.right, bounds.top, handleSize)
        drawHandle(canvas, bounds.left, bounds.bottom, handleSize)
        drawHandle(canvas, bounds.right, bounds.bottom, handleSize)
        
        // Edges
        drawHandle(canvas, bounds.centerX(), bounds.top, handleSize)
        drawHandle(canvas, bounds.centerX(), bounds.bottom, handleSize)
        drawHandle(canvas, bounds.left, bounds.centerY(), handleSize)
        drawHandle(canvas, bounds.right, bounds.centerY(), handleSize)
        
        // Rotate
        val stemLength = 30f / zoom
        val centerX = bounds.centerX()
        val rotateY = bounds.top - stemLength
        canvas.drawLine(centerX, bounds.top, centerX, rotateY, selectionBorderPaint)
        drawHandle(canvas, centerX, rotateY, handleSize)

        canvas.restore()
    }
    
    private fun drawHandle(canvas: Canvas, x: Float, y: Float, size: Float) {
        canvas.drawCircle(x, y, size, selectionHandlePaint)
        canvas.drawCircle(x, y, size, selectionBorderPaint)
    }
}

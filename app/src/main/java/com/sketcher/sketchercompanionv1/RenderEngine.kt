package com.sketcher.sketchercompanionv1

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
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

    // Workspace Paints
    private val workspacePaint = Paint().apply {
        color = Color.parseColor("#FFEEEEEE") // Light Gray
        style = Paint.Style.FILL
    }
    
    private val paperPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 4f, 0x44000000) // Drop shadow
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

    // --- TEMPORARY OBJECTS (Avoid allocations in draw) ---
    private val tempMatrix = Matrix()
    private val tempInverseMatrix = Matrix()
    private val tempFloatArray = FloatArray(9)
    private val tempScreenBounds = FloatArray(4)
    private val tempWorldBounds = FloatArray(4)
    private val tempPaperRect = RectF()
    private val debugPath = Path()
    private val tempClipBounds = android.graphics.Rect()

    // --- PUBLIC DRAWING METHODS ---

    fun drawLayers(
        canvas: Canvas, 
        layers: List<Layer>, 
        viewMatrix: Matrix,
        componentLibrary: Map<String, ComponentDefinition>,
        selectedElements: Set<LayerElement>?,
        isTransformActive: Boolean,
        drawGrid: Boolean = true,
        clientMode: Boolean = false,
        isCancelled: () -> Boolean = { false }
    ) {
        // Draw Background
        val sizeConfig = canvasSizeConfig
        if (sizeConfig != null) {
            // Finite Canvas (Paper Mode)
            
            // 1. Draw Infinite Workspace Background
            canvas.drawRect(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), workspacePaint)
            
            // 2. Draw Paper Sheet
            canvas.save()
            canvas.concat(viewMatrix)
            
            val halfW = sizeConfig.widthInPixels / 2f
            val halfH = sizeConfig.heightInPixels / 2f
            
            // Calculate Paper Rect based on Origin
            // If TOP_LEFT (default): 0,0 is Top-Left. Rect: [0, 0, w, h]
            // If CENTER: 0,0 is Center. Rect: [-w/2, -h/2, w/2, h/2]
            
            val left = if (sizeConfig.origin == CoordinateOrigin.CENTER) -halfW else 0f
            val top = if (sizeConfig.origin == CoordinateOrigin.CENTER) -halfH else 0f
            val right = left + sizeConfig.widthInPixels
            val bottom = top + sizeConfig.heightInPixels
            
            paperPaint.color = canvasBackgroundColor
            canvas.drawRect(left, top, right, bottom, paperPaint)
            
            // Clip to paper for content?
            canvas.clipRect(left, top, right, bottom)
            
            // Draw Grid (aligned to paper)
            // drawGrid expects World Space canvas.
            // If Origin is CENTER, (0,0) is center. Grid draws from 0.
            // If Origin is TOP_LEFT, (0,0) is top-left. Grid draws from 0.
            // So drawGrid logic (drawing from 0) is correct for BOTH, 
            // provided the paper is drawn relative to that 0,0.
            
            // We need to pass the paper bounds to drawGrid for clamping though.
            if (drawGrid) {
                tempPaperRect.set(left, top, right, bottom)
                drawGrid(
                    canvas = canvas, 
                    viewMatrix = viewMatrix, 
                    isFinite = true, 
                    paperBounds = tempPaperRect
                )
            }
            
            canvas.restore()
            
        } else {
            // Infinite Canvas
            canvas.drawColor(canvasBackgroundColor)
            
            if (drawGrid) {
                canvas.save()
                canvas.save()
                canvas.concat(viewMatrix)
                drawGrid(canvas, viewMatrix, false, null)
                canvas.restore()   
            }
            // Note: drawGrid previously restored its own save, but we'll refactor drawGrid to be cleaner
        }
        
        // Draw Layers
        
        // Calculate visible world bounds for Frustum Culling
        tempInverseMatrix.set(viewMatrix)
        val inverted = viewMatrix.invert(tempInverseMatrix)
        var visibleWorldBounds: RectF? = null
        if (inverted) {
            tempScreenBounds[0] = 0f
            tempScreenBounds[1] = 0f
            tempScreenBounds[2] = canvas.width.toFloat()
            tempScreenBounds[3] = canvas.height.toFloat()
            tempInverseMatrix.mapPoints(tempWorldBounds, tempScreenBounds)
            
            val wMinX = kotlin.math.min(tempWorldBounds[0], tempWorldBounds[2])
            val wMaxX = kotlin.math.max(tempWorldBounds[0], tempWorldBounds[2])
            val wMinY = kotlin.math.min(tempWorldBounds[1], tempWorldBounds[3])
            val wMaxY = kotlin.math.max(tempWorldBounds[1], tempWorldBounds[3])
            
            visibleWorldBounds = RectF(wMinX, wMinY, wMaxX, wMaxY)
        }

        for (layer in layers) {
             if (isCancelled()) return
             val visible = if (clientMode) {
                 layer.isVisible && layer.isVisibleOnClient
             } else {
                 layer.isVisible
             }
             if (!visible) continue
             
             // Setup Layer Paint/Alpha
             val layerAlpha = if (layer.opacity < 1f) (layer.opacity * 255).toInt() else 255
             val saveCount = if (layerAlpha < 255) {
                 canvas.saveLayerAlpha(null, layerAlpha) 
             } else {
                 canvas.save()
             }
             
             canvas.concat(viewMatrix)
             
             for (element in layer.elements) {
                  if (isCancelled()) {
                      canvas.restoreToCount(saveCount)
                      return
                  }
                  val isSelected = selectedElements?.contains(element) == true
                  if (isSelected && isTransformActive) continue 

                  // Frustum culling filter
                  if (visibleWorldBounds != null) {
                      val elementBounds = element.getBoundingBox(componentLibrary)
                      if (!RectF.intersects(visibleWorldBounds, elementBounds)) {
                          continue
                      }
                  }

                  drawElementRecursive(canvas, element, componentLibrary, viewMatrix)
             }
             
             canvas.restoreToCount(saveCount)
        }
    }
    
    // Recursive drawing for groups/components
    fun drawElementRecursive(canvas: Canvas, element: LayerElement, library: Map<String, ComponentDefinition>, viewMatrix: Matrix) {
         when (element) {
             is VectorStroke -> drawVectorStroke(canvas, element, viewMatrix)
             is FillData -> drawFill(canvas, element)
             is GroupElement -> {
                 canvas.save()
                 canvas.concat(element.matrix)
                 val nextMatrix = Matrix(viewMatrix)
                 nextMatrix.postConcat(element.matrix)
                 element.elements.forEach { drawElementRecursive(canvas, it, library, nextMatrix) }
                 canvas.restore()
             }
             is ComponentInstance -> {
                 val def = library[element.definitionId]
                 if (def != null) {
                     canvas.save()
                     canvas.concat(element.matrix)
                     val nextMatrix = Matrix(viewMatrix)
                     nextMatrix.postConcat(element.matrix)
                     def.elements.forEach { drawElementRecursive(canvas, it, library, nextMatrix) }
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

    private fun drawVectorStroke(canvas: Canvas, stroke: VectorStroke, viewMatrix: Matrix) {
    // Pass 1: FILL (if enabled)
    if (stroke.isFillEnabled && stroke.fillPath != null) {
        vectorPaint.style = Paint.Style.FILL
        vectorPaint.color = stroke.fillColor
        canvas.drawPath(stroke.fillPath, vectorPaint)
    }

    // Pass 2: STROKE (if enabled)
    if (stroke.isStrokeEnabled) {
        // For FREEHAND, the 'path' IS already the mesh (shape)
        if (stroke.strokeType == StrokeType.FREEHAND) {
            vectorPaint.style = Paint.Style.FILL
            vectorPaint.color = stroke.strokeColor
            if (stroke.paths.isNotEmpty()) {
                for (p in stroke.paths) {
                    canvas.drawPath(p, vectorPaint)
                }
            } else {
                canvas.drawPath(stroke.path, vectorPaint)
            }
        } else {
            // For others, it's a line
            vectorPaint.style = Paint.Style.STROKE
            vectorPaint.color = stroke.strokeColor
            vectorPaint.strokeWidth = if (stroke.maxWidth > 0) stroke.maxWidth else 0f
            canvas.drawPath(stroke.path, vectorPaint)
        }
    }

    // Pass 3: DEBUG WIREFRAME (if enabled and stroke has points)
    if (isDebugWireframe && stroke.points.isNotEmpty()) {
        drawDebugWireframe(canvas, stroke.points, viewMatrix)
    }
}
    
    fun drawFill(canvas: Canvas, fill: FillData) {
        fillPaint.color = fill.color
        canvas.drawPath(fill.path, fillPaint)
    }

    /**
     * Draws the committed (baked) head of a live stroke.
     * Called before drawLiveStroke so the live tail is rendered on top,
     * covering the end cap of the committed polygon and hiding any seam.
     */
    fun drawCommittedPreview(canvas: Canvas, committedPath: Path, color: Int) {
        vectorPaint.color = color
        vectorPaint.style = Paint.Style.FILL
        canvas.drawPath(committedPath, vectorPaint)
    }

    fun drawLiveStroke(
    canvas: Canvas, 
    previewPoints: List<StrokePoint>?, 
    previewPath: Path?, 
    previewColor: Int, 
    fillPath: Path? = null,
    fillColor: Int = 0,
    isFillActive: Boolean = false,
    isStrokeActive: Boolean = true,
    currentLiveGeneratedRadius: Float,
    viewMatrix: Matrix,
    isDrawing: Boolean
) {
     if (!isDrawing) return
     
     canvas.save()
     canvas.concat(viewMatrix)

     // Pass 1: FILL
     if (isFillActive && fillPath != null) {
         vectorPaint.style = Paint.Style.FILL
         vectorPaint.color = fillColor
         canvas.drawPath(fillPath, vectorPaint)
     }

     // Pass 2: STROKE
     if (isStrokeActive && previewPath != null) {
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
        viewMatrix.getValues(tempFloatArray)
        val zoom = kotlin.math.sqrt(tempFloatArray[Matrix.MSCALE_X] * tempFloatArray[Matrix.MSCALE_X] + tempFloatArray[Matrix.MSKEW_X] * tempFloatArray[Matrix.MSKEW_X])
        
        debugPaint.strokeWidth = 2f / zoom
        val pRadius = 4f / zoom
        
        debugPath.reset()
        if (points.isNotEmpty()) {
            debugPath.moveTo(points.first().x, points.first().y)
            for (p in points) {
                debugPath.lineTo(p.x, p.y)
                canvas.drawCircle(p.x, p.y, pRadius, debugPointPaint)
            }
        }
        canvas.drawPath(debugPath, debugPaint)
    }

    fun drawGrid(
        canvas: Canvas, 
        viewMatrix: Matrix, 
        isFinite: Boolean,
        paperBounds: RectF? // Replaced float w/h with RectF for arbitrary bounds (center origin)
    ) {
        if (!gridConfig.isVisible || gridConfig.spacing <= 0f) return

        val stepPx = UnitUtils.projectUnitsToPixels(
            value = gridConfig.spacing, 
            unit = currentUnit, 
            basePxPerMm = scaleConfig.basePixelsPerMillimeter
        )
        
        if (stepPx <= 0f) return

        // Calculate zoom for visibility thresholds
        viewMatrix.getValues(tempFloatArray)
        val zoom = kotlin.math.sqrt(tempFloatArray[Matrix.MSCALE_X] * tempFloatArray[Matrix.MSCALE_X] + tempFloatArray[Matrix.MSKEW_X] * tempFloatArray[Matrix.MSKEW_X])
        val screenStep = stepPx * zoom

        // Determine visible world bounds
        // Invert Matrix to map screen bounds to world
        tempInverseMatrix.set(viewMatrix)
        val inverted = viewMatrix.invert(tempInverseMatrix)
        if (!inverted) return // Should not happen usually

        tempScreenBounds[0] = 0f
        tempScreenBounds[1] = 0f
        tempScreenBounds[2] = canvas.width.toFloat()
        tempScreenBounds[3] = canvas.height.toFloat()
        
        tempInverseMatrix.mapPoints(tempWorldBounds, tempScreenBounds)
        
        val left = tempWorldBounds[0]
        val top = tempWorldBounds[1]
        val right = tempWorldBounds[2]
        val bottom = tempWorldBounds[3]
        
        var wMinX = kotlin.math.min(left, right)
        var wMaxX = kotlin.math.max(left, right)
        var wMinY = kotlin.math.min(top, bottom)
        var wMaxY = kotlin.math.max(top, bottom)
        
        // If finite, clamp grid to paper
        if (isFinite) {
            // Since we might have already clipped in drawLayers, this is redundant for drawing but good for loop limits
            // However, drawLayers logic calls drawGrid INSIDE a save/concat block.
            // But wait, the original drawGrid did canvas.concat(viewMatrix) ITSELF.
            // My modified drawLayers has:
            // Finite -> concat -> drawGrid
            // Infinite -> no concat -> drawGrid?
            
            // Let's standardise: drawGrid expects the canvas to ALREADY BE IN WORLD SPACE?
            // The original code did: canvas.save(); canvas.concat(viewMatrix); ... canvas.restore()
            // So it expected Screen Space canvas.
            
            // IF we are in Finite mode in drawLayers, we are ALREADY in World Space (viewMatrix applied).
            // IF we are in Infinite mode in drawLayers, we are NOT (I changed it to do nothing).
            
            // Wait, let's look at my drawLayers change again.
            // Finite: canvas.concat(viewMatrix); drawGrid(...) -> So canvas is in World Space.
            // Infinite: I did NOT concat. -> So canvas is in Screen Space.
            
            // This is inconsistent. I should make drawLayers consistent or handle it here.
            
            // Let's assume drawLayers handles the matrix for Finite (because of the paper rect).
            // For Infinite, let's have drawLayers ALSO handle the matrix? 
            
            // REVISIT drawLayers logic (I'll fix it in the next step if I messed up, but for now let's assume:)
            // -> I will remove the matrix concat from drawGrid and assume caller sets it up.
            // Why? Because for Finite paper, we want to draw relative to the paper (0,0).
        }
        
        // Actually, let's fix drawLayers to always setup the matrix for the grid, 
        // OR have drawGrid take valid world bounds.
        
        // If canvas is already transformed, `viewMatrix` passed here is redundant for transformation
        // BUT needed for zoom calculation.
        
        // Let's refine the logic:
        // We will assume the canvas is ALREADY transformed to World Space (or Paper Space).
        // So we just draw lines from wMin to wMax.
        
        // Re-calculating bounds based on viewMatrix (which is Screen->World) is tricky if we are already in World space.
        canvas.getClipBounds(tempClipBounds)
        wMinX = tempClipBounds.left.toFloat()
        wMaxX = tempClipBounds.right.toFloat()
        wMinY = tempClipBounds.top.toFloat()
        wMaxY = tempClipBounds.bottom.toFloat()
        
        // Clamp to paper Size if finite
        if (isFinite && paperBounds != null) {
            wMinX = kotlin.math.max(wMinX, paperBounds.left)
            wMaxX = kotlin.math.min(wMaxX, paperBounds.right)
            wMinY = kotlin.math.max(wMinY, paperBounds.top)
            wMaxY = kotlin.math.min(wMaxY, paperBounds.bottom)
        }

        // Center Offset REMOVED -> Grid always starts at (0,0) of the world/paper
        val offsetX = 0f
        val offsetY = 0f

        val startXIndex = kotlin.math.floor((wMinX - offsetX) / stepPx).toInt()
        val endXIndex = kotlin.math.ceil((wMaxX - offsetX) / stepPx).toInt()
        
        val startYIndex = kotlin.math.floor((wMinY - offsetY) / stepPx).toInt()
        val endYIndex = kotlin.math.ceil((wMaxY - offsetY) / stepPx).toInt()
        
        // Safety cap
        if ((endXIndex - startXIndex) > 2000 || (endYIndex - startYIndex) > 2000) return 

        // We do NOT concat viewMatrix here because we assume caller (drawLayers) did it.
        
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
                gridPaint.strokeWidth = if (thicknessScale > 1.0f && zoom > 0.001f) (thicknessScale / zoom) else 0f
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
                gridPaint.strokeWidth = if (thicknessScale > 1.0f && zoom > 0.001f) (thicknessScale / zoom) else 0f
                canvas.drawLine(wMinX, y, wMaxX, y, gridPaint)
            }
        }
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
        viewMatrix.getValues(tempFloatArray)
        val zoom = kotlin.math.sqrt(tempFloatArray[Matrix.MSCALE_X] * tempFloatArray[Matrix.MSCALE_X] + tempFloatArray[Matrix.MSKEW_X] * tempFloatArray[Matrix.MSKEW_X])
        
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

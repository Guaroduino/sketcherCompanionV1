package com.skecher.sketchercompanionv1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.PointF
import android.view.View
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.ink.strokes.Stroke



import com.skecher.sketchercompanionv1.dto.GridConfig
import com.skecher.sketchercompanionv1.dto.ScaleConfig
import com.skecher.sketchercompanionv1.dto.DistanceUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class FillData(val path: android.graphics.Path, val color: Int)
data class Layer(
    val id: String, 
    val name: String,
    val strokes: MutableList<Stroke>, 
    val fills: MutableList<FillData>,
    val vectorStrokes: MutableList<VectorStroke> = mutableListOf(), 
    var isVisible: Boolean = true,
    var opacity: Float = 1f
)

class SketcherCanvasView(context: Context) : View(context) {

    private val viewMatrix = Matrix()
    private val strokeRenderer = CanvasStrokeRenderer.create()
    
    // BITMAP CACHING
    private var backingBitmap: android.graphics.Bitmap? = null
    private var backingCanvas: Canvas? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            backingBitmap = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            backingCanvas = Canvas(backingBitmap!!)
            redrawAllCache()
        }
    }

    // Call this to Bake a finalized stroke into the bitmap
    fun bakeStroke(stroke: VectorStroke) {
        val requestCanvas = backingCanvas ?: return
        
        // We must draw with the CURRENT View Matrix so it aligns with the screen
        requestCanvas.save()
        requestCanvas.concat(viewMatrix)
        
        vectorPaint.color = stroke.color
        requestCanvas.drawPath(stroke.path, vectorPaint)
        
        requestCanvas.restore()
        invalidate()
    }

    // Direct Add & Bake (Optimistic UI for Instant Feedback)
    fun addInkStroke(stroke: androidx.ink.strokes.Stroke, layerIndex: Int) {
        if (layerIndex in layers.indices) {
            layers[layerIndex].strokes.add(stroke)
            bakeInkStroke(stroke)
        }
    }

    // Bake Android Ink Stroke
    fun bakeInkStroke(stroke: androidx.ink.strokes.Stroke) {
        // NO-OP: Ink strokes are now rendered live in the overlay view.
        // val requestCanvas = backingCanvas ?: return
        // ...
        invalidate()
    }
    
    // Also Bake Fills (optional but useful)
    fun bakeFill(fill: FillData) {
        val requestCanvas = backingCanvas ?: return
        requestCanvas.save()
        requestCanvas.concat(viewMatrix)
        fillPaint.color = fill.color
        requestCanvas.drawPath(fill.path, fillPaint)
        requestCanvas.restore()
        invalidate()
    }
    
    // Helper to completely rebuild the cache
    fun redrawAllCache() {
        val requestCanvas = backingCanvas ?: return
        
        // Clear with Background Color (Opaque)
        requestCanvas.drawColor(canvasBackgroundColor)
        
        // Draw Layers
        for (layer in layers) {
            if (!layer.isVisible) continue
            
            // Layer Opacity
            val layerSaveCount = if (layer.opacity < 1f) {
                requestCanvas.saveLayerAlpha(0f, 0f, width.toFloat(), height.toFloat(), (layer.opacity * 255).toInt())
            } else {
                requestCanvas.save()
            }
            
            // Fills
            if (layer.fills.isNotEmpty()) {
                requestCanvas.save()
                requestCanvas.concat(viewMatrix)
                for (fill in layer.fills) {
                    fillPaint.color = fill.color
                    requestCanvas.drawPath(fill.path, fillPaint)
                }
                requestCanvas.restore()
            }
            
            // Vector Strokes
            if (layer.vectorStrokes.isNotEmpty()) {
                requestCanvas.save()
                requestCanvas.concat(viewMatrix)
                for (vStroke in layer.vectorStrokes) {
                    if (isDebugWireframeByVM) {
                        drawDebugStroke(requestCanvas, vStroke)
                    } else {
                        vectorPaint.color = vStroke.color
                        requestCanvas.drawPath(vStroke.path, vectorPaint)
                    }
                }
                requestCanvas.restore()
            }
            
            // Ink Strokes are now handled by the overlay View (TextureView), NOT baked here.
            // This ensures they remain "Live" and maintain their shader fidelity.
            
            requestCanvas.restoreToCount(layerSaveCount)
        }
        
        invalidate()
    }
    
    // GRID CONFIG
    var gridConfig: GridConfig = GridConfig()
        set(value) { field = value; invalidate() }
    var scaleConfig: ScaleConfig = ScaleConfig()
        set(value) { field = value; invalidate() }
    var currentUnit: DistanceUnit = DistanceUnit.M
        set(value) { field = value; invalidate() }
        
    private val gridPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 0f // Hairline
    }

    // BACKGROUND
    var canvasBackgroundColor: Int = android.graphics.Color.WHITE
        set(value) {
            field = value
            redrawAllCache() // Must redraw cache because cache now contains background
        }

    // LAYERS (Replaces flat lists)
    var isDebugPredictionEnabled: Boolean = false
        set(value) {
            field = value
            android.util.Log.d("SketcherCanvasView", "isDebugPredictionEnabled set to: $value")
            invalidate()
        }
    var isDebugWireframe: Boolean = false
        set(value) {
            field = value
            invalidate()
        }
        
    private val layers = mutableListOf<Layer>()
    
    // PREVIEW STATE (For live drawing/filling)
    private var currentVectorPreviewPath: android.graphics.Path? = null
    private var currentVectorPreviewPoints: List<StrokePoint>? = null // For prediction
    private var currentMaxWidth: Float = 10f // Max stroke width for prediction
    private var currentMinSizeFactor: Float = 0f // Min size factor for prediction
    private var currentFillPath: android.graphics.Path? = null
    private var currentFillColor: Int? = null
    private var currentPredictedPoint: android.graphics.PointF? = null
    private val fillPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }
    private val vectorPaint = android.graphics.Paint().apply {
        style = android.graphics.Paint.Style.FILL
        isAntiAlias = true
    }

    /**
     * FIX ROTACIÓN: Usamos 'post' para asegurar que la invalidación
     * ocurra cuando la vista ya esté adjunta y medida.
     */
    fun setLayers(newLayers: List<Layer>) {
        layers.clear()
        layers.addAll(newLayers)
        // We do NOT redraw cache here automatically anymore, to allow efficient 'bakeStroke'.
        // External changes (Undo/Redo/Load) must call 'redrawAllCache()' explicitly.
    }


    
    fun updateCurrentVectorPreview(
        path: android.graphics.Path?, 
        points: List<StrokePoint>?, 
        color: Int, 
        maxWidth: Float = 10f,
        minSizeFactor: Float = 0f,
        predictedPoint: android.graphics.PointF? = null
    ) {
        currentVectorPreviewPath = path
        currentVectorPreviewPoints = points
        currentMaxWidth = maxWidth
        currentMinSizeFactor = minSizeFactor
        currentPredictedPoint = predictedPoint
        if (color != 0) vectorPaint.color = color
        invalidate()
    }

    // --- LASSO FILL METHODS ---
    fun updateCurrentFill(path: android.graphics.Path?, color: Int) {
        currentFillPath = path
        currentFillColor = color
        invalidate()
    }



    fun eraseContentAt(worldX: Float, worldY: Float): Any? {
        // Iterate layers top-down (reversed)
        for (layer in layers.reversed()) {
            if (!layer.isVisible) continue 
            
            // 1. Check Strokes (Top priority usually, or same layer order)
            // Let's check strokes first as they are "on top" of fills in drawing order
            for (i in layer.strokes.indices.reversed()) {
                val stroke = layer.strokes[i]
                if (StrokeGeometry.isStrokeTouched(stroke, worldX, worldY)) {
                    layer.strokes.removeAt(i)
                    invalidate()
                    return stroke
                }
            }
            
            // 2. Check Fills
            for (i in layer.fills.indices.reversed()) {
                val fill = layer.fills[i]
                // Hit test path
                val bounds = android.graphics.RectF()
                fill.path.computeBounds(bounds, true)
                
                if (bounds.contains(worldX, worldY)) {
                    // Precise check using Region
                    val region = android.graphics.Region()
                    region.setPath(fill.path, android.graphics.Region(
                        bounds.left.toInt(), bounds.top.toInt(), 
                        bounds.right.toInt(), bounds.bottom.toInt()
                    ))
                    
                    if (region.contains(worldX.toInt(), worldY.toInt())) {
                        layer.fills.removeAt(i)
                        invalidate()
                        return fill
                    }
                }
            }
        }
        return null
    }

    fun setCameraMatrix(matrix: Matrix) {
        viewMatrix.set(matrix)
        redrawAllCache()
    }

    fun clearCanvas() {
        layers.forEach { 
            it.strokes.clear()
            it.fills.clear()
            it.vectorStrokes.clear() // Should clear vector strokes too if not already
        }
        redrawAllCache()
    }

    // --- GRID RENDERING ---
    private fun drawGrid(canvas: Canvas) {
        if (!gridConfig.isVisible) return

        val spacing = gridConfig.spacing
        if (spacing <= 0f) return

        // 1. Calculate Pixels per Project Unit
        // formula: pixels = (baseMm / ratio) * (dpi / 25.4)
        // 1. Calculate Pixels per Unit using Central Logic
        val stepPx = com.skecher.sketchercompanionv1.utils.UnitUtils.projectUnitsToPixels(
            value = spacing, 
            unit = currentUnit, 
            basePxPerMm = scaleConfig.basePixelsPerMillimeter
        )
        
        // 3. Check Density (Performance Guard)
        // Get current zoom from matrix to check screen density
        val transformValues = FloatArray(9)
        viewMatrix.getValues(transformValues)
        val zoom = kotlin.math.sqrt(transformValues[Matrix.MSCALE_X] * transformValues[Matrix.MSCALE_X] + transformValues[Matrix.MSKEW_X] * transformValues[Matrix.MSKEW_X])
        
        val screenStep = stepPx * zoom
        if (screenStep < 3f) {
             // Too dense to render anything meaningfully, or just render majors?
             // Prompt says: "If pixelsPerProjectUnit * spacing is smaller than 3 pixels ... STOP rendering minor lines and only render Major/Mid"
             // Actually, if it's REALLY dense, even majors might be too much.
             // But let's follow the density logic filtering per level.
        }

        // 4. Calculate Visible Bounds in WORLD Coordinates
        // Invert Matrix
        val inverse = Matrix()
        viewMatrix.invert(inverse)
        
        val screenBounds = floatArrayOf(0f, 0f, width.toFloat(), height.toFloat())
        val worldBounds = floatArrayOf(0f, 0f, 0f, 0f)
        inverse.mapPoints(worldBounds, screenBounds)
        val left = worldBounds[0]
        val top = worldBounds[1]
        val right = worldBounds[2]
        val bottom = worldBounds[3]
        
        // Normalize bounds (handle rotation/inversion if needed, though simple invert usually works for axis aligned)
        // Assuming no rotation for simplicty in grid loop ranges, or taking min/max
        val wMinX = kotlin.math.min(left, right)
        val wMaxX = kotlin.math.max(left, right)
        val wMinY = kotlin.math.min(top, bottom)
        val wMaxY = kotlin.math.max(top, bottom)

        // 5. Draw Loop
        // We draw vertical lines at: x = index * stepPx
        // We draw horizontal lines at: y = index * stepPx
        
        val startXIndex = floor(wMinX / stepPx).toInt()
        val endXIndex = ceil(wMaxX / stepPx).toInt()
        
        val startYIndex = floor(wMinY / stepPx).toInt()
        val endYIndex = ceil(wMaxY / stepPx).toInt()
        
        // Limit loop count to prevent hanging on extreme zoom bugs
        if ((endXIndex - startXIndex) > 2000 || (endYIndex - startYIndex) > 2000) return 

        // Draw Verticals
        for (i in startXIndex..endXIndex) {
            val x = i * stepPx
            
            var drawLine = false
            var thicknessScale = 1.0f
            var lineColor = gridConfig.color // Default to Primary

            if (i % 10 == 0) {
                // Major
                drawLine = true
                thicknessScale = 2.0f
                lineColor = gridConfig.color
            } else if (i % 5 == 0) {
                // Mid
                if (screenStep >= 3f) { // Only if not too dense
                    drawLine = true
                    thicknessScale = 1.5f
                    lineColor = gridConfig.secondaryColor
                }
            } else {
                // Minor
                if (screenStep >= 8f) { // Require more space for minors
                    drawLine = true
                    thicknessScale = 1.0f
                    lineColor = gridConfig.tertiaryColor
                }
            }
            
            if (drawLine) {
                gridPaint.color = lineColor
                // gridPaint.alpha is now implicit in the color itself
                gridPaint.strokeWidth = if (thicknessScale > 1.0f) (thicknessScale / zoom) else 0f
                
                canvas.drawLine(x, wMinY, x, wMaxY, gridPaint)
            }
        }

        // Draw Horizontals
        for (i in startYIndex..endYIndex) {
            val y = i * stepPx
            
            var drawLine = false
            var thicknessScale = 1.0f
            var lineColor = gridConfig.color

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
            } else {
                if (screenStep >= 8f) {
                    drawLine = true
                    thicknessScale = 1.0f
                    lineColor = gridConfig.tertiaryColor
                }
            }
            
            if (drawLine) {
                gridPaint.color = lineColor
                gridPaint.strokeWidth = if (thicknessScale > 1.0f) (thicknessScale / zoom) else 0f
                canvas.drawLine(wMinX, y, wMaxX, y, gridPaint)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(canvasBackgroundColor)
        
        // 0. Grid (Dynamic, drawn behind cache but managed separately)
        canvas.save()
        canvas.concat(viewMatrix)
        drawGrid(canvas)
        canvas.restore()
        
        // 1. Cached Bitmap (Contains all finalized VECTOR strokes/fills)
        // Drawn at Identity (0,0) because it is a Screen Buffer
        backingBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }
        
        // 2. DYNAMIC INK RENDERING (Live Overlay)
        // Ink strokes are stored in WORLD-SPACE coordinates (transformed when captured).
        // Like the backingBitmap, they are drawn WITHOUT viewMatrix because they're already
        // in world-space. The viewMatrix was applied during transformation (InkUtils.transformStrokeToWorld).
        // 
        // This is different from Vector strokes which are drawn WITH viewMatrix in the bitmap cache.
        // Here we draw world-space strokes directly to screen-space canvas (identity transform).
        
        // NO canvas.concat(viewMatrix) here! Strokes are already in world-space.
        
        // Setup Multiply Paint for "Wet" look
        val multiplyPaint = android.graphics.Paint()
        multiplyPaint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.MULTIPLY)
        
        // Iterate layers to draw Ink strokes
        for (layer in layers) {
            if (!layer.isVisible) continue
            if (layer.strokes.isEmpty()) continue
            
            // Apply Layer Opacity if needed
            val layerAlpha = if (layer.opacity < 1f) {
                (layer.opacity * 255).toInt()
            } else {
                255
            }
            
            for (stroke in layer.strokes) {
                // Draw stroke with multiply blend mode for "wet" appearance
                // The stroke is in world-space, we draw it with identity transform
                val saveCount = canvas.saveLayer(null, multiplyPaint)
                canvas.drawColor(android.graphics.Color.WHITE) // Neutral base for multiply
                
                // Draw the stroke - it's in world coordinates, we apply viewMatrix here
                // to convert to screen coordinates
                canvas.save()
                canvas.concat(viewMatrix)
                strokeRenderer.draw(canvas, stroke, Matrix())
                canvas.restore()
                
                canvas.restoreToCount(saveCount)
            }
        }
        
        // 3. Previews (Live content) - Needs Matrix
        canvas.save()
        canvas.concat(viewMatrix)
        
        // ... (rest of previews)
        
        // 4. Current Fill in progress (Preview) - Drawn BEFORE stroke to stay behind
        currentFillPath?.let { path ->
            currentFillColor?.let { color ->
                fillPaint.color = color
                canvas.drawPath(path, fillPaint)
            }
        }

        // 5. Vector Preview with Prediction
        currentVectorPreviewPath?.let { path ->
             // Draw the stable path (real data)
             canvas.drawPath(path, vectorPaint)
             
             val points = currentVectorPreviewPoints
             if (points != null && points.isNotEmpty()) {
                 // 4. Dynamic Velocity-Based Prediction
                 currentPredictedPoint?.let { pred ->
                     val last = points.last()
                     
                     // Render faded prediction trail
                     val predictionPath = android.graphics.Path()
                     val dynamicRange = 1.0f - currentMinSizeFactor
                     
                     val lastScale = currentMinSizeFactor + (dynamicRange * last.pressure)
                     val lastWidth = currentMaxWidth * lastScale
                     
                     // We don't have pressure for the predicted point, assume same as last
                     val predWidth = lastWidth 

                     // Visual "Trail" - Draw a continuous line for better visibility
                     predictionPath.moveTo(last.x, last.y)
                     predictionPath.lineTo(pred.x, pred.y)
                     
                     val originalColor = vectorPaint.color
                     val originalAlpha = vectorPaint.alpha
                     val originalWidth = vectorPaint.strokeWidth
                     val originalStyle = vectorPaint.style

                     if (isDebugPredictionEnabled) {
                         // DEBUG: RED PREDICTION LINE + BLUE CROSS
                         android.util.Log.d("CanvasDebug", "Drawing Prediction Line (DEBUG)")
                         
                         vectorPaint.color = android.graphics.Color.RED
                         vectorPaint.alpha = 255 
                         vectorPaint.strokeWidth = 5f 
                         vectorPaint.style = android.graphics.Paint.Style.STROKE 
                         
                         // 1. Draw Actual Prediction Line
                         canvas.drawPath(predictionPath, vectorPaint)
                         
                         // 2. DEBUG CROSS at Predicted Target (Blue)
                         vectorPaint.color = android.graphics.Color.BLUE
                         vectorPaint.strokeWidth = 3f
                         canvas.drawLine(pred.x - 20, pred.y, pred.x + 20, pred.y, vectorPaint)
                         canvas.drawLine(pred.x, pred.y - 20, pred.x, pred.y + 20, vectorPaint)
                         
                     } else {
                         // STANDARD VISUAL (Seamless Continuation)
                         // Use original color and alpha
                         vectorPaint.color = originalColor
                         vectorPaint.alpha = originalAlpha
                         vectorPaint.style = android.graphics.Paint.Style.STROKE 
                         
                         // Match the width of the tip of the stroke
                         vectorPaint.strokeWidth = lastWidth 
                         
                         canvas.drawPath(predictionPath, vectorPaint)
                     }

                     // Restore
                     vectorPaint.color = originalColor
                     vectorPaint.alpha = originalAlpha
                     vectorPaint.strokeWidth = originalWidth
                     vectorPaint.style = originalStyle

                 }
             }
        }

        canvas.restore()
        
        // ATOMIC HANDOFF: Execute queued action (e.g., clearing wet layer) immediately after drawing dry layer
        onDrawAction?.invoke()
        onDrawAction = null
    }

    // Callback to be executed immediately after onDraw completes its work
    var onDrawAction: (() -> Unit)? = null

    // DEBUG PAINTS
    private val debugSkeletonPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLUE
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val debugVertexPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.YELLOW
        style = android.graphics.Paint.Style.FILL
    }
    private val debugLeftEdgePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.GREEN
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val debugRightEdgePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.RED
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2f
    }

    var isDebugWireframeByVM: Boolean = false
        set(value) {
            field = value
            redrawAllCache()
        }

    private fun drawDebugStroke(canvas: Canvas, stroke: VectorStroke) {
        // Step A: Draw Skeleton
        if (stroke.points.size >= 2) {
            for (i in 0 until stroke.points.size - 1) {
                val p1 = stroke.points[i]
                val p2 = stroke.points[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, debugSkeletonPaint)
            }
        }
        
        // Draw Vertices
        for (p in stroke.points) {
            canvas.drawCircle(p.x, p.y, 4f, debugVertexPaint)
        }

        // Step B: Draw Edges
        if (stroke.leftPoints.size >= 2) {
            for (i in 0 until stroke.leftPoints.size - 1) {
                val p1 = stroke.leftPoints[i]
                val p2 = stroke.leftPoints[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, debugLeftEdgePaint)
            }
        }
        if (stroke.rightPoints.size >= 2) {
            for (i in 0 until stroke.rightPoints.size - 1) {
                val p1 = stroke.rightPoints[i]
                val p2 = stroke.rightPoints[i + 1]
                canvas.drawLine(p1.x, p1.y, p2.x, p2.y, debugRightEdgePaint)
            }
        }
    }
}


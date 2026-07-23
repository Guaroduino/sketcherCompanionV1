package com.sketcher.sketchercompanionv1

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.BitmapShader
import android.graphics.Shader
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.UnitUtils
import com.sketcher.sketchercompanionv1.utils.SvgPatternCache
import com.sketcher.sketchercompanionv1.utils.MathTextureCache
import com.sketcher.sketchercompanionv1.utils.ImageTextureCache
import com.sketcher.sketchercompanionv1.managers.SnapPoint
import com.sketcher.sketchercompanionv1.managers.SnapType
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

/**
 * Handles all direct Canvas drawing operations.
 * Holds the Paint state and configuration for rendering.
 */
class RenderEngine {

    // Blur mask filter cache to avoid GC pressure in draw loops
    private val blurFilterCache = HashMap<Float, android.graphics.BlurMaskFilter>()
    private fun getBlurFilter(radius: Float): android.graphics.BlurMaskFilter {
        val clampedRadius = radius.coerceAtLeast(0.01f)
        return blurFilterCache.getOrPut(clampedRadius) {
            android.graphics.BlurMaskFilter(clampedRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
        }
    }

    // --- PAINTS ---
    private val svgAlphaPaint = Paint()
    private val matrixStack = ArrayList<Matrix>().apply {
        repeat(16) { add(Matrix()) }
    }
    private var matrixStackPointer = 0
    private fun obtainMatrix(): Matrix {
        if (matrixStackPointer >= matrixStack.size) {
            matrixStack.add(Matrix())
        }
        return matrixStack[matrixStackPointer++]
    }
    private fun releaseMatrix() {
        matrixStackPointer--
    }

    private val tempShaderMatrix = Matrix()

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

    private val layerAlphaPaint = Paint()

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

    private val snapPaint = Paint().apply {
        color = Color.parseColor("#FF34C759") // iOS Green
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    // --- CONFIGURATION ---
    var gridConfig: GridConfig = GridConfig()
    var scaleConfig: ScaleConfig = ScaleConfig()
    var currentUnit: DistanceUnit = DistanceUnit.M
    var canvasSizeConfig: CanvasSizeConfig? = null // For bounds/grid centering
    var canvasBackgroundColor: Int = Color.WHITE
    var canvasBackgroundStyle: FillStyle = FillStyle.Solid(Color.WHITE)
    var workspaceBackgroundColor: Int = Color.parseColor("#FFEEEEEE")
        set(value) {
            field = value
            workspacePaint.color = value
        }
    
    var isDebugWireframe: Boolean = false
    var hiddenElementId: String? = null

    // --- TEMPORARY OBJECTS (Avoid allocations in draw) ---
    private val tempMatrix = Matrix()
    private val tempInverseMatrix = Matrix()
    private val tempFloatArray = FloatArray(9)
    private val tempScreenBounds = FloatArray(4)
    private val tempWorldBounds = FloatArray(4)
    private val tempPaperRect = RectF()
    private val debugPath = Path()
    private val tempClipBounds = android.graphics.Rect()

    private val gripFillPaint = Paint().apply {
        color = Color.parseColor("#FF007AFF") // iOS Blue
        style = Paint.Style.FILL
        isAntiAlias = true
    }
    
    private val gripBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val gripLinePaint = Paint().apply {
        color = Color.parseColor("#FF007AFF")
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val gripHandlePaint = Paint().apply {
        color = Color.parseColor("#FF007AFF")
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val gripHandleBorderPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    private val snapTrianglePath = Path()

    fun applyFillStyle(paint: Paint, style: FillStyle, alphaMultiplier: Float = 1f) {
        paint.shader = null // Clear previous shader
        when (style) {
            is FillStyle.Solid -> {
                val origColor = style.color
                val origAlpha = Color.alpha(origColor)
                val newAlpha = (origAlpha * alphaMultiplier).toInt().coerceIn(0, 255)
                val solidColor = (origColor and 0x00FFFFFF) or (newAlpha shl 24)
                
                if (style.imagePath != null) {
                    val bitmap = ImageTextureCache.getOrCreate(style.imagePath)
                    if (bitmap != null) {
                        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                        tempShaderMatrix.reset()
                        val basePxPerMm = scaleConfig.basePixelsPerMillimeter.coerceAtLeast(0.001f)
                        val targetSizePx = 100f * basePxPerMm
                        val baseScaleX = targetSizePx / bitmap.width
                        val baseScaleY = targetSizePx / bitmap.height
                        val matrix = tempShaderMatrix.apply {
                            postScale(baseScaleX * style.scaleX, baseScaleY * style.scaleY)
                            postRotate(style.rotation)
                            postTranslate(style.offsetX, style.offsetY)
                        }
                        shader.setLocalMatrix(matrix)
                        paint.shader = shader
                        
                        val filterColor = if (style.tintMix > 0f) {
                            val mixAlpha = (style.tintMix.coerceIn(0f, 1f) * 255).toInt()
                            (solidColor and 0x00FFFFFF) or (mixAlpha shl 24)
                        } else {
                            solidColor
                        }
                        
                        val finalAlpha = (style.opacity * alphaMultiplier * 255).toInt().coerceIn(0, 255)
                        paint.color = Color.argb(finalAlpha, 255, 255, 255)
                        
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val blendMode = try {
                                android.graphics.BlendMode.valueOf(style.blendModeName)
                            } catch (e: Exception) {
                                android.graphics.BlendMode.SRC_ATOP
                            }
                            paint.colorFilter = android.graphics.BlendModeColorFilter(filterColor, blendMode)
                        } else {
                            val pdMode = try {
                                PorterDuff.Mode.valueOf(style.blendModeName)
                            } catch (e: Exception) {
                                if (style.blendModeName == "DIFFERENCE") PorterDuff.Mode.MULTIPLY else PorterDuff.Mode.SRC_ATOP
                            }
                            paint.colorFilter = PorterDuffColorFilter(filterColor, pdMode)
                        }
                    } else {
                        paint.color = solidColor
                        paint.shader = null
                        paint.colorFilter = null
                    }
                } else {
                    paint.color = solidColor
                    paint.shader = null
                    paint.colorFilter = null
                }
            }
            is FillStyle.SvgPattern -> {
                val bitmap = SvgPatternCache.getOrCreate(style)
                if (bitmap != null) {
                    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    tempShaderMatrix.reset()
                    val matrix = tempShaderMatrix.apply {
                        postScale(style.scaleX, style.scaleY)
                        postRotate(style.rotation)
                        postTranslate(style.offsetX, style.offsetY)
                    }
                    shader.setLocalMatrix(matrix)
                    paint.shader = shader
                    val finalAlpha = (style.opacity * alphaMultiplier * 255).toInt().coerceIn(0, 255)
                    paint.color = Color.argb(finalAlpha, 255, 255, 255)
                } else {
                    paint.color = Color.TRANSPARENT
                }
            }
            is FillStyle.MathTexture -> {
                if (style.patternName.uppercase() in listOf("NOTEBOOK", "MATH_GRID", "CALLIGRAPHY")) {
                    val origColor = style.secondaryColor
                    val origAlpha = Color.alpha(origColor)
                    val finalAlpha = (origAlpha * style.opacity * alphaMultiplier).toInt().coerceIn(0, 255)
                    paint.color = (origColor and 0x00FFFFFF) or (finalAlpha shl 24)
                } else {
                    val bitmap = MathTextureCache.getOrCreate(style)
                    if (bitmap != null) {
                        val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                        tempShaderMatrix.reset()
                        val matrix = tempShaderMatrix.apply {
                            postRotate(style.angle)
                        }
                        shader.setLocalMatrix(matrix)
                        paint.shader = shader
                        val finalAlpha = (style.opacity * alphaMultiplier * 255).toInt().coerceIn(0, 255)
                        paint.color = Color.argb(finalAlpha, 255, 255, 255)
                    } else {
                        paint.color = Color.TRANSPARENT
                    }
                }
            }
            is FillStyle.ImageTexture -> {
                val bitmap = ImageTextureCache.getOrCreate(style.imagePath)
                if (bitmap != null) {
                    val shader = BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
                    tempShaderMatrix.reset()
                    val basePxPerMm = scaleConfig.basePixelsPerMillimeter.coerceAtLeast(0.001f)
                    val targetSizePx = 100f * basePxPerMm
                    val baseScaleX = targetSizePx / bitmap.width
                    val baseScaleY = targetSizePx / bitmap.height
                    val matrix = tempShaderMatrix.apply {
                        postScale(baseScaleX * style.scaleX, baseScaleY * style.scaleY)
                        postRotate(style.rotation)
                        postTranslate(style.offsetX, style.offsetY)
                    }
                    shader.setLocalMatrix(matrix)
                    paint.shader = shader
                    val finalAlpha = (style.opacity * alphaMultiplier * 255).toInt().coerceIn(0, 255)
                    paint.color = Color.argb(finalAlpha, 255, 255, 255)
                    if (style.tintColor != Color.TRANSPARENT && style.tintMix > 0f) {
                        val filterColor = (style.tintColor and 0x00FFFFFF) or (((style.tintMix).coerceIn(0f, 1f) * 255).toInt() shl 24)
                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                            val blendMode = try {
                                android.graphics.BlendMode.valueOf(style.blendModeName)
                            } catch (e: Exception) {
                                android.graphics.BlendMode.SRC_ATOP
                            }
                            paint.colorFilter = android.graphics.BlendModeColorFilter(filterColor, blendMode)
                        } else {
                            val pdMode = try {
                                PorterDuff.Mode.valueOf(style.blendModeName)
                            } catch (e: Exception) {
                                if (style.blendModeName == "DIFFERENCE") PorterDuff.Mode.MULTIPLY else PorterDuff.Mode.SRC_ATOP
                            }
                            paint.colorFilter = PorterDuffColorFilter(filterColor, pdMode)
                        }
                    } else {
                        paint.colorFilter = null
                    }
                } else {
                    paint.color = Color.TRANSPARENT
                    paint.colorFilter = null
                }
            }
        }
    }

    fun drawPaperBackground(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        style: FillStyle,
        pixelsPerMm: Float = 5.0f
    ) {
        val bgPaint = Paint().apply {
            this.style = Paint.Style.FILL
            isAntiAlias = true
        }
        applyFillStyle(bgPaint, style, alphaMultiplier = 1f)
        canvas.drawRect(left, top, right, bottom, bgPaint)

        if (style is FillStyle.MathTexture) {
            when (style.patternName.uppercase()) {
                "NOTEBOOK" -> {
                    drawNotebookBackground(canvas, left, top, right, bottom, pixelsPerMm)
                }
                "MATH_GRID" -> {
                    drawMathGridBackground(canvas, left, top, right, bottom, pixelsPerMm)
                }
                "CALLIGRAPHY" -> {
                    drawCalligraphyBackground(canvas, left, top, right, bottom, pixelsPerMm)
                }
            }
        }
    }

    private fun drawNotebookBackground(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, pixelsPerMm: Float) {
        val marginX = left + 32f * pixelsPerMm
        val lineSpacing = 8f * pixelsPerMm
        val topMargin = top + 35f * pixelsPerMm
        
        val linePaint = Paint().apply {
            color = Color.parseColor("#C5D0E6") // Soft light blue-gray
            strokeWidth = 1f * (pixelsPerMm / 5f)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        val marginPaint = Paint().apply {
            color = Color.parseColor("#FF5252") // Soft red
            strokeWidth = 1.5f * (pixelsPerMm / 5f)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        // Draw horizontal lines
        var currentY = topMargin
        while (currentY < bottom) {
            canvas.drawLine(left, currentY, right, currentY, linePaint)
            currentY += lineSpacing
        }
        
        // Draw vertical margin line
        if (marginX < right) {
            canvas.drawLine(marginX, top, marginX, bottom, marginPaint)
        }
    }

    private fun drawMathGridBackground(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, pixelsPerMm: Float) {
        val marginX = left + 32f * pixelsPerMm
        val gridSpacing = 5f * pixelsPerMm
        
        val gridPaint = Paint().apply {
            color = Color.parseColor("#D9E1F0") // Very soft grid lines
            strokeWidth = 0.8f * (pixelsPerMm / 5f)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        val marginPaint = Paint().apply {
            color = Color.parseColor("#FF5252") // Soft red
            strokeWidth = 1.5f * (pixelsPerMm / 5f)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        // Draw vertical grid lines
        var currentX = left + gridSpacing
        while (currentX < right) {
            canvas.drawLine(currentX, top, currentX, bottom, gridPaint)
            currentX += gridSpacing
        }
        
        // Draw horizontal grid lines
        var currentY = top + gridSpacing
        while (currentY < bottom) {
            canvas.drawLine(left, currentY, right, currentY, gridPaint)
            currentY += gridSpacing
        }
        
        // Draw vertical margin line
        if (marginX < right) {
            canvas.drawLine(marginX, top, marginX, bottom, marginPaint)
        }
    }

    private fun drawCalligraphyBackground(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float, pixelsPerMm: Float) {
        val marginX = left + 32f * pixelsPerMm
        val bandHeight = 4f * pixelsPerMm   // Inside height of double lines (for lowercase letters)
        val bandSpacing = 8f * pixelsPerMm  // Distance between consecutive bands
        val topMargin = top + 35f * pixelsPerMm
        
        val linePaint = Paint().apply {
            color = Color.parseColor("#A2B5CD") // Soft blue-gray
            strokeWidth = 1f * (pixelsPerMm / 5f)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        val shadedPaint = Paint().apply {
            color = Color.parseColor("#15A2B5CD") // 8% opacity blue-gray shading
            style = Paint.Style.FILL
        }
        
        val marginPaint = Paint().apply {
            color = Color.parseColor("#FF5252") // Soft red
            strokeWidth = 1.5f * (pixelsPerMm / 5f)
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        
        // Draw calligraphy lines and bands
        var currentY = topMargin
        while (currentY + bandHeight < bottom) {
            // Draw shaded band inside the pair of lines
            canvas.drawRect(left, currentY, right, currentY + bandHeight, shadedPaint)
            
            // Draw the top and bottom lines of the band
            canvas.drawLine(left, currentY, right, currentY, linePaint)
            canvas.drawLine(left, currentY + bandHeight, right, currentY + bandHeight, linePaint)
            
            // Move to next band
            currentY += bandHeight + bandSpacing
        }
        
        // Draw vertical margin line
        if (marginX < right) {
            canvas.drawLine(marginX, top, marginX, bottom, marginPaint)
        }
    }

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
        editingParent: LayerElement? = null,
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
            
            val pixelsPerMm = if (sizeConfig.preset != null) {
                val widthMm = if (sizeConfig.orientation == PaperOrientation.PORTRAIT) {
                    sizeConfig.preset.widthMm
                } else {
                    sizeConfig.preset.heightMm
                }
                sizeConfig.widthInPixels / widthMm
            } else {
                sizeConfig.widthInPixels / 215.9f // Letter width fallback
            }
            
            drawPaperBackground(canvas, left, top, right, bottom, canvasBackgroundStyle, pixelsPerMm)
            
            // Draw paper bounds and shadow
            RenderHelper.drawCanvasBounds(canvas, left, top, right, bottom)
            
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
            if (canvasBackgroundStyle is FillStyle.Solid) {
                canvas.drawColor((canvasBackgroundStyle as FillStyle.Solid).color)
            } else {
                // If it is a texture/pattern, we draw it in World Space so that panning/zooming works.
                canvas.save()
                canvas.concat(viewMatrix)
                
                // Get the viewport size in world coordinates to fill the screen correctly.
                val inverse = Matrix()
                if (viewMatrix.invert(inverse)) {
                    val pts = floatArrayOf(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat())
                    inverse.mapPoints(pts)
                    val wLeft = minOf(pts[0], pts[2])
                    val wRight = maxOf(pts[0], pts[2])
                    val wTop = minOf(pts[1], pts[3])
                    val wBottom = maxOf(pts[1], pts[3])
                    
                    // Add margin to avoid issues with fast panning/zooming or rotation
                    val widthMargin = (wRight - wLeft) * 0.5f
                    val heightMargin = (wBottom - wTop) * 0.5f
                    
                    val bgPaint = Paint().apply {
                        style = Paint.Style.FILL
                        isAntiAlias = true
                    }
                    applyFillStyle(bgPaint, canvasBackgroundStyle, alphaMultiplier = 1f)
                    canvas.drawRect(wLeft - widthMargin, wTop - heightMargin, wRight + widthMargin, wBottom + heightMargin, bgPaint)
                } else {
                    // Fallback to solid color if matrix not invertible
                    canvas.drawColor(canvasBackgroundColor)
                }
                canvas.restore()
            }
            
            if (drawGrid) {
                canvas.save()
                canvas.concat(viewMatrix)
                drawGrid(canvas, viewMatrix, false, null)
                canvas.restore()   
            }
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
             
             canvas.save()
             canvas.concat(viewMatrix)
             
             for (element in layer.elements) {
                  if (isCancelled()) {
                      canvas.restore()
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

                  val alphaMultiplier = (if (editingParent != null) {
                      if (element === editingParent) 1.0f else 0.3f
                  } else {
                      1.0f
                  }) * layer.opacity

                  drawElementRecursive(canvas, element, componentLibrary, viewMatrix, alphaMultiplier)
             }
             
             canvas.restore()
        }
    }
    
    // Recursive drawing for groups/components
    fun drawElementRecursive(
        canvas: Canvas, 
        element: LayerElement, 
        library: Map<String, ComponentDefinition>, 
        viewMatrix: Matrix,
        alphaMultiplier: Float = 1f
    ) {
         val elementId = when (element) {
             is TextElement -> element.id
             is ImageElement -> element.id
             is GroupElement -> element.id
             is SvgElement -> element.id
             is ComponentInstance -> element.id
             else -> null
         }
         if (elementId != null && elementId == hiddenElementId) return

         when (element) {
             is VectorStroke -> drawVectorStroke(canvas, element, viewMatrix, alphaMultiplier)
             is FillData -> drawFill(canvas, element, alphaMultiplier)
             is GroupElement -> {
                 canvas.save()
                 canvas.concat(element.matrix)
                 val nextMatrix = obtainMatrix().apply { set(viewMatrix) }
                 nextMatrix.postConcat(element.matrix)
                 element.elements.forEach { drawElementRecursive(canvas, it, library, nextMatrix, alphaMultiplier) }
                 releaseMatrix()
                 canvas.restore()
             }
             is ComponentInstance -> {
                 val def = library[element.definitionId]
                 if (def != null) {
                     canvas.save()
                     canvas.concat(element.matrix)
                     val nextMatrix = obtainMatrix().apply { set(viewMatrix) }
                     nextMatrix.postConcat(element.matrix)
                     def.elements.forEach { drawElementRecursive(canvas, it, library, nextMatrix, alphaMultiplier) }
                     releaseMatrix()
                     canvas.restore()
                 }
             }
             is ImageElement -> {
                 val origAlpha = imagePaint.alpha
                 val newAlpha = (origAlpha * alphaMultiplier).toInt().coerceIn(0, 255)
                 imagePaint.alpha = newAlpha
                 canvas.drawBitmap(element.bitmap, element.matrix, imagePaint)
                 imagePaint.alpha = origAlpha
             }
              is SvgElement -> {
                  if (alphaMultiplier < 1f) {
                      val bounds = element.getBoundingBox(library)
                      val saveCount = canvas.saveLayer(bounds, svgAlphaPaint.apply { alpha = (alphaMultiplier * 255).toInt().coerceIn(0, 255) })
                      element.render(canvas)
                      canvas.restoreToCount(saveCount)
                  } else {
                      element.render(canvas)
                  }
              }
              is TextElement -> {
                  drawTextElement(canvas, element, alphaMultiplier)
              }
              else -> {} // Unknown
          }
     }

     private fun drawTextElement(canvas: Canvas, element: TextElement, alphaMultiplier: Float) {
         canvas.save()
         canvas.concat(element.getMatrix())

         val spanned = Html.fromHtml(element.textHtml, Html.FROM_HTML_MODE_LEGACY)
         val textPaint = TextPaint().apply {
             isAntiAlias = true
             textSize = element.defaultTextSize
             color = element.defaultTextColor
             alpha = (alphaMultiplier * 255).toInt().coerceIn(0, 255)
         }
         
         try {
             textPaint.typeface = android.graphics.Typeface.create(element.fontFamilyName, android.graphics.Typeface.NORMAL)
         } catch (e: Exception) {
             // Fallback
         }

         val layoutAlignment = when (element.alignment) {
             "CENTER" -> Layout.Alignment.ALIGN_CENTER
             "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
             else -> Layout.Alignment.ALIGN_NORMAL
         }

         val textWidth = if (element.width > 0f) element.width.toInt() else 1

         val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, textPaint, textWidth)
             .setAlignment(layoutAlignment)
             .setLineSpacing(0f, 1f)
             .setIncludePad(true)
             .build()

         layout.draw(canvas)
         canvas.restore()
     }

      private fun drawVectorStroke(canvas: Canvas, stroke: VectorStroke, viewMatrix: Matrix, alphaMultiplier: Float = 1f) {
        viewMatrix.getValues(tempFloatArray)
        val zoom = kotlin.math.sqrt(tempFloatArray[Matrix.MSCALE_X] * tempFloatArray[Matrix.MSCALE_X] + tempFloatArray[Matrix.MSKEW_X] * tempFloatArray[Matrix.MSKEW_X]).coerceAtLeast(0.001f)

        val isPaintOrWatercolor = stroke.brushType == "PAINT" || stroke.brushType == "WATERCOLOR"
        if (isPaintOrWatercolor) {
            // 1. Draw Fill (if enabled) directly
            if (stroke.isFillEnabled && stroke.fillPath != null) {
                vectorPaint.style = Paint.Style.FILL
                applyFillStyle(vectorPaint, stroke.fillStyle, alphaMultiplier)
                canvas.drawPath(stroke.fillPath, vectorPaint)
                vectorPaint.shader = null
            }
            
            // 2. Draw Stroke outline (if enabled) directly
            if (stroke.isStrokeEnabled) {
                stroke.getBrushRenderer().draw(canvas, stroke, vectorPaint, alphaMultiplier, zoom = zoom) { p, alpha ->
                    applyFillStyle(p, stroke.strokeStyle, alpha)
                }
            }
            return
        }

        // Pass 1: FILL (if enabled)
        if (stroke.isFillEnabled && stroke.fillPath != null) {
            vectorPaint.style = Paint.Style.FILL
            applyFillStyle(vectorPaint, stroke.fillStyle, alphaMultiplier)
            canvas.drawPath(stroke.fillPath, vectorPaint)
            vectorPaint.shader = null
        }

        // Pass 2: STROKE (if enabled)
        if (stroke.isStrokeEnabled) {
            val isMeshBrush = stroke.brushType == "FREEHAND" || stroke.brushType == "PEN" || stroke.brushType == "PLUMA" || stroke.brushType == "PENCIL_CUMULATIVE" || stroke.brushType == "PAINT" || stroke.brushType == "WATERCOLOR"
            if (isMeshBrush) {
                val isCumulative = stroke.brushType == "PENCIL_CUMULATIVE"
                val strokeOpacity = stroke.strokeStyle.opacity
                val totalOpacity = alphaMultiplier * strokeOpacity
                
                if (totalOpacity < 1f && !isCumulative) {
                    val bounds = stroke.getBoundingBox(emptyMap())
                    val tempBounds = RectF(bounds)
                    val pad = stroke.maxWidth.coerceAtLeast(4f) * 1.5f
                    tempBounds.inset(-pad, -pad)
                    
                    val savePaint = layerAlphaPaint.apply { alpha = (totalOpacity * 255).toInt().coerceIn(0, 255) }
                    val saveCount = canvas.saveLayer(tempBounds, savePaint)
                    
                    stroke.getBrushRenderer().draw(canvas, stroke, vectorPaint, 1f, zoom = zoom) { p, alpha ->
                        applyFillStyle(p, stroke.strokeStyle.copyWithOpacity(1f), alpha)
                    }
                    canvas.restoreToCount(saveCount)
                } else {
                    stroke.getBrushRenderer().draw(canvas, stroke, vectorPaint, alphaMultiplier, zoom = zoom) { p, alpha ->
                        applyFillStyle(p, stroke.strokeStyle, alpha)
                    }
                }
            } else {
                // For others, it's a line
                vectorPaint.style = Paint.Style.STROKE
                applyFillStyle(vectorPaint, stroke.strokeStyle, alphaMultiplier)
                
                var width = if (stroke.maxWidth > 0) stroke.maxWidth else 0f
                if (stroke.isScreenSpaceWidth) {
                    viewMatrix.getValues(tempFloatArray)
                    val zoom = kotlin.math.sqrt(tempFloatArray[Matrix.MSCALE_X] * tempFloatArray[Matrix.MSCALE_X] + tempFloatArray[Matrix.MSKEW_X] * tempFloatArray[Matrix.MSKEW_X])
                    if (zoom > 0.001f) {
                        width /= zoom
                    }
                }
                vectorPaint.strokeWidth = width

                // Apply dash path effect for CAD styles
                if (stroke.isCadGeometry) {
                    when (stroke.lineStyle.uppercase()) {
                        "DASHED" -> {
                            val interval = kotlin.math.max(1f, width)
                            vectorPaint.pathEffect = android.graphics.DashPathEffect(
                                floatArrayOf(4f * interval, 2f * interval), 0f
                            )
                        }
                        "DOTTED" -> {
                            val interval = kotlin.math.max(1f, width)
                            // With round caps, 0-length dash draws a dot
                            vectorPaint.pathEffect = android.graphics.DashPathEffect(
                                floatArrayOf(0f, 2f * interval), 0f
                            )
                        }
                        else -> {
                            vectorPaint.pathEffect = null
                        }
                    }
                } else {
                    vectorPaint.pathEffect = null
                }

                canvas.drawPath(stroke.path, vectorPaint)
                vectorPaint.pathEffect = null
            }
        }

        // Pass 3: DEBUG WIREFRAME (if enabled and stroke has points)
        if (isDebugWireframe && stroke.points.isNotEmpty()) {
            drawDebugWireframe(canvas, stroke.points, viewMatrix, stroke.path, stroke.paths)
        }
    }

    fun drawTextWidthGrips(canvas: Canvas, element: TextElement, viewMatrix: Matrix, density: Float) {
        val size = 6f * density
        val pts = FloatArray(2)
        
        gripHandlePaint.style = Paint.Style.FILL
        gripHandlePaint.color = Color.WHITE
        gripHandleBorderPaint.strokeWidth = 1.5f * density
        
        // The text bounds in local space
        val w = element.width
        val h = element.getBoundingBox(emptyMap()).let {
            // getBoundingBox is already transformed by element matrix, but we need local height.
            // Actually, getBoundingBox does mapRect.
            // Let's just use an estimate for height or just draw at the middle of local Y.
            // The local bounds are (0, 0) to (w, localHeight)
            val tempRect = RectF(0f, 0f, element.width, 100f) // Fallback height
            tempRect
        }
        // Actually, just draw at local y = 0 or middle? Let's do local x=w, y=0. Or middle of line 1.
        // It's easier to draw them at (0, 0) and (w, 0) in local coords, but centered vertically is better.
        // Let's put the handle at local (w, 0) for now, or just left and right.
        
        val leftAnchor = FloatArray(2).apply { this[0] = 0f; this[1] = 0f }
        val rightAnchor = FloatArray(2).apply { this[0] = element.width; this[1] = 0f }
        
        val combinedMatrix = Matrix(viewMatrix)
        combinedMatrix.preConcat(element.getMatrix())
        
        combinedMatrix.mapPoints(leftAnchor)
        combinedMatrix.mapPoints(rightAnchor)
        
        // Draw Left Grip
        canvas.drawCircle(leftAnchor[0], leftAnchor[1], size, gripHandlePaint)
        canvas.drawCircle(leftAnchor[0], leftAnchor[1], size, gripHandleBorderPaint)
        
        // Draw Right Grip
        canvas.drawCircle(rightAnchor[0], rightAnchor[1], size, gripHandlePaint)
        canvas.drawCircle(rightAnchor[0], rightAnchor[1], size, gripHandleBorderPaint)
    }

    fun drawGrips(canvas: Canvas, stroke: VectorStroke, viewMatrix: Matrix, density: Float) {
        val size = 6f * density // 6dp half size
        val pts = FloatArray(2)
        
        if (stroke.strokeType == StrokeType.BEZIER) {
            val sizeAnchor = 6f * density
            val sizeHandle = 4f * density
            val anchorPts = FloatArray(2)
            
            gripLinePaint.strokeWidth = 1.5f * density
            gripHandleBorderPaint.strokeWidth = 1f * density
            
            val N = stroke.points.size
            val numNodes = (N + 2) / 3
            
            // Draw tangent lines and handles first
            for (i in 0 until numNodes) {
                val anchorIdx = 3 * i
                if (anchorIdx >= N) break
                val anchor = stroke.points[anchorIdx]
                
                anchorPts[0] = anchor.x
                anchorPts[1] = anchor.y
                viewMatrix.mapPoints(anchorPts)
                val ax = anchorPts[0]
                val ay = anchorPts[1]
                
                val outIdx = anchorIdx + 1
                if (outIdx < N) {
                    val outPt = stroke.points[outIdx]
                    pts[0] = outPt.x
                    pts[1] = outPt.y
                    viewMatrix.mapPoints(pts)
                    val ox = pts[0]
                    val oy = pts[1]
                    canvas.drawLine(ax, ay, ox, oy, gripLinePaint)
                    canvas.drawCircle(ox, oy, sizeHandle, gripHandlePaint)
                    canvas.drawCircle(ox, oy, sizeHandle, gripHandleBorderPaint)
                }
                
                val inIdx = anchorIdx - 1
                if (anchorIdx > 0 && inIdx < N) {
                    val inPt = stroke.points[inIdx]
                    pts[0] = inPt.x
                    pts[1] = inPt.y
                    viewMatrix.mapPoints(pts)
                    val ix = pts[0]
                    val iy = pts[1]
                    canvas.drawLine(ax, ay, ix, iy, gripLinePaint)
                    canvas.drawCircle(ix, iy, sizeHandle, gripHandlePaint)
                    canvas.drawCircle(ix, iy, sizeHandle, gripHandleBorderPaint)
                }
            }
            
            // Draw main anchors
            for (i in 0 until numNodes) {
                val anchorIdx = 3 * i
                if (anchorIdx >= N) break
                val anchor = stroke.points[anchorIdx]
                pts[0] = anchor.x
                pts[1] = anchor.y
                viewMatrix.mapPoints(pts)
                val ax = pts[0]
                val ay = pts[1]
                canvas.drawRect(ax - sizeAnchor, ay - sizeAnchor, ax + sizeAnchor, ay + sizeAnchor, gripFillPaint)
                canvas.drawRect(ax - sizeAnchor, ay - sizeAnchor, ax + sizeAnchor, ay + sizeAnchor, gripBorderPaint)
            }
        } else {
            for (p in stroke.points) {
                pts[0] = p.x
                pts[1] = p.y
                viewMatrix.mapPoints(pts)
                val sx = pts[0]
                val sy = pts[1]
                canvas.drawRect(sx - size, sy - size, sx + size, sy + size, gripFillPaint)
                canvas.drawRect(sx - size, sy - size, sx + size, sy + size, gripBorderPaint)
            }
        }
    }

    
    fun drawFill(canvas: Canvas, fill: FillData, alphaMultiplier: Float = 1f) {
        applyFillStyle(fillPaint, fill.fillStyle, alphaMultiplier)
        canvas.drawPath(fill.path, fillPaint)
        fillPaint.shader = null
    }

    /**
     * Draws the committed (baked) head of a live stroke.
     * Called before drawLiveStroke so the live tail is rendered on top,
     * covering the end cap of the committed polygon and hiding any seam.
     */
    fun drawCommittedPreview(
        canvas: Canvas, 
        committedPath: Path, 
        strokeColor: Int, 
        fillColor: Int = 0, 
        isStrokeActive: Boolean = true, 
        isFillActive: Boolean = false, 
        strokeType: StrokeType = StrokeType.FREEHAND,
        brushType: String = "FREEHAND",
        fillStyle: FillStyle? = null,
        strokeStyle: FillStyle? = null
    ) {
        if (brushType == "PAINT" || brushType == "WATERCOLOR") {
            if (isFillActive) {
                vectorPaint.style = Paint.Style.FILL
                if (fillStyle != null) {
                    applyFillStyle(vectorPaint, fillStyle)
                } else {
                    vectorPaint.color = fillColor
                    vectorPaint.shader = null
                }
                canvas.drawPath(committedPath, vectorPaint)
            }
            if (isStrokeActive) {
                vectorPaint.style = Paint.Style.STROKE
                vectorPaint.strokeWidth = 2f
                vectorPaint.pathEffect = null
                if (strokeStyle != null) {
                    applyFillStyle(vectorPaint, strokeStyle)
                } else {
                    vectorPaint.color = strokeColor
                    vectorPaint.shader = null
                }
                canvas.drawPath(committedPath, vectorPaint)
            }
        } else {
            vectorPaint.style = Paint.Style.FILL
            if (strokeStyle != null) {
                applyFillStyle(vectorPaint, strokeStyle)
            } else {
                vectorPaint.color = strokeColor
                vectorPaint.shader = null
            }
            canvas.drawPath(committedPath, vectorPaint)
        }
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
        isDrawing: Boolean,
        isCad: Boolean = false,
        lineStyle: String = "SOLID",
        strokeType: StrokeType = StrokeType.FREEHAND,
        fillStyle: FillStyle? = null,
        strokeStyle: FillStyle? = null,
        brushType: String = "FREEHAND"
    ) {
         if (!isDrawing) return
         
         canvas.save()
         canvas.concat(viewMatrix)
    
         if (brushType == "PAINT" || brushType == "WATERCOLOR") {
             if (isFillActive && previewPath != null) {
                 vectorPaint.style = Paint.Style.FILL
                 if (fillStyle != null) {
                     applyFillStyle(vectorPaint, fillStyle)
                 } else {
                     vectorPaint.shader = null
                     vectorPaint.color = fillColor
                 }
                 canvas.drawPath(previewPath, vectorPaint)
                 vectorPaint.shader = null
             }
             if (isStrokeActive && previewPath != null) {
                 vectorPaint.style = Paint.Style.STROKE
                 vectorPaint.strokeWidth = 2f
                 if (strokeStyle != null) {
                     applyFillStyle(vectorPaint, strokeStyle)
                 } else {
                     vectorPaint.shader = null
                     vectorPaint.color = previewColor
                 }
                 vectorPaint.pathEffect = null
                 canvas.drawPath(previewPath, vectorPaint)
             }
         } else {
             // Pass 1: FILL
             if (isFillActive && fillPath != null) {
                 vectorPaint.style = Paint.Style.FILL
                 if (fillStyle != null) {
                     applyFillStyle(vectorPaint, fillStyle)
                 } else {
                     vectorPaint.shader = null
                     vectorPaint.color = fillColor
                 }
                 canvas.drawPath(fillPath, vectorPaint)
                 vectorPaint.shader = null
             }
        
             // Pass 2: STROKE
             if (isStrokeActive && previewPath != null) {
                 if (strokeStyle != null) {
                     applyFillStyle(vectorPaint, strokeStyle)
                 } else {
                     vectorPaint.shader = null
                     vectorPaint.color = previewColor
                 }
                 val width = if (isCad || previewPoints == null) currentLiveGeneratedRadius * 2 else 0f
                 val isBrushMesh = brushType == "FREEHAND" || brushType == "PEN" || brushType == "PLUMA" || brushType == "PENCIL_CUMULATIVE"
                 
                 if (isCad && !isBrushMesh) {
                     vectorPaint.style = Paint.Style.STROKE
                     vectorPaint.strokeWidth = width
                     
                     when (lineStyle.uppercase()) {
                         "DASHED" -> {
                             val interval = kotlin.math.max(1f, width)
                             vectorPaint.pathEffect = android.graphics.DashPathEffect(
                                 floatArrayOf(4f * interval, 2f * interval), 0f
                             )
                         }
                         "DOTTED" -> {
                             val interval = kotlin.math.max(1f, width)
                             vectorPaint.pathEffect = android.graphics.DashPathEffect(
                                 floatArrayOf(0f, 2f * interval), 0f
                             )
                         }
                         else -> {
                             vectorPaint.pathEffect = null
                         }
                     }
                 } else {
                     vectorPaint.style = if (previewPoints != null || isBrushMesh) Paint.Style.FILL else Paint.Style.STROKE
                     vectorPaint.strokeWidth = width
                     vectorPaint.pathEffect = null
                 }
                 
                 canvas.drawPath(previewPath, vectorPaint)
                 vectorPaint.pathEffect = null
             }
         }
         
         // Debug Wireframe
         if (isDebugWireframe && previewPoints != null) {
             drawDebugWireframe(canvas, previewPoints, viewMatrix, previewPath)
         }
         
         canvas.restore()
    }
    
    fun drawDebugWireframe(
        canvas: Canvas,
        points: List<StrokePoint>,
        viewMatrix: Matrix,
        meshPath: Path? = null,
        subPaths: List<Path> = emptyList()
    ) {
        // Calculate zoom for consistent hairline
        viewMatrix.getValues(tempFloatArray)
        val zoom = kotlin.math.sqrt(tempFloatArray[Matrix.MSCALE_X] * tempFloatArray[Matrix.MSCALE_X] + tempFloatArray[Matrix.MSKEW_X] * tempFloatArray[Matrix.MSKEW_X])
        
        debugPaint.strokeWidth = 2f / zoom
        val pRadius = 4f / zoom
        
        // 1. Draw the spine (center line) and points
        debugPath.reset()
        if (points.isNotEmpty()) {
            debugPath.moveTo(points.first().x, points.first().y)
            for (p in points) {
                debugPath.lineTo(p.x, p.y)
                canvas.drawCircle(p.x, p.y, pRadius, debugPointPaint)
            }
        }
        canvas.drawPath(debugPath, debugPaint)

        // 2. Draw the mesh/path outline (if provided)
        if (meshPath != null) {
            canvas.drawPath(meshPath, debugPaint)
        }
        for (sub in subPaths) {
            canvas.drawPath(sub, debugPaint)
        }
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

    fun drawSelectionOverlay(canvas: Canvas, manager: SelectionManager, viewMatrix: Matrix, density: Float) {
        if (manager.selectedElements.isEmpty()) return
        val bounds = manager.baseBounds
        if (bounds.isEmpty) return

        // Combined matrix of view transformation and selection transformation
        val combinedMatrix = Matrix(viewMatrix)
        combinedMatrix.preConcat(manager.selectionMatrix)

        // Key selection handle coordinates mapped to screen coordinates
        val pts = FloatArray(16)
        pts[0] = bounds.left;       pts[1] = bounds.top        // TL
        pts[2] = bounds.right;      pts[3] = bounds.top        // TR
        pts[4] = bounds.right;      pts[5] = bounds.bottom     // BR
        pts[6] = bounds.left;       pts[7] = bounds.bottom     // BL
        pts[8] = bounds.centerX();  pts[9] = bounds.top        // TC
        pts[10] = bounds.centerX(); pts[11] = bounds.bottom    // BC
        pts[12] = bounds.left;      pts[13] = bounds.centerY() // LC
        pts[14] = bounds.right;     pts[15] = bounds.centerY() // RC

        combinedMatrix.mapPoints(pts)

        // Draw Bounding Box Fill in screen space
        val fillPath = Path()
        fillPath.moveTo(pts[0], pts[1])
        fillPath.lineTo(pts[2], pts[3])
        fillPath.lineTo(pts[4], pts[5])
        fillPath.lineTo(pts[6], pts[7])
        fillPath.close()
        canvas.drawPath(fillPath, selectionBoxPaint)

        // Draw Bounding Box Borders
        selectionBorderPaint.strokeWidth = 2f * density
        canvas.drawLine(pts[0], pts[1], pts[2], pts[3], selectionBorderPaint)
        canvas.drawLine(pts[2], pts[3], pts[4], pts[5], selectionBorderPaint)
        canvas.drawLine(pts[4], pts[5], pts[6], pts[7], selectionBorderPaint)
        canvas.drawLine(pts[6], pts[7], pts[0], pts[1], selectionBorderPaint)

        // Draw Handles
        val handleRadius = 6f * density
        
        // Corners & Edges
        if (!manager.isScaleLocked) {
            // Corners
            drawHandle(canvas, pts[0], pts[1], handleRadius, density) // TL
            drawHandle(canvas, pts[2], pts[3], handleRadius, density) // TR
            drawHandle(canvas, pts[4], pts[5], handleRadius, density) // BR
            drawHandle(canvas, pts[6], pts[7], handleRadius, density) // BL
            
            // Edges
            drawHandle(canvas, pts[8], pts[9], handleRadius, density)   // TC
            drawHandle(canvas, pts[10], pts[11], handleRadius, density) // BC
            drawHandle(canvas, pts[12], pts[13], handleRadius, density) // LC
            drawHandle(canvas, pts[14], pts[15], handleRadius, density) // RC
        }

        // Rotate Handle (extended from TC pointing away from BC)
        val dx = pts[8] - pts[10]
        val dy = pts[9] - pts[11]
        val len = kotlin.math.hypot(dx, dy)
        val ux = if (len > 0f) dx / len else 0f
        val uy = if (len > 0f) dy / len else -1f
        
        val stemLength = 30f * density
        val rotX = pts[8] + ux * stemLength
        val rotY = pts[9] + uy * stemLength
        
        canvas.drawLine(pts[8], pts[9], rotX, rotY, selectionBorderPaint)
        drawHandle(canvas, rotX, rotY, handleRadius, density)
    }
    
    private fun drawHandle(canvas: Canvas, x: Float, y: Float, size: Float, density: Float) {
        selectionBorderPaint.strokeWidth = 2f * density
        canvas.drawCircle(x, y, size, selectionHandlePaint)
        canvas.drawCircle(x, y, size, selectionBorderPaint)
    }

    fun drawSnapMarker(canvas: Canvas, snapPoint: SnapPoint, viewMatrix: Matrix, density: Float) {
        val pts = floatArrayOf(snapPoint.point.x, snapPoint.point.y)
        viewMatrix.mapPoints(pts)
        val sx = pts[0]
        val sy = pts[1]

        val size = 8f * density // 8dp half size

        canvas.save()
        when (snapPoint.type) {
            SnapType.ENDPOINT -> {
                // Square
                canvas.drawRect(sx - size, sy - size, sx + size, sy + size, snapPaint)
            }
            SnapType.MIDPOINT -> {
                // Triangle
                snapTrianglePath.reset()
                snapTrianglePath.moveTo(sx, sy - size)
                snapTrianglePath.lineTo(sx + size, sy + size)
                snapTrianglePath.lineTo(sx - size, sy + size)
                snapTrianglePath.close()
                canvas.drawPath(snapTrianglePath, snapPaint)
            }
            SnapType.CENTER -> {
                // Circle
                canvas.drawCircle(sx, sy, size, snapPaint)
            }
            SnapType.INTERSECTION -> {
                // Cross (X)
                canvas.drawLine(sx - size, sy - size, sx + size, sy + size, snapPaint)
                canvas.drawLine(sx + size, sy - size, sx - size, sy + size, snapPaint)
            }
        }
        canvas.restore()
    }
}

package com.sketcher.sketchercompanionv1.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF

object ImageProcessor {

    /**
     * Processes the original bitmap by applying rotation/flip, cropping (rect or freehand), 
     * and transparency keying with individual color tolerances.
     */
    fun processImage(
        original: Bitmap,
        rotation: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        transparentColors: List<Int>,
        transparentColorTolerances: List<Float>,
        cropRect: RectF?,
        cropPath: List<PointF>?
    ): Bitmap {
        // 1. Apply Rotation and Flip
        var transformed: Bitmap = original
        if (rotation != 0f || flipHorizontal || flipVertical) {
            val matrix = android.graphics.Matrix()
            if (flipHorizontal) {
                matrix.postScale(-1f, 1f, original.width / 2f, original.height / 2f)
            }
            if (flipVertical) {
                matrix.postScale(1f, -1f, original.width / 2f, original.height / 2f)
            }
            if (rotation != 0f) {
                matrix.postRotate(rotation)
            }
            try {
                transformed = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matrix, true)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Apply Cropping
        var cropped: Bitmap = transformed
        if (cropPath != null && cropPath.size >= 3) {
            try {
                // Freehand crop
                val path = Path()
                path.moveTo(cropPath[0].x, cropPath[0].y)
                for (i in 1 until cropPath.size) {
                    path.lineTo(cropPath[i].x, cropPath[i].y)
                }
                path.close()
                
                val bounds = RectF()
                path.computeBounds(bounds, true)
                val left = bounds.left.toInt().coerceIn(0, transformed.width - 1)
                val top = bounds.top.toInt().coerceIn(0, transformed.height - 1)
                val right = bounds.right.toInt().coerceIn(left + 1, transformed.width)
                val bottom = bounds.bottom.toInt().coerceIn(top + 1, transformed.height)
                val width = right - left
                val height = bottom - top
                
                if (width > 0 && height > 0) {
                    val maskBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(maskBitmap)
                    
                    // Translate path to mask coordinates
                    val translateMatrix = android.graphics.Matrix().apply { setTranslate(-left.toFloat(), -top.toFloat()) }
                    val translatedPath = Path(path)
                    translatedPath.transform(translateMatrix)
                    
                    val paint = Paint().apply {
                        isAntiAlias = true
                        style = Paint.Style.FILL
                        color = android.graphics.Color.BLACK
                    }
                    canvas.drawPath(translatedPath, paint)
                    
                    // Source-in draw the transformed bitmap cropped region
                    val srcPaint = Paint().apply {
                        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
                    }
                    canvas.drawBitmap(transformed, -left.toFloat(), -top.toFloat(), srcPaint)
                    cropped = maskBitmap
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else if (cropRect != null) {
            try {
                // Rectangular crop
                val left = cropRect.left.toInt().coerceIn(0, transformed.width - 1)
                val top = cropRect.top.toInt().coerceIn(0, transformed.height - 1)
                val right = cropRect.right.toInt().coerceIn(left + 1, transformed.width)
                val bottom = cropRect.bottom.toInt().coerceIn(top + 1, transformed.height)
                val width = right - left
                val height = bottom - top
                if (width > 0 && height > 0) {
                    cropped = Bitmap.createBitmap(transformed, left, top, width, height)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Ensure cropped is mutable and has ARGB_8888 config
        val mutableBitmap = if (cropped.isMutable && cropped.config == Bitmap.Config.ARGB_8888) {
            cropped
        } else {
            cropped.copy(Bitmap.Config.ARGB_8888, true)
        }
        
        // 3. Apply Transparency Filter with per-color tolerances
        if (transparentColors.isNotEmpty()) {
            val width = mutableBitmap.width
            val height = mutableBitmap.height
            val pixels = IntArray(width * height)
            mutableBitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            
            // Pre-calculate squared thresholds for each color to avoid sqrt/pow in loop
            val thresholdsSq = FloatArray(transparentColors.size)
            for (j in transparentColors.indices) {
                val tol = if (j < transparentColorTolerances.size) transparentColorTolerances[j] else 10f
                val threshold = (tol / 100f) * 441.67f
                thresholdsSq[j] = threshold * threshold
            }
            
            for (i in pixels.indices) {
                val color = pixels[i]
                if (android.graphics.Color.alpha(color) == 0) continue
                
                val r = android.graphics.Color.red(color)
                val g = android.graphics.Color.green(color)
                val b = android.graphics.Color.blue(color)
                
                var shouldMakeTransparent = false
                for (j in transparentColors.indices) {
                    val targetColor = transparentColors[j]
                    val tr = android.graphics.Color.red(targetColor)
                    val tg = android.graphics.Color.green(targetColor)
                    val tb = android.graphics.Color.blue(targetColor)
                    
                    // Fast Euclidean squared distance comparison
                    val distSq = (r - tr) * (r - tr) + (g - tg) * (g - tg) + (b - tb) * (b - tb)
                    if (distSq <= thresholdsSq[j]) {
                        shouldMakeTransparent = true
                        break
                    }
                }
                if (shouldMakeTransparent) {
                    pixels[i] = 0 // Transparent (0x00000000) valid for premultiplied
                }
            }
            mutableBitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        }
        
        // Ensure the bitmap is marked as having alpha, otherwise software canvas will ignore transparency!
        mutableBitmap.setHasAlpha(true)
        
        return mutableBitmap
    }
}

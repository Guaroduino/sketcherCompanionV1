package com.sketcher.sketchercompanionv1.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.LruCache
import com.caverock.androidsvg.SVG
import com.sketcher.sketchercompanionv1.dto.FillStyle
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object SvgPatternCache {
    private val cache = LruCache<String, Bitmap>(16) // Max 16 SVG patterns in memory

    fun getOrCreate(style: FillStyle.SvgPattern): Bitmap? {
        val cacheKey = "${style.svgContent.hashCode()}_${style.scaleX}_${style.scaleY}_${style.rotation}"
        var bitmap = cache.get(cacheKey)
        if (bitmap != null) return bitmap

        try {
            val svg = SVG.getFromString(style.svgContent)
            val docWidth = svg.documentWidth.takeIf { it > 0f } ?: 128f
            val docHeight = svg.documentHeight.takeIf { it > 0f } ?: 128f

            // Create a base bitmap for the repeating pattern tile
            val width = docWidth.toInt().coerceAtLeast(16).coerceAtMost(512)
            val height = docHeight.toInt().coerceAtLeast(16).coerceAtMost(512)

            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.TRANSPARENT)
            svg.renderToCanvas(canvas)

            cache.put(cacheKey, bitmap)
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun clear() {
        cache.evictAll()
    }
}

object MathTextureCache {
    private val cache = LruCache<String, Bitmap>(32) // Max 32 procedural textures in memory

    fun getOrCreate(style: FillStyle.MathTexture): Bitmap? {
        if (style.patternName.uppercase() in listOf("NOTEBOOK", "MATH_GRID", "CALLIGRAPHY")) {
            return null
        }
        val cacheKey = "${style.patternName}_${style.primaryColor}_${style.secondaryColor}_${style.spacing}_${style.thickness}_${style.angle}"
        var bitmap = cache.get(cacheKey)
        if (bitmap != null) return bitmap

        val spacing = style.spacing.coerceAtLeast(4f).coerceAtMost(500f)
        val thickness = style.thickness.coerceAtLeast(0.5f).coerceAtMost(spacing)

        try {
            bitmap = when (style.patternName.uppercase()) {
                "GRID" -> createGridTile(spacing, thickness, style.primaryColor, style.secondaryColor)
                "CHECKERBOARD" -> createCheckerboardTile(spacing, style.primaryColor, style.secondaryColor)
                "STRIPES" -> createStripesTile(spacing, thickness, style.primaryColor, style.secondaryColor)
                "DOTS" -> createDotsTile(spacing, thickness, style.primaryColor, style.secondaryColor)
                "NOISE" -> createNoiseTile(spacing, thickness, style.primaryColor, style.secondaryColor)
                "SCRATCHES" -> createScratchesTile(spacing, thickness, style.primaryColor, style.secondaryColor)
                else -> createGridTile(spacing, thickness, style.primaryColor, style.secondaryColor)
            }
            if (bitmap != null) {
                cache.put(cacheKey, bitmap)
            }
            return bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun createGridTile(spacing: Float, thickness: Float, primaryColor: Int, secondaryColor: Int): Bitmap {
        val size = spacing.toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(secondaryColor)

        val paint = Paint().apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = thickness
        }

        // Draw left and top border lines to form a grid when repeated
        canvas.drawLine(0f, 0f, 0f, size.toFloat(), paint)
        canvas.drawLine(0f, 0f, size.toFloat(), 0f, paint)
        return bmp
    }

    private fun createCheckerboardTile(spacing: Float, primaryColor: Int, secondaryColor: Int): Bitmap {
        val size = (spacing * 2).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(secondaryColor)

        val paint = Paint().apply {
            color = primaryColor
            style = Paint.Style.FILL
        }

        val half = spacing
        // Draw two alternate squares
        canvas.drawRect(0f, 0f, half, half, paint)
        canvas.drawRect(half, half, half * 2, half * 2, paint)
        return bmp
    }

    private fun createStripesTile(spacing: Float, thickness: Float, primaryColor: Int, secondaryColor: Int): Bitmap {
        val size = spacing.toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(secondaryColor)

        val paint = Paint().apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = thickness
            isAntiAlias = true
        }

        val s = size.toFloat()
        // Draw primary diagonal line from top-right to bottom-left (seamless repeat lines)
        canvas.drawLine(0f, s, s, 0f, paint)
        canvas.drawLine(-s/2f, s/2f, s/2f, -s/2f, paint)
        canvas.drawLine(s/2f, s * 1.5f, s * 1.5f, s/2f, paint)
        return bmp
    }

    private fun createDotsTile(spacing: Float, thickness: Float, primaryColor: Int, secondaryColor: Int): Bitmap {
        val size = spacing.toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(secondaryColor)

        val paint = Paint().apply {
            color = primaryColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        val center = size / 2f
        canvas.drawCircle(center, center, thickness, paint)
        return bmp
    }

    private fun createNoiseTile(spacing: Float, thickness: Float, primaryColor: Int, secondaryColor: Int): Bitmap {
        val size = spacing.toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(secondaryColor)
        
        val paint = Paint().apply {
            color = primaryColor
            style = Paint.Style.FILL
        }
        
        val seed = primaryColor.toLong() xor secondaryColor.toLong() xor spacing.toBits().toLong() xor thickness.toBits().toLong()
        val rng = java.util.Random(seed)
        
        val density = 0.3f
        val r = thickness.coerceAtLeast(1f)
        val areaPerDot = Math.PI * r * r
        val totalArea = size * size
        val numDots = (totalArea * density / areaPerDot).toInt().coerceAtLeast(1)
        
        for (i in 0 until numDots) {
            val x = rng.nextFloat() * size
            val y = rng.nextFloat() * size
            canvas.drawCircle(x, y, r, paint)
            canvas.drawCircle(x + size, y, r, paint)
            canvas.drawCircle(x - size, y, r, paint)
            canvas.drawCircle(x, y + size, r, paint)
            canvas.drawCircle(x, y - size, r, paint)
            canvas.drawCircle(x + size, y + size, r, paint)
            canvas.drawCircle(x - size, y - size, r, paint)
            canvas.drawCircle(x + size, y - size, r, paint)
            canvas.drawCircle(x - size, y + size, r, paint)
        }
        return bmp
    }

    private fun createScratchesTile(spacing: Float, thickness: Float, primaryColor: Int, secondaryColor: Int): Bitmap {
        val size = spacing.toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(secondaryColor)
        
        val paint = Paint().apply {
            color = primaryColor
            style = Paint.Style.STROKE
            strokeWidth = thickness
            isAntiAlias = true
        }
        
        val seed = primaryColor.toLong() xor secondaryColor.toLong() xor spacing.toBits().toLong() xor thickness.toBits().toLong()
        val rng = java.util.Random(seed)
        
        val numScratches = (size / 10).coerceAtLeast(2)
        for (i in 0 until numScratches) {
            val startX = rng.nextFloat() * size
            val startY = rng.nextFloat() * size
            val length = rng.nextFloat() * size * 0.8f + size * 0.2f
            val angle = rng.nextFloat() * Math.PI.toFloat() * 2f
            val endX = startX + Math.cos(angle.toDouble()).toFloat() * length
            val endY = startY + Math.sin(angle.toDouble()).toFloat() * length
            
            for (dx in -1..1) {
                for (dy in -1..1) {
                    canvas.drawLine(startX + dx * size, startY + dy * size, endX + dx * size, endY + dy * size, paint)
                }
            }
        }
        return bmp
    }

    fun clear() {
        cache.evictAll()
    }
}

object ImageTextureCache {
    private val cache = LruCache<String, Bitmap>(8) // Max 8 large image textures in memory
    
    private var localTexturesDir: File? = null
    private var localLibraryAssetsDir: File? = null

    fun init(context: Context) {
        if (localTexturesDir == null) {
            localTexturesDir = File(context.filesDir, "textures")
            val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
            localLibraryAssetsDir = File(context.filesDir, "users/$currentUid/library_assets")
        }
    }

    fun getOrCreate(imagePath: String): Bitmap? {
        var bitmap = cache.get(imagePath)
        if (bitmap != null) return bitmap

        try {
            var file = File(imagePath)
            
            // If the path isn't an absolute path that exists, try resolving it as a relative filename
            if (!file.exists() && !file.isAbsolute) {
                val fallbackTex = localTexturesDir?.let { File(it, file.name) }
                val fallbackLib = localLibraryAssetsDir?.let { File(it, file.name) }
                
                if (fallbackTex?.exists() == true) {
                    file = fallbackTex
                } else if (fallbackLib?.exists() == true) {
                    file = fallbackLib
                }
            }

            if (file.exists()) {
                val opts = BitmapFactory.Options().apply {
                    // Limit texture dimensions to 1024x1024 to save memory and avoid GL crashes
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeFile(file.absolutePath, opts)
                var scale = 1
                while (opts.outWidth / scale > 1024 || opts.outHeight / scale > 1024) {
                    scale *= 2
                }

                val decodeOpts = BitmapFactory.Options().apply {
                    inSampleSize = scale
                }
                bitmap = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
                if (bitmap != null) {
                    cache.put(imagePath, bitmap) // Cache by the requested key
                }
                return bitmap
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Copies an imported texture image to the app's local directory for persistence.
     * Returns the absolute path of the local copy.
     */
    fun saveTextureLocally(inputStream: InputStream, context: Context): String? {
        return try {
            val dir = File(context.filesDir, "textures")
            if (!dir.exists()) dir.mkdirs()

            val fileName = "tex_${UUID.randomUUID()}.png"
            val destFile = File(dir, fileName)

            FileOutputStream(destFile).use { out ->
                inputStream.copyTo(out)
            }
            destFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun copyDefaultTexturesFromAssets(context: Context) {
        try {
            val assetManager = context.assets
            val textures = assetManager.list("textures") ?: return
            val destDir = File(context.filesDir, "textures/default")
            if (!destDir.exists()) {
                destDir.mkdirs()
            }
            for (fileName in textures) {
                val destFile = File(destDir, fileName)
                if (!destFile.exists() || destFile.length() == 0L) {
                    assetManager.open("textures/$fileName").use { input ->
                        FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clear() {
        cache.evictAll()
    }
}

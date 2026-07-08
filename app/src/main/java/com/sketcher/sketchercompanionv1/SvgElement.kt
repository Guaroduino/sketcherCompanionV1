package com.sketcher.sketchercompanionv1

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.RectF
import com.caverock.androidsvg.SVG

data class SvgElement(
    val id: String,
    val svgFileName: String,
    val svgContent: String, // Raw XML content
    val matrixValues: FloatArray = FloatArray(9).apply { Matrix().getValues(this) },
    override var isScaleLocked: Boolean = false
) : LayerElement {

    @Transient
    private var _svg: SVG? = null

    // Helper to get or parse the SVG object
    fun getSvg(): SVG? {
        if (_svg == null && svgContent.isNotEmpty()) {
            try {
                _svg = SVG.getFromString(svgContent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return _svg
    }
    
    // Helper to get Matrix object from values
    fun getMatrix(): Matrix {
        val m = Matrix()
        m.setValues(matrixValues)
        return m
    }

    override fun getBoundingBox(library: Map<String, ComponentDefinition>): RectF {
        val svg = getSvg() ?: return RectF()
        
        // Document dimensions
        var w = svg.documentWidth
        var h = svg.documentHeight
        
        // Fallback if dimensions are missing or 0
        if (w <= 0) w = 100f // Arbitrary fallback if still 0
        if (h <= 0) h = 100f

        val rect = RectF(0f, 0f, w, h)
        getMatrix().mapRect(rect)
        return rect
    }

    override fun transform(matrix: Matrix) {
        val current = getMatrix()
        current.postConcat(matrix)
        current.getValues(matrixValues)
    }

    override fun copyElement(): LayerElement {
        return SvgElement(
            id = java.util.UUID.randomUUID().toString(), // New ID for copy
            svgFileName = svgFileName,
            svgContent = svgContent,
            matrixValues = matrixValues.clone(),
            isScaleLocked = isScaleLocked
        )
    }
    
    fun render(canvas: Canvas) {
        val svg = getSvg() ?: return
        canvas.save()
        canvas.concat(getMatrix())
        svg.renderToCanvas(canvas)
        canvas.restore()
    }
}


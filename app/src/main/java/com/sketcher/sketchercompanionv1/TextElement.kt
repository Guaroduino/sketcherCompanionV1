package com.sketcher.sketchercompanionv1

import android.graphics.Matrix
import android.graphics.RectF
import android.text.Html
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint

data class TextElement(
    val id: String,
    var textHtml: String,               // HTML-formatted rich text content
    var width: Float,                   // Text wrapping width
    val matrixValues: FloatArray = FloatArray(9).apply { Matrix().getValues(this) },
    var defaultTextColor: Int = android.graphics.Color.BLACK,
    var defaultTextSize: Float = 16f,   // Base text size
    var fontFamilyName: String = "sans-serif",
    var alignment: String = "LEFT",     // "LEFT", "CENTER", "RIGHT"
    var styleTemplateName: String? = null, // "TITLE", "SUBTITLE", "BODY", "CODE"
    override var isScaleLocked: Boolean = false
) : LayerElement {

    fun getMatrix(): Matrix {
        val m = Matrix()
        m.setValues(matrixValues)
        return m
    }

    override fun getBoundingBox(library: Map<String, ComponentDefinition>): RectF {
        val spanned = Html.fromHtml(textHtml, Html.FROM_HTML_MODE_LEGACY)
        val paint = TextPaint().apply {
            textSize = defaultTextSize
        }
        val layoutAlignment = when (alignment) {
            "CENTER" -> Layout.Alignment.ALIGN_CENTER
            "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
        val textWidth = if (width > 0f) width.toInt() else 1
        
        // Compute wrapped text height using StaticLayout
        val layout = StaticLayout.Builder.obtain(spanned, 0, spanned.length, paint, textWidth)
            .setAlignment(layoutAlignment)
            .setLineSpacing(0f, 1f)
            .setIncludePad(true)
            .build()

        val rect = RectF(0f, 0f, width, layout.height.toFloat())
        getMatrix().mapRect(rect)
        return rect
    }

    override fun transform(matrix: Matrix) {
        val current = getMatrix()
        current.postConcat(matrix)
        current.getValues(matrixValues)
    }

    override fun copyElement(): LayerElement {
        return TextElement(
            id = java.util.UUID.randomUUID().toString(),
            textHtml = textHtml,
            width = width,
            matrixValues = matrixValues.clone(),
            defaultTextColor = defaultTextColor,
            defaultTextSize = defaultTextSize,
            fontFamilyName = fontFamilyName,
            alignment = alignment,
            styleTemplateName = styleTemplateName,
            isScaleLocked = isScaleLocked
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TextElement) return false
        if (id != other.id) return false
        if (textHtml != other.textHtml) return false
        if (width != other.width) return false
        if (!matrixValues.contentEquals(other.matrixValues)) return false
        if (defaultTextColor != other.defaultTextColor) return false
        if (defaultTextSize != other.defaultTextSize) return false
        if (fontFamilyName != other.fontFamilyName) return false
        if (alignment != other.alignment) return false
        if (styleTemplateName != other.styleTemplateName) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + textHtml.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + matrixValues.contentHashCode()
        result = 31 * result + defaultTextColor
        result = 31 * result + defaultTextSize.hashCode()
        result = 31 * result + fontFamilyName.hashCode()
        result = 31 * result + alignment.hashCode()
        result = 31 * result + (styleTemplateName?.hashCode() ?: 0)
        return result
    }
}

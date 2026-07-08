package com.sketcher.sketchercompanionv1.dto

import android.graphics.Color

enum class FillType {
    SOLID,
    SVG_PATTERN,
    MATH_TEXTURE,
    IMAGE_TEXTURE
}

sealed interface FillStyle {
    val type: FillType
    val opacity: Float

    data class Solid(
        val color: Int
    ) : FillStyle {
        override val type = FillType.SOLID
        override val opacity: Float
            get() = Color.alpha(color) / 255f
    }

    data class SvgPattern(
        val svgContent: String,       // XML raw del SVG para portabilidad
        val scaleX: Float = 1.0f,
        val scaleY: Float = 1.0f,
        val rotation: Float = 0.0f,
        val offsetX: Float = 0.0f,
        val offsetY: Float = 0.0f,
        override val opacity: Float = 1.0f
    ) : FillStyle {
        override val type = FillType.SVG_PATTERN
    }

    data class MathTexture(
        val patternName: String,      // "GRID", "CHECKERBOARD", "STRIPES", "DOTS"
        val primaryColor: Int = Color.BLACK,
        val secondaryColor: Int = Color.TRANSPARENT,
        val spacing: Float = 20.0f,    // Frecuencia o tamaño de celda
        val thickness: Float = 2.0f,  // Grosor de línea o tamaño de punto
        val angle: Float = 0.0f,       // Rotación de la textura matemática
        override val opacity: Float = 1.0f
    ) : FillStyle {
        override val type = FillType.MATH_TEXTURE
    }

    data class ImageTexture(
        val imagePath: String,        // Ruta local del archivo copiado internamente
        val scaleX: Float = 1.0f,
        val scaleY: Float = 1.0f,
        val rotation: Float = 0.0f,
        val offsetX: Float = 0.0f,
        val offsetY: Float = 0.0f,
        override val opacity: Float = 1.0f,
        val tintColor: Int = Color.TRANSPARENT,
        val tintMix: Float = 0.0f,
        val blendModeName: String = "SRC_ATOP"
    ) : FillStyle {
        override val type = FillType.IMAGE_TEXTURE
    }
}

fun FillStyle.copyWithOpacity(newOpacity: Float): FillStyle {
    return when (this) {
        is FillStyle.Solid -> {
            val alpha = (newOpacity * 255f).toInt().coerceIn(0, 255)
            FillStyle.Solid((this.color and 0x00FFFFFF) or (alpha shl 24))
        }
        is FillStyle.MathTexture -> this.copy(opacity = newOpacity)
        is FillStyle.SvgPattern -> this.copy(opacity = newOpacity)
        is FillStyle.ImageTexture -> this.copy(opacity = newOpacity)
    }
}

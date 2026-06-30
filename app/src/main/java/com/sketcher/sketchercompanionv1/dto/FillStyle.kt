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

    data class Solid(
        val color: Int
    ) : FillStyle {
        override val type = FillType.SOLID
    }

    data class SvgPattern(
        val svgContent: String,       // XML raw del SVG para portabilidad
        val scaleX: Float = 1.0f,
        val scaleY: Float = 1.0f,
        val rotation: Float = 0.0f,
        val offsetX: Float = 0.0f,
        val offsetY: Float = 0.0f
    ) : FillStyle {
        override val type = FillType.SVG_PATTERN
    }

    data class MathTexture(
        val patternName: String,      // "GRID", "CHECKERBOARD", "STRIPES", "DOTS"
        val primaryColor: Int = Color.BLACK,
        val secondaryColor: Int = Color.TRANSPARENT,
        val spacing: Float = 20.0f,    // Frecuencia o tamaño de celda
        val thickness: Float = 2.0f,  // Grosor de línea o tamaño de punto
        val angle: Float = 0.0f       // Rotación de la textura matemática
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
        val opacity: Float = 1.0f
    ) : FillStyle {
        override val type = FillType.IMAGE_TEXTURE
    }
}

package com.skecher.sketchercompanionv1

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

object RenderHelper {

    fun drawElementRecursive(
        canvas: Canvas, 
        element: LayerElement,
        drawVector: (VectorStroke, Canvas) -> Unit,
        drawInk: (AndroidInkElement, Canvas) -> Unit,
        drawFill: (FillData, Canvas) -> Unit,
        drawImage: (ImageElement, Canvas) -> Unit,
        drawSvg: (SvgElement, Canvas) -> Unit,
        componentLibrary: Map<String, ComponentDefinition>,
        isDimmed: Boolean = false
    ) {
        if (isDimmed) {
            val paint = Paint().apply { alpha = 80 } // ~30% opacity
            canvas.saveLayer(null, paint)
        }

        when (element) {
            is GroupElement -> {
                canvas.save()
                canvas.concat(element.matrix)
                for (child in element.elements) {
                    drawElementRecursive(canvas, child, drawVector, drawInk, drawFill, drawImage, drawSvg, componentLibrary)
                }
                canvas.restore()
            }
            is ComponentInstance -> {
                val definition = componentLibrary[element.definitionId]
                if (definition != null) {
                    canvas.save()
                    canvas.concat(element.matrix)
                    for (child in definition.elements) {
                        drawElementRecursive(canvas, child, drawVector, drawInk, drawFill, drawImage, drawSvg, componentLibrary)
                    }
                    canvas.restore()
                }
            }
            is VectorStroke -> drawVector(element, canvas)
            is AndroidInkElement -> drawInk(element, canvas)
            is FillData -> drawFill(element, canvas)
            is ImageElement -> drawImage(element, canvas)
            is SvgElement -> drawSvg(element, canvas)
            else -> {} // Should not happen with sealed interface but satisfies compiler
        }

        if (isDimmed) {
            canvas.restore()
        }
    }
}

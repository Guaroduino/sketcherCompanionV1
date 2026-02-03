package com.skecher.sketchercompanionv1.ui.utils

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import androidx.ink.strokes.Stroke
import com.skecher.sketchercompanionv1.AndroidInkElement
import com.skecher.sketchercompanionv1.FillData
import com.skecher.sketchercompanionv1.ImageElement
import com.skecher.sketchercompanionv1.LayerElement
import com.skecher.sketchercompanionv1.SvgElement
import com.skecher.sketchercompanionv1.VectorStroke
import com.skecher.sketchercompanionv1.GroupElement

object RenderHelper {

    fun drawElementRecursive(
        canvas: Canvas, 
        element: LayerElement,
        drawVector: (VectorStroke, Canvas) -> Unit,
        drawInk: (AndroidInkElement, Canvas) -> Unit,
        drawFill: (FillData, Canvas) -> Unit,
        drawImage: (ImageElement, Canvas) -> Unit,
        drawSvg: (SvgElement, Canvas) -> Unit
    ) {
        when (element) {
            is GroupElement -> {
                canvas.save()
                canvas.concat(element.matrix)
                for (child in element.elements) {
                    drawElementRecursive(canvas, child, drawVector, drawInk, drawFill, drawImage, drawSvg)
                }
                canvas.restore()
            }
            is VectorStroke -> drawVector(element, canvas)
            is AndroidInkElement -> drawInk(element, canvas)
            is FillData -> drawFill(element, canvas)
            is ImageElement -> drawImage(element, canvas)
            is SvgElement -> drawSvg(element, canvas)
        }
    }
}

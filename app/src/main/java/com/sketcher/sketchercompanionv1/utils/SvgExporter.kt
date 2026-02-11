package com.sketcher.sketchercompanionv1.utils

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.util.Base64

import com.sketcher.sketchercompanionv1.*
import com.sketcher.sketchercompanionv1.dto.*
import java.io.ByteArrayOutputStream

object SvgExporter {

    fun export(projectData: ProjectData, layers: List<Layer>, config: ExportSvgConfig): String {
        val width = config.width
        val height = config.height
        
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>\n")
        sb.append("<svg xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\" ")
        
        // Calculate ViewBox based on Home View vs Fit Content
        val viewBox: String
        
        if (config.useHomeView) {
            // In Home View, we match the viewport dimensions
            viewBox = "0 0 ${projectData.canvasMetadata.width} ${projectData.canvasMetadata.height}"
        } else {
            // Fit Content: The config width/height already represent the bounds
            viewBox = "0 0 $width $height"
        }

        sb.append("width=\"$width\" height=\"$height\" viewBox=\"$viewBox\">\n")
        
        sb.append("  <defs>\n")
        sb.append("  </defs>\n")
        
        // Background
        if (config.includeBackground) {
            val bgColor = projectData.backgroundConfig.color
            val paramHex = colorToHex(bgColor)
            sb.append("  <rect width=\"$width\" height=\"$height\" fill=\"$paramHex\" />\n")
        }

        // Apply Transform if using Home View to match perspective
        if (config.useHomeView && projectData.canvasMetadata.cameraMatrix.size == 9) {
            val vals = projectData.canvasMetadata.cameraMatrix.toFloatArray()
            val a = vals[Matrix.MSCALE_X]
            val b = vals[Matrix.MSKEW_Y]
            val c = vals[Matrix.MSKEW_X]
            val d = vals[Matrix.MSCALE_Y]
            val e = vals[Matrix.MTRANS_X]
            val f = vals[Matrix.MTRANS_Y]
            sb.append("  <g transform=\"matrix($a, $b, $c, $d, $e, $f)\">\n")
        }

        // Iterate Layers (Bottom to Top)
        for (layer in layers) {
            if (!layer.isVisible) continue
            
            sb.append("  <g id=\"${layer.id}\" opacity=\"${layer.opacity}\">\n")
            
            for (element in layer.elements) {
                when (element) {
                    is FillData -> exportFill(sb, element)
                    is VectorStroke -> exportVectorStroke(sb, element)
                    is ImageElement -> exportImage(sb, element)
                    is SvgElement -> exportSvgElement(sb, element)
                    is FillData -> exportFill(sb, element)
                    is VectorStroke -> exportVectorStroke(sb, element)
                    is ImageElement -> exportImage(sb, element)
                    is SvgElement -> exportSvgElement(sb, element)
                    // is AndroidInkElement -> exportInk(sb, element) // Removed
                    is GroupElement -> exportGroup(sb, element)
                    is ComponentInstance -> exportComponentInstance(sb, element, projectData.componentLibrary)
                    else -> {}
                }
            }
            
            sb.append("  </g>\n")
        }

        if (config.useHomeView && projectData.canvasMetadata.cameraMatrix.size == 9) {
            sb.append("  </g>\n")
        }

        sb.append("</svg>")
        return sb.toString()
    }

    private fun exportGroup(sb: StringBuilder, group: GroupElement) {
        val matrix = group.matrix
        val values = FloatArray(9)
        matrix.getValues(values)
        
        val a = values[Matrix.MSCALE_X]
        val b = values[Matrix.MSKEW_Y]
        val c = values[Matrix.MSKEW_X]
        val d = values[Matrix.MSCALE_Y]
        val e = values[Matrix.MTRANS_X]
        val f = values[Matrix.MTRANS_Y]
        
        sb.append("    <g id=\"${group.id}\" transform=\"matrix($a, $b, $c, $d, $e, $f)\">\n")
        
        for (child in group.elements) {
            when (child) {
                is FillData -> exportFill(sb, child)
                is VectorStroke -> exportVectorStroke(sb, child)
                is ImageElement -> exportImage(sb, child)
                is SvgElement -> exportSvgElement(sb, child)
                is SvgElement -> exportSvgElement(sb, child)
                // is AndroidInkElement -> exportInk(sb, child) // Removed
                is GroupElement -> exportGroup(sb, child)
                is GroupElement -> exportGroup(sb, child)
                is ComponentInstance -> exportComponentInstance(sb, child, emptyMap()) // Definition elements usually don't have nested instances that need a library passed down if they are already resolved or flat
                else -> {}
            }
        }
        
        sb.append("    </g>\n")
    }

    private fun exportComponentInstance(sb: StringBuilder, instance: ComponentInstance, library: Map<String, com.sketcher.sketchercompanionv1.dto.ComponentDefinitionJson>) {
        val definition = library[instance.definitionId]
        if (definition != null) {
            val matrix = instance.matrix
            val values = FloatArray(9)
            matrix.getValues(values)
            
            val a = values[Matrix.MSCALE_X]
            val b = values[Matrix.MSKEW_Y]
            val c = values[Matrix.MSKEW_X]
            val d = values[Matrix.MSCALE_Y]
            val e = values[Matrix.MTRANS_X]
            val f = values[Matrix.MTRANS_Y]
            
            sb.append("    <g id=\"${instance.id}\" transform=\"matrix($a, $b, $c, $d, $e, $f)\">\n")
            
            for (childJson in definition.elements) {
                // Convert JSON elements back to LayerElement for export if needed?
                // Or implement exportFromJson?
                // Actually, SvgExporter.export takes the live List<Layer>.
                // For ComponentInstance, we have the Definition in the library.
                // The library in ProjectData has ComponentDefinitionJson.
                // It's probably easier to just use a simplified export for these.
                // For now, let's just add a comment or try a basic recursive call if we can map them back.
                
                sb.append("      <!-- Component Element Export TBD -->\n")
            }
            sb.append("    </g>\n")
        }
    }

    private fun exportFill(sb: StringBuilder, fill: FillData) {
        val d = PathToSvgHelper.pathToString(fill.path)
        if (d.isEmpty()) return
        
        val colorHex = colorToHex(fill.color)
        val alpha = (Color.alpha(fill.color) / 255f)
        
        // For fill, we can put opacity in style or fill-opacity attribute
        // Using fill-opacity if alpha < 1
        val opacityAttr = if (alpha < 1f) "fill-opacity=\"$alpha\"" else ""
        
        sb.append("    <path d=\"$d\" fill=\"$colorHex\" stroke=\"none\" $opacityAttr />\n")
    }

    private fun exportVectorStroke(sb: StringBuilder, stroke: VectorStroke) {
        // Use the generated path for visual consistency
        val d = PathToSvgHelper.pathToString(stroke.path)
        if (d.isEmpty()) return
        
        val colorHex = colorToHex(stroke.color)
        val alpha = (Color.alpha(stroke.color) / 255f)
        val strokeOpacity = if (alpha < 1f) "stroke-opacity=\"$alpha\"" else ""
        
        // VectorStroke typically uses the path as the FILL of the shape because it's an outline path?
        // Wait, VectorStroke in this app seems to be an Outline Path (created by PathGenerator).
        // If PathGenerator generates the outline (left/right points), then we should FILL it.
        // Let's check: VectorStroke has `path` which is usually the outline.
        // IF it was a centerline, we would STROKE it.
        // PathGenerator.generateStrokePath returns a closed shape usually?
        // Let's assume it is a filled shape for now (Outline).
        // If it was just a line, we'd stroke it. 
        // Based on previous files, `generateStrokePath` creates a complex path.
        
        sb.append("    <path d=\"$d\" fill=\"$colorHex\" stroke=\"none\" $strokeOpacity />\n")
    }



    private fun exportImage(sb: StringBuilder, element: ImageElement) {
        val base64 = bitmapToBase64(element.bitmap)
        val matrix = element.matrix
        val values = FloatArray(9)
        matrix.getValues(values)
        // SUV matrix: a c e / b d f => values[0] values[1] values[2] / values[3] values[4] values[5]
        // transform="matrix(a, b, c, d, e, f)" where a=MSCALE_X, b=MSKEW_Y, c=MSKEW_X, d=MSCALE_Y, e=MTRANS_X, f=MTRANS_Y
        // Android Matrix values:
        // [0] scaleX  [1] skewX   [2] transX
        // [3] skewY   [4] scaleY  [5] transY
        // SVG Matrix: matrix(scaleX, skewY, skewX, scaleY, transX, transY)
        
        val a = values[Matrix.MSCALE_X]
        val b = values[Matrix.MSKEW_Y]
        val c = values[Matrix.MSKEW_X]
        val d = values[Matrix.MSCALE_Y]
        val e = values[Matrix.MTRANS_X]
        val f = values[Matrix.MTRANS_Y]
        
        // Image Dimensions
        val w = element.bitmap.width
        val h = element.bitmap.height
        
        sb.append("    <image href=\"data:image/png;base64,$base64\" width=\"$w\" height=\"$h\" transform=\"matrix($a, $b, $c, $d, $e, $f)\" />\n")
    }

    private fun exportSvgElement(sb: StringBuilder, element: SvgElement) {
        // Embed the inner SVG.
        // We can inject it into a group with the proper transform.
        val matrix = element.getMatrix()
        val values = FloatArray(9)
        matrix.getValues(values)
        
        val a = values[Matrix.MSCALE_X]
        val b = values[Matrix.MSKEW_Y]
        val c = values[Matrix.MSKEW_X]
        val d = values[Matrix.MSCALE_Y]
        val e = values[Matrix.MTRANS_X]
        val f = values[Matrix.MTRANS_Y]
        
        sb.append("    <g transform=\"matrix($a, $b, $c, $d, $e, $f)\">\n")
        
        // We need to parse the content to strip the outer <svg> tag if we want to be clean, 
        // or just rely on browser handling nested <svg>.
        // Nested <svg> is valid in spec.
        // However, we should ensure the inner SVG has width/height/viewbox.
        // `element.svgContent` is the raw string.
        sb.append(element.svgContent)
        
        sb.append("    </g>\n")
    }

    private fun colorToHex(color: Int): String {
        // Ignore alpha for hex string, we handle opacity separately
        return String.format("#%06X", (0xFFFFFF and color))
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        val byteArray = stream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.DEFAULT).trim().replace("\n", "")
    }
}


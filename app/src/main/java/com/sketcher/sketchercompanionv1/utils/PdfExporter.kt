package com.sketcher.sketchercompanionv1.utils

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.RectF
import android.net.Uri
import com.sketcher.sketchercompanionv1.dto.CanvasSizeConfig
import com.sketcher.sketchercompanionv1.dto.ProjectData
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.RenderHelper
import java.io.FileOutputStream

/**
 * Utility for exporting canvas content to PDF
 */
object PdfExporter {

    enum class BoundsMode {
        CANVAS_SIZE,    // Use configured canvas size
        ZOOM_EXTENDS,   // Fit all content
        HOME_VIEW       // Use saved home camera view
    }

    data class PdfExportConfig(
        val boundsMode: BoundsMode,
        val includeBackground: Boolean = true,
        val dpi: Int = 300
    )

    /**
     * Export layers to PDF file
     */
    fun export(
        context: Context,
        uri: Uri,
        layers: List<Layer>,
        projectData: ProjectData,
        config: PdfExportConfig,
        componentLibrary: Map<String, ComponentDefinition>,
        canvasSizeConfig: CanvasSizeConfig?
    ): Boolean {
        return try {
            // Calculate bounds based on mode
            val bounds = calculateBounds(
                config.boundsMode,
                layers,
                projectData,
                canvasSizeConfig,
                componentLibrary
            )

            // Create PDF document
            val pdfDocument = PdfDocument()
            
            // Create page with calculated dimensions
            val pageInfo = PdfDocument.PageInfo.Builder(
                bounds.width.toInt(),
                bounds.height.toInt(),
                1
            ).create()
            
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            // Draw background if enabled
            if (config.includeBackground) {
                val bgPaint = Paint().apply {
                    color = projectData.backgroundConfig.color
                    style = Paint.Style.FILL
                }
                canvas.drawRect(0f, 0f, bounds.width, bounds.height, bgPaint)
            }

            // Apply transformation matrix to fit content
            canvas.save()
            canvas.concat(bounds.transform)

            // Render all layers
            for (layer in layers) {
                if (!layer.isVisible) continue
                
                val layerAlpha = if (layer.opacity < 1f) (layer.opacity * 255).toInt() else 255
                val saveCount = if (layerAlpha < 255) {
                    canvas.saveLayerAlpha(0f, 0f, bounds.width, bounds.height, layerAlpha)
                } else {
                    canvas.save()
                }

                for (element in layer.elements) {
                    RenderHelper.drawElementRecursive(
                        canvas,
                        element,
                        componentLibrary = componentLibrary,
                        isDimmed = false
                    )
                }

                canvas.restoreToCount(saveCount)
            }

            canvas.restore()

            // Finish page
            pdfDocument.finishPage(page)

            // Write to file
            context.contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            }

            pdfDocument.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private data class Bounds(
        val width: Float,
        val height: Float,
        val transform: Matrix
    )

    private fun calculateBounds(
        mode: BoundsMode,
        layers: List<Layer>,
        projectData: ProjectData,
        canvasSizeConfig: CanvasSizeConfig?,
        componentLibrary: Map<String, ComponentDefinition>
    ): Bounds {
        return when (mode) {
            BoundsMode.CANVAS_SIZE -> {
                // Use configured canvas size
                if (canvasSizeConfig != null) {
                    Bounds(
                        width = canvasSizeConfig.widthInPixels,
                        height = canvasSizeConfig.heightInPixels,
                        transform = Matrix() // Identity - content is already in canvas coordinates
                    )
                } else {
                    // Fallback to zoom extends if no canvas size
                    calculateZoomExtendsBounds(layers, componentLibrary)
                }
            }
            BoundsMode.ZOOM_EXTENDS -> {
                calculateZoomExtendsBounds(layers, componentLibrary)
            }
            BoundsMode.HOME_VIEW -> {
                calculateHomeViewBounds(projectData)
            }
        }
    }

    private fun calculateZoomExtendsBounds(
        layers: List<Layer>,
        componentLibrary: Map<String, ComponentDefinition>
    ): Bounds {
        // Calculate bounding box of all elements
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE

        for (layer in layers) {
            if (!layer.isVisible) continue
            for (element in layer.elements) {
                val bounds = element.getBoundingBox(componentLibrary)
                minX = minOf(minX, bounds.left)
                minY = minOf(minY, bounds.top)
                maxX = maxOf(maxX, bounds.right)
                maxY = maxOf(maxY, bounds.bottom)
            }
        }

        // Add padding
        val padding = 50f
        minX -= padding
        minY -= padding
        maxX += padding
        maxY += padding

        val width = maxX - minX
        val height = maxY - minY

        // Create transform to shift content to origin
        val transform = Matrix().apply {
            setTranslate(-minX, -minY)
        }

        return Bounds(width, height, transform)
    }

    private fun calculateHomeViewBounds(projectData: ProjectData): Bounds {
        // Use saved home camera view
        val width = projectData.canvasMetadata.width.toFloat()
        val height = projectData.canvasMetadata.height.toFloat()

        // Apply home camera matrix
        val cameraMatrix = Matrix()
        if (projectData.canvasMetadata.cameraMatrix.size == 9) {
            cameraMatrix.setValues(projectData.canvasMetadata.cameraMatrix.toFloatArray())
        }

        return Bounds(width, height, cameraMatrix)
    }
}


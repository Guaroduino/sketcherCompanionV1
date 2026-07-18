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
import com.sketcher.sketchercompanionv1.dto.FillStyle
import com.sketcher.sketchercompanionv1.dto.BackgroundConfig
import com.sketcher.sketchercompanionv1.dto.CanvasMetadata
import com.sketcher.sketchercompanionv1.CanvasPage
import com.sketcher.sketchercompanionv1.RenderEngine
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
     * Export multiple pages to a single multi-page PDF file
     */
    fun exportPages(
        context: Context,
        uri: Uri,
        pages: List<CanvasPage>,
        infiniteCanvasBoundsMode: BoundsMode,
        componentLibrary: Map<String, ComponentDefinition>
    ): Boolean {
        return try {
            val pdfDocument = PdfDocument()

            for ((index, page) in pages.withIndex()) {
                val mode = if (page.canvasSizeConfig != null) {
                    BoundsMode.CANVAS_SIZE
                } else {
                    infiniteCanvasBoundsMode
                }

                // Temporary ProjectData for bounds calculation
                val width = page.canvasSizeConfig?.widthInPixels ?: 2480f
                val height = page.canvasSizeConfig?.heightInPixels ?: 3508f
                val tempProjectData = ProjectData(
                    id = page.id,
                    layers = page.layers.map { it.toLayerJson() },
                    backgroundConfig = BackgroundConfig(
                        color = page.backgroundColor,
                        gridConfig = page.gridConfig,
                        fillStyle = page.backgroundStyle.toFillStyleJson()
                    ),
                    paletteColors = emptyList(),
                    toolConfigs = emptyMap(),
                    canvasMetadata = CanvasMetadata(
                        width = width,
                        height = height,
                        cameraMatrix = page.cameraMatrixValues.toList(),
                        scaleConfig = page.scaleConfig
                    ),
                    componentLibrary = componentLibrary.mapValues { it.value.toComponentDefinitionJson() },
                    workspaceProfile = null
                )

                val bounds = calculateBounds(
                    mode,
                    page.layers,
                    tempProjectData,
                    page.canvasSizeConfig,
                    componentLibrary
                )

                val pageInfo = PdfDocument.PageInfo.Builder(
                    bounds.width.toInt(),
                    bounds.height.toInt(),
                    index + 1
                ).create()

                val pdfPage = pdfDocument.startPage(pageInfo)
                val canvas = pdfPage.canvas

                // Draw background
                val renderEngine = RenderEngine()
                val bgStyle = page.backgroundStyle
                val canvasSizeConfig = page.canvasSizeConfig
                val pixelsPerMm = if (canvasSizeConfig != null) {
                    val preset = canvasSizeConfig.preset
                    if (preset != null) {
                        val widthMm = if (canvasSizeConfig.orientation == com.sketcher.sketchercompanionv1.dto.PaperOrientation.PORTRAIT) {
                            preset.widthMm
                        } else {
                            preset.heightMm
                        }
                        canvasSizeConfig.widthInPixels / widthMm
                    } else {
                        canvasSizeConfig.widthInPixels / 215.9f
                    }
                } else {
                    5.0f
                }
                renderEngine.drawPaperBackground(canvas, 0f, 0f, bounds.width, bounds.height, bgStyle, pixelsPerMm)

                // Apply transform
                canvas.save()
                canvas.concat(bounds.transform)

                // Render layers
                for (layer in page.layers) {
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
                pdfDocument.finishPage(pdfPage)
            }

            // Write to file
            context.contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                FileOutputStream(descriptor.fileDescriptor).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            }

            pdfDocument.close()
            true
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            false
        }
    }

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
                val renderEngine = RenderEngine()
                val bgStyle = projectData.backgroundConfig.fillStyle.toFillStyle(projectData.backgroundConfig.color)
                val canvasSizeConfig = projectData.canvasSizeConfig
                val pixelsPerMm = if (canvasSizeConfig != null) {
                    val preset = canvasSizeConfig.preset
                    if (preset != null) {
                        val widthMm = if (canvasSizeConfig.orientation == com.sketcher.sketchercompanionv1.dto.PaperOrientation.PORTRAIT) {
                            preset.widthMm
                        } else {
                            preset.heightMm
                        }
                        canvasSizeConfig.widthInPixels / widthMm
                    } else {
                        canvasSizeConfig.widthInPixels / 215.9f
                    }
                } else {
                    5.0f
                }
                renderEngine.drawPaperBackground(canvas, 0f, 0f, bounds.width, bounds.height, bgStyle, pixelsPerMm)
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
            if (uri.scheme == "file") {
                val file = java.io.File(uri.path ?: "")
                FileOutputStream(file).use { outputStream ->
                    pdfDocument.writeTo(outputStream)
                }
            } else {
                context.contentResolver.openFileDescriptor(uri, "w")?.use { descriptor ->
                    FileOutputStream(descriptor.fileDescriptor).use { outputStream ->
                        pdfDocument.writeTo(outputStream)
                    }
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
                    val transform = Matrix()
                    if (canvasSizeConfig.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) {
                        transform.postTranslate(canvasSizeConfig.widthInPixels / 2f, canvasSizeConfig.heightInPixels / 2f)
                    }
                    Bounds(
                        width = canvasSizeConfig.widthInPixels,
                        height = canvasSizeConfig.heightInPixels,
                        transform = transform
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
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE

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


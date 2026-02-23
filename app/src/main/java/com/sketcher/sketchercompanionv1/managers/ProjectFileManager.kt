package com.sketcher.sketchercompanionv1.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.*
import com.sketcher.sketchercompanionv1.exporters.DxfExporter
import com.sketcher.sketchercompanionv1.importers.DxfImporter
import com.sketcher.sketchercompanionv1.importers.DxfImportData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.io.OutputStream

class ProjectFileManager {

    suspend fun saveProject(context: Context, projectData: ProjectData, layers: List<Layer>, uri: Uri) {
        withContext(Dispatchers.IO) {
            ZipStorageManager.saveProject(context, projectData, layers, uri)
        }
    }

    suspend fun loadProject(context: Context, uri: Uri): Triple<ProjectData, Map<String, Bitmap>, Map<String, String>> {
        return withContext(Dispatchers.IO) {
            ZipStorageManager.loadProject(context, uri)
        }
    }

    suspend fun exportSvg(context: Context, uri: Uri, projectData: ProjectData, layers: List<Layer>, config: ExportSvgConfig) {
        withContext(Dispatchers.IO) {
            val svgString = SvgExporter.export(projectData, layers, config)
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(svgString.toByteArray())
            }
        }
    }

    suspend fun generateSvgContent(projectData: ProjectData, layers: List<Layer>, config: ExportSvgConfig): String {
        return withContext(Dispatchers.IO) {
            SvgExporter.export(projectData, layers, config)
        }
    }

    suspend fun exportDxf(layers: List<Layer>, outputStream: OutputStream) {
        withContext(Dispatchers.IO) {
            DxfExporter.export(layers, outputStream)
        }
    }

    suspend fun exportPdf(
        context: Context,
        uri: Uri,
        layers: List<Layer>,
        projectData: ProjectData,
        config: PdfExporter.PdfExportConfig,
        componentLibrary: Map<String, ComponentDefinition>,
        canvasSizeConfig: CanvasSizeConfig?
    ) {
        withContext(Dispatchers.IO) {
            PdfExporter.export(
                context = context,
                uri = uri,
                layers = layers,
                projectData = projectData,
                config = config,
                componentLibrary = componentLibrary,
                canvasSizeConfig = canvasSizeConfig
            )
        }
    }

    suspend fun saveTemplate(context: Context, projectData: ProjectData, layers: List<Layer>, name: String) {
        withContext(Dispatchers.IO) {
            TemplateManager.saveAsTemplate(context, projectData, layers, name)
        }
    }

    suspend fun loadTemplate(context: Context, file: File): Triple<ProjectData, Map<String, Bitmap>, Map<String, String>> {
        return withContext(Dispatchers.IO) {
            TemplateManager.loadTemplate(context, file)
        }
    }

    fun calculateVisibleBounds(layers: List<Layer>, componentLibrary: Map<String, ComponentDefinition>): RectF {
        val totalBounds = RectF()
        var first = true
        
        for (layer in layers) {
            if (!layer.isVisible) continue
            for (element in layer.elements) {
                val bounds = element.getBounds(componentLibrary)
                if (first) {
                    totalBounds.set(bounds)
                    first = false
                } else {
                    totalBounds.union(bounds)
                }
            }
        }
        return totalBounds
    }

    suspend fun loadScaledBitmap(context: Context, uri: Uri): Bitmap? {
        return withContext(Dispatchers.IO) {
            BitmapUtils.loadScaledBitmap(context, uri)
        }
    }

    suspend fun loadSvgContent(context: Context, uri: Uri): String? {
        return withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().toString(Charsets.UTF_8)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}

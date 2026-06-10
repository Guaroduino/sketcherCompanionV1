package com.sketcher.sketchercompanionv1.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.ImageElement
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.LayerElement
import com.sketcher.sketchercompanionv1.GroupElement
import com.sketcher.sketchercompanionv1.dto.ProjectData
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipStorageManager {

    private const val ENTRY_PROJECT_JSON = "project.json"
    private const val DIR_ASSETS = "assets/"

    private fun collectAssets(
        elements: List<LayerElement>,
        savedFileNames: MutableSet<String>,
        zipOut: ZipOutputStream
    ) {
        elements.forEach { element ->
            when (element) {
                is ImageElement -> {
                    val fileName = element.imageFileName
                    if (fileName.isNotEmpty() && savedFileNames.add(fileName)) {
                        val entryName = "$DIR_ASSETS$fileName"
                        val imageEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(imageEntry)
                        element.bitmap.compress(Bitmap.CompressFormat.PNG, 100, zipOut)
                        zipOut.closeEntry()
                    }
                }
                is com.sketcher.sketchercompanionv1.SvgElement -> {
                    val fileName = element.svgFileName
                    if (fileName.isNotEmpty() && savedFileNames.add(fileName)) {
                        val entryName = "$DIR_ASSETS$fileName"
                        val svgEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(svgEntry)
                        zipOut.write(element.svgContent.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                    }
                }
                is GroupElement -> {
                    collectAssets(element.elements, savedFileNames, zipOut)
                }
                else -> { /* Ignore other elements */ }
            }
        }
    }

    /**
     * Saves the project to a .skc (ZIP) file.
     * Guaranteed to use PNG for image assets to preserve transparency.
     */
    fun saveProject(
        context: Context,
        projectData: ProjectData,
        layers: List<Layer>,
        uri: Uri,
        components: Collection<ComponentDefinition> = emptyList()
    ) {
        val contentResolver = context.contentResolver
        
        // Use try-with-resources logic
        contentResolver.openOutputStream(uri)?.use { outputStream ->
            ZipOutputStream(BufferedOutputStream(outputStream)).use { zipOut ->
                
                // 1. Write project.json
                val jsonString = Gson().toJson(projectData)
                val jsonEntry = ZipEntry(ENTRY_PROJECT_JSON)
                zipOut.putNextEntry(jsonEntry)
                zipOut.write(jsonString.toByteArray(Charsets.UTF_8))
                zipOut.closeEntry()

                // 2. Write Assets (Images & SVGs)
                val savedFileNames = mutableSetOf<String>()

                // Collect from layers (recursively)
                layers.forEach { layer ->
                    collectAssets(layer.elements, savedFileNames, zipOut)
                }

                // Collect from component definitions (recursively)
                components.forEach { component ->
                    collectAssets(component.elements, savedFileNames, zipOut)
                }
            }
        }
    }

    /**
     * Loads a project from a .skc (ZIP) file.
     * Returns the ProjectJson DTO, a Map of Filename -> Bitmap, and Map of Filename -> String (SVG).
     */
    fun loadProject(context: Context, uri: Uri): Triple<ProjectData, Map<String, Bitmap>, Map<String, String>> {
        var projectData: ProjectData? = null
        val bitmapMap = mutableMapOf<String, Bitmap>()
        val svgMap = mutableMapOf<String, String>()

        val contentResolver = context.contentResolver
        contentResolver.openInputStream(uri)?.use { inputStream ->
            ZipInputStream(BufferedInputStream(inputStream)).use { zipIn ->
                var entry: ZipEntry? = zipIn.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name == ENTRY_PROJECT_JSON) {
                        // Read JSON
                        val bytes = zipIn.readBytes()
                        val jsonString = String(bytes, Charsets.UTF_8)
                        projectData = Gson().fromJson(jsonString, ProjectData::class.java)
                    } else if (name.startsWith(DIR_ASSETS)) {
                        // Read Asset
                        val cleanName = name.removePrefix(DIR_ASSETS)
                        if (cleanName.isNotEmpty()) {
                            val bytes = zipIn.readBytes()
                            if (bytes.isNotEmpty()) {
                                if (cleanName.endsWith(".svg", ignoreCase = true)) {
                                    // Handle SVG
                                    val content = String(bytes, Charsets.UTF_8)
                                    svgMap[cleanName] = content
                                } else {
                                    // Handle Image
                                    val options = BitmapFactory.Options().apply {
                                        inPreferredConfig = Bitmap.Config.ARGB_8888
                                    }
                                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                                    if (bitmap != null) {
                                        bitmapMap[cleanName] = bitmap
                                    }
                                }
                            }
                        }
                    }
                    
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }

        if (projectData == null) {
            throw IllegalStateException("Invalid .skc file: project.json missing")
        }

        return Triple(projectData!!, bitmapMap, svgMap)
    }
}


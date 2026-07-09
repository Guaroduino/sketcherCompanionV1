package com.sketcher.sketchercompanionv1.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.dto.ProjectJson
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
                    val originalFileName = element.originalImageFileName
                    val originalBmp = element.originalBitmap
                    if (originalFileName != null && originalBmp != null && originalFileName.isNotEmpty() && savedFileNames.add(originalFileName)) {
                        val entryName = "$DIR_ASSETS$originalFileName"
                        val imageEntry = ZipEntry(entryName)
                        zipOut.putNextEntry(imageEntry)
                        originalBmp.compress(Bitmap.CompressFormat.PNG, 100, zipOut)
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
        components: Collection<ComponentDefinition> = emptyList(),
        thumbnail: Bitmap? = null,
        toolStatesJson: String? = null
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

                // Save tool_states.json if provided
                if (toolStatesJson != null) {
                    try {
                        val entry = ZipEntry("tool_states.json")
                        zipOut.putNextEntry(entry)
                        zipOut.write(toolStatesJson.toByteArray(Charsets.UTF_8))
                        zipOut.closeEntry()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Write preview.png if thumbnail is provided
                if (thumbnail != null) {
                    try {
                        val entry = ZipEntry("preview.png")
                        zipOut.putNextEntry(entry)
                        thumbnail.compress(Bitmap.CompressFormat.PNG, 90, zipOut)
                        zipOut.closeEntry()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Save toolbar layout if available in SharedPreferences
                val prefs = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)
                val toolbarLayoutJson = prefs.getString("saved_layout_v2", null)
                if (toolbarLayoutJson != null) {
                    val entry = ZipEntry("toolbar_layout.json")
                    zipOut.putNextEntry(entry)
                    zipOut.write(toolbarLayoutJson.toByteArray(Charsets.UTF_8))
                    zipOut.closeEntry()
                }

                // Save custom icons if available in SharedPreferences
                val themePrefs = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
                val themeJson = themePrefs.getString("saved_theme", null)
                if (themeJson != null) {
                    try {
                        val parsed = Gson().fromJson(themeJson, Map::class.java)
                        val customIconsObj = parsed["customIcons"]
                        if (customIconsObj != null) {
                            val customIconsJson = Gson().toJson(customIconsObj)
                            val entry = ZipEntry("custom_icons.json")
                            zipOut.putNextEntry(entry)
                            zipOut.write(customIconsJson.toByteArray(Charsets.UTF_8))
                            zipOut.closeEntry()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

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

                // 2.5 Write Textures from FillStyles
                val texturePaths = mutableSetOf<String>()
                layers.forEach { layer ->
                    layer.elements.forEach { it.collectAllAssetPaths(texturePaths) }
                }
                components.forEach { component ->
                    component.elements.forEach { it.collectAllAssetPaths(texturePaths) }
                }
                texturePaths.forEach { absPath ->
                    try {
                        val file = java.io.File(absPath)
                        if (file.exists()) {
                            val fileName = file.name
                            if (fileName.isNotEmpty() && savedFileNames.add(fileName)) {
                                val entryName = "$DIR_ASSETS$fileName"
                                val imageEntry = java.util.zip.ZipEntry(entryName)
                                zipOut.putNextEntry(imageEntry)
                                java.io.FileInputStream(file).use { it.copyTo(zipOut) }
                                zipOut.closeEntry()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
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
                    } else if (name == "toolbar_layout.json") {
                        val bytes = zipIn.readBytes()
                        val jsonString = String(bytes, Charsets.UTF_8)
                        if (jsonString.isNotEmpty()) {
                            val prefs = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("saved_layout_v2", jsonString).apply()
                        }
                    } else if (name == "tool_states.json") {
                        val bytes = zipIn.readBytes()
                        val jsonString = String(bytes, Charsets.UTF_8)
                        if (jsonString.isNotEmpty()) {
                            val prefs = context.getSharedPreferences("tool_state_temp_prefs", Context.MODE_PRIVATE)
                            prefs.edit().putString("temp_loaded_tool_states", jsonString).apply()
                        }
                    } else if (name == "custom_icons.json") {
                        val bytes = zipIn.readBytes()
                        val jsonString = String(bytes, Charsets.UTF_8)
                        if (jsonString.isNotEmpty()) {
                            try {
                                val typeToken = object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
                                val loadedIcons: Map<String, String> = Gson().fromJson(jsonString, typeToken)
                                
                                val themePrefs = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
                                val themeJson = themePrefs.getString("saved_theme", null)
                                val gson = Gson()
                                val updatedThemeJson = if (themeJson != null) {
                                    val mapType = object : com.google.gson.reflect.TypeToken<MutableMap<String, Any>>() {}.type
                                    val map: MutableMap<String, Any> = gson.fromJson(themeJson, mapType)
                                    val currentIcons = map["customIcons"] as? Map<*, *> ?: emptyMap<Any, Any>()
                                    val mergedIcons = currentIcons.toMutableMap().apply {
                                        putAll(loadedIcons)
                                    }
                                    map["customIcons"] = mergedIcons
                                    gson.toJson(map)
                                } else {
                                    gson.toJson(mapOf("customIcons" to loadedIcons))
                                }
                                themePrefs.edit().putString("saved_theme", updatedThemeJson).apply()
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
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
                                    
                                    // Save to local textures directory for ImageTextureCache to find via relative paths
                                    try {
                                        val texDir = java.io.File(context.filesDir, "textures")
                                        if (!texDir.exists()) texDir.mkdirs()
                                        val texFile = java.io.File(texDir, cleanName)
                                        if (!texFile.exists()) {
                                            java.io.FileOutputStream(texFile).use { it.write(bytes) }
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
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

    fun loadThumbnail(file: java.io.File): Bitmap? {
        try {
            if (!file.exists()) return null
            java.io.FileInputStream(file).use { fileIn ->
                ZipInputStream(java.io.BufferedInputStream(fileIn)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "preview.png") {
                            val bytes = zipIn.readBytes()
                            if (bytes.isNotEmpty()) {
                                val options = BitmapFactory.Options().apply {
                                    inPreferredConfig = Bitmap.Config.ARGB_8888
                                }
                                return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            }
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    fun loadGlobalScaleRatio(file: java.io.File): Float {
        try {
            if (!file.exists()) return 1.0f
            java.io.FileInputStream(file).use { fileIn ->
                ZipInputStream(java.io.BufferedInputStream(fileIn)).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        if (entry.name == "project.json") {
                            val reader = java.io.InputStreamReader(zipIn, Charsets.UTF_8)
                            val gson = Gson()
                            val projectJson = gson.fromJson(reader, ProjectJson::class.java)
                            return projectJson.scaleConfig?.globalScaleRatio ?: 1.0f
                        }
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return 1.0f
    }
}


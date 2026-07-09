package com.sketcher.sketchercompanionv1.managers

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.ImageElement
import com.sketcher.sketchercompanionv1.LayerElement
import com.sketcher.sketchercompanionv1.LibraryComponent
import com.sketcher.sketchercompanionv1.LibraryFolder
import com.sketcher.sketchercompanionv1.LibraryItem
import com.sketcher.sketchercompanionv1.SvgElement
import com.sketcher.sketchercompanionv1.dto.LibraryItemJson
import com.sketcher.sketchercompanionv1.dto.LibraryStateJson
import com.sketcher.sketchercompanionv1.utils.toLayerElementJson
import com.sketcher.sketchercompanionv1.utils.toComponentDefinitionJson
import com.sketcher.sketchercompanionv1.utils.toComponentDefinition
import com.sketcher.sketchercompanionv1.utils.collectAllAssetPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LibraryManager {
    private const val LIBRARY_FILE_NAME = "global_library.json"
    private const val ASSETS_DIR_NAME = "library_assets"

    private fun getUserDir(context: Context): File {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val userDir = File(context.filesDir, "users/$currentUid")
        if (!userDir.exists()) userDir.mkdirs()
        return userDir
    }

    suspend fun saveLibrary(context: Context, items: List<LibraryItem>) {
        withContext(Dispatchers.IO) {
            val userDir = getUserDir(context)
            val assetsDir = File(userDir, ASSETS_DIR_NAME)
            if (!assetsDir.exists()) {
                assetsDir.mkdirs()
            }

            val itemsJson = items.map { item ->
                when (item) {
                    is LibraryFolder -> LibraryItemJson(
                        type = "FOLDER",
                        id = item.id,
                        name = item.name,
                        parentId = item.parentId
                    )
                    is LibraryComponent -> {
                        // Save assets
                        saveAssets(item.definition.elements, assetsDir)

                        val originalDefJson = item.definition.toComponentDefinitionJson()
                        val strippedDefJson = originalDefJson.copy(
                            elements = stripOriginalImages(originalDefJson.elements)
                        )

                        LibraryItemJson(
                            type = "COMPONENT",
                            id = item.id,
                            name = item.name,
                            parentId = item.parentId,
                            componentDefinition = strippedDefJson,
                            thumbnailFileName = item.thumbnailFileName
                        )
                    }
                }
            }

            val stateJson = LibraryStateJson(itemsJson)
            val jsonString = Gson().toJson(stateJson)

            val file = File(userDir, LIBRARY_FILE_NAME)
            file.writeText(jsonString, Charsets.UTF_8)
        }
    }

    suspend fun loadLibrary(context: Context): List<LibraryItem> {
        return withContext(Dispatchers.IO) {
            val userDir = getUserDir(context)
            val file = File(userDir, LIBRARY_FILE_NAME)
            if (!file.exists()) return@withContext emptyList()

            val assetsDir = File(userDir, ASSETS_DIR_NAME)
            val jsonString = file.readText(Charsets.UTF_8)
            val stateJson = try { Gson().fromJson(jsonString, LibraryStateJson::class.java) } catch (e: Exception) { null }

            stateJson?.items?.mapNotNull { itemJson ->
                when (itemJson.type) {
                    "FOLDER" -> LibraryFolder(itemJson.id, itemJson.name, itemJson.parentId)
                    "COMPONENT" -> {
                        val defJson = itemJson.componentDefinition ?: return@mapNotNull null
                        
                        // Load assets before converting
                        val bitmapMap = mutableMapOf<String, Bitmap>()
                        val svgMap = mutableMapOf<String, String>()
                        
                        fun loadAssetsRecursively(elementsJson: List<com.sketcher.sketchercompanionv1.dto.LayerElementJson>) {
                            elementsJson.forEach { elJson ->
                                if (elJson.type == "IMAGE" && elJson.image != null) {
                                    val fileName = elJson.image.fileName
                                    val imgFile = File(assetsDir, fileName)
                                    if (imgFile.exists() && !bitmapMap.containsKey(fileName)) {
                                        val options = BitmapFactory.Options().apply {
                                            inPreferredConfig = Bitmap.Config.ARGB_8888
                                        }
                                        val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath, options)
                                        if (bitmap != null) {
                                            bitmapMap[fileName] = bitmap
                                        }
                                    }
                                    val originalFileName = elJson.image.originalFileName
                                    if (originalFileName != null) {
                                        val origFile = File(assetsDir, originalFileName)
                                        if (origFile.exists() && !bitmapMap.containsKey(originalFileName)) {
                                            val options = BitmapFactory.Options().apply {
                                                inPreferredConfig = Bitmap.Config.ARGB_8888
                                            }
                                            val bitmap = BitmapFactory.decodeFile(origFile.absolutePath, options)
                                            if (bitmap != null) {
                                                bitmapMap[originalFileName] = bitmap
                                            }
                                        }
                                    }
                                } else if (elJson.type == "SVG" && elJson.svg != null) {
                                    val fileName = elJson.svg.fileName
                                    val svgFile = File(assetsDir, fileName)
                                    if (svgFile.exists() && !svgMap.containsKey(fileName)) {
                                        svgMap[fileName] = svgFile.readText(Charsets.UTF_8)
                                    }
                                } else if (elJson.type == "GROUP" && elJson.group != null) {
                                    loadAssetsRecursively(elJson.group.elements)
                                }
                            }
                        }
                        
                        loadAssetsRecursively(defJson.elements)
                        
                        val definition = defJson.toComponentDefinition(
                            bitmapLoader = { fileName -> bitmapMap[fileName] },
                            svgLoader = { fileName -> svgMap[fileName] }
                        )
                        LibraryComponent(itemJson.id, itemJson.name, itemJson.parentId, definition, itemJson.thumbnailFileName)
                    }
                    else -> null
                }
            } ?: emptyList()
        }
    }

    private fun saveAssets(elements: List<LayerElement>, assetsDir: File) {
        val texturePaths = mutableSetOf<String>()

        elements.forEach { element ->
            element.collectAllAssetPaths(texturePaths)

            if (element is ImageElement) {
                val file = File(assetsDir, element.imageFileName)
                if (!file.exists()) {
                    FileOutputStream(file).use { out ->
                        element.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else if (element is com.sketcher.sketchercompanionv1.SvgElement) {
                val file = File(assetsDir, element.svgFileName)
                if (!file.exists()) {
                    file.writeText(element.svgContent, Charsets.UTF_8)
                }
            } else if (element is com.sketcher.sketchercompanionv1.GroupElement) {
                saveAssets(element.elements, assetsDir)
            } else if (element is com.sketcher.sketchercompanionv1.ComponentInstance) {
                // Not supported to save recursive components right now unless we fetch their definition
            }
        }

        // Copy texture files
        texturePaths.forEach { absPath ->
            try {
                val sourceFile = java.io.File(absPath)
                if (sourceFile.exists()) {
                    val destFile = java.io.File(assetsDir, sourceFile.name)
                    if (!destFile.exists()) {
                        java.io.FileInputStream(sourceFile).use { input ->
                            java.io.FileOutputStream(destFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    private fun stripOriginalImages(elements: List<com.sketcher.sketchercompanionv1.dto.LayerElementJson>): List<com.sketcher.sketchercompanionv1.dto.LayerElementJson> {
        return elements.map { el ->
            if (el.type == "IMAGE" && el.image != null) {
                el.copy(
                    image = el.image.copy(
                        originalFileName = null,
                        cropRectLeft = null,
                        cropRectTop = null,
                        cropRectRight = null,
                        cropRectBottom = null,
                        cropPathPointsX = null,
                        cropPathPointsY = null
                    )
                )
            } else if (el.type == "GROUP" && el.group != null) {
                el.copy(
                    group = el.group.copy(
                        elements = stripOriginalImages(el.group.elements)
                    )
                )
            } else {
                el
            }
        }
    }
}

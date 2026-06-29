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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object LibraryManager {
    private const val LIBRARY_FILE_NAME = "global_library.json"
    private const val ASSETS_DIR_NAME = "library_assets"

    suspend fun saveLibrary(context: Context, items: List<LibraryItem>) {
        withContext(Dispatchers.IO) {
            val assetsDir = File(context.filesDir, ASSETS_DIR_NAME)
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

                        LibraryItemJson(
                            type = "COMPONENT",
                            id = item.id,
                            name = item.name,
                            parentId = item.parentId,
                            componentDefinition = item.definition.toComponentDefinitionJson(),
                            thumbnailFileName = item.thumbnailFileName
                        )
                    }
                }
            }

            val stateJson = LibraryStateJson(itemsJson)
            val jsonString = Gson().toJson(stateJson)

            val file = File(context.filesDir, LIBRARY_FILE_NAME)
            file.writeText(jsonString, Charsets.UTF_8)
        }
    }

    suspend fun loadLibrary(context: Context): List<LibraryItem> {
        return withContext(Dispatchers.IO) {
            val file = File(context.filesDir, LIBRARY_FILE_NAME)
            if (!file.exists()) return@withContext emptyList()

            val assetsDir = File(context.filesDir, ASSETS_DIR_NAME)
            val jsonString = file.readText(Charsets.UTF_8)
            val stateJson = Gson().fromJson(jsonString, LibraryStateJson::class.java)

            stateJson.items.mapNotNull { itemJson ->
                when (itemJson.type) {
                    "FOLDER" -> LibraryFolder(itemJson.id, itemJson.name, itemJson.parentId)
                    "COMPONENT" -> {
                        val defJson = itemJson.componentDefinition ?: return@mapNotNull null
                        
                        // Load assets before converting
                        val bitmapMap = mutableMapOf<String, Bitmap>()
                        val svgMap = mutableMapOf<String, String>()
                        
                        defJson.elements.forEach { elJson ->
                            if (elJson.type == "IMAGE" && elJson.image != null) {
                                val fileName = elJson.image.fileName
                                val imgFile = File(assetsDir, fileName)
                                if (imgFile.exists()) {
                                    val bitmap = BitmapFactory.decodeFile(imgFile.absolutePath)
                                    if (bitmap != null) {
                                        bitmapMap[fileName] = bitmap
                                    }
                                }
                            }
                            // TODO: load SVGs if needed, but we don't have full support in ZipStorageManager for global SVG yet.
                        }
                        
                        val definition = defJson.toComponentDefinition(
                            bitmapLoader = { fileName -> bitmapMap[fileName] },
                            svgLoader = { fileName -> svgMap[fileName] }
                        )
                        LibraryComponent(itemJson.id, itemJson.name, itemJson.parentId, definition, itemJson.thumbnailFileName)
                    }
                    else -> null
                }
            }
        }
    }

    private fun saveAssets(elements: List<LayerElement>, assetsDir: File) {
        elements.forEach { element ->
            if (element is ImageElement) {
                val file = File(assetsDir, element.imageFileName)
                if (!file.exists()) {
                    FileOutputStream(file).use { out ->
                        element.bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                    }
                }
            } else if (element is com.sketcher.sketchercompanionv1.GroupElement) {
                saveAssets(element.elements, assetsDir)
            } else if (element is com.sketcher.sketchercompanionv1.ComponentInstance) {
                // Not supported to save recursive components right now unless we fetch their definition
            }
        }
    }
}

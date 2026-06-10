package com.sketcher.sketchercompanionv1.utils

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.dto.ProjectData
import java.io.File
import java.util.UUID

object TemplateManager {

    private const val TEMPLATE_DIR = "templates"
    private const val EXTENSION = ".skt"

    /**
     * Saves the current project state as a template.
     */
    fun saveAsTemplate(context: Context, projectData: ProjectData, layers: List<Layer>, components: Collection<com.sketcher.sketchercompanionv1.ComponentDefinition>, templateName: String) {
        val templatesDir = File(context.filesDir, TEMPLATE_DIR)
        if (!templatesDir.exists()) {
            templatesDir.mkdirs()
        }

        val file = File(templatesDir, "$templateName$EXTENSION")
        // Use Uri.fromFile to allow ZipStorageManager to open it via ContentResolver (if supported) 
        // OR we should verify ZipStorageManager supports file schemes. 
        // ContentResolver.openOutputStream(Uri.fromFile(file)) works on standard Android.
        val uri = Uri.fromFile(file)
        
        ZipStorageManager.saveProject(context, projectData, layers, uri, components)
    }

    /**
     * Loads a template and regenerates the Project ID.
     */
    fun loadTemplate(context: Context, file: File): Triple<ProjectData, Map<String, Bitmap>, Map<String, String>> {
        val uri = Uri.fromFile(file)
        val (originalData, bitmaps, svgs) = ZipStorageManager.loadProject(context, uri)
        
        // Regenerate ID because it's a new project based on a template
        val newData = originalData.copy(id = UUID.randomUUID().toString())
        
        return Triple(newData, bitmaps, svgs)
    }

    fun getAvailableTemplates(context: Context): List<File> {
        val templatesDir = File(context.filesDir, TEMPLATE_DIR)
        if (!templatesDir.exists()) return emptyList()
        
        return templatesDir.listFiles { _, name -> name.endsWith(EXTENSION) }?.toList() ?: emptyList()
    }
}


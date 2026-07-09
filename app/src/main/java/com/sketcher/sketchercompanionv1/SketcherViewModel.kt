package com.sketcher.sketchercompanionv1



import android.app.Application

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice

import android.graphics.Color as AndroidColor

import android.graphics.Matrix

import android.graphics.Paint

import android.graphics.PorterDuff

import android.graphics.PorterDuffXfermode

import android.net.wifi.WifiManager

import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.setValue

import androidx.compose.runtime.mutableStateListOf

import androidx.compose.runtime.toMutableStateList

import androidx.compose.runtime.mutableFloatStateOf

import androidx.compose.runtime.mutableStateMapOf

import androidx.compose.runtime.mutableIntStateOf

import androidx.lifecycle.AndroidViewModel

import androidx.lifecycle.viewModelScope

import com.sketcher.sketchercompanionv1.projection.LiveProjectionServer

import com.sketcher.sketchercompanionv1.projection.ProjectionClient

import java.io.ByteArrayOutputStream

import java.net.Inet4Address

import java.net.NetworkInterface



import kotlinx.coroutines.flow.MutableStateFlow

import kotlinx.coroutines.flow.StateFlow

import kotlinx.coroutines.flow.asStateFlow
import androidx.compose.runtime.remember
import kotlinx.coroutines.tasks.await
import com.sketcher.sketchercompanionv1.managers.LibraryManager
import com.sketcher.sketchercompanionv1.LibraryItem
import com.sketcher.sketchercompanionv1.LibraryFolder
import com.sketcher.sketchercompanionv1.utils.toComponentDefinitionJson
import com.sketcher.sketchercompanionv1.LibraryComponent


import kotlinx.coroutines.launch

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.withContext

import com.google.gson.Gson

import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.tools.*

import com.sketcher.sketchercompanionv1.utils.TemplateManager
import com.sketcher.sketchercompanionv1.utils.toFillStyle
import com.sketcher.sketchercompanionv1.utils.toFillStyleJson

import com.sketcher.sketchercompanionv1.importers.DxfImportData

import com.sketcher.sketchercompanionv1.utils.PathUtils

import java.io.File

import java.util.UUID

import com.sketcher.sketchercompanionv1.utils.toLayerJson

import com.sketcher.sketchercompanionv1.utils.toLayer

import com.sketcher.sketchercompanionv1.utils.toComponentDefinitionJson

import com.sketcher.sketchercompanionv1.utils.toComponentDefinition

import com.sketcher.sketchercompanionv1.utils.SvgExporter

import java.util.ArrayDeque

import android.graphics.Bitmap

import android.graphics.Canvas

import android.graphics.RectF

import android.net.Uri

import androidx.annotation.MainThread

import com.sketcher.sketchercompanionv1.command.*

import com.sketcher.sketchercompanionv1.data.ThemeRepository

import com.sketcher.sketchercompanionv1.data.ToolbarRepository

import com.sketcher.sketchercompanionv1.dto.CustomTool
import com.sketcher.sketchercompanionv1.dto.BrushPreset
import com.sketcher.sketchercompanionv1.dto.ToolType
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

import com.sketcher.sketchercompanionv1.ui.components.ToolPayload

import com.sketcher.sketchercompanionv1.ui.model.StudioTool

import com.sketcher.sketchercompanionv1.ui.model.ToolLocation

import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted

import androidx.compose.material.icons.Icons

import androidx.compose.material.icons.filled.*



class SketcherViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)

    private fun getSafeInt(key: String, default: Int): Int {
        return try {
            prefs.getInt(key, default)
        } catch (e: Exception) {
            try {
                prefs.getLong(key, default.toLong()).toInt()
            } catch (ex: Exception) {
                default
            }
        }
    }

    private val themeRepository = ThemeRepository(application)

    private val toolbarRepository = ToolbarRepository(application)
    
    private val cloudSyncRepository = com.sketcher.sketchercompanionv1.data.repository.CloudSyncRepository()
    
    var isSyncingCloud by mutableStateOf(false)
        private set
    var cloudSyncMessage by mutableStateOf<String?>(null)
        private set
    var syncTrigger by mutableStateOf(0)
        private set

    fun incrementSyncTrigger() {
        viewModelScope.launch(Dispatchers.Main) {
            syncTrigger++
        }
    }

    fun getProjectLastUploadedTime(context: Context, projectId: String): Long {
        val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("sync_project_last_uploaded_$projectId", 0L)
    }

    fun setProjectLastUploadedTime(context: Context, projectId: String, timestamp: Long) {
        val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("sync_project_last_uploaded_$projectId", timestamp).apply()
        incrementSyncTrigger()
    }

    fun getFolderLastUploadedTime(context: Context, relPath: String): Long {
        val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("sync_folder_last_uploaded_$relPath", 0L)
    }

    fun setFolderLastUploadedTime(context: Context, relPath: String, timestamp: Long) {
        val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("sync_folder_last_uploaded_$relPath", timestamp).apply()
        incrementSyncTrigger()
    }

    fun getLibraryLastUploadedTime(context: Context): Long {
        val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
        return prefs.getLong("sync_library_last_uploaded", 0L)
    }

    fun setLibraryLastUploadedTime(context: Context, timestamp: Long) {
        val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
        prefs.edit().putLong("sync_library_last_uploaded", timestamp).apply()
        incrementSyncTrigger()
    }

    @Composable
    fun isProjectSynced(context: Context, projectId: String, localLastModified: Long): Boolean {
        val trigger = syncTrigger
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return false
        val lastUploaded = getProjectLastUploadedTime(context, projectId)
        return lastUploaded != 0L && (localLastModified <= lastUploaded || Math.abs(localLastModified - lastUploaded) <= 2000)
    }

    @Composable
    fun isFolderSynced(context: Context, relPath: String, localLastModified: Long): Boolean {
        val trigger = syncTrigger
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return false
        
        val rootDir = remember(relPath) { getProjectsRootDir(context) }
        val folderDir = remember(relPath) { java.io.File(rootDir, relPath) }
        val metadataFile = remember(relPath) { java.io.File(folderDir, ".metadata.json") }
        
        val isFolderMetaSynced = if (metadataFile.exists()) {
            val lastUploaded = getFolderLastUploadedTime(context, relPath)
            lastUploaded != 0L && (metadataFile.lastModified() <= lastUploaded || Math.abs(metadataFile.lastModified() - lastUploaded) <= 2000L)
        } else {
            true
        }
        if (!isFolderMetaSynced) return false

        // Check if all projects inside are synced
        if (folderDir.exists() && folderDir.isDirectory) {
            val projectFiles = remember(trigger, relPath) {
                folderDir.walkTopDown().filter { it.extension == "skc" }.toList()
            }
            val allProjectsSynced = projectFiles.all { file ->
                val pId = getProjectId(file) ?: return@all true
                val lastUploadedProj = getProjectLastUploadedTime(context, pId)
                lastUploadedProj != 0L && (file.lastModified() <= lastUploadedProj || Math.abs(file.lastModified() - lastUploadedProj) <= 2000L)
            }
            if (!allProjectsSynced) return false
        }
        return true
    }

    @Composable
    fun isLibrarySynced(context: Context): Boolean {
        val trigger = syncTrigger
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return false
        val localLibraryFile = getLibraryFile(context)
        if (!localLibraryFile.exists()) return true
        val localLastModified = localLibraryFile.lastModified()
        val lastUploaded = getLibraryLastUploadedTime(context)
        return lastUploaded != 0L && (localLastModified <= lastUploaded || Math.abs(localLastModified - lastUploaded) <= 2000)
    }

    fun getDeviceUid(context: Context): String {
        val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
        var uid = prefs.getString("device_uid", null)
        if (uid == null) {
            uid = UUID.randomUUID().toString()
            prefs.edit().putString("device_uid", uid).apply()
        }
        return uid
    }

    private fun rewriteProjectIdInZip(file: java.io.File, newProjectId: String) {
        try {
            val tempFile = java.io.File(file.parentFile, "${file.name}.idtemp")
            java.util.zip.ZipInputStream(file.inputStream().buffered()).use { zipIn ->
                java.util.zip.ZipOutputStream(tempFile.outputStream().buffered()).use { zipOut ->
                    var entry = zipIn.nextEntry
                    while (entry != null) {
                        zipOut.putNextEntry(java.util.zip.ZipEntry(entry.name))
                        if (entry.name == "project.json") {
                            val json = zipIn.bufferedReader(Charsets.UTF_8).readText()
                            val obj = org.json.JSONObject(json)
                            obj.put("id", newProjectId)
                            zipOut.write(obj.toString().toByteArray(Charsets.UTF_8))
                        } else {
                            zipIn.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            if (tempFile.exists()) {
                file.delete()
                tempFile.renameTo(file)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    var projectVersionsList by mutableStateOf<List<Map<String, Any>>>(emptyList())
        private set
    var isLoadingVersions by mutableStateOf(false)
        private set

    fun fetchProjectVersions(projectId: String) {
        viewModelScope.launch {
            isLoadingVersions = true
            try {
                val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                if (user != null) {
                    val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                        .collection("users").document(user.uid)
                        .collection("projects").document(projectId).get().await()
                    val versions = snapshot.get("versions") as? List<Map<String, Any>> ?: emptyList()
                    projectVersionsList = versions.sortedByDescending { (it["timestamp"] as? Number)?.toLong() ?: 0L }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingVersions = false
            }
        }
    }

    fun restoreProjectVersion(context: Context, projectId: String, relativePath: String, fileUrl: String, timestamp: Long) {
        viewModelScope.launch {
            isSyncingCloud = true
            cloudSyncMessage = "Restaurando versión..."
            try {
                val rootDir = getProjectsRootDir(context)
                val destFile = java.io.File(rootDir, relativePath)
                destFile.parentFile?.mkdirs()
                
                val tempFile = java.io.File(destFile.parentFile, "${destFile.name}.tmp")
                val downloadRes = cloudSyncRepository.downloadProject(projectId, tempFile, fileUrl)
                if (downloadRes.isSuccess) {
                    if (destFile.exists()) destFile.delete()
                    tempFile.renameTo(destFile)
                    
                    // Set new local modification timestamp (current time) to treat it as a new version
                    val newTimestamp = System.currentTimeMillis()
                    destFile.setLastModified(newTimestamp)
                    
                    // Upload this restored version back as the latest version
                    uploadProjectSilently(context, destFile)
                    
                    setProjectLastUploadedTime(context, projectId, newTimestamp)
                    refreshLocalItems()
                    cloudSyncMessage = "Versión restaurada con éxito"
                } else {
                    cloudSyncMessage = "Error al descargar la versión"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cloudSyncMessage = "Error al restaurar"
            } finally {
                kotlinx.coroutines.delay(2000)
                cloudSyncMessage = null
                isSyncingCloud = false
            }
        }
    }

    fun createProjectCopyFromVersion(context: Context, originalName: String, projectId: String, fileUrl: String) {
        viewModelScope.launch {
            isSyncingCloud = true
            cloudSyncMessage = "Creando copia..."
            try {
                val rootDir = getProjectsRootDir(context)
                val newProjectId = UUID.randomUUID().toString()
                
                var baseName = "${originalName}_Copia"
                var copyFile = java.io.File(rootDir, "$baseName.skc")
                var counter = 1
                while (copyFile.exists()) {
                    copyFile = java.io.File(rootDir, "${baseName}_$counter.skc")
                    counter++
                }
                
                val tempFile = java.io.File(rootDir, "${copyFile.name}.tmp")
                val downloadRes = cloudSyncRepository.downloadProject(projectId, tempFile, fileUrl)
                if (downloadRes.isSuccess) {
                    tempFile.renameTo(copyFile)
                    
                    // Rewrite ID in the new file
                    rewriteProjectIdInZip(copyFile, newProjectId)
                    
                    // Copy thumbnail if it exists
                    val thumbCacheDir = java.io.File(context.cacheDir, "thumbnails")
                    val oldThumbFile = java.io.File(thumbCacheDir, "$projectId.png")
                    val newThumbFile = java.io.File(thumbCacheDir, "$newProjectId.png")
                    if (oldThumbFile.exists()) {
                        try {
                            oldThumbFile.copyTo(newThumbFile, overwrite = true)
                        } catch (e: Exception) {}
                    }
                    
                    // Upload this new project copy
                    uploadProjectSilently(context, copyFile)
                    
                    refreshLocalItems()
                    cloudSyncMessage = "Copia creada con éxito"
                } else {
                    cloudSyncMessage = "Error al descargar la versión"
                }
            } catch (e: Exception) {
                e.printStackTrace()
                cloudSyncMessage = "Error al crear copia"
            } finally {
                kotlinx.coroutines.delay(2000)
                cloudSyncMessage = null
                isSyncingCloud = false
            }
        }
    }


    private val projectFileManager = com.sketcher.sketchercompanionv1.managers.ProjectFileManager()

    val toolManager = com.sketcher.sketchercompanionv1.managers.ToolManager(application)

    val selectionManager = SelectionManager()

    val liveProjectionController = com.sketcher.sketchercompanionv1.projection.LiveProjectionController(
        scope = viewModelScope,
        onClientCountChanged = { count -> projectionClientCount = count },
        onUrlChanged = { url -> projectionUrl = url },
        onActiveChanged = { active -> isProjectionActive = active },
        onViewportsChanged = { viewports -> projectionViewports = viewports }
    )

    val toolbarManager by lazy {
        com.sketcher.sketchercompanionv1.managers.ToolbarManager(
            toolbarRepository = toolbarRepository,
            prefs = prefs,
            getDefaultStrokeColor = { strokeColor.value },
            getDefaultFillColor = { fillColor.value },
            activateTool = { payload, id -> activateTool(payload, id) },
            getActionForTool = { id -> getActionForTool(id) }
        )
    }



    // STATE

    // --- UI/DEBUG SETTINGS (Restored) ---

    var isDebugWireframe by mutableStateOf(false)

    var lastViewportWidth by mutableFloatStateOf(0f)

    var lastViewportHeight by mutableFloatStateOf(0f)

    

    var showPerformanceStats by mutableStateOf(prefs.getBoolean("show_performance_stats", false))

        private set



    fun togglePerformanceStats() {

        showPerformanceStats = !showPerformanceStats

        prefs.edit().putBoolean("show_performance_stats", showPerformanceStats).apply()

    }

    var showPublicLibrary by mutableStateOf(prefs.getBoolean("show_public_library", false))
        private set

    fun togglePublicLibrary() {
        showPublicLibrary = !showPublicLibrary
        prefs.edit().putBoolean("show_public_library", showPublicLibrary).apply()
    }

    private val _showExperimentalTools = MutableStateFlow(prefs.getBoolean("show_experimental_tools", false))

    var showExperimentalTools by mutableStateOf(prefs.getBoolean("show_experimental_tools", false))

        private set

    fun toggleExperimentalTools() {
        showExperimentalTools = !showExperimentalTools
        prefs.edit().putBoolean("show_experimental_tools", showExperimentalTools).apply()
        _showExperimentalTools.value = showExperimentalTools
        ToolRegistry.showExperimental = showExperimentalTools
        if (!showExperimentalTools) {
            val isCurrentToolExperimental = when (currentTool) {
                ToolType.WATERCOLOR, ToolType.PENCIL_CUMULATIVE -> true
                else -> false
            }
            if (isCurrentToolExperimental) {
                selectTool(ToolType.FREEHAND)
            }
        }
    }



    val strokeCount: Int by androidx.compose.runtime.derivedStateOf {
        layers.sumOf { layer -> layer.elements.count { it is VectorStroke } }
    }

    val pointCount: Int by androidx.compose.runtime.derivedStateOf {
        layers.sumOf { layer -> layer.elements.sumOf { el -> if (el is VectorStroke) el.points.size else 0 } }
    }



    





    var canUndo by mutableStateOf(false)

        private set

    var canRedo by mutableStateOf(false)

        private set

    

    // SCALE CONFIG

    var scaleConfig by mutableStateOf(ScaleConfig())

        private set



    // UNITS

    var currentUnit by mutableStateOf(DistanceUnit.MM)



    // GRID CONFIG

    var gridConfig by mutableStateOf(GridConfig(spacing = 5f, isVisible = false))

    var isSnapToGridEnabled by mutableStateOf(false)

    var isElementSnappingEnabled by mutableStateOf(prefs.getBoolean("element_snapping", false))

        private set



    fun toggleElementSnapping() {

        isElementSnappingEnabled = !isElementSnappingEnabled

        prefs.edit().putBoolean("element_snapping", isElementSnappingEnabled).apply()

    }



    private val _isSnapEndpointEnabled = mutableStateOf(prefs.getBoolean("snap_endpoint_enabled", true))

    var isSnapEndpointEnabled: Boolean

        get() = _isSnapEndpointEnabled.value

        set(value) {

            _isSnapEndpointEnabled.value = value

            prefs.edit().putBoolean("snap_endpoint_enabled", value).apply()

        }



    private val _isSnapMidpointEnabled = mutableStateOf(prefs.getBoolean("snap_midpoint_enabled", true))

    var isSnapMidpointEnabled: Boolean

        get() = _isSnapMidpointEnabled.value

        set(value) {

            _isSnapMidpointEnabled.value = value

            prefs.edit().putBoolean("snap_midpoint_enabled", value).apply()

        }



    private val _isSnapCenterEnabled = mutableStateOf(prefs.getBoolean("snap_center_enabled", true))

    var isSnapCenterEnabled: Boolean

        get() = _isSnapCenterEnabled.value

        set(value) {

            _isSnapCenterEnabled.value = value

            prefs.edit().putBoolean("snap_center_enabled", value).apply()

        }



    private val _isSnapIntersectionEnabled = mutableStateOf(prefs.getBoolean("snap_intersection_enabled", true))

    var isSnapIntersectionEnabled: Boolean

        get() = _isSnapIntersectionEnabled.value

        set(value) {

            _isSnapIntersectionEnabled.value = value

            prefs.edit().putBoolean("snap_intersection_enabled", value).apply()

        }



    // CANVAS SIZE CONFIG

    var canvasSizeConfig by mutableStateOf<CanvasSizeConfig?>(null)

        private set



    // SETTINGS

    var isRotationLocked by mutableStateOf(prefs.getBoolean("rotation_lock", false))

    var isPalmRejectionEnabled by mutableStateOf(prefs.getBoolean("palm_rejection", detectStylusSupport(getApplication<Application>())))

    var showTooltips by mutableStateOf(prefs.getBoolean("show_tooltips", true))



    // LAYOUT MIRROR

    var swapVertical by mutableStateOf(prefs.getBoolean("swap_vertical", false))

        private set

    var swapHorizontal by mutableStateOf(prefs.getBoolean("swap_horizontal", false))

        private set



    fun toggleSwapVertical() {

        swapVertical = !swapVertical

        prefs.edit().putBoolean("swap_vertical", swapVertical).apply()

    }



    fun toggleSwapHorizontal() {

        swapHorizontal = !swapHorizontal

        prefs.edit().putBoolean("swap_horizontal", swapHorizontal).apply()

    }

    

    var interfaceScale by mutableStateOf(prefs.getFloat("interface_scale", 0.8f))

        private set

        

    fun updateInterfaceScale(scale: Float) {

        // Guard against invalid values coming from UI controls (NaN/Infinite)

        if (!scale.isFinite()) return



        val clampedScale = scale.coerceIn(0.5f, 1.5f)

        if (!clampedScale.isFinite() || clampedScale <= 0f) return



        interfaceScale = clampedScale

        prefs.edit().putFloat("interface_scale", clampedScale).apply()

    }



    var buttonSpacingFactor by mutableStateOf(prefs.getFloat("button_spacing_factor", 1.0f))

        private set



    fun updateButtonSpacingFactor(factor: Float) {

        if (!factor.isFinite()) return

        val clampedFactor = factor.coerceIn(0.15f, 2.0f)

        if (!clampedFactor.isFinite() || clampedFactor <= 0f) return



        buttonSpacingFactor = clampedFactor

        prefs.edit().putFloat("button_spacing_factor", clampedFactor).apply()

    }



    // BACKGROUND COLOR

    var backgroundColor by mutableIntStateOf(AndroidColor.WHITE)
    var backgroundStyle by mutableStateOf<FillStyle>(FillStyle.Solid(AndroidColor.WHITE))



    // Toolbar Appearance

    var toolbarBackgroundColor by mutableIntStateOf(getSafeInt("toolbar_background_color", AndroidColor.WHITE))

    fun updateToolbarBackgroundColor(color: Int) { 

        toolbarBackgroundColor = color

        prefs.edit().putInt("toolbar_background_color", color).apply()

    }



    // --- TOOLBAR STATE (Dynamic Slot System) ---

    private fun filterExperimentalFromSubTools(tool: StudioTool): StudioTool {
        if (tool.subTools.isEmpty()) return tool
        val filteredSubs = tool.subTools.filter { !it.isExperimental }.map { filterExperimentalFromSubTools(it) }
        return tool.copy(subTools = filteredSubs)
    }

    val toolbarState: kotlinx.coroutines.flow.StateFlow<Map<ToolLocation, List<StudioTool>>> =
        kotlinx.coroutines.flow.combine(
            toolbarManager.toolbarState,
            _showExperimentalTools
        ) { state, showExp ->
            if (showExp) {
                state
            } else {
                state.mapValues { (_, list) ->
                    list.filter { !it.isExperimental }.map { filterExperimentalFromSubTools(it) }
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = emptyMap()
        )

    val contextualToolbar: kotlinx.coroutines.flow.StateFlow<List<StudioTool>> =
        kotlinx.coroutines.flow.combine(
            toolbarManager.contextualToolbar,
            _showExperimentalTools
        ) { list, showExp ->
            if (showExp) {
                list
            } else {
                list.filter { !it.isExperimental }.map { filterExperimentalFromSubTools(it) }
            }
        }.stateIn(
            scope = viewModelScope,
            started = kotlinx.coroutines.flow.SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val isEditMode = toolbarManager.isEditMode

    fun toggleEditMode() {

        toolbarManager.toggleEditMode()

    }



    // --- PROPERTIES PANEL STATE ---

    var showPropertiesPanel by mutableStateOf(false)
        private set

    var showCustomToolsManagerDialog by mutableStateOf(false)

    private var _activeCustomToolIdCompose by mutableStateOf<String?>(null)
    var activeCustomToolId: String?
        get() = _activeCustomToolIdCompose
        set(value) {
            _activeCustomToolIdCompose = value
            toolManager.activeCustomToolId = value
        }



    fun togglePropertiesPanel() {

        showPropertiesPanel = !showPropertiesPanel

    }



    val assignedToolColors = toolbarManager.assignedToolColors
    val assignedToolStabilization = toolbarManager.assignedToolStabilization
    val assignedToolOpacity = toolbarManager.assignedToolOpacity

    var lastActiveColorToolId: String?
        get() = toolbarManager.lastActiveColorToolId
        set(value) { toolbarManager.lastActiveColorToolId = value }

    var lastActiveStabilizationToolId: String?
        get() = toolbarManager.lastActiveStabilizationToolId
        set(value) { toolbarManager.lastActiveStabilizationToolId = value }

    fun updateLastActiveToolColor(color: Int) {
        toolbarManager.updateLastActiveToolColor(color)
    }

    fun updateLastActiveToolStabilization(stabilization: Float) {
        toolbarManager.updateLastActiveToolStabilization(stabilization)
    }

    fun updateLastActiveToolOpacity(opacity: Float) {
        toolbarManager.updateLastActiveToolOpacity(opacity)
    }

    fun restoreStabilizationToPreset() {
        val defaultStab = if (toolManager.currentTool == ToolType.FREEHAND || toolManager.currentTool == ToolType.PENCIL_CUMULATIVE) 0.07f else 0f
        val idx = toolManager.selectedPresetIndex.value
        val list = toolManager.brushPresets.value
        val presetStab = if (idx != null && idx in list.indices) {
            list[idx].stabilization ?: defaultStab
        } else if (list.isNotEmpty()) {
            list[0].stabilization ?: defaultStab
        } else {
            defaultStab
        }
        toolManager.restoreStabilizationToPreset()
        updateLastActiveToolStabilization(presetStab)
    }

    fun restoreOpacityToPreset() {
        val idx = toolManager.selectedPresetIndex.value
        val list = toolManager.brushPresets.value
        val defaultOpacity = 1.0f
        val presetOpacity = if (idx != null && idx in list.indices) {
            list[idx].opacity
        } else if (list.isNotEmpty()) {
            list[0].opacity
        } else {
            defaultOpacity
        }
        toolManager.restoreOpacityToPreset()
        updateLastActiveToolOpacity(presetOpacity)
    }

    fun revertBrushPreset(index: Int) {
        toolManager.revertBrushPreset(index)
        val list = toolManager.brushPresets.value
        if (index in list.indices) {
            val preset = list[index]
            val defaultStab = if (toolManager.currentTool == ToolType.FREEHAND || toolManager.currentTool == ToolType.PENCIL_CUMULATIVE) 0.07f else 0f
            updateLastActiveToolStabilization(preset.stabilization ?: defaultStab)
            updateLastActiveToolOpacity(preset.opacity)
        }
    }

    private val _showStrokeColorPicker = MutableStateFlow(false)
    val showStrokeColorPicker = _showStrokeColorPicker.asStateFlow()

    private val _showFillColorPicker = MutableStateFlow(false)
    val showFillColorPicker = _showFillColorPicker.asStateFlow()

    private val _showStabilizePicker = MutableStateFlow(false)
    val showStabilizePicker = _showStabilizePicker.asStateFlow()

    fun setShowStrokeColorPicker(show: Boolean) { _showStrokeColorPicker.value = show }
    fun setShowFillColorPicker(show: Boolean) { _showFillColorPicker.value = show }
    fun setShowStabilizePicker(show: Boolean) { _showStabilizePicker.value = show }

    private val _showGridMenuDialog = MutableStateFlow(false)
    val showGridMenuDialog = _showGridMenuDialog.asStateFlow()
    fun setShowGridMenuDialog(show: Boolean) { _showGridMenuDialog.value = show }

    private val _showStudioMenu = MutableStateFlow(false)
    val showStudioMenu = _showStudioMenu.asStateFlow()
    fun setShowStudioMenu(show: Boolean) { _showStudioMenu.value = show }

    private val _showPersonalizationDialog = MutableStateFlow(false)
    val showPersonalizationDialog = _showPersonalizationDialog.asStateFlow()
    fun setShowPersonalizationDialog(show: Boolean) { _showPersonalizationDialog.value = show }





    fun activateTool(payload: ToolPayload, toolId: String? = null) {

        if (payload == ToolPayload.STROKE_COLOR || payload == ToolPayload.FILL_COLOR) {

            lastActiveColorToolId = toolId

        }

        when(payload) {
            ToolPayload.CUSTOM -> {
                if (toolId != null) {
                    val ct = toolManager.customTools.value.find { it.id == toolId }
                    if (ct != null) {
                        activateCustomTool(ct)
                    }
                }
            }
            ToolPayload.PENCIL -> {
                selectTool(ToolType.FREEHAND)
            }
            ToolPayload.PEN -> {
                selectTool(ToolType.PEN)
            }
            ToolPayload.PAINT -> {
                selectTool(ToolType.PAINT)
            }
            ToolPayload.WATERCOLOR -> {
                selectTool(ToolType.WATERCOLOR)
            }
            ToolPayload.PLUMA -> {
                selectTool(ToolType.PLUMA)
            }
            ToolPayload.PENCIL_CUMULATIVE -> {
                selectTool(ToolType.PENCIL_CUMULATIVE)
            }
            ToolPayload.ERASER -> selectTool(ToolType.ERASER)

            ToolPayload.STROKE_COLOR -> {
                revertToPresetColor(isStroke = true)
            }

            ToolPayload.FILL_COLOR -> {
                revertToPresetColor(isStroke = false)
            }
            ToolPayload.POINT_ERASER -> {
                selectTool(ToolType.POINT_ERASER)
            }
            ToolPayload.CUT_ERASER -> {
                selectTool(ToolType.CUT_ERASER)
            }
            ToolPayload.STABILIZE -> {
                lastActiveStabilizationToolId = toolId
                toolId?.let { id ->
                    assignedToolStabilization.value[id]?.let { setGlobalStabilization(it) }
                    assignedToolOpacity.value[id]?.let { updateBrushOpacity(it) }
                }
            }
            ToolPayload.TEXT -> {
                selectTool(ToolType.TEXT)
            }

        }

    }



    fun editTool(payload: ToolPayload, toolId: String? = null) {
        if (payload == ToolPayload.STROKE_COLOR || payload == ToolPayload.FILL_COLOR) {
            lastActiveColorToolId = toolId
        }
        if (payload == ToolPayload.STABILIZE) {
            lastActiveStabilizationToolId = toolId
        }
        val settings = toolManager.currentFreehandSettings as? com.sketcher.sketchercompanionv1.tools.WatercolorSettings
        val isLocked = currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true
        when(payload) {
            ToolPayload.STROKE_COLOR -> {
                if (!isLocked) {
                    _showStrokeColorPicker.value = true
                }
            }
            ToolPayload.FILL_COLOR -> _showFillColorPicker.value = true
            ToolPayload.STABILIZE -> _showStabilizePicker.value = true
            else -> {} // Other tools might not have edit dialogs yet
        }
    }



    // --- ASSIGNED TOOLS STATE ---

    val assignedTools = toolbarManager.assignedTools

    fun assignTool(toolId: String, payload: ToolPayload) {

        toolbarManager.assignTool(toolId, payload)

    }



    fun addTool(location: ToolLocation, tool: StudioTool) = toolbarManager.addTool(location, tool)

    fun removeTool(location: ToolLocation, index: Int) = toolbarManager.removeTool(location, index)

    fun replaceTool(location: ToolLocation, index: Int, newTool: StudioTool) = toolbarManager.replaceTool(location, index, newTool)

    fun addSubTool(location: ToolLocation, parentIndex: Int, tool: StudioTool) = toolbarManager.addSubTool(location, parentIndex, tool)

    fun removeSubTool(location: ToolLocation, parentIndex: Int, subToolIndex: Int) = toolbarManager.removeSubTool(location, parentIndex, subToolIndex)

    fun swapSubToolToMain(location: ToolLocation, parentIndex: Int, subToolIndex: Int) = toolbarManager.swapSubToolToMain(location, parentIndex, subToolIndex)

    fun moveSubTool(location: ToolLocation, parentIndex: Int, fromIndex: Int, toIndex: Int) = toolbarManager.moveSubTool(location, parentIndex, fromIndex, toIndex)

    fun insertPlaceholderSlot(location: ToolLocation, targetIndex: Int, relativePosition: Int) = toolbarManager.insertPlaceholderSlot(location, targetIndex, relativePosition)

    private fun ensureDrawingToolActive() {
        val isDrawTool = currentTool == ToolType.FREEHAND ||
                         currentTool == ToolType.PEN ||
                         currentTool == ToolType.PAINT ||
                         currentTool == ToolType.WATERCOLOR ||
                         currentTool == ToolType.PLUMA ||
                         currentTool == ToolType.PENCIL_CUMULATIVE
        if (!isDrawTool) {
            selectTool(ToolType.FREEHAND)
        }
    }

    fun activateCustomTool(customTool: CustomTool) {
        selectTool(customTool.baseToolType)
        activeCustomToolId = customTool.id
        toolManager.applyBrushPresetDirectly(customTool.preset)
        val defaultStab = if (customTool.baseToolType == ToolType.FREEHAND || customTool.baseToolType == ToolType.PENCIL_CUMULATIVE) 0.07f else 0f
        val presetStab = customTool.preset.stabilization ?: defaultStab
        val presetOpacity = customTool.preset.opacity
        updateLastActiveToolStabilization(presetStab)
        updateLastActiveToolOpacity(presetOpacity)
    }

    internal fun getActionForTool(id: String): () -> Unit {
        val ct = toolManager.customTools.value.find { it.id == id }
        if (ct != null) {
            return {
                activateCustomTool(ct)
            }
        }
        return when(id) {
            "brush_workshop" -> ({ showCustomToolsManagerDialog = true })
            "undo" -> ({ undo() })

        "redo" -> ({ redo() })

        "action_copy" -> ({ copy() })

        "action_cut" -> ({ cut() })

        "action_paste" -> ({ paste() })

        "menu" -> ({ _showStudioMenu.value = true })

        "settings" -> ({ _showPersonalizationDialog.value = true })

        "grid_menu" -> ({ _showGridMenuDialog.value = true })

        StudioTool.PROPERTIES_TOOL_ID -> ({ togglePropertiesPanel() })

        "zoom_in" -> ({ zoomIn() })

        "zoom_out" -> ({ zoomOut() })

        "zoom_fit" -> ({ fitContent() })

        "home_view" -> ({ resetCamera() })

        "stroke_color" -> ({
            val settings = toolManager.currentFreehandSettings as? com.sketcher.sketchercompanionv1.tools.WatercolorSettings
            val isLocked = currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true
            if (!isLocked) {
                _showStrokeColorPicker.value = true
            }
        })

        "fill_color" -> ({ _showFillColorPicker.value = true })

        "toggle_snap" -> ({ toggleElementSnapping() })

        "pencil" -> ({ 
             selectTool(ToolType.FREEHAND) 
        })
 
        "paint" -> ({ 
             selectTool(ToolType.PAINT) 
        })
 
        "watercolor" -> ({ 
             selectTool(ToolType.WATERCOLOR) 
        })
 
        "pluma" -> ({ 
             selectTool(ToolType.PLUMA) 
        })
 
        "pencil_cumulative" -> ({ 
             selectTool(ToolType.PENCIL_CUMULATIVE) 
        })
 
        "pen" -> ({ 
             selectTool(ToolType.PEN) 
        })
 
        "eraser" -> ({ selectTool(ToolType.ERASER) })
        "point_eraser" -> ({ selectTool(ToolType.POINT_ERASER) })
        "cut_eraser" -> ({ selectTool(ToolType.CUT_ERASER) })
 
        "stroke_type" -> ({
            ensureDrawingToolActive()
        })

        "stroke_freehand" -> ({
            ensureDrawingToolActive()
            updateStrokeType(StrokeType.FREEHAND)
        })
        "stroke_line" -> ({
            ensureDrawingToolActive()
            updateStrokeType(StrokeType.LINE)
        })
        "stroke_polyline" -> ({
            ensureDrawingToolActive()
            updateStrokeType(StrokeType.POLYLINE)
        })
        "stroke_circle" -> ({
            ensureDrawingToolActive()
            updateStrokeType(StrokeType.CIRCLE)
        })
        "stroke_arc" -> ({
            ensureDrawingToolActive()
            updateStrokeType(StrokeType.ARC)
        })
        "stroke_ellipse" -> ({
            ensureDrawingToolActive()
            updateStrokeType(StrokeType.ELLIPSE)
        })
        "stroke_spline" -> ({
            ensureDrawingToolActive()
            updateStrokeType(StrokeType.SPLINE)
        })
        "stroke_bezier" -> ({
            ensureDrawingToolActive()
            updateStrokeType(StrokeType.BEZIER)
        })

        "line" -> ({
             ensureDrawingToolActive()
             updateStrokeType(StrokeType.LINE)
        })
 
        "circle" -> ({
             ensureDrawingToolActive()
             updateStrokeType(StrokeType.CIRCLE)
        })
 
        "polyline" -> ({
             ensureDrawingToolActive()
             updateStrokeType(StrokeType.POLYLINE)
        })
 
        "arc" -> ({
             ensureDrawingToolActive()
             updateStrokeType(StrokeType.ARC)
        })
 
        "ellipse" -> ({
             ensureDrawingToolActive()
             updateStrokeType(StrokeType.ELLIPSE)
        })
 
        "spline" -> ({
             ensureDrawingToolActive()
             updateStrokeType(StrokeType.SPLINE)
        })
 
        "bezier" -> ({
             ensureDrawingToolActive()
             updateStrokeType(StrokeType.BEZIER)
        })

        "trim" -> ({ selectTool(ToolType.TRIM) })

        "extend" -> ({ selectTool(ToolType.EXTEND) })

        "orto" -> ({ toggleOrthoMode() })

        "mirror" -> ({ selectTool(ToolType.MIRROR) })

        "mover_pt_pt" -> ({ selectTool(ToolType.MOVE_PT_PT) })

        "align_2_pt" -> ({ selectTool(ToolType.ALIGN_2_PT) })

        "offset" -> ({ selectTool(ToolType.OFFSET) })

        "fillet" -> ({ selectTool(ToolType.FILLET) })

        "chamfer" -> ({ selectTool(ToolType.CHAMFER) })

        "edit_points" -> ({ selectTool(ToolType.EDIT_POINTS) })

        "tool_selection" -> ({

             if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) confirmTransform()

             selectTool(ToolType.SELECTION)

             currentSelectionMode = SelectionMode.FREEHAND

        })

        "tool_selection_freehand" -> ({ 

             if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) confirmTransform()

             selectTool(ToolType.SELECTION)

             currentSelectionMode = SelectionMode.FREEHAND 

        })

        "tool_selection_rect" -> ({ 

             if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) confirmTransform()

             selectTool(ToolType.SELECTION)

             currentSelectionMode = SelectionMode.RECTANGLE 

        })

        "tool_selection_polygon" -> ({ 

             if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) confirmTransform()

             selectTool(ToolType.SELECTION)

             currentSelectionMode = SelectionMode.POLYGON 

        })

        "tool_transform" -> ({ 

             selectTool(ToolType.SELECTION)

             enterTransformMode()

        })

        "context_deselect" -> ({ clearSelection() })

        "context_lock_scale" -> ({ toggleScaleLock() })

        "context_delete" -> ({ deleteSelection() })

        "context_copy" -> ({ duplicateSelection() })

        "context_transform" -> ({ 

             enterTransformMode()

        })

        "context_flip_horizontal" -> ({ flipHorizontal() })

        "context_flip_vertical" -> ({ flipVertical() })

        "context_edit_image" -> ({ startEditingSelectedImage() })

        "context_group" -> ({ groupSelection() })

        "context_component" -> ({ makeComponent() })

        "context_ungroup" -> ({ ungroupSelection() })

        "context_make_unique" -> ({ makeComponentUnique() })

        "context_edit" -> ({ enterEditMode() })

        else -> ({})
    }
}







    var toolbarAlpha by mutableStateOf(prefs.getFloat("toolbar_alpha", 0.9f))

    fun updateToolbarAlpha(alpha: Float) {

        toolbarAlpha = alpha

        prefs.edit().putFloat("toolbar_alpha", alpha).apply()

    }

    

    var isToolbarBlurEnabled by mutableStateOf(prefs.getBoolean("toolbar_blur_enabled", false))

    fun toggleToolbarBlur() {

        isToolbarBlurEnabled = !isToolbarBlurEnabled

        prefs.edit().putBoolean("toolbar_blur_enabled", isToolbarBlurEnabled).apply()

    }



    fun toggleRotationLock() { isRotationLocked = !isRotationLocked; prefs.edit().putBoolean("rotation_lock", isRotationLocked).apply() }

    fun togglePalmRejection() { isPalmRejectionEnabled = !isPalmRejectionEnabled; prefs.edit().putBoolean("palm_rejection", isPalmRejectionEnabled).apply() }

    fun toggleTooltips() { showTooltips = !showTooltips; prefs.edit().putBoolean("show_tooltips", showTooltips).apply() }



    fun reloadToolbarLayout() {

        toolbarManager.reloadToolbarLayout()

    }



    fun reloadPreferences() {

        showPerformanceStats = prefs.getBoolean("show_performance_stats", false)

        showPublicLibrary = prefs.getBoolean("show_public_library", false)

        showExperimentalTools = prefs.getBoolean("show_experimental_tools", false)
        _showExperimentalTools.value = showExperimentalTools
        ToolRegistry.showExperimental = showExperimentalTools
        if (!showExperimentalTools) {
            val isCurrentToolExperimental = when (currentTool) {
                ToolType.WATERCOLOR, ToolType.PENCIL_CUMULATIVE -> true
                else -> false
            }
            if (isCurrentToolExperimental) {
                selectTool(ToolType.FREEHAND)
            }
        }

        isRotationLocked = prefs.getBoolean("rotation_lock", false)

        isPalmRejectionEnabled = prefs.getBoolean("palm_rejection", detectStylusSupport(getApplication<Application>()))

        showTooltips = prefs.getBoolean("show_tooltips", true)

        swapVertical = prefs.getBoolean("swap_vertical", false)

        swapHorizontal = prefs.getBoolean("swap_horizontal", false)

        interfaceScale = prefs.getFloat("interface_scale", 0.8f)

        buttonSpacingFactor = prefs.getFloat("button_spacing_factor", 1.0f)

        toolbarBackgroundColor = getSafeInt("toolbar_background_color", AndroidColor.WHITE)

        toolbarAlpha = prefs.getFloat("toolbar_alpha", 0.9f)

        isToolbarBlurEnabled = prefs.getBoolean("toolbar_blur_enabled", false)



        // Reload Theme Config

        _themeConfig.value = themeRepository.getTheme()



        // Reload Toolbar Layout

        reloadToolbarLayout()



        // Reload Tool configs

        toolManager.reloadConfigs()

    }



    fun backupPreferences() {

        val context = getApplication<Application>()

        val prefsActive = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)

        val themeActive = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)

        val toolbarActive = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)



        val prefsBackup = context.getSharedPreferences("sketcher_prefs_backup", Context.MODE_PRIVATE)

        val themeBackup = context.getSharedPreferences("app_theme_backup", Context.MODE_PRIVATE)

        val toolbarBackup = context.getSharedPreferences("toolbar_prefs_backup", Context.MODE_PRIVATE)



        copySharedPreferences(prefsActive, prefsBackup)

        copySharedPreferences(themeActive, themeBackup)

        copySharedPreferences(toolbarActive, toolbarBackup)



        hasPreferencesBackup = true

        android.widget.Toast.makeText(context, "Preferencias guardadas", android.widget.Toast.LENGTH_SHORT).show()

    }



    fun restorePreferences() {

        val context = getApplication<Application>()

        val prefsActive = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)

        val themeActive = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)

        val toolbarActive = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)



        val prefsBackup = context.getSharedPreferences("sketcher_prefs_backup", Context.MODE_PRIVATE)

        val themeBackup = context.getSharedPreferences("app_theme_backup", Context.MODE_PRIVATE)

        val toolbarBackup = context.getSharedPreferences("toolbar_prefs_backup", Context.MODE_PRIVATE)



        if (prefsBackup.all.isNotEmpty()) {

            copySharedPreferences(prefsBackup, prefsActive)

            copySharedPreferences(themeBackup, themeActive)

            copySharedPreferences(toolbarBackup, toolbarActive)



            reloadPreferences()

            android.widget.Toast.makeText(context, "Preferencias restauradas", android.widget.Toast.LENGTH_SHORT).show()

        }

    }



    fun resetPreferencesToDefault() {

        val context = getApplication<Application>()

        context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE).edit().clear().commit()

        context.getSharedPreferences("app_theme", Context.MODE_PRIVATE).edit().clear().commit()

        context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE).edit().clear().commit()



        reloadPreferences()

        android.widget.Toast.makeText(context, "Preferencias restablecidas a valores por defecto", android.widget.Toast.LENGTH_SHORT).show()

    }



    private fun copySharedPreferences(source: android.content.SharedPreferences, dest: android.content.SharedPreferences) {

        val editor = dest.edit()

        editor.clear()

        for ((key, value) in source.all) {

            when (value) {

                is Boolean -> editor.putBoolean(key, value)

                is Float -> editor.putFloat(key, value)

                is Int -> editor.putInt(key, value)

                is Long -> editor.putLong(key, value)

                is String -> editor.putString(key, value)

                is Set<*> -> {

                    @Suppress("UNCHECKED_CAST")

                    editor.putStringSet(key, value as Set<String>)

                }

            }

        }

        editor.commit()

    }

    

    fun updateScaleConfig(u: String, b: Float) { scaleConfig = scaleConfig.copy(unitName = u, basePixelsPerMillimeter = b); currentUnit = DistanceUnit.fromSymbol(u) }
    fun updateGlobalScaleRatio(ratio: Float) { scaleConfig = scaleConfig.copy(globalScaleRatio = ratio) }

    fun updateGridConfig(v: Boolean, s: Float, c: Int, c2: Int, c3: Int) { gridConfig = GridConfig(v, s, c, c2, c3) }

    fun setUnit(u: DistanceUnit) { currentUnit = u; scaleConfig = scaleConfig.copy(unitName = u.symbol) }

    fun updateCanvasSize(config: CanvasSizeConfig?) {

        canvasSizeConfig = config

        if (config != null && lastViewportWidth > 0f && lastViewportHeight > 0f) {

            centerPaperAsHomeCamera()

        }

    }



    // --- COROUTINE HELPERS ---

    fun launchIO(block: suspend CoroutineScope.() -> Unit) = viewModelScope.launch(Dispatchers.IO) {

        block()

    }



    fun launchDefault(block: suspend CoroutineScope.() -> Unit) = viewModelScope.launch(Dispatchers.Default) {

        block()

    }



    // PROJECT METADATA

    var projectId by mutableStateOf(UUID.randomUUID().toString())

    val pages = mutableStateListOf<CanvasPage>().apply {
        add(CanvasPage(
            id = java.util.UUID.randomUUID().toString(),
            name = "Página 1",
            layers = mutableStateListOf(Layer("l_${System.currentTimeMillis()}", "Capa 1", mutableStateListOf())),
            activeLayerIndex = 0,
            backgroundColor = android.graphics.Color.WHITE,
            backgroundStyle = FillStyle.Solid(android.graphics.Color.WHITE),
            gridConfig = GridConfig(),
            canvasSizeConfig = null,
            cameraMatrixValues = FloatArray(9).apply { Matrix().getValues(this) },
            scaleConfig = ScaleConfig(),
            currentUnit = DistanceUnit.MM
        ))
    }

    var activePageIndex by mutableIntStateOf(0)
        private set

    var currentFileUri: android.net.Uri? by mutableStateOf(null)

    var hasUnsavedChanges: Boolean = false
    var hasUnsavedChangesSinceLastAutosave: Boolean = false

    private var lastInteractionTime = System.currentTimeMillis()
    private var lastAutosaveTime = System.currentTimeMillis()
    private val AUTOSAVE_INTERVAL_MS = 120_000L // 2 minutes

    fun registerUserInteraction() {
        lastInteractionTime = System.currentTimeMillis()
    }



    // --- THEME ENGINE ---

    private val _themeConfig = MutableStateFlow(themeRepository.getTheme())

    val themeConfig: StateFlow<UiThemeConfig> = _themeConfig.asStateFlow()



    fun updateTheme(newConfig: UiThemeConfig) {
        _themeConfig.value = newConfig
        themeRepository.saveTheme(newConfig)
    }

    // --- COMPONENTS & ISOLATION ---
    val componentLibrary = mutableMapOf<String, ComponentDefinition>()
    
    var editingContext by mutableStateOf<MutableList<LayerElement>?>(null)
        private set

    val activeContainer: MutableList<LayerElement>
        get() = editingContext ?: layerManager.activeElements()

    var editingContainerMatrix by mutableStateOf<Matrix?>(null)
        private set

    var editingParent by mutableStateOf<LayerElement?>(null)
        private set

    private var editingBackupElements: List<LayerElement>? = null

    var lastExportPngConfig by mutableStateOf(ExportPngConfig(transparentBackground = false, useHomeView = true, width = 1920, height = 1080))
    var lastExportSvgConfig by mutableStateOf(ExportSvgConfig(includeBackground = true, useHomeView = true, width = 1920f, height = 1080f))
    var dxfExportConfig by mutableStateOf(DxfExportConfig("", false))

    // --- TOOL STATE & CONFIG (Delegated to ToolManager) ---
    val currentTool: ToolType get() = toolManager.currentTool
    val currentStrokeType: StrokeType get() = toolManager.currentStrokeType
    
    val brushSize = toolManager.brushSize
    val brushOpacity = toolManager.brushOpacity
    val currentSize: Float get() = toolManager.currentSize
    val currentOpacity: Float get() = toolManager.currentOpacity
    val currentFreehandSettings: com.sketcher.sketchercompanionv1.tools.ToolSettings get() = toolManager.currentFreehandSettings

    val strokeColor = toolManager.strokeColor
    val strokeStyle = toolManager.strokeStyle
    val fillColor = toolManager.fillColor
    val fillStyle = toolManager.fillStyle
    val fillOpacity = toolManager.fillOpacity
    val isStrokeActive = toolManager.isStrokeActive
    val isFillActive = toolManager.isFillActive
    val isStrokeColorPreset = toolManager.isStrokeColorPreset
    val isFillColorPreset = toolManager.isFillColorPreset

    var isGeometricStrokeInProgress by mutableStateOf(false)
        private set

    fun updateGeometricStrokeInProgress(inProgress: Boolean) {
        isGeometricStrokeInProgress = inProgress
    }

    fun updateBrushSize(newSize: Float) = toolManager.updateBrushSize(newSize)
    fun updateBrushOpacity(newAlpha: Float) {
        toolManager.updateBrushOpacity(newAlpha)
        updateLastActiveToolOpacity(newAlpha)
    }
    fun updateFillOpacity(opacity: Float) = toolManager.updateFillOpacity(opacity)
    fun updateStrokeType(type: StrokeType) = toolManager.updateStrokeType(type)
    
    val currentEraserShape: com.sketcher.sketchercompanionv1.dto.EraserShape
        get() = toolManager.currentEraserShape

    fun setEraserShape(shape: com.sketcher.sketchercompanionv1.dto.EraserShape) {
        toolManager.setEraserShape(shape)
    }

    fun setStrokeColor(color: Int) {
        toolManager.setStrokeColor(color)
        if (isSingleTextSelected) {
            updateSelectedTextProperty("Cambiar Color de Texto") {
                it.copy(defaultTextColor = color)
            }
        }
    }
    fun setStrokeStyle(style: FillStyle) = toolManager.setStrokeStyle(style)
    fun setFillColor(color: Int) = toolManager.setFillColor(color)
    fun setFillStyle(style: FillStyle) = toolManager.setFillStyle(style)
    fun toggleStroke(enabled: Boolean) = toolManager.toggleStroke(enabled)
    fun toggleFill(enabled: Boolean) = toolManager.toggleFill(enabled)
    
    fun selectTool(type: ToolType) {
        if (currentTool == ToolType.SELECTION && type != ToolType.SELECTION) {
            if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
                confirmTransform()
            }
        }
        val prevCustomId = activeCustomToolId
        if (prevCustomId != null) {
            toolManager.reloadToolConfigAndSettings(currentTool)
        }
        activeCustomToolId = null
        toolManager.selectTool(type)
    }

    fun saveActiveCustomToolChanges() {
        val customId = activeCustomToolId ?: return
        toolManager.saveActiveCustomToolChanges(customId)
    }

    fun revertCustomToolChanges() {
        val customId = activeCustomToolId ?: return
        toolManager.revertCustomToolChanges(customId)
    }

    fun isCustomToolModified(customId: String): Boolean {
        return toolManager.isCustomToolModified(customId)
    }

    val brushPresets = toolManager.brushPresets
    val selectedPresetIndex = toolManager.selectedPresetIndex
    fun saveBrushPreset(index: Int) = toolManager.saveBrushPreset(index)
    fun selectBrushPreset(index: Int) {
        toolManager.selectBrushPreset(index)
        val defaultStab = if (toolManager.currentTool == ToolType.FREEHAND || toolManager.currentTool == ToolType.PENCIL_CUMULATIVE) 0.07f else 0f
        val list = toolManager.brushPresets.value
        if (index in list.indices) {
            val preset = list[index]
            val presetStab = preset.stabilization ?: defaultStab
            val presetOpacity = preset.opacity
            updateLastActiveToolStabilization(presetStab)
            updateLastActiveToolOpacity(presetOpacity)
        }
    }
    fun isPresetModified(index: Int): Boolean = toolManager.isPresetModified(index)
    fun revertToPresetColor(isStroke: Boolean) {
        toolManager.revertToPresetColor(isStroke)
    }
    val fillPresets = toolManager.fillPresets
    fun saveFillPreset(index: Int, style: FillStyle) = toolManager.saveFillPreset(index, style)

    val customTools = toolManager.customTools
    fun addCustomTool(ct: CustomTool) = toolManager.addCustomTool(ct)
    fun updateCustomTool(ct: CustomTool) = toolManager.updateCustomTool(ct)
    fun removeCustomTool(id: String) {
        toolManager.removeCustomTool(id)
        if (activeCustomToolId == id) {
            activeCustomToolId = null
        }
    }

    fun updateActiveCustomTool() {
        val id = activeCustomToolId ?: return
        val currentCt = customTools.value.find { it.id == id } ?: return
        
        val newSettings = toolManager.getToolConfigMap()[currentTool]?.settings
        
        val updatedCt = currentCt.copy(
            preset = BrushPreset(
                size = brushSize.value,
                opacity = brushOpacity.value,
                settings = newSettings ?: com.sketcher.sketchercompanionv1.tools.PencilSettings(),
                strokeColor = strokeColor.value,
                fillColor = fillColor.value,
                isStrokeActive = isStrokeActive.value,
                isFillActive = isFillActive.value,
                fillStyle = fillStyle.value,
                strokeStyle = strokeStyle.value,
                stabilization = toolManager.globalStabilizationLevel
            )
        )
        toolManager.updateCustomTool(updatedCt)
    }

    // --- EXPOSED CONFIGS ---
    var fingerModeActive by mutableStateOf(false)
        private set
    var fingerOffsetXValue by mutableFloatStateOf(0f)
        private set
    var fingerOffsetYValue by mutableFloatStateOf(50f)
        private set

    var hasPreferencesBackup by mutableStateOf(false)
        private set

    init {
        ToolRegistry.showExperimental = showExperimentalTools
        hasPreferencesBackup = application.getSharedPreferences("sketcher_prefs_backup", Context.MODE_PRIVATE).all.isNotEmpty()
        
        toolManager.onCustomToolAddedOrUpdated = { ct ->
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val gson = com.google.gson.Gson()
                    val jsonObj = com.sketcher.sketchercompanionv1.dto.CustomToolJson(
                        id = ct.id,
                        name = ct.name,
                        iconName = ct.iconName,
                        iconResName = ct.iconResName,
                        baseToolType = ct.baseToolType.name,
                        preset = com.sketcher.sketchercompanionv1.dto.BrushPresetJson(
                            size = ct.preset.size,
                            opacity = ct.preset.opacity,
                            settingsType = ct.preset.settings::class.java.simpleName,
                            settingsJson = gson.toJson(ct.preset.settings),
                            strokeColor = ct.preset.strokeColor,
                            fillColor = ct.preset.fillColor,
                            isStrokeActive = ct.preset.isStrokeActive,
                            isFillActive = ct.preset.isFillActive,
                            fillStyle = ct.preset.fillStyle?.toFillStyleJson(),
                            strokeStyle = ct.preset.strokeStyle?.toFillStyleJson(),
                            stabilization = ct.preset.stabilization
                        ),
                        customIconJson = ct.customIconJson
                    )
                    val jsonStr = gson.toJson(jsonObj)
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    val map: Map<String, Any> = gson.fromJson(jsonStr, type)
                    cloudSyncRepository.syncCustomBrush(ct.id, map)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        
        toolManager.onCustomToolRemoved = { id ->
            toolbarManager.removeToolFromAllLayouts(id)
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                cloudSyncRepository.syncCustomBrush(id, null)
            }
        }
        
        toolManager.loadCustomTools()
        selectTool(currentTool)
        
        fetchUiPresetsCloud()
        
        toolbarManager.initLayout()

        // Periodic background autosave (runs every 2 minutes if there are changes and user is idle)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                val now = System.currentTimeMillis()
                if (hasUnsavedChangesSinceLastAutosave &&
                    (now - lastAutosaveTime >= AUTOSAVE_INTERVAL_MS) &&
                    (now - lastInteractionTime >= 3_000)) {
                    autoSaveProject(application)
                }
            }
        }
    }

    // --- UI PRESETS CLOUD SYNC ---

    fun resetDefaultUiPreset() {
        toolbarManager.resetDefaultUiPreset()
    }
    fun saveUiPresetCloud(name: String) {
        toolbarManager.saveUiPreset(name)
        val json = toolbarManager.getUiPresetJson(name)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            cloudSyncRepository.syncUiPreset(name, json)
        }
    }

    fun renameUiPresetCloud(oldName: String, newName: String) {
        toolbarManager.renameUiPreset(oldName, newName)
        val newJson = toolbarManager.getUiPresetJson(newName)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            cloudSyncRepository.syncUiPreset(oldName, null)
            cloudSyncRepository.syncUiPreset(newName, newJson)
        }
    }

    fun copyUiPresetCloud(oldName: String, newName: String) {
        toolbarManager.copyUiPreset(oldName, newName)
        val newJson = toolbarManager.getUiPresetJson(newName)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            cloudSyncRepository.syncUiPreset(newName, newJson)
        }
    }

    fun deleteUiPresetCloud(name: String) {
        toolbarManager.deleteUiPreset(name)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            cloudSyncRepository.syncUiPreset(name, null)
        }
    }

    private fun fetchUiPresetsCloud() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = cloudSyncRepository.getAllUiPresets()
            if (result.isSuccess) {
                val presets = result.getOrNull() ?: emptyList()
                presets.forEach { preset ->
                    val name = preset["name"] as? String
                    val data = preset["data"] as? String
                    if (name != null && data != null && name != "Default") {
                        toolbarManager.importUiPreset(name, data)
                    }
                }
            }
        }
    }

    fun updateContextualToolbar(newList: List<StudioTool>) {
        toolbarManager.updateContextualToolbar(newList)
    }

    fun setFingerMode(enabled: Boolean) {
        fingerModeActive = enabled
        toolManager.setFingerMode(enabled)
    }

    fun setFingerOffset(x: Float, y: Float) {
        fingerOffsetXValue = x
        fingerOffsetYValue = y
        toolManager.setFingerOffset(x, y)
    }

    // --- SELECTION STATE ---
    enum class SelectionMode { RECTANGLE, FREEHAND, POLYGON, TRANSFORM_BOX }
    enum class SelectionScope { CURRENT_LAYER, ALL_LAYERS }
    var currentSelectionMode by mutableStateOf(SelectionMode.RECTANGLE)
    var selectionScope by mutableStateOf(SelectionScope.CURRENT_LAYER)
    var isSelectionAspectRatioLocked by mutableStateOf(true)

    val isGroupSelected: Boolean
        get() = selectionManager.selectedElements.size == 1 && (selectionManager.selectedElements.first() is GroupElement)

    val canEnterEditMode: Boolean
        get() = selectionManager.selectedElements.size == 1 && (selectionManager.selectedElements.first() is GroupElement || selectionManager.selectedElements.first() is ComponentInstance)

    val isSelectionEmpty: Boolean get() = selectionManager.selectedElements.isEmpty()
    val hasSelection: Boolean get() = selectionManager.hasSelection.value
    val selectionCount: Int get() = selectionManager.selectionCount.value

    fun clearSelection() {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
            confirmTransform()
        }
        selectionManager.clearSelection()
    }

    fun deleteSelection() {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
            confirmTransform()
        }
        val selected = selectionManager.selectedElements.toList()
        if (selected.isEmpty()) return
        
        performSnapshotAction("Borrar Seleccion") {
            if (editingContext != null) {
                activeContainer.removeAll(selected)
            } else {
                val currentLayers = layers.toMutableList()
                currentLayers.forEachIndexed { index, layer ->
                    val remaining = layer.elements.filter { it !in selected }
                    if (remaining.size != layer.elements.size) {
                        currentLayers[index] = layer.copy(elements = remaining.toMutableStateList())
                    }
                }
                layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            }
            selectionManager.clearSelection()
        }
    }

    fun duplicateSelection() {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
            confirmTransform()
        }
        val selected = selectionManager.selectedElements.toList()
        if (selected.isEmpty()) return
        
        performSnapshotAction("Duplicar Seleccion") {
            val offsetMatrix = Matrix().apply { postTranslate(20f, 20f) }
            val duplicatedElements = selected.map { 
                val copy = it.copyElement()
                if (copy is Transformable) copy.transform(offsetMatrix)
                copy
            }
            activeContainer.addAll(duplicatedElements)
            
            if (editingContext == null) {
                val currentLayers = layers.toMutableList()
                val activeLayer = currentLayers[activeLayerIndex]
                currentLayers[activeLayerIndex] = activeLayer.copy(
                    elements = activeLayer.elements
                )
                layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            } else {
                notifyLayersChanged()
            }
            
            selectionManager.selectedElements.clear()
            selectionManager.selectedElements.addAll(duplicatedElements)
            selectionManager.selectionMatrix.reset()
            selectionManager.recalculateBaseBounds(componentLibrary)
        }
        enterTransformMode()
    }

    fun flipHorizontal() {
        if (selectionManager.selectedElements.isEmpty()) return
        if (currentSelectionMode != SelectionMode.TRANSFORM_BOX) {
            enterTransformMode()
        }
        selectionManager.flipHorizontal()
        notifyLayersChanged()
    }

    fun flipVertical() {
        if (selectionManager.selectedElements.isEmpty()) return
        if (currentSelectionMode != SelectionMode.TRANSFORM_BOX) {
            enterTransformMode()
        }
        selectionManager.flipVertical()
        notifyLayersChanged()
    }

    private var layersSnapshotBeforeTransform: List<Layer>? = null

    fun enterTransformMode() {
        if (selectionManager.selectedElements.isEmpty()) return
        selectionManager.recalculateBaseBounds(componentLibrary)
        selectTool(ToolType.SELECTION)
        layersSnapshotBeforeTransform = createLayersSnapshot()
        selectionManager.backupOriginalElements()
        currentSelectionMode = SelectionMode.TRANSFORM_BOX
    }

    fun toggleScaleLock() {
        selectionManager.toggleScaleLock()
        notifyLayersChanged()
    }

    fun confirmTransform() {
        selectionManager.commitTransformSession(activeContainer, componentLibrary)
        val before = layersSnapshotBeforeTransform
        if (before != null) {
            val activeIndexBefore = activeLayerIndex
            val after = createLayersSnapshot()
            performAction(SnapshotCommand("Transformar", before, after, activeIndexBefore))
        }
        layersSnapshotBeforeTransform = null
        selectionManager.clearBackup()
        currentSelectionMode = SelectionMode.FREEHAND
    }

    fun cancelTransform() {
        selectionManager.cancelTransformSession(activeContainer, componentLibrary)
        layersSnapshotBeforeTransform = null
        selectionManager.clearBackup()
        currentSelectionMode = SelectionMode.FREEHAND
        notifyLayersChanged()
    }

    fun makeComponent() {
        if (selectionManager.selectedElements.isEmpty()) return
        
        performSnapshotAction("Crear Componente") {
            val elementsToComponent = selectionManager.selectedElements.toList()
            val defId = "comp_" + java.util.UUID.randomUUID().toString()
            val definition = ComponentDefinition(defId, elementsToComponent.map { it.copyElement() }.toMutableList(), creationScale = scaleConfig.globalScaleRatio)
            componentLibrary[defId] = definition
            
            layers.forEach { layer ->
                layer.elements.removeAll(elementsToComponent)
            }
            if (editingContext != null) {
                activeContainer.removeAll(elementsToComponent)
            }
            
            val instance = ComponentInstance(
                id = "inst_" + java.util.UUID.randomUUID().toString(),
                definitionId = defId
            )
            activeContainer.add(instance)
            
            selectionManager.clearSelection()
            if (editingContext == null) {
                val newList = layers.toMutableList()
                // Must recreate the entire list because elements could have been removed from multiple layers
                for (i in newList.indices) {
                    newList[i] = newList[i].copy()
                }
                layerManager.internalUpdateLayers(newList, activeLayerIndex)
            }
        }
    }

    fun enterEditMode() {
        if (selectionManager.selectedElements.size != 1) return
        val selected = selectionManager.selectedElements.first()
        
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
            confirmTransform()
        }
        
        if (selected is GroupElement) {
            editingContext = selected.elements.map { it.copyElement() }.toMutableList()
            editingBackupElements = selected.elements.map { it.copyElement() }
            editingParent = selected
            editingContainerMatrix = Matrix(selected.matrix)
            selectionManager.clearSelection()
            currentSelectionMode = SelectionMode.FREEHAND
            notifyLayersChanged()
        } else if (selected is ComponentInstance) {
            val definition = componentLibrary[selected.definitionId]
            if (definition != null) {
                editingContext = definition.elements
                editingBackupElements = definition.elements.map { it.copyElement() }
                editingParent = selected
                editingContainerMatrix = Matrix(selected.matrix)
                selectionManager.clearSelection()
                currentSelectionMode = SelectionMode.FREEHAND
                notifyLayersChanged()
            }
        }
    }

    fun exitEditMode() { 
        editingContext = null
        editingParent = null
        editingContainerMatrix = null
        editingBackupElements = null
        selectionManager.clearSelection()
        notifyLayersChanged()
    }

    fun cancelComponentEdit() {
        val backup = editingBackupElements
        val parent = editingParent
        if (backup != null && parent != null) {
            if (parent is GroupElement) {
                // Not saving changes
            } else if (parent is ComponentInstance) {
                val definition = componentLibrary[parent.definitionId]
                if (definition != null) {
                    definition.elements.clear()
                    definition.elements.addAll(backup)
                }
            }
        }
        editingBackupElements = null
        exitEditMode()
    }

    fun confirmComponentEdit() {
        editingBackupElements = null
        val parent = editingParent
        val ctx = editingContext
        if (parent is GroupElement && ctx != null) {
            val oldGroup = parent
            val newGroup = GroupElement(oldGroup.id, ctx.toMutableList(), oldGroup.matrix)
            // Replace in container
            val idx = activeContainer.indexOfFirst { it === oldGroup }
            if (idx != -1) {
                activeContainer[idx] = newGroup
            } else {
                // Should not happen normally
            }
        }
        exitEditMode()
    }

    fun groupSelection() {

        val selected = selectionManager.selectedElements.toList()

        if (selected.isEmpty()) return

        

        performSnapshotAction("Agrupar") {

            val group = GroupElement(

                id = UUID.randomUUID().toString(),

                elements = selected.toMutableList(),

                matrix = Matrix()

            )

            

            activeContainer.removeAll(selected)

            activeContainer.add(group)

            

            selectionManager.clearSelection()

            selectionManager.selectedElements.add(group)

            selectionManager.recalculateBaseBounds(componentLibrary)

            

            if (editingContext == null) {

                val newList = layers.toMutableList()

                newList[activeLayerIndex] = newList[activeLayerIndex].copy()

                layerManager.internalUpdateLayers(newList, activeLayerIndex)

            }

            notifyLayersChanged()

        }

    }



    fun ungroupSelection() {

        val selected = selectionManager.selectedElements.toList()

        val groupsAndInstances = selected.filter { it is GroupElement || it is ComponentInstance }

        if (groupsAndInstances.isEmpty()) return

        

        performSnapshotAction("Desagrupar") {

            groupsAndInstances.forEach { item ->

                val idx = activeContainer.indexOfFirst { it === item }
                if (idx != -1) {
                    activeContainer.removeAt(idx)

                    

                    val children = when (item) {

                        is GroupElement -> {

                            item.elements.map { child ->

                                val copy = child.copyElement()

                                if (copy is com.sketcher.sketchercompanionv1.Transformable) {

                                    copy.transform(item.matrix)

                                }

                                copy

                            }

                        }

                        is ComponentInstance -> {

                            val definition = componentLibrary[item.definitionId]

                            definition?.elements?.map { child ->

                                val copy = child.copyElement()

                                if (copy is com.sketcher.sketchercompanionv1.Transformable) {

                                    copy.transform(item.matrix)

                                }

                                copy

                            } ?: emptyList()

                        }

                        else -> emptyList()

                    }

                    

                    activeContainer.addAll(children)

                }

            }

            

            selectionManager.clearSelection()

            if (editingContext == null) {

                val newList = layers.toMutableList()

                newList[activeLayerIndex] = newList[activeLayerIndex].copy()

                layerManager.internalUpdateLayers(newList, activeLayerIndex)

            }

            notifyLayersChanged()

        }

    }



    fun makeComponentUnique() {

        val selected = selectionManager.selectedElements.toList()

        if (selected.size != 1) return

        val instance = selected.first() as? ComponentInstance ?: return

        

        val oldDef = componentLibrary[instance.definitionId] ?: return

        

        performSnapshotAction("Hacer Único") {

            val newDefId = "comp_${UUID.randomUUID()}"

            val copiedElements = oldDef.elements.map { it.copyElement() }.toMutableList()

            val newDef = ComponentDefinition(newDefId, copiedElements, creationScale = scaleConfig.globalScaleRatio)

            componentLibrary[newDefId] = newDef

            

            val idx = activeContainer.indexOfFirst { it === instance }

            if (idx != -1) {

                val updatedInstance = instance.copy(definitionId = newDefId)

                activeContainer[idx] = updatedInstance

                

                selectionManager.clearSelection()

                selectionManager.selectedElements.add(updatedInstance)

                selectionManager.recalculateBaseBounds(componentLibrary)

            }

            

            if (editingContext == null) {

                val newList = layers.toMutableList()

                newList[activeLayerIndex] = newList[activeLayerIndex].copy()

                layerManager.internalUpdateLayers(newList, activeLayerIndex)

            }

            notifyLayersChanged()

        }

    }



    // --- CLIPBOARD ---

    private val clipboard = mutableListOf<LayerElement>()

    var canPaste by mutableStateOf(false)

        private set



    fun copy() {

        if (selectionManager.selectedElements.isEmpty()) return

        clipboard.clear()

        clipboard.addAll(selectionManager.selectedElements.map { it.copyElement() })

        canPaste = true

    }



    fun cut() {

        if (selectionManager.selectedElements.isEmpty()) return

        copy()

        deleteSelection()

    }



    fun paste() {

        if (clipboard.isEmpty()) return

        

        performSnapshotAction("Pegar") {

            if (layers.isEmpty()) return@performSnapshotAction

            

            val offset = 50f 

            val m = Matrix()

            m.postTranslate(offset, offset)

            

            val pasted = clipboard.map { 

                it.copyElement().apply { 

                    if (this is com.sketcher.sketchercompanionv1.Transformable) transform(m)

                } 

            }

            

            activeContainer.addAll(pasted)

            

            if (editingContext == null) {

                val currentLayers = layers.toMutableList()

                val activeLayer = currentLayers[activeLayerIndex]

                currentLayers[activeLayerIndex] = activeLayer.copy(

                    elements = activeLayer.elements

                )

                layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)

            } else {

                notifyLayersChanged()

            }

            

            selectionManager.clearSelection()

            selectionManager.selectedElements.addAll(pasted)

            selectionManager.recalculateBaseBounds(componentLibrary)

        }

    }



    // --- GLOBAL STABILIZER ---

    val globalStabilization = toolManager.smoothing

    fun updateGlobalStabilization(value: Float) {
        toolManager.setGlobalStabilization(value)
        updateLastActiveToolStabilization(value)
    }

    var globalStabilizationLevel: Float
        get() = toolManager.globalStabilizationLevel
        set(value) {
            toolManager.setGlobalStabilization(value)
            updateLastActiveToolStabilization(value)
        }

    fun setGlobalStabilization(level: Float) {
        toolManager.setGlobalStabilization(level)
        updateLastActiveToolStabilization(level)
    }



    fun updateFreehandSettings(newSettings: com.sketcher.sketchercompanionv1.tools.ToolSettings) {
        toolManager.updateFreehandSettings(newSettings)
    }

    fun setFreehandSmoothing(value: Float) {
        val curr = currentFreehandSettings
        when (curr) {
            is com.sketcher.sketchercompanionv1.tools.PencilSettings -> {
                if (curr.smoothing != value) {
                    updateFreehandSettings(curr.copy(smoothing = value))
                }
            }
            is com.sketcher.sketchercompanionv1.tools.PenSettings -> {
                if (curr.smoothing != value) {
                    updateFreehandSettings(curr.copy(smoothing = value))
                }
            }
            is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> {
                if (curr.smoothing != value) {
                    updateFreehandSettings(curr.copy(smoothing = value))
                }
            }
            is com.sketcher.sketchercompanionv1.tools.PaintSettings -> {
                if (curr.smoothing != value) {
                    updateFreehandSettings(curr.copy(smoothing = value))
                }
            }
            is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> {
                if (curr.smoothing != value) {
                    updateFreehandSettings(curr.copy(smoothing = value))
                }
            }
        }
    }

    fun setFreehandTolerance(value: Float) {
        val curr = currentFreehandSettings
        if (curr is com.sketcher.sketchercompanionv1.tools.PencilSettings) {
            if (curr.simplificationTolerance != value) {
                val enabled = value > 0f
                updateFreehandSettings(curr.copy(
                    simplificationTolerance = value,
                    isSimplificationEnabled = enabled
                ))
            }
        }
    }

    fun setFreehandPredictionLatency(ms: Float) {
        val curr = currentFreehandSettings
        if (curr is com.sketcher.sketchercompanionv1.tools.PencilSettings) {
            if (curr.predictionLatency != ms.toLong()) {
                updateFreehandSettings(curr.copy(predictionLatency = ms.toLong()))
            }
        }
    }

    fun setFreehandMinWidth(ratio: Float) {
        val curr = currentFreehandSettings
        if (curr is com.sketcher.sketchercompanionv1.tools.PencilSettings) {
            if (curr.minWidthRatio != ratio) {
                updateFreehandSettings(curr.copy(minWidthRatio = ratio))
            }
        }
    }





    



    

    



    val layerManager = com.sketcher.sketchercompanionv1.managers.LayerManager(

        performSnapshotAction = { label, action -> performSnapshotAction(label, action) }

    )



    val layers: androidx.compose.runtime.snapshots.SnapshotStateList<Layer> get() = layerManager.layers



    var activeLayerIndex: Int

        get() = layerManager.activeLayerIndex

        set(value) { layerManager.setActiveLayer(value) }

    

    // --- LAYERS ---

    fun toggleLayerVisibility(index: Int) = layerManager.toggleLayerVisibility(index)

    fun toggleLayerClientVisibility(index: Int) = layerManager.toggleLayerClientVisibility(index)

    fun setLayerOpacity(index: Int, opacity: Float) = layerManager.setLayerOpacity(index, opacity)

    fun setActiveLayer(index: Int) = layerManager.setActiveLayer(index)

    

    fun addLayer() = layerManager.addLayer()

    fun addNewLayer(toTop: Boolean) = layerManager.addNewLayer(toTop)

    fun removeLayer(index: Int) = layerManager.removeLayer(index)

    fun removeActiveLayer() = layerManager.removeActiveLayer()

    

    fun toggleLayerLock(index: Int) = layerManager.toggleLayerLock(index)

    fun renameLayer(index: Int, newName: String) = layerManager.renameLayer(index, newName)

    fun duplicateLayer(index: Int) = layerManager.duplicateLayer(index)

    

    fun moveLayer(fromIndex: Int, toIndex: Int) = layerManager.moveLayer(fromIndex, toIndex)

    fun moveLayerUp(index: Int) = layerManager.moveLayerUp(index)

    fun moveLayerDown(index: Int) = layerManager.moveLayerDown(index)

    fun addLayerAbove(index: Int) = layerManager.addLayerAbove(index)

    fun addLayerBelow(index: Int) = layerManager.addLayerBelow(index)

    

    fun mergeLayers(fromIndex: Int, toIndex: Int) = layerManager.mergeLayers(fromIndex, toIndex)

    fun mergeLayerUp(index: Int) = layerManager.mergeLayerUp(index)

    fun mergeLayerDown(index: Int) = layerManager.mergeLayerDown(index)



    // --- UNDO/REDO STACK ---

    private val undoStack = ArrayDeque<UndoCommand>()

    private val redoStack = ArrayDeque<UndoCommand>()

    

    /**

     * Executes a command and adds it to the undo stack. Must be called from the Main thread.

     */

    @MainThread

    fun performAction(command: UndoCommand) {

        command.execute()

        undoStack.push(command)

        if (undoStack.size > 100) undoStack.removeLast()

        redoStack.clear()

        updateUndoRedoSupport()

        notifyLayersChanged()

    }



    private var lastUndoTime = 0L

    private var lastRedoTime = 0L



    private fun updateUndoRedoSupport() {

        canUndo = undoStack.isNotEmpty()

        canRedo = redoStack.isNotEmpty()

    }



    fun trimElementAt(x: Float, y: Float) {

        val activeLayer = layers.getOrNull(activeLayerIndex) ?: return

        val visibleStrokes = activeContainer.filterIsInstance<VectorStroke>()

        

        var targetStroke: VectorStroke? = null

        for (stroke in visibleStrokes.asReversed()) {

            if (selectionManager.isHit(stroke, x, y, componentLibrary)) {

                targetStroke = stroke

                break

            }

        }

        

        if (targetStroke != null) {

            val remaining = com.sketcher.sketchercompanionv1.utils.GeometryUtils.trimStroke(

                targetStroke,

                visibleStrokes,

                x,

                y

            )

            if (remaining != null) {

                val cmd = ModifyElementsCommand(

                    targetContainer = activeContainer,

                    elementsToRemove = listOf(targetStroke),

                    elementsToAdd = remaining,

                    label = "Recortar Segmento"

                )

                performAction(cmd)

            }

        }

    }



    fun extendElementAt(x: Float, y: Float) {

        val activeLayer = layers.getOrNull(activeLayerIndex) ?: return

        val visibleStrokes = activeContainer.filterIsInstance<VectorStroke>()

        

        var targetStroke: VectorStroke? = null

        for (stroke in visibleStrokes.asReversed()) {

            if (selectionManager.isHit(stroke, x, y, componentLibrary)) {

                targetStroke = stroke

                break

            }

        }

        

        if (targetStroke != null) {

            val extended = com.sketcher.sketchercompanionv1.utils.GeometryUtils.extendStroke(

                targetStroke,

                visibleStrokes,

                x,

                y

            )

            if (extended != null) {

                val cmd = ModifyElementsCommand(

                    targetContainer = activeContainer,

                    elementsToRemove = listOf(targetStroke),

                    elementsToAdd = listOf(extended),

                    label = "Alargar Segmento"

                )

                performAction(cmd)

            }

        }

    }



    fun commitGripEdit(stroke: VectorStroke, oldPoints: List<StrokePoint>, newPoints: List<StrokePoint>) {

        val activeLayer = layers.getOrNull(activeLayerIndex) ?: return

        

        val isCad = stroke.strokeType != com.sketcher.sketchercompanionv1.dto.StrokeType.FREEHAND
        val isMeshBrush = stroke.brushType == "FREEHAND" || stroke.brushType == "PEN" || stroke.brushType == "PLUMA" || stroke.brushType == "PENCIL_CUMULATIVE" || stroke.brushType == "PAINT" || stroke.brushType == "WATERCOLOR"
        val isPaintOrWatercolor = stroke.brushType == "PAINT" || stroke.brushType == "WATERCOLOR"

        val updatedPath = if (isCad) {
            val centerline = com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(stroke.strokeType, newPoints)
            if (isMeshBrush) {
                val pm = android.graphics.PathMeasure(centerline, false)
                val densePoints = mutableListOf<StrokePoint>()
                val pos = FloatArray(2)
                val length = pm.length
                if (length > 0f) {
                    val steps = (length / 2f).toInt().coerceIn(10, 1000)
                    for (i in 0..steps) {
                        val distance = (i.toFloat() / steps) * length
                        pm.getPosTan(distance, pos, null)
                        densePoints.add(StrokePoint(pos[0], pos[1], 1.0f, 0L))
                    }
                }
                val meshPath = android.graphics.Path()
                if (densePoints.isNotEmpty()) {
                    val toolType = when (stroke.brushType) {
                        "PLUMA" -> com.sketcher.sketchercompanionv1.dto.ToolType.PLUMA
                        "PAINT" -> com.sketcher.sketchercompanionv1.dto.ToolType.PAINT
                        "WATERCOLOR" -> com.sketcher.sketchercompanionv1.dto.ToolType.WATERCOLOR
                        else -> com.sketcher.sketchercompanionv1.dto.ToolType.FREEHAND
                    }
                    val settings = toolManager.getToolConfigMap()[toolType]?.settings?.toFreehandSettings(toolType) ?: com.sketcher.sketchercompanionv1.dto.FreehandSettings()
                    com.sketcher.sketchercompanionv1.PerfectFreehandGenerator.generate(densePoints, settings.copy(size = stroke.maxWidth, isComplete = true), outPath = meshPath)
                }
                meshPath
            } else {
                centerline
            }
        } else {
            if (isMeshBrush) {
                if (stroke.isFlattened) {
                    val path = android.graphics.Path()
                    if (newPoints.isNotEmpty()) {
                        path.moveTo(newPoints[0].x, newPoints[0].y)
                        for (i in 1 until newPoints.size) {
                            path.lineTo(newPoints[i].x, newPoints[i].y)
                        }
                        path.close()
                    }
                    path
                } else {
                    val toolType = when (stroke.brushType) {
                        "PLUMA" -> com.sketcher.sketchercompanionv1.dto.ToolType.PLUMA
                        "PAINT" -> com.sketcher.sketchercompanionv1.dto.ToolType.PAINT
                        "WATERCOLOR" -> com.sketcher.sketchercompanionv1.dto.ToolType.WATERCOLOR
                        else -> com.sketcher.sketchercompanionv1.dto.ToolType.FREEHAND
                    }
                    val settings = toolManager.getToolConfigMap()[toolType]?.settings?.toFreehandSettings(toolType) ?: com.sketcher.sketchercompanionv1.dto.FreehandSettings()
                    com.sketcher.sketchercompanionv1.PerfectFreehandGenerator.generate(newPoints, settings.copy(size = stroke.maxWidth, isComplete = true)).path
                }
            } else {
                val path = android.graphics.Path()
                if (newPoints.isNotEmpty()) {
                    path.moveTo(newPoints[0].x, newPoints[0].y)
                    for (i in 1 until newPoints.size) {
                        path.lineTo(newPoints[i].x, newPoints[i].y)
                    }
                    path.close()
                }
                path
            }
        }

        val updatedFillPath = if (stroke.isFillEnabled) {
            if (isCad) {
                if (isPaintOrWatercolor) updatedPath else com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(stroke.strokeType, newPoints)
            } else {
                val path = android.graphics.Path()
                if (newPoints.isNotEmpty()) {
                    path.moveTo(newPoints[0].x, newPoints[0].y)
                    for (i in 1 until newPoints.size) {
                        path.lineTo(newPoints[i].x, newPoints[i].y)
                    }
                    path.close()
                }
                path
            }
        } else {
            null
        }

        val updatedStroke = stroke.copy(
            points = newPoints,
            path = updatedPath,
            fillPath = updatedFillPath
        )

        

        val cmd = ModifyElementsCommand(

            targetContainer = activeContainer,

            elementsToRemove = listOf(stroke),

            elementsToAdd = listOf(updatedStroke),

            label = "Edición de Puntos"

        )

        performAction(cmd)

        

        // Mantener el elemento editado seleccionado
        val idx = selectionManager.selectedElements.indexOfFirst { it === stroke }
        if (idx != -1) {
            selectionManager.selectedElements[idx] = updatedStroke
            selectionManager.recalculateBaseBounds(componentLibrary)
        }

    }

    

    /**

     * Performs an Undo operation. Must be called from the Main thread.

     */

    var isOrthoModeActive = androidx.compose.runtime.mutableStateOf(false)
        private set

    fun toggleOrthoMode() {
        isOrthoModeActive.value = !isOrthoModeActive.value
    }

    fun mirrorSelected(p1: android.graphics.PointF, p2: android.graphics.PointF) {
        if (selectionManager.selectedElements.isEmpty()) return
        val mirrored = selectionManager.selectedElements.map { element ->
            if (element is VectorStroke) {
                com.sketcher.sketchercompanionv1.utils.GeometryUtils.mirrorStroke(element, p1, p2)
            } else {
                element.copyElement()
            }
        }
        val cmd = com.sketcher.sketchercompanionv1.command.ModifyElementsCommand(
            targetContainer = activeContainer,
            elementsToRemove = emptyList(),
            elementsToAdd = mirrored,
            label = "Espejo Simetría"
        )
        performAction(cmd)
        selectionManager.clearSelection()
        selectionManager.selectedElements.addAll(mirrored)
        selectionManager.recalculateBaseBounds(componentLibrary)
    }

    fun transformSelectedElements(matrix: android.graphics.Matrix, label: String) {
        if (selectionManager.selectedElements.isEmpty()) return
        val transformed = selectionManager.selectedElements.map { element ->
            val copy = element.copyElement()
            copy.transform(matrix)
            copy
        }
        val cmd = com.sketcher.sketchercompanionv1.command.ModifyElementsCommand(
            targetContainer = activeContainer,
            elementsToRemove = selectionManager.selectedElements.toList(),
            elementsToAdd = transformed,
            label = label
        )
        performAction(cmd)
        selectionManager.clearSelection()
        selectionManager.selectedElements.addAll(transformed)
        selectionManager.recalculateBaseBounds(componentLibrary)
    }

    fun offsetStroke(target: VectorStroke, distance: Float, directionPoint: android.graphics.PointF) {
        val offsetted = com.sketcher.sketchercompanionv1.utils.GeometryUtils.offsetStroke(target, distance, directionPoint) ?: return
        val cmd = com.sketcher.sketchercompanionv1.command.ModifyElementsCommand(
            targetContainer = activeContainer,
            elementsToRemove = emptyList(),
            elementsToAdd = listOf(offsetted),
            label = "Desfase"
        )
        performAction(cmd)
    }

    fun filletStrokes(s1: VectorStroke, s2: VectorStroke, radius: Float) {
        val result = com.sketcher.sketchercompanionv1.utils.GeometryUtils.applyFillet(s1, s2, radius) ?: return
        val cmd = com.sketcher.sketchercompanionv1.command.ModifyElementsCommand(
            targetContainer = activeContainer,
            elementsToRemove = listOf(s1, s2),
            elementsToAdd = listOf(result.first, result.second, result.third),
            label = "Empalme"
        )
        performAction(cmd)
    }

    fun chamferStrokes(s1: VectorStroke, s2: VectorStroke, d1: Float, d2: Float) {
        val result = com.sketcher.sketchercompanionv1.utils.GeometryUtils.applyChamfer(s1, s2, d1, d2) ?: return
        val cmd = com.sketcher.sketchercompanionv1.command.ModifyElementsCommand(
            targetContainer = activeContainer,
            elementsToRemove = listOf(s1, s2),
            elementsToAdd = listOf(result.first, result.second, result.third),
            label = "Chaflán"
        )
        performAction(cmd)
    }

    @MainThread

    fun undo() {

        val now = System.currentTimeMillis()

        if (now - lastUndoTime < 300L) return

        lastUndoTime = now



        if (undoStack.isEmpty()) return

        selectionManager.clearSelection()

        val command = undoStack.pop()

        command.undo()

        redoStack.push(command)

        updateUndoRedoSupport()

        notifyLayersChanged()

    }



    /**

     * Performs a Redo operation. Must be called from the Main thread.

     */

    @MainThread

    fun redo() {

        val now = System.currentTimeMillis()

        if (now - lastRedoTime < 300L) return

        lastRedoTime = now



        if (redoStack.isEmpty()) return

        selectionManager.clearSelection()

        val command = redoStack.pop()

        command.execute()

        undoStack.push(command)

        updateUndoRedoSupport()

        notifyLayersChanged()

    }



    private fun notifyLayersChanged() {

        // StateFlow only emits if the value (reference) changes.

        // We create a shallow copy of the list to trigger the emission.

        editingParent?.invalidateCache()

        layerUpdateTrigger++

        liveProjectionController.markDirty()

    }



    var layerUpdateTrigger by mutableStateOf(0)



    private inner class SnapshotCommand(

        private val label: String,

        private val before: List<Layer>,

        private val after: List<Layer>,

        private val activeIndexBefore: Int,

        private val activeIndexAfter: Int = activeIndexBefore

    ) : UndoCommand {

        override fun execute() { restoreSnapshot(after, activeIndexAfter) }

        override fun undo() { restoreSnapshot(before, activeIndexBefore) }

        override fun getLabel(): String = label

    }



    @MainThread

    private fun performSnapshotAction(label: String, action: () -> Unit) {

        val before = createLayersSnapshot()

        val activeIndexBefore = activeLayerIndex

        action()

        val after = createLayersSnapshot()

        val activeIndexAfter = activeLayerIndex

        performAction(SnapshotCommand(label, before, after, activeIndexBefore, activeIndexAfter))

        hasUnsavedChanges = true
        hasUnsavedChangesSinceLastAutosave = true

    }



    @MainThread

    private fun createLayersSnapshot(): List<Layer> {

        val selected = selectionManager.selectedElements

        return layers.map { layer ->

            layer.copy(elements = layer.elements.map { element ->

                if (selected.any { it === element }) element.copyElement() else element

            }.toMutableStateList())

        }

    }

    

    @MainThread

    private fun restoreSnapshot(state: List<Layer>, restoredActiveIndex: Int) {

        val selectedIndicesByLayerId = layers.associate { layer ->

            layer.id to layer.elements.mapIndexedNotNull { index, element ->

                if (selectionManager.selectedElements.any { it === element }) index else null

            }

        }



        layerManager.internalUpdateLayers(

            newList = state.map { savedLayer ->

                savedLayer.copy(elements = savedLayer.elements.toMutableStateList())

            },

            activeIndex = restoredActiveIndex

        )



        val newSelected = mutableListOf<LayerElement>()

        layerManager.layers.forEach { layer ->

            val indices = selectedIndicesByLayerId[layer.id]

            if (indices != null) {

                indices.forEach { idx ->

                    if (idx in layer.elements.indices) {

                        newSelected.add(layer.elements[idx])

                    }

                }

            }

        }

        selectionManager.selectedElements.clear()

        selectionManager.selectedElements.addAll(newSelected)



        layerUpdateTrigger++

    }

    

    // --- ACTIONS ---



    

    val screenPxPerMm: Float by lazy {

        com.sketcher.sketchercompanionv1.utils.UnitUtils.getScreenPxPerMm(getApplication())

    }



    fun getZoomScale100(): Float {

        val basePx = scaleConfig.basePixelsPerMillimeter

        val safeBasePx = if (basePx == 0f) 5f else basePx

        return screenPxPerMm / safeBasePx

    }



    private fun isHomeCameraDefaultOrIdentity(): Boolean {

        return homeCameraMatrixValues[0] == 1f && homeCameraMatrixValues[1] == 0f && homeCameraMatrixValues[2] == 0f &&

               homeCameraMatrixValues[3] == 0f && homeCameraMatrixValues[4] == 1f && homeCameraMatrixValues[5] == 0f &&

               homeCameraMatrixValues[6] == 0f && homeCameraMatrixValues[7] == 0f && homeCameraMatrixValues[8] == 1f

    }

    private fun isCurrentCameraIdentity(): Boolean {
        return cameraMatrixValues[0] == 1f && cameraMatrixValues[1] == 0f && cameraMatrixValues[2] == 0f &&
               cameraMatrixValues[3] == 0f && cameraMatrixValues[4] == 1f && cameraMatrixValues[5] == 0f &&
               cameraMatrixValues[6] == 0f && cameraMatrixValues[7] == 0f && cameraMatrixValues[8] == 1f
    }



    fun centerPaperAsHomeCamera() {

        if (lastViewportWidth > 0.0f && lastViewportHeight > 0.0f) {

            val scale = getZoomScale100()

            val m = Matrix()

            val config = canvasSizeConfig

            if (config != null) {

                val w = config.widthInPixels

                val h = config.heightInPixels

                if (w > 0 && h > 0) {

                    val cx = if (config.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) 0f else w / 2.0f

                    val cy = if (config.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) 0f else h / 2.0f

                    m.postTranslate(-cx, -cy)

                    m.postScale(scale, scale)

                    m.postTranslate(lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)

                } else {

                    m.postScale(scale, scale)

                    m.postTranslate(lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)

                }

            } else {

                m.postScale(scale, scale)

                m.postTranslate(lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)

            }

            m.getValues(homeCameraMatrixValues)

            prefs.edit().putString("home_camera_matrix_v4", homeCameraMatrixValues.joinToString(",")).apply()

            saveCameraState(m)

        }

    }



    // --- CAMERA ---

    val cameraMatrixValues = FloatArray(9).apply { Matrix().getValues(this) }

    private val _cameraMatrix = MutableStateFlow(Matrix())

    val cameraMatrix = _cameraMatrix.asStateFlow()

    private var isProjectCameraLoaded = false



    private val homeCameraMatrixValues = FloatArray(9).apply {

        val saved = prefs.getString("home_camera_matrix_v4", null)

        if (saved != null) {

            val arr = saved.split(",").map { it.toFloat() }.toFloatArray()

            if (arr.size == 9) arr.copyInto(this) else Matrix().getValues(this)

        } else {

            Matrix().getValues(this)

        }

    }

    var cameraUpdateTrigger by mutableIntStateOf(0)

    var showHomeRestoredFeedback by mutableStateOf(false)

    

    fun resetCamera() { 

        homeCameraMatrixValues.copyInto(cameraMatrixValues)

        cameraUpdateTrigger++ 

        val m = Matrix()

        m.setValues(homeCameraMatrixValues)

        _cameraMatrix.value = m

        showHomeRestoredFeedback = true

    }

    fun saveHomeCamera() {

        cameraMatrixValues.copyInto(homeCameraMatrixValues)

        prefs.edit().putString("home_camera_matrix_v4", homeCameraMatrixValues.joinToString(",")).apply()

    }

    fun saveCameraState(matrix: Matrix) { 

        matrix.getValues(cameraMatrixValues)

        cameraUpdateTrigger++

        _cameraMatrix.value = Matrix(matrix)

        liveProjectionController.markDirty()

    }

    fun saveDimensions(w: Float, h: Float) {

        val sizeChanged = (lastViewportWidth != w || lastViewportHeight != h)

        lastViewportWidth = w

        lastViewportHeight = h

        if (sizeChanged && w > 0f && h > 0f) {

            liveProjectionController.updateViewportDimensions(w, h)

            if (isCurrentCameraIdentity()) {
                centerPaperAsHomeCamera()
            } else if (isHomeCameraDefaultOrIdentity() && !isProjectCameraLoaded) {
                centerPaperAsHomeCamera()
            }

            isProjectCameraLoaded = false

        }

    }

    private fun getMatrixScale(matrix: Matrix): Float {

        val values = FloatArray(9)

        matrix.getValues(values)

        val sX = values[Matrix.MSCALE_X]

        val skX = values[Matrix.MSKEW_X]

        return kotlin.math.sqrt(sX * sX + skX * skX)

    }



    fun zoomIn() {

        if (lastViewportWidth <= 0.0f || lastViewportHeight <= 0.0f) return

        val m = Matrix(_cameraMatrix.value)

        val currentScale = getMatrixScale(m)

        val zoomScale100 = getZoomScale100()

        val currentNormalizedZoom = currentScale / zoomScale100

        val targetNormalizedZoom = (currentNormalizedZoom * 1.2f).coerceIn(0.2f, 12.0f)

        val snapThreshold = 0.08f

        val finalNormalizedZoom = if (kotlin.math.abs(targetNormalizedZoom - 1.0f) < snapThreshold) 1.0f else targetNormalizedZoom

        val finalScale = finalNormalizedZoom * zoomScale100

        val factor = finalScale / currentScale

        m.postScale(factor, factor, lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)

        saveCameraState(m)

    }



    fun zoomOut() {

        if (lastViewportWidth <= 0.0f || lastViewportHeight <= 0.0f) return

        val m = Matrix(_cameraMatrix.value)

        val currentScale = getMatrixScale(m)

        val zoomScale100 = getZoomScale100()

        val currentNormalizedZoom = currentScale / zoomScale100

        val targetNormalizedZoom = (currentNormalizedZoom * 0.8f).coerceIn(0.2f, 12.0f)

        val snapThreshold = 0.08f

        val finalNormalizedZoom = if (kotlin.math.abs(targetNormalizedZoom - 1.0f) < snapThreshold) 1.0f else targetNormalizedZoom

        val finalScale = finalNormalizedZoom * zoomScale100

        val factor = finalScale / currentScale

        m.postScale(factor, factor, lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)

        saveCameraState(m)

    }



    fun fitContent() {

         // Logic to fit content (Reused/Simplifed from original if possible, or copied from Step 87 snapshot if valid)

         // Creating a robust fitContent based on visible layers

         

         // 1. Check if Paper Size is configured (Priority)

         if (canvasSizeConfig != null) {

             val w = canvasSizeConfig!!.widthInPixels

             val h = canvasSizeConfig!!.heightInPixels

             if (w > 0 && h > 0 && lastViewportWidth > 0.0f && lastViewportHeight > 0.0f) {

                 val padding = 50f

                 val scaleX = (lastViewportWidth - padding * 2.0f) / w.toFloat()

                 val scaleY = (lastViewportHeight - padding * 2.0f) / h.toFloat()

                 val scale = kotlin.math.min(scaleX, scaleY).coerceIn(0.1f, 12.0f)

                 

                 val cx = if (canvasSizeConfig!!.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) 0f else w.toFloat() / 2.0f

                 val cy = if (canvasSizeConfig!!.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) 0f else h.toFloat() / 2.0f

                 

                 val m = Matrix()

                 m.postTranslate(-cx, -cy)

                 m.postScale(scale, scale)

                 m.postTranslate(lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)

                 

                 // Save state

                 saveCameraState(m)

                 

                 // Also save as Home

                 saveHomeCamera()

                 return

             }

         }



         if (layers.all { it.elements.isEmpty() }) { resetCamera(); return }

         var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE

         var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE

         var hasContent = false

         

         layers.forEach { layer ->

            layer.elements.forEach { element ->

                 val bounds = element.getBoundingBox(componentLibrary)

                 if (bounds.left < minX) minX = bounds.left

                 if (bounds.right > maxX) maxX = bounds.right

                 if (bounds.top < minY) minY = bounds.top

                 if (bounds.bottom > maxY) maxY = bounds.bottom

                 hasContent = true

            }

         }

         

         if (!hasContent) { resetCamera(); return }

         

         val padding = 50f

         val w = maxX - minX; val h = maxY - minY

         if (w > 0 && h > 0 && lastViewportWidth > 0 && lastViewportHeight > 0) {

             val scaleX = (lastViewportWidth - padding*2) / w

             val scaleY = (lastViewportHeight - padding*2) / h

             val scale = kotlin.math.min(scaleX, scaleY).coerceIn(0.1f, 12.0f) // Updated limit

             val cx = (minX + maxX)/2

             val cy = (minY + maxY)/2

             

             val m = Matrix()

             m.postTranslate(-cx, -cy)

             m.postScale(scale, scale)

             m.postTranslate(lastViewportWidth/2, lastViewportHeight/2)

             saveCameraState(m)

         }

    }



    fun setZoomOneHundred() {

        if (lastViewportWidth <= 0.0f || lastViewportHeight <= 0.0f) return

        

        val m = Matrix()

        m.setValues(cameraMatrixValues)

        

        val values = FloatArray(9)

        m.getValues(values)

        val currentScale = values[Matrix.MSCALE_X]

        val currentTx = values[Matrix.MTRANS_X]

        val currentTy = values[Matrix.MTRANS_Y]

        

        // Center of viewport

        val cx = lastViewportWidth / 2f

        val cy = lastViewportHeight / 2f

        

        // World coordinates of center

        val worldX = (cx - currentTx) / currentScale

        val worldY = (cy - currentTy) / currentScale

        

        // New Matrix: Scale zoomScale100, preserve center

        val targetScale = getZoomScale100()

        m.setScale(targetScale, targetScale)

        m.postTranslate(cx - worldX * targetScale, cy - worldY * targetScale)

        

        saveCameraState(m)

    }



    fun addImportedDxfData(data: DxfImportData, scaleToFit: Boolean, defaultStrokeWidth: Float, fillClosedShapes: Boolean = false, sourceUnit: DistanceUnit = DistanceUnit.MM) {

        performSnapshotAction("Importar DXF") {

            // 1. Determine Scale/Offset

            var matrix = Matrix()

            if (scaleToFit && canvasSizeConfig != null) {

                val bounds = data.totalBounds

                val canvasW = canvasSizeConfig!!.widthInPixels

                val canvasH = canvasSizeConfig!!.heightInPixels

                

                if (bounds.width() > 0 && bounds.height() > 0) {

                     val scaleX = canvasW / bounds.width()

                     val scaleY = canvasH / bounds.height()

                     val scale = kotlin.math.min(scaleX, scaleY) * 0.9f 

                     

                      val cx = bounds.centerX()

                      val cy = bounds.centerY()

                      

                      matrix.postTranslate(-cx, -cy)

                      matrix.postScale(scale, scale)

                      val targetCx = if (canvasSizeConfig!!.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) 0f else canvasW / 2f

                      val targetCy = if (canvasSizeConfig!!.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) 0f else canvasH / 2f

                      matrix.postTranslate(targetCx, targetCy)

                }

            } else {

                val pixelScale = sourceUnit.toMillimeters * scaleConfig.basePixelsPerMillimeter

                matrix.postScale(pixelScale, pixelScale)

            }



            // 2. Group by Layer

            val pathsByLayer = data.paths.groupBy { it.layerName }

            

            // 3. Process Layers

            pathsByLayer.entries.forEach { entry ->

                val layerName = entry.key

                val dxfPaths = entry.value



                // Find or Create Layer

                val currentLayers = layers.toMutableList()

                var targetLayer = currentLayers.find { it.name == layerName }

                if (targetLayer == null) {

                    targetLayer = Layer("l_dxf_${UUID.randomUUID()}", layerName, mutableStateListOf())

                    currentLayers.add(targetLayer)

                    layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)

                }

                val layerIndex = layers.indexOf(targetLayer)

                val mutableElements = targetLayer.elements.toMutableList()



                dxfPaths.forEach { dp ->

                    val path = android.graphics.Path(dp.path)

                    path.transform(matrix)

                    val strokeColor = dp.color ?: AndroidColor.BLACK 

                    val points = PathUtils.samplePath(path)

                    val isFilledShape = fillClosedShapes && dp.isClosed

                    

                    val finalPath: android.graphics.Path

                    val brushType: String

                    

                    if (isFilledShape) {

                        finalPath = path

                        brushType = "FILLED_SHAPE" 

                    } else {

                        val outlinePath = android.graphics.Path()

                        val paint = android.graphics.Paint().apply {

                            style = android.graphics.Paint.Style.STROKE

                            strokeWidth = defaultStrokeWidth

                            strokeCap = android.graphics.Paint.Cap.ROUND

                            strokeJoin = android.graphics.Paint.Join.ROUND

                        }

                        paint.getFillPath(path, outlinePath)

                        finalPath = outlinePath

                        brushType = "FREEHAND"

                    }



                    val stroke = VectorStroke(

                         points = points,

                         strokeColor = strokeColor,

                         brushType = brushType,

                         strokeType = StrokeType.FREEHAND,

                         maxWidth = defaultStrokeWidth, 

                         path = finalPath

                    )

                    mutableElements.add(stroke)

                }

                val newList = layers.toMutableList()

                newList[layerIndex] = targetLayer.copy(elements = mutableElements.toMutableStateList())

                layerManager.internalUpdateLayers(newList, activeLayerIndex)

            }

            

            // Fit camera if we scaled content to fit canvas

            if (scaleToFit && canvasSizeConfig != null) {

                 resetCamera() 

            }

        }

    }

    

    // Legacy single stroke import (now replaced/unused but keeping safe or removing?)

    // Removing old addImportedStrokes to avoid confusion/duplication



    // Export Helpers

    fun getExportDefaults(useHomeView: Boolean): Pair<Int, Int> {

        return if (useHomeView) {

            Pair(lastViewportWidth.toInt().coerceAtLeast(100), lastViewportHeight.toInt().coerceAtLeast(100))

        } else {

            val bounds = calculateVisibleBounds()

            if (bounds.isEmpty) return Pair(800, 600)

            val padding = kotlin.math.max(bounds.width(), bounds.height()) * 0.05f

            bounds.inset(-padding, -padding)

            Pair(bounds.width().toInt().coerceAtLeast(100), bounds.height().toInt().coerceAtLeast(100))

        }

    }

    private fun getExportSource(
        useHomeView: Boolean,
        transparentBackground: Boolean = false,
        sizeConfig: com.sketcher.sketchercompanionv1.dto.CanvasSizeConfig? = null,
        targetLayers: List<Layer>? = null
    ): Pair<RectF, Float> {

        val bounds = RectF()

        if (useHomeView) {

            bounds.set(0f, 0f, lastViewportWidth, lastViewportHeight)

        } else {

             val sizeCfg = sizeConfig ?: canvasSizeConfig

             if (sizeCfg != null && !transparentBackground) {

                 val halfW = sizeCfg.widthInPixels / 2f

                 val halfH = sizeCfg.heightInPixels / 2f

                 val left = if (sizeCfg.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) -halfW else 0f

                 val top = if (sizeCfg.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER) -halfH else 0f

                 val right = left + sizeCfg.widthInPixels

                 val bottom = top + sizeCfg.heightInPixels

                 val paperBounds = RectF(left, top, right, bottom)

                 // Add 5% padding so shadow/border are fully visible

                 val paddingX = paperBounds.width() * 0.05f

                 val paddingY = paperBounds.height() * 0.05f

                 paperBounds.inset(-paddingX, -paddingY)

                 bounds.set(paperBounds)

             } else {

                 val layersToUse = targetLayers ?: layers

                 val visibleBounds = calculateVisibleBounds(layersToUse)

                 if (visibleBounds.isEmpty) return Pair(RectF(0f, 0f, 100f, 100f), 1f)

                 val padding = kotlin.math.max(visibleBounds.width(), visibleBounds.height()) * 0.05f

                 visibleBounds.inset(-padding, -padding)

                 bounds.set(visibleBounds)

             }

        }

        return Pair(bounds, 1f)

    }

    fun renderExportBitmap(
        config: ExportPngConfig,
        customLayers: List<Layer>? = null,
        customBgColor: Int? = null,
        customBgStyle: com.sketcher.sketchercompanionv1.dto.FillStyle? = null,
        customSizeConfig: com.sketcher.sketchercompanionv1.dto.CanvasSizeConfig? = null,
        customGridConfig: com.sketcher.sketchercompanionv1.dto.GridConfig? = null,
        customScaleConfig: com.sketcher.sketchercompanionv1.dto.ScaleConfig? = null,
        customUnit: DistanceUnit? = null,
        cropBounds: android.graphics.RectF? = null
    ): Bitmap? {

        try {

             val targetLayers = customLayers ?: layers

             val targetBgColor = customBgColor ?: backgroundColor

             val targetBgStyle = customBgStyle ?: backgroundStyle

             val targetSizeConfig = customSizeConfig ?: canvasSizeConfig

             val targetGridConfig = customGridConfig ?: gridConfig

             val targetScaleConfig = customScaleConfig ?: scaleConfig

             val targetUnit = customUnit ?: currentUnit

             var (sourceBounds, _) = getExportSource(
                 useHomeView = config.useHomeView,
                 transparentBackground = config.transparentBackground,
                 sizeConfig = targetSizeConfig,
                 targetLayers = targetLayers
             )

             if (cropBounds != null) {
                 sourceBounds = cropBounds
             }

             if (config.width <= 0 || config.height <= 0) return null

             val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)

             val canvas = Canvas(bitmap)

             val matrix = Matrix()

             if (config.useHomeView) {

                 matrix.setValues(homeCameraMatrixValues)

                 val scaleX = config.width.toFloat() / lastViewportWidth

                 val scaleY = config.height.toFloat() / lastViewportHeight

                 val scale = minOf(scaleX, scaleY)

                 val contentWidth = lastViewportWidth * scale

                 val contentHeight = lastViewportHeight * scale

                 val dx = (config.width - contentWidth) / 2f

                 val dy = (config.height - contentHeight) / 2f

                 matrix.postScale(scale, scale)

                 matrix.postTranslate(dx, dy)

             } else {

                 val scaleX = config.width.toFloat() / sourceBounds.width()

                 val scaleY = config.height.toFloat() / sourceBounds.height()

                 val scale = minOf(scaleX, scaleY)

                 val contentWidth = sourceBounds.width() * scale

                 val contentHeight = sourceBounds.height() * scale

                 val dx = (config.width - contentWidth) / 2f

                 val dy = (config.height - contentHeight) / 2f

                 matrix.postTranslate(-sourceBounds.left, -sourceBounds.top)

                 matrix.postScale(scale, scale)

                 matrix.postTranslate(dx, dy)

             }

             if (config.transparentBackground) {

                 canvas.drawColor(AndroidColor.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

                 canvas.save()

                 canvas.concat(matrix)

                 for (layer in targetLayers) {

                     if (!layer.isVisible) continue

                     val layerAlpha = if (layer.opacity < 1f) (layer.opacity * 255).toInt() else 255
                     val saveCount = if (layerAlpha < 255) {
                          val paint = android.graphics.Paint().apply { alpha = layerAlpha }
                          canvas.saveLayer(0f, 0f, canvas.width.toFloat(), canvas.height.toFloat(), paint)
                      } else {
                          canvas.save()
                      }

                     for (element in layer.elements) RenderHelper.drawElementRecursive(canvas, element, componentLibrary)

                     canvas.restoreToCount(saveCount)

                 }

                 canvas.restore()

             } else {

                 val renderEngine = RenderEngine().apply {

                     this.canvasSizeConfig = targetSizeConfig

                     this.canvasBackgroundStyle = targetBgStyle

                     this.canvasBackgroundColor = targetBgColor

                     this.gridConfig = targetGridConfig

                     this.scaleConfig = targetScaleConfig

                     this.currentUnit = targetUnit

                 }

                 renderEngine.drawLayers(

                     canvas = canvas,

                     layers = targetLayers,

                     viewMatrix = matrix,

                     componentLibrary = componentLibrary,

                     selectedElements = null,

                     isTransformActive = false,

                     drawGrid = renderEngine.gridConfig.isVisible

                 )

             }

             return bitmap

        } catch (e: Exception) {

             e.printStackTrace()

             return null

        }

    }



    fun exportPng(context: Context, uri: android.net.Uri, config: ExportPngConfig) {

        launchIO {

            try {

                // Rendering bitmap must happen on specific thread if it involves UI? 

                // Actually renderExportBitmap uses Canvas drawing which is CPU bound but generic.

                // However, Android Canvas often needs to be on Main thread if hardware accelerated?

                // Software bitmap creation is fine on background thread.

                val bitmap = renderExportBitmap(config) ?: return@launchIO

                

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->

                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)

                }

                bitmap.recycle()

            } catch (e: Exception) {

                e.printStackTrace()

            }

        }

    }



    fun renderSelectionExportBitmap(transparent: Boolean = false, maxDimension: Int = 1024): Bitmap? {
        val bounds = selectionManager.getSelectionBounds()
        if (bounds.isEmpty || bounds.width() <= 0f || bounds.height() <= 0f) return null

        val w = bounds.width()
        val h = bounds.height()
        val targetWidth: Int
        val targetHeight: Int
        if (w >= h) {
            targetWidth = maxDimension
            targetHeight = (maxDimension * (h / w)).toInt().coerceAtLeast(1)
        } else {
            targetHeight = maxDimension
            targetWidth = (maxDimension * (w / h)).toInt().coerceAtLeast(1)
        }

        val config = ExportPngConfig(
            transparentBackground = transparent,
            useHomeView = false,
            width = targetWidth,
            height = targetHeight
        )

        return renderExportBitmap(config = config, cropBounds = bounds)
    }

    fun importRenderedBitmap(bitmap: Bitmap, filename: String) {
        activeImageEditState = com.sketcher.sketchercompanionv1.dto.ImageEditState(
            isNewImport = true,
            elementId = null,
            originalBitmap = bitmap,
            filename = filename
        )
    }



    // --- ADD METHODS (Missing from Step 93 truncation) ---

    // Note: Step 33 had addInkStroke, addVectorStroke etc.

    // I should add them back.

    

    fun addVectorStroke(stroke: VectorStroke) {

        val targetLayer = layers[activeLayerIndex]

        if (targetLayer.isLocked) return

        

        performAction(AddStrokeCommand(activeContainer, stroke))

    }

    



    fun addHybridStroke(stroke: VectorStroke, fill: FillData?) {
        val targetLayer = layers[activeLayerIndex]
        if (targetLayer.isLocked) return

        val isPaintOrWatercolor = stroke.brushType == "PAINT" || stroke.brushType == "WATERCOLOR"
        var shouldJoin = true
        if (isPaintOrWatercolor) {
            val toolType = if (stroke.brushType == "PAINT") ToolType.PAINT else ToolType.WATERCOLOR
            val toolSettings = toolManager.getToolConfigMap()[toolType]?.settings
            shouldJoin = when (toolSettings) {
                is com.sketcher.sketchercompanionv1.tools.PaintSettings -> toolSettings.paintJoinPrevious
                is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> toolSettings.paintJoinPrevious
                else -> true
            }
        }

        if (isPaintOrWatercolor && shouldJoin) {
            val containerSnapshot = synchronized(activeContainer) { activeContainer.toList() }
            viewModelScope.launch {
                val mergedResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    val sameColorPaintStrokes = containerSnapshot.filterIsInstance<VectorStroke>().filter { existing ->
                        existing.brushType == stroke.brushType &&
                        existing.strokeColor == stroke.strokeColor &&
                        existing.fillColor == stroke.fillColor &&
                        existing.isFillEnabled == stroke.isFillEnabled &&
                        existing.isStrokeEnabled == stroke.isStrokeEnabled
                    }
                    
                    val overlappingStrokes = sameColorPaintStrokes.filter { existing ->
                        if (android.graphics.RectF.intersects(existing.getBoundingBox(), stroke.getBoundingBox())) {
                            val intersectPath = android.graphics.Path()
                            intersectPath.op(existing.path, stroke.path, android.graphics.Path.Op.INTERSECT)
                            !intersectPath.isEmpty
                        } else {
                            false
                        }
                    }
                    
                    if (overlappingStrokes.isNotEmpty()) {
                        val mergedPath = android.graphics.Path(stroke.path).apply { fillType = android.graphics.Path.FillType.EVEN_ODD }
                        for (existing in overlappingStrokes) {
                            val existingPath = android.graphics.Path(existing.path).apply { fillType = android.graphics.Path.FillType.EVEN_ODD }
                            val unionPath = android.graphics.Path().apply { fillType = android.graphics.Path.FillType.EVEN_ODD }
                            unionPath.op(mergedPath, existingPath, android.graphics.Path.Op.UNION)
                            mergedPath.set(unionPath)
                        }
                        
                        val zoom = run {
                            val vals = FloatArray(9)
                            _cameraMatrix.value.getValues(vals)
                            val scale = kotlin.math.sqrt(vals[android.graphics.Matrix.MSCALE_X] * vals[android.graphics.Matrix.MSCALE_X] + vals[android.graphics.Matrix.MSKEW_X] * vals[android.graphics.Matrix.MSKEW_X])
                            if (scale > 0.001f) scale else 1.0f
                        }
                        
                        val step = (8f / zoom).coerceAtLeast(1.0f)
                        val epsilon = (1.5f / zoom).coerceAtLeast(0.2f)
                        val outlinePointsPointF = com.sketcher.sketchercompanionv1.utils.GeometryUtils.flattenPath(mergedPath, step = step)
                        val outlineStrokePoints = outlinePointsPointF.map { pt -> StrokePoint(pt.x, pt.y, 0.5f) }
                        val simplifiedPoints = if (outlineStrokePoints.size > 2) {
                            com.sketcher.sketchercompanionv1.utils.StrokeSimplifier.simplify(outlineStrokePoints, epsilon, 20f)
                        } else {
                            outlineStrokePoints
                        }

                        val mergedStroke = stroke.copy(
                            points = simplifiedPoints,
                            path = mergedPath,
                            fillPath = if (stroke.isFillEnabled) mergedPath else null
                        )
                        Pair(overlappingStrokes, mergedStroke)
                    } else {
                        null
                    }
                }
                
                if (mergedResult != null) {
                    performAction(com.sketcher.sketchercompanionv1.command.MergePaintStrokesCommand(
                        activeContainer, 
                        mergedResult.first, 
                        stroke, 
                        mergedResult.second
                    ))
                } else {
                    performAction(AddHybridStrokeCommand(activeContainer, stroke, fill))
                }
            }
            return
        }

        performAction(AddHybridStrokeCommand(activeContainer, stroke, fill))
    }



    fun getSelectionLayerInfo(): String {

        val selected = selectionManager.selectedElements

        if (selected.isEmpty()) return ""



        val currentLayers = layers

        val layersFound = mutableSetOf<Int>()

        for (i in currentLayers.indices) {

            val layer = currentLayers[i]

            if (selected.any { isElementInHierarchy(layer.elements, it) }) {

                layersFound.add(i)

            }

        }



        return when {

            layersFound.isEmpty() -> "Capa desconocida"

            layersFound.size == 1 -> currentLayers[layersFound.first()].name

            else -> "MÃºltiples capas"

        }

    }



    private fun isElementInHierarchy(container: List<LayerElement>, target: LayerElement): Boolean {

        for (e in container) {

            if (e === target) return true

            if (e is GroupElement) {

                if (isElementInHierarchy(e.elements, target)) return true

            }

        }

        return false

    }



    fun moveSelectionToLayer(targetLayerIndex: Int) {

        if (targetLayerIndex !in layers.indices) return

        val selected = selectionManager.selectedElements.toList()

        if (selected.isEmpty()) return



        performSnapshotAction("Mover a Capa") {

            val currentLayers = layers.toMutableList()

            // 1. Remove from wherever they are

            for (element in selected) {

                removeElementFromHierarchyInternal(currentLayers, element)

            }

            // 2. Add to target layer

            currentLayers[targetLayerIndex].elements.addAll(selected)

            // 3. Mark layers as changed

            for (i in currentLayers.indices) {

                currentLayers[i] = currentLayers[i].copy()

            }

            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)

        }

    }



    private fun removeElementFromHierarchyInternal(layersList: List<Layer>, element: LayerElement) {

        for (layer in layersList) {

            if (removeRecursive(layer.elements, element)) return

        }

    }



    private fun removeElementFromHierarchy(element: LayerElement) {

        for (layer in layers) {

            if (removeRecursive(layer.elements, element)) return

        }

    }



    private fun removeRecursive(elements: MutableList<LayerElement>, target: LayerElement): Boolean {

        val iterator = elements.iterator()

        while (iterator.hasNext()) {

            val e = iterator.next()

            if (e === target) {

                iterator.remove()

                return true

            }

            if (e is GroupElement) {

                if (removeRecursive(e.elements, target)) return true

            }

        }

        return false

    }

    

    var activeImageEditState by mutableStateOf<com.sketcher.sketchercompanionv1.dto.ImageEditState?>(null)

        private set

    var activeTextEditState by mutableStateOf<com.sketcher.sketchercompanionv1.dto.TextEditState?>(null)
        private set

    var activeTextElementForEdit by mutableStateOf<TextElement?>(null)
        private set

    var activeEditTextRef by mutableStateOf<android.widget.EditText?>(null)

    val selectedTextElement: TextElement?
        get() = selectionManager.selectedElements.firstOrNull() as? TextElement

    val isSingleTextSelected: Boolean
        get() = selectionManager.selectedElements.size == 1 && selectedTextElement != null

    fun startEditingText(element: TextElement) {
        selectionManager.clearSelection()
        selectionManager.selectedElements.add(element)
        activeTextElementForEdit = element
        
        activeTextEditState = com.sketcher.sketchercompanionv1.dto.TextEditState(
            isNewText = false,
            elementId = element.id,
            textHtml = element.textHtml,
            defaultTextColor = element.defaultTextColor,
            defaultTextSize = element.defaultTextSize,
            fontFamilyName = element.fontFamilyName,
            alignment = element.alignment,
            styleTemplateName = element.styleTemplateName
        )
    }

    fun startCreatingText(x: Float, y: Float) {
        val matrix = android.graphics.Matrix().apply { postTranslate(x, y) }
        val values = FloatArray(9).apply { matrix.getValues(this) }
        val newElement = TextElement(
            id = java.util.UUID.randomUUID().toString(),
            textHtml = "",
            width = 300f,
            matrixValues = values,
            defaultTextColor = strokeColor.value,
            defaultTextSize = 16f,
            fontFamilyName = "sans-serif",
            alignment = "LEFT",
            styleTemplateName = "BODY"
        )
        
        performSnapshotAction("Crear Texto") {
            activeContainer.add(newElement)
        }
        selectionManager.clearSelection()
        selectionManager.selectedElements.add(newElement)
        activeTextElementForEdit = newElement

        activeTextEditState = com.sketcher.sketchercompanionv1.dto.TextEditState(
            isNewText = true,
            elementId = newElement.id,
            textHtml = "",
            defaultTextColor = newElement.defaultTextColor,
            defaultTextSize = newElement.defaultTextSize,
            fontFamilyName = newElement.fontFamilyName,
            alignment = newElement.alignment,
            styleTemplateName = newElement.styleTemplateName,
            initialX = x,
            initialY = y
        )
    }

    fun dismissTextEdits() {
        val element = activeTextElementForEdit
        if (element != null && activeTextEditState?.isNewText == true && element.textHtml.isBlank()) {
            // Remove empty new text
            performSnapshotAction("Cancelar Texto") {
                activeContainer.remove(element)
            }
            selectionManager.clearSelection()
        }
        activeTextEditState = null
        activeTextElementForEdit = null
    }

    fun applyTextEdits(
        html: String,
        color: Int,
        size: Float,
        font: String,
        alignment: String,
        template: String?
    ) {
        val state = activeTextEditState ?: return
        val element = activeTextElementForEdit ?: return
        
        activeTextEditState = null
        activeTextElementForEdit = null

        performSnapshotAction("Editar Texto") {
            val index = activeContainer.indexOfFirst { it is TextElement && it.id == state.elementId }
            if (index != -1) {
                val orig = activeContainer[index] as TextElement
                val updated = orig.copy(
                        textHtml = html,
                        defaultTextColor = color,
                        defaultTextSize = size,
                        fontFamilyName = font,
                        alignment = alignment,
                        styleTemplateName = template
                    )
                    activeContainer[index] = updated
                }
            }
    }

    fun updateSelectedTextProperty(actionName: String, updateBlock: (TextElement) -> TextElement) {
        val element = selectedTextElement ?: return
        performSnapshotAction(actionName) {
            val index = activeContainer.indexOfFirst { it is TextElement && it.id == element.id }
            if (index != -1) {
                activeContainer[index] = updateBlock(element)
            }
        }
    }

    val isSingleImageSelected: Boolean

        get() = selectionManager.selectedElements.size == 1 && selectionManager.selectedElements.first() is ImageElement

    fun startEditingSelectedImage() {

        val selected = selectionManager.selectedElements.firstOrNull() as? ImageElement ?: return

        val original = selected.originalBitmap ?: selected.bitmap

        activeImageEditState = com.sketcher.sketchercompanionv1.dto.ImageEditState(

            isNewImport = false,

            elementId = selected.id,

            originalBitmap = original,

            filename = selected.imageFileName,

            matrix = selected.matrix,

            initialTransparentColors = selected.transparentColors,

            initialTolerance = selected.tolerance,

            initialCropRect = selected.cropRect,

            initialCropPath = selected.cropPath,

            initialTransparentColorTolerances = selected.transparentColorTolerances,

            initialRotation = selected.rotation,

            initialFlipHorizontal = selected.flipHorizontal,

            initialFlipVertical = selected.flipVertical

        )

    }



    fun dismissImageEdits() {

        activeImageEditState = null

    }



    @MainThread

    fun applyImageEdits(

        processedBitmap: android.graphics.Bitmap,

        transparentColors: List<Int>,

        tolerance: Float,

        cropRect: android.graphics.RectF?,

        cropPath: List<android.graphics.PointF>?,

        transparentColorTolerances: List<Float>,

        rotation: Float,

        flipHorizontal: Boolean,

        flipVertical: Boolean,

        calibrationScaleFactor: Float = 1.0f

    ) {

        val state = activeImageEditState ?: return

        activeImageEditState = null



        if (state.isNewImport) {

            if (activeLayerIndex !in layers.indices) return

            performSnapshotAction("Insertar Imagen") {

                val currentLayers = layers.toMutableList()

                val layer = currentLayers[activeLayerIndex]

                val matrix = Matrix()

                var targetX = lastViewportWidth / 2.0f
                var targetY = lastViewportHeight / 2.0f
                if (lastViewportWidth > 0.0f && lastViewportHeight > 0.0f) {
                    val pts = floatArrayOf(targetX, targetY)
                    val inv = android.graphics.Matrix()
                    if (_cameraMatrix.value.invert(inv)) {
                        inv.mapPoints(pts)
                    }
                    targetX = pts[0]
                    targetY = pts[1]
                    matrix.postTranslate(targetX - processedBitmap.width / 2.0f, targetY - processedBitmap.height / 2.0f)
                }
                
                if (calibrationScaleFactor != 1.0f) {
                    matrix.postScale(calibrationScaleFactor, calibrationScaleFactor, targetX, targetY)
                }

                val element = ImageElement(

                    id = java.util.UUID.randomUUID().toString(),

                    bitmap = processedBitmap, 

                    imageFileName = state.filename,

                    matrix = matrix,

                    originalBitmap = state.originalBitmap,

                    originalImageFileName = "orig_${state.filename}",

                    transparentColors = transparentColors,

                    tolerance = tolerance,

                    cropRect = cropRect,

                    cropPath = cropPath,

                    transparentColorTolerances = transparentColorTolerances,

                    rotation = rotation,

                    flipHorizontal = flipHorizontal,

                    flipVertical = flipVertical,

                    isScaleLocked = true

                )

                layer.elements.add(element)

                currentLayers[activeLayerIndex] = layer.copy()

                layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)

                selectionManager.clearSelection()

                selectionManager.selectedElements.add(element)

                enterTransformMode()

            }

        } else {

            val elementId = state.elementId ?: return

            performSnapshotAction("Editar Imagen") {

                val currentLayers = layers.toMutableList()

                var updatedElement: ImageElement? = null

                

                for (layer in currentLayers) {

                    val idx = layer.elements.indexOfFirst { it is ImageElement && it.id == elementId }

                    if (idx != -1) {

                        val oldElement = layer.elements[idx] as ImageElement
                        val oldBounds = oldElement.getBoundingBox(componentLibrary)
                        val oldCenterX = oldBounds.centerX()
                        val oldCenterY = oldBounds.centerY()

                        val newElement = oldElement.copy(
                            bitmap = processedBitmap,
                            originalBitmap = state.originalBitmap,
                            transparentColors = transparentColors,
                            tolerance = tolerance,
                            cropRect = cropRect,
                            cropPath = cropPath,
                            transparentColorTolerances = transparentColorTolerances,
                            rotation = rotation,
                            flipHorizontal = flipHorizontal,
                            flipVertical = flipVertical
                        ) as ImageElement

                        // Compensate for center shift caused by cropping or rotating
                        val newBounds = newElement.getBoundingBox(componentLibrary)
                        val dx = oldCenterX - newBounds.centerX()
                        val dy = oldCenterY - newBounds.centerY()
                        newElement.matrix.postTranslate(dx, dy)

                        if (calibrationScaleFactor != 1.0f) {
                            val bounds = newElement.getBoundingBox(componentLibrary)
                            newElement.matrix.postScale(calibrationScaleFactor, calibrationScaleFactor, bounds.centerX(), bounds.centerY())
                        }

                        layer.elements[idx] = newElement
                        updatedElement = newElement

                        currentLayers[currentLayers.indexOf(layer)] = layer.copy()

                        break

                    }

                }

                

                layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)

                

                if (updatedElement != null) {

                    selectionManager.clearSelection()

                    selectionManager.selectedElements.add(updatedElement)

                }

            }

        }

    }



    fun insertImage(context: Context, uri: android.net.Uri) {

         launchIO {

             // Enforce maxDimension of 1024 to prevent crashes/OOM on large images

             com.sketcher.sketchercompanionv1.utils.BitmapUtils.loadScaledBitmap(context, uri, 1024)?.let { bitmap ->

                 withContext(Dispatchers.Main) {

                      val filename = "img_${java.util.UUID.randomUUID()}.png"

                      activeImageEditState = com.sketcher.sketchercompanionv1.dto.ImageEditState(

                          isNewImport = true,

                          elementId = null,

                          originalBitmap = bitmap,

                          filename = filename

                      )

                 }

             }

         }

    }



    fun insertPdfPageBitmap(context: Context, bitmap: android.graphics.Bitmap, pageIndex: Int, dpi: Int, originalFileName: String) {

        val sanitizedName = originalFileName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")

        val filename = "pdf_${sanitizedName}_p${pageIndex}_${dpi}dpi_${java.util.UUID.randomUUID()}.png"

        activeImageEditState = com.sketcher.sketchercompanionv1.dto.ImageEditState(

            isNewImport = true,

            elementId = null,

            originalBitmap = bitmap,

            filename = filename

        )

    }

    

    fun insertSvg(context: Context, uri: android.net.Uri) {

        launchIO {

            // Modified to wrap SVG in GroupElement

            try {

                val stream = context.contentResolver.openInputStream(uri)

                val bytes = stream?.readBytes()

                stream?.close()

                if (bytes != null) {

                    val content = String(bytes, Charsets.UTF_8)

                    val svgElement = SvgElement("svg_${UUID.randomUUID()}", "import.svg", content)

                    

                    // Wrap in GroupElement as requested

                    val group = GroupElement(

                        id = UUID.randomUUID().toString(),

                        elements = mutableListOf(svgElement),

                        matrix = Matrix()

                    )

                    

                    withContext(Dispatchers.Main) {

                        performSnapshotAction("Insertar SVG") {

                            activeContainer.add(group)

                            notifyLayersChanged()

                        }

                    }

                }

            } catch(e: Exception) { e.printStackTrace() }

        }

    }

    

    // --- IO & ZIP ---

    fun saveProject(context: Context, saveLauncher: androidx.activity.result.ActivityResultLauncher<String>) {

        if (currentFileUri != null) {

            saveProjectToZip(context, currentFileUri!!)

        } else {

            saveLauncher.launch("drawing.skc")

        }

    }



    private fun autoSaveProject(context: Context) {
        lastAutosaveTime = System.currentTimeMillis()
        val autosaveFile = java.io.File(context.cacheDir, "autosave.skc")

        saveProjectToZip(context, android.net.Uri.fromFile(autosaveFile), isAutosave = true)

    }



    fun saveProjectToZip(context: Context, uri: android.net.Uri, isAutosave: Boolean = false) {

        saveCurrentPageState()
        val currentPagesJson = pages.map { it.toPageJson() }
        val currentActivePageIndex = activePageIndex
        val allPagesLayersSnapshot = pages.flatMap { page ->
            page.layers.map { layer -> layer.copy(elements = layer.elements.toMutableStateList()) }
        }

        val currentComponentLibrary = componentLibrary.toMap()

        val savedProjectId = projectId

        val savedBgColor = backgroundColor

        val savedBgStyle = backgroundStyle

        val savedGridConfig = gridConfig

        val savedScaleConfig = scaleConfig

        val savedUnit = currentUnit

        val savedViewportW = lastViewportWidth

        val savedViewportH = lastViewportHeight

        val savedCameraMatrix = cameraMatrixValues.toList()



        // Generate thumbnail on Main thread (safe from ConcurrentModificationException)
        val thumbnailBmp = if (isAutosave) null else try {
            val firstPage = pages.firstOrNull()
            if (firstPage != null) {
                renderExportBitmap(
                    config = ExportPngConfig(transparentBackground = false, useHomeView = false, width = 256, height = 256),
                    customLayers = firstPage.layers,
                    customBgColor = firstPage.backgroundColor,
                    customBgStyle = firstPage.backgroundStyle,
                    customSizeConfig = firstPage.canvasSizeConfig,
                    customGridConfig = firstPage.gridConfig,
                    customScaleConfig = firstPage.scaleConfig,
                    customUnit = firstPage.currentUnit
                )
            } else {
                renderExportBitmap(ExportPngConfig(transparentBackground = false, useHomeView = false, width = 256, height = 256))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }

        // Cache thumbnail in memory immediately
        val path = uri.path
        if (path != null && thumbnailBmp != null) {
            try {
                val cachedBmp = thumbnailBmp.copy(Bitmap.Config.ARGB_8888, false)
                thumbnailCache[path] = cachedBmp
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }



        launchIO {

            try {

                val projectData = ProjectData(

                    id = savedProjectId,

                    layers = pages.firstOrNull()?.layers?.map { it.toLayerJson() } ?: emptyList(),

                    backgroundConfig = BackgroundConfig(
                        color = pages.firstOrNull()?.backgroundColor ?: savedBgColor,
                        gridConfig = pages.firstOrNull()?.gridConfig ?: savedGridConfig,
                        fillStyle = (pages.firstOrNull()?.backgroundStyle ?: savedBgStyle).toFillStyleJson()
                    ),

                    paletteColors = emptyList(),

                    toolConfigs = emptyMap(),

                    canvasMetadata = CanvasMetadata(

                        width = savedViewportW, height = savedViewportH, 

                        cameraMatrix = pages.firstOrNull()?.cameraMatrixValues?.toList() ?: savedCameraMatrix,

                        scaleConfig = (pages.firstOrNull()?.scaleConfig ?: savedScaleConfig).copy(unitName = (pages.firstOrNull()?.currentUnit ?: savedUnit).symbol)

                    ),

                    componentLibrary = currentComponentLibrary.mapValues { it.value.toComponentDefinitionJson() },

                    canvasSizeConfig = pages.firstOrNull()?.canvasSizeConfig ?: canvasSizeConfig,

                    uiPresetName = toolbarManager.activeUiPresetName.value,

                    pages = currentPagesJson,

                    activePageIndex = currentActivePageIndex

                )

                com.sketcher.sketchercompanionv1.utils.ZipStorageManager.saveProject(

                    context = context,

                    projectData = projectData,

                    layers = allPagesLayersSnapshot,

                    uri = uri,

                    components = currentComponentLibrary.values,

                    thumbnail = thumbnailBmp,

                    toolStatesJson = toolManager.getToolStatesJson(activeCustomToolId)

                )



                // Delete autosave file if user successfully saved manually

                if (!isAutosave) {

                    val autosaveFile = java.io.File(context.cacheDir, "autosave.skc")

                    if (autosaveFile.exists()) {

                        autosaveFile.delete()

                    }

                    

                    // Trigger Cloud Upload in background
                    if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
                        launchIO {
                            val thumbUri = if (thumbnailBmp != null) {
                                val thumbFile = java.io.File(context.cacheDir, "temp_upload_thumb.png")
                                val out = java.io.FileOutputStream(thumbFile)
                                thumbnailBmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                                out.flush()
                                out.close()
                                android.net.Uri.fromFile(thumbFile)
                            } else null
                            
                            val localFile = java.io.File(uri.path ?: "")
                            val relativePath = try {
                                localFile.toRelativeString(getProjectsRootDir(context))
                            } catch (e: Exception) {
                                localFile.name
                            }
                            val fileTimestamp = if (localFile.exists()) localFile.lastModified() else System.currentTimeMillis()

                            val uploadResult = cloudSyncRepository.uploadProject(
                                projectId = savedProjectId,
                                projectName = localFile.nameWithoutExtension.ifEmpty { "Project" },
                                projectFileUri = uri,
                                relativePath = relativePath,
                                thumbnailUri = thumbUri,
                                timestamp = fileTimestamp,
                                metadata = mapOf(
                                    "lastModified" to fileTimestamp,
                                    "deviceUid" to getDeviceUid(context),
                                    "deviceName" to android.os.Build.MODEL,
                                    "fileSize" to (if (localFile.exists()) localFile.length() else 0L)
                                )
                            )
                            if (uploadResult.isSuccess) {
                                setProjectLastUploadedTime(context, savedProjectId, fileTimestamp)
                            }
                        }
                    }

                }



                withContext(Dispatchers.Main) {

                    if (!isAutosave) {

                        currentFileUri = uri

                        hasUnsavedChanges = false
                        hasUnsavedChangesSinceLastAutosave = false
                        lastAutosaveTime = System.currentTimeMillis()

                        android.widget.Toast.makeText(context, "Proyecto guardado correctamente", android.widget.Toast.LENGTH_SHORT).show()

                    } else {

                        hasUnsavedChangesSinceLastAutosave = false // Autosave cleared the cache unsaved state

                    }

                }

            } catch (e: Exception) {

                e.printStackTrace()

                withContext(Dispatchers.Main) {

                    android.widget.Toast.makeText(context, "Error al guardar: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()

                }

            } finally {

                thumbnailBmp?.recycle()

            }

        }

    }



    fun loadProjectFromZip(context: Context, uri: android.net.Uri) {

        launchIO {

            try {

                val (projectData, bitmapMap, svgMap) = com.sketcher.sketchercompanionv1.utils.ZipStorageManager.loadProject(context, uri)

                withContext(Dispatchers.Main) {

                    restoreProjectState(context, projectData, bitmapMap, svgMap)

                    reloadToolbarLayout()

                    currentFileUri = uri

                    android.widget.Toast.makeText(context, "Proyecto cargado correctamente", android.widget.Toast.LENGTH_SHORT).show()

                }

            } catch (e: Exception) {

                e.printStackTrace()

                withContext(Dispatchers.Main) {

                    android.widget.Toast.makeText(context, "Error al cargar: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()

                }

            }

        }

    }

    

    private fun restoreProjectState(context: Context, data: ProjectData, bitmaps: Map<String, android.graphics.Bitmap>, svgs: Map<String, String>) {

        undoStack.clear()

        redoStack.clear()

        updateUndoRedoSupport()

        // Restore ToolStates if available
        val tempPrefs = context.getSharedPreferences("tool_state_temp_prefs", Context.MODE_PRIVATE)
        val tempJson = tempPrefs.getString("temp_loaded_tool_states", null)
        if (tempJson != null) {
            val customId = toolManager.restoreToolStatesFromJson(tempJson)
            activeCustomToolId = customId
            if (customId != null) {
                val ct = toolManager.customTools.value.find { it.id == customId }
                if (ct != null) {
                    toolManager.applyBrushPresetDirectly(ct.preset)
                }
            }
            tempPrefs.edit().remove("temp_loaded_tool_states").apply()
        } else {
            // For old projects without saved states, reset to Preset modes and fallback to SELECTION
            toolManager.resetAllPresetOverrideStates()
            activeCustomToolId = null
            toolManager.selectTool(ToolType.SELECTION)
        }

        

        projectId = data.id

        hasUnsavedChanges = false
        hasUnsavedChangesSinceLastAutosave = false

        

        componentLibrary.clear()

        data.componentLibrary.forEach { (id, json) ->

             componentLibrary[id] = json.toComponentDefinition( { bitmaps[it] }, { svgs[it] } )

        }

        pages.clear()
        if (data.pages != null && data.pages.isNotEmpty()) {
            data.pages.forEach { pageJson ->
                val pageLayers = mutableStateListOf<Layer>()
                pageJson.layers.forEach { l -> pageLayers.add(l.toLayer( { bitmaps[it] }, { svgs[it] } )) }

                val page = CanvasPage(
                    id = pageJson.id,
                    name = pageJson.name,
                    layers = pageLayers,
                    activeLayerIndex = 0,
                    backgroundColor = pageJson.backgroundConfig.color,
                    backgroundStyle = pageJson.backgroundConfig.fillStyle.toFillStyle(pageJson.backgroundConfig.color),
                    gridConfig = pageJson.backgroundConfig.gridConfig ?: GridConfig(),
                    canvasSizeConfig = pageJson.canvasSizeConfig,
                    cameraMatrixValues = FloatArray(9).apply {
                        if (pageJson.canvasMetadata.cameraMatrix.size == 9) {
                            for (i in 0..8) this[i] = pageJson.canvasMetadata.cameraMatrix[i]
                        } else {
                            Matrix().getValues(this)
                        }
                    },
                    scaleConfig = pageJson.canvasMetadata.scaleConfig ?: ScaleConfig(),
                    currentUnit = DistanceUnit.fromSymbol(pageJson.canvasMetadata.scaleConfig?.unitName ?: "mm")
                )
                pages.add(page)
            }
            activePageIndex = data.activePageIndex.coerceIn(pages.indices)
        } else {
            // Legacy project support
            val pageLayers = mutableStateListOf<Layer>()
            data.layers.forEach { l -> pageLayers.add(l.toLayer( { bitmaps[it] }, { svgs[it] } )) }

            val page = CanvasPage(
                id = UUID.randomUUID().toString(),
                name = "Página 1",
                layers = pageLayers,
                activeLayerIndex = 0,
                backgroundColor = data.backgroundConfig.color,
                backgroundStyle = data.backgroundConfig.fillStyle.toFillStyle(data.backgroundConfig.color),
                gridConfig = data.backgroundConfig.gridConfig ?: GridConfig(),
                canvasSizeConfig = data.canvasSizeConfig,
                cameraMatrixValues = FloatArray(9).apply {
                    if (data.canvasMetadata.cameraMatrix.size == 9) {
                        for (i in 0..8) this[i] = data.canvasMetadata.cameraMatrix[i]
                    } else {
                        Matrix().getValues(this)
                    }
                },
                scaleConfig = data.canvasMetadata.scaleConfig ?: ScaleConfig(),
                currentUnit = DistanceUnit.fromSymbol(data.canvasMetadata.scaleConfig?.unitName ?: "mm")
            )
            pages.add(page)
            activePageIndex = 0
        }

        // Apply active page state to ViewModel
        val targetPage = pages[activePageIndex]
        layerManager.internalUpdateLayers(targetPage.layers, targetPage.activeLayerIndex)
        backgroundColor = targetPage.backgroundColor
        backgroundStyle = targetPage.backgroundStyle
        gridConfig = targetPage.gridConfig
        canvasSizeConfig = targetPage.canvasSizeConfig
        scaleConfig = targetPage.scaleConfig
        currentUnit = targetPage.currentUnit

        targetPage.cameraMatrixValues.copyInto(cameraMatrixValues)
        _cameraMatrix.value = Matrix().apply { setValues(cameraMatrixValues) }
        isProjectCameraLoaded = true
        cameraUpdateTrigger++

        // Restore ToolConfigs (Cleaned)

        data.toolConfigs.forEach { (t, c) -> toolManager.applyToolConfig(t, c) }

        if (activeCustomToolId == null) {
            selectTool(currentTool) // Refresh
        } else {
            toolManager.selectTool(currentTool)
        }

        

        if ((isHomeCameraDefaultOrIdentity() || isCurrentCameraIdentity()) && lastViewportWidth > 0f && lastViewportHeight > 0f) {
            centerPaperAsHomeCamera()
        }

        if (data.uiPresetName != null) {
            toolbarManager.onProjectUiPresetLoaded(data.uiPresetName)
        } else {
            toolbarManager.onProjectUiPresetCleared()
        }
    }

    





    // --- MISSING METHODS ---

    fun saveCurrentPageState() {
        val index = activePageIndex
        if (index in pages.indices) {
            val page = pages[index]
            page.layers.clear()
            page.layers.addAll(layers)
            page.activeLayerIndex = activeLayerIndex
            page.backgroundColor = backgroundColor
            page.backgroundStyle = backgroundStyle
            page.gridConfig = gridConfig
            page.canvasSizeConfig = canvasSizeConfig
            page.scaleConfig = scaleConfig
            page.currentUnit = currentUnit
            cameraMatrixValues.copyInto(page.cameraMatrixValues)
            page.undoStack.clear()
            page.undoStack.addAll(undoStack)
            page.redoStack.clear()
            page.redoStack.addAll(redoStack)
        }
    }

    fun loadPage(index: Int) {
        if (index !in pages.indices) return
        
        saveCurrentPageState()
        
        activePageIndex = index
        val targetPage = pages[index]
        
        layerManager.internalUpdateLayers(targetPage.layers, targetPage.activeLayerIndex)
        backgroundColor = targetPage.backgroundColor
        backgroundStyle = targetPage.backgroundStyle
        gridConfig = targetPage.gridConfig
        canvasSizeConfig = targetPage.canvasSizeConfig
        scaleConfig = targetPage.scaleConfig
        currentUnit = targetPage.currentUnit
        
        targetPage.cameraMatrixValues.copyInto(cameraMatrixValues)
        _cameraMatrix.value = Matrix().apply { setValues(cameraMatrixValues) }
        
        undoStack.clear()
        undoStack.addAll(targetPage.undoStack)
        redoStack.clear()
        redoStack.addAll(targetPage.redoStack)
        updateUndoRedoSupport()

        if (isCurrentCameraIdentity() && lastViewportWidth > 0f && lastViewportHeight > 0f) {
            centerPaperAsHomeCamera()
        } else {
            cameraUpdateTrigger++
        }
        notifyLayersChanged()
    }

    fun addPage() {
        saveCurrentPageState()
        val firstPage = pages.firstOrNull()
        val newPageId = UUID.randomUUID().toString()
        val newPage = CanvasPage(
            id = newPageId,
            name = "Página ${pages.size + 1}",
            layers = mutableStateListOf(Layer("l_${System.currentTimeMillis()}", "Capa 1", mutableStateListOf())),
            activeLayerIndex = 0,
            backgroundColor = firstPage?.backgroundColor ?: android.graphics.Color.WHITE,
            backgroundStyle = firstPage?.backgroundStyle ?: FillStyle.Solid(android.graphics.Color.WHITE),
            gridConfig = firstPage?.gridConfig ?: GridConfig(),
            canvasSizeConfig = firstPage?.canvasSizeConfig ?: canvasSizeConfig,
            cameraMatrixValues = FloatArray(9).apply { Matrix().getValues(this) },
            scaleConfig = firstPage?.scaleConfig ?: scaleConfig,
            currentUnit = firstPage?.currentUnit ?: currentUnit
        )
        pages.add(newPage)
        loadPage(pages.lastIndex)
        hasUnsavedChanges = true
    }

    fun removePage(index: Int) {
        if (pages.size <= 1) return
        if (index !in pages.indices) return
        
        pages.removeAt(index)
        
        if (activePageIndex >= pages.size) {
            activePageIndex = pages.size - 1
        }
        loadPage(activePageIndex)
        hasUnsavedChanges = true
    }

    fun duplicatePage(index: Int) {
        if (index !in pages.indices) return
        saveCurrentPageState()
        val source = pages[index]
        
        val copiedLayers = mutableStateListOf<Layer>()
        source.layers.forEach { layer ->
            val copiedElements = mutableStateListOf<LayerElement>()
            layer.elements.forEach { el -> copiedElements.add(el.copyElement()) }
            copiedLayers.add(layer.copy(
                id = "l_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}",
                elements = copiedElements
            ))
        }

        val copy = CanvasPage(
            id = UUID.randomUUID().toString(),
            name = "${source.name} (Copy)",
            layers = copiedLayers,
            activeLayerIndex = source.activeLayerIndex,
            backgroundColor = source.backgroundColor,
            backgroundStyle = source.backgroundStyle,
            gridConfig = source.gridConfig,
            canvasSizeConfig = source.canvasSizeConfig,
            cameraMatrixValues = source.cameraMatrixValues.clone(),
            scaleConfig = source.scaleConfig,
            currentUnit = source.currentUnit
        )
        
        pages.add(index + 1, copy)
        loadPage(index + 1)
        hasUnsavedChanges = true
    }

    fun renamePage(index: Int, newName: String) {
        if (index in pages.indices) {
            pages[index] = pages[index].copy(name = newName)
            hasUnsavedChanges = true
        }
    }

    fun movePage(fromIndex: Int, toIndex: Int) {
        if (fromIndex in pages.indices && toIndex in pages.indices && fromIndex != toIndex) {
            saveCurrentPageState()
            val item = pages.removeAt(fromIndex)
            pages.add(toIndex, item)
            
            if (activePageIndex == fromIndex) {
                activePageIndex = toIndex
            } else if (fromIndex < activePageIndex && toIndex >= activePageIndex) {
                activePageIndex--
            } else if (fromIndex > activePageIndex && toIndex <= activePageIndex) {
                activePageIndex++
            }
            hasUnsavedChanges = true
        }
    }

    fun movePageUp(index: Int) {
        if (index < pages.lastIndex) {
            movePage(index, index + 1)
        }
    }

    fun movePageDown(index: Int) {
        if (index > 0) {
            movePage(index, index - 1)
        }
    }







    private fun calculateVisibleBounds(customLayers: List<Layer>? = null): RectF {

        val totalBounds = RectF()

        var first = true

        val targetLayers = customLayers ?: layers

        for (layer in targetLayers) {

            if (!layer.isVisible) continue

            for (element in layer.elements) {

                val bounds = element.getBoundingBox(componentLibrary)

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



    fun generateSvgContent(config: ExportSvgConfig): String {

        val projectData = ProjectData(

            id = projectId,

            layers = layers.map { it.toLayerJson() },

            backgroundConfig = BackgroundConfig(color = backgroundColor, gridConfig = gridConfig),

            paletteColors = emptyList(),

            toolConfigs = emptyMap(),

            canvasMetadata = CanvasMetadata(

                width = config.width,

                height = config.height,

                cameraMatrix = emptyList(), // Not used directly in SvgExporter.export for coordinates if using homeView logic in exporter

                scaleConfig = scaleConfig

            ),

            componentLibrary = componentLibrary.mapValues { it.value.toComponentDefinitionJson() },
            uiPresetName = toolbarManager.activeUiPresetName.value
        )

        return SvgExporter.export(projectData, layers, config)

    }



    fun exportSvg(context: Context, uri: android.net.Uri, config: ExportSvgConfig) {

        launchIO {

            try {

                // Ensure state capture (though exportSvg uses current state, which might race)

                // ideally generateSvgContent logic runs here.

                val svgString = generateSvgContent(config)

                context.contentResolver.openOutputStream(uri)?.use { outputStream ->

                    outputStream.write(svgString.toByteArray())

                }

            } catch (e: Exception) {

                e.printStackTrace()

            }

        }

    }





    // PDF Export

    private var pdfExportBoundsMode: com.sketcher.sketchercompanionv1.utils.PdfExporter.BoundsMode = 

        com.sketcher.sketchercompanionv1.utils.PdfExporter.BoundsMode.CANVAS_SIZE



    fun setPdfExportBoundsMode(useZoomExtends: Boolean) {

        pdfExportBoundsMode = if (useZoomExtends) {

            com.sketcher.sketchercompanionv1.utils.PdfExporter.BoundsMode.ZOOM_EXTENDS

        } else {

            com.sketcher.sketchercompanionv1.utils.PdfExporter.BoundsMode.HOME_VIEW

        }

    }



    fun exportPdf(context: Context, uri: android.net.Uri) {

        val currentLayersSnapshot = layers.map { it.copy(elements = it.elements.toMutableStateList()) }

        val currentComponentLibrary = componentLibrary.toMap()

        val savedCanvasSizeConfig = canvasSizeConfig

        val savedPdfExportBoundsMode = pdfExportBoundsMode

        val savedProjectId = projectId

        val savedBgColor = backgroundColor
        val savedBgStyle = backgroundStyle

        val savedGridConfig = gridConfig

        val savedScaleConfig = scaleConfig



        launchIO {

            try {

                // Determine bounds mode based on canvas size configuration

                val boundsMode = if (savedCanvasSizeConfig != null) {

                    com.sketcher.sketchercompanionv1.utils.PdfExporter.BoundsMode.CANVAS_SIZE

                } else {

                    savedPdfExportBoundsMode

                }



                val config = com.sketcher.sketchercompanionv1.utils.PdfExporter.PdfExportConfig(

                    boundsMode = boundsMode,

                    includeBackground = true,

                    dpi = 300

                )



                // Use canvas size config dimensions if available, otherwise use reasonable defaults

                val width = savedCanvasSizeConfig?.widthInPixels ?: 2480f // A4 at 300 DPI

                val height = savedCanvasSizeConfig?.heightInPixels ?: 3508f // A4 at 300 DPI

                

                val projectData = ProjectData(

                    id = savedProjectId,

                    layers = currentLayersSnapshot.map { it.toLayerJson() },

                    backgroundConfig = BackgroundConfig(color = savedBgColor, gridConfig = savedGridConfig, fillStyle = savedBgStyle.toFillStyleJson()),

                    paletteColors = emptyList(),

                    toolConfigs = emptyMap(),

                    canvasMetadata = CanvasMetadata(

                        width = width,

                        height = height,

                        cameraMatrix = emptyList(), // Camera matrix handled by PdfExporter

                        scaleConfig = savedScaleConfig

                    ),

                    componentLibrary = currentComponentLibrary.mapValues { it.value.toComponentDefinitionJson() },

                    uiPresetName = toolbarManager.activeUiPresetName.value

                )



                com.sketcher.sketchercompanionv1.utils.PdfExporter.export(

                    context = context,

                    uri = uri,

                    layers = currentLayersSnapshot,

                    projectData = projectData,

                    config = config,

                    componentLibrary = currentComponentLibrary,

                    canvasSizeConfig = savedCanvasSizeConfig

                )

            } catch (e: Exception) {

                e.printStackTrace()

            }

        }

    }

    fun generateTempPdfForPrinting(
        context: Context,
        tempFile: java.io.File,
        boundsMode: com.sketcher.sketchercompanionv1.utils.PdfExporter.BoundsMode,
        callback: (Boolean) -> Unit
    ) {
        saveCurrentPageState()
        val currentPagesSnapshot = pages.map { page ->
            page.copy(
                layers = page.layers.map { layer ->
                    layer.copy(elements = layer.elements.toMutableStateList())
                }.toMutableStateList()
            )
        }
        val currentComponentLibrary = componentLibrary.toMap()

        launchIO {
            try {
                if (!tempFile.exists()) {
                    tempFile.parentFile?.mkdirs()
                    tempFile.createNewFile()
                }

                val success = com.sketcher.sketchercompanionv1.utils.PdfExporter.exportPages(
                    context = context,
                    uri = android.net.Uri.fromFile(tempFile),
                    pages = currentPagesSnapshot,
                    infiniteCanvasBoundsMode = boundsMode,
                    componentLibrary = currentComponentLibrary
                )

                withContext(Dispatchers.Main) {
                    callback(success)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    private var eraserDragActive = false
    private val eraserSnapshots = mutableMapOf<MutableList<LayerElement>, List<LayerElement>>()

    fun startEraserDrag() {
        eraserDragActive = true
        eraserSnapshots.clear()
        val containers = if (editingContext != null) {
            listOf(activeContainer)
        } else {
            if (selectionScope == SelectionScope.ALL_LAYERS) {
                layers.map { it.elements }
            } else {
                val activeIndex = activeLayerIndex
                val activeLayer = layers.getOrNull(activeIndex)
                if (activeLayer != null) listOf(activeLayer.elements) else emptyList()
            }
        }
        for (container in containers) {
            eraserSnapshots[container] = synchronized(container) { container.toList() }
        }
    }

    fun endEraserDrag() {
        if (!eraserDragActive) return
        eraserDragActive = false

        val commands = mutableListOf<UndoCommand>()
        for ((container, snapshot) in eraserSnapshots) {
            val currentList = synchronized(container) { container.toList() }
            val elementsRemoved = snapshot.filter { it !in currentList }
            val elementsAdded = currentList.filter { it !in snapshot }

            if (elementsRemoved.isNotEmpty() || elementsAdded.isNotEmpty()) {
                commands.add(com.sketcher.sketchercompanionv1.command.PointEraseCommand(container, elementsRemoved, elementsAdded))
            }
        }

        if (commands.isNotEmpty()) {
            val finalCommand = if (commands.size == 1) commands.first() else com.sketcher.sketchercompanionv1.command.CompoundCommand(commands, "Borrador de Puntos")
            undoStack.push(finalCommand)
            if (undoStack.size > 100) undoStack.removeLast()
            redoStack.clear()
            updateUndoRedoSupport()
        }
        eraserSnapshots.clear()
    }

    fun erase(x: Float, y: Float, diameterPx: Float): Boolean {
        var changed = false

        // Convert diameterPx to World Radius
        val radiusWorld = com.sketcher.sketchercompanionv1.utils.UnitUtils.pixelsToProjectUnits(
            diameterPx, currentUnit, scaleConfig.basePixelsPerMillimeter
        ) / 2f

        val isLocalSession = !eraserDragActive
        if (isLocalSession) {
            startEraserDrag()
        }

        if (currentTool == ToolType.POINT_ERASER) {
            val containersToCheck = if (editingContext != null) {
                listOf(activeContainer)
            } else {
                if (selectionScope == SelectionScope.ALL_LAYERS) {
                    layers.map { it.elements }
                } else {
                    val activeIndex = activeLayerIndex
                    val activeLayer = layers.getOrNull(activeIndex)
                    if (activeLayer != null) listOf(activeLayer.elements) else emptyList()
                }
            }

            for (container in containersToCheck) {
                if (erasePartialInContainer(container, x, y, radiusWorld)) {
                    changed = true
                }
            }
        } else {
            // Legacy full-stroke eraser
            if (editingContext != null) {
                val hits = mutableListOf<LayerElement>()
                val elementsSnapshot = synchronized(activeContainer) { activeContainer.toList() }
                for (element in elementsSnapshot) {
                    val bounds = element.getBoundingBox(componentLibrary)
                    if (RectF.intersects(bounds, RectF(x - radiusWorld, y - radiusWorld, x + radiusWorld, y + radiusWorld))) {
                        hits.add(element)
                    }
                }
                if (hits.isNotEmpty()) {
                    for (element in hits) {
                        performAction(EraseCommand(activeContainer, element))
                        changed = true
                    }
                }
            } else {
                // Normal mode (non-editing)
                val hitsWithLayer = mutableListOf<Pair<Layer, LayerElement>>()
                val layersSnapshot = synchronized(layers) {
                    layers.toList().map { layer ->
                        val elementsSnapshot = synchronized(layer.elements) {
                            layer.elements.toList()
                        }
                        layer to elementsSnapshot
                    }
                }

                val layersToCheck = if (selectionScope == SelectionScope.ALL_LAYERS) {
                    layersSnapshot
                } else {
                    val activeIndex = activeLayerIndex
                    if (activeIndex in layersSnapshot.indices) {
                        listOf(layersSnapshot[activeIndex])
                    } else {
                        emptyList()
                    }
                }

                for ((layer, elements) in layersToCheck) {
                    for (element in elements) {
                        val bounds = element.getBoundingBox(componentLibrary)
                        if (RectF.intersects(bounds, RectF(x - radiusWorld, y - radiusWorld, x + radiusWorld, y + radiusWorld))) {
                            hitsWithLayer.add(layer to element)
                        }
                    }
                }

                if (hitsWithLayer.isNotEmpty()) {
                    for ((layer, element) in hitsWithLayer) {
                        performAction(EraseCommand(layer.elements, element))
                        changed = true
                    }
                }
            }
        }

        if (isLocalSession) {
            endEraserDrag()
        }

        if (changed) {
            notifyLayersChanged()
        }

        return changed
    }

    fun cutWithPath(eraserPath: android.graphics.Path): Boolean {
        var changed = false
        val containersToCheck = if (editingContext != null) {
            listOf(activeContainer)
        } else {
            if (selectionScope == SelectionScope.ALL_LAYERS) {
                layers.map { it.elements }
            } else {
                val activeIndex = activeLayerIndex
                val activeLayer = layers.getOrNull(activeIndex)
                if (activeLayer != null) listOf(activeLayer.elements) else emptyList()
            }
        }

        for (container in containersToCheck) {
            if (cutWithPathInContainer(container, eraserPath)) {
                changed = true
            }
        }
        if (changed) {
            notifyLayersChanged()
        }
        return changed
    }

    private fun cutWithPathInContainer(
        container: MutableList<LayerElement>,
        eraserPath: android.graphics.Path
    ): Boolean {
        val zoom = run {
            val vals = FloatArray(9)
            _cameraMatrix.value.getValues(vals)
            val scale = kotlin.math.sqrt(vals[android.graphics.Matrix.MSCALE_X] * vals[android.graphics.Matrix.MSCALE_X] + vals[android.graphics.Matrix.MSKEW_X] * vals[android.graphics.Matrix.MSKEW_X])
            if (scale > 0.001f) scale else 1.0f
        }
        var changed = false
        val toRemove = mutableListOf<LayerElement>()
        val toAdd = mutableListOf<LayerElement>()

        val snapshot = synchronized(container) { container.toList() }
        val eraserBounds = RectF()
        eraserPath.computeBounds(eraserBounds, true)

        for (element in snapshot) {
            val bounds = element.getBoundingBox(componentLibrary)
            if (!RectF.intersects(bounds, eraserBounds)) continue

            when (element) {
                is VectorStroke -> {
                    val newPath = android.graphics.Path()
                    val success = newPath.op(element.path, eraserPath, android.graphics.Path.Op.DIFFERENCE)
                    if (success) {
                        toRemove.add(element)
                        if (!newPath.isEmpty) {
                            val newFillPath = if (element.fillPath != null) {
                                val fp = android.graphics.Path()
                                if (fp.op(element.fillPath, eraserPath, android.graphics.Path.Op.DIFFERENCE)) fp else null
                            } else null

                            val step = (8f / zoom).coerceAtLeast(1.0f)
                            val outlinePoints = com.sketcher.sketchercompanionv1.utils.GeometryUtils.flattenPath(newPath, step = step)
                            val newPoints = outlinePoints.map { pt -> StrokePoint(pt.x, pt.y, 0.5f) }

                            val updatedStroke = element.copy(
                                path = newPath,
                                fillPath = newFillPath,
                                points = newPoints
                            )
                            toAdd.add(updatedStroke)
                        }
                        changed = true
                    }
                }
                is FillData -> {
                    val newPath = android.graphics.Path()
                    val success = newPath.op(element.path, eraserPath, android.graphics.Path.Op.DIFFERENCE)
                    if (success) {
                        toRemove.add(element)
                        if (!newPath.isEmpty) {
                            val updatedFill = element.copy(path = newPath)
                            toAdd.add(updatedFill)
                        }
                        changed = true
                    }
                }
                else -> {}
            }
        }

        if (changed) {
            synchronized(container) {
                val firstIndex = toRemove.map { container.indexOf(it) }.filter { it != -1 }.minOrNull()
                container.removeAll(toRemove)
                if (firstIndex != null && firstIndex <= container.size) {
                    container.addAll(firstIndex, toAdd)
                } else {
                    container.addAll(toAdd)
                }
            }
        }
        return changed
    }

    private fun erasePartialInContainer(
        container: MutableList<LayerElement>,
        x: Float,
        y: Float,
        radiusWorld: Float
    ): Boolean {
        val zoom = run {
            val vals = FloatArray(9)
            _cameraMatrix.value.getValues(vals)
            val scale = kotlin.math.sqrt(vals[android.graphics.Matrix.MSCALE_X] * vals[android.graphics.Matrix.MSCALE_X] + vals[android.graphics.Matrix.MSKEW_X] * vals[android.graphics.Matrix.MSKEW_X])
            if (scale > 0.001f) scale else 1.0f
        }
        var changed = false
        val toRemove = mutableListOf<LayerElement>()
        val toAdd = mutableListOf<LayerElement>()

        val snapshot = synchronized(container) { container.toList() }
        val eraserBounds = RectF(x - radiusWorld, y - radiusWorld, x + radiusWorld, y + radiusWorld)

        for (element in snapshot) {
            val bounds = element.getBoundingBox(componentLibrary)
            if (!RectF.intersects(bounds, eraserBounds)) continue

            when (element) {
                is VectorStroke -> {
                    // Borrado por puntos (línea media)
                    val segments = mutableListOf<MutableList<StrokePoint>>()
                    var currentSeg = mutableListOf<StrokePoint>()
                        val radiusSq = radiusWorld * radiusWorld

                        for (pt in element.points) {
                            val inEraser = if (currentEraserShape == com.sketcher.sketchercompanionv1.dto.EraserShape.SQUARE) {
                                kotlin.math.abs(pt.x - x) < radiusWorld && kotlin.math.abs(pt.y - y) < radiusWorld
                            } else {
                                val dx = pt.x - x
                                val dy = pt.y - y
                                dx * dx + dy * dy < radiusSq
                            }
                            if (inEraser) {
                                if (currentSeg.isNotEmpty()) {
                                    segments.add(currentSeg)
                                    currentSeg = mutableListOf()
                                }
                            } else {
                                currentSeg.add(pt)
                            }
                        }
                        if (currentSeg.isNotEmpty()) {
                            segments.add(currentSeg)
                        }

                        // Si hubo cambios en los puntos
                        if (segments.size != 1 || segments[0].size != element.points.size) {
                            toRemove.add(element)
                            changed = true

                            for (segment in segments) {
                                if (segment.size >= 2 || (segment.isNotEmpty() && element.strokeType == StrokeType.FREEHAND)) {
                                    val isMeshBrush = element.brushType == "FREEHAND" || element.brushType == "PEN" || element.brushType == "PLUMA" || element.brushType == "PENCIL_CUMULATIVE" || element.brushType == "PAINT" || element.brushType == "WATERCOLOR"
                                    val (newPath, leftPts, rightPts) = if (element.isFlattened) {
                                        val p = android.graphics.Path().apply {
                                            if (segment.isNotEmpty()) {
                                                moveTo(segment[0].x, segment[0].y)
                                                for (i in 1 until segment.size) {
                                                    lineTo(segment[i].x, segment[i].y)
                                                }
                                                close()
                                            }
                                        }
                                        Triple(p, emptyList(), emptyList())
                                    } else if (isMeshBrush) {
                                        val toolType = when (element.brushType) {
                                            "PLUMA" -> ToolType.PLUMA
                                            "PAINT" -> ToolType.PAINT
                                            "WATERCOLOR" -> ToolType.WATERCOLOR
                                            else -> ToolType.FREEHAND
                                        }
                                        val settings = toolManager.getToolConfigMap()[toolType]?.settings?.toFreehandSettings(toolType) ?: FreehandSettings()
                                        val genResult = PerfectFreehandGenerator.generate(
                                            segment,
                                            settings.copy(size = element.maxWidth, isComplete = true),
                                            zoom = zoom
                                        )
                                        Triple(genResult.path, genResult.left, genResult.right)
                                    } else {
                                        val cp = com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(element.strokeType, segment)
                                        Triple(cp, emptyList(), emptyList())
                                    }

                                    var newFillPath: android.graphics.Path? = null
                                    if (element.isFillEnabled && segment.size >= 3) {
                                        newFillPath = android.graphics.Path().apply {
                                            moveTo(segment[0].x, segment[0].y)
                                            for (i in 1 until segment.size) {
                                                lineTo(segment[i].x, segment[i].y)
                                            }
                                            close()
                                        }
                                    }

                                    val subStroke = element.copy(
                                        points = segment,
                                        path = newPath,
                                        fillPath = newFillPath,
                                        leftPoints = leftPts,
                                        rightPoints = rightPts
                                    )
                                    toAdd.add(subStroke)
                                }
                            }
                        }
                }
                is FillData -> {
                    // Corte booleano de relleno
                    val eraserPath = android.graphics.Path().apply {
                        if (currentEraserShape == com.sketcher.sketchercompanionv1.dto.EraserShape.SQUARE) {
                            addRect(x - radiusWorld, y - radiusWorld, x + radiusWorld, y + radiusWorld, android.graphics.Path.Direction.CW)
                        } else {
                            addCircle(x, y, radiusWorld, android.graphics.Path.Direction.CW)
                        }
                    }
                    val newPath = android.graphics.Path()
                    val success = newPath.op(element.path, eraserPath, android.graphics.Path.Op.DIFFERENCE)
                    if (success) {
                        toRemove.add(element)
                        if (!newPath.isEmpty) {
                            val updatedFill = element.copy(path = newPath)
                            toAdd.add(updatedFill)
                        }
                        changed = true
                    }
                }
                else -> {}
            }
        }

        if (changed) {
            synchronized(container) {
                val firstIndex = toRemove.map { container.indexOf(it) }.filter { it != -1 }.minOrNull()
                container.removeAll(toRemove)
                if (firstIndex != null && firstIndex <= container.size) {
                    container.addAll(firstIndex, toAdd)
                } else {
                    container.addAll(toAdd)
                }
            }
        }

        return changed
    }

    fun clear() {

        undoStack.clear()

        redoStack.clear()

        updateUndoRedoSupport()

        componentLibrary.clear()

        projectId = java.util.UUID.randomUUID().toString()

        toolManager.resetAllPresetOverrideStates()

        currentFileUri = null
        hasUnsavedChanges = false
        hasUnsavedChangesSinceLastAutosave = false

        backgroundColor = android.graphics.Color.WHITE
        backgroundStyle = FillStyle.Solid(android.graphics.Color.WHITE)
        canvasSizeConfig = null

        gridConfig = GridConfig()

        

        // Reset camera

        val identity = android.graphics.Matrix()

        identity.getValues(cameraMatrixValues)

        identity.getValues(homeCameraMatrixValues)

        prefs.edit().remove("home_camera_matrix_v4").apply()

        cameraUpdateTrigger++

        

        val newLayer = Layer("l_${System.currentTimeMillis()}", "Capa 1", androidx.compose.runtime.mutableStateListOf())

        pages.clear()
        val defaultPage = CanvasPage(
            id = java.util.UUID.randomUUID().toString(),
            name = "Página 1",
            layers = mutableStateListOf(newLayer),
            activeLayerIndex = 0,
            backgroundColor = android.graphics.Color.WHITE,
            backgroundStyle = FillStyle.Solid(android.graphics.Color.WHITE),
            gridConfig = GridConfig(),
            canvasSizeConfig = null,
            cameraMatrixValues = FloatArray(9).apply { identity.getValues(this) },
            scaleConfig = ScaleConfig(),
            currentUnit = DistanceUnit.MM
        )
        pages.add(defaultPage)
        activePageIndex = 0

        layerManager.internalUpdateLayers(mutableListOf(newLayer), 0)

        selectionManager.clearSelection()

        notifyLayersChanged()

    }



    fun saveTemplate(context: Context, name: String) {

         saveCurrentPageState()
         val currentPagesJson = pages.map { it.toPageJson() }
         val currentActivePageIndex = activePageIndex
         val allPagesLayersSnapshot = pages.flatMap { page ->
             page.layers.map { layer -> layer.copy(elements = layer.elements.toMutableStateList()) }
         }

         val currentComponentLibrary = componentLibrary.values.toList()

         val savedProjectId = projectId

         val savedBgColor = backgroundColor
         val savedBgStyle = backgroundStyle

         val savedGridConfig = gridConfig

         val savedScaleConfig = scaleConfig



         launchIO {

             // Construct ProjectData from current state for saving

             val projectData = com.sketcher.sketchercompanionv1.dto.ProjectData(

                 id = savedProjectId,

                 layers = pages.firstOrNull()?.layers?.map { it.toLayerJson() } ?: emptyList(),

                 backgroundConfig = com.sketcher.sketchercompanionv1.dto.BackgroundConfig(savedBgColor, savedGridConfig, savedBgStyle.toFillStyleJson()),

                 paletteColors = emptyList(),

                 toolConfigs = emptyMap(), // Simplify for now or map toolConfigs

                 canvasMetadata = com.sketcher.sketchercompanionv1.dto.CanvasMetadata(

                     width = 2000f, // Use actualviewport if possible

                     height = 2000f,

                     cameraMatrix = pages.firstOrNull()?.cameraMatrixValues?.toList() ?: emptyList(), // Simplify

                     scaleConfig = savedScaleConfig

                 ),
                 uiPresetName = toolbarManager.activeUiPresetName.value,
                 pages = currentPagesJson,
                 activePageIndex = currentActivePageIndex
             )

             

             com.sketcher.sketchercompanionv1.utils.TemplateManager.saveAsTemplate(

                 context = context, 

                 projectData = projectData,

                 layers = allPagesLayersSnapshot,

                 components = currentComponentLibrary,

                 templateName = name

             )

         }

    }



    fun loadFromTemplate(context: Context, file: java.io.File) {

        launchIO {

            try {

                val (projectData, _, _) = com.sketcher.sketchercompanionv1.utils.TemplateManager.loadTemplate(context, file)

                

                withContext(Dispatchers.Main) {

                    performSnapshotAction("Cargar Plantilla") {

                        val newLayers = mutableListOf<Layer>()

                        projectData.layers.forEach { lJson ->

                            val l = Layer(

                                id = lJson.id, 

                                name = lJson.name, 

                                elements = mutableStateListOf(), 

                                isVisible = lJson.isVisible, 

                                opacity = lJson.opacity,

                                isVisibleOnClient = lJson.isVisibleOnClient ?: false

                            )

                            newLayers.add(l)

                        }

                        layerManager.internalUpdateLayers(newLayers, 0)

                        if (layers.isEmpty()) layerManager.addNewLayer(true)

                    }

                    _themeConfig.value = themeRepository.getTheme()

                    reloadToolbarLayout()

                }

            } catch (e: Exception) {

                e.printStackTrace()

            }

        }

    }

    // --- DXF SUPPORT ---

    fun addImportedStrokes(strokes: List<VectorStroke>, scaleToFit: Boolean) {

        if (strokes.isEmpty()) return

        performSnapshotAction("Importar Trazos") {

            // 1. Calculate Bounds of Imported Strokes

            var minX = Float.MAX_VALUE

            var minY = Float.MAX_VALUE

            var maxX = -Float.MAX_VALUE

            var maxY = -Float.MAX_VALUE

            

            strokes.forEach { stroke ->

                stroke.points.forEach { p ->

                    if (p.x < minX) minX = p.x

                    if (p.x > maxX) maxX = p.x

                    if (p.y < minY) minY = p.y

                    if (p.y > maxY) maxY = p.y

                }

            }

            

            val importWidth = maxX - minX

            val importHeight = maxY - minY

            

            // 2. Determine Scale and Offset

            val matrix = Matrix()

            

            if (scaleToFit && importWidth > 0 && importHeight > 0) {

                // Get visible viewport or canvas size

                val targetWidth = if (canvasSizeConfig != null) canvasSizeConfig!!.widthInPixels else lastViewportWidth

                val targetHeight = if (canvasSizeConfig != null) canvasSizeConfig!!.heightInPixels else lastViewportHeight

                

                if (targetWidth > 0 && targetHeight > 0) {

                     val scaleX = targetWidth / importWidth

                     val scaleY = targetHeight / importHeight

                     val scale = kotlin.math.min(scaleX, scaleY) * 0.8f // 80% fill

                     

                     val cx = (minX + maxX) / 2f

                     val cy = (minY + maxY) / 2f

                     val isCenterOrigin = canvasSizeConfig?.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER

                     val targetCx = if (isCenterOrigin) 0f else targetWidth / 2f

                     val targetCy = if (isCenterOrigin) 0f else targetHeight / 2f

                     

                     matrix.postTranslate(-cx, -cy)

                     matrix.postScale(scale, scale)

                     matrix.postTranslate(targetCx, targetCy)

                }

            } else {

                // Just center if no scale

                 val cx = (minX + maxX) / 2f

                 val cy = (minY + maxY) / 2f

                 val targetWidth = if (canvasSizeConfig != null) canvasSizeConfig!!.widthInPixels else lastViewportWidth

                 val targetHeight = if (canvasSizeConfig != null) canvasSizeConfig!!.heightInPixels else lastViewportHeight

                 val isCenterOrigin = canvasSizeConfig?.origin == com.sketcher.sketchercompanionv1.dto.CoordinateOrigin.CENTER

                 val targetCx = if (isCenterOrigin) 0f else targetWidth / 2f

                 val targetCy = if (isCenterOrigin) 0f else targetHeight / 2f

                 

                 matrix.postTranslate(-cx, -cy)

                 matrix.postTranslate(targetCx, targetCy)

            }



            // 3. Transform and Add

            val transformedStrokes = strokes.map { stroke ->

                 val centerlinePath = android.graphics.Path(stroke.path)

                 centerlinePath.transform(matrix) // Transform centerline directly

                 

                 // Expand centerline to outline (Filled Shape) matches renderer expectation

                 val outlinePath = android.graphics.Path()

                 val strokePaint = android.graphics.Paint().apply {

                     style = android.graphics.Paint.Style.STROKE

                     strokeWidth = stroke.maxWidth

                     strokeCap = android.graphics.Paint.Cap.ROUND

                     strokeJoin = android.graphics.Paint.Join.ROUND

                 }

                 strokePaint.getFillPath(centerlinePath, outlinePath)

                 

                 // Sample points from centerline (not outline) for physics/logic

                 val newPoints = if (stroke.points.isNotEmpty()) {

                     stroke.points.map { p ->

                         val pts = floatArrayOf(p.x, p.y)

                         matrix.mapPoints(pts)

                         StrokePoint(pts[0], pts[1], p.pressure)

                     }

                 } else {

                     val pm = android.graphics.PathMeasure(centerlinePath, false)

                     val pathLength = pm.length

                     val numPoints = (pathLength / 2f).toInt().coerceAtLeast(2)

                     val sampledPoints = mutableListOf<StrokePoint>()

                     val pos = floatArrayOf(0f, 0f)

                     val tan = floatArrayOf(0f, 0f)

                     

                     for (i in 0..numPoints) {

                         val distance = (i.toFloat() / numPoints) * pathLength

                         pm.getPosTan(distance, pos, tan)

                         sampledPoints.add(StrokePoint(pos[0], pos[1], 0.5f))

                     }

                     sampledPoints

                 }

                 

                 stroke.copy(points = newPoints, path = outlinePath)

            }

            

            if (layers.isNotEmpty()) {

                val newList = layers.toMutableList()

                val activeLayer = newList[activeLayerIndex]

                activeLayer.elements.addAll(transformedStrokes)

                newList[activeLayerIndex] = activeLayer.copy()

                layerManager.internalUpdateLayers(newList, activeLayerIndex)

            }

        }

    }

    

    fun exportDxf(context: Context, uri: android.net.Uri) {

         val currentLayersSnapshot = layers.map { it.copy(elements = it.elements.toMutableStateList()) }

         // Run export in IO

         viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {

             try {

                 context.contentResolver.openOutputStream(uri)?.use { outputStream ->

                     // Support exporting all layers with structure

                     // If selection only is needed later, we would need to filter layers

                     com.sketcher.sketchercompanionv1.exporters.DxfExporter.export(currentLayersSnapshot, outputStream)

                 }

             } catch (e: Exception) {

                 e.printStackTrace()

             }

         }

    }

    

    private fun collectStrokes(elements: List<LayerElement>, target: MutableList<VectorStroke>, parentMatrix: Matrix) {

        elements.forEach { element ->

            if (element is VectorStroke) {

                // Apply parent matrix (Group transforms)

                val m = Matrix(parentMatrix) 

                val copy = element.copyElement() as VectorStroke

                copy.transform(m)

                target.add(copy)

            } else if (element is GroupElement) {

                val newMatrix = Matrix(parentMatrix)

                newMatrix.preConcat(element.matrix)

                collectStrokes(element.elements, target, newMatrix)

            }

        }

    }



    var isMultiStepStrokeInProgress by mutableStateOf(false)

        private set



    fun updateMultiStepStrokeInProgress(inProgress: Boolean) {

        isMultiStepStrokeInProgress = inProgress

    }

    // ── LIVE PROJECTION ─────────────────────────────────────────────────

    var isProjectionActive by mutableStateOf(false)
        private set

    var projectionUrl by mutableStateOf("")
        private set

    var projectionClientCount by mutableIntStateOf(0)
        private set

    var isProjectionPaused by mutableStateOf(false)
        private set

    var projectionMode by mutableStateOf("sync") // "sync" | "fixed"
        private set

    private val _fixedZoomMode = mutableStateOf("fit")
    var fixedZoomMode: String
        get() = _fixedZoomMode.value
        set(value) {
            _fixedZoomMode.value = value
            liveProjectionController.updateFixedZoomMode(value)
        }

    data class ProjectionViewport(val left: Float, val top: Float, val right: Float, val bottom: Float, val color: Int, val label: String)

    var projectionViewports by mutableStateOf<List<ProjectionViewport>>(emptyList())
        private set

    fun toggleProjectionPause() {
        liveProjectionController.togglePause()
        isProjectionPaused = liveProjectionController.isProjectionPaused
    }

    fun updateProjectionMode(mode: String) {
        liveProjectionController.updateMode(mode)
        projectionMode = liveProjectionController.projectionMode
    }

    // ── WIRELESS SECONDARY DISPLAY PROJECTION ───────────────────────────

    @Volatile var wirelessProjectionManager: com.sketcher.sketchercompanionv1.projection.WirelessProjectionManager? = null

    var isWirelessProjectionActive by mutableStateOf(false)
        private set

    fun startWirelessProjection() {
        isWirelessProjectionActive = true
    }

    fun stopWirelessProjection() {
        isWirelessProjectionActive = false
    }

    fun startProjection() {
        liveProjectionController.start()
    }

    fun stopProjection() {
        liveProjectionController.stop()
    }

    fun renderAndSendSyncFrame(
        livePoints: List<StrokePoint>?,
        livePath: android.graphics.Path?,
        committedPath: android.graphics.Path?,
        liveFillPath: android.graphics.Path?,
        liveRadius: Float
    ) {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) return
        liveProjectionController.renderAndSendSyncFrame(
            layers = layers,
            componentLibrary = componentLibrary,
            backgroundStyle = backgroundStyle,
            cameraMatrixValues = cameraMatrixValues,
            strokeColor = strokeColor.value,
            fillColor = fillColor.value,
            isStrokeActive = isStrokeActive.value,
            isFillActive = isFillActive.value,
            fillStyle = fillStyle.value,
            strokeStyle = strokeStyle.value,
            livePoints = livePoints,
            livePath = livePath,
            committedPath = committedPath,
            liveFillPath = liveFillPath,
            liveRadius = liveRadius
        )
    }

    fun renderAndSendFixedSnapshot(
        livePoints: List<StrokePoint>?,
        livePath: android.graphics.Path?,
        committedPath: android.graphics.Path?,
        liveFillPath: android.graphics.Path?,
        liveRadius: Float
    ) {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) return
        liveProjectionController.renderAndSendFixedSnapshot(
            layers = layers,
            componentLibrary = componentLibrary,
            backgroundStyle = backgroundStyle,
            homeCameraMatrixValues = homeCameraMatrixValues,
            strokeColor = strokeColor.value,
            fillColor = fillColor.value,
            isStrokeActive = isStrokeActive.value,
            isFillActive = isFillActive.value,
            fillStyle = fillStyle.value,
            strokeStyle = strokeStyle.value,
            livePoints = livePoints,
            livePath = livePath,
            committedPath = committedPath,
            liveFillPath = liveFillPath,
            liveRadius = liveRadius
        )
    }

    override fun onCleared() {

        stopProjection()

        super.onCleared()

    }


    private val _globalLibraryItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    val globalLibraryItems: StateFlow<List<LibraryItem>> = _globalLibraryItems.asStateFlow()

    fun loadGlobalLibrary(context: Context) {
        viewModelScope.launch {
            _globalLibraryItems.value = LibraryManager.loadLibrary(context)
        }
    }

    fun saveGlobalLibrary(context: Context) {
        viewModelScope.launch {
            LibraryManager.saveLibrary(context, _globalLibraryItems.value)
            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
                launchIO {
                    try {
                        val localLibraryFile = getLibraryFile(context)
                        val libraryJson = localLibraryFile.readText()
                        val timestamp = localLibraryFile.lastModified()
                        val assetsDir = getLibraryAssetsDir(context)
                        val backupRes = cloudSyncRepository.backupLibrary(libraryJson, timestamp, assetsDir)
                        if (backupRes.isSuccess) {
                            setLibraryLastUploadedTime(context, timestamp)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun addToGlobalLibrary(context: Context, name: String, parentId: String?) {
        val selected = selectionManager.selectedElements.toList()
        if (selected.size == 1 && selected.first() is ComponentInstance) {
            val instance = selected.first() as ComponentInstance
            val definition = componentLibrary[instance.definitionId] ?: return
            
            var thumbnailName: String? = null
            val bounds = instance.getBoundingBox(componentLibrary)
            if (!bounds.isEmpty) {
                try {
                    val size = 256
                    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    
                    val scaleX = size / bounds.width()
                    val scaleY = size / bounds.height()
                    val scale = java.lang.Math.min(scaleX, scaleY) * 0.8f
                    
                    val dx = (size - bounds.width() * scale) / 2f
                    val dy = (size - bounds.height() * scale) / 2f
                    
                    val m = android.graphics.Matrix()
                    m.postTranslate(-bounds.left, -bounds.top)
                    m.postScale(scale, scale)
                    m.postTranslate(dx, dy)
                    
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.concat(m)
                    
                    com.sketcher.sketchercompanionv1.RenderEngine().drawElementRecursive(
                        canvas,
                        instance,
                        componentLibrary,
                        m,
                        1f
                    )
                    
                    val thumbFile = "thumb_" + java.util.UUID.randomUUID().toString() + ".png"
                    val assetsDir = getLibraryAssetsDir(context)
                    if (!assetsDir.exists()) assetsDir.mkdirs()
                    val out = java.io.FileOutputStream(java.io.File(assetsDir, thumbFile))
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    out.close()
                    bitmap.recycle()
                    
                    thumbnailName = thumbFile
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val newId = "lib_comp_" + java.util.UUID.randomUUID().toString()
            val newItem = LibraryComponent(newId, name, parentId, definition, thumbnailName)
            _globalLibraryItems.value = _globalLibraryItems.value + newItem
            saveGlobalLibrary(context)
        }
    }

    fun createLibraryFolder(context: Context, name: String, parentId: String?) {
        val newId = "lib_folder_" + java.util.UUID.randomUUID().toString()
        val newItem = LibraryFolder(newId, name, parentId)
        _globalLibraryItems.value = _globalLibraryItems.value + newItem
        saveGlobalLibrary(context)
    }

    fun deleteLibraryItem(context: Context, id: String) {
        deleteLibraryItems(context, setOf(id))
    }

    fun deleteLibraryItems(context: Context, ids: Set<String>) {
        fun getChildrenIds(parentId: String): List<String> {
            val children = _globalLibraryItems.value.filter { it.parentId == parentId }
            return children.map { it.id } + children.flatMap { getChildrenIds(it.id) }
        }
        val toDelete = ids + ids.flatMap { getChildrenIds(it) }.toSet()
        _globalLibraryItems.value = _globalLibraryItems.value.filterNot { it.id in toDelete }
        saveGlobalLibrary(context)
    }

    fun moveLibraryItem(context: Context, id: String, newParentId: String?) {
        moveLibraryItems(context, setOf(id), newParentId)
    }

    fun moveLibraryItems(context: Context, ids: Set<String>, newParentId: String?) {
        _globalLibraryItems.value = _globalLibraryItems.value.map {
            if (it.id in ids) {
                when (it) {
                    is LibraryFolder -> it.copy(parentId = newParentId)
                    is LibraryComponent -> it.copy(parentId = newParentId)
                    else -> it
                }
            } else it
        }
        saveGlobalLibrary(context)
    }

    fun renameLibraryItem(context: Context, id: String, newName: String) {
        _globalLibraryItems.value = _globalLibraryItems.value.map {
            if (it.id == id) {
                when (it) {
                    is LibraryFolder -> it.copy(name = newName)
                    is LibraryComponent -> it.copy(name = newName)
                    else -> it
                }
            } else it
        }
        saveGlobalLibrary(context)
    }

    fun addElementToGlobalLibrary(context: Context, name: String, element: LayerElement, parentId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val definitionId = "def_" + java.util.UUID.randomUUID().toString()
            val definition = ComponentDefinition(definitionId, mutableListOf(element), creationScale = scaleConfig.globalScaleRatio)
            
            var thumbnailName: String? = null
            val bounds = element.getBoundingBox(emptyMap())
            if (!bounds.isEmpty) {
                try {
                    val size = 256
                    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    
                    val scaleX = size / bounds.width()
                    val scaleY = size / bounds.height()
                    val scale = java.lang.Math.min(scaleX, scaleY) * 0.8f
                    
                    val dx = (size - bounds.width() * scale) / 2f
                    val dy = (size - bounds.height() * scale) / 2f
                    
                    val m = android.graphics.Matrix()
                    m.postTranslate(-bounds.left, -bounds.top)
                    m.postScale(scale, scale)
                    m.postTranslate(dx, dy)
                    
                    canvas.drawColor(android.graphics.Color.WHITE)
                    canvas.concat(m)
                    
                    com.sketcher.sketchercompanionv1.RenderEngine().drawElementRecursive(
                        canvas,
                        element,
                        emptyMap(),
                        m,
                        1f
                    )
                    
                    val thumbFile = "thumb_" + java.util.UUID.randomUUID().toString() + ".png"
                    val assetsDir = getLibraryAssetsDir(context)
                    if (!assetsDir.exists()) assetsDir.mkdirs()
                    val out = java.io.FileOutputStream(java.io.File(assetsDir, thumbFile))
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    out.close()
                    bitmap.recycle()
                    
                    thumbnailName = thumbFile
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            val newId = "lib_comp_" + java.util.UUID.randomUUID().toString()
            val newItem = LibraryComponent(newId, name, parentId, definition, thumbnailName)
            
            withContext(Dispatchers.Main) {
                componentLibrary[definitionId] = definition
                _globalLibraryItems.value = _globalLibraryItems.value + newItem
                saveGlobalLibrary(context)
            }
        }
    }

    fun addSvgToGlobalLibrary(context: Context, name: String, svgElement: SvgElement, parentId: String?) {
        val group = GroupElement(
            id = java.util.UUID.randomUUID().toString(),
            elements = mutableListOf(svgElement),
            matrix = android.graphics.Matrix()
        )
        addElementToGlobalLibrary(context, name, group, parentId)
    }

    fun addImageToGlobalLibrary(
        context: Context,
        name: String,
        bitmap: android.graphics.Bitmap,
        transparentColors: List<Int>,
        tolerance: Float,
        cropRect: android.graphics.RectF?,
        cropPath: List<android.graphics.PointF>?,
        transparentColorTolerances: List<Float>,
        rotation: Float,
        flipHorizontal: Boolean,
        flipVertical: Boolean,
        calibrationScaleFactor: Float = 1.0f,
        parentId: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val imgFileName = "img_" + java.util.UUID.randomUUID().toString() + ".png"
            val assetsDir = getLibraryAssetsDir(context)
            if (!assetsDir.exists()) assetsDir.mkdirs()
            val destFile = java.io.File(assetsDir, imgFileName)
            try {
                val out = java.io.FileOutputStream(destFile)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val matrix = android.graphics.Matrix()
            if (calibrationScaleFactor != 1.0f) {
                matrix.postScale(calibrationScaleFactor, calibrationScaleFactor)
            }
            
            val imageElement = ImageElement(
                id = java.util.UUID.randomUUID().toString(),
                bitmap = bitmap,
                imageFileName = imgFileName,
                matrix = matrix,
                transparentColors = transparentColors,
                tolerance = tolerance,
                cropRect = cropRect,
                cropPath = cropPath,
                transparentColorTolerances = transparentColorTolerances,
                rotation = rotation,
                flipHorizontal = flipHorizontal,
                flipVertical = flipVertical
            )
            
            addElementToGlobalLibrary(context, name, imageElement, parentId)
        }
    }

    fun addDxfToGlobalLibrary(
        context: Context,
        name: String,
        data: DxfImportData,
        scaleToFit: Boolean,
        defaultStrokeWidth: Float,
        fillClosedShapes: Boolean,
        sourceUnit: DistanceUnit,
        parentId: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val matrix = android.graphics.Matrix()
            if (scaleToFit) {
                val bounds = data.totalBounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    val scale = 200f / kotlin.math.max(bounds.width(), bounds.height())
                    matrix.postTranslate(-bounds.centerX(), -bounds.centerY())
                    matrix.postScale(scale, scale)
                }
            } else {
                val pixelScale = sourceUnit.toMillimeters * scaleConfig.basePixelsPerMillimeter
                matrix.postScale(pixelScale, pixelScale)
            }
            
            val mutableElements = mutableListOf<LayerElement>()
            val pathsByLayer = data.paths.groupBy { it.layerName }
            
            pathsByLayer.entries.forEach { entry ->
                val dxfPaths = entry.value
                dxfPaths.forEach { dp ->
                    val path = android.graphics.Path(dp.path)
                    path.transform(matrix)
                    val strokeColor = dp.color ?: android.graphics.Color.BLACK
                    val points = PathUtils.samplePath(path)
                    val isFilledShape = fillClosedShapes && dp.isClosed
                    
                    val finalPath: android.graphics.Path
                    val brushType: String
                    if (isFilledShape) {
                        finalPath = path
                        brushType = "FILLED_SHAPE"
                    } else {
                        val outlinePath = android.graphics.Path()
                        val paint = android.graphics.Paint().apply {
                            style = android.graphics.Paint.Style.STROKE
                            strokeWidth = defaultStrokeWidth
                            strokeCap = android.graphics.Paint.Cap.ROUND
                            strokeJoin = android.graphics.Paint.Join.ROUND
                        }
                        paint.getFillPath(path, outlinePath)
                        finalPath = outlinePath
                        brushType = "FREEHAND"
                    }
                    
                    val stroke = VectorStroke(
                        points = points,
                        strokeColor = strokeColor,
                        brushType = brushType,
                        strokeType = StrokeType.FREEHAND,
                        maxWidth = defaultStrokeWidth,
                        path = finalPath
                    )
                    mutableElements.add(stroke)
                }
            }
            
            val group = GroupElement(
                id = java.util.UUID.randomUUID().toString(),
                elements = mutableElements,
                matrix = android.graphics.Matrix()
            )
            
            addElementToGlobalLibrary(context, name, group, parentId)
        }
    }

    fun instantiateFromGlobalLibrary(component: LibraryComponent) {
        performSnapshotAction("Insertar de Librería") {
            val defId = "comp_" + java.util.UUID.randomUUID().toString()
            val definition = ComponentDefinition(defId, component.definition.elements.map { it.copyElement() }.toMutableList(), creationScale = component.definition.creationScale)
            componentLibrary[defId] = definition
            
            val viewportCenter = floatArrayOf(lastViewportWidth / 2f, lastViewportHeight / 2f)
            val inverseCamera = android.graphics.Matrix()
            if (_cameraMatrix.value.invert(inverseCamera)) {
                inverseCamera.mapPoints(viewportCenter)
            }
            
            val dummyInstance = ComponentInstance("dummy", defId)
            
            val scaleFactor = component.definition.creationScale / scaleConfig.globalScaleRatio
            dummyInstance.matrix.postScale(scaleFactor, scaleFactor)
            
            val bounds = dummyInstance.getBoundingBox(componentLibrary)
            
            val dx = viewportCenter[0] - bounds.centerX()
            val dy = viewportCenter[1] - bounds.centerY()
            
            val instance = ComponentInstance(
                id = "inst_" + java.util.UUID.randomUUID().toString(),
                definitionId = defId
            )
            instance.isScaleLocked = true
            
            instance.matrix.postScale(scaleFactor, scaleFactor)
            instance.matrix.postTranslate(dx, dy)
            
            activeContainer.add(instance)
            selectionManager.clearSelection()
            selectionManager.selectedElements.add(instance)
            selectionManager.recalculateBaseBounds(componentLibrary)
            if (editingContext == null) {
                notifyLayersChanged()
            }
            enterTransformMode()
        }
    }

    // --- DASHBOARD AND FOLDER MANAGEMENT ---
    var showDashboard by mutableStateOf(true)
    var currentDirectory by mutableStateOf<java.io.File?>(null)
        private set
    val localItems = mutableStateListOf<DashboardItem>()
    val thumbnailCache = mutableStateMapOf<String, android.graphics.Bitmap?>()

    fun clearLocalDataSync(context: Context) {
        clear()
        thumbnailCache.clear()
        _globalLibraryItems.value = emptyList()

        val rootDir = getProjectsRootDir(context)
        if (rootDir.exists()) {
            rootDir.deleteRecursively()
            rootDir.mkdirs()
        }

        val libFile = getLibraryFile(context)
        if (libFile.exists()) {
            libFile.delete()
        }

        val assetsDir = getLibraryAssetsDir(context)
        if (assetsDir.exists()) {
            assetsDir.deleteRecursively()
            assetsDir.mkdirs()
        }

        val pendingPrefs = context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE)
        pendingPrefs.edit().clear().apply()
    }

    fun clearLocalData(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val rootDir = getProjectsRootDir(context)
            if (rootDir.exists()) {
                rootDir.deleteRecursively()
                rootDir.mkdirs()
            }
            val libFile = getLibraryFile(context)
            if (libFile.exists()) {
                libFile.delete()
            }
            val assetsDir = getLibraryAssetsDir(context)
            if (assetsDir.exists()) {
                assetsDir.deleteRecursively()
                assetsDir.mkdirs()
            }
            val pendingPrefs = context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE)
            pendingPrefs.edit().clear().apply()

            withContext(Dispatchers.Main) {
                clear()
                thumbnailCache.clear()
                _globalLibraryItems.value = emptyList()
                refreshLocalItems()
            }
        }
    }

    fun getProjectsRootDir(context: Context): java.io.File {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val dir = java.io.File(context.filesDir, "users/$currentUid/projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getLibraryFile(context: Context): java.io.File {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val dir = java.io.File(context.filesDir, "users/$currentUid")
        if (!dir.exists()) dir.mkdirs()
        return java.io.File(dir, "global_library.json")
    }

    fun getLibraryAssetsDir(context: Context): java.io.File {
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        val dir = java.io.File(context.filesDir, "users/$currentUid/library_assets")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun migrateOldDataIfNecessary(context: Context, newProjectsDir: java.io.File) {
        val oldProjectsDir = java.io.File(context.filesDir, "projects")
        if (oldProjectsDir.exists() && oldProjectsDir.isDirectory) {
            val oldFiles = oldProjectsDir.listFiles()
            if (oldFiles != null && oldFiles.isNotEmpty() && newProjectsDir.listFiles()?.isEmpty() == true) {
                oldFiles.forEach { file ->
                    val dest = java.io.File(newProjectsDir, file.name)
                    file.renameTo(dest)
                }
            }
        }
        
        val oldLibFile = java.io.File(context.filesDir, "global_library.json")
        val newLibFile = getLibraryFile(context)
        if (oldLibFile.exists() && !newLibFile.exists()) {
            oldLibFile.renameTo(newLibFile)
        }
        
        val oldAssetsDir = java.io.File(context.filesDir, "library_assets")
        val newAssetsDir = getLibraryAssetsDir(context)
        if (oldAssetsDir.exists() && oldAssetsDir.isDirectory) {
            val oldAssets = oldAssetsDir.listFiles()
            if (oldAssets != null && oldAssets.isNotEmpty() && newAssetsDir.listFiles()?.isEmpty() == true) {
                oldAssets.forEach { file ->
                    val dest = java.io.File(newAssetsDir, file.name)
                    file.renameTo(dest)
                }
            }
        }
    }

    fun initLocalProjects(context: Context) {
        val rootDir = getProjectsRootDir(context)
        migrateOldDataIfNecessary(context, rootDir)
        currentDirectory = rootDir
        refreshLocalItems()
    }

    fun navigateToFolder(folder: java.io.File) {
        currentDirectory = folder
        refreshLocalItems()
    }

    fun navigateUp(context: Context): Boolean {
        val current = currentDirectory ?: return false
        val rootDir = getProjectsRootDir(context)
        if (current.absolutePath == rootDir.absolutePath) {
            return false
        }
        val parent = current.parentFile
        if (parent != null && parent.absolutePath.startsWith(rootDir.absolutePath)) {
            currentDirectory = parent
            refreshLocalItems()
            return true
        }
        return false
    }

    fun getProjectDisplayName(fileNameWithoutExtension: String): String {
        if (fileNameWithoutExtension.length > 37 && fileNameWithoutExtension[fileNameWithoutExtension.length - 37] == '_') {
            val possibleUuid = fileNameWithoutExtension.substring(fileNameWithoutExtension.length - 36)
            if (possibleUuid.contains('-')) {
                return fileNameWithoutExtension.substring(0, fileNameWithoutExtension.length - 37)
            }
        }
        return fileNameWithoutExtension
    }

    fun refreshLocalItems() {
        val dir = currentDirectory ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val files = dir.listFiles() ?: emptyArray()
            val items = files.mapNotNull { file ->
                if (file.isDirectory) {
                    val count = file.listFiles { f -> f.extension == "skc" }?.size ?: 0
                    val metadataFile = java.io.File(file, ".metadata.json")
                    val metadata = if (metadataFile.exists()) {
                        try {
                            Gson().fromJson(metadataFile.readText(), FolderMetadata::class.java) ?: FolderMetadata()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            FolderMetadata()
                        }
                    } else {
                        FolderMetadata()
                    }
                    DashboardItem.Folder(
                        name = file.name,
                        path = file.absolutePath,
                        lastModified = file.lastModified(),
                        itemCount = count,
                        metadata = metadata
                    )
                } else if (file.isFile && file.extension == "skc") {
                    var finalFile = file
                    val nameWithoutExt = file.nameWithoutExtension
                    val hasUuid = nameWithoutExt.length > 37 && nameWithoutExt[nameWithoutExt.length - 37] == '_' && nameWithoutExt.substring(nameWithoutExt.length - 36).contains('-')
                    if (!hasUuid) {
                        val pid = getProjectId(file) ?: UUID.randomUUID().toString()
                        val newFile = java.io.File(file.parentFile, "${nameWithoutExt}_$pid.skc")
                        if (file.renameTo(newFile)) {
                            finalFile = newFile
                        }
                    }
                    val scaleRatio = com.sketcher.sketchercompanionv1.utils.ZipStorageManager.loadGlobalScaleRatio(finalFile)
                    DashboardItem.Project(
                        name = getProjectDisplayName(finalFile.nameWithoutExtension),
                        path = finalFile.absolutePath,
                        lastModified = finalFile.lastModified(),
                        sizeBytes = finalFile.length(),
                        globalScaleRatio = scaleRatio
                    )
                } else {
                    null
                }
            }.sortedWith(compareByDescending<DashboardItem> { it is DashboardItem.Folder }.thenByDescending { it.lastModified })

            withContext(Dispatchers.Main) {
                localItems.clear()
                localItems.addAll(items)
            }

            // Load thumbnails asynchronously
            items.filterIsInstance<DashboardItem.Project>().forEach { project ->
                if (!thumbnailCache.containsKey(project.path)) {
                    val bmp = com.sketcher.sketchercompanionv1.utils.ZipStorageManager.loadThumbnail(java.io.File(project.path))
                    withContext(Dispatchers.Main) {
                        thumbnailCache[project.path] = bmp
                    }
                }
            }
        }
    }

    fun updateFolderMetadata(context: Context, folderPath: String, coverStyle: String, coverFill: com.sketcher.sketchercompanionv1.dto.FillStyleJson?, coverProject: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val folder = java.io.File(folderPath)
            if (folder.exists() && folder.isDirectory) {
                val metadataFile = java.io.File(folder, ".metadata.json")
                val metadata = FolderMetadata(coverStyle = coverStyle, coverFill = coverFill, coverProject = coverProject)
                try {
                    metadataFile.writeText(Gson().toJson(metadata))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                refreshLocalItems()
                autoSyncCloud(context)
            }
        }
    }

    fun createLocalProject(
        context: Context,
        name: String,
        templateFile: java.io.File? = null,
        scaleRatio: Float = 1.0f,
        canvasSizeConfig: com.sketcher.sketchercompanionv1.dto.CanvasSizeConfig? = null,
        backgroundStyle: com.sketcher.sketchercompanionv1.dto.FillStyle? = null,
        uiPresetName: String? = null
    ) {
        val dir = currentDirectory ?: return
        val pid = UUID.randomUUID().toString()
        projectId = pid
        val file = java.io.File(dir, "${name}_$pid.skc")
        if (file.exists()) {
            android.widget.Toast.makeText(context, "El archivo ya existe", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        
        if (templateFile != null) {
            val (projectData, bitmaps, svgs) = com.sketcher.sketchercompanionv1.utils.TemplateManager.loadTemplate(context, templateFile)
            
            val newScaleConfig = projectData.canvasMetadata.scaleConfig?.copy(globalScaleRatio = scaleRatio) 
                ?: com.sketcher.sketchercompanionv1.dto.ScaleConfig(globalScaleRatio = scaleRatio)
            val newMetadata = projectData.canvasMetadata.copy(scaleConfig = newScaleConfig)
            val newProjectData = projectData.copy(id = pid, canvasMetadata = newMetadata)
            
            restoreProjectState(context, newProjectData, bitmaps, svgs)
        } else {
            clear()
            projectId = pid
            updateGlobalScaleRatio(scaleRatio)
            this.canvasSizeConfig = canvasSizeConfig
            if (backgroundStyle != null) {
                this.backgroundStyle = backgroundStyle
                if (backgroundStyle is com.sketcher.sketchercompanionv1.dto.FillStyle.Solid) {
                    this.backgroundColor = backgroundStyle.color
                } else {
                    this.backgroundColor = android.graphics.Color.WHITE
                }
            } else {
                this.backgroundStyle = com.sketcher.sketchercompanionv1.dto.FillStyle.Solid(android.graphics.Color.WHITE)
                this.backgroundColor = android.graphics.Color.WHITE
            }

            if (uiPresetName != null) {
                toolbarManager.onProjectUiPresetLoaded(uiPresetName)
            } else {
                toolbarManager.onProjectUiPresetCleared()
            }
        }
        
        currentFileUri = android.net.Uri.fromFile(file)
        saveProjectToZip(context, currentFileUri!!)
        showDashboard = false
    }

    fun saveAsLocalProject(context: Context, name: String) {
        val rootDir = getProjectsRootDir(context)
        val dir = currentDirectory ?: rootDir
        val newPid = UUID.randomUUID().toString()
        projectId = newPid
        val file = java.io.File(dir, "${name}_$newPid.skc")
        if (file.exists()) {
            android.widget.Toast.makeText(context, "El archivo ya existe", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        currentFileUri = android.net.Uri.fromFile(file)
        saveProjectToZip(context, currentFileUri!!)
    }

    fun createLocalFolder(
        context: Context,
        name: String,
        coverStyle: String = "classic",
        coverFill: com.sketcher.sketchercompanionv1.dto.FillStyleJson? = null,
        coverProject: String? = null
    ) {
        val dir = currentDirectory ?: return
        val folder = java.io.File(dir, name)
        if (folder.exists()) {
            android.widget.Toast.makeText(context, "La carpeta ya existe", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        folder.mkdirs()
        
        // Write metadata
        val metadataFile = java.io.File(folder, ".metadata.json")
        val metadata = FolderMetadata(coverStyle = coverStyle, coverFill = coverFill, coverProject = coverProject)
        try {
            metadataFile.writeText(Gson().toJson(metadata))
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        refreshLocalItems()
        autoSyncCloud(context)
    }

    fun renameLocalItem(context: Context, item: DashboardItem, newName: String) {
        val oldFile = java.io.File(item.path)
        val parent = oldFile.parentFile ?: return
        val newFile = if (item is DashboardItem.Project) {
            val pid = getProjectId(oldFile) ?: UUID.randomUUID().toString()
            java.io.File(parent, "${newName}_$pid.skc")
        } else {
            java.io.File(parent, newName)
        }
        if (newFile.exists()) {
            android.widget.Toast.makeText(context, "Ya existe un elemento con ese nombre", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val rootDir = getProjectsRootDir(context)
        val oldRelPath = if (item is DashboardItem.Folder) {
            try { oldFile.toRelativeString(rootDir) } catch(e: Exception) { oldFile.name }
        } else null

        if (oldFile.renameTo(newFile)) {
            // Update active file URI if renamed the open project
            val activeUri = currentFileUri
            if (activeUri != null && activeUri.scheme == "file" && activeUri.path == oldFile.absolutePath) {
                currentFileUri = android.net.Uri.fromFile(newFile)
            }
            // Update thumbnailCache
            if (item is DashboardItem.Project) {
                val oldBmp = thumbnailCache.remove(item.path)
                if (oldBmp != null) {
                    thumbnailCache[newFile.absolutePath] = oldBmp
                }
            } else if (item is DashboardItem.Folder && oldRelPath != null) {
                val prefs = context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE)
                val pendingFolders = prefs.getStringSet("folders", emptySet())?.toMutableSet() ?: mutableSetOf()
                pendingFolders.add(oldRelPath)
                prefs.edit().putStringSet("folders", pendingFolders).apply()
            }
            refreshLocalItems()
            autoSyncCloud(context)
        } else {
            android.widget.Toast.makeText(context, "Error al renombrar", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteLocalItem(context: Context, item: DashboardItem) {
        val file = java.io.File(item.path)
        
        // If deleted file is currently open in editor, clear workspace
        val activeUri = currentFileUri
        if (activeUri != null && activeUri.scheme == "file" && activeUri.path == file.absolutePath) {
            clear()
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            val idsToDelete = mutableListOf<String>()
            val foldersToDelete = mutableListOf<String>()
            if (file.isDirectory) {
                val rootDir = getProjectsRootDir(context)
                val relPath = try { file.toRelativeString(rootDir) } catch(e: Exception) { file.name }
                foldersToDelete.add(relPath)
                file.walkTopDown().forEach { f ->
                    if (f.extension == "skc") {
                        getProjectId(f)?.let { idsToDelete.add(it) }
                    }
                }
            } else if (file.extension == "skc") {
                getProjectId(file)?.let { idsToDelete.add(it) }
            }

            file.deleteRecursively()
            
            withContext(Dispatchers.Main) {
                thumbnailCache.remove(item.path)
                refreshLocalItems()
            }

            val prefs = context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE)
            val pendingProjects = prefs.getStringSet("projects", emptySet())?.toMutableSet() ?: mutableSetOf()
            val pendingFolders = prefs.getStringSet("folders", emptySet())?.toMutableSet() ?: mutableSetOf()
            
            pendingProjects.addAll(idsToDelete)
            pendingFolders.addAll(foldersToDelete)
            
            prefs.edit()
                .putStringSet("projects", pendingProjects)
                .putStringSet("folders", pendingFolders)
                .apply()

            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
                val syncedProjects = mutableListOf<String>()
                idsToDelete.forEach { id ->
                    val res = cloudSyncRepository.deleteProject(id)
                    if (res.isSuccess) {
                        syncedProjects.add(id)
                    }
                }
                val syncedFolders = mutableListOf<String>()
                foldersToDelete.forEach { folderRelPath ->
                    val res = cloudSyncRepository.deleteFolder(folderRelPath)
                    if (res.isSuccess) {
                        syncedFolders.add(folderRelPath)
                    }
                }
                
                if (syncedProjects.isNotEmpty() || syncedFolders.isNotEmpty()) {
                    val updatedProjects = prefs.getStringSet("projects", emptySet())?.toMutableSet() ?: mutableSetOf()
                    val updatedFolders = prefs.getStringSet("folders", emptySet())?.toMutableSet() ?: mutableSetOf()
                    updatedProjects.removeAll(syncedProjects)
                    updatedFolders.removeAll(syncedFolders)
                    prefs.edit()
                        .putStringSet("projects", updatedProjects)
                        .putStringSet("folders", updatedFolders)
                        .apply()
                }
            }
            withContext(Dispatchers.Main) {
                autoSyncCloud(context)
            }
        }
    }

    fun moveLocalItem(context: Context, item: DashboardItem, targetFolder: java.io.File) {
        val oldFile = java.io.File(item.path)
        val newFile = java.io.File(targetFolder, oldFile.name)
        if (newFile.exists()) {
            android.widget.Toast.makeText(context, "Ya existe un elemento con el mismo nombre en la carpeta destino", android.widget.Toast.LENGTH_SHORT).show()
            return
        }

        val rootDir = getProjectsRootDir(context)
        val oldRelPath = if (item is DashboardItem.Folder) {
            try { oldFile.toRelativeString(rootDir) } catch(e: Exception) { oldFile.name }
        } else null

        if (oldFile.renameTo(newFile)) {
            val activeUri = currentFileUri
            if (activeUri != null && activeUri.scheme == "file" && activeUri.path == oldFile.absolutePath) {
                currentFileUri = android.net.Uri.fromFile(newFile)
            }
            if (item is DashboardItem.Project) {
                val oldBmp = thumbnailCache.remove(item.path)
                if (oldBmp != null) {
                    thumbnailCache[newFile.absolutePath] = oldBmp
                }
            } else if (item is DashboardItem.Folder && oldRelPath != null) {
                val prefs = context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE)
                val pendingFolders = prefs.getStringSet("folders", emptySet())?.toMutableSet() ?: mutableSetOf()
                pendingFolders.add(oldRelPath)
                prefs.edit().putStringSet("folders", pendingFolders).apply()
            }
            refreshLocalItems()
            autoSyncCloud(context)
        } else {
            android.widget.Toast.makeText(context, "Error al mover el elemento", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    fun loadLocalProject(context: Context, project: DashboardItem.Project) {
        val file = java.io.File(project.path)
        loadProjectFromZip(context, android.net.Uri.fromFile(file))
        showDashboard = false
    }

    fun importExternalProject(context: Context, uri: android.net.Uri) {
        val dir = currentDirectory ?: return
        val fileName = com.sketcher.sketchercompanionv1.utils.BitmapUtils.getFileNameFromUri(context, uri) ?: ""
        if (!fileName.endsWith(".skc", ignoreCase = true)) {
            android.widget.Toast.makeText(context, "Error: Solo se pueden importar archivos .skc", android.widget.Toast.LENGTH_LONG).show()
            return
        }
        var baseName = fileName.substringBeforeLast(".")
        val ext = "skc"
        var destFile = java.io.File(dir, "$baseName.$ext")
        var counter = 1
        while (destFile.exists()) {
            destFile = java.io.File(dir, "${baseName}_$counter.$ext")
            counter++
        }
        
        launchIO {
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    java.io.FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
                withContext(Dispatchers.Main) {
                    refreshLocalItems()
                    android.widget.Toast.makeText(context, "Proyecto importado", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: java.lang.Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Error al importar: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    fun saveCurrentProjectLocal(context: Context) {
        val uri = currentFileUri
        if (uri != null && hasUnsavedChangesSinceLastAutosave) {
            saveProjectToZip(context, uri)
        } else if (uri == null && hasUnsavedChangesSinceLastAutosave) {
            val rootDir = getProjectsRootDir(context)
            val dir = currentDirectory ?: rootDir
            val formatter = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            val defaultName = "Dibujo_" + formatter.format(java.util.Date())
            val file = java.io.File(dir, "${defaultName}_$projectId.skc")
            currentFileUri = android.net.Uri.fromFile(file)
            saveProjectToZip(context, currentFileUri!!)
        }
    }

    fun exitEditorToDashboard(context: Context) {
        hasUnsavedChangesSinceLastAutosave = true
        saveCurrentProjectLocal(context)
        clearProjectState()
        showDashboard = true
        refreshLocalItems()
        autoSyncCloud(context)
    }

    fun exitEditorWithoutSaving(context: Context) {
        hasUnsavedChangesSinceLastAutosave = false
        clearProjectState()
        showDashboard = true
        refreshLocalItems()
    }

    private fun clearProjectState() {
        pages.clear()
        undoStack.clear()
        redoStack.clear()
        componentLibrary.clear()
        projectId = ""
        currentFileUri = null
        hasUnsavedChanges = false
        hasUnsavedChangesSinceLastAutosave = false
        updateUndoRedoSupport()
    }

    // --- CLOUD SYNC LOGIC ---
    fun clearCloudSyncMessage() {
        cloudSyncMessage = null
    }

    fun triggerCloudBackup(context: Context) {
        viewModelScope.launch {
            isSyncingCloud = true
            cloudSyncMessage = "Iniciando respaldo en la nube..."
            try {
                // 1. Recopilar preferencias locales
                val prefsActive = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
                val themeActive = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
                val toolbarActive = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)

                val combinedPrefs = mutableMapOf<String, Any>()
                combinedPrefs["sketcher_prefs"] = prefsActive.all
                combinedPrefs["app_theme"] = themeActive.all
                combinedPrefs["toolbar_prefs"] = toolbarActive.all

                // Subir preferencias
                val prefsResult = cloudSyncRepository.backupPreferences(combinedPrefs)
                if (prefsResult.isFailure) {
                    cloudSyncMessage = "Error respaldando preferencias"
                    isSyncingCloud = false
                    return@launch
                }

                // 2. Recopilar Librería
                cloudSyncMessage = "Respaldando librería..."
                LibraryManager.saveLibrary(context, _globalLibraryItems.value)
                val localLibraryFile = getLibraryFile(context)
                val libraryJson = localLibraryFile.readText()
                val timestamp = localLibraryFile.lastModified()
                val assetsDir = getLibraryAssetsDir(context)
                val libResult = cloudSyncRepository.backupLibrary(libraryJson, timestamp, assetsDir)
                if (libResult.isFailure) {
                    cloudSyncMessage = "Error respaldando librería"
                    isSyncingCloud = false
                    return@launch
                }

                cloudSyncMessage = "¡Respaldo exitoso!"
            } catch (e: Exception) {
                cloudSyncMessage = "Error: ${e.message}"
            } finally {
                isSyncingCloud = false
            }
        }
    }

    fun triggerCloudRestore(context: Context) {
        viewModelScope.launch {
            isSyncingCloud = true
            cloudSyncMessage = "Descargando copia de seguridad..."
            try {
                // 1. Restaurar Preferencias
                val prefsResult = cloudSyncRepository.restorePreferences()
                if (prefsResult.isSuccess) {
                    val data = prefsResult.getOrNull() ?: emptyMap()
                    
                    val sketcherPrefsMap = data["sketcher_prefs"] as? Map<String, Any>
                    val themePrefsMap = data["app_theme"] as? Map<String, Any>
                    val toolbarPrefsMap = data["toolbar_prefs"] as? Map<String, Any>

                    val sketcherPrefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
                    val themePrefs = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
                    val toolbarPrefs = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)

                    fun applyMapToPrefs(map: Map<String, Any>?, editor: android.content.SharedPreferences.Editor) {
                        map?.forEach { (k, v) ->
                            when (v) {
                                is Boolean -> editor.putBoolean(k, v)
                                is Float -> editor.putFloat(k, v)
                                is Int -> editor.putInt(k, v)
                                is Long -> editor.putLong(k, v)
                                is String -> editor.putString(k, v)
                                // Firebase returns numbers as Long/Double sometimes, so cast carefully
                                is Double -> editor.putFloat(k, v.toFloat())
                            }
                        }
                    }

                    if (sketcherPrefsMap != null) {
                        val editor = sketcherPrefs.edit()
                        editor.clear()
                        applyMapToPrefs(sketcherPrefsMap, editor)
                        editor.apply()
                    }

                    if (themePrefsMap != null) {
                        val editor = themePrefs.edit()
                        editor.clear()
                        applyMapToPrefs(themePrefsMap, editor)
                        editor.apply()
                    }

                    if (toolbarPrefsMap != null) {
                        val editor = toolbarPrefs.edit()
                        editor.clear()
                        applyMapToPrefs(toolbarPrefsMap, editor)
                        editor.apply()
                    }
                    
                    // Restore custom brushes
                    val brushesResult = cloudSyncRepository.getAllCustomBrushes()
                    if (brushesResult.isSuccess) {
                        val brushesList = brushesResult.getOrNull() ?: emptyList()
                        if (brushesList.isNotEmpty()) {
                            val gson = com.google.gson.Gson()
                            val jsonStringList = brushesList.map { gson.toJson(it) }
                            
                            val tokenType = object : com.google.gson.reflect.TypeToken<List<com.sketcher.sketchercompanionv1.dto.CustomToolJson>>() {}.type
                            val loaded: List<com.sketcher.sketchercompanionv1.dto.CustomToolJson> = gson.fromJson(gson.toJson(brushesList), tokenType)
                            
                            val mapped = loaded.mapNotNull { j ->
                                try {
                                    val settings = when (j.preset.settingsType) {
                                        "PencilSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java)
                                        "PenSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PenSettings::class.java)
                                        "PlumaSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PlumaSettings::class.java)
                                        "PaintSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PaintSettings::class.java)
                                        "WatercolorSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.WatercolorSettings::class.java)
                                        else -> com.sketcher.sketchercompanionv1.tools.PencilSettings()
                                    }
                                    com.sketcher.sketchercompanionv1.dto.CustomTool(
                                        id = j.id,
                                        name = j.name,
                                        iconName = j.iconName,
                                        iconResName = j.iconResName,
                                        baseToolType = try { com.sketcher.sketchercompanionv1.dto.ToolType.valueOf(j.baseToolType) } catch (e: Exception) { com.sketcher.sketchercompanionv1.dto.ToolType.FREEHAND },
                                        preset = com.sketcher.sketchercompanionv1.dto.BrushPreset(
                                            size = j.preset.size,
                                            opacity = j.preset.opacity,
                                            settings = settings,
                                            strokeColor = j.preset.strokeColor,
                                            fillColor = j.preset.fillColor,
                                            isStrokeActive = j.preset.isStrokeActive,
                                            isFillActive = j.preset.isFillActive,
                                            fillStyle = j.preset.fillStyle?.toFillStyle(j.preset.fillColor ?: 0),
                                            strokeStyle = j.preset.strokeStyle?.toFillStyle(j.preset.strokeColor ?: 0),
                                            stabilization = j.preset.stabilization
                                        )
                                    )
                                } catch (e: Exception) { null }
                            }
                            
                            if (mapped.isNotEmpty()) {
                                toolManager.onCustomToolAddedOrUpdated = null
                                toolManager.onCustomToolRemoved = null
                                toolManager.saveCustomTools(mapped)
                                
                                val currentTheme = _themeConfig.value
                                val newCustomIcons = currentTheme.customIcons.toMutableMap()
                                var iconsChanged = false
                                mapped.forEach { ct ->
                                    if (ct.customIconJson != null) {
                                        newCustomIcons[ct.id] = ct.customIconJson
                                        iconsChanged = true
                                    }
                                }
                                if (iconsChanged) {
                                    updateTheme(currentTheme.copy(customIcons = newCustomIcons))
                                }

                                
                                // Re-bind callbacks
                                toolManager.onCustomToolAddedOrUpdated = { ct ->
                                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val gsonInner = com.google.gson.Gson()
                                            val jsonObj = com.sketcher.sketchercompanionv1.dto.CustomToolJson(
                                                id = ct.id,
                                                name = ct.name,
                                                iconName = ct.iconName,
                                                iconResName = ct.iconResName,
                                                baseToolType = ct.baseToolType.name,
                                                preset = com.sketcher.sketchercompanionv1.dto.BrushPresetJson(
                                                    size = ct.preset.size,
                                                    opacity = ct.preset.opacity,
                                                    settingsType = ct.preset.settings::class.java.simpleName,
                                                    settingsJson = gsonInner.toJson(ct.preset.settings),
                                                    strokeColor = ct.preset.strokeColor,
                                                    fillColor = ct.preset.fillColor,
                                                    isStrokeActive = ct.preset.isStrokeActive,
                                                    isFillActive = ct.preset.isFillActive,
                                                    fillStyle = ct.preset.fillStyle?.toFillStyleJson(),
                                                    strokeStyle = ct.preset.strokeStyle?.toFillStyleJson(),
                                                    stabilization = ct.preset.stabilization
                                                )
                                            )
                                            val jsonStr = gsonInner.toJson(jsonObj)
                                            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                            val map: Map<String, Any> = gsonInner.fromJson(jsonStr, type)
                                            cloudSyncRepository.syncCustomBrush(ct.id, map)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                                toolManager.onCustomToolRemoved = { id ->
                                    toolbarManager.removeToolFromAllLayouts(id)
                                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        cloudSyncRepository.syncCustomBrush(id, null)
                                    }
                                }
                            }
                        }
                    }
                    
                    // Reload local state
                    _themeConfig.value = themeRepository.getTheme()
                    // loadConfig()
                }

                // 2. Restaurar Librería
                cloudSyncMessage = "Descargando librería..."
                val assetsDir = getLibraryAssetsDir(context)
                val libResult = cloudSyncRepository.restoreLibrary(assetsDir)
                if (libResult.isSuccess) {
                    val pair = libResult.getOrNull()
                    if (pair != null) {
                        val (jsonStr, timestamp) = pair
                        // Guardar el JSON descargado en local
                        val file = getLibraryFile(context)
                        file.writeText(jsonStr)
                        file.setLastModified(timestamp)
                        // Recargar
                        loadGlobalLibrary(context)
                    }
                }

                cloudSyncMessage = "¡Restauración exitosa!"
            } catch (e: Exception) {
                cloudSyncMessage = "Error: ${e.message}"
            } finally {
                isSyncingCloud = false
            }
        }
    }

    fun autoSyncCloud(context: Context) {
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser == null) return
        if (isSyncingCloud) return
        val currentUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: "guest"
        viewModelScope.launch {
            val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
            val lastUid = prefs.getString("last_sync_uid", null)
            if (lastUid != null && lastUid != currentUid) {
                currentDirectory = getProjectsRootDir(context)
                clear()
                thumbnailCache.clear()
                _globalLibraryItems.value = emptyList()
            }
            prefs.edit().putString("last_sync_uid", currentUid).apply()

            isSyncingCloud = true
            cloudSyncMessage = "Sincronizando..."
            try {
                withContext(Dispatchers.IO) {
                // Restore Preferences silently
                val prefsResult = cloudSyncRepository.restorePreferences()
                if (prefsResult.isSuccess) {
                    val data = prefsResult.getOrNull() ?: emptyMap()
                    val sketcherPrefsMap = data["sketcher_prefs"] as? Map<String, Any>
                    val themePrefsMap = data["app_theme"] as? Map<String, Any>
                    val toolbarPrefsMap = data["toolbar_prefs"] as? Map<String, Any>

                    val sketcherPrefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
                    val themePrefs = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
                    val toolbarPrefs = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)

                    fun applyMapToPrefs(map: Map<String, Any>?, editor: android.content.SharedPreferences.Editor) {
                        map?.forEach { (k, v) ->
                            when (v) {
                                is Boolean -> editor.putBoolean(k, v)
                                is Float -> editor.putFloat(k, v)
                                is Int -> editor.putInt(k, v)
                                is Long -> editor.putLong(k, v)
                                is String -> editor.putString(k, v)
                                is Double -> editor.putFloat(k, v.toFloat())
                            }
                        }
                    }

                    if (sketcherPrefsMap != null) { val editor = sketcherPrefs.edit(); editor.clear(); applyMapToPrefs(sketcherPrefsMap, editor); editor.apply() }
                    if (themePrefsMap != null) { val editor = themePrefs.edit(); editor.clear(); applyMapToPrefs(themePrefsMap, editor); editor.apply() }
                    if (toolbarPrefsMap != null) { val editor = toolbarPrefs.edit(); editor.clear(); applyMapToPrefs(toolbarPrefsMap, editor); editor.apply() }
                    _themeConfig.value = themeRepository.getTheme()
                    
                    val brushesResult = cloudSyncRepository.getAllCustomBrushes()
                    if (brushesResult.isSuccess) {
                        val brushesList = brushesResult.getOrNull() ?: emptyList()
                        if (brushesList.isNotEmpty()) {
                            val gson = com.google.gson.Gson()
                            val tokenType = object : com.google.gson.reflect.TypeToken<List<com.sketcher.sketchercompanionv1.dto.CustomToolJson>>() {}.type
                            val loaded: List<com.sketcher.sketchercompanionv1.dto.CustomToolJson> = gson.fromJson(gson.toJson(brushesList), tokenType)
                            
                            val mapped = loaded.mapNotNull { j ->
                                try {
                                    val settings = when (j.preset.settingsType) {
                                        "PencilSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java)
                                        "PenSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PenSettings::class.java)
                                        "PlumaSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PlumaSettings::class.java)
                                        "PaintSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PaintSettings::class.java)
                                        "WatercolorSettings" -> gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.WatercolorSettings::class.java)
                                        else -> com.sketcher.sketchercompanionv1.tools.PencilSettings()
                                    }
                                    com.sketcher.sketchercompanionv1.dto.CustomTool(
                                        id = j.id,
                                        name = j.name,
                                        iconName = j.iconName,
                                        iconResName = j.iconResName,
                                        baseToolType = try { com.sketcher.sketchercompanionv1.dto.ToolType.valueOf(j.baseToolType) } catch (e: Exception) { com.sketcher.sketchercompanionv1.dto.ToolType.FREEHAND },
                                        preset = com.sketcher.sketchercompanionv1.dto.BrushPreset(
                                            size = j.preset.size,
                                            opacity = j.preset.opacity,
                                            settings = settings,
                                            strokeColor = j.preset.strokeColor,
                                            fillColor = j.preset.fillColor,
                                            isStrokeActive = j.preset.isStrokeActive,
                                            isFillActive = j.preset.isFillActive,
                                            fillStyle = j.preset.fillStyle?.toFillStyle(j.preset.fillColor ?: 0),
                                            strokeStyle = j.preset.strokeStyle?.toFillStyle(j.preset.strokeColor ?: 0),
                                            stabilization = j.preset.stabilization
                                        )
                                    )
                                } catch (e: Exception) { null }
                            }
                            
                            if (mapped.isNotEmpty()) {
                                toolManager.onCustomToolAddedOrUpdated = null
                                toolManager.onCustomToolRemoved = null
                                toolManager.saveCustomTools(mapped)
                                
                                val currentTheme = _themeConfig.value
                                val newCustomIcons = currentTheme.customIcons.toMutableMap()
                                var iconsChanged = false
                                mapped.forEach { ct ->
                                    if (ct.customIconJson != null) {
                                        newCustomIcons[ct.id] = ct.customIconJson
                                        iconsChanged = true
                                    }
                                }
                                if (iconsChanged) {
                                    updateTheme(currentTheme.copy(customIcons = newCustomIcons))
                                }

                                
                                toolManager.onCustomToolAddedOrUpdated = { ct ->
                                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        try {
                                            val gsonInner = com.google.gson.Gson()
                                            val jsonObj = com.sketcher.sketchercompanionv1.dto.CustomToolJson(
                                                id = ct.id,
                                                name = ct.name,
                                                iconName = ct.iconName,
                                                iconResName = ct.iconResName,
                                                baseToolType = ct.baseToolType.name,
                                                preset = com.sketcher.sketchercompanionv1.dto.BrushPresetJson(
                                                    size = ct.preset.size,
                                                    opacity = ct.preset.opacity,
                                                    settingsType = ct.preset.settings::class.java.simpleName,
                                                    settingsJson = gsonInner.toJson(ct.preset.settings),
                                                    strokeColor = ct.preset.strokeColor,
                                                    fillColor = ct.preset.fillColor,
                                                    isStrokeActive = ct.preset.isStrokeActive,
                                                    isFillActive = ct.preset.isFillActive,
                                                    fillStyle = ct.preset.fillStyle?.toFillStyleJson(),
                                                    strokeStyle = ct.preset.strokeStyle?.toFillStyleJson(),
                                                    stabilization = ct.preset.stabilization
                                                )
                                            )
                                            val jsonStr = gsonInner.toJson(jsonObj)
                                            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                                            val map: Map<String, Any> = gsonInner.fromJson(jsonStr, type)
                                            cloudSyncRepository.syncCustomBrush(ct.id, map)
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                }
                                toolManager.onCustomToolRemoved = { id ->
                                    toolbarManager.removeToolFromAllLayouts(id)
                                    viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        cloudSyncRepository.syncCustomBrush(id, null)
                                    }
                                }
                            }
                        }
                    }
                }

                // Sync Library
                val localLibraryFile = getLibraryFile(context)
                val localLibTimestamp = if (localLibraryFile.exists()) localLibraryFile.lastModified() else 0L
                val cloudLibTimestampResult = cloudSyncRepository.getLibraryTimestamp()
                val cloudLibTimestamp = if (cloudLibTimestampResult.isSuccess) cloudLibTimestampResult.getOrNull() ?: 0L else 0L
                
                val assetsDir = getLibraryAssetsDir(context)
                
                if (cloudLibTimestamp > localLibTimestamp + 5000) {
                    val libResult = cloudSyncRepository.restoreLibrary(assetsDir)
                    if (libResult.isSuccess) {
                        val pair = libResult.getOrNull()
                        if (pair != null) {
                            val (jsonStr, timestamp) = pair
                            localLibraryFile.writeText(jsonStr)
                            localLibraryFile.setLastModified(timestamp)
                            loadGlobalLibrary(context)
                            setLibraryLastUploadedTime(context, timestamp)
                        }
                    }
                } else if (localLibTimestamp > cloudLibTimestamp + 5000) {
                    if (localLibraryFile.exists()) {
                        val backupRes = cloudSyncRepository.backupLibrary(localLibraryFile.readText(), localLibTimestamp, assetsDir)
                        if (backupRes.isSuccess) {
                            setLibraryLastUploadedTime(context, localLibTimestamp)
                        }
                    }
                } else {
                    setLibraryLastUploadedTime(context, localLibTimestamp)
                }

                // Sync Pending Deletions
                val pendingPrefs = context.getSharedPreferences("sync_pending_deletions", Context.MODE_PRIVATE)
                val pendingProjects = pendingPrefs.getStringSet("projects", emptySet())?.toMutableSet() ?: mutableSetOf()
                val pendingFolders = pendingPrefs.getStringSet("folders", emptySet())?.toMutableSet() ?: mutableSetOf()
                
                val syncedProjects = mutableListOf<String>()
                pendingProjects.forEach { id ->
                    val res = cloudSyncRepository.deleteProject(id)
                    if (res.isSuccess) {
                        syncedProjects.add(id)
                    }
                }
                val syncedFolders = mutableListOf<String>()
                pendingFolders.forEach { folderRelPath ->
                    val res = cloudSyncRepository.deleteFolder(folderRelPath)
                    if (res.isSuccess) {
                        syncedFolders.add(folderRelPath)
                    }
                }
                
                if (syncedProjects.isNotEmpty() || syncedFolders.isNotEmpty()) {
                    pendingProjects.removeAll(syncedProjects)
                    pendingFolders.removeAll(syncedFolders)
                    pendingPrefs.edit()
                        .putStringSet("projects", pendingProjects)
                        .putStringSet("folders", pendingFolders)
                        .apply()
                }

                // Sync Folders
                cloudSyncMessage = "Sincronizando carpetas..."
                val foldersResult = cloudSyncRepository.getFolders()
                if (foldersResult.isSuccess) {
                    val cloudFolders = foldersResult.getOrDefault(emptyList())
                    val rootDir = getProjectsRootDir(context)

                    val localFoldersMap = mutableMapOf<String, java.io.File>()
                    val localFoldersLastModified = mutableMapOf<String, Long>()

                    fun scanFolders(dir: java.io.File) {
                        dir.listFiles()?.forEach { file ->
                            if (file.isDirectory) {
                                val relPath = try { file.toRelativeString(rootDir) } catch(e: Exception) { file.name }
                                localFoldersMap[relPath] = file
                                val metadataFile = java.io.File(file, ".metadata.json")
                                val ts = if (metadataFile.exists()) metadataFile.lastModified() else file.lastModified()
                                localFoldersLastModified[relPath] = ts
                                scanFolders(file)
                            }
                        }
                    }
                    scanFolders(rootDir)

                    val processedCloudFolders = mutableSetOf<String>()
                    for (f in cloudFolders) {
                        val relPath = f["relativePath"] as? String ?: continue
                        val cloudTimestamp = (f["timestamp"] as? Number)?.toLong() ?: 0L
                        val isDeleted = f["deleted"] as? Boolean == true
                        val coverStyle = f["coverStyle"] as? String ?: "classic"
                        val coverFillMap = f["coverFill"] as? Map<String, Any>
                        val coverProjectRel = f["coverProject"] as? String
                        
                        processedCloudFolders.add(relPath)

                        if (pendingFolders.contains(relPath)) {
                            continue
                        }

                        val localFolder = localFoldersMap[relPath]
                        val destFolder = java.io.File(rootDir, relPath)

                        if (isDeleted) {
                            if (localFolder != null && localFolder.exists()) {
                                localFolder.deleteRecursively()
                            }
                            continue
                        }

                        // Reconstruct absolute path for coverProject
                        val coverProjectAbs = if (!coverProjectRel.isNullOrEmpty()) {
                            java.io.File(rootDir, coverProjectRel).absolutePath
                        } else {
                            null
                        }

                        // Convert coverFillMap to FillStyleJson
                        val coverFillJson = if (coverFillMap != null) {
                            try {
                                val json = Gson().toJson(coverFillMap)
                                Gson().fromJson(json, com.sketcher.sketchercompanionv1.dto.FillStyleJson::class.java)
                            } catch (e: Exception) {
                                null
                            }
                        } else {
                            null
                        }

                        val metadata = FolderMetadata(
                            coverStyle = coverStyle,
                            coverFill = coverFillJson,
                            coverProject = coverProjectAbs
                        )

                        if (localFolder == null) {
                            destFolder.mkdirs()
                            val metadataFile = java.io.File(destFolder, ".metadata.json")
                            try {
                                metadataFile.writeText(Gson().toJson(metadata))
                                metadataFile.setLastModified(cloudTimestamp)
                                setFolderLastUploadedTime(context, relPath, cloudTimestamp)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        } else {
                            val localTimestamp = localFoldersLastModified[relPath] ?: 0L
                            if (cloudTimestamp > localTimestamp + 5000) {
                                val metadataFile = java.io.File(destFolder, ".metadata.json")
                                try {
                                    metadataFile.writeText(Gson().toJson(metadata))
                                    metadataFile.setLastModified(cloudTimestamp)
                                    setFolderLastUploadedTime(context, relPath, cloudTimestamp)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            } else if (localTimestamp > cloudTimestamp + 5000) {
                                uploadFolderSilently(context, rootDir, localFolder)
                            } else {
                                setFolderLastUploadedTime(context, relPath, localTimestamp)
                            }
                        }
                    }

                    for ((relPath, localFolder) in localFoldersMap) {
                        if (!processedCloudFolders.contains(relPath) && !pendingFolders.contains(relPath)) {
                            uploadFolderSilently(context, rootDir, localFolder)
                        }
                    }
                }

                // Sync Projects
                cloudSyncMessage = "Sincronizando proyectos..."
                val projectsResult = cloudSyncRepository.getProjects()
                if (projectsResult.isSuccess) {
                    val cloudProjects = projectsResult.getOrDefault(emptyList())
                    val rootDir = getProjectsRootDir(context)

                    val localProjectsMap = mutableMapOf<String, java.io.File>()
                    val localProjectsLastModified = mutableMapOf<String, Long>()

                    fun scanDir(dir: java.io.File) {
                        dir.listFiles()?.forEach { file ->
                            if (file.isDirectory) scanDir(file)
                            else if (file.extension == "skc") {
                                val pid = getProjectId(file)
                                if (pid != null) {
                                    localProjectsMap[pid] = file
                                    localProjectsLastModified[pid] = file.lastModified()
                                }
                            }
                        }
                    }
                    scanDir(rootDir)

                    val processedCloudIds = mutableSetOf<String>()
                    for (p in cloudProjects) {
                        val pId = p["id"] as? String ?: continue
                        val cloudTimestamp = (p["timestamp"] as? Number)?.toLong() ?: 0L
                        val isDeleted = p["deleted"] as? Boolean == true
                        
                        processedCloudIds.add(pId)

                        if (pendingProjects.contains(pId)) {
                            continue
                        }

                        val localFile = localProjectsMap[pId]
                        val fileUrl = p["fileUrl"] as? String
                        val thumbnailUrl = p["thumbnailUrl"] as? String

                        if (isDeleted) {
                            if (localFile != null && localFile.exists()) {
                                val localTimestamp = localProjectsLastModified[pId] ?: 0L
                                if (localTimestamp > cloudTimestamp + 5000) {
                                    // Local is newer -> restore project in cloud
                                    uploadProjectSilently(context, localFile)
                                } else {
                                    // Delete locally
                                    localFile.delete()
                                    withContext(Dispatchers.Main) {
                                        thumbnailCache.remove(localFile.absolutePath)
                                    }
                                }
                            }
                            continue
                        }

                        val relativePath = p["relativePath"] as? String ?: (p["name"] as? String)?.let { "$it.skc" } ?: continue
                        
                        val baseName = relativePath.substringBeforeLast(".")
                        val ext = relativePath.substringAfterLast(".", "")
                        val hasUuid = baseName.length > 37 && baseName[baseName.length - 37] == '_' && baseName.substring(baseName.length - 36).contains('-')
                        val destRelativePath = if (hasUuid) {
                            relativePath
                        } else {
                            if (relativePath.contains("/")) {
                                val parentPath = relativePath.substringBeforeLast("/")
                                val fileName = relativePath.substringAfterLast("/")
                                val fileBase = fileName.substringBeforeLast(".")
                                val fileExt = fileName.substringAfterLast(".", "")
                                "$parentPath/${fileBase}_$pId.$fileExt"
                            } else {
                                "${baseName}_$pId.$ext"
                            }
                        }
                        val destFile = java.io.File(rootDir, destRelativePath)

                        if (localFile == null) {
                            // Missing locally -> Download
                            destFile.parentFile?.mkdirs()
                            val downloadRes = cloudSyncRepository.downloadProject(pId, destFile, fileUrl)
                            if (downloadRes.isSuccess) {
                                destFile.setLastModified(cloudTimestamp)
                                setProjectLastUploadedTime(context, pId, cloudTimestamp)
                            }
                            if (!thumbnailUrl.isNullOrEmpty()) {
                                val thumbCacheDir = java.io.File(context.cacheDir, "thumbnails")
                                if (!thumbCacheDir.exists()) thumbCacheDir.mkdirs()
                                val thumbFile = java.io.File(thumbCacheDir, "$pId.png")
                                cloudSyncRepository.downloadThumbnail(pId, thumbFile, thumbnailUrl)
                            }
                        } else {
                            // Exists locally
                            val localTimestamp = localProjectsLastModified[pId] ?: 0L
                            val lastSynced = getProjectLastUploadedTime(context, pId)

                            // Conflict detection
                            val isConflict = (localTimestamp > lastSynced + 5000) && (cloudTimestamp > lastSynced + 5000)

                            if (isConflict) {
                                // Conflict -> Upload local as a new version.
                                // Local edits are preserved as latest, remote edits are kept in history.
                                uploadProjectSilently(context, localFile)
                            } else if (cloudTimestamp > localTimestamp + 5000) {
                                // Cloud is newer -> Download safely
                                val finalFile = if (localFile.absolutePath != destFile.absolutePath) {
                                    destFile.parentFile?.mkdirs()
                                    if (localFile.renameTo(destFile)) destFile else localFile
                                } else {
                                    destFile
                                }
                                val tempFile = java.io.File(finalFile.parentFile, "${finalFile.name}.tmp")
                                val downloadRes = cloudSyncRepository.downloadProject(pId, tempFile, fileUrl)
                                if (downloadRes.isSuccess) {
                                    if (finalFile.exists()) finalFile.delete()
                                    tempFile.renameTo(finalFile)
                                    finalFile.setLastModified(cloudTimestamp)
                                    setProjectLastUploadedTime(context, pId, cloudTimestamp)
                                } else {
                                    if (tempFile.exists()) tempFile.delete()
                                }
                                
                                if (!thumbnailUrl.isNullOrEmpty()) {
                                    val thumbCacheDir = java.io.File(context.cacheDir, "thumbnails")
                                    if (!thumbCacheDir.exists()) thumbCacheDir.mkdirs()
                                    val thumbFile = java.io.File(thumbCacheDir, "$pId.png")
                                    cloudSyncRepository.downloadThumbnail(pId, thumbFile, thumbnailUrl)
                                }
                            } else if (localTimestamp > cloudTimestamp + 5000) {
                                // Local is newer
                                uploadProjectSilently(context, localFile)
                            } else if (localFile.absolutePath != destFile.absolutePath) {
                                // Path changed locally but timestamp is close -> upload to sync metadata
                                uploadProjectSilently(context, localFile)
                            } else {
                                // Already in sync
                                setProjectLastUploadedTime(context, pId, localTimestamp)
                            }
                        }
                    }

                    // Process local projects not in cloud
                    for ((pId, localFile) in localProjectsMap) {
                        if (!processedCloudIds.contains(pId) && !pendingProjects.contains(pId)) {
                            uploadProjectSilently(context, localFile)
                        }
                    }
                }
                }

                refreshLocalItems()
                cloudSyncMessage = "Sincronización completada"
                kotlinx.coroutines.delay(2000)
                cloudSyncMessage = null
            } catch (e: Exception) {
                cloudSyncMessage = "Error de sincronización"
                kotlinx.coroutines.delay(2000)
                cloudSyncMessage = null
            } finally {
                isSyncingCloud = false
            }
        }
    }

    private suspend fun downloadThumbnailSilently(context: Context, p: Map<String, Any>, pId: String) {
        val thumbUrl = p["thumbnailUrl"] as? String
        if (!thumbUrl.isNullOrEmpty()) {
            val thumbCacheDir = java.io.File(context.cacheDir, "thumbnails")
            if (!thumbCacheDir.exists()) thumbCacheDir.mkdirs()
            val thumbFile = java.io.File(thumbCacheDir, "$pId.png")
            cloudSyncRepository.downloadThumbnail(pId, thumbFile)
        }
    }

    private suspend fun uploadProjectSilently(context: Context, file: java.io.File) {
        val pId = getProjectId(file) ?: return
        val rootDir = getProjectsRootDir(context)
        val relativePath = try {
            file.toRelativeString(rootDir)
        } catch (e: Exception) {
            file.name
        }
        val name = getProjectDisplayName(file.nameWithoutExtension).ifEmpty { "Project" }
        
        val thumbCacheDir = java.io.File(context.cacheDir, "thumbnails")
        val thumbFile = java.io.File(thumbCacheDir, "$pId.png")
        val thumbUri = if (thumbFile.exists()) android.net.Uri.fromFile(thumbFile) else null

        val fileTimestamp = if (file.exists()) file.lastModified() else System.currentTimeMillis()
        val uploadRes = cloudSyncRepository.uploadProject(
            projectId = pId,
            projectName = name,
            projectFileUri = android.net.Uri.fromFile(file),
            relativePath = relativePath,
            thumbnailUri = thumbUri,
            timestamp = fileTimestamp,
            metadata = mapOf(
                "lastModified" to fileTimestamp,
                "deviceUid" to getDeviceUid(context),
                "deviceName" to android.os.Build.MODEL,
                "fileSize" to (if (file.exists()) file.length() else 0L)
            )
        )
        if (uploadRes.isSuccess) {
            setProjectLastUploadedTime(context, pId, fileTimestamp)
        }
    }

    private suspend fun uploadFolderSilently(context: Context, rootDir: java.io.File, folder: java.io.File) {
        val relPath = try { folder.toRelativeString(rootDir) } catch(e: Exception) { folder.name }
        val metadataFile = java.io.File(folder, ".metadata.json")
        val metadata = if (metadataFile.exists()) {
            try {
                Gson().fromJson(metadataFile.readText(), FolderMetadata::class.java) ?: FolderMetadata()
            } catch (e: Exception) {
                FolderMetadata()
            }
        } else {
            FolderMetadata()
        }

        val relativeCoverProject = if (!metadata.coverProject.isNullOrEmpty()) {
            val file = java.io.File(metadata.coverProject)
            try {
                file.toRelativeString(rootDir)
            } catch (e: Exception) {
                metadata.coverProject
            }
        } else {
            null
        }

        val coverFillMap = if (metadata.coverFill != null) {
            try {
                val json = Gson().toJson(metadata.coverFill)
                val mapType = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                Gson().fromJson<Map<String, Any>>(json, mapType)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

        val ts = if (metadataFile.exists()) metadataFile.lastModified() else folder.lastModified()
        val uploadRes = cloudSyncRepository.uploadFolder(
            relativePath = relPath,
            coverStyle = metadata.coverStyle,
            coverFill = coverFillMap,
            coverProject = relativeCoverProject,
            timestamp = ts
        )
        if (uploadRes.isSuccess) {
            setFolderLastUploadedTime(context, relPath, ts)
        }
    }

    private fun getProjectIdFromZip(file: java.io.File): String? {
        try {
            java.util.zip.ZipFile(file).use { zip ->
                val entry = zip.getEntry("project.json") ?: return null
                val json = zip.getInputStream(entry).bufferedReader().readText()
                val obj = org.json.JSONObject(json)
                return obj.optString("id", null)
            }
        } catch (e: Exception) { return null }
    }

    fun getProjectId(file: java.io.File): String? {
        val nameWithoutExt = file.nameWithoutExtension
        if (nameWithoutExt.length > 37 && nameWithoutExt[nameWithoutExt.length - 37] == '_') {
            val possibleUuid = nameWithoutExt.substring(nameWithoutExt.length - 36)
            if (possibleUuid.contains('-')) {
                return possibleUuid
            }
        }
        val zipId = getProjectIdFromZip(file)
        if (!zipId.isNullOrEmpty()) {
            return zipId
        }
        return null
    }

    fun wipeCloudProjects(context: Context) {
        viewModelScope.launch {
            isSyncingCloud = true
            cloudSyncMessage = "Borrando proyectos en la nube..."
            val result = cloudSyncRepository.wipeAllProjects()
            if (result.isSuccess) {
                cloudSyncMessage = "Nube borrada exitosamente"
            } else {
                cloudSyncMessage = "Error al borrar la nube"
            }
            kotlinx.coroutines.delay(2000)
            cloudSyncMessage = null
            isSyncingCloud = false
        }
    }

    private fun detectStylusSupport(context: Context): Boolean {
        // 1. Check system feature for API 33+ (FEATURE_INPUT_STYLUS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (context.packageManager.hasSystemFeature("android.hardware.input.stylus")) {
                return true
            }
        }

        // 2. Check Samsung S-Pen feature
        if (context.packageManager.hasSystemFeature("com.sec.feature.spen_usp")) {
            return true
        }

        // 3. Scan connected input devices for a stylus source or names indicating stylus/pen
        val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
        if (inputManager != null) {
            val deviceIds = inputManager.inputDeviceIds
            for (id in deviceIds) {
                val device = inputManager.getInputDevice(id) ?: continue
                val sources = device.sources
                if ((sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS) {
                    return true
                }
                val name = device.name.lowercase()
                if (name.contains("stylus") || name.contains("pen") || name.contains("wacom")) {
                    return true
                }
            }
        }
        return false
    }
}

// --- DATA STRUCTURES FOR DASHBOARD ITEMS ---
data class FolderMetadata(
    val coverStyle: String = "classic", // "spiral", "classic", "minimalist"
    val coverFill: com.sketcher.sketchercompanionv1.dto.FillStyleJson? = null,
    val coverProject: String? = null
)

sealed interface DashboardItem {
    val name: String
    val path: String
    val lastModified: Long

    data class Folder(
        override val name: String,
        override val path: String,
        override val lastModified: Long,
        val itemCount: Int,
        val metadata: FolderMetadata
    ) : DashboardItem

    data class Project(
        override val name: String,
        override val path: String,
        override val lastModified: Long,
        val sizeBytes: Long,
        val globalScaleRatio: Float = 1.0f
    ) : DashboardItem
}
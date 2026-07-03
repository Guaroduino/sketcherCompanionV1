package com.sketcher.sketchercompanionv1



import android.app.Application

import android.content.Context

import android.graphics.Color as AndroidColor

import android.graphics.Matrix

import android.graphics.Paint

import android.graphics.PorterDuff

import android.graphics.PorterDuffXfermode

import android.net.wifi.WifiManager

import androidx.compose.runtime.getValue

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
import com.sketcher.sketchercompanionv1.managers.LibraryManager
import com.sketcher.sketchercompanionv1.LibraryItem
import com.sketcher.sketchercompanionv1.LibraryFolder
import com.sketcher.sketchercompanionv1.LibraryComponent


import kotlinx.coroutines.launch

import kotlinx.coroutines.Dispatchers

import kotlinx.coroutines.CoroutineScope

import kotlinx.coroutines.withContext

import com.google.gson.Gson

import com.sketcher.sketchercompanionv1.dto.*

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

    private val themeRepository = ThemeRepository(application)

    private val toolbarRepository = ToolbarRepository(application)

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

    val toolbarManager = com.sketcher.sketchercompanionv1.managers.ToolbarManager(
        toolbarRepository = toolbarRepository,
        prefs = prefs,
        getDefaultStrokeColor = { strokeColor.value },
        getDefaultFillColor = { fillColor.value },
        activateTool = { payload, id -> activateTool(payload, id) },
        getActionForTool = { id -> getActionForTool(id) }
    )



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

    var isPalmRejectionEnabled by mutableStateOf(prefs.getBoolean("palm_rejection", false))

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

    var toolbarBackgroundColor by mutableIntStateOf(prefs.getInt("toolbar_background_color", AndroidColor.WHITE))

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



    fun togglePropertiesPanel() {

        showPropertiesPanel = !showPropertiesPanel

    }



    val assignedToolColors = toolbarManager.assignedToolColors

    var lastActiveColorToolId: String?
        get() = toolbarManager.lastActiveColorToolId
        set(value) { toolbarManager.lastActiveColorToolId = value }

    fun updateLastActiveToolColor(color: Int) {

        toolbarManager.updateLastActiveToolColor(color)

    }



    private val _showStrokeColorPicker = MutableStateFlow(false)

    val showStrokeColorPicker = _showStrokeColorPicker.asStateFlow()



    private val _showFillColorPicker = MutableStateFlow(false)

    val showFillColorPicker = _showFillColorPicker.asStateFlow()



    fun setShowStrokeColorPicker(show: Boolean) { _showStrokeColorPicker.value = show }

    fun setShowFillColorPicker(show: Boolean) { _showFillColorPicker.value = show }





    fun activateTool(payload: ToolPayload, toolId: String? = null) {

        if (payload == ToolPayload.STROKE_COLOR || payload == ToolPayload.FILL_COLOR) {

            lastActiveColorToolId = toolId

        }

        when(payload) {

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

                toolId?.let { id ->

                    assignedToolColors.value[id]?.let { setStrokeColor(it) }

                }

                // Do NOT open picker on activation anymore

            }

            ToolPayload.FILL_COLOR -> {
                // Toggle fill active state without resetting to solid color
                toggleFill(!isFillActive.value)
            }
            ToolPayload.POINT_ERASER -> {
                selectTool(ToolType.POINT_ERASER)
            }
            ToolPayload.CUT_ERASER -> {
                selectTool(ToolType.CUT_ERASER)
            }

        }

    }



    fun editTool(payload: ToolPayload, toolId: String? = null) {
        if (payload == ToolPayload.STROKE_COLOR || payload == ToolPayload.FILL_COLOR) {
            lastActiveColorToolId = toolId
        }
        when(payload) {
            ToolPayload.STROKE_COLOR -> _showStrokeColorPicker.value = true
            ToolPayload.FILL_COLOR -> _showFillColorPicker.value = true
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



    internal fun getActionForTool(id: String): () -> Unit = when(id) {

        "undo" -> ({ undo() })

        "redo" -> ({ redo() })

        "action_copy" -> ({ copy() })

        "action_cut" -> ({ cut() })

        "action_paste" -> ({ paste() })

        "menu" -> ({})

        "settings" -> ({})

        StudioTool.PROPERTIES_TOOL_ID -> ({ togglePropertiesPanel() })

        "zoom_in" -> ({ zoomIn() })

        "zoom_out" -> ({ zoomOut() })

        "zoom_fit" -> ({ fitContent() })

        "home_view" -> ({ resetCamera() })

        "stroke_color" -> ({ _showStrokeColorPicker.value = true })

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
 
        "stroke_freehand" -> ({ updateStrokeType(StrokeType.FREEHAND) })
        "stroke_line" -> ({ updateStrokeType(StrokeType.LINE) })
        "stroke_polyline" -> ({ updateStrokeType(StrokeType.POLYLINE) })
        "stroke_circle" -> ({ updateStrokeType(StrokeType.CIRCLE) })
        "stroke_arc" -> ({ updateStrokeType(StrokeType.ARC) })
        "stroke_ellipse" -> ({ updateStrokeType(StrokeType.ELLIPSE) })
        "stroke_spline" -> ({ updateStrokeType(StrokeType.SPLINE) })
        "stroke_bezier" -> ({ updateStrokeType(StrokeType.BEZIER) })

        "line" -> ({
             updateStrokeType(StrokeType.LINE)
        })
 
        "circle" -> ({
             updateStrokeType(StrokeType.CIRCLE)
        })
 
        "polyline" -> ({
             updateStrokeType(StrokeType.POLYLINE)
        })
 
        "arc" -> ({
             updateStrokeType(StrokeType.ARC)
        })
 
        "ellipse" -> ({
             updateStrokeType(StrokeType.ELLIPSE)
        })
 
        "spline" -> ({
             updateStrokeType(StrokeType.SPLINE)
        })
 
        "bezier" -> ({
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

        isPalmRejectionEnabled = prefs.getBoolean("palm_rejection", false)

        showTooltips = prefs.getBoolean("show_tooltips", true)

        swapVertical = prefs.getBoolean("swap_vertical", false)

        swapHorizontal = prefs.getBoolean("swap_horizontal", false)

        interfaceScale = prefs.getFloat("interface_scale", 0.8f)

        buttonSpacingFactor = prefs.getFloat("button_spacing_factor", 1.0f)

        toolbarBackgroundColor = prefs.getInt("toolbar_background_color", AndroidColor.WHITE)

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

    

    fun updateScaleConfig(u: String, b: Float) { scaleConfig = ScaleConfig(u, b); currentUnit = DistanceUnit.fromSymbol(u) }

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

    var currentFileUri: android.net.Uri? by mutableStateOf(null)

    var hasUnsavedChanges: Boolean = false



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
    val currentFreehandSettings: FreehandSettings get() = toolManager.currentFreehandSettings

    val strokeColor = toolManager.strokeColor
    val fillColor = toolManager.fillColor
    val fillStyle = toolManager.fillStyle
    val fillOpacity = toolManager.fillOpacity
    val isStrokeActive = toolManager.isStrokeActive
    val isFillActive = toolManager.isFillActive

    var isGeometricStrokeInProgress by mutableStateOf(false)
        private set

    fun updateGeometricStrokeInProgress(inProgress: Boolean) {
        isGeometricStrokeInProgress = inProgress
    }

    fun updateBrushSize(newSize: Float) = toolManager.updateBrushSize(newSize)
    fun updateBrushOpacity(newAlpha: Float) = toolManager.updateBrushOpacity(newAlpha)
    fun updateFillOpacity(opacity: Float) = toolManager.updateFillOpacity(opacity)
    fun updateStrokeType(type: StrokeType) = toolManager.updateStrokeType(type)
    
    val currentEraserShape: com.sketcher.sketchercompanionv1.dto.EraserShape
        get() = toolManager.currentEraserShape

    fun setEraserShape(shape: com.sketcher.sketchercompanionv1.dto.EraserShape) {
        toolManager.setEraserShape(shape)
    }

    fun setStrokeColor(color: Int) = toolManager.setStrokeColor(color)
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
        toolManager.selectTool(type)
    }
    val brushPresets = toolManager.brushPresets
    val selectedPresetIndex = toolManager.selectedPresetIndex
    fun saveBrushPreset(index: Int) = toolManager.saveBrushPreset(index)
    fun selectBrushPreset(index: Int) = toolManager.selectBrushPreset(index)
    fun isPresetModified(index: Int): Boolean = toolManager.isPresetModified(index)
    fun revertToPresetColor(isStroke: Boolean) {
        val index = selectedPresetIndex.value ?: 0
        val list = brushPresets.value
        if (index in list.indices) {
            val preset = list[index]
            if (isStroke) {
                preset.strokeColor?.let { setStrokeColor(it) }
            } else {
                preset.fillColor?.let { setFillColor(it) }
                preset.fillStyle?.let { setFillStyle(it) }
                preset.isFillActive?.let { toggleFill(it) }
            }
        }
    }
    val fillPresets = toolManager.fillPresets
    fun saveFillPreset(index: Int, style: FillStyle) = toolManager.saveFillPreset(index, style)

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
        selectTool(currentTool)
        toolbarManager.initLayout()

        // Periodic background autosave (runs every 30 seconds if there are changes)
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(30_000)
                if (hasUnsavedChanges) {
                    autoSaveProject(application)
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
        selectTool(ToolType.SELECTION)
        layersSnapshotBeforeTransform = createLayersSnapshot()
        selectionManager.backupOriginalElements()
        currentSelectionMode = SelectionMode.TRANSFORM_BOX
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
            val definition = ComponentDefinition(defId, elementsToComponent.map { it.copyElement() }.toMutableList())
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

            val newDef = ComponentDefinition(newDefId, copiedElements)

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

    private val _globalStabilization = MutableStateFlow(prefs.getFloat("global_stabilization", 0.07f))

    val globalStabilization = _globalStabilization.asStateFlow()



    fun updateGlobalStabilization(value: Float) {

        val clamped = value.coerceIn(0f, 1f)

        _globalStabilization.value = clamped

        setGlobalStabilization(clamped)

    }



    var globalStabilizationLevel by mutableFloatStateOf(prefs.getFloat("global_stabilization", 0.07f))

        private set



    fun setGlobalStabilization(level: Float) {

        val clamped = level.coerceIn(0f, 1f)

        if (globalStabilizationLevel != clamped) {

            globalStabilizationLevel = clamped

            _globalStabilization.value = clamped // Keep StateFlow in sync

            prefs.edit().putFloat("global_stabilization", clamped).apply()

        }

    }



    fun updateFreehandSettings(newSettings: FreehandSettings) {

        toolManager.updateFreehandSettings(newSettings)

    }



    fun setFreehandSmoothing(value: Float) {

        if (currentFreehandSettings.smoothing != value) {

            updateFreehandSettings(currentFreehandSettings.copy(smoothing = value))

        }

    }



    fun setFreehandTolerance(value: Float) {

         if (currentFreehandSettings.simplificationTolerance != value) {

            val enabled = value > 0f

            updateFreehandSettings(currentFreehandSettings.copy(

                simplificationTolerance = value,

                isSimplificationEnabled = enabled

            ))

        }

    }



    fun setFreehandPredictionLatency(ms: Float) {

         if (currentFreehandSettings.predictionLatency != ms.toLong()) {

            updateFreehandSettings(currentFreehandSettings.copy(predictionLatency = ms.toLong()))

        }

    }







    fun setFreehandMinWidth(ratio: Float) {

        if (currentFreehandSettings.minWidthRatio != ratio) {

            updateFreehandSettings(currentFreehandSettings.copy(minWidthRatio = ratio))

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
                    val settings = toolManager.getToolConfigMap()[toolType]?.freehandSettings ?: com.sketcher.sketchercompanionv1.dto.FreehandSettings()
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
                    val settings = toolManager.getToolConfigMap()[toolType]?.freehandSettings ?: com.sketcher.sketchercompanionv1.dto.FreehandSettings()
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

    }

    fun saveDimensions(w: Float, h: Float) {

        val sizeChanged = (lastViewportWidth != w || lastViewportHeight != h)

        lastViewportWidth = w

        lastViewportHeight = h

        if (sizeChanged && w > 0f && h > 0f) {

            liveProjectionController.updateViewportDimensions(w, h)

            if (isHomeCameraDefaultOrIdentity()) {

                centerPaperAsHomeCamera()

            }

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

        val targetScale = (currentScale * 1.2f).coerceIn(0.2f, 12.0f)

        val snapThreshold = 0.08f

        val finalScale = if (kotlin.math.abs(targetScale - 1.0f) < snapThreshold) 1.0f else targetScale

        val factor = finalScale / currentScale

        m.postScale(factor, factor, lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)

        saveCameraState(m)

    }



    fun zoomOut() {

        if (lastViewportWidth <= 0.0f || lastViewportHeight <= 0.0f) return

        val m = Matrix(_cameraMatrix.value)

        val currentScale = getMatrixScale(m)

        val targetScale = (currentScale * 0.8f).coerceIn(0.2f, 12.0f)

        val snapThreshold = 0.08f

        val finalScale = if (kotlin.math.abs(targetScale - 1.0f) < snapThreshold) 1.0f else targetScale

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



    private fun getExportSource(useHomeView: Boolean): Pair<RectF, Float> {

        val bounds = RectF()

        

        if (useHomeView) {

            bounds.set(0f, 0f, lastViewportWidth, lastViewportHeight)

        } else {

             val visibleBounds = calculateVisibleBounds()

             if (visibleBounds.isEmpty) return Pair(RectF(0f,0f,100f,100f), 1f)

             val padding = kotlin.math.max(visibleBounds.width(), visibleBounds.height()) * 0.05f

             visibleBounds.inset(-padding, -padding)

             bounds.set(visibleBounds)

        }

        return Pair(bounds, 1f)

    }



    fun renderExportBitmap(config: ExportPngConfig): Bitmap? {

        try {

             val (sourceBounds, _) = getExportSource(config.useHomeView)

             if (config.width <= 0 || config.height <= 0) return null

             

             val bitmap = Bitmap.createBitmap(config.width, config.height, Bitmap.Config.ARGB_8888)

             val canvas = Canvas(bitmap)

             

             if (!config.transparentBackground) {

                 canvas.drawColor(backgroundColor)

             } else {

                 canvas.drawColor(AndroidColor.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)

             }

             

             val matrix = Matrix()

             if (config.useHomeView) {

                 matrix.setValues(homeCameraMatrixValues)

                 val scaleX = config.width.toFloat() / lastViewportWidth

                 val scaleY = config.height.toFloat() / lastViewportHeight

                 matrix.postScale(scaleX, scaleY)

             } else {

                 matrix.postTranslate(-sourceBounds.left, -sourceBounds.top)

                 val scaleX = config.width.toFloat() / sourceBounds.width()

                 val scaleY = config.height.toFloat() / sourceBounds.height()

                 matrix.postScale(scaleX, scaleY)

             }

             

             canvas.save()

             canvas.concat(matrix)

             for (layer in layers) {

                 if (!layer.isVisible) continue

                 val layerAlpha = if (layer.opacity < 1f) (layer.opacity * 255).toInt() else 255

                 val saveCount = if (layerAlpha < 255) canvas.saveLayerAlpha(null, layerAlpha) else canvas.save()

                 for (element in layer.elements) RenderHelper.drawElementRecursive(canvas, element, componentLibrary)

                 canvas.restoreToCount(saveCount)

             }

             canvas.restore()

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
            val toolSettings = toolManager.getToolConfigMap()[toolType]?.freehandSettings ?: FreehandSettings()
            shouldJoin = toolSettings.paintJoinPrevious
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

        flipVertical: Boolean

    ) {

        val state = activeImageEditState ?: return

        activeImageEditState = null



        if (state.isNewImport) {

            if (activeLayerIndex !in layers.indices) return

            performSnapshotAction("Insertar Imagen") {

                val currentLayers = layers.toMutableList()

                val layer = currentLayers[activeLayerIndex]

                val matrix = Matrix()

                if (lastViewportWidth > 0.0f && lastViewportHeight > 0.0f) {

                     matrix.postTranslate(lastViewportWidth / 2.0f - processedBitmap.width / 2.0f, lastViewportHeight / 2.0f - processedBitmap.height / 2.0f)

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

                    flipVertical = flipVertical

                )

                layer.elements.add(element)

                currentLayers[activeLayerIndex] = layer.copy()

                layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)

                selectionManager.clearSelection()

                selectionManager.selectedElements.add(element)

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

        val autosaveFile = java.io.File(context.cacheDir, "autosave.skc")

        saveProjectToZip(context, android.net.Uri.fromFile(autosaveFile), isAutosave = true)

    }



    fun saveProjectToZip(context: Context, uri: android.net.Uri, isAutosave: Boolean = false) {

        val currentLayersSnapshot = layers.map { it.copy(elements = it.elements.toMutableStateList()) } // Shallow-ish copy

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

        val thumbnailBmp = try {

            renderExportBitmap(ExportPngConfig(transparentBackground = false, useHomeView = false, width = 256, height = 256))

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

                    layers = currentLayersSnapshot.map { it.toLayerJson() },

                    backgroundConfig = BackgroundConfig(color = savedBgColor, gridConfig = savedGridConfig, fillStyle = savedBgStyle.toFillStyleJson()),

                    paletteColors = emptyList(),

                    toolConfigs = emptyMap(),

                    canvasMetadata = CanvasMetadata(

                        width = savedViewportW, height = savedViewportH, 

                        cameraMatrix = savedCameraMatrix,

                        scaleConfig = savedScaleConfig.copy(unitName = savedUnit.symbol)

                    ),

                    componentLibrary = currentComponentLibrary.mapValues { it.value.toComponentDefinitionJson() },

                    canvasSizeConfig = canvasSizeConfig

                )

                com.sketcher.sketchercompanionv1.utils.ZipStorageManager.saveProject(

                    context = context,

                    projectData = projectData,

                    layers = currentLayersSnapshot,

                    uri = uri,

                    components = currentComponentLibrary.values,

                    thumbnail = thumbnailBmp

                )



                // Delete autosave file if user successfully saved manually

                if (!isAutosave) {

                    val autosaveFile = java.io.File(context.cacheDir, "autosave.skc")

                    if (autosaveFile.exists()) {

                        autosaveFile.delete()

                    }

                }



                withContext(Dispatchers.Main) {

                    if (!isAutosave) {

                        currentFileUri = uri

                        hasUnsavedChanges = false

                        android.widget.Toast.makeText(context, "Proyecto guardado correctamente", android.widget.Toast.LENGTH_SHORT).show()

                    } else {

                        hasUnsavedChanges = false // Autosave cleared the unsaved changes state until next change

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

                    restoreProjectState(projectData, bitmapMap, svgMap)

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

    

    private fun restoreProjectState(data: ProjectData, bitmaps: Map<String, android.graphics.Bitmap>, svgs: Map<String, String>) {

        val newLayers = mutableListOf<Layer>()

        

        val activeLayer = layers[activeLayerIndex]

        activeLayer.elements.clear()

        layers[activeLayerIndex] = activeLayer.copy()

        

        undoStack.clear()

        redoStack.clear()

        updateUndoRedoSupport()

        

        projectId = data.id

        

        componentLibrary.clear()

        data.componentLibrary.forEach { (id, json) ->

             componentLibrary[id] = json.toComponentDefinition( { bitmaps[it] }, { svgs[it] } )

        }



        data.layers.forEach { l -> newLayers.add(l.toLayer( { bitmaps[it] }, { svgs[it] } )) }

        layerManager.internalUpdateLayers(newLayers, 0)

        

        // Restore ToolConfigs (Cleaned)

        data.toolConfigs.forEach { (t, c) -> toolManager.applyToolConfig(t, c) }

        selectTool(currentTool) // Refresh

        

        backgroundColor = data.backgroundConfig.color
        backgroundStyle = data.backgroundConfig.fillStyle.toFillStyle(data.backgroundConfig.color)

        val loadedGrid = data.backgroundConfig.gridConfig

        gridConfig = loadedGrid ?: GridConfig()

        

        canvasSizeConfig = data.canvasSizeConfig

        

        // Camera

        if (data.canvasMetadata.cameraMatrix.size == 9) {

             for(i in 0..8) cameraMatrixValues[i] = data.canvasMetadata.cameraMatrix[i]

        }

        cameraUpdateTrigger++



        if (isHomeCameraDefaultOrIdentity() && lastViewportWidth > 0f && lastViewportHeight > 0f) {

            centerPaperAsHomeCamera()

        }

    }

    





    // --- MISSING METHODS ---







    private fun calculateVisibleBounds(): RectF {

        val totalBounds = RectF()

        var first = true

        

        for (layer in layers) {

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

            componentLibrary = componentLibrary.mapValues { it.value.toComponentDefinitionJson() }

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

                    componentLibrary = currentComponentLibrary.mapValues { it.value.toComponentDefinitionJson() }

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
                                        val settings = toolManager.getToolConfigMap()[toolType]?.freehandSettings ?: FreehandSettings()
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

        currentFileUri = null

        backgroundColor = android.graphics.Color.WHITE
        backgroundStyle = FillStyle.Solid(android.graphics.Color.WHITE)

        gridConfig = GridConfig()

        

        // Reset camera

        val identity = android.graphics.Matrix()

        identity.getValues(cameraMatrixValues)

        identity.getValues(homeCameraMatrixValues)

        prefs.edit().remove("home_camera_matrix_v4").apply()

        cameraUpdateTrigger++

        

        val newLayer = Layer("l_${System.currentTimeMillis()}", "Capa 1", androidx.compose.runtime.mutableStateListOf())

        layerManager.internalUpdateLayers(mutableListOf(newLayer), 0)

        selectionManager.clearSelection()

        notifyLayersChanged()

    }



    fun saveTemplate(context: Context, name: String) {

         val currentLayersSnapshot = layers.map { it.copy(elements = it.elements.toMutableStateList()) }

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

                 layers = currentLayersSnapshot.map { it.toLayerJson() },

                 backgroundConfig = com.sketcher.sketchercompanionv1.dto.BackgroundConfig(savedBgColor, savedGridConfig, savedBgStyle.toFillStyleJson()),

                 paletteColors = emptyList(),

                 toolConfigs = emptyMap(), // Simplify for now or map toolConfigs

                 canvasMetadata = com.sketcher.sketchercompanionv1.dto.CanvasMetadata(

                     width = 2000f, // Use actualviewport if possible

                     height = 2000f,

                     cameraMatrix = emptyList(), // Simplify

                     scaleConfig = savedScaleConfig

                 )

             )

             

             com.sketcher.sketchercompanionv1.utils.TemplateManager.saveAsTemplate(

                 context = context, 

                 projectData = projectData,

                 layers = currentLayersSnapshot,

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
                    val assetsDir = java.io.File(context.filesDir, "library_assets")
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
        fun getChildrenIds(parentId: String): List<String> {
            val children = _globalLibraryItems.value.filter { it.parentId == parentId }
            return children.map { it.id } + children.flatMap { getChildrenIds(it.id) }
        }
        val toDelete = setOf(id) + getChildrenIds(id)
        _globalLibraryItems.value = _globalLibraryItems.value.filterNot { it.id in toDelete }
        saveGlobalLibrary(context)
    }

    fun moveLibraryItem(context: Context, id: String, newParentId: String?) {
        _globalLibraryItems.value = _globalLibraryItems.value.map {
            if (it.id == id) {
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
            val definition = ComponentDefinition(definitionId, mutableListOf(element))
            
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
                    val assetsDir = java.io.File(context.filesDir, "library_assets")
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

    fun addImageToGlobalLibrary(context: Context, name: String, bitmap: android.graphics.Bitmap, parentId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val imgFileName = "img_" + java.util.UUID.randomUUID().toString() + ".png"
            val assetsDir = java.io.File(context.filesDir, "library_assets")
            if (!assetsDir.exists()) assetsDir.mkdirs()
            val destFile = java.io.File(assetsDir, imgFileName)
            try {
                val out = java.io.FileOutputStream(destFile)
                bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                out.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            val imageElement = ImageElement(
                id = java.util.UUID.randomUUID().toString(),
                bitmap = bitmap,
                imageFileName = imgFileName,
                matrix = android.graphics.Matrix()
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
            val definition = ComponentDefinition(defId, component.definition.elements.map { it.copyElement() }.toMutableList())
            componentLibrary[defId] = definition
            
            val viewportCenter = floatArrayOf(lastViewportWidth / 2f, lastViewportHeight / 2f)
            val inverseCamera = android.graphics.Matrix()
            if (_cameraMatrix.value.invert(inverseCamera)) {
                inverseCamera.mapPoints(viewportCenter)
            }
            
            val dummyInstance = ComponentInstance("dummy", defId)
            val bounds = dummyInstance.getBoundingBox(componentLibrary)
            
            val dx = viewportCenter[0] - bounds.centerX()
            val dy = viewportCenter[1] - bounds.centerY()
            
            val instance = ComponentInstance(
                id = "inst_" + java.util.UUID.randomUUID().toString(),
                definitionId = defId
            )
            
            instance.matrix.postTranslate(dx, dy)
            
            activeContainer.add(instance)
            selectionManager.clearSelection()
            selectionManager.selectedElements.add(instance)
            selectionManager.recalculateBaseBounds(componentLibrary)
            if (editingContext == null) {
                notifyLayersChanged()
            }
        }
    }

    // --- DASHBOARD AND FOLDER MANAGEMENT ---
    var showDashboard by mutableStateOf(true)
    var currentDirectory by mutableStateOf<java.io.File?>(null)
        private set
    val localItems = mutableStateListOf<DashboardItem>()
    val thumbnailCache = mutableStateMapOf<String, android.graphics.Bitmap?>()

    fun initLocalProjects(context: Context) {
        val rootDir = java.io.File(context.filesDir, "projects")
        if (!rootDir.exists()) {
            rootDir.mkdirs()
        }
        if (currentDirectory == null) {
            currentDirectory = rootDir
        }
        refreshLocalItems()
    }

    fun navigateToFolder(folder: java.io.File) {
        currentDirectory = folder
        refreshLocalItems()
    }

    fun navigateUp(context: Context): Boolean {
        val current = currentDirectory ?: return false
        val rootDir = java.io.File(context.filesDir, "projects")
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
                    DashboardItem.Project(
                        name = file.nameWithoutExtension,
                        path = file.absolutePath,
                        lastModified = file.lastModified(),
                        sizeBytes = file.length()
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
            }
        }
    }

    fun createLocalProject(context: Context, name: String) {
        val dir = currentDirectory ?: return
        val file = java.io.File(dir, "$name.skc")
        if (file.exists()) {
            android.widget.Toast.makeText(context, "El archivo ya existe", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        clear()
        currentFileUri = android.net.Uri.fromFile(file)
        saveProjectToZip(context, currentFileUri!!)
        showDashboard = false
    }

    fun createLocalFolder(context: Context, name: String) {
        val dir = currentDirectory ?: return
        val folder = java.io.File(dir, name)
        if (folder.exists()) {
            android.widget.Toast.makeText(context, "La carpeta ya existe", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        folder.mkdirs()
        refreshLocalItems()
    }

    fun renameLocalItem(context: Context, item: DashboardItem, newName: String) {
        val oldFile = java.io.File(item.path)
        val parent = oldFile.parentFile ?: return
        val extension = if (item is DashboardItem.Project) ".skc" else ""
        val newFile = java.io.File(parent, "$newName$extension")
        if (newFile.exists()) {
            android.widget.Toast.makeText(context, "Ya existe un elemento con ese nombre", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
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
            }
            refreshLocalItems()
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
            file.deleteRecursively()
            withContext(Dispatchers.Main) {
                thumbnailCache.remove(item.path)
                refreshLocalItems()
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
            }
            refreshLocalItems()
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
        val fileName = com.sketcher.sketchercompanionv1.utils.BitmapUtils.getFileNameFromUri(context, uri) ?: "imported_drawing.skc"
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

    fun exitEditorToDashboard(context: Context) {
        val uri = currentFileUri
        if (uri != null && hasUnsavedChanges) {
            saveProjectToZip(context, uri)
        }
        showDashboard = true
        refreshLocalItems()
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
        val sizeBytes: Long
    ) : DashboardItem
}
package com.sketcher.sketchercompanionv1

import android.app.Application
import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
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

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext
import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.TemplateManager
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
import com.sketcher.sketchercompanionv1.command.*
import com.sketcher.sketchercompanionv1.data.ThemeRepository
import com.sketcher.sketchercompanionv1.data.ToolbarRepository
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.components.ToolPayload
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

data class ExportPngConfig(
    val transparentBackground: Boolean,
    val useHomeView: Boolean,
    val width: Int,
    val height: Int
)


class SketcherViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
    private val themeRepository = ThemeRepository(application)
    private val toolbarRepository = ToolbarRepository(application)
    private val projectFileManager = com.sketcher.sketchercompanionv1.managers.ProjectFileManager()
    val toolManager = com.sketcher.sketchercompanionv1.managers.ToolManager(application)
    val selectionManager = SelectionManager()

    // STATE
    // --- UI/DEBUG SETTINGS (Restored) ---
    var isDebugWireframe by mutableStateOf(false)
    var lastViewportWidth by mutableFloatStateOf(0f)
    var lastViewportHeight by mutableFloatStateOf(0f)
    


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

    // CANVAS SIZE CONFIG
    var canvasSizeConfig by mutableStateOf<CanvasSizeConfig?>(null)
        private set

    // SETTINGS
    var isRotationLocked by mutableStateOf(prefs.getBoolean("rotation_lock", false))
    var isPalmRejectionEnabled by mutableStateOf(prefs.getBoolean("palm_rejection", false))
    var showTooltips by mutableStateOf(prefs.getBoolean("show_tooltips", true))
    
    var interfaceScale by mutableStateOf(prefs.getFloat("interface_scale", 1.0f))
        private set
        
    fun updateInterfaceScale(scale: Float) {
        // Guard against invalid values coming from UI controls (NaN/Infinite)
        if (!scale.isFinite()) return

        val clampedScale = scale.coerceIn(0.5f, 1.5f)
        if (!clampedScale.isFinite() || clampedScale <= 0f) return

        interfaceScale = clampedScale
        prefs.edit().putFloat("interface_scale", clampedScale).apply()
    }

    // BACKGROUND COLOR
    var backgroundColor by mutableIntStateOf(AndroidColor.WHITE)

    // Toolbar Appearance
    var toolbarBackgroundColor by mutableIntStateOf(prefs.getInt("toolbar_background_color", AndroidColor.WHITE))
    fun updateToolbarBackgroundColor(color: Int) { 
        toolbarBackgroundColor = color
        prefs.edit().putInt("toolbar_background_color", color).apply()
    }

    // --- TOOLBAR STATE (Dynamic Slot System) ---
    private val _toolbarState = MutableStateFlow<Map<ToolLocation, List<StudioTool>>>(emptyMap())
    val toolbarState = _toolbarState.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode = _isEditMode.asStateFlow()

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    // --- PROPERTIES PANEL STATE ---
    var showPropertiesPanel by mutableStateOf(false)
        private set

    fun togglePropertiesPanel() {
        showPropertiesPanel = !showPropertiesPanel
    }

    private val _assignedToolColors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val assignedToolColors = _assignedToolColors.asStateFlow()

    private var lastActiveColorToolId: String? = null

    fun updateLastActiveToolColor(color: Int) {
        lastActiveColorToolId?.let { id ->
            _assignedToolColors.value = _assignedToolColors.value + (id to color)
            toolbarRepository.saveLayout(_toolbarState.value, _assignedTools.value, _assignedToolColors.value)
        }
    }

    private val _showStrokeColorPicker = MutableStateFlow(false)
    val showStrokeColorPicker = _showStrokeColorPicker.asStateFlow()

    private val _showFillColorPicker = MutableStateFlow(false)
    val showFillColorPicker = _showFillColorPicker.asStateFlow()

    fun setShowStrokeColorPicker(show: Boolean) { _showStrokeColorPicker.value = show }
    fun setShowFillColorPicker(show: Boolean) { _showFillColorPicker.value = show }


    fun activateTool(payload: ToolPayload, toolId: String? = null) {
        lastActiveColorToolId = toolId
        when(payload) {
            ToolPayload.PENCIL -> selectTool(ToolType.FREEHAND)
            ToolPayload.ERASER -> selectTool(ToolType.ERASER)
            ToolPayload.STROKE_COLOR -> {
                toolId?.let { id ->
                    _assignedToolColors.value[id]?.let { setStrokeColor(it) }
                }
                // Do NOT open picker on activation anymore
            }
            ToolPayload.FILL_COLOR -> {
                toolId?.let { id ->
                    _assignedToolColors.value[id]?.let { setFillColor(it) }
                }
                // Do NOT open picker on activation anymore
            }
        }
    }

    fun editTool(payload: ToolPayload) {
         when(payload) {
             ToolPayload.STROKE_COLOR -> _showStrokeColorPicker.value = true
             ToolPayload.FILL_COLOR -> _showFillColorPicker.value = true
             else -> {} // Other tools might not have edit dialogs yet
         }
    }

    // --- ASSIGNED TOOLS STATE ---
    private val _assignedTools = MutableStateFlow<Map<String, ToolPayload>>(emptyMap())
    val assignedTools = _assignedTools.asStateFlow()
    
    private fun getPayloadFromToolId(id: String): ToolPayload? = when(id) {
        "pencil" -> ToolPayload.PENCIL
        "eraser" -> ToolPayload.ERASER
        "stroke_color" -> ToolPayload.STROKE_COLOR
        "fill_color" -> ToolPayload.FILL_COLOR
        else -> null
    }

    fun assignTool(toolId: String, payload: ToolPayload) {
        // 1. Update Map
        _assignedTools.value = _assignedTools.value + (toolId to payload)
        
        // Initialize tool color if it's a color tool
        if (payload == ToolPayload.STROKE_COLOR) {
            _assignedToolColors.value = _assignedToolColors.value + (toolId to strokeColor.value)
        } else if (payload == ToolPayload.FILL_COLOR) {
            _assignedToolColors.value = _assignedToolColors.value + (toolId to fillColor.value)
        }

        // 2. Update List (Visuals) to reflect the new tool's icon/desc
        val currentMap = _toolbarState.value.toMutableMap()
        for ((loc, list) in currentMap) {
            val idx = list.indexOfFirst { it.id == toolId }
            if (idx != -1) {
                val oldTool = list[idx]
                val newList = list.toMutableList()
                newList[idx] = oldTool.copy(
                    icon = payload.icon,
                    contentDescription = payload.label,
                    isPlaceholder = false 
                )
                currentMap[loc] = newList
                _toolbarState.value = currentMap
                break
            }
        }
        
        // 3. Activate
        activateTool(payload, toolId)
        
        // 4. Save
        toolbarRepository.saveLayout(_toolbarState.value, _assignedTools.value, _assignedToolColors.value)
    }

    fun addTool(location: ToolLocation, tool: StudioTool) {
        val uniqueId = UUID.randomUUID().toString()
        val initialPayload = getPayloadFromToolId(tool.id)
        
        if (initialPayload != null) {
            _assignedTools.value = _assignedTools.value + (uniqueId to initialPayload)
        }

        val uniqueTool = tool.copy(
            id = uniqueId,
            registryId = tool.id,
            onClick = {
                val payload = _assignedTools.value[uniqueId]
                if (payload != null) {
                    activateTool(payload, uniqueId)
                } else {
                    // Use registryId because it's the stable identifier for actions
                    getActionForTool(tool.id).invoke() 
                }
            }
        )
        
        val currentMap = _toolbarState.value.toMutableMap()
        val list = currentMap[location]?.toMutableList() ?: mutableListOf()
        list.add(uniqueTool)
        currentMap[location] = list
        _toolbarState.value = currentMap
        
        toolbarRepository.saveLayout(_toolbarState.value, _assignedTools.value, _assignedToolColors.value)
    }

    fun removeTool(location: ToolLocation, index: Int) {
        val currentMap = _toolbarState.value.toMutableMap()
        val list = currentMap[location]?.toMutableList() ?: return
        if (index in list.indices) {
            val tool = list.removeAt(index)
            // Cleanup assignment
            _assignedTools.value = _assignedTools.value - tool.id
            
            currentMap[location] = list
            _toolbarState.value = currentMap
            toolbarRepository.saveLayout(_toolbarState.value, _assignedTools.value, _assignedToolColors.value)
        }
    }

    fun replaceTool(location: ToolLocation, index: Int, newTool: StudioTool) {
        val currentMap = _toolbarState.value.toMutableMap()
        val list = currentMap[location]?.toMutableList() ?: return
        if (index in list.indices) {
            val oldTool = list[index]
            _assignedTools.value = _assignedTools.value - oldTool.id
            
            val uniqueId = UUID.randomUUID().toString()
            val initialPayload = getPayloadFromToolId(newTool.id)
            
            if (initialPayload != null) {
                _assignedTools.value = _assignedTools.value + (uniqueId to initialPayload)
            }
            
            val uniqueTool = newTool.copy(
                id = uniqueId,
                registryId = newTool.id,
                onClick = {
                    val payload = _assignedTools.value[uniqueId]
                    if (payload != null) {
                        activateTool(payload, uniqueId)
                    } else {
                        getActionForTool(newTool.id).invoke()
                    }
                }
            )
            
            list[index] = uniqueTool
            currentMap[location] = list
            _toolbarState.value = currentMap
            toolbarRepository.saveLayout(_toolbarState.value, _assignedTools.value, _assignedToolColors.value)
        }
    }

    private fun getActionForTool(id: String): () -> Unit = when(id) {
        "undo" -> ({ undo() })
        "redo" -> ({ redo() })
        "menu" -> ({})
        "settings" -> ({})
        StudioTool.PROPERTIES_TOOL_ID -> ({ togglePropertiesPanel() })
        "zoom_in" -> ({ zoomIn() })
        "zoom_out" -> ({ zoomOut() })
        "zoom_fit" -> ({ fitContent() })
        "home_view" -> ({ resetCamera() })
        "stroke_color" -> ({ _showStrokeColorPicker.value = true })
        "fill_color" -> ({ _showFillColorPicker.value = true })
        else -> ({})
    }

    private fun initToolbarState() {
        _toolbarState.value = mapOf(
            ToolLocation.LeftBar to listOf(
                StudioTool("brush", Icons.Default.Brush, "Brush", isPlaceholder = false),
                StudioTool("undo", Icons.Default.Undo, "Undo", isPlaceholder = true) { undo() },
                StudioTool("redo", Icons.Default.Redo, "Redo", isPlaceholder = true) { redo() }
            ),
            ToolLocation.RightBar to listOf(
                StudioTool(StudioTool.PROPERTIES_TOOL_ID, Icons.Default.Tune, "Properties", isPlaceholder = false)
            ),
            ToolLocation.TopBar to listOf(),
            ToolLocation.BottomBar to listOf(),
            ToolLocation.TopLeftCorner to listOf(
                StudioTool("menu", Icons.Default.Menu, "Menu", isPlaceholder = false)
            ),
            ToolLocation.TopRightCorner to listOf(
                StudioTool("settings", Icons.Default.Settings, "Settings", isPlaceholder = false)
            ),
            ToolLocation.BottomLeftCorner to listOf(
                StudioTool("undo", Icons.Default.Undo, "Undo", isPlaceholder = false) { undo() }
            ),
            ToolLocation.BottomRightCorner to listOf(
                StudioTool("redo", Icons.Default.Redo, "Redo", isPlaceholder = false) { redo() }
            )
        )
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
    
    fun updateScaleConfig(u: String, b: Float) { scaleConfig = ScaleConfig(u, b); currentUnit = DistanceUnit.fromSymbol(u) }
    fun updateGridConfig(v: Boolean, s: Float, c: Int, c2: Int, c3: Int) { gridConfig = GridConfig(v, s, c, c2, c3) }
    fun setUnit(u: DistanceUnit) { currentUnit = u; scaleConfig = scaleConfig.copy(unitName = u.symbol) }
    fun updateCanvasSize(config: CanvasSizeConfig?) { canvasSizeConfig = config }

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

    var lastExportPngConfig by mutableStateOf(ExportPngConfig(transparentBackground = false, useHomeView = true, width = 1920, height = 1080))
    var lastExportSvgConfig by mutableStateOf(ExportSvgConfig(includeBackground = true, useHomeView = true, width = 1920f, height = 1080f))
    var dxfExportConfig by mutableStateOf(DxfExportConfig("", false))

    // --- TOOL STATE & CONFIG (Delegated to ToolManager) ---
    val currentTool: ToolType get() = toolManager.currentTool
    val currentStrokeType: StrokeType get() = toolManager.currentStrokeType
    
    val brushSize: StateFlow<Float> = toolManager.brushSize
    val brushOpacity: StateFlow<Float> = toolManager.brushOpacity
    val currentSize: Float get() = toolManager.currentSize
    val currentOpacity: Float get() = toolManager.currentOpacity
    val currentFreehandSettings: FreehandSettings get() = toolManager.currentFreehandSettings

    val strokeColor: StateFlow<Int> = toolManager.strokeColor
    val fillColor: StateFlow<Int> = toolManager.fillColor
    val isStrokeActive: StateFlow<Boolean> = toolManager.isStrokeActive
    val isFillActive: StateFlow<Boolean> = toolManager.isFillActive

    var isGeometricStrokeInProgress by mutableStateOf(false)
        private set

    fun updateGeometricStrokeInProgress(inProgress: Boolean) {
        isGeometricStrokeInProgress = inProgress
    }

    fun updateBrushSize(newSize: Float) = toolManager.updateBrushSize(newSize)
    fun updateBrushOpacity(newAlpha: Float) = toolManager.updateBrushOpacity(newAlpha)
    fun updateStrokeType(type: StrokeType) = toolManager.updateStrokeType(type)
    
    fun setStrokeColor(color: Int) = toolManager.setStrokeColor(color)
    fun setFillColor(color: Int) = toolManager.setFillColor(color)
    fun toggleStroke(enabled: Boolean) = toolManager.toggleStroke(enabled)
    fun toggleFill(enabled: Boolean) = toolManager.toggleFill(enabled)
    
    fun selectTool(type: ToolType) = toolManager.selectTool(type)
    fun saveSizePreset(index: Int, size: Float) = toolManager.saveSizePreset(index, size)
    val sizePresets: StateFlow<List<Float>> = toolManager.sizePresets

    // legacy methods removed...
    
    // --- EXPOSED CONFIGS ---
    var fingerModeActive by mutableStateOf(false)
        private set
    var fingerOffsetXValue by mutableFloatStateOf(0f)
        private set
    var fingerOffsetYValue by mutableFloatStateOf(50f)
        private set

    init {
        selectTool(currentTool)
        
        // Try to load saved layout
        val loaded = toolbarRepository.loadLayout()
        if (loaded != null) {
            val (tools, assigned, colors) = loaded
            _assignedTools.value = assigned
            _assignedToolColors.value = colors
            
            // Re-bind actions (since they aren't serialized)
            val toolsWithActions = tools.mapValues { (_, list) ->
                list.map { tool ->
                    tool.copy(onClick = {
                        val payload = _assignedTools.value[tool.id]
                        if (payload != null) {
                            activateTool(payload, tool.id)
                        } else {
                            // Use registryId to lookup action
                            getActionForTool(tool.registryId).invoke()
                        }
                    })
                }
            }
            _toolbarState.value = toolsWithActions
        } else {
            initToolbarState()
        }
    }

    // --- GLOBAL OFFSET SETTERS ---
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
    enum class SelectionMode { RECTANGLE, FREEHAND, TRANSFORM_BOX }
    enum class SelectionScope { CURRENT_LAYER, ALL_LAYERS }
    var currentSelectionMode by mutableStateOf(SelectionMode.RECTANGLE)
    var selectionScope by mutableStateOf(SelectionScope.CURRENT_LAYER)
    var isSelectionAspectRatioLocked by mutableStateOf(true)

    val isGroupSelected: Boolean
        get() = selectionManager.selectedElements.size == 1 && (selectionManager.selectedElements.first() is GroupElement)

    val canEnterEditMode: Boolean
        get() = selectionManager.selectedElements.size == 1 && (selectionManager.selectedElements.first() is GroupElement || selectionManager.selectedElements.first() is ComponentInstance)

    val isSelectionEmpty: Boolean get() = selectionManager.selectedElements.isEmpty()

    fun deleteSelection() {
        if (selectionManager.selectedElements.isEmpty()) return
        performSnapshotAction("Borrar Selección") {
            val newList = layers.toMutableList()
            newList.forEachIndexed { index, layer ->
                val remaining = layer.elements.filter { it !in selectionManager.selectedElements }.toMutableList()
                if (remaining.size != layer.elements.size) {
                    newList[index] = layer.copy(elements = remaining.toMutableStateList())
                }
            }
            layerManager.internalUpdateLayers(newList, activeLayerIndex)
            selectionManager.clearSelection()
        }
    }

    // --- RESTORED LOGIC ---

    fun makeComponent() {
        if (selectionManager.selectedElements.isEmpty()) return
        
        performSnapshotAction("Crear Componente") {
            val elementsToComponent = selectionManager.selectedElements.toList()
            val defId = "comp_${UUID.randomUUID()}"
            val definition = ComponentDefinition(defId, elementsToComponent.map { it.copyElement() }.toMutableList())
            componentLibrary[defId] = definition
            
            // Remove from current container and add instance
            activeContainer.removeAll(elementsToComponent)
            val instance = ComponentInstance(
                id = "inst_${UUID.randomUUID()}",
                definitionId = defId
            )
            activeContainer.add(instance)
            
            selectionManager.clearSelection()
            if (editingContext == null) {
                val newList = layers.toMutableList()
                newList[activeLayerIndex] = newList[activeLayerIndex].copy()
                layerManager.internalUpdateLayers(newList, activeLayerIndex)
            }
        }
    }

    fun enterEditMode() {
        if (selectionManager.selectedElements.size != 1) return
        val selected = selectionManager.selectedElements.first()
        
        if (selected is GroupElement) {
            editingContext = selected.elements as? MutableList<LayerElement>
            editingContainerMatrix = Matrix(selected.matrix)
            selectionManager.clearSelection()
        } else if (selected is ComponentInstance) {
            val definition = componentLibrary[selected.definitionId]
            if (definition != null) {
                editingContext = definition.elements
                editingContainerMatrix = Matrix(selected.matrix)
                selectionManager.clearSelection()
            }
        }
    }

    fun exitEditMode() { 
        editingContext = null
        editingContainerMatrix = null
        selectionManager.clearSelection()
    }

    fun groupSelection() {
        val selected = selectionManager.selectedElements
        if (selected.isEmpty()) return
        
        performSnapshotAction("Agrupar") {
            // 1. Create Group
            val group = GroupElement(
                id = UUID.randomUUID().toString(),
                elements = selected.toMutableList(),
                matrix = Matrix()
            )
            
            val currentLayers = layers.toMutableList()
            val activeLayer = currentLayers[activeLayerIndex]
            
            // Remove from source layers
            currentLayers.forEachIndexed { index, layer ->
                 if (layer.elements.removeAll(selected)) {
                     currentLayers[index] = layer.copy()
                 }
            }
            
            // Re-find active layer after potentially copying all
            val targetLayer = currentLayers[activeLayerIndex]
            targetLayer.elements.add(group)
            currentLayers[activeLayerIndex] = targetLayer.copy()
            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            
            selectionManager.clearSelection()
            selectionManager.selectedElements.add(group)
            selectionManager.recalculateBaseBounds(componentLibrary)
        }
    }

    fun ungroupSelection() {
        val selected = selectionManager.selectedElements
        val groups = selected.filterIsInstance<GroupElement>()
        if (groups.isEmpty()) return
        
        performSnapshotAction("Desagrupar") {
            val currentLayers = layers.toMutableList()
            groups.forEach { group ->
                 currentLayers.forEachIndexed { layerIndex, layer ->
                     if (layer.elements.contains(group)) {
                         layer.elements.remove(group)
                         
                         val children = group.elements.map { child ->
                             if (child is com.sketcher.sketchercompanionv1.Transformable) {
                                 val newChild = child.copyElement()
                                 if (newChild is com.sketcher.sketchercompanionv1.Transformable) {
                                      newChild.transform(group.matrix)
                                 }
                                 newChild
                             } else {
                                 child 
                             }
                         }
                         
                         layer.elements.addAll(children)
                         currentLayers[layerIndex] = layer.copy()
                     }
                 }
            }
            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            selectionManager.clearSelection()
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
            
            val currentLayers = layers.toMutableList()
            val offset = 50f 
            val m = Matrix()
            m.postTranslate(offset, offset)
            
            val pasted = clipboard.map { 
                it.copyElement().apply { 
                    if (this is com.sketcher.sketchercompanionv1.Transformable) transform(m)
                } 
            }
            
            val activeLayer = currentLayers[activeLayerIndex]
            activeLayer.elements.addAll(pasted)
            currentLayers[activeLayerIndex] = activeLayer.copy()
            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            
            selectionManager.clearSelection()
            selectionManager.selectedElements.addAll(pasted)
            selectionManager.recalculateBaseBounds(componentLibrary)
        }
    }

    // --- GLOBAL STABILIZER ---
    private val _smoothing = MutableStateFlow(prefs.getFloat("global_stabilization", 0f))
    val smoothing = _smoothing.asStateFlow()

    fun updateSmoothing(value: Float) {
        val clamped = value.coerceIn(0f, 1f)
        _smoothing.value = clamped
        setGlobalStabilization(clamped)
    }

    var globalStabilizationLevel by mutableFloatStateOf(prefs.getFloat("global_stabilization", 0f))
        private set

    fun setGlobalStabilization(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        if (globalStabilizationLevel != clamped) {
            globalStabilizationLevel = clamped
            _smoothing.value = clamped // Keep StateFlow in sync
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
         if (currentFreehandSettings.tolerance != value) {
            val enabled = value > 0f
            updateFreehandSettings(currentFreehandSettings.copy(
                tolerance = value,
                isSimplificationEnabled = enabled
            ))
        }
    }

    fun setFreehandPredictionLatency(ms: Float) {
         if (currentFreehandSettings.predictionLatency != ms) {
            updateFreehandSettings(currentFreehandSettings.copy(predictionLatency = ms))
        }
    }



    fun setFreehandMinWidth(ratio: Float) {
        if (currentFreehandSettings.minWidthRatio != ratio) {
            updateFreehandSettings(currentFreehandSettings.copy(minWidthRatio = ratio))
        }
    }

    fun setFreehandMinPredictionVelocity(speed: Float) {
        if (currentFreehandSettings.minPredictionVelocity != speed) {
            updateFreehandSettings(currentFreehandSettings.copy(minPredictionVelocity = speed))
        }
    }
    
    fun setFreehandMaxPredictionVelocity(speed: Float) {
        if (currentFreehandSettings.maxPredictionVelocity != speed) {
             updateFreehandSettings(currentFreehandSettings.copy(maxPredictionVelocity = speed))
        }
    }



    
    // PRESETS
    private fun loadPresets(): List<Float> {
        return listOf(
            prefs.getFloat("size_preset_0", 1f),
            prefs.getFloat("size_preset_1", 3f),
            prefs.getFloat("size_preset_2", 6f)
        )
    }
    var brushSizePresets = mutableStateListOf<Float>().apply { addAll(loadPresets()) }
        private set

    fun updateBrushSizePreset(index: Int, size: Float) {
        if (index in 0 until brushSizePresets.size) {
            brushSizePresets[index] = size
            prefs.edit().putFloat("size_preset_$index", size).apply()
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
    
    fun performAction(command: UndoCommand) {
        command.execute()
        undoStack.push(command)
        if (undoStack.size > 100) undoStack.removeLast()
        redoStack.clear()
        updateUndoRedoSupport()
        notifyLayersChanged()
    }

    private fun updateUndoRedoSupport() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
    
    fun undo() {
        if (undoStack.isEmpty()) return
        val command = undoStack.pop()
        command.undo()
        redoStack.push(command)
        updateUndoRedoSupport()
        notifyLayersChanged()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val command = redoStack.pop()
        command.execute()
        undoStack.push(command)
        updateUndoRedoSupport()
        notifyLayersChanged()
    }

    private fun notifyLayersChanged() {
        // StateFlow only emits if the value (reference) changes.
        // We create a shallow copy of the list to trigger the emission.
        
        layerUpdateTrigger++
    }

    var layerUpdateTrigger by mutableStateOf(0)

    private inner class SnapshotCommand(
        private val label: String,
        private val before: List<Layer>,
        private val after: List<Layer>,
        private val activeIndex: Int
    ) : UndoCommand {
        override fun execute() { restoreSnapshot(after, activeIndex) }
        override fun undo() { restoreSnapshot(before, activeIndex) }
        override fun getLabel(): String = label
    }

    private fun performSnapshotAction(label: String, action: () -> Unit) {
        val before = createLayersSnapshot()
        val activeIndexBefore = activeLayerIndex
        action()
        val after = createLayersSnapshot()
        performAction(SnapshotCommand(label, before, after, activeIndexBefore))
    }

    private fun createLayersSnapshot(): List<Layer> {
        return layers.map { layer -> layer.copy(elements = layer.elements.map { it.copyElement() }.toMutableStateList()) }
    }
    
    private fun restoreSnapshot(state: List<Layer>, restoredActiveIndex: Int) {
        layerManager.internalUpdateLayers(
            newList = state.map { savedLayer ->
                savedLayer.copy(elements = savedLayer.elements.map { it.copyElement() }.toMutableStateList())
            },
            activeIndex = restoredActiveIndex
        )
        layerUpdateTrigger++
    }
    
    // --- ACTIONS ---

    
    // --- CAMERA ---
    val cameraMatrixValues = FloatArray(9).apply { Matrix().getValues(this) }
    private val _cameraMatrix = MutableStateFlow(Matrix())
    val cameraMatrix = _cameraMatrix.asStateFlow()

    private val homeCameraMatrixValues = FloatArray(9).apply {
        val saved = prefs.getString("home_camera_matrix_v3", null)
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
        prefs.edit().putString("home_camera_matrix_v3", homeCameraMatrixValues.joinToString(",")).apply()
    }
    fun saveCameraState(matrix: Matrix) { 
        matrix.getValues(cameraMatrixValues)
        cameraUpdateTrigger++
        _cameraMatrix.value = Matrix(matrix)
    }
    fun saveDimensions(w: Float, h: Float) { lastViewportWidth = w; lastViewportHeight = h }
    fun zoomIn() {
        if (lastViewportWidth <= 0.0f || lastViewportHeight <= 0.0f) return
        val m = Matrix(_cameraMatrix.value)
        m.postScale(1.2f, 1.2f, lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)
        saveCameraState(m)
    }

    fun zoomOut() {
        if (lastViewportWidth <= 0.0f || lastViewportHeight <= 0.0f) return
        val m = Matrix(_cameraMatrix.value)
        m.postScale(0.8f, 0.8f, lastViewportWidth / 2.0f, lastViewportHeight / 2.0f)
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
                 
                 val cx = w.toFloat() / 2.0f
                 val cy = h.toFloat() / 2.0f
                 
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
        
        // New Matrix: Scale 1.0, preserve center
        m.setScale(1f, 1f)
        m.postTranslate(cx - worldX, cy - worldY)
        
        m.getValues(cameraMatrixValues)
        cameraUpdateTrigger++
    }

    fun addImportedDxfData(data: DxfImportData, scaleToFit: Boolean, defaultStrokeWidth: Float, fillClosedShapes: Boolean = false) {
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
                     matrix.postTranslate(canvasW/2f, canvasH/2f)
                }
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

        editingContainerMatrix?.let { containerM ->
            val inverse = Matrix()
            containerM.invert(inverse)
            stroke.transform(inverse)
        }
        
        performAction(AddStrokeCommand(targetLayer, stroke))
    }
    

    fun addHybridStroke(stroke: VectorStroke, fill: FillData?) {
        val targetLayer = layers[activeLayerIndex]
        if (targetLayer.isLocked) return

        val inverse = Matrix()
        editingContainerMatrix?.let { containerM ->
            containerM.invert(inverse)
        }
        
        // Transform
        fill?.let { if (editingContainerMatrix != null) it.transform(inverse) }
        if (editingContainerMatrix != null) stroke.transform(inverse)

        performAction(AddHybridStrokeCommand(targetLayer, stroke, fill))
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
    
    fun insertImage(context: Context, uri: android.net.Uri) {
         launchIO {
             com.sketcher.sketchercompanionv1.utils.BitmapUtils.loadScaledBitmap(context, uri)?.let { bitmap ->
                 withContext(Dispatchers.Main) {
                     insertImageWithBitmap(bitmap, "img_${java.util.UUID.randomUUID()}.png")
                 }
             }
         }
    }
    
    fun insertImageWithBitmap(bitmap: android.graphics.Bitmap, filename: String) {
        if (activeLayerIndex !in layers.indices) return
        performSnapshotAction("Insertar Imagen") {
            val currentLayers = layers.toMutableList()
            val layer = currentLayers[activeLayerIndex]
            val matrix = Matrix()
            if (lastViewportWidth > 0.0f && lastViewportHeight > 0.0f) {
                 matrix.postTranslate(lastViewportWidth / 2.0f - bitmap.width / 2.0f, lastViewportHeight / 2.0f - bitmap.height / 2.0f)
            }
            val element = ImageElement(
                id = java.util.UUID.randomUUID().toString(),
                bitmap = bitmap, 
                imageFileName = filename,
                matrix = matrix
            )
            layer.elements.add(element)
            currentLayers[activeLayerIndex] = layer.copy()
            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            selectionManager.clearSelection()
            selectionManager.selectedElements.add(element)
        }
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
    fun saveProjectToZip(context: Context, uri: android.net.Uri) {
        launchIO {
            try {
                // Snapshot state on Main thread or copy it?
                // StateFlow access is thread-safe for reading value, but content might change.
                // ideally we capture state on Main, then save on IO.
                val currentLayersSnapshot = layers.map { it.copy(elements = it.elements.toMutableStateList()) } // Shallow-ish copy
                val currentComponentLibrary = componentLibrary.toMap()
                val savedProjectId = projectId
                val savedBgColor = backgroundColor
                val savedGridConfig = gridConfig
                val savedScaleConfig = scaleConfig
                val savedUnit = currentUnit
                val savedViewportW = lastViewportWidth
                val savedViewportH = lastViewportHeight
                val savedCameraMatrix = cameraMatrixValues.toList()

                val projectData = ProjectData(
                    id = savedProjectId,
                    layers = currentLayersSnapshot.map { it.toLayerJson() },
                    backgroundConfig = BackgroundConfig(color = savedBgColor, gridConfig = savedGridConfig),
                    paletteColors = emptyList(),
                    toolConfigs = emptyMap(),
                    canvasMetadata = CanvasMetadata(
                        width = savedViewportW, height = savedViewportH, 
                        cameraMatrix = savedCameraMatrix,
                        scaleConfig = savedScaleConfig.copy(unitName = savedUnit.symbol)
                    ),
                    componentLibrary = currentComponentLibrary.mapValues { it.value.toComponentDefinitionJson() }
                )
                com.sketcher.sketchercompanionv1.utils.ZipStorageManager.saveProject(context, projectData, currentLayersSnapshot, uri)
                withContext(Dispatchers.Main) {
                    currentFileUri = uri
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun loadProjectFromZip(context: Context, uri: android.net.Uri) {
        launchIO {
            try {
                val (projectData, bitmapMap, svgMap) = com.sketcher.sketchercompanionv1.utils.ZipStorageManager.loadProject(context, uri)
                withContext(Dispatchers.Main) {
                    restoreProjectState(projectData, bitmapMap, svgMap)
                    currentFileUri = uri
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }
    
    private fun restoreProjectState(data: ProjectData, bitmaps: Map<String, android.graphics.Bitmap>, svgs: Map<String, String>) {
        val newLayers = mutableListOf<Layer>()
        undoStack.clear()
        redoStack.clear()
        
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
        val loadedGrid = data.backgroundConfig.gridConfig
        gridConfig = loadedGrid ?: GridConfig()
        
        // Camera
        if (data.canvasMetadata.cameraMatrix.size == 9) {
             for(i in 0..8) cameraMatrixValues[i] = data.canvasMetadata.cameraMatrix[i]
        }
        cameraUpdateTrigger++
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
        launchIO {
            try {
                // Determine bounds mode based on canvas size configuration
                val boundsMode = if (canvasSizeConfig != null) {
                    com.sketcher.sketchercompanionv1.utils.PdfExporter.BoundsMode.CANVAS_SIZE
                } else {
                    pdfExportBoundsMode
                }

                val config = com.sketcher.sketchercompanionv1.utils.PdfExporter.PdfExportConfig(
                    boundsMode = boundsMode,
                    includeBackground = true,
                    dpi = 300
                )

                // Use canvas size config dimensions if available, otherwise use reasonable defaults
                val width = canvasSizeConfig?.widthInPixels ?: 2480f // A4 at 300 DPI
                val height = canvasSizeConfig?.heightInPixels ?: 3508f // A4 at 300 DPI

                // Snapshot for thread safety
                val currentLayersSnapshot = layers.toList()
                val currentComponentLibrary = componentLibrary.toMap()
                
                val projectData = ProjectData(
                    id = projectId,
                    layers = currentLayersSnapshot.map { it.toLayerJson() },
                    backgroundConfig = BackgroundConfig(color = backgroundColor, gridConfig = gridConfig),
                    paletteColors = emptyList(),
                    toolConfigs = emptyMap(),
                    canvasMetadata = CanvasMetadata(
                        width = width,
                        height = height,
                        cameraMatrix = emptyList(), // Camera matrix handled by PdfExporter
                        scaleConfig = scaleConfig
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
                    canvasSizeConfig = canvasSizeConfig
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }


    
    fun erase(x: Float, y: Float, diameterPx: Float): Boolean {
        var changed = false
        // Command-based Eraser Implementation
        val hits = mutableListOf<Pair<Layer, LayerElement>>()

        // Convert diameterPx to World Radius
        val radiusWorld = com.sketcher.sketchercompanionv1.utils.UnitUtils.pixelsToProjectUnits(
            diameterPx, currentUnit, scaleConfig.basePixelsPerMillimeter
        ) / 2f
        
        // 1. Identify hits (Read Phase)
        val currentLayers = layers
        val layersToCheck = if (selectionScope == SelectionScope.ALL_LAYERS) currentLayers else 
            (if (activeLayerIndex in currentLayers.indices) listOf(currentLayers[activeLayerIndex]) else emptyList())

        for (layer in layersToCheck) {
            // Check for intersection
            for (element in layer.elements) {
                val bounds = element.getBoundingBox(componentLibrary)
                 // Simple rect intersection for eraser
                if (RectF.intersects(bounds, RectF(x - radiusWorld, y - radiusWorld, x + radiusWorld, y + radiusWorld))) {
                    hits.add(layer to element)
                }
            }
        }

        // 2. Execute Commands (Write Phase)
        if (hits.isNotEmpty()) {
            for ((layer, element) in hits) {
                // Use EraseCommand (Renamed from RemoveStrokeCommand)
                performAction(EraseCommand(layer, element))
                changed = true
            }
        }
        return changed
    }


    
    fun clear() {
        performSnapshotAction("Limpiar Lienzo") {
            layerManager.internalUpdateLayers(mutableListOf<Layer>(), 0)
            addNewLayerInternal(true) // Helper to add layer without recursive command
            selectionManager.clearSelection()
        }
    }

    private fun addNewLayerInternal(toTop: Boolean) { 
        val currentLayers = layers.toMutableList()
        val l = Layer("l_${System.currentTimeMillis()}", "Capa ${currentLayers.size+1}", mutableStateListOf())
        val newIndex = if(toTop) { currentLayers.add(l); currentLayers.lastIndex } else { currentLayers.add(0, l); 0 }
        layerManager.internalUpdateLayers(currentLayers, newIndex)
    }
    

    fun saveTemplate(context: Context, name: String) {
         launchIO {
             // Construct ProjectData from current state for saving
             val projectData = com.sketcher.sketchercompanionv1.dto.ProjectData(
                 id = projectId,
                 layers = layers.map { it.toLayerJson() },
                 backgroundConfig = com.sketcher.sketchercompanionv1.dto.BackgroundConfig(backgroundColor, gridConfig),
                 paletteColors = emptyList(),
                 toolConfigs = emptyMap(), // Simplify for now or map toolConfigs
                 canvasMetadata = com.sketcher.sketchercompanionv1.dto.CanvasMetadata(
                     width = 2000f, // Use actualviewport if possible
                     height = 2000f,
                     cameraMatrix = emptyList(), // Simplify
                     scaleConfig = scaleConfig
                 )
             )
             
             com.sketcher.sketchercompanionv1.utils.TemplateManager.saveAsTemplate(
                 context, 
                 projectData,
                 layers,
                 name
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
                                opacity = lJson.opacity
                            )
                            newLayers.add(l)
                        }
                        layerManager.internalUpdateLayers(newLayers, 0)
                        if (layers.isEmpty()) layerManager.addNewLayer(true)
                    }
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
                     val targetCx = targetWidth / 2f
                     val targetCy = targetHeight / 2f
                     
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
                 val targetCx = targetWidth / 2f
                 val targetCy = targetHeight / 2f
                 
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
         // Run export in IO
         viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
             try {
                 context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                     // Support exporting all layers with structure
                     // If selection only is needed later, we would need to filter layers
                     com.sketcher.sketchercompanionv1.exporters.DxfExporter.export(layers, outputStream)
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
}

/**
 * Command to erase a [LayerElement] from a [Layer].
 * Supports Undo by restoring it to its original position.
 */
class EraseCommand(
    private val targetLayer: Layer,
    private val elementToRemove: LayerElement
) : UndoCommand {

    private var originalIndex: Int = -1

    override fun execute() {
        val index = targetLayer.elements.indexOf(elementToRemove)
        if (index != -1) {
            originalIndex = index
            targetLayer.elements.removeAt(index)
        }
    }

    override fun undo() {
        if (originalIndex != -1) {
            if (originalIndex <= targetLayer.elements.size) {
                targetLayer.elements.add(originalIndex, elementToRemove)
            } else {
                targetLayer.elements.add(elementToRemove)
            }
        }
    }

    override fun getLabel(): String = "Borrador"
}


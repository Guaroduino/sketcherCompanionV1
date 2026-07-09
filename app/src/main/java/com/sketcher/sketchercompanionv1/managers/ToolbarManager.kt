package com.sketcher.sketchercompanionv1.managers

import android.content.SharedPreferences
import com.sketcher.sketchercompanionv1.data.ToolbarRepository
import com.sketcher.sketchercompanionv1.ui.components.ToolPayload
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline

class ToolbarManager(
    private val toolbarRepository: ToolbarRepository,
    private val prefs: SharedPreferences,
    private val getDefaultStrokeColor: () -> Int,
    private val getDefaultFillColor: () -> Int,
    private val activateTool: (ToolPayload, String) -> Unit,
    private val getActionForTool: (String) -> () -> Unit
) {
    private val _toolbarState = MutableStateFlow<Map<ToolLocation, List<StudioTool>>>(emptyMap())
    val toolbarState: StateFlow<Map<ToolLocation, List<StudioTool>>> = _toolbarState.asStateFlow()

    private val _uiPresetsNames = MutableStateFlow<List<String>>(emptyList())
    val uiPresetsNames: StateFlow<List<String>> = _uiPresetsNames.asStateFlow()

    private val _activeUiPresetName = MutableStateFlow<String>("Default")
    val activeUiPresetName: StateFlow<String> = _activeUiPresetName.asStateFlow()

    private val _contextualToolbar = MutableStateFlow<List<StudioTool>>(emptyList())
    val contextualToolbar: StateFlow<List<StudioTool>> = _contextualToolbar.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _assignedTools = MutableStateFlow<Map<String, ToolPayload>>(emptyMap())
    val assignedTools: StateFlow<Map<String, ToolPayload>> = _assignedTools.asStateFlow()

    private val _assignedToolColors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val assignedToolColors: StateFlow<Map<String, Int>> = _assignedToolColors.asStateFlow()

    private val _assignedToolStabilization = MutableStateFlow<Map<String, Float>>(emptyMap())
    val assignedToolStabilization = _assignedToolStabilization.asStateFlow()

    private val _assignedToolOpacity = MutableStateFlow<Map<String, Float>>(emptyMap())
    val assignedToolOpacity = _assignedToolOpacity.asStateFlow()

    var lastActiveColorToolId: String? = null
    var lastActiveStabilizationToolId: String? = null

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun updateLastActiveToolColor(color: Int) {
        lastActiveColorToolId?.let { id ->
            _assignedToolColors.value = _assignedToolColors.value + (id to color)
            saveLayout()
        }
    }

    fun updateLastActiveToolStabilization(stabilization: Float) {
        lastActiveStabilizationToolId?.let { id ->
            _assignedToolStabilization.value = _assignedToolStabilization.value + (id to stabilization)
            saveLayout()
        }
    }

    fun updateLastActiveToolOpacity(opacity: Float) {
        lastActiveStabilizationToolId?.let { id ->
            _assignedToolOpacity.value = _assignedToolOpacity.value + (id to opacity)
            saveLayout()
        }
    }

    fun initLayout() {
        _uiPresetsNames.value = toolbarRepository.getUiPresetsNames()
        _activeUiPresetName.value = toolbarRepository.getActiveUiPresetName()
        
        if (_uiPresetsNames.value.isEmpty()) {
            _uiPresetsNames.value = listOf("Default")
            val loaded = toolbarRepository.loadLayout()
            if (loaded != null) {
                toolbarRepository.saveUiPreset("Default", loaded)
            } else {
                initToolbarState()
                val currentState = com.sketcher.sketchercompanionv1.data.ToolbarStateResult(
                    tools = _toolbarState.value,
                    assignedMap = _assignedTools.value,
                    toolColors = _assignedToolColors.value,
                    contextualTools = _contextualToolbar.value,
                    toolStabilization = _assignedToolStabilization.value,
                    toolOpacity = _assignedToolOpacity.value
                )
                toolbarRepository.saveUiPreset("Default", currentState)
            }
            toolbarRepository.setActiveUiPresetName("Default")
            _activeUiPresetName.value = "Default"
        }

        val loaded = toolbarRepository.loadLayout()
        val layoutResetV11 = prefs.getInt("layout_reset_v11", 0)
        
        if (loaded != null && layoutResetV11 >= 1) {
            _assignedTools.value = loaded.assignedMap
            _assignedToolColors.value = loaded.toolColors
            _assignedToolStabilization.value = loaded.toolStabilization
            _assignedToolOpacity.value = loaded.toolOpacity ?: loaded.toolStabilization.mapValues { 1f }
            
            val toolsWithActions = loaded.tools.mapValues { (_, list) ->
                list.map { tool -> bindToolActions(migrateTool(tool)) }
            }
            _toolbarState.value = toolsWithActions
            _contextualToolbar.value = loaded.contextualTools.map { bindToolActions(migrateTool(it)) }
        } else {
            initToolbarState()
            saveLayout()
            val currentState = com.sketcher.sketchercompanionv1.data.ToolbarStateResult(
                tools = _toolbarState.value,
                assignedMap = _assignedTools.value,
                toolColors = _assignedToolColors.value,
                contextualTools = _contextualToolbar.value,
                toolStabilization = _assignedToolStabilization.value,
                toolOpacity = _assignedToolOpacity.value
            )
            toolbarRepository.saveUiPreset("Default", currentState)
            prefs.edit().putInt("layout_reset_v11", 1).apply()
        }
    }

    private fun migrateTool(tool: StudioTool): StudioTool {
        val targetTool = if (tool.registryId == "stroke_type" || tool.registryId == "stroke_freehand") {
            val savedTypeStr = prefs.getString("current_stroke_type", "FREEHAND") ?: "FREEHAND"
            val targetId = when (savedTypeStr) {
                "FREEHAND" -> "stroke_freehand"
                "LINE" -> "stroke_line"
                "POLYLINE" -> "stroke_polyline"
                "CIRCLE" -> "stroke_circle"
                "ARC" -> "stroke_arc"
                "ELLIPSE" -> "stroke_ellipse"
                "SPLINE" -> "stroke_spline"
                "BEZIER" -> "stroke_bezier"
                else -> "stroke_freehand"
            }
            ToolRegistry.getToolById(targetId) ?: ToolRegistry.getToolById("stroke_freehand") ?: tool
        } else {
            tool
        }
        
        val migratedSubTools = targetTool.subTools.map { migrateTool(it) }
        return targetTool.copy(subTools = migratedSubTools)
    }

    private fun getPayloadFromToolId(id: String): ToolPayload? = when(id) {
        "pencil" -> ToolPayload.PENCIL
        "pen" -> ToolPayload.PEN
        "eraser" -> ToolPayload.ERASER
        "point_eraser" -> ToolPayload.POINT_ERASER
        "cut_eraser" -> ToolPayload.CUT_ERASER
        "stroke_color" -> ToolPayload.STROKE_COLOR
        "fill_color" -> ToolPayload.FILL_COLOR
        "paint" -> ToolPayload.PAINT
        "watercolor" -> ToolPayload.WATERCOLOR
        "pluma" -> ToolPayload.PLUMA
        "pencil_cumulative" -> ToolPayload.PENCIL_CUMULATIVE
        "quick_stabilization" -> ToolPayload.STABILIZE
        "text" -> ToolPayload.TEXT
        else -> null
    }

    fun assignTool(toolId: String, payload: ToolPayload) {
        _assignedTools.value = _assignedTools.value + (toolId to payload)
        
        if (payload == ToolPayload.STROKE_COLOR) {
            _assignedToolColors.value = _assignedToolColors.value + (toolId to getDefaultStrokeColor())
        } else if (payload == ToolPayload.FILL_COLOR) {
            _assignedToolColors.value = _assignedToolColors.value + (toolId to getDefaultFillColor())
        } else if (payload == ToolPayload.STABILIZE) {
            _assignedToolStabilization.value = _assignedToolStabilization.value + (toolId to 0f)
            _assignedToolOpacity.value = _assignedToolOpacity.value + (toolId to 1f)
        }

        val updatedToolbar = _toolbarState.value.mapValues { (_, list) ->
            list.map { tool ->
                if (tool.id == toolId) bindToolActions(tool)
                else {
                    val updatedSubs = tool.subTools.map { sub ->
                        if (sub.id == toolId) bindToolActions(sub) else sub
                    }
                    if (updatedSubs != tool.subTools) tool.copy(subTools = updatedSubs) else tool
                }
            }
        }
        _toolbarState.value = updatedToolbar

        val updatedContextual = _contextualToolbar.value.map { tool ->
            if (tool.id == toolId) bindToolActions(tool)
            else {
                val updatedSubs = tool.subTools.map { sub ->
                    if (sub.id == toolId) bindToolActions(sub) else sub
                }
                if (updatedSubs != tool.subTools) tool.copy(subTools = updatedSubs) else tool
            }
        }
        _contextualToolbar.value = updatedContextual

        activateTool(payload, toolId)
        saveLayout()
    }

    private fun bindToolActions(tool: StudioTool): StudioTool {
        val boundSubTools = tool.subTools.map { bindToolActions(it) }
        val uniqueId = tool.id
        val payload = _assignedTools.value[uniqueId]
        
        val iconToUse = payload?.icon ?: tool.icon
        val iconResToUse = payload?.iconResId ?: tool.iconResId
        val descToUse = payload?.label ?: tool.contentDescription
        val isPlaceholderToUse = if (payload != null) false else tool.isPlaceholder

        return tool.copy(
            icon = iconToUse,
            iconResId = iconResToUse,
            contentDescription = descToUse,
            isPlaceholder = isPlaceholderToUse,
            subTools = boundSubTools,
            onClick = {
                if (payload != null) {
                    activateTool(payload, uniqueId)
                } else {
                    getActionForTool(tool.registryId).invoke()
                }
            }
        )
    }

    fun addTool(location: ToolLocation, tool: StudioTool) {
        val uniqueId = UUID.randomUUID().toString()
        val initialPayload = getPayloadFromToolId(tool.id)
        
        if (initialPayload != null) {
            _assignedTools.value = _assignedTools.value + (uniqueId to initialPayload)
        }

        val uniqueTool = tool.copy(
            id = uniqueId,
            registryId = tool.id
        )
        val boundTool = bindToolActions(uniqueTool)
        
        if (location == ToolLocation.ContextBar) {
            val list = _contextualToolbar.value.toMutableList()
            list.add(boundTool)
            _contextualToolbar.value = list
        } else {
            val currentMap = _toolbarState.value.toMutableMap()
            val list = currentMap[location]?.toMutableList() ?: mutableListOf()
            list.add(boundTool)
            currentMap[location] = list
            _toolbarState.value = currentMap
        }
        saveLayout()
    }

    fun removeTool(location: ToolLocation, index: Int) {
        if (location == ToolLocation.ContextBar) {
            val list = _contextualToolbar.value.toMutableList()
            if (index in list.indices) {
                val tool = list.removeAt(index)
                _assignedTools.value = _assignedTools.value - tool.id
                _contextualToolbar.value = list
            }
        } else {
            val currentMap = _toolbarState.value.toMutableMap()
            val list = currentMap[location]?.toMutableList() ?: return
            if (index in list.indices) {
                val tool = list.removeAt(index)
                var updatedAssigned = _assignedTools.value - tool.id
                tool.subTools.forEach { sub ->
                    updatedAssigned = updatedAssigned - sub.id
                }
                _assignedTools.value = updatedAssigned
                currentMap[location] = list
                _toolbarState.value = currentMap
            }
        }
        saveLayout()
    }

    fun replaceTool(location: ToolLocation, index: Int, newTool: StudioTool) {
        if (location == ToolLocation.ContextBar) {
            val list = _contextualToolbar.value.toMutableList()
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
                    registryId = newTool.id
                )
                list[index] = bindToolActions(uniqueTool)
                _contextualToolbar.value = list
            }
        } else {
            val currentMap = _toolbarState.value.toMutableMap()
            val list = currentMap[location]?.toMutableList() ?: return
            if (index in list.indices) {
                val oldTool = list[index]
                val existingSubTools = oldTool.subTools
                _assignedTools.value = _assignedTools.value - oldTool.id
                
                val uniqueId = UUID.randomUUID().toString()
                val initialPayload = getPayloadFromToolId(newTool.id)
                if (initialPayload != null) {
                    _assignedTools.value = _assignedTools.value + (uniqueId to initialPayload)
                }
                
                val uniqueTool = newTool.copy(
                    id = uniqueId,
                    registryId = newTool.id,
                    subTools = existingSubTools
                )
                list[index] = bindToolActions(uniqueTool)
                currentMap[location] = list
                _toolbarState.value = currentMap
            }
        }
        saveLayout()
    }

    fun addSubTool(location: ToolLocation, parentIndex: Int, tool: StudioTool) {
        if (location == ToolLocation.ContextBar) {
            val list = _contextualToolbar.value.toMutableList()
            if (parentIndex in list.indices) {
                val parentTool = list[parentIndex]
                val subList = parentTool.subTools.toMutableList()
                val uniqueId = UUID.randomUUID().toString()
                val initialPayload = getPayloadFromToolId(tool.id)
                if (initialPayload != null) {
                    _assignedTools.value = _assignedTools.value + (uniqueId to initialPayload)
                }
                val uniqueTool = tool.copy(id = uniqueId, registryId = tool.id)
                subList.add(uniqueTool)
                val updatedParentTool = parentTool.copy(subTools = subList)
                list[parentIndex] = bindToolActions(updatedParentTool)
                _contextualToolbar.value = list
                saveLayout()
            }
            return
        }
        val currentMap = _toolbarState.value.toMutableMap()
        val list = currentMap[location]?.toMutableList() ?: return
        if (parentIndex in list.indices) {
            val parentTool = list[parentIndex]
            val subList = parentTool.subTools.toMutableList()
            val uniqueId = UUID.randomUUID().toString()
            val initialPayload = getPayloadFromToolId(tool.id)
            if (initialPayload != null) {
                _assignedTools.value = _assignedTools.value + (uniqueId to initialPayload)
            }
            val uniqueTool = tool.copy(
                id = uniqueId,
                registryId = tool.id
            )
            subList.add(uniqueTool)
            val updatedParentTool = parentTool.copy(subTools = subList)
            list[parentIndex] = bindToolActions(updatedParentTool)
            currentMap[location] = list
            _toolbarState.value = currentMap
            saveLayout()
        }
    }

    fun removeSubTool(location: ToolLocation, parentIndex: Int, subToolIndex: Int) {
        if (location == ToolLocation.ContextBar) {
            val list = _contextualToolbar.value.toMutableList()
            if (parentIndex in list.indices) {
                val parentTool = list[parentIndex]
                val subList = parentTool.subTools.toMutableList()
                if (subToolIndex in subList.indices) {
                    val removedTool = subList.removeAt(subToolIndex)
                    _assignedTools.value = _assignedTools.value - removedTool.id
                    val updatedParentTool = parentTool.copy(subTools = subList)
                    list[parentIndex] = bindToolActions(updatedParentTool)
                    _contextualToolbar.value = list
                    saveLayout()
                }
            }
            return
        }
        val currentMap = _toolbarState.value.toMutableMap()
        val list = currentMap[location]?.toMutableList() ?: return
        if (parentIndex in list.indices) {
            val parentTool = list[parentIndex]
            val subList = parentTool.subTools.toMutableList()
            if (subToolIndex in subList.indices) {
                val removedTool = subList.removeAt(subToolIndex)
                _assignedTools.value = _assignedTools.value - removedTool.id
                val updatedParentTool = parentTool.copy(subTools = subList)
                list[parentIndex] = bindToolActions(updatedParentTool)
                currentMap[location] = list
                _toolbarState.value = currentMap
                saveLayout()
            }
        }
    }

    fun swapSubToolToMain(location: ToolLocation, parentIndex: Int, subToolIndex: Int) {
        if (location == ToolLocation.ContextBar) {
            val list = _contextualToolbar.value.toMutableList()
            if (parentIndex in list.indices) {
                val parentTool = list[parentIndex]
                val subList = if (parentTool.subTools.isNotEmpty()) parentTool.subTools.toMutableList()
                              else ToolRegistry.getSubToolsFor(parentTool.registryId).toMutableList()
                if (subToolIndex in subList.indices) {
                    val clickedSubTool = subList[subToolIndex]
                    subList[subToolIndex] = parentTool.copy(subTools = emptyList())
                    val newMainTool = clickedSubTool.copy(subTools = subList)
                    list[parentIndex] = bindToolActions(newMainTool)
                    _contextualToolbar.value = list
                    newMainTool.onClick()
                    saveLayout()
                }
            }
            return
        }
        val currentMap = _toolbarState.value.toMutableMap()
        val list = currentMap[location]?.toMutableList() ?: return
        if (parentIndex in list.indices) {
            val parentTool = list[parentIndex]
            val subList = if (parentTool.subTools.isNotEmpty()) parentTool.subTools.toMutableList()
                          else ToolRegistry.getSubToolsFor(parentTool.registryId).toMutableList()
            if (subToolIndex in subList.indices) {
                val clickedSubTool = subList[subToolIndex]
                subList[subToolIndex] = parentTool.copy(subTools = emptyList())
                val newMainTool = clickedSubTool.copy(subTools = subList)
                list[parentIndex] = bindToolActions(newMainTool)
                currentMap[location] = list
                _toolbarState.value = currentMap
                newMainTool.onClick()
                saveLayout()
            }
        }
    }

    fun moveSubTool(location: ToolLocation, parentIndex: Int, fromIndex: Int, toIndex: Int) {
        if (location == ToolLocation.ContextBar) {
            val list = _contextualToolbar.value.toMutableList()
            if (parentIndex in list.indices) {
                val parentTool = list[parentIndex]
                val fullList = (listOf(parentTool) + parentTool.subTools).toMutableList()
                if (fromIndex in fullList.indices && toIndex in fullList.indices) {
                    val temp = fullList[fromIndex]
                    fullList[fromIndex] = fullList[toIndex]
                    fullList[toIndex] = temp
                    val clearedSubList = fullList.subList(1, fullList.size).map { it.copy(subTools = emptyList()) }
                    val updatedParentTool = fullList[0].copy(subTools = clearedSubList)
                    list[parentIndex] = bindToolActions(updatedParentTool)
                    _contextualToolbar.value = list
                    saveLayout()
                }
            }
            return
        }
        val currentMap = _toolbarState.value.toMutableMap()
        val list = currentMap[location]?.toMutableList() ?: return
        if (parentIndex in list.indices) {
            val parentTool = list[parentIndex]
            val fullList = (listOf(parentTool) + parentTool.subTools).toMutableList()
            if (fromIndex in fullList.indices && toIndex in fullList.indices) {
                val temp = fullList[fromIndex]
                fullList[fromIndex] = fullList[toIndex]
                fullList[toIndex] = temp
                val clearedSubList = fullList.subList(1, fullList.size).map { it.copy(subTools = emptyList()) }
                val updatedParentTool = fullList[0].copy(subTools = clearedSubList)
                list[parentIndex] = bindToolActions(updatedParentTool)
                currentMap[location] = list
                _toolbarState.value = currentMap
                saveLayout()
            }
        }
    }

    fun insertPlaceholderSlot(location: ToolLocation, targetIndex: Int, relativePosition: Int) {
        if (location == ToolLocation.ContextBar) {
            val list = _contextualToolbar.value.toMutableList()
            val insertIndex = if (targetIndex in list.indices) {
                if (relativePosition < 0) targetIndex else targetIndex + 1
            } else {
                list.size
            }
            val placeholderTool = StudioTool(
                id = UUID.randomUUID().toString(),
                icon = androidx.compose.material.icons.Icons.Default.AddCircleOutline,
                contentDescription = "New Slot",
                isPlaceholder = true,
                registryId = "placeholder"
            )
            val boundTool = bindToolActions(placeholderTool)
            list.add(insertIndex, boundTool)
            _contextualToolbar.value = list
            saveLayout()
            return
        }
        val currentMap = _toolbarState.value.toMutableMap()
        val list = currentMap[location]?.toMutableList() ?: return
        if (targetIndex in list.indices) {
            val insertIndex = if (relativePosition < 0) targetIndex else targetIndex + 1
            val placeholderTool = StudioTool(
                id = UUID.randomUUID().toString(),
                icon = androidx.compose.material.icons.Icons.Default.AddCircleOutline,
                contentDescription = "New Slot",
                isPlaceholder = true,
                registryId = "placeholder"
            )
            val boundTool = bindToolActions(placeholderTool)
            list.add(insertIndex, boundTool)
            currentMap[location] = list
            _toolbarState.value = currentMap
            saveLayout()
        }
    }

    private fun initToolbarState() {
        if (_assignedTools.value.isEmpty()) {
            _assignedTools.value = mapOf(
                "pencil" to ToolPayload.PENCIL,
                "pen" to ToolPayload.PEN,
                "paint" to ToolPayload.PAINT,
                "watercolor" to ToolPayload.WATERCOLOR,
                "pluma" to ToolPayload.PLUMA,
                "eraser" to ToolPayload.ERASER,
                "stroke_color" to ToolPayload.STROKE_COLOR,
                "fill_color" to ToolPayload.FILL_COLOR
            )
        }
        if (_assignedToolColors.value.isEmpty()) {
            _assignedToolColors.value = mapOf(
                "stroke_color" to getDefaultStrokeColor(),
                "fill_color" to getDefaultFillColor()
            )
        }
        
        _toolbarState.value = mapOf(
            ToolLocation.LeftBar to listOf(),
            ToolLocation.RightBar to listOfNotNull(
                ToolRegistry.getToolById(StudioTool.SIZE_OPACITY_TOOL_ID),
                ToolRegistry.getToolById("brush_workshop"),
                ToolRegistry.getToolById("pencil")?.copy(
                    subTools = listOfNotNull(
                        ToolRegistry.getToolById("pen"),
                        ToolRegistry.getToolById("paint"),
                        ToolRegistry.getToolById("watercolor"),
                        ToolRegistry.getToolById("pluma")
                    )
                ),
                ToolRegistry.getToolById("stroke_freehand"),
                ToolRegistry.getToolById(StudioTool.STABILIZATION_TOOL_ID),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("stroke_color"),
                ToolRegistry.getToolById("fill_color"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("eraser"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("text")
            ),
            ToolLocation.TopBar to listOfNotNull(
                ToolRegistry.getToolById("zoom_fit"),
                ToolRegistry.getToolById("zoom_in"),
                ToolRegistry.getToolById("zoom_out"),
                ToolRegistry.getToolById("home_view")
            ),
            ToolLocation.BottomBar to listOfNotNull(
                ToolRegistry.getToolById("tool_selection"),
                ToolRegistry.getToolById("action_paste"),
                ToolRegistry.getToolById("grid_menu")
            ),
            ToolLocation.TopLeftCorner to listOfNotNull(
                ToolRegistry.getToolById("menu")
            ),
            ToolLocation.TopRightCorner to emptyList(),
            ToolLocation.BottomLeftCorner to listOfNotNull(
                ToolRegistry.getToolById("undo")
            ),
            ToolLocation.BottomRightCorner to listOfNotNull(
                ToolRegistry.getToolById("redo")
            )
        ).mapValues { (_, list) ->
            list.map { tool ->
                bindToolActions(tool)
            }
        }

        val contextualWithActions = toolbarRepository.getDefaultContextualTools().map { tool ->
            tool.copy(onClick = {
                getActionForTool(tool.registryId).invoke()
            })
        }
        _contextualToolbar.value = contextualWithActions
    }

    fun reloadToolbarLayout() {
        val loaded = toolbarRepository.loadLayout()
        if (loaded != null) {
            _assignedTools.value = loaded.assignedMap
            _assignedToolColors.value = loaded.toolColors
            _assignedToolStabilization.value = loaded.toolStabilization
            _assignedToolOpacity.value = loaded.toolOpacity ?: loaded.toolStabilization.mapValues { 1f }
            
            val toolsWithActions = loaded.tools.mapValues { (_, list) ->
                list.map { tool -> bindToolActions(tool) }
            }
            _toolbarState.value = toolsWithActions
            
            val contextualWithActions = loaded.contextualTools.map { tool ->
                tool.copy(onClick = {
                    getActionForTool(tool.registryId).invoke()
                })
            }
            _contextualToolbar.value = contextualWithActions
        } else {
            _assignedTools.value = mapOf(
                "pencil" to ToolPayload.PENCIL,
                "pen" to ToolPayload.PEN,
                "paint" to ToolPayload.PAINT,
                "watercolor" to ToolPayload.WATERCOLOR,
                "pluma" to ToolPayload.PLUMA,
                "eraser" to ToolPayload.ERASER,
                "stroke_color" to ToolPayload.STROKE_COLOR,
                "fill_color" to ToolPayload.FILL_COLOR
            )
            _assignedToolColors.value = mapOf(
                "stroke_color" to getDefaultStrokeColor(),
                "fill_color" to getDefaultFillColor()
            )
            initToolbarState()
            
            val defaultContextual = listOfNotNull(
                ToolRegistry.getToolById("context_transform"),
                ToolRegistry.getToolById("context_lock_scale"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("action_copy"),
                ToolRegistry.getToolById("action_cut"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("context_delete"),
                ToolRegistry.getToolById("context_flip_horizontal"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("context_group"),
                ToolRegistry.getToolById("context_edit"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("context_deselect")
            ).map { tool ->
                tool.copy(onClick = {
                    getActionForTool(tool.registryId).invoke()
                })
            }
            _contextualToolbar.value = defaultContextual
        }
    }

    fun updateContextualToolbar(newList: List<StudioTool>) {
        _contextualToolbar.value = newList.map { bindToolActions(it) }
        saveLayout()
    }

    fun saveUiPreset(name: String) {
        val currentState = com.sketcher.sketchercompanionv1.data.ToolbarStateResult(
            tools = _toolbarState.value,
            assignedMap = _assignedTools.value,
            toolColors = _assignedToolColors.value,
            contextualTools = _contextualToolbar.value,
            toolStabilization = _assignedToolStabilization.value,
            toolOpacity = _assignedToolOpacity.value
        )
        toolbarRepository.saveUiPreset(name, currentState)
        _uiPresetsNames.value = toolbarRepository.getUiPresetsNames()
        _activeUiPresetName.value = name
        toolbarRepository.setActiveUiPresetName(name)
        saveLayout()
    }

    fun loadUiPreset(name: String) {
        val loaded = toolbarRepository.loadUiPreset(name)
        if (loaded != null) {
            _assignedTools.value = loaded.assignedMap
            _assignedToolColors.value = loaded.toolColors
            _assignedToolStabilization.value = loaded.toolStabilization
            _assignedToolOpacity.value = loaded.toolOpacity ?: loaded.toolStabilization.mapValues { 1f }
            
            val toolsWithActions = loaded.tools.mapValues { (_, list) ->
                list.map { tool -> bindToolActions(tool) }
            }
            _toolbarState.value = toolsWithActions
            _contextualToolbar.value = loaded.contextualTools.map { bindToolActions(it) }
            
            _activeUiPresetName.value = name
            toolbarRepository.setActiveUiPresetName(name)
            
            saveLayout()
        }
    }

    fun deleteUiPreset(name: String) {
        if (name == "Default") return
        toolbarRepository.deleteUiPreset(name)
        _uiPresetsNames.value = toolbarRepository.getUiPresetsNames()
        
        if (_activeUiPresetName.value == name) {
            loadUiPreset("Default")
        }
    }

    fun onProjectUiPresetLoaded(name: String) {
        val exists = toolbarRepository.getUiPresetsNames().contains(name)
        if (!exists) {
            val currentLayout = toolbarRepository.loadLayout()
            if (currentLayout != null) {
                toolbarRepository.saveUiPreset(name, currentLayout)
            }
        }
        _uiPresetsNames.value = toolbarRepository.getUiPresetsNames()
        _activeUiPresetName.value = name
        toolbarRepository.setActiveUiPresetName(name)
    }

    fun onProjectUiPresetCleared() {
        _activeUiPresetName.value = "Default"
        toolbarRepository.setActiveUiPresetName("Default")
    }

    private fun removeToolFromListRecursive(list: List<StudioTool>, targetId: String): List<StudioTool> {
        return list.mapNotNull { tool ->
            if (tool.registryId == targetId) null
            else tool.copy(subTools = removeToolFromListRecursive(tool.subTools, targetId))
        }
    }

    fun removeToolFromAllLayouts(toolId: String) {
        _toolbarState.value = _toolbarState.value.mapValues { (_, tools) ->
            removeToolFromListRecursive(tools, toolId)
        }
        _contextualToolbar.value = removeToolFromListRecursive(_contextualToolbar.value, toolId)
        
        _assignedTools.value = _assignedTools.value - toolId
        _assignedToolColors.value = _assignedToolColors.value - toolId
        _assignedToolStabilization.value = _assignedToolStabilization.value - toolId
        _assignedToolOpacity.value = _assignedToolOpacity.value - toolId
        
        saveLayout()

        val presets = toolbarRepository.getUiPresetsNames()
        for (presetName in presets) {
            val loaded = toolbarRepository.loadUiPreset(presetName)
            if (loaded != null) {
                val cleanTools = loaded.tools.mapValues { (_, tools) ->
                    removeToolFromListRecursive(tools, toolId)
                }
                val cleanContextual = removeToolFromListRecursive(loaded.contextualTools, toolId)
                val cleanAssigned = loaded.assignedMap - toolId
                val cleanColors = loaded.toolColors - toolId
                val cleanStab = loaded.toolStabilization - toolId
                val cleanOpacity = loaded.toolOpacity - toolId
                
                val cleanResult = com.sketcher.sketchercompanionv1.data.ToolbarStateResult(
                    tools = cleanTools,
                    assignedMap = cleanAssigned,
                    toolColors = cleanColors,
                    contextualTools = cleanContextual,
                    toolStabilization = cleanStab,
                    toolOpacity = cleanOpacity
                )
                toolbarRepository.saveUiPreset(presetName, cleanResult)
            }
        }
    }

    private fun saveLayout() {
        toolbarRepository.saveLayout(
            _toolbarState.value,
            _assignedTools.value,
            _assignedToolColors.value,
            _assignedToolStabilization.value,
            _assignedToolOpacity.value,
            _contextualToolbar.value
        )
        val activeName = _activeUiPresetName.value
        if (activeName.isNotEmpty()) {
            val currentState = com.sketcher.sketchercompanionv1.data.ToolbarStateResult(
                tools = _toolbarState.value,
                assignedMap = _assignedTools.value,
                toolColors = _assignedToolColors.value,
                contextualTools = _contextualToolbar.value,
                toolStabilization = _assignedToolStabilization.value,
                toolOpacity = _assignedToolOpacity.value
            )
            toolbarRepository.saveUiPreset(activeName, currentState)
        }
    }
}

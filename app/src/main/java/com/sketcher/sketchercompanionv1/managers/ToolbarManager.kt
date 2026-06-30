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

    private val _contextualToolbar = MutableStateFlow<List<StudioTool>>(emptyList())
    val contextualToolbar: StateFlow<List<StudioTool>> = _contextualToolbar.asStateFlow()

    private val _isEditMode = MutableStateFlow(false)
    val isEditMode: StateFlow<Boolean> = _isEditMode.asStateFlow()

    private val _assignedTools = MutableStateFlow<Map<String, ToolPayload>>(emptyMap())
    val assignedTools: StateFlow<Map<String, ToolPayload>> = _assignedTools.asStateFlow()

    private val _assignedToolColors = MutableStateFlow<Map<String, Int>>(emptyMap())
    val assignedToolColors: StateFlow<Map<String, Int>> = _assignedToolColors.asStateFlow()

    var lastActiveColorToolId: String? = null

    fun toggleEditMode() {
        _isEditMode.value = !_isEditMode.value
    }

    fun updateLastActiveToolColor(color: Int) {
        lastActiveColorToolId?.let { id ->
            _assignedToolColors.value = _assignedToolColors.value + (id to color)
            saveLayout()
        }
    }

    fun initLayout() {
        val loaded = toolbarRepository.loadLayout()
        val layoutResetV7 = prefs.getInt("layout_reset_v7", 0)
        
        if (loaded != null && layoutResetV7 >= 1) {
            _assignedTools.value = loaded.assignedMap
            _assignedToolColors.value = loaded.toolColors
            
            val toolsWithActions = loaded.tools.mapValues { (_, list) ->
                list.map { tool -> bindToolActions(tool) }
            }
            _toolbarState.value = toolsWithActions
            _contextualToolbar.value = loaded.contextualTools.map { bindToolActions(it) }
        } else {
            initToolbarState()
            saveLayout()
            prefs.edit().putInt("layout_reset_v7", 1).apply()
        }
    }

    private fun getPayloadFromToolId(id: String): ToolPayload? = when(id) {
        "pencil" -> ToolPayload.PENCIL
        "pen" -> ToolPayload.PEN
        "eraser" -> ToolPayload.ERASER
        "stroke_color" -> ToolPayload.STROKE_COLOR
        "fill_color" -> ToolPayload.FILL_COLOR
        "paint" -> ToolPayload.PAINT
        else -> null
    }

    fun assignTool(toolId: String, payload: ToolPayload) {
        _assignedTools.value = _assignedTools.value + (toolId to payload)
        
        if (payload == ToolPayload.STROKE_COLOR) {
            _assignedToolColors.value = _assignedToolColors.value + (toolId to getDefaultStrokeColor())
        } else if (payload == ToolPayload.FILL_COLOR) {
            _assignedToolColors.value = _assignedToolColors.value + (toolId to getDefaultFillColor())
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
        val descToUse = payload?.label ?: tool.contentDescription
        val isPlaceholderToUse = if (payload != null) false else tool.isPlaceholder

        return tool.copy(
            icon = iconToUse,
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
                ToolRegistry.getToolById("pencil"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("stroke_color"),
                ToolRegistry.getToolById("fill_color"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("eraser"),
                ToolRegistry.getToolById("pen"),
                ToolRegistry.getToolById("divider"),
                ToolRegistry.getToolById("edit_points"),
                ToolRegistry.getToolById("toggle_snap")
            ),
            ToolLocation.TopBar to listOfNotNull(
                ToolRegistry.getToolById("zoom_fit"),
                ToolRegistry.getToolById("zoom_in"),
                ToolRegistry.getToolById("zoom_out"),
                ToolRegistry.getToolById("home_view")
            ),
            ToolLocation.BottomBar to listOfNotNull(
                ToolRegistry.getToolById("tool_selection"),
                ToolRegistry.getToolById("action_paste")
            ),
            ToolLocation.TopLeftCorner to listOfNotNull(
                ToolRegistry.getToolById("menu")
            ),
            ToolLocation.TopRightCorner to listOfNotNull(
                ToolRegistry.getToolById("settings")
            ),
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

    private fun saveLayout() {
        toolbarRepository.saveLayout(
            _toolbarState.value,
            _assignedTools.value,
            _assignedToolColors.value,
            _contextualToolbar.value
        )
    }
}

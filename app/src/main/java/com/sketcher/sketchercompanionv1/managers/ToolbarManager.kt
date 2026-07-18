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
    private val getDefaultStrokeColor: () -> Int,
    private val getDefaultFillColor: () -> Int,
    private val activateTool: (ToolPayload, String) -> Unit,
    private val getActionForTool: (String) -> () -> Unit
) {
    private val _toolbarState = MutableStateFlow<Map<ToolLocation, List<StudioTool>>>(emptyMap())
    val toolbarState: StateFlow<Map<ToolLocation, List<StudioTool>>> = _toolbarState.asStateFlow()

    var onLayoutChanged: ((com.sketcher.sketchercompanionv1.data.ToolbarStateResult) -> Unit)? = null

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



    private fun migrateTool(tool: StudioTool): StudioTool {
        val targetTool = if (tool.registryId == "stroke_type" || tool.registryId == "stroke_freehand") {
            val savedTypeStr = "FREEHAND"
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

    fun removeToolFromAllLayouts(toolRegistryId: String) {
        val updatedMap = _toolbarState.value.mapValues { (_, list) ->
            list.filter { it.registryId != toolRegistryId }
        }
        _toolbarState.value = updatedMap
        saveLayout()
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

    fun revealTool(registryId: String) {
        // Search in contextual toolbar
        val ctxList = _contextualToolbar.value.toMutableList()
        var foundContextual = false
        for (i in ctxList.indices) {
            val parentTool = ctxList[i]
            if (parentTool.registryId == registryId) return // Already at top level
            val subList = if (parentTool.subTools.isNotEmpty()) parentTool.subTools else ToolRegistry.getSubToolsFor(parentTool.registryId)
            val subIndex = subList.indexOfFirst { it.registryId == registryId }
            if (subIndex != -1) {
                swapSubToolToMain(ToolLocation.ContextBar, i, subIndex)
                foundContextual = true
                break
            }
        }
        if (foundContextual) return

        // Search in main toolbars
        val currentMap = _toolbarState.value.toMutableMap()
        for ((location, list) in currentMap) {
            for (i in list.indices) {
                val parentTool = list[i]
                if (parentTool.registryId == registryId) return // Already at top level
                val subList = if (parentTool.subTools.isNotEmpty()) parentTool.subTools else ToolRegistry.getSubToolsFor(parentTool.registryId)
                val subIndex = subList.indexOfFirst { it.registryId == registryId }
                if (subIndex != -1) {
                    swapSubToolToMain(location, i, subIndex)
                    return
                }
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



    fun updateContextualToolbar(newList: List<StudioTool>) {
        _contextualToolbar.value = newList.map { bindToolActions(it) }
        saveLayout()
    }



    fun setToolbarLayout(layout: com.sketcher.sketchercompanionv1.data.ToolbarStateResult) {
        _assignedTools.value = layout.assignedMap
        _assignedToolColors.value = layout.toolColors
        _assignedToolStabilization.value = layout.toolStabilization
        _assignedToolOpacity.value = layout.toolOpacity
        
        val toolsWithActions = layout.tools.mapValues { (_, list) ->
            list.map { tool -> bindToolActions(migrateTool(tool)) }
        }
        _toolbarState.value = toolsWithActions
        
        val contextualWithActions = layout.contextualTools.map { tool ->
            bindToolActions(migrateTool(tool))
        }
        _contextualToolbar.value = contextualWithActions
    }

    private fun removeToolFromListRecursive(list: List<StudioTool>, targetId: String): List<StudioTool> {
        return list.mapNotNull { tool ->
            if (tool.registryId == targetId) null
            else tool.copy(subTools = removeToolFromListRecursive(tool.subTools, targetId))
        }
    }

    fun removeToolFromCurrentLayout(toolId: String) {
        _toolbarState.value = _toolbarState.value.mapValues { (_, tools) ->
            removeToolFromListRecursive(tools, toolId)
        }
        _contextualToolbar.value = removeToolFromListRecursive(_contextualToolbar.value, toolId)
        
        _assignedTools.value = _assignedTools.value - toolId
        _assignedToolColors.value = _assignedToolColors.value - toolId
        _assignedToolStabilization.value = _assignedToolStabilization.value - toolId
        _assignedToolOpacity.value = _assignedToolOpacity.value - toolId
        
        saveLayout()
    }

    private fun saveLayout() {
        onLayoutChanged?.invoke(getCurrentToolbarState())
    }

    fun getCurrentToolbarState(): com.sketcher.sketchercompanionv1.data.ToolbarStateResult {
        return com.sketcher.sketchercompanionv1.data.ToolbarStateResult(
            tools = _toolbarState.value,
            assignedMap = _assignedTools.value,
            toolColors = _assignedToolColors.value,
            contextualTools = _contextualToolbar.value,
            toolStabilization = _assignedToolStabilization.value,
            toolOpacity = _assignedToolOpacity.value
        )
    }
}

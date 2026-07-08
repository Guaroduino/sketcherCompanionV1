package com.sketcher.sketchercompanionv1.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sketcher.sketchercompanionv1.ui.components.ToolPayload
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry

data class SavedTool(
    val instanceId: String,
    val registryId: String,
    val isPlaceholder: Boolean,
    val payload: ToolPayload? = null,
    val subTools: List<SavedTool>? = null
)

data class SavedLayout(
    val tools: Map<ToolLocation, List<SavedTool>>,
    val assignedMap: Map<String, ToolPayload>,
    val toolColors: Map<String, Int>? = emptyMap(),
    val contextualToolbar: List<SavedTool>? = null,
    val toolStabilization: Map<String, Float>? = emptyMap(),
    val toolOpacity: Map<String, Float>? = emptyMap()
)

class ToolbarRepository(context: Context) {
    private val prefs = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveLayout(
        toolbarState: Map<ToolLocation, List<StudioTool>>,
        assignedMap: Map<String, ToolPayload>,
        toolColors: Map<String, Int>,
        toolStabilization: Map<String, Float>,
        toolOpacity: Map<String, Float>,
        contextualToolbar: List<StudioTool>
    ) {
        fun mapToolToSaved(tool: StudioTool): SavedTool {
            return SavedTool(
                instanceId = tool.id,
                registryId = tool.registryId,
                isPlaceholder = tool.isPlaceholder,
                payload = assignedMap[tool.id],
                subTools = tool.subTools.map { mapToolToSaved(it) }
            )
        }

        val savedToolsMap = toolbarState.mapValues { (_, tools) ->
            tools.map { mapToolToSaved(it) }
        }
        val savedContextualTools = contextualToolbar.map { mapToolToSaved(it) }
        
        val layout = SavedLayout(savedToolsMap, assignedMap, toolColors, savedContextualTools, toolStabilization, toolOpacity)
        val json = gson.toJson(layout)
        prefs.edit().putString("saved_layout_v2", json).apply()
    }

    fun loadLayout(): ToolbarStateResult? {
        val json = prefs.getString("saved_layout_v2", null) ?: return null
        return parseLayoutJson(json)
    }

    fun parseLayoutJson(json: String): ToolbarStateResult? {
        return try {
            val type = object : TypeToken<SavedLayout>() {}.type
            val layout: SavedLayout = gson.fromJson(json, type)
            
            fun reconstructStudioTool(saved: SavedTool, assignedMap: Map<String, ToolPayload>): StudioTool? {
                var effectiveRegistryId = if (saved.registryId.startsWith("tool_selection_")) "tool_selection" else saved.registryId
                if (effectiveRegistryId == "settings") return null
                if (effectiveRegistryId == "stroke_type") {
                    effectiveRegistryId = "stroke_freehand"
                }

                val baseTool = ToolRegistry.getToolById(effectiveRegistryId) 
                    ?: ToolRegistry.getToolById("pencil") // Fallback
                
                if (baseTool == null) return null
                
                val restoredSubTools = saved.subTools?.mapNotNull { subSaved ->
                    reconstructStudioTool(subSaved, assignedMap)
                } ?: emptyList()

                var restored = baseTool.copy(
                    id = saved.instanceId,
                    registryId = effectiveRegistryId,
                    isPlaceholder = baseTool.isPlaceholder,
                    subTools = restoredSubTools
                )
                
                val assignedPayload = assignedMap[saved.instanceId]
                if (assignedPayload != null) {
                     restored = restored.copy(
                         icon = assignedPayload.icon,
                         iconResId = assignedPayload.iconResId,
                         contentDescription = assignedPayload.label,
                         isPlaceholder = false
                     )
                }
                
                return restored
            }

            val reconstructedTools = layout.tools.mapValues { (_, savedList) ->
                savedList.mapNotNull { saved ->
                    reconstructStudioTool(saved, layout.assignedMap)
                }
            }
            
            val loadedContextual = layout.contextualToolbar?.mapNotNull { saved ->
                reconstructStudioTool(saved, layout.assignedMap)
            } ?: getDefaultContextualTools()

            val reconstructedContextualTools = if (loadedContextual.none { it.registryId == "context_edit" }) {
                getDefaultContextualTools()
            } else {
                val tools = loadedContextual.toMutableList()
                val editTool = tools.find { it.registryId == "context_edit" }
                if (editTool != null) {
                    tools.remove(editTool)
                    tools.add(0, editTool)
                }
                tools
            }
            
            ToolbarStateResult(
                tools = reconstructedTools, 
                assignedMap = layout.assignedMap, 
                toolColors = layout.toolColors ?: emptyMap(),
                contextualTools = reconstructedContextualTools,
                toolStabilization = layout.toolStabilization ?: emptyMap(),
                toolOpacity = layout.toolOpacity ?: emptyMap()
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun getUiPresetsNames(): List<String> {
        val json = prefs.getString("ui_presets_names", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveUiPreset(name: String, result: ToolbarStateResult) {
        val list = getUiPresetsNames().toMutableList()
        if (!list.contains(name)) {
            list.add(name)
            val jsonList = gson.toJson(list)
            prefs.edit().putString("ui_presets_names", jsonList).apply()
        }
        
        fun mapToolToSaved(tool: StudioTool): SavedTool {
            return SavedTool(
                instanceId = tool.id,
                registryId = tool.registryId,
                isPlaceholder = tool.isPlaceholder,
                payload = result.assignedMap[tool.id],
                subTools = tool.subTools.map { mapToolToSaved(it) }
            )
        }

        val savedToolsMap = result.tools.mapValues { (_, tools) ->
            tools.map { mapToolToSaved(it) }
        }
        val savedContextualTools = result.contextualTools.map { mapToolToSaved(it) }
        
        val layout = SavedLayout(savedToolsMap, result.assignedMap, result.toolColors, savedContextualTools, result.toolStabilization, result.toolOpacity)
        val json = gson.toJson(layout)
        prefs.edit().putString("ui_preset_data_$name", json).apply()
    }

    fun loadUiPreset(name: String): ToolbarStateResult? {
        val json = prefs.getString("ui_preset_data_$name", null) ?: return null
        return parseLayoutJson(json)
    }

    fun deleteUiPreset(name: String) {
        val list = getUiPresetsNames().toMutableList()
        if (list.contains(name)) {
            list.remove(name)
            val jsonList = gson.toJson(list)
            prefs.edit().putString("ui_presets_names", jsonList).apply()
        }
        prefs.edit().remove("ui_preset_data_$name").apply()
    }

    fun getActiveUiPresetName(): String {
        return prefs.getString("active_ui_preset_name", "Default") ?: "Default"
    }

    fun setActiveUiPresetName(name: String) {
        prefs.edit().putString("active_ui_preset_name", name).apply()
    }

    fun getDefaultContextualTools(): List<StudioTool> {
        return listOfNotNull(
            ToolRegistry.getToolById("context_edit"),
            ToolRegistry.getToolById("context_transform"),
            ToolRegistry.getToolById("context_lock_scale"),
            ToolRegistry.getToolById("context_copy"),
            ToolRegistry.getToolById("context_delete"),
            ToolRegistry.getToolById("context_deselect"),
            ToolRegistry.getToolById("context_flip_horizontal"),
            ToolRegistry.getToolById("context_flip_vertical"),
            ToolRegistry.getToolById("context_group"),
            ToolRegistry.getToolById("context_component"),
            ToolRegistry.getToolById("context_ungroup"),
            ToolRegistry.getToolById("context_make_unique")
        )
    }
}

data class ToolbarStateResult(
    val tools: Map<ToolLocation, List<StudioTool>>,
    val assignedMap: Map<String, ToolPayload>,
    val toolColors: Map<String, Int>,
    val contextualTools: List<StudioTool>,
    val toolStabilization: Map<String, Float> = emptyMap(),
    val toolOpacity: Map<String, Float> = emptyMap()
)

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
    val contextualToolbar: List<SavedTool>? = null
)

class ToolbarRepository(context: Context) {
    private val prefs = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveLayout(
        toolbarState: Map<ToolLocation, List<StudioTool>>,
        assignedMap: Map<String, ToolPayload>,
        toolColors: Map<String, Int>,
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
        
        val layout = SavedLayout(savedToolsMap, assignedMap, toolColors, savedContextualTools)
        val json = gson.toJson(layout)
        prefs.edit().putString("saved_layout_v2", json).apply()
    }

    fun loadLayout(): ToolbarStateResult? {
        val json = prefs.getString("saved_layout_v2", null) ?: return null
        
        return try {
            val type = object : TypeToken<SavedLayout>() {}.type
            val layout: SavedLayout = gson.fromJson(json, type)
            
            fun reconstructStudioTool(saved: SavedTool, assignedMap: Map<String, ToolPayload>): StudioTool? {
                val effectiveRegistryId = if (saved.registryId.startsWith("tool_selection_")) "tool_selection" else saved.registryId

                if (effectiveRegistryId == StudioTool.PROPERTIES_TOOL_ID || effectiveRegistryId == StudioTool.STABILIZATION_TOOL_ID) {
                    return null
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
                contextualTools = reconstructedContextualTools
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getDefaultContextualTools(): List<StudioTool> {
        return listOfNotNull(
            ToolRegistry.getToolById("context_edit"),
            ToolRegistry.getToolById("context_transform"),
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
    val contextualTools: List<StudioTool>
)

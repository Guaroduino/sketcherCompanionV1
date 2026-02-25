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
    val payload: ToolPayload? = null
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
        val savedToolsMap = toolbarState.mapValues { (_, tools) ->
            tools.map { tool ->
                SavedTool(
                    instanceId = tool.id,
                    registryId = tool.registryId,
                    isPlaceholder = tool.isPlaceholder,
                    payload = assignedMap[tool.id]
                )
            }
        }
        val savedContextualTools = contextualToolbar.map { tool ->
            SavedTool(
                instanceId = tool.id,
                registryId = tool.registryId,
                isPlaceholder = tool.isPlaceholder,
                payload = assignedMap[tool.id]
            )
        }
        
        val layout = SavedLayout(savedToolsMap, assignedMap, toolColors, savedContextualTools)
        val json = gson.toJson(layout)
        prefs.edit().putString("saved_layout_v2", json).apply()
    }

    fun loadLayout(): ToolbarStateResult? {
        val json = prefs.getString("saved_layout_v2", null) ?: return null
        
        return try {
            val type = object : TypeToken<SavedLayout>() {}.type
            val layout: SavedLayout = gson.fromJson(json, type)
            
            val reconstructedTools = layout.tools.mapValues { (_, savedList) ->
                savedList.mapNotNull { saved ->
                    // Migration: collapse separate selection tools into the generic parent
                    val effectiveRegistryId = if (saved.registryId.startsWith("tool_selection_")) "tool_selection" else saved.registryId

                    // Find base template
                    val baseTool = ToolRegistry.getToolById(effectiveRegistryId) 
                        ?: ToolRegistry.getToolById("pencil") // Fallback
                    
                    if (baseTool == null) return@mapNotNull null
                    
                    var restored = baseTool.copy(
                        id = saved.instanceId,
                        registryId = effectiveRegistryId,
                        isPlaceholder = saved.isPlaceholder
                    )
                    
                    // Apply payload if present (assigned tool)
                    val assignedPayload = layout.assignedMap[saved.instanceId]
                    if (assignedPayload != null) {
                         restored = restored.copy(
                             icon = assignedPayload.icon,
                             contentDescription = assignedPayload.label,
                             isPlaceholder = false
                         )
                    }
                    
                    restored
                }
            }
            
            val reconstructedContextualTools = layout.contextualToolbar?.mapNotNull { saved ->
                val baseTool = ToolRegistry.getToolById(saved.registryId) ?: return@mapNotNull null
                var restored = baseTool.copy(
                    id = saved.instanceId,
                    registryId = saved.registryId,
                    isPlaceholder = saved.isPlaceholder
                )
                
                val assignedPayload = layout.assignedMap[saved.instanceId]
                if (assignedPayload != null) {
                    restored = restored.copy(
                        icon = assignedPayload.icon,
                        contentDescription = assignedPayload.label,
                        isPlaceholder = false
                    )
                }
                restored
            } ?: getDefaultContextualTools()
            
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
            ToolRegistry.getToolById("context_deselect"),
            ToolRegistry.getToolById("context_transform"),
            ToolRegistry.getToolById("context_copy"),
            ToolRegistry.getToolById("context_delete")
        )
    }
}

data class ToolbarStateResult(
    val tools: Map<ToolLocation, List<StudioTool>>,
    val assignedMap: Map<String, ToolPayload>,
    val toolColors: Map<String, Int>,
    val contextualTools: List<StudioTool>
)

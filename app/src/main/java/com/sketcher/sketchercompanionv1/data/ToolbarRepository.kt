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
    val toolColors: Map<String, Int>? = emptyMap()
)

class ToolbarRepository(context: Context) {
    private val prefs = context.getSharedPreferences("toolbar_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun saveLayout(
        toolbarState: Map<ToolLocation, List<StudioTool>>,
        assignedMap: Map<String, ToolPayload>,
        toolColors: Map<String, Int>
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
        
        val layout = SavedLayout(savedToolsMap, assignedMap, toolColors)
        val json = gson.toJson(layout)
        prefs.edit().putString("saved_layout_v2", json).apply()
    }

    fun loadLayout(): Triple<Map<ToolLocation, List<StudioTool>>, Map<String, ToolPayload>, Map<String, Int>>? {
        val json = prefs.getString("saved_layout_v2", null) ?: return null
        
        return try {
            val type = object : TypeToken<SavedLayout>() {}.type
            val layout: SavedLayout = gson.fromJson(json, type)
            
            val reconstructedTools = layout.tools.mapValues { (_, savedList) ->
                savedList.mapNotNull { saved ->
                    // Find base template
                    val baseTool = ToolRegistry.getToolById(saved.registryId) 
                        ?: ToolRegistry.getToolById("pencil") // Fallback
                    
                    if (baseTool == null) return@mapNotNull null
                    
                    var restored = baseTool.copy(
                        id = saved.instanceId,
                        registryId = saved.registryId,
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
            
            Triple(reconstructedTools, layout.assignedMap, layout.toolColors ?: emptyMap())
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

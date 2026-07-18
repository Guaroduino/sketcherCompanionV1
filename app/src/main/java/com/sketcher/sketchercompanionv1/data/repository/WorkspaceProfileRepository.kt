package com.sketcher.sketchercompanionv1.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sketcher.sketchercompanionv1.data.SavedLayout
import com.sketcher.sketchercompanionv1.data.SavedTool
import com.sketcher.sketchercompanionv1.data.ToolbarStateResult
import com.sketcher.sketchercompanionv1.data.db.WorkspaceProfileDao
import com.sketcher.sketchercompanionv1.data.db.entities.WorkspaceProfileEntity
import com.sketcher.sketchercompanionv1.dto.ThemeJson
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry
import com.sketcher.sketchercompanionv1.ui.model.WorkspaceProfile
import com.sketcher.sketchercompanionv1.ui.theme.toDomain
import com.sketcher.sketchercompanionv1.ui.theme.toThemeJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class WorkspaceProfileRepository(private val dao: WorkspaceProfileDao) {
    private val gson = Gson()

    fun getAllProfiles(): Flow<List<WorkspaceProfile>> {
        return dao.getAllProfiles().map { entities ->
            entities.mapNotNull { it.toDomain() }
        }
    }

    fun createDefaultWorkspaceProfile(getDefaultStrokeColor: () -> Int, getDefaultFillColor: () -> Int): WorkspaceProfile {
        val layout = ToolbarStateResult(
            tools = com.sketcher.sketchercompanionv1.ui.model.ToolbarDefaultFactory.createDefaultToolbarState(),
            assignedMap = com.sketcher.sketchercompanionv1.ui.model.ToolbarDefaultFactory.createDefaultAssignedTools(),
            toolColors = com.sketcher.sketchercompanionv1.ui.model.ToolbarDefaultFactory.createDefaultToolColors(getDefaultStrokeColor, getDefaultFillColor),
            contextualTools = com.sketcher.sketchercompanionv1.ui.model.ToolbarDefaultFactory.createDefaultContextualToolbar()
        )
        return WorkspaceProfile(
            id = "default_profile_id",
            name = "Default",
            isDefault = true,
            isReadOnly = false,
            layout = layout,
            theme = com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig()
        )
    }

    suspend fun getProfileById(id: String): WorkspaceProfile? {
        val entity = dao.getProfileById(id)
        return entity?.toDomain()
    }

    suspend fun getDefaultProfile(): WorkspaceProfile? {
        val entity = dao.getDefaultProfile()
        return entity?.toDomain()
    }

    suspend fun saveProfile(profile: WorkspaceProfile) {
        dao.insertProfile(profile.toEntity())
    }

    suspend fun deleteProfile(id: String) {
        dao.deleteProfileById(id)
    }

    private fun WorkspaceProfileEntity.toDomain(): WorkspaceProfile? {
        val themeDto = try {
            gson.fromJson(this.themeJson, ThemeJson::class.java)
        } catch (e: Exception) {
            null
        } ?: return null

        val layoutDto = try {
            val type = object : TypeToken<SavedLayout>() {}.type
            gson.fromJson<SavedLayout>(this.layoutJson, type)
        } catch (e: Exception) {
            null
        } ?: return null

        val reconstructedLayout = reconstructLayout(layoutDto) ?: return null

        return WorkspaceProfile(
            id = this.id,
            name = this.name,
            isDefault = this.isDefault,
            isReadOnly = this.isReadOnly,
            layout = reconstructedLayout,
            theme = themeDto.toDomain()
        )
    }

    private fun WorkspaceProfile.toEntity(): WorkspaceProfileEntity {
        val themeJson = gson.toJson(this.theme.toThemeJson())
        
        fun mapToolToSaved(tool: StudioTool): SavedTool {
            return SavedTool(
                instanceId = tool.id,
                registryId = tool.registryId,
                isPlaceholder = tool.isPlaceholder,
                payload = this.layout.assignedMap[tool.id],
                subTools = tool.subTools.map { mapToolToSaved(it) }
            )
        }

        val savedToolsMap = this.layout.tools.mapValues { (_, tools) ->
            tools.map { mapToolToSaved(it) }
        }
        val savedContextualTools = this.layout.contextualTools.map { mapToolToSaved(it) }
        
        val savedLayout = SavedLayout(
            tools = savedToolsMap,
            assignedMap = this.layout.assignedMap,
            toolColors = this.layout.toolColors,
            contextualToolbar = savedContextualTools,
            toolStabilization = this.layout.toolStabilization,
            toolOpacity = this.layout.toolOpacity
        )
        val layoutJson = gson.toJson(savedLayout)

        return WorkspaceProfileEntity(
            id = this.id,
            name = this.name,
            layoutJson = layoutJson,
            themeJson = themeJson,
            isDefault = this.isDefault,
            isReadOnly = this.isReadOnly,
            lastModified = System.currentTimeMillis()
        )
    }

    private fun reconstructLayout(layout: SavedLayout): ToolbarStateResult? {
        return try {
            fun reconstructStudioTool(saved: SavedTool, assignedMap: Map<String, com.sketcher.sketchercompanionv1.ui.components.ToolPayload>): StudioTool? {
                var effectiveRegistryId = if (saved.registryId.startsWith("tool_selection_")) "tool_selection" else saved.registryId
                if (effectiveRegistryId == "settings") return null
                if (effectiveRegistryId == "stroke_type") {
                    effectiveRegistryId = "stroke_freehand"
                }

                val baseTool = ToolRegistry.getToolById(effectiveRegistryId) 
                    ?: ToolRegistry.getToolById("pencil") // Fallback
                
                if (baseTool == null) return null
                
                val restoredSubTools = if (saved.subTools.isNullOrEmpty()) {
                    baseTool.subTools
                } else {
                    saved.subTools.mapNotNull { subSaved ->
                        reconstructStudioTool(subSaved, assignedMap)
                    }
                }

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

    private fun getDefaultContextualTools(): List<StudioTool> {
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

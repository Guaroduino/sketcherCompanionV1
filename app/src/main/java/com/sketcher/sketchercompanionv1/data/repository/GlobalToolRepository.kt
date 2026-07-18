package com.sketcher.sketchercompanionv1.data.repository

import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.data.db.ToolDao
import com.sketcher.sketchercompanionv1.data.db.entities.UserToolEntity
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.*
import com.sketcher.sketchercompanionv1.tools.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class GlobalToolRepository(private val toolDao: ToolDao) {
    private val gson = Gson()

    fun getAllGlobalToolsFlow(): Flow<List<CustomTool>> {
        return toolDao.getAllToolsFlow().map { entities ->
            entities.map { it.toCustomTool(gson) }
        }
    }

    suspend fun getAllGlobalTools(): List<CustomTool> {
        return toolDao.getAllTools().map { it.toCustomTool(gson) }
    }

    suspend fun getGlobalToolById(id: String): CustomTool? {
        return toolDao.getToolById(id)?.toCustomTool(gson)
    }

    suspend fun saveGlobalTool(tool: CustomTool, isDefault: Boolean = false, sortOrder: Int = 0) {
        val entity = tool.toEntity(gson, isDefault, sortOrder)
        toolDao.insertTool(entity)
    }

    suspend fun saveGlobalTools(tools: List<CustomTool>) {
        val entities = tools.mapIndexed { index, tool ->
            tool.toEntity(gson, isDefault = false, sortOrder = index)
        }
        toolDao.insertTools(entities)
    }

    suspend fun deleteGlobalTool(id: String) {
        toolDao.deleteToolById(id)
    }

    suspend fun clearAll() {
        toolDao.deleteAll()
    }
}

// Extension functions for mapping
fun UserToolEntity.toCustomTool(gson: Gson): CustomTool {
    val presetJsonObj = try {
        gson.fromJson(presetJson, BrushPresetJson::class.java)
    } catch (e: Exception) {
        // Fallback
        BrushPresetJson(
            size = 5f,
            opacity = 1f,
            settingsType = "PencilSettings",
            settingsJson = "{}",
            strokeColor = android.graphics.Color.BLACK,
            fillColor = android.graphics.Color.WHITE,
            isStrokeColorLocked = false,
            isFillColorLocked = false
        )
    }

    val toolType = try { ToolType.valueOf(baseToolType) } catch (e: Exception) { ToolType.FREEHAND }
    val settings = when (toolType) {
        ToolType.FREEHAND -> try { gson.fromJson(presetJsonObj.settingsJson, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PencilSettings()}
        ToolType.PEN -> try { gson.fromJson(presetJsonObj.settingsJson, com.sketcher.sketchercompanionv1.tools.PenSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PenSettings()}
        ToolType.PLUMA -> try { gson.fromJson(presetJsonObj.settingsJson, com.sketcher.sketchercompanionv1.tools.PlumaSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PlumaSettings()}
        ToolType.PAINT -> try { gson.fromJson(presetJsonObj.settingsJson, com.sketcher.sketchercompanionv1.tools.PaintSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PaintSettings()}
        ToolType.WATERCOLOR -> try { gson.fromJson(presetJsonObj.settingsJson, com.sketcher.sketchercompanionv1.tools.WatercolorSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.WatercolorSettings()}
        ToolType.PENCIL_CUMULATIVE -> try { gson.fromJson(presetJsonObj.settingsJson, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PencilSettings(isCumulativeOpacity = true)}
        else -> com.sketcher.sketchercompanionv1.tools.PencilSettings()
    }

    return CustomTool(
        id = id,
        name = name,
        iconName = iconName,
        iconResName = iconResName,
        baseToolType = toolType,
        preset = BrushPreset(
            size = presetJsonObj.size,
            opacity = presetJsonObj.opacity,
            settings = settings,
            strokeColor = presetJsonObj.strokeColor,
            fillColor = presetJsonObj.fillColor,
            isStrokeActive = presetJsonObj.isStrokeActive,
            isFillActive = presetJsonObj.isFillActive,
            isStrokeColorLocked = presetJsonObj.isStrokeColorLocked,
            isFillColorLocked = presetJsonObj.isFillColorLocked,
            fillStyle = presetJsonObj.fillStyle.toFillStyle(presetJsonObj.fillColor ?: 0),
            strokeStyle = presetJsonObj.strokeStyle.toFillStyle(presetJsonObj.strokeColor ?: 0),
            stabilization = presetJsonObj.stabilization
        ),
        customIconJson = customIconJson
    )
}

fun CustomTool.toEntity(gson: Gson, isDefault: Boolean, sortOrder: Int): UserToolEntity {
    val presetJsonObj = BrushPresetJson(
        size = preset.size,
        opacity = preset.opacity,
        settingsType = preset.settings::class.java.simpleName,
        settingsJson = gson.toJson(preset.settings),
        strokeColor = preset.strokeColor,
        fillColor = preset.fillColor,
        isStrokeActive = preset.isStrokeActive,
        isFillActive = preset.isFillActive,
        isStrokeColorLocked = preset.isStrokeColorLocked,
        isFillColorLocked = preset.isFillColorLocked,
        fillStyle = preset.fillStyle?.toFillStyleJson(),
        strokeStyle = preset.strokeStyle?.toFillStyleJson(),
        stabilization = preset.stabilization
    )
    return UserToolEntity(
        id = id,
        name = name,
        iconName = iconName,
        iconResName = iconResName,
        baseToolType = baseToolType.name,
        presetJson = gson.toJson(presetJsonObj),
        customIconJson = customIconJson,
        isDefault = isDefault,
        sortOrder = sortOrder
    )
}

package com.sketcher.sketchercompanionv1.managers

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.*
import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.dto.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ToolManager(context: Context) {
    private val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    // --- TOOL STATE & CONFIG ---
    var currentTool by mutableStateOf(
        try { 
            val savedName = prefs.getString("current_tool", ToolType.FREEHAND.name) ?: ToolType.FREEHAND.name
            val saved = if (ToolType.entries.any { it.name == savedName }) {
                ToolType.valueOf(savedName)
            } else {
                ToolType.FREEHAND
            }
            // Selection and Eraser should not persist between sessions
            if (saved == ToolType.SELECTION || saved == ToolType.ERASER) ToolType.FREEHAND else saved
        } catch(e: Exception) { ToolType.FREEHAND }
    )
        private set

    var currentStrokeType by mutableStateOf(
        try {
            val savedType = prefs.getString("current_stroke_type", StrokeType.FREEHAND.name) ?: StrokeType.FREEHAND.name
            StrokeType.valueOf(savedType)
        } catch (e: Exception) { StrokeType.FREEHAND }
    )
        private set

    var lastDrawingTool by mutableStateOf(ToolType.FREEHAND)

    // --- BRUSH SIZE & OPACITY ---
    private val _brushSize = MutableStateFlow(2f)
    val brushSize = _brushSize.asStateFlow()

    private val _brushOpacity = MutableStateFlow(1f)
    val brushOpacity = _brushOpacity.asStateFlow()

    private val _sizePresets = MutableStateFlow(listOf(5f, 15f, 30f))
    val sizePresets = _sizePresets.asStateFlow()

    var currentSize by mutableFloatStateOf(2f)
        private set
    var currentOpacity by mutableFloatStateOf(1f)
        private set

    // --- COLOR SYSTEM ---
    private val _strokeColor = MutableStateFlow(AndroidColor.BLACK)
    val strokeColor = _strokeColor.asStateFlow()

    private val _fillColor = MutableStateFlow(AndroidColor.argb(128, 0, 0, 255))
    val fillColor = _fillColor.asStateFlow()

    private val _isStrokeActive = MutableStateFlow(true)
    val isStrokeActive = _isStrokeActive.asStateFlow()

    private val _isFillActive = MutableStateFlow(false)
    val isFillActive = _isFillActive.asStateFlow()

    // --- STABILIZATION & SMOOTHING ---
    private val _smoothing = MutableStateFlow(prefs.getFloat("global_stabilization", 0f))
    val smoothing = _smoothing.asStateFlow()

    var globalStabilizationLevel by mutableFloatStateOf(prefs.getFloat("global_stabilization", 0f))
        private set

    var currentFreehandSettings by mutableStateOf(loadFreehandSettings())
        private set

    // --- FINGER OFFSET ---
    var fingerModeActive by mutableStateOf(false)
        private set
    var fingerOffsetXValue by mutableFloatStateOf(0f)
        private set
    var fingerOffsetYValue by mutableFloatStateOf(50f)
        private set

    private val toolConfigs: MutableMap<ToolType, ToolConfig> = mutableStateMapOf<ToolType, ToolConfig>().apply {
        val savedFreehand = loadFreehandSettings()
        
        fun loadConfig(type: ToolType, defSize: Float, defOpacity: Float): ToolConfig {
            val s = prefs.getFloat("tool_size_${type.name}", defSize)
            val o = prefs.getFloat("tool_alpha_${type.name}", defOpacity)
            return ToolConfig(size = s, opacity = o, freehandSettings = if (type == ToolType.FREEHAND) savedFreehand else FreehandSettings())
        }

        put(ToolType.FREEHAND, loadConfig(ToolType.FREEHAND, 2f, 1f))
        put(ToolType.FILL, loadConfig(ToolType.FILL, 1f, 1.0f))
        put(ToolType.ERASER, loadConfig(ToolType.ERASER, 10f, 1f))
        put(ToolType.SELECTION, loadConfig(ToolType.SELECTION, 1f, 1f))
    }

    init {
        val freehandConfig = toolConfigs[ToolType.FREEHAND]!!
        fingerModeActive = freehandConfig.isFingerMode
        fingerOffsetXValue = freehandConfig.fingerOffsetX
        fingerOffsetYValue = freehandConfig.fingerOffsetY
        selectTool(currentTool)
    }

    // --- LOGIC METHODS ---

    fun selectTool(type: ToolType) {
        if (type != ToolType.ERASER && type != ToolType.SELECTION && type != ToolType.FILL) {
            lastDrawingTool = type
        }
        currentTool = type
        prefs.edit().putString("current_tool", type.name).apply()
        
        val config = toolConfigs[type] ?: toolConfigs[ToolType.FREEHAND]!!
        currentSize = config.size
        _brushSize.value = config.size
        currentOpacity = config.opacity
        _brushOpacity.value = config.opacity
        currentFreehandSettings = config.freehandSettings
    }

    fun setToolSize(size: Float) {
        currentSize = size
        _brushSize.value = size
        val config = toolConfigs[currentTool]!!
        toolConfigs[currentTool] = config.copy(size = size)
        prefs.edit().putFloat("tool_size_${currentTool.name}", size).apply()
    }

    fun setToolOpacity(opacity: Float) {
        currentOpacity = opacity
        _brushOpacity.value = opacity
        val config = toolConfigs[currentTool]!!
        toolConfigs[currentTool] = config.copy(opacity = opacity)
        prefs.edit().putFloat("tool_alpha_${currentTool.name}", opacity).apply()
    }

    fun updateBrushSize(newSize: Float) = setToolSize(newSize)
    fun updateBrushOpacity(newAlpha: Float) = setToolOpacity(newAlpha)

    fun saveSizePreset(index: Int, size: Float) {
        val currentList = _sizePresets.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = size
            _sizePresets.value = currentList
            prefs.edit().putFloat("size_preset_$index", size).apply()
        }
    }

    fun updateStrokeType(type: StrokeType) {
        currentStrokeType = type
        prefs.edit().putString("current_stroke_type", type.name).apply()
    }

    fun setStrokeColor(color: Int) {
        _strokeColor.value = color
        _isStrokeActive.value = true
    }

    fun setFillColor(color: Int) {
        _fillColor.value = color
        _isFillActive.value = true
    }

    fun toggleStroke(enabled: Boolean) { _isStrokeActive.value = enabled }
    fun toggleFill(enabled: Boolean) { _isFillActive.value = enabled }

    fun setGlobalStabilization(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        if (globalStabilizationLevel != clamped) {
            globalStabilizationLevel = clamped
            _smoothing.value = clamped
            prefs.edit().putFloat("global_stabilization", clamped).apply()
        }
    }

    fun updateSmoothing(value: Float) = setGlobalStabilization(value)

    fun updateFreehandSettings(newSettings: FreehandSettings) {
        currentFreehandSettings = newSettings
        val config = toolConfigs[currentTool]!!
        toolConfigs[currentTool] = config.copy(freehandSettings = newSettings)
        saveFreehandSettings(newSettings)
    }

    fun setFingerMode(enabled: Boolean) {
        fingerModeActive = enabled
        val currentConfigs = toolConfigs.toMap()
        toolConfigs.clear()
        currentConfigs.forEach { (type, config) ->
            toolConfigs[type] = config.copy(isFingerMode = enabled)
        }
    }

    fun setFingerOffset(x: Float, y: Float) {
        fingerOffsetXValue = x
        fingerOffsetYValue = y
        val currentConfigs = toolConfigs.toMap()
        toolConfigs.clear()
        currentConfigs.forEach { (type, config) ->
             toolConfigs[type] = config.copy(fingerOffsetX = x, fingerOffsetY = y)
        }
    }

    private fun loadFreehandSettings(): FreehandSettings {
        val json = prefs.getString("freehand_settings_v2", null) ?: return FreehandSettings()
        return try { gson.fromJson(json, FreehandSettings::class.java) } catch (e: Exception) { FreehandSettings() }
    }

    private fun saveFreehandSettings(settings: FreehandSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("freehand_settings_v2", json).apply()
    }

    fun getToolConfigMap(): Map<ToolType, ToolConfig> = toolConfigs.toMap()

    fun updateToolConfigs(newConfigs: Map<ToolType, ToolConfig>) {
        newConfigs.forEach { (type, config) ->
            toolConfigs[type] = config
            // Persist
            prefs.edit().putFloat("tool_size_${type.name}", config.size).apply()
            prefs.edit().putFloat("tool_alpha_${type.name}", config.opacity).apply()
            // Note: full FreehandSettings persistence might need Gson here too, 
            // but usually selectTool handles the sync from config to UI state.
        }
    }

    /** Applies a single config entry loaded from a saved project file. */
    fun applyToolConfig(type: ToolType, config: ToolConfig) {
        toolConfigs[type] = config
        prefs.edit()
            .putFloat("tool_size_${type.name}", config.size)
            .putFloat("tool_alpha_${type.name}", config.opacity)
            .apply()
    }
}

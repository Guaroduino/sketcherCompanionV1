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

    private val _brushPresets = MutableStateFlow<List<BrushPreset>>(emptyList())
    val brushPresets = _brushPresets.asStateFlow()

    private val _selectedPresetIndex = MutableStateFlow<Int?>(null)
    val selectedPresetIndex = _selectedPresetIndex.asStateFlow()

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

    // --- EXPERIMENTAL: Flattened Outer Stroke ---
    var isFlattenedOuterStrokeEnabled by mutableStateOf(true)
        private set

    fun toggleFlattenedOuterStroke() {
        val newVal = !isFlattenedOuterStrokeEnabled
        isFlattenedOuterStrokeEnabled = newVal
        if (newVal && currentFreehandSettings.isCumulativeOpacity) {
            updateFreehandSettings(currentFreehandSettings.copy(isCumulativeOpacity = false))
        }
    }

    // --- FINGER OFFSET ---
    var fingerModeActive by mutableStateOf(false)
        private set
    var fingerOffsetXValue by mutableFloatStateOf(0f)
        private set
    var fingerOffsetYValue by mutableFloatStateOf(50f)
        private set

    private val toolConfigs: MutableMap<ToolType, ToolConfig> = mutableStateMapOf<ToolType, ToolConfig>().apply {
        val savedFreehand = loadFreehandSettings()
        val savedPen = loadPenSettings()
        val savedPaint = loadPaintSettings()
        val savedPluma = loadPlumaSettings()
        
        fun loadConfig(type: ToolType, defSize: Float, defOpacity: Float): ToolConfig {
            val s = prefs.getFloat("tool_size_${type.name}", defSize)
            val o = prefs.getFloat("tool_alpha_${type.name}", defOpacity)
            val settings = when(type) {
                ToolType.FREEHAND -> savedFreehand
                ToolType.PEN -> savedPen
                ToolType.PAINT -> savedPaint
                ToolType.PLUMA -> savedPluma
                else -> FreehandSettings()
            }
            return ToolConfig(size = s, opacity = o, freehandSettings = settings)
        }

        put(ToolType.FREEHAND, loadConfig(ToolType.FREEHAND, 2f, 1f))
        put(ToolType.PEN, loadConfig(ToolType.PEN, 2f, 1f))
        put(ToolType.PAINT, loadConfig(ToolType.PAINT, 10f, 1f))
        put(ToolType.PLUMA, loadConfig(ToolType.PLUMA, 2.5f, 1f))
        put(ToolType.FILL, loadConfig(ToolType.FILL, 1f, 1.0f))
        put(ToolType.ERASER, loadConfig(ToolType.ERASER, 10f, 1f))
        put(ToolType.SELECTION, loadConfig(ToolType.SELECTION, 1f, 1f))
        put(ToolType.TRIM, loadConfig(ToolType.TRIM, 1f, 1f))
        put(ToolType.EXTEND, loadConfig(ToolType.EXTEND, 1f, 1f))
        put(ToolType.EDIT_POINTS, loadConfig(ToolType.EDIT_POINTS, 1f, 1f))
    }

    init {
        val freehandConfig = toolConfigs[ToolType.FREEHAND]!!
        fingerModeActive = freehandConfig.isFingerMode
        fingerOffsetXValue = freehandConfig.fingerOffsetX
        fingerOffsetYValue = freehandConfig.fingerOffsetY
        _brushPresets.value = loadBrushPresetsForTool(currentTool)
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
        var settings = config.freehandSettings
        
        // Enforce tool-specific constraints
        when (type) {
            ToolType.FREEHAND -> {
                isFlattenedOuterStrokeEnabled = true
                settings = settings.copy(isCumulativeOpacity = false)
            }
            ToolType.PAINT -> {
                isFlattenedOuterStrokeEnabled = true
                settings = settings.copy(
                    capStart = true,
                    capEnd = true,
                    useCurveForPolygon = true,
                    isCumulativeOpacity = false
                )
            }
            ToolType.PEN -> {
                isFlattenedOuterStrokeEnabled = false
                settings = settings.copy(isCumulativeOpacity = false)
            }
            ToolType.PLUMA -> {
                isFlattenedOuterStrokeEnabled = false
                settings = settings.copy(isCumulativeOpacity = false)
            }
            else -> {}
        }
        
        currentSize = config.size
        _brushSize.value = config.size
        currentOpacity = config.opacity
        _brushOpacity.value = config.opacity
        currentFreehandSettings = settings
        toolConfigs[type] = config.copy(freehandSettings = settings)
        
        if (settings.isCumulativeOpacity) {
            isFlattenedOuterStrokeEnabled = false
        }

        // Load presets for this specific tool
        _brushPresets.value = loadBrushPresetsForTool(type)
        _selectedPresetIndex.value = null

        if (type == ToolType.PAINT) {
            _isStrokeActive.value = true
            _isFillActive.value = true
        } else if (type == ToolType.FREEHAND || type == ToolType.PEN || type == ToolType.PLUMA) {
            _isStrokeActive.value = true
            _isFillActive.value = false
        }
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

    fun updateFillOpacity(opacity: Float) {
        val current = _fillColor.value
        val alpha = (opacity * 255f).coerceIn(0f, 255f).toInt()
        _fillColor.value = (current and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun loadBrushPresetsForTool(type: ToolType): List<BrushPreset> {
        val prefKey = when(type) {
            ToolType.FREEHAND -> "pencil_presets_v1"
            ToolType.PEN -> "pen_presets_v1"
            ToolType.PAINT -> "paint_presets_v1"
            ToolType.PLUMA -> "pluma_presets_v1"
            else -> "brush_presets_v1"
        }
        val json = prefs.getString(prefKey, null)
        if (json != null) {
            try {
                val tokenType = object : com.google.gson.reflect.TypeToken<List<BrushPreset>>() {}.type
                val loaded: List<BrushPreset> = gson.fromJson(json, tokenType)
                if (loaded.size >= 5) {
                    return loaded
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return getDefaultPresetsForTool(type)
    }

    private fun saveBrushPresetsForTool(type: ToolType, list: List<BrushPreset>) {
        val prefKey = when(type) {
            ToolType.FREEHAND -> "pencil_presets_v1"
            ToolType.PEN -> "pen_presets_v1"
            ToolType.PAINT -> "paint_presets_v1"
            ToolType.PLUMA -> "pluma_presets_v1"
            else -> "brush_presets_v1"
        }
        val json = gson.toJson(list)
        prefs.edit().putString(prefKey, json).apply()
    }

    private fun getDefaultPresetsForTool(type: ToolType): List<BrushPreset> {
        return when(type) {
            ToolType.PEN -> listOf(
                BrushPreset(size = 1f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0f, smoothing = 0f)),
                BrushPreset(size = 2f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0f, smoothing = 0f)),
                BrushPreset(size = 4f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0f, smoothing = 0f)),
                BrushPreset(size = 8f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0f, smoothing = 0f)),
                BrushPreset(size = 15f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0f, smoothing = 0f))
            )
            ToolType.PLUMA -> listOf(
                BrushPreset(size = 1.5f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.1f, smoothing = 0.5f)),
                BrushPreset(size = 3f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.2f, smoothing = 0.5f)),
                BrushPreset(size = 6f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.3f, smoothing = 0.5f)),
                BrushPreset(size = 10f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.4f, smoothing = 0.5f)),
                BrushPreset(size = 18f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.5f, smoothing = 0.5f))
            )
            ToolType.PAINT -> listOf(
                BrushPreset(size = 8f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 1.5f)),
                BrushPreset(size = 15f, opacity = 0.8f, freehandSettings = FreehandSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 2.0f)),
                BrushPreset(size = 25f, opacity = 0.6f, freehandSettings = FreehandSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 3.0f)),
                BrushPreset(size = 40f, opacity = 0.4f, freehandSettings = FreehandSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 4.0f)),
                BrushPreset(size = 60f, opacity = 0.2f, freehandSettings = FreehandSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 5.0f))
            )
            else -> listOf(
                BrushPreset(size = 2f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.4f, smoothing = 0.3f)),
                BrushPreset(size = 5f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.5f, smoothing = 0.4f)),
                BrushPreset(size = 12f, opacity = 1f, freehandSettings = FreehandSettings(thinning = 0.6f, smoothing = 0.5f)),
                BrushPreset(size = 20f, opacity = 0.8f, freehandSettings = FreehandSettings(thinning = 0.7f, smoothing = 0.6f, simulatePressure = true, taperStart = 20f, taperEnd = 20f)),
                BrushPreset(size = 35f, opacity = 0.4f, freehandSettings = FreehandSettings(thinning = 0f, smoothing = 0.4f))
            )
        }
    }

    fun saveBrushPreset(index: Int) {
        val currentPreset = BrushPreset(
            size = currentSize,
            opacity = currentOpacity,
            freehandSettings = currentFreehandSettings
        )
        val currentList = _brushPresets.value.toMutableList()
        if (index in 0 until currentList.size) {
            currentList[index] = currentPreset
            _brushPresets.value = currentList
            saveBrushPresetsForTool(currentTool, currentList)
            _selectedPresetIndex.value = index
        }
    }

    fun selectBrushPreset(index: Int) {
        val list = _brushPresets.value
        if (index in list.indices) {
            val preset = list[index]
            setToolSize(preset.size)
            setToolOpacity(preset.opacity)
            updateFreehandSettings(preset.freehandSettings)
            _selectedPresetIndex.value = index
        }
    }

    fun isPresetModified(index: Int): Boolean {
        val list = _brushPresets.value
        if (index !in list.indices) return false
        val preset = list[index]
        return _brushSize.value != preset.size ||
               _brushOpacity.value != preset.opacity ||
               currentFreehandSettings != preset.freehandSettings
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
        var settings = newSettings
        when (currentTool) {
            ToolType.FREEHAND -> {
                isFlattenedOuterStrokeEnabled = true
                settings = settings.copy(isCumulativeOpacity = false)
            }
            ToolType.PAINT -> {
                isFlattenedOuterStrokeEnabled = true
                settings = settings.copy(
                    capStart = true,
                    capEnd = true,
                    useCurveForPolygon = true,
                    isCumulativeOpacity = false
                )
            }
            ToolType.PEN -> {
                isFlattenedOuterStrokeEnabled = false
                settings = settings.copy(isCumulativeOpacity = false)
            }
            ToolType.PLUMA -> {
                isFlattenedOuterStrokeEnabled = false
                settings = settings.copy(isCumulativeOpacity = false)
            }
            else -> {}
        }
        currentFreehandSettings = settings
        val config = toolConfigs[currentTool]!!
        toolConfigs[currentTool] = config.copy(freehandSettings = settings)
        when (currentTool) {
            ToolType.PEN -> savePenSettings(settings)
            ToolType.FREEHAND -> saveFreehandSettings(settings)
            ToolType.PAINT -> savePaintSettings(settings)
            ToolType.PLUMA -> savePlumaSettings(settings)
            else -> {}
        }
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
        val json = prefs.getString("freehand_settings_v3", null) ?: return FreehandSettings()
        return try { gson.fromJson(json, FreehandSettings::class.java) } catch (e: Exception) { FreehandSettings() }
    }

    private fun saveFreehandSettings(settings: FreehandSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("freehand_settings_v3", json).apply()
    }

    private fun loadPaintSettings(): FreehandSettings {
        val json = prefs.getString("paint_settings_v3", null) ?: return FreehandSettings(thinning = 0.5f, smoothing = 0.5f, simulatePressure = true)
        return try { gson.fromJson(json, FreehandSettings::class.java) } catch (e: Exception) { FreehandSettings() }
    }

    private fun savePaintSettings(settings: FreehandSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("paint_settings_v3", json).apply()
    }

    private fun loadPenSettings(): FreehandSettings {
        val json = prefs.getString("pen_settings_v3", null) ?: return FreehandSettings(thinning = 0f, smoothing = 0f, simulatePressure = false)
        return try { gson.fromJson(json, FreehandSettings::class.java) } catch (e: Exception) { FreehandSettings() }
    }

    private fun savePenSettings(settings: FreehandSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("pen_settings_v3", json).apply()
    }

    private fun loadPlumaSettings(): FreehandSettings {
        val json = prefs.getString("pluma_settings_v3", null) ?: return FreehandSettings()
        return try { gson.fromJson(json, FreehandSettings::class.java) } catch (e: Exception) { FreehandSettings() }
    }

    private fun savePlumaSettings(settings: FreehandSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("pluma_settings_v3", json).apply()
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

    fun reloadConfigs() {
        currentTool = try { 
            val savedName = prefs.getString("current_tool", ToolType.FREEHAND.name) ?: ToolType.FREEHAND.name
            val saved = if (ToolType.entries.any { it.name == savedName }) {
                ToolType.valueOf(savedName)
            } else {
                ToolType.FREEHAND
            }
            if (saved == ToolType.SELECTION || saved == ToolType.ERASER) ToolType.FREEHAND else saved
        } catch(e: Exception) { ToolType.FREEHAND }

        currentStrokeType = try {
            val savedType = prefs.getString("current_stroke_type", StrokeType.FREEHAND.name) ?: StrokeType.FREEHAND.name
            StrokeType.valueOf(savedType)
        } catch (e: Exception) { StrokeType.FREEHAND }

        globalStabilizationLevel = prefs.getFloat("global_stabilization", 0f)
        _smoothing.value = globalStabilizationLevel

        val savedFreehand = loadFreehandSettings()
        val savedPen = loadPenSettings()
        val savedPaint = loadPaintSettings()
        val savedPluma = loadPlumaSettings()
        
        fun loadConfig(type: ToolType, defSize: Float, defOpacity: Float): ToolConfig {
            val s = prefs.getFloat("tool_size_${type.name}", defSize)
            val o = prefs.getFloat("tool_alpha_${type.name}", defOpacity)
            val settings = when(type) {
                ToolType.FREEHAND -> savedFreehand
                ToolType.PEN -> savedPen
                ToolType.PAINT -> savedPaint
                ToolType.PLUMA -> savedPluma
                else -> FreehandSettings()
            }
            return ToolConfig(size = s, opacity = o, freehandSettings = settings)
        }

        toolConfigs[ToolType.FREEHAND] = loadConfig(ToolType.FREEHAND, 2f, 1f)
        toolConfigs[ToolType.PEN] = loadConfig(ToolType.PEN, 2f, 1f)
        toolConfigs[ToolType.PAINT] = loadConfig(ToolType.PAINT, 10f, 1f)
        toolConfigs[ToolType.PLUMA] = loadConfig(ToolType.PLUMA, 2.5f, 1f)
        toolConfigs[ToolType.FILL] = loadConfig(ToolType.FILL, 1f, 1.0f)
        toolConfigs[ToolType.ERASER] = loadConfig(ToolType.ERASER, 10f, 1f)
        toolConfigs[ToolType.SELECTION] = loadConfig(ToolType.SELECTION, 1f, 1f)
        toolConfigs[ToolType.TRIM] = loadConfig(ToolType.TRIM, 1f, 1f)
        toolConfigs[ToolType.EXTEND] = loadConfig(ToolType.EXTEND, 1f, 1f)
        toolConfigs[ToolType.EDIT_POINTS] = loadConfig(ToolType.EDIT_POINTS, 1f, 1f)

        val freehandConfig = toolConfigs[ToolType.FREEHAND]!!
        fingerModeActive = freehandConfig.isFingerMode
        fingerOffsetXValue = freehandConfig.fingerOffsetX
        fingerOffsetYValue = freehandConfig.fingerOffsetY
        
        selectTool(currentTool)
    }
}

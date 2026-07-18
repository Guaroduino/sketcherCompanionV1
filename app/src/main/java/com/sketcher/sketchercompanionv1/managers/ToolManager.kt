package com.sketcher.sketchercompanionv1.managers

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.*
import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.R
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.tools.*
import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.utils.toFillStyle
import com.sketcher.sketchercompanionv1.utils.toFillStyleJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sketcher.sketchercompanionv1.data.repository.GlobalToolRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class ToolManager(
    private val context: Context,
    private val globalToolRepository: GlobalToolRepository,
    private val scope: CoroutineScope
) {
    private val prefs = context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    private var isProjectOpen = false
    private var activeProjectToolsConfig: MutableMap<String, ToolConfigJson> = mutableMapOf()
    
    private val _globalCustomTools = MutableStateFlow<List<CustomTool>>(emptyList())
    val globalCustomTools = _globalCustomTools.asStateFlow()

    private val _projectCustomTools = MutableStateFlow<List<CustomTool>>(emptyList())
    val projectCustomTools = _projectCustomTools.asStateFlow()

    private val _customTools = MutableStateFlow<List<CustomTool>>(emptyList())
    val customTools = _customTools.asStateFlow()

    private fun updateEffectiveTools() {
        val map = _globalCustomTools.value.associateBy { it.id }.toMutableMap()
        _projectCustomTools.value.forEach { map[it.id] = it }
        val effective = map.values.toList()
        _customTools.value = effective
        updateRegistryCustomTools(effective)
    }

    private fun getSafeInt(key: String, default: Int): Int {
        return try {
            prefs.getInt(key, default)
        } catch (e: Exception) {
            try {
                prefs.getLong(key, default.toLong()).toInt()
            } catch (ex: Exception) {
                default
            }
        }
    }

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
            if (saved == ToolType.SELECTION || saved == ToolType.ERASER || saved == ToolType.POINT_ERASER || saved == ToolType.CUT_ERASER) ToolType.FREEHAND else saved
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

    private val _fillPresets = MutableStateFlow<List<FillStyle>>(emptyList())
    val fillPresets = _fillPresets.asStateFlow()

    private val _selectedPresetIndex = MutableStateFlow<Int?>(null)
    val selectedPresetIndex = _selectedPresetIndex.asStateFlow()

    private val _isStrokeColorPreset = MutableStateFlow(true)
    val isStrokeColorPreset = _isStrokeColorPreset.asStateFlow()

    private val _isFillColorPreset = MutableStateFlow(true)
    val isFillColorPreset = _isFillColorPreset.asStateFlow()

    var activeCustomToolId: String? = null
        set(value) {
            field = value
            updatePresetFlows()
        }

    private val lastPresetIndexPerTool = mutableMapOf<ToolType, Int>().apply {
        ToolType.entries.forEach { type ->
            put(type, getSafeInt("last_preset_index_${type.name}", 0))
        }
    }

    var onCustomToolAddedOrUpdated: ((CustomTool) -> Unit)? = null
    var onCustomToolRemoved: ((String) -> Unit)? = null


    var currentSize by mutableFloatStateOf(2f)
        private set
    var currentOpacity by mutableFloatStateOf(1f)
        private set

    // --- COLOR SYSTEM ---
    private val _strokeColor = MutableStateFlow(AndroidColor.BLACK)
    val strokeColor = _strokeColor.asStateFlow()

    private val _strokeStyle = MutableStateFlow<FillStyle>(FillStyle.Solid(AndroidColor.BLACK))
    val strokeStyle = _strokeStyle.asStateFlow()

    private val _fillColor = MutableStateFlow(AndroidColor.WHITE)
    val fillColor = _fillColor.asStateFlow()

    private val _fillStyle = MutableStateFlow<FillStyle>(FillStyle.Solid(AndroidColor.WHITE))
    val fillStyle = _fillStyle.asStateFlow()

    private val _fillOpacity = MutableStateFlow(0.5f)
    val fillOpacity = _fillOpacity.asStateFlow()

    private val _isStrokeActive = MutableStateFlow(true)
    val isStrokeActive = _isStrokeActive.asStateFlow()

    private val _isFillActive = MutableStateFlow(false)
    val isFillActive = _isFillActive.asStateFlow()

    // --- STABILIZATION & SMOOTHING ---
    private val _smoothing = MutableStateFlow(prefs.getFloat("global_stabilization", 0f))
    val smoothing = _smoothing.asStateFlow()

    var globalStabilizationLevel by mutableFloatStateOf(prefs.getFloat("global_stabilization", 0f))
        private set

    // Active tools map containing concrete BrushTool implementations
    private val activeTools = mutableMapOf<ToolType, com.sketcher.sketchercompanionv1.tools.BrushTool>(
        ToolType.FREEHAND to com.sketcher.sketchercompanionv1.tools.PencilTool(settings = loadFreehandSettings()),
        ToolType.PEN to com.sketcher.sketchercompanionv1.tools.PenTool(settings = loadPenSettings()),
        ToolType.PLUMA to com.sketcher.sketchercompanionv1.tools.PlumaTool(settings = loadPlumaSettings()),
        ToolType.PAINT to com.sketcher.sketchercompanionv1.tools.PaintTool(settings = loadPaintSettings()),
        ToolType.WATERCOLOR to com.sketcher.sketchercompanionv1.tools.WatercolorTool(settings = loadWatercolorSettings()),
        ToolType.PENCIL_CUMULATIVE to com.sketcher.sketchercompanionv1.tools.PencilCumulativeTool(settings = loadPencilCumulativeSettings())
    )

    var currentFreehandSettings by mutableStateOf<com.sketcher.sketchercompanionv1.tools.ToolSettings>(
        activeTools[currentTool]?.settings ?: com.sketcher.sketchercompanionv1.tools.PencilSettings()
    )
        private set

    var currentEraserShape by mutableStateOf(EraserShape.CIRCLE)
        private set

    // --- EXPERIMENTAL: Flattened Outer Stroke ---
    var isFlattenedOuterStrokeEnabled by mutableStateOf(true)
        private set

    fun toggleFlattenedOuterStroke() {
        val newVal = !isFlattenedOuterStrokeEnabled
        isFlattenedOuterStrokeEnabled = newVal
        val current = currentFreehandSettings
        if (newVal && current is com.sketcher.sketchercompanionv1.tools.PencilSettings && current.isCumulativeOpacity) {
            updateFreehandSettings(current.copy(isCumulativeOpacity = false))
        }
    }

    // --- FINGER OFFSET ---
    var fingerModeActive by mutableStateOf(false)
        private set
    var fingerOffsetXValue by mutableFloatStateOf(0f)
        private set
    var fingerOffsetYValue by mutableFloatStateOf(50f)
        private set

    private fun loadToolConfig(type: ToolType, defSize: Float, defOpacity: Float, settings: ToolSettings): ToolConfig {
        val s = prefs.getFloat("tool_size_${type.name}", defSize)
        val o = prefs.getFloat("tool_alpha_${type.name}", defOpacity)
        val shapeName = prefs.getString("tool_eraser_shape_${type.name}", EraserShape.CIRCLE.name) ?: EraserShape.CIRCLE.name
        val shape = try { EraserShape.valueOf(shapeName) } catch (e: Exception) { EraserShape.CIRCLE }

        val defaultStrokeColor = AndroidColor.BLACK
        val defaultFillColor = if (type == ToolType.PAINT || type == ToolType.WATERCOLOR) AndroidColor.BLACK else AndroidColor.WHITE
        val defaultStrokeActive = true
        val defaultFillActive = (type == ToolType.PAINT || type == ToolType.WATERCOLOR)
        val defaultStabilization = if (type == ToolType.FREEHAND || type == ToolType.PENCIL_CUMULATIVE) 0.07f else 0f

        val sc = getSafeInt("tool_stroke_color_${type.name}", defaultStrokeColor)
        val fc = getSafeInt("tool_fill_color_${type.name}", defaultFillColor)
        val sa = prefs.getBoolean("tool_stroke_active_${type.name}", defaultStrokeActive)
        val fa = prefs.getBoolean("tool_fill_active_${type.name}", defaultFillActive)
        val stab = prefs.getFloat("tool_stabilization_${type.name}", defaultStabilization)
        
        val fsJsonStr = prefs.getString("tool_fill_style_v2_${type.name}", null)
        val ssJsonStr = prefs.getString("tool_stroke_style_v2_${type.name}", null)
        
        val fsJson = if (fsJsonStr != null) {
            try { gson.fromJson(fsJsonStr, FillStyleJson::class.java) } catch (e: Exception) { null }
        } else null
        
        val ssJson = if (ssJsonStr != null) {
            try { gson.fromJson(ssJsonStr, FillStyleJson::class.java) } catch (e: Exception) { null }
        } else null

        return ToolConfig(
            size = s,
            opacity = o,
            settings = settings,
            eraserShape = shape,
            strokeColor = sc,
            fillColor = fc,
            isStrokeActive = sa,
            isFillActive = fa,
            fillStyle = fsJson,
            strokeStyle = ssJson,
            stabilization = stab
        )
    }

    private fun persistToolConfigColorState(type: ToolType, config: ToolConfig) {
        if (isProjectOpen) {
            val json = ToolConfigJson(
                size = config.size,
                opacity = config.opacity,
                settingsType = config.settings::class.java.simpleName,
                settingsJson = gson.toJson(config.settings),
                fillSettingsTolerance = config.fillSettings.tolerance,
                isFingerMode = config.isFingerMode,
                fingerOffsetX = config.fingerOffsetX,
                fingerOffsetY = config.fingerOffsetY,
                eraserShape = config.eraserShape.name,
                strokeColor = config.strokeColor,
                fillColor = config.fillColor,
                isStrokeActive = config.isStrokeActive,
                isFillActive = config.isFillActive,
                fillStyle = config.fillStyle,
                strokeStyle = config.strokeStyle,
                stabilization = config.stabilization
            )
            activeProjectToolsConfig[type.name] = json
        } else {
            prefs.edit().apply {
                putInt("tool_stroke_color_${type.name}", config.strokeColor)
                putInt("tool_fill_color_${type.name}", config.fillColor)
                putBoolean("tool_stroke_active_${type.name}", config.isStrokeActive)
                putBoolean("tool_fill_active_${type.name}", config.isFillActive)
                putFloat("tool_stabilization_${type.name}", config.stabilization)
                
                val fsStr = config.fillStyle?.let { gson.toJson(it) }
                val ssStr = config.strokeStyle?.let { gson.toJson(it) }
                putString("tool_fill_style_v2_${type.name}", fsStr)
                putString("tool_stroke_style_v2_${type.name}", ssStr)
                apply()
            }
        }
    }

    private val toolConfigs: MutableMap<ToolType, ToolConfig> = mutableStateMapOf<ToolType, ToolConfig>().apply {
        put(ToolType.FREEHAND, loadToolConfig(ToolType.FREEHAND, 2f, 1f, activeTools[ToolType.FREEHAND]?.settings ?: PencilSettings()))
        put(ToolType.PEN, loadToolConfig(ToolType.PEN, 2f, 1f, activeTools[ToolType.PEN]?.settings ?: PenSettings()))
        put(ToolType.PAINT, loadToolConfig(ToolType.PAINT, 10f, 1f, activeTools[ToolType.PAINT]?.settings ?: PaintSettings()))
        put(ToolType.PLUMA, loadToolConfig(ToolType.PLUMA, 2.5f, 1f, activeTools[ToolType.PLUMA]?.settings ?: PlumaSettings()))
        put(ToolType.WATERCOLOR, loadToolConfig(ToolType.WATERCOLOR, 20f, 0.4f, activeTools[ToolType.WATERCOLOR]?.settings ?: WatercolorSettings()))
        put(ToolType.PENCIL_CUMULATIVE, loadToolConfig(ToolType.PENCIL_CUMULATIVE, 2f, 0.5f, activeTools[ToolType.PENCIL_CUMULATIVE]?.settings ?: PencilSettings(isCumulativeOpacity = true)))
        put(ToolType.FILL, loadToolConfig(ToolType.FILL, 1f, 1.0f, PencilSettings()))
        put(ToolType.ERASER, loadToolConfig(ToolType.ERASER, 10f, 1f, PencilSettings()))
        put(ToolType.POINT_ERASER, loadToolConfig(ToolType.POINT_ERASER, 10f, 1f, PencilSettings()))
        put(ToolType.CUT_ERASER, loadToolConfig(ToolType.CUT_ERASER, 10f, 1f, PencilSettings()))
        put(ToolType.SELECTION, loadToolConfig(ToolType.SELECTION, 1f, 1f, PencilSettings()))
        put(ToolType.TRIM, loadToolConfig(ToolType.TRIM, 1f, 1f, PencilSettings()))
        put(ToolType.EXTEND, loadToolConfig(ToolType.EXTEND, 1f, 1f, PencilSettings()))
        put(ToolType.EDIT_POINTS, loadToolConfig(ToolType.EDIT_POINTS, 1f, 1f, PencilSettings()))
    }

    init {
        val freehandConfig = toolConfigs[ToolType.FREEHAND]!!
        fingerModeActive = freehandConfig.isFingerMode
        fingerOffsetXValue = freehandConfig.fingerOffsetX
        fingerOffsetYValue = freehandConfig.fingerOffsetY
        _brushPresets.value = loadBrushPresetsForTool(currentTool)
        _fillPresets.value = loadFillPresets()
        selectTool(currentTool)
    }

    // --- LOGIC METHODS ---

    fun selectTool(type: ToolType) {
        if (type != ToolType.ERASER && type != ToolType.POINT_ERASER && type != ToolType.CUT_ERASER && type != ToolType.SELECTION && type != ToolType.FILL) {
            lastDrawingTool = type
        }
        currentTool = type
        prefs.edit().putString("current_tool", type.name).apply()
        
        val config = toolConfigs[type] ?: toolConfigs[ToolType.FREEHAND]!!
        val settings = activeTools[type]?.settings ?: com.sketcher.sketchercompanionv1.tools.PencilSettings()
        
        // Enforce tool-specific constraints for outer stroke flattening
        when (type) {
            ToolType.FREEHAND -> {
                isFlattenedOuterStrokeEnabled = true
            }
            ToolType.PENCIL_CUMULATIVE -> {
                isFlattenedOuterStrokeEnabled = false
            }
            ToolType.PAINT -> {
                isFlattenedOuterStrokeEnabled = true
            }
            ToolType.WATERCOLOR -> {
                isFlattenedOuterStrokeEnabled = true
            }
            ToolType.PEN -> {
                isFlattenedOuterStrokeEnabled = false
            }
            ToolType.PLUMA -> {
                isFlattenedOuterStrokeEnabled = false
            }
            else -> {}
        }
        
        currentSize = config.size
        _brushSize.value = config.size
        currentOpacity = config.opacity
        _brushOpacity.value = config.opacity
        currentFreehandSettings = settings
        currentEraserShape = config.eraserShape
        
        // Load presets for this specific tool first
        _brushPresets.value = loadBrushPresetsForTool(type)
        val presetIdx = lastPresetIndexPerTool[type] ?: 0
        val presetsList = _brushPresets.value
        val preset = if (presetIdx in presetsList.indices) presetsList[presetIdx] else null

        // Restore color/style states from tool config, respecting preset/explicit overrides
        if (getIsStrokeColorPreset(type, presetIdx) && preset != null) {
            val sc = preset.strokeColor ?: config.strokeColor
            _strokeColor.value = sc
            _strokeStyle.value = preset.strokeStyle ?: FillStyle.Solid(sc)
            _isStrokeActive.value = preset.isStrokeActive ?: config.isStrokeActive
        } else {
            val sc = getExplicitStrokeColor(type, presetIdx, config.strokeColor)
            _strokeColor.value = sc
            _strokeStyle.value = getExplicitStrokeStyle(type, presetIdx) ?: FillStyle.Solid(sc)
            _isStrokeActive.value = config.isStrokeActive
        }

        if (getIsFillColorPreset(type, presetIdx) && preset != null) {
            val fc = preset.fillColor ?: config.fillColor
            _fillColor.value = fc
            _fillStyle.value = preset.fillStyle ?: FillStyle.Solid(fc)
            _isFillActive.value = preset.isFillActive ?: config.isFillActive
        } else {
            val fc = getExplicitFillColor(type, presetIdx, config.fillColor)
            _fillColor.value = fc
            _fillStyle.value = getExplicitFillStyle(type, presetIdx) ?: FillStyle.Solid(fc)
            _isFillActive.value = config.isFillActive
        }

        // Restore stabilization!
        val targetStab = config.stabilization
        globalStabilizationLevel = targetStab
        _smoothing.value = targetStab

        toolConfigs[type] = config.copy(settings = settings)
        
        if (type == ToolType.WATERCOLOR && settings is com.sketcher.sketchercompanionv1.tools.WatercolorSettings && settings.linkStrokeToFill) {
            val sc = adjustColorBrightness(_fillColor.value, settings.strokeBrightnessOffset)
            _strokeColor.value = sc
            _strokeStyle.value = FillStyle.Solid(sc)
            _isStrokeActive.value = true
            setExplicitStrokeColorState(type, presetIdx, true, sc, FillStyle.Solid(sc))
            val conf = toolConfigs[type]!!
            val updatedConf = conf.copy(
                strokeColor = sc,
                strokeStyle = FillStyle.Solid(sc).toFillStyleJson(),
                isStrokeActive = true
            )
            toolConfigs[type] = updatedConf
            persistToolConfigColorState(type, updatedConf)
        }
        
        if (settings is com.sketcher.sketchercompanionv1.tools.PencilSettings && settings.isCumulativeOpacity) {
            isFlattenedOuterStrokeEnabled = false
        }

        _selectedPresetIndex.value = presetIdx
        updatePresetFlows()
    }

    fun setEraserShape(shape: EraserShape) {
        currentEraserShape = shape
        val config = toolConfigs[currentTool]!!
        toolConfigs[currentTool] = config.copy(eraserShape = shape)
        prefs.edit().putString("tool_eraser_shape_${currentTool.name}", shape.name).apply()
    }

    fun setToolSize(size: Float) {
        currentSize = size
        _brushSize.value = size
        val config = toolConfigs[currentTool]!!
        toolConfigs[currentTool] = config.copy(size = size)
        prefs.edit().putFloat("tool_size_${currentTool.name}", size).apply()
        
        activeCustomToolId?.let { customId ->
            saveActiveCustomToolChanges(customId)
        }
    }

    fun setToolOpacity(opacity: Float) {
        currentOpacity = opacity
        _brushOpacity.value = opacity
        val config = toolConfigs[currentTool]!!
        toolConfigs[currentTool] = config.copy(opacity = opacity)
        prefs.edit().putFloat("tool_alpha_${currentTool.name}", opacity).apply()
        
        activeCustomToolId?.let { customId ->
            saveActiveCustomToolChanges(customId)
        }
    }

    fun updateBrushSize(newSize: Float) = setToolSize(newSize)
    fun updateBrushOpacity(newAlpha: Float) = setToolOpacity(newAlpha)

    fun updateFillOpacity(opacity: Float) {
        _fillOpacity.value = opacity
    }

    private fun loadBrushPresetsForTool(type: ToolType): List<BrushPreset> {
        val prefKey = when(type) {
            ToolType.FREEHAND -> "pencil_presets_v2"
            ToolType.PENCIL_CUMULATIVE -> "pencil_cumulative_presets_v2"
            ToolType.PEN -> "pen_presets_v2"
            ToolType.PAINT -> "paint_presets_v2"
            ToolType.PLUMA -> "pluma_presets_v2"
            ToolType.WATERCOLOR -> "watercolor_presets_v2"
            else -> "brush_presets_v2"
        }
        val json = prefs.getString(prefKey, null)
        if (json != null) {
            try {
                val tokenType = object : com.google.gson.reflect.TypeToken<List<BrushPresetJson>>() {}.type
                val loaded: List<BrushPresetJson> = gson.fromJson(json, tokenType)
                if (loaded.size >= 5) {
                    return loaded.map { pJson ->
                        val settings = when (type) {
                            ToolType.FREEHAND -> gson.fromJson(pJson.settingsJson, PencilSettings::class.java)
                            ToolType.PEN -> gson.fromJson(pJson.settingsJson, PenSettings::class.java)
                            ToolType.PLUMA -> gson.fromJson(pJson.settingsJson, PlumaSettings::class.java)
                            ToolType.PAINT -> gson.fromJson(pJson.settingsJson, PaintSettings::class.java)
                            ToolType.WATERCOLOR -> gson.fromJson(pJson.settingsJson, WatercolorSettings::class.java)
                            ToolType.PENCIL_CUMULATIVE -> gson.fromJson(pJson.settingsJson, PencilSettings::class.java)
                            else -> PencilSettings()
                        }
                        BrushPreset(
                            size = pJson.size,
                            opacity = pJson.opacity,
                            settings = settings,
                            strokeColor = pJson.strokeColor,
                            fillColor = pJson.fillColor,
                            isStrokeActive = pJson.isStrokeActive,
                            isFillActive = pJson.isFillActive,
                            fillStyle = pJson.fillStyle?.toFillStyle(pJson.fillColor ?: 0),
                            strokeStyle = pJson.strokeStyle?.toFillStyle(pJson.strokeColor ?: 0),
                            stabilization = pJson.stabilization
                        )
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return getDefaultPresetsForTool(type)
    }

    private fun saveBrushPresetsForTool(type: ToolType, list: List<BrushPreset>) {
        val prefKey = when(type) {
            ToolType.FREEHAND -> "pencil_presets_v2"
            ToolType.PENCIL_CUMULATIVE -> "pencil_cumulative_presets_v2"
            ToolType.PEN -> "pen_presets_v2"
            ToolType.PAINT -> "paint_presets_v2"
            ToolType.PLUMA -> "pluma_presets_v2"
            ToolType.WATERCOLOR -> "watercolor_presets_v2"
            else -> "brush_presets_v2"
        }
        val jsonList = list.map { preset ->
            BrushPresetJson(
                size = preset.size,
                opacity = preset.opacity,
                settingsType = preset.settings::class.java.simpleName,
                settingsJson = gson.toJson(preset.settings),
                strokeColor = preset.strokeColor,
                fillColor = preset.fillColor,
                isStrokeActive = preset.isStrokeActive,
                isFillActive = preset.isFillActive,
                fillStyle = preset.fillStyle?.toFillStyleJson(),
                strokeStyle = preset.strokeStyle?.toFillStyleJson(),
                stabilization = preset.stabilization
            )
        }
        val json = gson.toJson(jsonList)
        prefs.edit().putString(prefKey, json).apply()
    }

    private fun getDefaultPresetsForTool(type: ToolType): List<BrushPreset> {
        return when(type) {
            ToolType.PENCIL_CUMULATIVE -> listOf(
                BrushPreset(size = 2f, opacity = 0.3f, settings = PencilSettings(thinning = 0.4f, smoothing = 0.3f, isCumulativeOpacity = true), strokeColor = AndroidColor.BLACK, fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.BLACK), stabilization = 0.07f),
                BrushPreset(size = 5f, opacity = 0.4f, settings = PencilSettings(thinning = 0.5f, smoothing = 0.4f, isCumulativeOpacity = true), strokeColor = AndroidColor.DKGRAY, fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.DKGRAY), stabilization = 0.07f),
                BrushPreset(size = 12f, opacity = 0.5f, settings = PencilSettings(thinning = 0.6f, smoothing = 0.5f, isCumulativeOpacity = true), strokeColor = AndroidColor.rgb(233, 30, 99), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(233, 30, 99)), stabilization = 0.07f),
                BrushPreset(size = 20f, opacity = 0.6f, settings = PencilSettings(thinning = 0.7f, smoothing = 0.6f, simulatePressure = true, start = com.sketcher.sketchercompanionv1.dto.StrokeEndOptions(taperEnabled = true, customTaper = 20f), end = com.sketcher.sketchercompanionv1.dto.StrokeEndOptions(taperEnabled = true, customTaper = 20f), isCumulativeOpacity = true), strokeColor = AndroidColor.rgb(33, 150, 243), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(33, 150, 243)), stabilization = 0.15f),
                BrushPreset(size = 35f, opacity = 0.4f, settings = PencilSettings(thinning = 0f, smoothing = 0.4f, isCumulativeOpacity = true), strokeColor = AndroidColor.rgb(76, 175, 80), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(76, 175, 80)), stabilization = 0.07f)
            )
            ToolType.PEN -> listOf(
                BrushPreset(size = 1f, opacity = 1f, settings = PenSettings(smoothing = 0f), strokeColor = AndroidColor.BLACK, fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.BLACK), stabilization = 0f),
                BrushPreset(size = 2f, opacity = 1f, settings = PenSettings(smoothing = 0f), strokeColor = AndroidColor.rgb(26, 35, 126), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(26, 35, 126)), stabilization = 0f),
                BrushPreset(size = 4f, opacity = 1f, settings = PenSettings(smoothing = 0f), strokeColor = AndroidColor.rgb(183, 28, 28), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(183, 28, 28)), stabilization = 0f),
                BrushPreset(size = 8f, opacity = 1f, settings = PenSettings(smoothing = 0f), strokeColor = AndroidColor.rgb(76, 175, 80), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(76, 175, 80)), stabilization = 0f),
                BrushPreset(size = 15f, opacity = 1f, settings = PenSettings(smoothing = 0f), strokeColor = AndroidColor.rgb(156, 39, 176), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(156, 39, 176)), stabilization = 0f)
            )
            ToolType.PLUMA -> listOf(
                BrushPreset(size = 1.5f, opacity = 1f, settings = PlumaSettings(thinning = 0.1f, smoothing = 0.5f), strokeColor = AndroidColor.BLACK, fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.BLACK), stabilization = 0.3f),
                BrushPreset(size = 3f, opacity = 1f, settings = PlumaSettings(thinning = 0.2f, smoothing = 0.5f), strokeColor = AndroidColor.rgb(121, 85, 72), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(121, 85, 72)), stabilization = 0.3f),
                BrushPreset(size = 6f, opacity = 1f, settings = PlumaSettings(thinning = 0.3f, smoothing = 0.5f), strokeColor = AndroidColor.rgb(26, 35, 126), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(26, 35, 126)), stabilization = 0.4f),
                BrushPreset(size = 10f, opacity = 1f, settings = PlumaSettings(thinning = 0.4f, smoothing = 0.5f), strokeColor = AndroidColor.rgb(183, 28, 28), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(183, 28, 28)), stabilization = 0.5f),
                BrushPreset(size = 18f, opacity = 1f, settings = PlumaSettings(thinning = 0.5f, smoothing = 0.5f), strokeColor = AndroidColor.rgb(255, 152, 0), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(255, 152, 0)), stabilization = 0.6f)
            )
            ToolType.PAINT -> listOf(
                BrushPreset(size = 8f, opacity = 1f, settings = PaintSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 1.5f), strokeColor = AndroidColor.rgb(233, 30, 99), fillColor = AndroidColor.rgb(233, 30, 99), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(233, 30, 99)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(233, 30, 99)), stabilization = 0.1f),
                BrushPreset(size = 15f, opacity = 0.8f, settings = PaintSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 2.0f), strokeColor = AndroidColor.rgb(33, 150, 243), fillColor = AndroidColor.rgb(33, 150, 243), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(33, 150, 243)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(33, 150, 243)), stabilization = 0.15f),
                BrushPreset(size = 25f, opacity = 0.6f, settings = PaintSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 3.0f), strokeColor = AndroidColor.rgb(255, 235, 59), fillColor = AndroidColor.rgb(255, 235, 59), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(255, 235, 59)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(255, 235, 59)), stabilization = 0.2f),
                BrushPreset(size = 40f, opacity = 0.4f, settings = PaintSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 4.0f), strokeColor = AndroidColor.rgb(76, 175, 80), fillColor = AndroidColor.rgb(76, 175, 80), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(76, 175, 80)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(76, 175, 80)), stabilization = 0.25f),
                BrushPreset(size = 60f, opacity = 0.2f, settings = PaintSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 5.0f), strokeColor = AndroidColor.rgb(156, 39, 176), fillColor = AndroidColor.rgb(156, 39, 176), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(156, 39, 176)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(156, 39, 176)), stabilization = 0.3f)
            )
            ToolType.WATERCOLOR -> listOf(
                BrushPreset(size = 15f, opacity = 0.3f, settings = WatercolorSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 2.0f, watercolorJitterSegment = 10f, watercolorJitterDeviation = 3f, watercolorBlurRadius = 4f, watercolorEdgeMode = WatercolorEdgeMode.BOTH, watercolorCenterOpacity = 0.8f, watercolorEdgeRingOpacity = 0.5f, watercolorEdgeRingWidth = 1f), strokeColor = AndroidColor.rgb(0, 188, 212), fillColor = AndroidColor.rgb(0, 188, 212), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(0, 188, 212)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(0, 188, 212)), stabilization = 0.2f),
                BrushPreset(size = 25f, opacity = 0.35f, settings = WatercolorSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 2.5f, watercolorJitterSegment = 12f, watercolorJitterDeviation = 4f, watercolorBlurRadius = 6f, watercolorEdgeMode = WatercolorEdgeMode.BOTH, watercolorCenterOpacity = 0.6f, watercolorEdgeRingOpacity = 0.8f, watercolorEdgeRingWidth = 1.5f), strokeColor = AndroidColor.rgb(232, 30, 99), fillColor = AndroidColor.rgb(232, 30, 99), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(232, 30, 99)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(232, 30, 99)), stabilization = 0.25f),
                BrushPreset(size = 40f, opacity = 0.4f, settings = WatercolorSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 3.0f, watercolorJitterSegment = 15f, watercolorJitterDeviation = 5f, watercolorBlurRadius = 8f, watercolorEdgeMode = WatercolorEdgeMode.BOTH, watercolorCenterOpacity = 0.4f, watercolorEdgeRingOpacity = 1.0f, watercolorEdgeRingWidth = 2.0f), strokeColor = AndroidColor.rgb(255, 235, 59), fillColor = AndroidColor.rgb(255, 235, 59), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(255, 235, 59)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(255, 235, 59)), stabilization = 0.3f),
                BrushPreset(size = 60f, opacity = 0.3f, settings = WatercolorSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 4.0f, watercolorJitterSegment = 18f, watercolorJitterDeviation = 6f, watercolorBlurRadius = 10f, watercolorEdgeMode = WatercolorEdgeMode.BOTH, watercolorCenterOpacity = 0.2f, watercolorEdgeRingOpacity = 1.0f, watercolorEdgeRingWidth = 2.5f), strokeColor = AndroidColor.rgb(255, 152, 0), fillColor = AndroidColor.rgb(255, 152, 0), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(255, 152, 0)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(255, 152, 0)), stabilization = 0.35f),
                BrushPreset(size = 80f, opacity = 0.25f, settings = WatercolorSettings(thinning = 0.5f, smoothing = 0.5f, paintOutlineWidth = 5.0f, watercolorJitterSegment = 20f, watercolorJitterDeviation = 7f, watercolorBlurRadius = 12f, watercolorEdgeMode = WatercolorEdgeMode.BOTH, watercolorCenterOpacity = 0.8f, watercolorEdgeRingOpacity = 0f, watercolorEdgeRingWidth = 0f), strokeColor = AndroidColor.rgb(233, 30, 99), fillColor = AndroidColor.rgb(233, 30, 99), isStrokeActive = true, isFillActive = true, fillStyle = FillStyle.Solid(AndroidColor.rgb(233, 30, 99)), strokeStyle = FillStyle.Solid(AndroidColor.rgb(233, 30, 99)), stabilization = 0.4f)
            )
            else -> listOf( // FREEHAND / Pencil
                BrushPreset(size = 2f, opacity = 1f, settings = PencilSettings(thinning = 0.4f, smoothing = 0.3f), strokeColor = AndroidColor.BLACK, fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.BLACK), stabilization = 0.07f),
                BrushPreset(size = 5f, opacity = 1f, settings = PencilSettings(thinning = 0.5f, smoothing = 0.4f), strokeColor = AndroidColor.DKGRAY, fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.DKGRAY), stabilization = 0.07f),
                BrushPreset(size = 12f, opacity = 1f, settings = PencilSettings(thinning = 0.6f, smoothing = 0.5f), strokeColor = AndroidColor.rgb(233, 30, 99), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(233, 30, 99)), stabilization = 0.07f),
                BrushPreset(size = 20f, opacity = 0.8f, settings = PencilSettings(thinning = 0.7f, smoothing = 0.6f, simulatePressure = true, start = com.sketcher.sketchercompanionv1.dto.StrokeEndOptions(taperEnabled = true, customTaper = 20f), end = com.sketcher.sketchercompanionv1.dto.StrokeEndOptions(taperEnabled = true, customTaper = 20f)), strokeColor = AndroidColor.rgb(33, 150, 243), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(33, 150, 243)), stabilization = 0.15f),
                BrushPreset(size = 35f, opacity = 0.4f, settings = PencilSettings(thinning = 0f, smoothing = 0.4f), strokeColor = AndroidColor.rgb(76, 175, 80), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(76, 175, 80)), stabilization = 0.07f)
            )
        }
    }

    fun saveBrushPreset(index: Int) {
        val currentPreset = BrushPreset(
            size = currentSize,
            opacity = currentOpacity,
            settings = currentFreehandSettings,
            strokeColor = _strokeColor.value,
            fillColor = _fillColor.value,
            isStrokeActive = _isStrokeActive.value,
            isFillActive = _isFillActive.value,
            fillStyle = _fillStyle.value,
            strokeStyle = _strokeStyle.value,
            stabilization = globalStabilizationLevel
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
            updateFreehandSettings(preset.settings)
            
            // Save last selected preset index for this tool!
            lastPresetIndexPerTool[currentTool] = index
            prefs.edit().putInt("last_preset_index_${currentTool.name}", index).apply()

            // Resolve Stroke Color & Style
            val sc: Int
            val ss: FillStyle
            val sa: Boolean
            if (getIsStrokeColorPreset(currentTool, index)) {
                sc = preset.strokeColor ?: _strokeColor.value
                ss = preset.strokeStyle ?: FillStyle.Solid(sc)
                sa = preset.isStrokeActive ?: _isStrokeActive.value
            } else {
                sc = getExplicitStrokeColor(currentTool, index, preset.strokeColor ?: _strokeColor.value)
                ss = getExplicitStrokeStyle(currentTool, index) ?: FillStyle.Solid(sc)
                sa = _isStrokeActive.value
            }

            // Resolve Fill Color & Style
            val fc: Int
            val fs: FillStyle
            val fa: Boolean
            if (getIsFillColorPreset(currentTool, index)) {
                fc = preset.fillColor ?: _fillColor.value
                fs = preset.fillStyle ?: FillStyle.Solid(fc)
                fa = preset.isFillActive ?: _isFillActive.value
            } else {
                fc = getExplicitFillColor(currentTool, index, preset.fillColor ?: _fillColor.value)
                fs = getExplicitFillStyle(currentTool, index) ?: FillStyle.Solid(fc)
                fa = _isFillActive.value
            }

            val defaultStab = if (currentTool == ToolType.FREEHAND || currentTool == ToolType.PENCIL_CUMULATIVE) 0.07f else 0f
            val stab = preset.stabilization ?: defaultStab

            val isLinked = currentTool == ToolType.WATERCOLOR && (preset.settings as? com.sketcher.sketchercompanionv1.tools.WatercolorSettings)?.linkStrokeToFill == true
            val finalSc = if (isLinked) adjustColorBrightness(fc, (preset.settings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings).strokeBrightnessOffset) else sc
            val finalSs = if (isLinked) FillStyle.Solid(finalSc) else ss
            val finalSa = if (isLinked) true else sa

            _strokeColor.value = finalSc
            _fillColor.value = fc
            _isStrokeActive.value = finalSa
            _isFillActive.value = fa
            _fillStyle.value = fs
            _strokeStyle.value = finalSs
            setGlobalStabilization(stab)

            // Update and persist the tool config with the preset values!
            val config = toolConfigs[currentTool]!!
            val updated = config.copy(
                size = preset.size,
                opacity = preset.opacity,
                strokeColor = finalSc,
                fillColor = fc,
                isStrokeActive = finalSa,
                isFillActive = fa,
                fillStyle = fs.toFillStyleJson(),
                strokeStyle = finalSs.toFillStyleJson(),
                stabilization = stab
            )
            toolConfigs[currentTool] = updated
            persistToolConfigColorState(currentTool, updated)

            _selectedPresetIndex.value = index
            updatePresetFlows()
        }
    }

    fun restoreStabilizationToPreset() {
        val index = _selectedPresetIndex.value
        val list = _brushPresets.value
        val defaultStab = if (currentTool == ToolType.FREEHAND || currentTool == ToolType.PENCIL_CUMULATIVE) 0.07f else 0f
        val presetStab = if (index != null && index in list.indices) {
            list[index].stabilization ?: defaultStab
        } else if (list.isNotEmpty()) {
            list[0].stabilization ?: defaultStab
        } else {
            defaultStab
        }
        setGlobalStabilization(presetStab)
    }

    fun restoreOpacityToPreset() {
        val index = _selectedPresetIndex.value
        val list = _brushPresets.value
        val defaultOpacity = 1.0f
        val presetOpacity = if (index != null && index in list.indices) {
            list[index].opacity
        } else if (list.isNotEmpty()) {
            list[0].opacity
        } else {
            defaultOpacity
        }
        setToolOpacity(presetOpacity)
    }

    fun revertBrushPreset(index: Int) {
        val list = _brushPresets.value
        if (index in list.indices) {
            val preset = list[index]
            setToolSize(preset.size)
            setToolOpacity(preset.opacity)
            updateFreehandSettings(preset.settings)
            
            val editor = prefs.edit()
            editor.remove("preset_stroke_explicit_${currentTool.name}_$index")
            editor.remove("preset_fill_explicit_${currentTool.name}_$index")
            editor.remove("preset_stroke_color_${currentTool.name}_$index")
            editor.remove("preset_fill_color_${currentTool.name}_$index")
            editor.remove("preset_stroke_style_${currentTool.name}_$index")
            editor.remove("preset_fill_style_${currentTool.name}_$index")
            editor.remove("preset_stroke_active_${currentTool.name}_$index")
            editor.remove("preset_fill_active_${currentTool.name}_$index")
            editor.apply()

            _strokeColor.value = preset.strokeColor ?: _strokeColor.value
            _strokeStyle.value = preset.strokeStyle ?: FillStyle.Solid(_strokeColor.value)
            _isStrokeActive.value = preset.isStrokeActive ?: _isStrokeActive.value

            _fillColor.value = preset.fillColor ?: _fillColor.value
            _fillStyle.value = preset.fillStyle ?: FillStyle.Solid(_fillColor.value)
            _isFillActive.value = preset.isFillActive ?: _isFillActive.value

            if (preset.stabilization != null) {
                setGlobalStabilization(preset.stabilization)
            }
            
            selectBrushPreset(index)
        }
    }

    fun isPresetModified(index: Int): Boolean {
        val list = _brushPresets.value
        if (index !in list.indices) return false
        val preset = list[index]
        return _brushSize.value != preset.size ||
               _brushOpacity.value != preset.opacity ||
               currentFreehandSettings != preset.settings ||
               (preset.strokeColor != null && _strokeColor.value != preset.strokeColor) ||
               (preset.fillColor != null && _fillColor.value != preset.fillColor) ||
               (preset.isStrokeActive != null && _isStrokeActive.value != preset.isStrokeActive) ||
               (preset.isFillActive != null && _isFillActive.value != preset.isFillActive) ||
               (preset.fillStyle != null && _fillStyle.value != preset.fillStyle) ||
               (preset.strokeStyle != null && _strokeStyle.value != preset.strokeStyle) ||
               (preset.stabilization != null && globalStabilizationLevel != preset.stabilization)
    }

    fun updateStrokeType(type: StrokeType) {
        currentStrokeType = type
        prefs.edit().putString("current_stroke_type", type.name).apply()
    }

    private fun adjustColorBrightness(color: Int, brightnessOffset: Float): Int {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(color, hsv)
        hsv[2] = (hsv[2] + brightnessOffset).coerceIn(0f, 1f)
        val alpha = android.graphics.Color.alpha(color)
        val rgb = android.graphics.Color.HSVToColor(hsv)
        return (rgb and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun getUpdatedStyleWithColor(currentStyle: FillStyle, color: Int): FillStyle {
        return when (currentStyle) {
            is FillStyle.ImageTexture -> currentStyle.copy(
                tintColor = color,
                tintMix = if (currentStyle.tintMix == 0f) 1f else currentStyle.tintMix
            )
            is FillStyle.MathTexture -> currentStyle.copy(primaryColor = color)
            is FillStyle.SvgPattern -> currentStyle
            is FillStyle.Solid -> currentStyle.copy(color = color)
        }
    }

    fun setStrokeColor(color: Int) {
        _strokeColor.value = color
        val newStyle = getUpdatedStyleWithColor(_strokeStyle.value, color)
        _strokeStyle.value = newStyle
        _isStrokeActive.value = true
        val isPaintOrWatercolor = (currentTool == ToolType.PAINT || currentTool == ToolType.WATERCOLOR)
        if (isPaintOrWatercolor) {
            _fillColor.value = color
            _fillStyle.value = getUpdatedStyleWithColor(_fillStyle.value, color)
        }
        
        val idx = _selectedPresetIndex.value ?: 0
        setExplicitStrokeColorState(currentTool, idx, true, color, newStyle)
        if (isPaintOrWatercolor) {
            setExplicitFillColorState(currentTool, idx, true, color, _fillStyle.value)
        }

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            strokeColor = color,
            strokeStyle = newStyle.toFillStyleJson(),
            isStrokeActive = true,
            fillColor = if (isPaintOrWatercolor) color else config.fillColor,
            fillStyle = if (isPaintOrWatercolor) _fillStyle.value.toFillStyleJson() else config.fillStyle,
            isFillActive = if (isPaintOrWatercolor) true else config.isFillActive
        )
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
    }

    fun setStrokeStyle(style: FillStyle) {
        _strokeStyle.value = style
        _isStrokeActive.value = true
        val color = when (style) {
            is FillStyle.Solid -> style.color
            is FillStyle.MathTexture -> style.primaryColor
            else -> {
                val alpha = (style.opacity * 255f).toInt().coerceIn(0, 255)
                alpha shl 24
            }
        }
        _strokeColor.value = color
        val isPaintOrWatercolor = (currentTool == ToolType.PAINT || currentTool == ToolType.WATERCOLOR)
        if (isPaintOrWatercolor) {
            _fillColor.value = color
            _fillStyle.value = style
        }
        
        val idx = _selectedPresetIndex.value ?: 0
        setExplicitStrokeColorState(currentTool, idx, true, color, style)
        if (isPaintOrWatercolor) {
            setExplicitFillColorState(currentTool, idx, true, color, style)
        }

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            strokeColor = color,
            strokeStyle = style.toFillStyleJson(),
            isStrokeActive = true,
            fillColor = if (isPaintOrWatercolor) color else config.fillColor,
            fillStyle = if (isPaintOrWatercolor) style.toFillStyleJson() else config.fillStyle,
            isFillActive = if (isPaintOrWatercolor) true else config.isFillActive
        )
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
    }

    fun setFillColor(color: Int) {
        _fillColor.value = color
        val newStyle = getUpdatedStyleWithColor(_fillStyle.value, color)
        _fillStyle.value = newStyle
        _isFillActive.value = true
        
        val idx = _selectedPresetIndex.value ?: 0
        setExplicitFillColorState(currentTool, idx, true, color, newStyle)

        var finalStrokeColor = _strokeColor.value
        var finalStrokeStyle = _strokeStyle.value
        var finalStrokeActive = _isStrokeActive.value

        val settings = currentFreehandSettings as? com.sketcher.sketchercompanionv1.tools.WatercolorSettings
        if (currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true) {
            finalStrokeColor = adjustColorBrightness(color, settings.strokeBrightnessOffset)
            finalStrokeStyle = getUpdatedStyleWithColor(_strokeStyle.value, finalStrokeColor)
            finalStrokeActive = true
            _strokeColor.value = finalStrokeColor
            _strokeStyle.value = finalStrokeStyle
            _isStrokeActive.value = true
            setExplicitStrokeColorState(currentTool, idx, true, finalStrokeColor, finalStrokeStyle)
        }

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            fillColor = color,
            fillStyle = newStyle.toFillStyleJson(),
            isFillActive = true,
            strokeColor = if (currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true) finalStrokeColor else config.strokeColor,
            strokeStyle = if (currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true) finalStrokeStyle.toFillStyleJson() else config.strokeStyle,
            isStrokeActive = if (currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true) finalStrokeActive else config.isStrokeActive
        )
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
    }

    fun setFillStyle(style: FillStyle) {
        _fillStyle.value = style
        _isFillActive.value = true
        val color = when (style) {
            is FillStyle.Solid -> style.color
            is FillStyle.MathTexture -> style.primaryColor
            else -> {
                val alpha = (style.opacity * 255f).toInt().coerceIn(0, 255)
                alpha shl 24
            }
        }
        _fillColor.value = color
        
        val idx = _selectedPresetIndex.value ?: 0
        setExplicitFillColorState(currentTool, idx, true, color, style)

        var finalStrokeColor = _strokeColor.value
        var finalStrokeStyle = _strokeStyle.value
        var finalStrokeActive = _isStrokeActive.value

        val settings = currentFreehandSettings as? com.sketcher.sketchercompanionv1.tools.WatercolorSettings
        if (currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true) {
            finalStrokeColor = adjustColorBrightness(color, settings.strokeBrightnessOffset)
            finalStrokeStyle = FillStyle.Solid(finalStrokeColor)
            finalStrokeActive = true
            _strokeColor.value = finalStrokeColor
            _strokeStyle.value = finalStrokeStyle
            _isStrokeActive.value = true
            setExplicitStrokeColorState(currentTool, idx, true, finalStrokeColor, finalStrokeStyle)
        }

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            fillColor = color,
            fillStyle = style.toFillStyleJson(),
            isFillActive = true,
            strokeColor = if (currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true) finalStrokeColor else config.strokeColor,
            strokeStyle = if (currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true) finalStrokeStyle.toFillStyleJson() else config.strokeStyle,
            isStrokeActive = if (currentTool == ToolType.WATERCOLOR && settings?.linkStrokeToFill == true) finalStrokeActive else config.isStrokeActive
        )
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
    }

    fun toggleStroke(enabled: Boolean) { 
        _isStrokeActive.value = enabled
        val config = toolConfigs[currentTool]!!
        val updated = config.copy(isStrokeActive = enabled)
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
        updatePresetFlows()
    }

    fun toggleFill(enabled: Boolean) { 
        _isFillActive.value = enabled
        val config = toolConfigs[currentTool]!!
        val updated = config.copy(isFillActive = enabled)
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
        updatePresetFlows()
    }

    fun setGlobalStabilization(level: Float) {
        val clamped = level.coerceIn(0f, 1f)
        if (globalStabilizationLevel != clamped) {
            globalStabilizationLevel = clamped
            _smoothing.value = clamped
            prefs.edit().putFloat("global_stabilization", clamped).apply()
        }
        
        val config = toolConfigs[currentTool]
        if (config != null && config.stabilization != clamped) {
            val updated = config.copy(stabilization = clamped)
            toolConfigs[currentTool] = updated
            persistToolConfigColorState(currentTool, updated)
        }
    }

    fun updateSmoothing(value: Float) = setGlobalStabilization(value)


    fun updateFreehandSettings(settings: com.sketcher.sketchercompanionv1.tools.ToolSettings) {
        val tool = activeTools[currentTool]
        if (tool != null) {
            tool.settings = settings
        }
        currentFreehandSettings = settings
        
        var finalStrokeColor = _strokeColor.value
        var finalStrokeStyle = _strokeStyle.value
        var finalStrokeActive = _isStrokeActive.value

        if (currentTool == ToolType.WATERCOLOR && settings is com.sketcher.sketchercompanionv1.tools.WatercolorSettings && settings.linkStrokeToFill) {
            finalStrokeColor = adjustColorBrightness(_fillColor.value, settings.strokeBrightnessOffset)
            finalStrokeStyle = FillStyle.Solid(finalStrokeColor)
            finalStrokeActive = true
            _strokeColor.value = finalStrokeColor
            _strokeStyle.value = finalStrokeStyle
            _isStrokeActive.value = true
            val idx = _selectedPresetIndex.value ?: 0
            setExplicitStrokeColorState(currentTool, idx, true, finalStrokeColor, finalStrokeStyle)
        }

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            settings = settings,
            strokeColor = if (currentTool == ToolType.WATERCOLOR && settings is com.sketcher.sketchercompanionv1.tools.WatercolorSettings && settings.linkStrokeToFill) finalStrokeColor else config.strokeColor,
            strokeStyle = if (currentTool == ToolType.WATERCOLOR && settings is com.sketcher.sketchercompanionv1.tools.WatercolorSettings && settings.linkStrokeToFill) finalStrokeStyle.toFillStyleJson() else config.strokeStyle,
            isStrokeActive = if (currentTool == ToolType.WATERCOLOR && settings is com.sketcher.sketchercompanionv1.tools.WatercolorSettings && settings.linkStrokeToFill) finalStrokeActive else config.isStrokeActive
        )
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
        when (currentTool) {
            ToolType.PEN -> savePenSettings(settings as com.sketcher.sketchercompanionv1.tools.PenSettings)
            ToolType.FREEHAND -> saveFreehandSettings(settings as com.sketcher.sketchercompanionv1.tools.PencilSettings)
            ToolType.PAINT -> savePaintSettings(settings as com.sketcher.sketchercompanionv1.tools.PaintSettings)
            ToolType.PLUMA -> savePlumaSettings(settings as com.sketcher.sketchercompanionv1.tools.PlumaSettings)
            ToolType.WATERCOLOR -> saveWatercolorSettings(settings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings)
            ToolType.PENCIL_CUMULATIVE -> savePencilCumulativeSettings(settings as com.sketcher.sketchercompanionv1.tools.PencilSettings)
            else -> {}
        }
    }

    fun saveActiveCustomToolChanges(customId: String) {
        val mapped = _customTools.value.map { ct ->
            if (ct.id == customId) {
                val sc = _strokeColor.value
                val fc = _fillColor.value
                val sa = _isStrokeActive.value
                val fa = _isFillActive.value
                val ss = _strokeStyle.value
                val fs = _fillStyle.value
                val stab = smoothing.value
                val currentFreehandSettings = activeTools[currentTool]?.settings ?: com.sketcher.sketchercompanionv1.tools.PencilSettings()
                
                // Ensure the settings object has the correct size and opacity before saving
                val updatedSettings = when (currentFreehandSettings) {
                    is com.sketcher.sketchercompanionv1.tools.PencilSettings -> currentFreehandSettings.copy(size = _brushSize.value, opacity = _brushOpacity.value)
                    is com.sketcher.sketchercompanionv1.tools.PenSettings -> currentFreehandSettings.copy(size = _brushSize.value, opacity = _brushOpacity.value)
                    is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> currentFreehandSettings.copy(size = _brushSize.value, opacity = _brushOpacity.value)
                    is com.sketcher.sketchercompanionv1.tools.PaintSettings -> currentFreehandSettings.copy(size = _brushSize.value, opacity = _brushOpacity.value)
                    is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> currentFreehandSettings.copy(size = _brushSize.value, opacity = _brushOpacity.value)
                    else -> currentFreehandSettings
                }
                
                ct.copy(
                    preset = BrushPreset(
                        size = _brushSize.value,
                        opacity = _brushOpacity.value,
                        settings = updatedSettings,
                        strokeColor = sc,
                        fillColor = fc,
                        isStrokeActive = sa,
                        isFillActive = fa,
                        fillStyle = fs,
                        strokeStyle = ss,
                        stabilization = stab
                    )
                )
            } else {
                ct
            }
        }
    // updateRegistryCustomTools(mapped)
    // saveCustomTools(mapped)
    }

    fun isCustomToolModified(customId: String): Boolean {
        val ct = _customTools.value.find { it.id == customId } ?: return false
        val config = toolConfigs[currentTool] ?: return false
        val preset = ct.preset
        
        if (config.size != preset.size) return true
        if (config.opacity != preset.opacity) return true
        if (currentFreehandSettings != preset.settings) return true
        if (smoothing.value != (preset.stabilization ?: 0f)) return true
        if (_strokeColor.value != (preset.strokeColor ?: 0)) return true
        if (_fillColor.value != (preset.fillColor ?: 0)) return true
        if (_isStrokeActive.value != (preset.isStrokeActive ?: true)) return true
        if (_isFillActive.value != (preset.isFillActive ?: false)) return true
        
        val fsJson = config.fillStyle?.let { gson.toJson(it) }
        val ssJson = config.strokeStyle?.let { gson.toJson(it) }
        val presetFsJson = preset.fillStyle?.let { gson.toJson(it.toFillStyleJson()) }
        val presetSsJson = preset.strokeStyle?.let { gson.toJson(it.toFillStyleJson()) }
        if (fsJson != presetFsJson) return true
        if (ssJson != presetSsJson) return true
        
        return false
    }

    fun revertCustomToolChanges(customId: String) {
        val ct = _customTools.value.find { it.id == customId } ?: return
        applyBrushPresetDirectly(ct.preset)
    }

    fun reloadToolConfigAndSettings(type: ToolType) {
        val reloadedSettings = when(type) {
            ToolType.FREEHAND -> loadFreehandSettings()
            ToolType.PEN -> loadPenSettings()
            ToolType.PLUMA -> loadPlumaSettings()
            ToolType.PAINT -> loadPaintSettings()
            ToolType.WATERCOLOR -> loadWatercolorSettings()
            ToolType.PENCIL_CUMULATIVE -> loadPencilCumulativeSettings()
            else -> null
        }
        if (reloadedSettings != null) {
            val tool = activeTools[type]
            if (tool != null) {
                tool.settings = reloadedSettings
            }
        }
        
        val defSize = when(type) {
            ToolType.PAINT -> 10f
            ToolType.WATERCOLOR -> 20f
            ToolType.FREEHAND -> 2f
            ToolType.PEN -> 2f
            ToolType.PLUMA -> 2.5f
            ToolType.PENCIL_CUMULATIVE -> 2f
            else -> 2f
        }
        val defOpacity = when(type) {
            ToolType.WATERCOLOR -> 0.4f
            ToolType.PENCIL_CUMULATIVE -> 0.5f
            else -> 1f
        }
        val currentSettings = activeTools[type]?.settings ?: com.sketcher.sketchercompanionv1.tools.PencilSettings()
        toolConfigs[type] = loadToolConfig(type, defSize, defOpacity, currentSettings)
    }

    fun setFingerMode(enabled: Boolean) {
        fingerModeActive = enabled
        toolConfigs.keys.toList().forEach { type ->
            toolConfigs[type]?.let { config ->
                toolConfigs[type] = config.copy(isFingerMode = enabled)
            }
        }
    }

    fun setFingerOffset(x: Float, y: Float) {
        fingerOffsetXValue = x
        fingerOffsetYValue = y
        toolConfigs.keys.toList().forEach { type ->
            toolConfigs[type]?.let { config ->
                toolConfigs[type] = config.copy(fingerOffsetX = x, fingerOffsetY = y)
            }
        }
    }

    private fun loadFreehandSettings(): com.sketcher.sketchercompanionv1.tools.PencilSettings {
        val json = prefs.getString("freehand_settings_v4", null) ?: return com.sketcher.sketchercompanionv1.tools.PencilSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PencilSettings() }
    }

    private fun saveFreehandSettings(settings: com.sketcher.sketchercompanionv1.tools.PencilSettings) {
        if (!isProjectOpen) {
            val json = gson.toJson(settings)
            prefs.edit().putString("freehand_settings_v4", json).apply()
        }
    }

    private fun loadPaintSettings(): com.sketcher.sketchercompanionv1.tools.PaintSettings {
        val json = prefs.getString("paint_settings_v4", null) ?: return com.sketcher.sketchercompanionv1.tools.PaintSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PaintSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PaintSettings() }
    }

    private fun savePaintSettings(settings: com.sketcher.sketchercompanionv1.tools.PaintSettings) {
        if (!isProjectOpen) {
            val json = gson.toJson(settings)
            prefs.edit().putString("paint_settings_v4", json).apply()
        }
    }

    private fun loadPenSettings(): com.sketcher.sketchercompanionv1.tools.PenSettings {
        val json = prefs.getString("pen_settings_v4", null) ?: return com.sketcher.sketchercompanionv1.tools.PenSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PenSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PenSettings() }
    }

    private fun savePenSettings(settings: com.sketcher.sketchercompanionv1.tools.PenSettings) {
        if (!isProjectOpen) {
            val json = gson.toJson(settings)
            prefs.edit().putString("pen_settings_v4", json).apply()
        }
    }

    private fun loadPlumaSettings(): com.sketcher.sketchercompanionv1.tools.PlumaSettings {
        val json = prefs.getString("pluma_settings_v4", null) ?: return com.sketcher.sketchercompanionv1.tools.PlumaSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PlumaSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PlumaSettings() }
    }

    private fun savePlumaSettings(settings: com.sketcher.sketchercompanionv1.tools.PlumaSettings) {
        if (!isProjectOpen) {
            val json = gson.toJson(settings)
            prefs.edit().putString("pluma_settings_v4", json).apply()
        }
    }

    private fun loadPencilCumulativeSettings(): com.sketcher.sketchercompanionv1.tools.PencilSettings {
        val json = prefs.getString("pencil_cumulative_settings_v2", null) ?: return com.sketcher.sketchercompanionv1.tools.PencilSettings(isCumulativeOpacity = true)
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PencilSettings(isCumulativeOpacity = true) }
    }

    private fun savePencilCumulativeSettings(settings: com.sketcher.sketchercompanionv1.tools.PencilSettings) {
        if (!isProjectOpen) {
            val json = gson.toJson(settings)
            prefs.edit().putString("pencil_cumulative_settings_v2", json).apply()
        }
    }

    private fun loadWatercolorSettings(): com.sketcher.sketchercompanionv1.tools.WatercolorSettings {
        val json = prefs.getString("watercolor_settings_v2", null) ?: return com.sketcher.sketchercompanionv1.tools.WatercolorSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.WatercolorSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.WatercolorSettings() }
    }

    private fun saveWatercolorSettings(settings: com.sketcher.sketchercompanionv1.tools.WatercolorSettings) {
        if (!isProjectOpen) {
            val json = gson.toJson(settings)
            prefs.edit().putString("watercolor_settings_v2", json).apply()
        }
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
            if (saved == ToolType.SELECTION || saved == ToolType.ERASER || saved == ToolType.POINT_ERASER || saved == ToolType.CUT_ERASER) ToolType.FREEHAND else saved
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
        val savedWatercolor = loadWatercolorSettings()
        val savedPencilCumulative = loadPencilCumulativeSettings()

        toolConfigs[ToolType.FREEHAND] = loadToolConfig(ToolType.FREEHAND, 2f, 1f, savedFreehand)
        toolConfigs[ToolType.PEN] = loadToolConfig(ToolType.PEN, 2f, 1f, savedPen)
        toolConfigs[ToolType.PAINT] = loadToolConfig(ToolType.PAINT, 10f, 1f, savedPaint)
        toolConfigs[ToolType.PLUMA] = loadToolConfig(ToolType.PLUMA, 2.5f, 1f, savedPluma)
        toolConfigs[ToolType.WATERCOLOR] = loadToolConfig(ToolType.WATERCOLOR, 20f, 0.4f, savedWatercolor)
        toolConfigs[ToolType.PENCIL_CUMULATIVE] = loadToolConfig(ToolType.PENCIL_CUMULATIVE, 2f, 0.5f, savedPencilCumulative)
        toolConfigs[ToolType.FILL] = loadToolConfig(ToolType.FILL, 1f, 1.0f, PencilSettings())
        toolConfigs[ToolType.ERASER] = loadToolConfig(ToolType.ERASER, 10f, 1f, PencilSettings())
        toolConfigs[ToolType.POINT_ERASER] = loadToolConfig(ToolType.POINT_ERASER, 10f, 1f, PencilSettings())
        toolConfigs[ToolType.CUT_ERASER] = loadToolConfig(ToolType.CUT_ERASER, 10f, 1f, PencilSettings())
        toolConfigs[ToolType.SELECTION] = loadToolConfig(ToolType.SELECTION, 1f, 1f, PencilSettings())
        toolConfigs[ToolType.TRIM] = loadToolConfig(ToolType.TRIM, 1f, 1f, PencilSettings())
        toolConfigs[ToolType.EXTEND] = loadToolConfig(ToolType.EXTEND, 1f, 1f, PencilSettings())
        toolConfigs[ToolType.EDIT_POINTS] = loadToolConfig(ToolType.EDIT_POINTS, 1f, 1f, PencilSettings())

        val freehandConfig = toolConfigs[ToolType.FREEHAND]!!
        fingerModeActive = freehandConfig.isFingerMode
        fingerOffsetXValue = freehandConfig.fingerOffsetX
        fingerOffsetYValue = freehandConfig.fingerOffsetY
        
        selectTool(currentTool)
    }

    // --- FILL STYLE PRESETS ---

    fun saveFillPreset(index: Int, style: FillStyle) {
        val currentList = _fillPresets.value.toMutableList()
        if (index in 0 until currentList.size) {
            currentList[index] = style
            _fillPresets.value = currentList
            
            try {
                val array = JSONArray()
                for (preset in currentList) {
                    array.put(fillStyleToJson(preset))
                }
                prefs.edit().putString("fill_presets_v1", array.toString()).apply()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun loadFillPresets(): List<FillStyle> {
        val json = prefs.getString("fill_presets_v1", null)
        if (json != null) {
            try {
                val array = JSONArray(json)
                val list = mutableListOf<FillStyle>()
                for (i in 0 until array.length()) {
                    val itemStr = array.getString(i)
                    jsonToFillStyle(itemStr)?.let { list.add(it) }
                }
                if (list.size >= 5) {
                    return list
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return getDefaultFillPresets()
    }

    private fun getDefaultFillPresets(): List<FillStyle> {
        val builtinSvgs = listOf(
            "<svg width='40' height='40' viewBox='0 0 40 40' xmlns='http://www.w3.org/2000/svg'><circle cx='20' cy='20' r='6' fill='black'/></svg>",
            "<svg width='52' height='60' viewBox='0 0 52 60' xmlns='http://www.w3.org/2000/svg'><path d='M26 0 L52 15 L52 45 L26 60 L0 45 L0 15 Z M26 10 L43 20 L43 40 L26 50 L9 40 L9 20 Z' fill='none' stroke='black' stroke-width='2'/></svg>",
            "<svg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'><path d='M0 10 Q 15 20, 30 10 T 60 10 M0 30 Q 15 40, 30 30 T 60 30 M0 50 Q 15 60, 30 50 T 60 50' fill='none' stroke='black' stroke-width='2'/></svg>",
            "<svg width='40' height='40' viewBox='0 0 40 40' xmlns='http://www.w3.org/2000/svg'><path d='M0 0 L40 40 M40 0 L0 40' fill='none' stroke='black' stroke-width='2'/></svg>"
        )
        return listOf(
            FillStyle.Solid(AndroidColor.rgb(255, 111, 97)),
            FillStyle.MathTexture(
                patternName = "GRID",
                primaryColor = AndroidColor.rgb(63, 81, 181),
                secondaryColor = AndroidColor.TRANSPARENT,
                spacing = 30f,
                thickness = 2f,
                angle = 0f
            ),
            FillStyle.MathTexture(
                patternName = "CHECKERBOARD",
                primaryColor = AndroidColor.rgb(180, 180, 180),
                secondaryColor = AndroidColor.WHITE,
                spacing = 24f,
                thickness = 1f,
                angle = 0f
            ),
            FillStyle.SvgPattern(
                svgContent = builtinSvgs[1],
                scaleX = 1f,
                scaleY = 1f,
                rotation = 0f,
                offsetX = 0f,
                offsetY = 0f
            ),
            FillStyle.SvgPattern(
                svgContent = builtinSvgs[2],
                scaleX = 1.2f,
                scaleY = 1.2f,
                rotation = 45f,
                offsetX = 0f,
                offsetY = 0f
            )
        )
    }

    private fun fillStyleToJson(style: FillStyle): String {
        val obj = JSONObject()
        obj.put("type", style.type.name)
        when (style) {
            is FillStyle.Solid -> {
                obj.put("color", style.color)
                style.imagePath?.let { obj.put("imagePath", it) }
                obj.put("scaleX", style.scaleX.toDouble())
                obj.put("scaleY", style.scaleY.toDouble())
                obj.put("rotation", style.rotation.toDouble())
                obj.put("offsetX", style.offsetX.toDouble())
                obj.put("offsetY", style.offsetY.toDouble())
                obj.put("tintMix", style.tintMix.toDouble())
                obj.put("blendModeName", style.blendModeName)
            }
            is FillStyle.SvgPattern -> {
                obj.put("svgContent", style.svgContent)
                obj.put("scaleX", style.scaleX.toDouble())
                obj.put("scaleY", style.scaleY.toDouble())
                obj.put("rotation", style.rotation.toDouble())
                obj.put("offsetX", style.offsetX.toDouble())
                obj.put("offsetY", style.offsetY.toDouble())
            }
            is FillStyle.MathTexture -> {
                obj.put("patternName", style.patternName)
                obj.put("primaryColor", style.primaryColor)
                obj.put("secondaryColor", style.secondaryColor)
                obj.put("spacing", style.spacing.toDouble())
                obj.put("thickness", style.thickness.toDouble())
                obj.put("angle", style.angle.toDouble())
            }
            is FillStyle.ImageTexture -> {
                obj.put("imagePath", style.imagePath)
                obj.put("scaleX", style.scaleX.toDouble())
                obj.put("scaleY", style.scaleY.toDouble())
                obj.put("rotation", style.rotation.toDouble())
                obj.put("offsetX", style.offsetX.toDouble())
                obj.put("offsetY", style.offsetY.toDouble())
                obj.put("opacity", style.opacity.toDouble())
                obj.put("tintColor", style.tintColor)
                obj.put("tintMix", style.tintMix.toDouble())
                obj.put("blendModeName", style.blendModeName)
            }
        }
        return obj.toString()
    }

    private fun jsonToFillStyle(jsonStr: String): FillStyle? {
        try {
            val obj = JSONObject(jsonStr)
            val typeStr = obj.getString("type")
            val type = FillType.valueOf(typeStr)
            return when (type) {
                FillType.SOLID -> {
                    FillStyle.Solid(
                        color = obj.getInt("color"),
                        imagePath = if (obj.has("imagePath")) obj.getString("imagePath") else null,
                        scaleX = obj.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = obj.optDouble("scaleY", 1.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = obj.optDouble("offsetY", 0.0).toFloat(),
                        tintMix = obj.optDouble("tintMix", 1.0).toFloat(),
                        blendModeName = obj.optString("blendModeName", "SRC_ATOP")
                    )
                }
                FillType.SVG_PATTERN -> {
                    FillStyle.SvgPattern(
                        svgContent = obj.getString("svgContent"),
                        scaleX = obj.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = obj.optDouble("scaleY", 1.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = obj.optDouble("offsetY", 0.0).toFloat()
                    )
                }
                FillType.MATH_TEXTURE -> {
                    FillStyle.MathTexture(
                        patternName = obj.getString("patternName"),
                        primaryColor = obj.optInt("primaryColor", AndroidColor.BLACK),
                        secondaryColor = obj.optInt("secondaryColor", AndroidColor.TRANSPARENT),
                        spacing = obj.optDouble("spacing", 20.0).toFloat(),
                        thickness = obj.optDouble("thickness", 2.0).toFloat(),
                        angle = obj.optDouble("angle", 0.0).toFloat()
                    )
                }
                FillType.IMAGE_TEXTURE -> {
                    FillStyle.ImageTexture(
                        imagePath = obj.getString("imagePath"),
                        scaleX = obj.optDouble("scaleX", 1.0).toFloat(),
                        scaleY = obj.optDouble("scaleY", 1.0).toFloat(),
                        rotation = obj.optDouble("rotation", 0.0).toFloat(),
                        offsetX = obj.optDouble("offsetX", 0.0).toFloat(),
                        offsetY = obj.optDouble("offsetY", 0.0).toFloat(),
                        opacity = obj.optDouble("opacity", 1.0).toFloat(),
                        tintColor = obj.optInt("tintColor", AndroidColor.TRANSPARENT),
                        tintMix = obj.optDouble("tintMix", 0.0).toFloat(),
                        blendModeName = obj.optString("blendModeName", "SRC_ATOP")
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // --- CUSTOM TOOLS LOGIC ---

    fun saveToolToGlobal(toolId: String) {
        val toolType = try { ToolType.valueOf(toolId) } catch (e: Exception) { null }
        if (toolType != null) {
            val config = toolConfigs[toolType]
            if (config != null) {
                prefs.edit().apply {
                    putInt("tool_stroke_color_${toolType.name}", config.strokeColor)
                    putInt("tool_fill_color_${toolType.name}", config.fillColor)
                    putBoolean("tool_stroke_active_${toolType.name}", config.isStrokeActive)
                    putBoolean("tool_fill_active_${toolType.name}", config.isFillActive)
                    putFloat("tool_stabilization_${toolType.name}", config.stabilization)
                    
                    val fsStr = config.fillStyle?.let { gson.toJson(it) }
                    val ssStr = config.strokeStyle?.let { gson.toJson(it) }
                    putString("tool_fill_style_v2_${toolType.name}", fsStr)
                    putString("tool_stroke_style_v2_${toolType.name}", ssStr)
                    putFloat("tool_size_${toolType.name}", config.size)
                    putFloat("tool_alpha_${toolType.name}", config.opacity)
                    apply()
                }
                
                when (toolType) {
                    ToolType.FREEHAND -> saveFreehandSettingsToGlobal(config.settings as? com.sketcher.sketchercompanionv1.tools.PencilSettings)
                    ToolType.PEN -> savePenSettingsToGlobal(config.settings as? com.sketcher.sketchercompanionv1.tools.PenSettings)
                    ToolType.PAINT -> savePaintSettingsToGlobal(config.settings as? com.sketcher.sketchercompanionv1.tools.PaintSettings)
                    ToolType.PLUMA -> savePlumaSettingsToGlobal(config.settings as? com.sketcher.sketchercompanionv1.tools.PlumaSettings)
                    ToolType.WATERCOLOR -> saveWatercolorSettingsToGlobal(config.settings as? com.sketcher.sketchercompanionv1.tools.WatercolorSettings)
                    ToolType.PENCIL_CUMULATIVE -> savePencilCumulativeSettingsToGlobal(config.settings as? com.sketcher.sketchercompanionv1.tools.PencilSettings)
                    else -> {}
                }
            }
        } else {
            val ct = _customTools.value.find { it.id == toolId }
            if (ct != null) {
                scope.launch(Dispatchers.IO) {
                    globalToolRepository.saveGlobalTool(ct)
                    val currentGlobal = _globalCustomTools.value.toMutableList()
                    val idx = currentGlobal.indexOfFirst { it.id == toolId }
                    if (idx != -1) {
                        currentGlobal[idx] = ct
                    } else {
                        currentGlobal.add(ct)
                    }
                    _globalCustomTools.value = currentGlobal
                    updateEffectiveTools()
                    onCustomToolAddedOrUpdated?.invoke(ct)
                }
            }
        }
    }
    
    private fun saveFreehandSettingsToGlobal(settings: com.sketcher.sketchercompanionv1.tools.PencilSettings?) {
        if (settings != null) prefs.edit().putString("freehand_settings_v4", gson.toJson(settings)).apply()
    }
    
    private fun savePenSettingsToGlobal(settings: com.sketcher.sketchercompanionv1.tools.PenSettings?) {
        if (settings != null) prefs.edit().putString("pen_settings_v4", gson.toJson(settings)).apply()
    }
    
    private fun savePaintSettingsToGlobal(settings: com.sketcher.sketchercompanionv1.tools.PaintSettings?) {
        if (settings != null) prefs.edit().putString("paint_settings_v4", gson.toJson(settings)).apply()
    }
    
    private fun savePlumaSettingsToGlobal(settings: com.sketcher.sketchercompanionv1.tools.PlumaSettings?) {
        if (settings != null) prefs.edit().putString("pluma_settings_v4", gson.toJson(settings)).apply()
    }
    
    private fun saveWatercolorSettingsToGlobal(settings: com.sketcher.sketchercompanionv1.tools.WatercolorSettings?) {
        if (settings != null) prefs.edit().putString("watercolor_settings_v2", gson.toJson(settings)).apply()
    }
    
    private fun savePencilCumulativeSettingsToGlobal(settings: com.sketcher.sketchercompanionv1.tools.PencilSettings?) {
        if (settings != null) prefs.edit().putString("pencil_cumulative_settings_v2", gson.toJson(settings)).apply()
    }

    fun loadProjectState(projectTools: List<CustomToolJson>?, projectConfigs: Map<String, ToolConfigJson>?) {
        isProjectOpen = true
        activeProjectToolsConfig = projectConfigs?.toMutableMap() ?: mutableMapOf()
        
        val mappedTools = projectTools?.map { j ->
            val toolType = try { ToolType.valueOf(j.baseToolType) } catch (e: Exception) { ToolType.FREEHAND }
            val settings = when (toolType) {
                ToolType.FREEHAND -> try { gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PencilSettings()}
                ToolType.PEN -> try { gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PenSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PenSettings()}
                ToolType.PLUMA -> try { gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PlumaSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PlumaSettings()}
                ToolType.PAINT -> try { gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PaintSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PaintSettings()}
                ToolType.WATERCOLOR -> try { gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.WatercolorSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.WatercolorSettings()}
                ToolType.PENCIL_CUMULATIVE -> try { gson.fromJson(j.preset.settingsJson, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PencilSettings(isCumulativeOpacity = true)}
                else -> com.sketcher.sketchercompanionv1.tools.PencilSettings()
            }
            CustomTool(
                id = j.id,
                name = j.name,
                iconName = j.iconName,
                iconResName = j.iconResName,
                baseToolType = toolType,
                preset = BrushPreset(
                    size = j.preset.size,
                    opacity = j.preset.opacity,
                    settings = settings,
                    strokeColor = j.preset.strokeColor,
                    fillColor = j.preset.fillColor,
                    isStrokeActive = j.preset.isStrokeActive,
                    isFillActive = j.preset.isFillActive,
                    isStrokeColorLocked = j.preset.isStrokeColorLocked,
                    isFillColorLocked = j.preset.isFillColorLocked,
                    fillStyle = j.preset.fillStyle?.toFillStyle(j.preset.fillColor ?: 0),
                    strokeStyle = j.preset.strokeStyle?.toFillStyle(j.preset.strokeColor ?: 0),
                    stabilization = j.preset.stabilization
                ),
                customIconJson = j.customIconJson
            )
        } ?: emptyList()
        
        _projectCustomTools.value = mappedTools
        
        projectConfigs?.forEach { (typeStr, configJson) ->
            try {
                val toolType = ToolType.valueOf(typeStr)
                val settings = when (toolType) {
                    ToolType.FREEHAND -> try { gson.fromJson(configJson.settingsJson, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PencilSettings()}
                    ToolType.PEN -> try { gson.fromJson(configJson.settingsJson, com.sketcher.sketchercompanionv1.tools.PenSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PenSettings()}
                    ToolType.PLUMA -> try { gson.fromJson(configJson.settingsJson, com.sketcher.sketchercompanionv1.tools.PlumaSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PlumaSettings()}
                    ToolType.PAINT -> try { gson.fromJson(configJson.settingsJson, com.sketcher.sketchercompanionv1.tools.PaintSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PaintSettings()}
                    ToolType.WATERCOLOR -> try { gson.fromJson(configJson.settingsJson, com.sketcher.sketchercompanionv1.tools.WatercolorSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.WatercolorSettings()}
                    ToolType.PENCIL_CUMULATIVE -> try { gson.fromJson(configJson.settingsJson, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch(e:Exception){com.sketcher.sketchercompanionv1.tools.PencilSettings(isCumulativeOpacity = true)}
                    else -> com.sketcher.sketchercompanionv1.tools.PencilSettings()
                }
                toolConfigs[toolType] = ToolConfig(
                    size = configJson.size,
                    opacity = configJson.opacity,
                    settings = settings,
                    isFingerMode = configJson.isFingerMode,
                    fingerOffsetX = configJson.fingerOffsetX,
                    fingerOffsetY = configJson.fingerOffsetY,
                    eraserShape = EraserShape.valueOf(configJson.eraserShape),
                    strokeColor = configJson.strokeColor,
                    fillColor = configJson.fillColor,
                    isStrokeActive = configJson.isStrokeActive,
                    isFillActive = configJson.isFillActive,
                    fillStyle = configJson.fillStyle,
                    strokeStyle = configJson.strokeStyle,
                    stabilization = configJson.stabilization
                )
            } catch(e: Exception) {}
        }
        
        updateEffectiveTools()
        selectTool(currentTool) // re-apply the config
    }

    fun closeProject() {
        isProjectOpen = false
        _projectCustomTools.value = emptyList()
        activeProjectToolsConfig.clear()
        updateEffectiveTools()
    }

    fun loadCustomTools(onLoaded: (() -> Unit)? = null) {
        scope.launch(Dispatchers.IO) {
            val tools = globalToolRepository.getAllGlobalTools()
            val hasInitialized = prefs.getBoolean("has_initialized_base_brushes", false)
            
            if (tools.isEmpty() && !hasInitialized) {
                populateDefaultCustomTools()
            } else {
                if (!hasInitialized) prefs.edit().putBoolean("has_initialized_base_brushes", true).apply()
                _globalCustomTools.value = tools
                updateEffectiveTools()
            }
            withContext(Dispatchers.Main) {
                onLoaded?.invoke()
            }
        }
    }

    fun restoreDefaultBrushes() {
        scope.launch(Dispatchers.IO) {
            populateDefaultCustomTools()
            _globalCustomTools.value.forEach { onCustomToolAddedOrUpdated?.invoke(it) }
        }
    }

    private suspend fun populateDefaultCustomTools() {
        prefs.edit().putBoolean("has_initialized_base_brushes", true).apply()
        val defaultBrushes = listOf(
            CustomTool(
                id = "default_pencil",
                name = "Lápiz Básico",
                iconName = "pencil",
                iconResName = "ic_tabler_pencil",
                baseToolType = ToolType.FREEHAND,
                preset = BrushPreset(size = 5f, opacity = 1f, settings = com.sketcher.sketchercompanionv1.tools.PencilSettings(), stabilization = 0.07f)
            ),
            CustomTool(
                id = "default_pen",
                name = "Bolígrafo Sólido",
                iconName = "pen",
                iconResName = "ic_tabler_pen",
                baseToolType = ToolType.PEN,
                preset = BrushPreset(size = 8f, opacity = 1f, settings = com.sketcher.sketchercompanionv1.tools.PenSettings(), stabilization = 0f)
            ),
            CustomTool(
                id = "default_pluma",
                name = "Pluma Caligráfica",
                iconName = "pluma",
                iconResName = "ic_tabler_pluma",
                baseToolType = ToolType.PLUMA,
                preset = BrushPreset(size = 15f, opacity = 1f, settings = com.sketcher.sketchercompanionv1.tools.PlumaSettings(), stabilization = 0f)
            ),
            CustomTool(
                id = "default_paint",
                name = "Pincel Acrílico",
                iconName = "paint",
                iconResName = "ic_tabler_paint",
                baseToolType = ToolType.PAINT,
                preset = BrushPreset(size = 20f, opacity = 0.8f, settings = com.sketcher.sketchercompanionv1.tools.PaintSettings(), stabilization = 0f)
            ),
            CustomTool(
                id = "default_watercolor",
                name = "Acuarela Suave",
                iconName = "watercolor",
                iconResName = "ic_tabler_watercolor",
                baseToolType = ToolType.WATERCOLOR,
                preset = BrushPreset(size = 30f, opacity = 0.5f, settings = com.sketcher.sketchercompanionv1.tools.WatercolorSettings(), stabilization = 0f)
            )
        )
        globalToolRepository.saveGlobalTools(defaultBrushes)
        _globalCustomTools.value = defaultBrushes
        updateEffectiveTools()
    }

    private fun updateRegistryCustomTools(list: List<CustomTool>) {
        ToolRegistry.customTools = list.map { ct ->
            val composeIcon = ToolRegistry.getIconByName(ct.iconName)
            val resId = ct.iconResName?.let {
                context.resources.getIdentifier(it, "drawable", context.packageName)
            } ?: 0
            
            StudioTool(
                id = ct.id,
                icon = composeIcon,
                contentDescription = ct.name,
                iconResId = if (resId != 0) resId else null,
                isPlaceholder = false,
                registryId = ct.id,
                parentGroupId = null
            )
        }
    }

    fun addCustomTool(ct: CustomTool) {
        val currentGlobal = _globalCustomTools.value.toMutableList()
        currentGlobal.removeAll { it.id == ct.id }
        currentGlobal.add(ct)
        _globalCustomTools.value = currentGlobal
        updateEffectiveTools()
        scope.launch(Dispatchers.IO) {
            globalToolRepository.saveGlobalTool(ct)
        }
        onCustomToolAddedOrUpdated?.invoke(ct)
    }

    fun removeCustomTool(id: String) {
        val currentGlobal = _globalCustomTools.value.toMutableList()
        currentGlobal.removeAll { it.id == id }
        _globalCustomTools.value = currentGlobal
        updateEffectiveTools()
        scope.launch(Dispatchers.IO) {
            globalToolRepository.deleteGlobalTool(id)
        }
        onCustomToolRemoved?.invoke(id)
    }

    fun updateCustomTool(ct: CustomTool) {
        val currentGlobal = _globalCustomTools.value.toMutableList()
        val index = currentGlobal.indexOfFirst { it.id == ct.id }
        if (index != -1) {
            currentGlobal[index] = ct
            _globalCustomTools.value = currentGlobal
            updateEffectiveTools()
            scope.launch(Dispatchers.IO) {
                globalToolRepository.saveGlobalTool(ct)
            }
            onCustomToolAddedOrUpdated?.invoke(ct)
        }
    }

    fun applyBrushPresetDirectly(preset: BrushPreset) {
        setToolSize(preset.size)
        setToolOpacity(preset.opacity)
        updateFreehandSettings(preset.settings)
        
        _selectedPresetIndex.value = null
        
        val customId = activeCustomToolId
        val useExplicitStroke = preset.isStrokeColorLocked
        val useExplicitFill = preset.isFillColorLocked

        val sc = if (useExplicitStroke) {
            preset.strokeColor ?: _strokeColor.value
        } else {
            _strokeColor.value
        }
        
        val ss = if (useExplicitStroke) {
            preset.strokeStyle ?: FillStyle.Solid(sc)
        } else {
            _strokeStyle.value
        }
        
        val sa = preset.isStrokeActive ?: _isStrokeActive.value

        val fc = if (useExplicitFill) {
            preset.fillColor ?: _fillColor.value
        } else {
            _fillColor.value
        }
        
        val fs = if (useExplicitFill) {
            preset.fillStyle ?: FillStyle.Solid(fc)
        } else {
            _fillStyle.value
        }
        
        val fa = preset.isFillActive ?: _isFillActive.value

        val defaultStab = if (currentTool == ToolType.FREEHAND || currentTool == ToolType.PENCIL_CUMULATIVE) 0.07f else 0f
        val stab = preset.stabilization ?: defaultStab

        val isLinked = currentTool == ToolType.WATERCOLOR && (preset.settings as? com.sketcher.sketchercompanionv1.tools.WatercolorSettings)?.linkStrokeToFill == true
        val finalSc = if (isLinked) adjustColorBrightness(fc, (preset.settings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings).strokeBrightnessOffset) else sc
        val finalSs = if (isLinked) FillStyle.Solid(finalSc) else ss
        val finalSa = if (isLinked) true else sa

        _strokeColor.value = finalSc
        _fillColor.value = fc
        _isStrokeActive.value = finalSa
        _isFillActive.value = fa
        _fillStyle.value = fs
        _strokeStyle.value = finalSs
        setGlobalStabilization(stab)

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            size = preset.size,
            opacity = preset.opacity,
            settings = preset.settings,
            strokeColor = finalSc,
            fillColor = fc,
            isStrokeActive = finalSa,
            isFillActive = fa,
            stabilization = stab
        )
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
        
        prefs.edit().apply {
            putFloat("tool_size_${currentTool.name}", preset.size)
            putFloat("tool_alpha_${currentTool.name}", preset.opacity)
            putFloat("tool_stabilization_${currentTool.name}", stab)
            apply()
        }
        
        updatePresetFlows()
    }

    fun getIsStrokeColorPreset(tool: ToolType, presetIndex: Int): Boolean {
        return prefs.getBoolean("preset_stroke_explicit_${tool.name}_$presetIndex", false).not()
    }

    fun getExplicitStrokeColor(tool: ToolType, presetIndex: Int, defaultColor: Int): Int {
        return getSafeInt("preset_stroke_color_${tool.name}_$presetIndex", defaultColor)
    }

    fun getExplicitStrokeStyle(tool: ToolType, presetIndex: Int): FillStyle? {
        val jsonStr = prefs.getString("preset_stroke_style_v2_${tool.name}_$presetIndex", null) ?: return null
        return try { jsonToFillStyle(jsonStr) } catch(e: Exception) { null }
    }

    fun setExplicitStrokeColorState(tool: ToolType, presetIndex: Int, explicit: Boolean, color: Int, style: FillStyle?) {
        val customId = activeCustomToolId
        if (customId != null) {
            val ct = _customTools.value.find { it.id == customId }
            if (ct != null) {
                val updatedPreset = ct.preset.copy(
                    isStrokeColorLocked = explicit,
                    strokeColor = color,
                    strokeStyle = style
                )
                updateCustomTool(ct.copy(preset = updatedPreset))
            }
        } else {
            prefs.edit().apply {
                putBoolean("preset_stroke_explicit_${tool.name}_$presetIndex", explicit)
                putInt("preset_stroke_color_${tool.name}_$presetIndex", color)
                if (style != null) {
                    putString("preset_stroke_style_v2_${tool.name}_$presetIndex", fillStyleToJson(style))
                } else {
                    remove("preset_stroke_style_v2_${tool.name}_$presetIndex")
                }
                apply()
            }
        }
        updatePresetFlows()
    }

    fun getIsFillColorPreset(tool: ToolType, presetIndex: Int): Boolean {
        return prefs.getBoolean("preset_fill_explicit_${tool.name}_$presetIndex", false).not()
    }

    fun getExplicitFillColor(tool: ToolType, presetIndex: Int, defaultColor: Int): Int {
        return getSafeInt("preset_fill_color_${tool.name}_$presetIndex", defaultColor)
    }

    fun getExplicitFillStyle(tool: ToolType, presetIndex: Int): FillStyle? {
        val jsonStr = prefs.getString("preset_fill_style_v2_${tool.name}_$presetIndex", null) ?: return null
        return try { jsonToFillStyle(jsonStr) } catch(e: Exception) { null }
    }

    fun setExplicitFillColorState(tool: ToolType, presetIndex: Int, explicit: Boolean, color: Int, style: FillStyle?) {
        val customId = activeCustomToolId
        if (customId != null) {
            val ct = _customTools.value.find { it.id == customId }
            if (ct != null) {
                val updatedPreset = ct.preset.copy(
                    isFillColorLocked = explicit,
                    fillColor = color,
                    fillStyle = style
                )
                updateCustomTool(ct.copy(preset = updatedPreset))
            }
        } else {
            prefs.edit().apply {
                putBoolean("preset_fill_explicit_${tool.name}_$presetIndex", explicit)
                putInt("preset_fill_color_${tool.name}_$presetIndex", color)
                if (style != null) {
                    putString("preset_fill_style_v2_${tool.name}_$presetIndex", fillStyleToJson(style))
                } else {
                    remove("preset_fill_style_v2_${tool.name}_$presetIndex")
                }
                apply()
            }
        }
        updatePresetFlows()
    }

    fun updatePresetFlows() {
        val customId = activeCustomToolId
        if (customId != null) {
            val ct = _customTools.value.find { it.id == customId }
            if (ct != null) {
                _isStrokeColorPreset.value = !ct.preset.isStrokeColorLocked
                _isFillColorPreset.value = !ct.preset.isFillColorLocked
            } else {
                _isStrokeColorPreset.value = true
                _isFillColorPreset.value = true
            }
        } else {
            val idx = _selectedPresetIndex.value ?: 0
            _isStrokeColorPreset.value = getIsStrokeColorPreset(currentTool, idx)
            _isFillColorPreset.value = getIsFillColorPreset(currentTool, idx)
        }
    }

    fun revertToPresetColor(isStroke: Boolean) {
        val customId = activeCustomToolId
        val preset = if (customId != null) {
            _customTools.value.find { it.id == customId }?.preset
        } else {
            val idx = _selectedPresetIndex.value ?: 0
            val list = _brushPresets.value
            if (idx in list.indices) list[idx] else null
        }
        
        if (preset != null) {
            if (isStroke) {
                if (customId == null) {
                    val idx = _selectedPresetIndex.value ?: 0
                    prefs.edit().apply {
                        putBoolean("preset_stroke_explicit_${currentTool.name}_$idx", false)
                        remove("preset_stroke_color_${currentTool.name}_$idx")
                        remove("preset_stroke_style_v2_${currentTool.name}_$idx")
                        apply()
                    }
                } else {
                    val ct = _customTools.value.find { it.id == customId }
                    if (ct != null) {
                        val updatedPreset = ct.preset.copy(
                            isStrokeColorLocked = false,
                            strokeColor = preset.strokeColor ?: AndroidColor.BLACK,
                            strokeStyle = preset.strokeStyle ?: FillStyle.Solid(preset.strokeColor ?: AndroidColor.BLACK)
                        )
                        updateCustomTool(ct.copy(preset = updatedPreset))
                    }
                }
                val sc = preset.strokeColor ?: AndroidColor.BLACK
                val ss = preset.strokeStyle ?: FillStyle.Solid(sc)
                val sa = preset.isStrokeActive ?: true
                _strokeColor.value = sc
                _strokeStyle.value = ss
                _isStrokeActive.value = sa
                
                val config = toolConfigs[currentTool]!!
                val updated = config.copy(
                    strokeColor = sc,
                    strokeStyle = ss.toFillStyleJson(),
                    isStrokeActive = sa
                )
                toolConfigs[currentTool] = updated
                persistToolConfigColorState(currentTool, updated)
            } else {
                if (customId == null) {
                    val idx = _selectedPresetIndex.value ?: 0
                    prefs.edit().apply {
                        putBoolean("preset_fill_explicit_${currentTool.name}_$idx", false)
                        remove("preset_fill_color_${currentTool.name}_$idx")
                        remove("preset_fill_style_v2_${currentTool.name}_$idx")
                        apply()
                    }
                } else {
                    val ct = _customTools.value.find { it.id == customId }
                    if (ct != null) {
                        val updatedPreset = ct.preset.copy(
                            isFillColorLocked = false,
                            fillColor = preset.fillColor ?: AndroidColor.WHITE,
                            fillStyle = preset.fillStyle ?: FillStyle.Solid(preset.fillColor ?: AndroidColor.WHITE)
                        )
                        updateCustomTool(ct.copy(preset = updatedPreset))
                    }
                }
                val fc = preset.fillColor ?: AndroidColor.WHITE
                val fs = preset.fillStyle ?: FillStyle.Solid(fc)
                val fa = preset.isFillActive ?: true
                _fillColor.value = fc
                _fillStyle.value = fs
                _isFillActive.value = fa
                
                val config = toolConfigs[currentTool]!!
                val updated = config.copy(
                    fillColor = fc,
                    fillStyle = fs.toFillStyleJson(),
                    isFillActive = fa
                )
                toolConfigs[currentTool] = updated
                persistToolConfigColorState(currentTool, updated)
            }
            updatePresetFlows()
        }
    }

    fun resetAllPresetOverrideStates() {
        val editor = prefs.edit()
        prefs.all.keys.forEach { key ->
            if (key.startsWith("preset_stroke_") || key.startsWith("preset_fill_") || key.startsWith("last_preset_index_")) {
                editor.remove(key)
            }
        }
        editor.apply()
        
        ToolType.entries.forEach { type ->
            lastPresetIndexPerTool[type] = 0
        }
        _selectedPresetIndex.value = 0
        selectTool(currentTool)
    }

    fun getToolStatesJson(activeCustomToolId: String?): String {
        val map = mutableMapOf<String, Any>()
        map["currentTool"] = currentTool.name
        map["currentStrokeType"] = currentStrokeType.name
        if (activeCustomToolId != null) {
            map["activeCustomToolId"] = activeCustomToolId
        }
        
        val lastPresetIndices = mutableMapOf<String, Int>()
        lastPresetIndexPerTool.forEach { (k, v) ->
            lastPresetIndices[k.name] = v
        }
        map["lastPresetIndexPerTool"] = lastPresetIndices
        
        val overrides = mutableMapOf<String, Any>()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("preset_stroke_") || key.startsWith("preset_fill_") || key.startsWith("last_preset_index_")) {
                if (value != null) {
                    overrides[key] = value
                }
            }
        }
        map["presetOverrides"] = overrides
        
        return gson.toJson(map)
    }

    fun restoreToolStatesFromJson(jsonStr: String): String? {
        var activeCustomId: String? = null
        try {
            val typeToken = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            val map: Map<String, Any> = gson.fromJson(jsonStr, typeToken)
            
            val editor = prefs.edit()
            
            prefs.all.keys.forEach { key ->
                if (key.startsWith("preset_stroke_") || key.startsWith("preset_fill_") || key.startsWith("last_preset_index_")) {
                    editor.remove(key)
                }
            }
            
            val overrides = map["presetOverrides"] as? Map<*, *>
            overrides?.forEach { (k, v) ->
                val key = k.toString()
                when (v) {
                    is Boolean -> editor.putBoolean(key, v)
                    is Float -> editor.putFloat(key, v)
                    is Int -> editor.putInt(key, v)
                    is Long -> {
                        if (v >= Int.MIN_VALUE && v <= Int.MAX_VALUE) {
                            editor.putInt(key, v.toInt())
                        } else {
                            editor.putLong(key, v)
                        }
                    }
                    is Double -> {
                        if (v % 1 == 0.0) {
                            val lv = v.toLong()
                            if (lv >= Int.MIN_VALUE && lv <= Int.MAX_VALUE) {
                                editor.putInt(key, lv.toInt())
                            } else {
                                editor.putLong(key, lv)
                            }
                        } else {
                            editor.putFloat(key, v.toFloat())
                        }
                    }
                    is String -> editor.putString(key, v)
                }
            }
            
            val lastPresetIndices = map["lastPresetIndexPerTool"] as? Map<*, *>
            lastPresetIndices?.forEach { (k, v) ->
                try {
                    val toolType = ToolType.valueOf(k.toString())
                    val index = (v as? Number)?.toInt() ?: 0
                    lastPresetIndexPerTool[toolType] = index
                    editor.putInt("last_preset_index_${toolType.name}", index)
                } catch(e: Exception) {}
            }
            
            editor.apply()
            
            val toolName = map["currentTool"]?.toString()
            if (toolName != null) {
                try {
                    val toolType = ToolType.valueOf(toolName)
                    currentTool = toolType
                } catch(e: Exception) {
                    currentTool = ToolType.SELECTION
                }
            } else {
                currentTool = ToolType.SELECTION
            }
            
            val strokeTypeName = map["currentStrokeType"]?.toString()
            if (strokeTypeName != null) {
                try {
                    val strokeType = StrokeType.valueOf(strokeTypeName)
                    currentStrokeType = strokeType
                    editor.putString("current_stroke_type", strokeType.name).apply()
                } catch(e: Exception) {}
            }
            
            activeCustomId = map["activeCustomToolId"]?.toString()
            
            selectTool(currentTool)
            
        } catch(e: Exception) {
            e.printStackTrace()
        }
        return activeCustomId
    }
}

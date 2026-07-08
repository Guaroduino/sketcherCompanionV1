package com.sketcher.sketchercompanionv1.managers

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.*
import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.tools.*
import com.sketcher.sketchercompanionv1.utils.toFillStyle
import com.sketcher.sketchercompanionv1.utils.toFillStyleJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.json.JSONArray

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

    private val lastPresetIndexPerTool = mutableMapOf<ToolType, Int>().apply {
        ToolType.entries.forEach { type ->
            put(type, prefs.getInt("last_preset_index_${type.name}", 0))
        }
    }

    private val _toolPresetGroupNames = MutableStateFlow<List<String>>(listOf("Default"))
    val toolPresetGroupNames: StateFlow<List<String>> = _toolPresetGroupNames.asStateFlow()

    private val _activeToolPresetGroupName = MutableStateFlow<String>("Default")
    val activeToolPresetGroupName: StateFlow<String> = _activeToolPresetGroupName.asStateFlow()

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

        val sc = prefs.getInt("tool_stroke_color_${type.name}", defaultStrokeColor)
        val fc = prefs.getInt("tool_fill_color_${type.name}", defaultFillColor)
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

        val names = getToolPresetGroupNames()
        _toolPresetGroupNames.value = if (names.contains("Default")) names else listOf("Default") + names
        _activeToolPresetGroupName.value = prefs.getString("active_tool_preset_group_name", "Default") ?: "Default"
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
                        val settings = when (pJson.settingsType) {
                            "PencilSettings" -> gson.fromJson(pJson.settingsJson, PencilSettings::class.java)
                            "PenSettings" -> gson.fromJson(pJson.settingsJson, PenSettings::class.java)
                            "PlumaSettings" -> gson.fromJson(pJson.settingsJson, PlumaSettings::class.java)
                            "PaintSettings" -> gson.fromJson(pJson.settingsJson, PaintSettings::class.java)
                            "WatercolorSettings" -> gson.fromJson(pJson.settingsJson, WatercolorSettings::class.java)
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
                BrushPreset(size = 20f, opacity = 0.6f, settings = PencilSettings(thinning = 0.7f, smoothing = 0.6f, simulatePressure = true, taperStart = 20f, taperEnd = 20f, isCumulativeOpacity = true), strokeColor = AndroidColor.rgb(33, 150, 243), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(33, 150, 243)), stabilization = 0.15f),
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
                BrushPreset(size = 20f, opacity = 0.8f, settings = PencilSettings(thinning = 0.7f, smoothing = 0.6f, simulatePressure = true, taperStart = 20f, taperEnd = 20f), strokeColor = AndroidColor.rgb(33, 150, 243), fillColor = AndroidColor.TRANSPARENT, isStrokeActive = true, isFillActive = false, fillStyle = FillStyle.Solid(AndroidColor.TRANSPARENT), strokeStyle = FillStyle.Solid(AndroidColor.rgb(33, 150, 243)), stabilization = 0.15f),
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

            _strokeColor.value = sc
            _fillColor.value = fc
            _isStrokeActive.value = sa
            _isFillActive.value = fa
            _fillStyle.value = fs
            _strokeStyle.value = ss
            setGlobalStabilization(stab)

            // Update and persist the tool config with the preset values!
            val config = toolConfigs[currentTool]!!
            val updated = config.copy(
                size = preset.size,
                opacity = preset.opacity,
                strokeColor = sc,
                fillColor = fc,
                isStrokeActive = sa,
                isFillActive = fa,
                fillStyle = fs.toFillStyleJson(),
                strokeStyle = ss.toFillStyleJson(),
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

    fun setStrokeColor(color: Int) {
        _strokeColor.value = color
        _strokeStyle.value = FillStyle.Solid(color)
        _isStrokeActive.value = true
        val isPaintOrWatercolor = (currentTool == ToolType.PAINT || currentTool == ToolType.WATERCOLOR)
        if (isPaintOrWatercolor) {
            _fillColor.value = color
            _fillStyle.value = FillStyle.Solid(color)
        }
        
        val idx = _selectedPresetIndex.value ?: 0
        setExplicitStrokeColorState(currentTool, idx, true, color, FillStyle.Solid(color))
        if (isPaintOrWatercolor) {
            setExplicitFillColorState(currentTool, idx, true, color, FillStyle.Solid(color))
        }

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            strokeColor = color,
            strokeStyle = FillStyle.Solid(color).toFillStyleJson(),
            isStrokeActive = true,
            fillColor = if (isPaintOrWatercolor) color else config.fillColor,
            fillStyle = if (isPaintOrWatercolor) FillStyle.Solid(color).toFillStyleJson() else config.fillStyle,
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
        _fillStyle.value = FillStyle.Solid(color)
        _isFillActive.value = true
        
        val idx = _selectedPresetIndex.value ?: 0
        setExplicitFillColorState(currentTool, idx, true, color, FillStyle.Solid(color))

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            fillColor = color,
            fillStyle = FillStyle.Solid(color).toFillStyleJson(),
            isFillActive = true
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

        val config = toolConfigs[currentTool]!!
        val updated = config.copy(
            fillColor = color,
            fillStyle = style.toFillStyleJson(),
            isFillActive = true
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
    }

    fun toggleFill(enabled: Boolean) { 
        _isFillActive.value = enabled
        val config = toolConfigs[currentTool]!!
        val updated = config.copy(isFillActive = enabled)
        toolConfigs[currentTool] = updated
        persistToolConfigColorState(currentTool, updated)
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
        val config = toolConfigs[currentTool]!!
        toolConfigs[currentTool] = config.copy(settings = settings)
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
        val json = gson.toJson(settings)
        prefs.edit().putString("freehand_settings_v4", json).apply()
    }

    private fun loadPaintSettings(): com.sketcher.sketchercompanionv1.tools.PaintSettings {
        val json = prefs.getString("paint_settings_v4", null) ?: return com.sketcher.sketchercompanionv1.tools.PaintSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PaintSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PaintSettings() }
    }

    private fun savePaintSettings(settings: com.sketcher.sketchercompanionv1.tools.PaintSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("paint_settings_v4", json).apply()
    }

    private fun loadPenSettings(): com.sketcher.sketchercompanionv1.tools.PenSettings {
        val json = prefs.getString("pen_settings_v4", null) ?: return com.sketcher.sketchercompanionv1.tools.PenSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PenSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PenSettings() }
    }

    private fun savePenSettings(settings: com.sketcher.sketchercompanionv1.tools.PenSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("pen_settings_v4", json).apply()
    }

    private fun loadPlumaSettings(): com.sketcher.sketchercompanionv1.tools.PlumaSettings {
        val json = prefs.getString("pluma_settings_v4", null) ?: return com.sketcher.sketchercompanionv1.tools.PlumaSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PlumaSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PlumaSettings() }
    }

    private fun savePlumaSettings(settings: com.sketcher.sketchercompanionv1.tools.PlumaSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("pluma_settings_v4", json).apply()
    }

    private fun loadPencilCumulativeSettings(): com.sketcher.sketchercompanionv1.tools.PencilSettings {
        val json = prefs.getString("pencil_cumulative_settings_v2", null) ?: return com.sketcher.sketchercompanionv1.tools.PencilSettings(isCumulativeOpacity = true)
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.PencilSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.PencilSettings(isCumulativeOpacity = true) }
    }

    private fun savePencilCumulativeSettings(settings: com.sketcher.sketchercompanionv1.tools.PencilSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("pencil_cumulative_settings_v2", json).apply()
    }

    private fun loadWatercolorSettings(): com.sketcher.sketchercompanionv1.tools.WatercolorSettings {
        val json = prefs.getString("watercolor_settings_v2", null) ?: return com.sketcher.sketchercompanionv1.tools.WatercolorSettings()
        return try { gson.fromJson(json, com.sketcher.sketchercompanionv1.tools.WatercolorSettings::class.java) } catch (e: Exception) { com.sketcher.sketchercompanionv1.tools.WatercolorSettings() }
    }

    private fun saveWatercolorSettings(settings: com.sketcher.sketchercompanionv1.tools.WatercolorSettings) {
        val json = gson.toJson(settings)
        prefs.edit().putString("watercolor_settings_v2", json).apply()
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
                    FillStyle.Solid(color = obj.getInt("color"))
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

    // --- TOOL PRESETS GROUPS (Presets de Herramientas) ---

    fun getToolPresetGroupNames(): List<String> {
        val json = prefs.getString("tool_preset_group_names", null) ?: return emptyList()
        return try {
            gson.fromJson(json, object : com.google.gson.reflect.TypeToken<List<String>>() {}.type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveToolPresetGroup(name: String) {
        val list = getToolPresetGroupNames().toMutableList()
        if (!list.contains(name)) {
            list.add(name)
            val jsonList = gson.toJson(list)
            prefs.edit().putString("tool_preset_group_names", jsonList).apply()
        }
        _toolPresetGroupNames.value = if (list.contains("Default")) list else listOf("Default") + list

        val data = JSONObject().apply {
            put("pencil_presets_v2", prefs.getString("pencil_presets_v2", null))
            put("pencil_cumulative_presets_v2", prefs.getString("pencil_cumulative_presets_v2", null))
            put("pen_presets_v2", prefs.getString("pen_presets_v2", null))
            put("paint_presets_v2", prefs.getString("paint_presets_v2", null))
            put("pluma_presets_v2", prefs.getString("pluma_presets_v2", null))
            put("watercolor_presets_v2", prefs.getString("watercolor_presets_v2", null))
        }

        prefs.edit().putString("tool_preset_group_data_$name", data.toString()).apply()
        
        _activeToolPresetGroupName.value = name
        prefs.edit().putString("active_tool_preset_group_name", name).apply()
    }

    fun loadToolPresetGroup(name: String) {
        if (name == "Default") {
            prefs.edit().apply {
                remove("pencil_presets_v2")
                remove("pencil_cumulative_presets_v2")
                remove("pen_presets_v2")
                remove("paint_presets_v2")
                remove("pluma_presets_v2")
                remove("watercolor_presets_v2")
                apply()
            }
        } else {
            val json = prefs.getString("tool_preset_group_data_$name", null) ?: return
            try {
                val obj = JSONObject(json)
                prefs.edit().apply {
                    listOf("pencil_presets_v2", "pencil_cumulative_presets_v2", "pen_presets_v2", "paint_presets_v2", "pluma_presets_v2", "watercolor_presets_v2").forEach { key ->
                        if (obj.has(key) && !obj.isNull(key)) {
                            putString(key, obj.getString(key))
                        } else {
                            remove(key)
                        }
                    }
                    apply()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        _activeToolPresetGroupName.value = name
        prefs.edit().putString("active_tool_preset_group_name", name).apply()

        // Reload configs and currently active presets
        reloadConfigs()
    }

    fun deleteToolPresetGroup(name: String) {
        val list = getToolPresetGroupNames().toMutableList()
        if (list.contains(name)) {
            list.remove(name)
            val jsonList = gson.toJson(list)
            prefs.edit().putString("tool_preset_group_names", jsonList).apply()
        }
        _toolPresetGroupNames.value = if (list.contains("Default")) list else listOf("Default") + list
        prefs.edit().remove("tool_preset_group_data_$name").apply()
        
        if (_activeToolPresetGroupName.value == name) {
            _activeToolPresetGroupName.value = "Default"
            prefs.edit().putString("active_tool_preset_group_name", "Default").apply()
            loadToolPresetGroup("Default")
        }
    }

    fun getIsStrokeColorPreset(tool: ToolType, presetIndex: Int): Boolean {
        return prefs.getBoolean("preset_stroke_explicit_${tool.name}_$presetIndex", false).not()
    }

    fun getExplicitStrokeColor(tool: ToolType, presetIndex: Int, defaultColor: Int): Int {
        return prefs.getInt("preset_stroke_color_${tool.name}_$presetIndex", defaultColor)
    }

    fun getExplicitStrokeStyle(tool: ToolType, presetIndex: Int): FillStyle? {
        val jsonStr = prefs.getString("preset_stroke_style_v2_${tool.name}_$presetIndex", null) ?: return null
        return try { jsonToFillStyle(jsonStr) } catch(e: Exception) { null }
    }

    fun setExplicitStrokeColorState(tool: ToolType, presetIndex: Int, explicit: Boolean, color: Int, style: FillStyle?) {
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
        updatePresetFlows()
    }

    fun getIsFillColorPreset(tool: ToolType, presetIndex: Int): Boolean {
        return prefs.getBoolean("preset_fill_explicit_${tool.name}_$presetIndex", false).not()
    }

    fun getExplicitFillColor(tool: ToolType, presetIndex: Int, defaultColor: Int): Int {
        return prefs.getInt("preset_fill_color_${tool.name}_$presetIndex", defaultColor)
    }

    fun getExplicitFillStyle(tool: ToolType, presetIndex: Int): FillStyle? {
        val jsonStr = prefs.getString("preset_fill_style_v2_${tool.name}_$presetIndex", null) ?: return null
        return try { jsonToFillStyle(jsonStr) } catch(e: Exception) { null }
    }

    fun setExplicitFillColorState(tool: ToolType, presetIndex: Int, explicit: Boolean, color: Int, style: FillStyle?) {
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
        updatePresetFlows()
    }

    fun updatePresetFlows() {
        val idx = _selectedPresetIndex.value ?: 0
        _isStrokeColorPreset.value = getIsStrokeColorPreset(currentTool, idx)
        _isFillColorPreset.value = getIsFillColorPreset(currentTool, idx)
    }

    fun revertToPresetColor(isStroke: Boolean) {
        val idx = _selectedPresetIndex.value ?: 0
        val list = _brushPresets.value
        if (idx in list.indices) {
            val preset = list[idx]
            if (isStroke) {
                prefs.edit().apply {
                    putBoolean("preset_stroke_explicit_${currentTool.name}_$idx", false)
                    remove("preset_stroke_color_${currentTool.name}_$idx")
                    remove("preset_stroke_style_v2_${currentTool.name}_$idx")
                    apply()
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
                prefs.edit().apply {
                    putBoolean("preset_fill_explicit_${currentTool.name}_$idx", false)
                    remove("preset_fill_color_${currentTool.name}_$idx")
                    remove("preset_fill_style_v2_${currentTool.name}_$idx")
                    apply()
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

    fun getToolStatesJson(): String {
        val map = mutableMapOf<String, Any>()
        map["currentTool"] = currentTool.name
        map["currentStrokeType"] = currentStrokeType.name
        
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

    fun restoreToolStatesFromJson(jsonStr: String) {
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
                    is Long -> editor.putLong(key, v)
                    is Double -> {
                        if (v % 1 == 0.0) {
                            editor.putInt(key, v.toInt())
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
                } catch(e: Exception) {}
            }
            
            val strokeTypeName = map["currentStrokeType"]?.toString()
            if (strokeTypeName != null) {
                try {
                    val strokeType = StrokeType.valueOf(strokeTypeName)
                    currentStrokeType = strokeType
                    editor.putString("current_stroke_type", strokeType.name).apply()
                } catch(e: Exception) {}
            }
            
            selectTool(currentTool)
            
        } catch(e: Exception) {
            e.printStackTrace()
        }
    }
}

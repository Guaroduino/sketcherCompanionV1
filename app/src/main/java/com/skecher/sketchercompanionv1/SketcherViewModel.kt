package com.skecher.sketchercompanionv1

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.ink.strokes.Stroke
import com.google.gson.Gson
import com.skecher.sketchercompanionv1.dto.ProjectData
import com.skecher.sketchercompanionv1.dto.BackgroundConfig
import com.skecher.sketchercompanionv1.dto.CanvasMetadata
import com.skecher.sketchercompanionv1.utils.TemplateManager
import java.io.File
import java.util.UUID
import com.skecher.sketchercompanionv1.dto.LayerJson
import com.skecher.sketchercompanionv1.dto.ScaleConfig
import com.skecher.sketchercompanionv1.dto.GridConfig
import com.skecher.sketchercompanionv1.dto.DistanceUnit
import com.skecher.sketchercompanionv1.utils.toLayerJson
import com.skecher.sketchercompanionv1.utils.toLayer
import com.skecher.sketchercompanionv1.utils.toComponentDefinitionJson
import com.skecher.sketchercompanionv1.utils.toComponentDefinition
import com.skecher.sketchercompanionv1.FillData
import com.skecher.sketchercompanionv1.AndroidInkElement
import java.util.ArrayDeque
import com.skecher.sketchercompanionv1.GroupElement
import com.skecher.sketchercompanionv1.Transformable


data class ToolConfig(
    val size: Float,
    val opacity: Float,
    val smoothing: Float,
    val sensitivity: Float,
    val minSizeFactor: Float
)

class SketcherViewModel(application: Application) : AndroidViewModel(application) {
    // Shared Prefs (Must be first)
    private val prefs = application.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)

    // STATE
    // Layers replaces _strokes

    // UNDO / REDO
    // Stack definitions moved below with Layout support

    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set
    
    // SCALE CONFIG
    var scaleConfig by mutableStateOf(ScaleConfig())
        private set

    // UNITS
    var currentUnit by mutableStateOf(DistanceUnit.MM)

    // GRID CONFIG (Default Spacing 50f, OFF)
    var gridConfig by mutableStateOf(GridConfig(spacing = 5f, isVisible = false))
    var isSnapToGridEnabled by mutableStateOf(false)

    // SETTINGS
    var isRotationLocked by mutableStateOf(prefs.getBoolean("rotation_lock", false))
    var isPalmRejectionEnabled by mutableStateOf(prefs.getBoolean("palm_rejection", false)) // "Stylus Only" Mode
    
    // Interface Scale (Persisted)
    var interfaceScale by mutableStateOf(prefs.getFloat("interface_scale", 1.0f))
        private set

    // BACKGROUND COLOR
    var backgroundColor by mutableIntStateOf(Color.WHITE)

    // Toolbar Appearance (Persisted)
    var toolbarBackgroundColor by mutableIntStateOf(prefs.getInt("toolbar_background_color", Color.WHITE))
    var toolbarAlpha by mutableStateOf(prefs.getFloat("toolbar_alpha", 0.9f))
    var isToolbarBlurEnabled by mutableStateOf(prefs.getBoolean("toolbar_blur_enabled", false))

    // PROJECT METADATA
    var projectId by mutableStateOf(UUID.randomUUID().toString())
    var currentFileUri: android.net.Uri? by mutableStateOf(null)

    // --- COMPONENTS & ISOLATION ---
    val componentLibrary = mutableMapOf<String, ComponentDefinition>()
    
    // editingContext tracks if we are inside a Group or Component Definition
    // Null = Main Layer, List = elements of a Group or Definition
    var editingContext by mutableStateOf<MutableList<LayerElement>?>(null)
        private set

    val activeContainer: MutableList<LayerElement>
        get() = editingContext ?: layers[activeLayerIndex].elements

    fun makeComponent() {
        if (selectionManager.selectedElements.isEmpty()) return
        
        saveStateForUndo()
        val elementsToComponent = selectionManager.selectedElements.toList()
        val defId = "comp_${UUID.randomUUID()}"
        val definition = ComponentDefinition(defId, elementsToComponent.map { it.copyElement() }.toMutableList())
        componentLibrary[defId] = definition
        
        // Remove from current container and add instance
        activeContainer.removeAll(elementsToComponent)
        val instance = ComponentInstance(
            id = "inst_${UUID.randomUUID()}",
            definitionId = defId
        )
        activeContainer.add(instance)
        
        selectionManager.clearSelection()
        if (editingContext == null) {
            layers[activeLayerIndex] = layers[activeLayerIndex].copy()
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    var editingContainerMatrix by mutableStateOf<Matrix?>(null)
        private set

    fun enterEditMode() {
        if (selectionManager.selectedElements.size != 1) return
        val selected = selectionManager.selectedElements.first()
        
        if (selected is GroupElement) {
            editingContext = selected.elements as? MutableList<LayerElement>
            editingContainerMatrix = Matrix(selected.matrix)
            selectionManager.clearSelection()
        } else if (selected is ComponentInstance) {
            val definition = componentLibrary[selected.definitionId]
            if (definition != null) {
                editingContext = definition.elements
                editingContainerMatrix = Matrix(selected.matrix)
                selectionManager.clearSelection()
            }
        }
    }

    fun exitEditMode() {
        editingContext = null
        editingContainerMatrix = null
        selectionManager.clearSelection()
    }


    // --- TOOL STATE & CONFIG ---
    var currentTool by mutableStateOf(ToolType.PRESSURE_PEN)
        private set
        
    var currentSize by mutableFloatStateOf(4f)
        private set
    var currentOpacity by mutableFloatStateOf(1f)
        private set
    var currentSmoothing by mutableFloatStateOf(0.4f)
        private set
    var currentSensitivity by mutableFloatStateOf(0.6f)
        private set
    var penMinSizeFactor by mutableFloatStateOf(0.4f)
        private set
        
    // Fill Specific (Global or per tool? "Fill Tool Color: Default Green". Implies per tool or separate state)
    // The request said: "Fill Tool ... Color Default: Green". 
    // Usually Color is global, but Fill Tool has specific requirement.
    // I'll keep color storage global but switch to Green if Fill Tool selected and not set??
    // Actually, "Fill Tool Color: Should default to Green when selected, unless user picked another."
    // I'll handle that in selectTool.
        
    // Tool Config Map
    private val toolConfigs = mutableMapOf<ToolType, ToolConfig>().apply {
        // Lápiz Técnico (Technical Pen)
        put(ToolType.TECHNICAL_PEN, ToolConfig(size = 9f, opacity = 1f, smoothing = 0.4f, sensitivity = 0.6f, minSizeFactor = 0.4f))
        // Lápiz (Pressure Pen)
        put(ToolType.PRESSURE_PEN, ToolConfig(size = 4f, opacity = 1f, smoothing = 0.0f, sensitivity = 1.0f, minSizeFactor = 0.4f)) // MinSize Default
        // Perfect Freehand
        put(ToolType.PERFECT_FREEHAND, ToolConfig(size = 9f, opacity = 1f, smoothing = 0.0f, sensitivity = 1.0f, minSizeFactor = 0.2f))
        // Marcador (Marker)
        put(ToolType.MARKER, ToolConfig(size = 9f, opacity = 0.6f, smoothing = 0.3f, sensitivity = 0.6f, minSizeFactor = 0.1f))
        // Resaltador (Highlighter)
        put(ToolType.HIGHLIGHTER, ToolConfig(size = 9f, opacity = 0.6f, smoothing = 0.3f, sensitivity = 0.6f, minSizeFactor = 0.1f))
        // Relleno (Fill)
        put(ToolType.FILL_SHAPE, ToolConfig(size = 1f, opacity = 1.0f, smoothing = 0.7f, sensitivity = 1f, minSizeFactor = 1f))
        // Borrador (Eraser) - Default
        put(ToolType.ERASER, ToolConfig(size = 30f, opacity = 1f, smoothing = 0f, sensitivity = 1f, minSizeFactor = 1f))
        // Selección (Selection)
        put(ToolType.SELECTION, ToolConfig(size = 1f, opacity = 1f, smoothing = 0f, sensitivity = 1f, minSizeFactor = 1f))
    }

    // --- SELECTION STATE ---
    val selectionManager = SelectionManager()
    enum class SelectionMode { RECTANGLE, FREEHAND, TRANSFORM_BOX }
    enum class SelectionScope { CURRENT_LAYER, ALL_LAYERS }
    var currentSelectionMode by mutableStateOf(SelectionMode.RECTANGLE)
    var selectionScope by mutableStateOf(SelectionScope.CURRENT_LAYER)
    var isSelectionAspectRatioLocked by mutableStateOf(true)

    val isGroupSelected: Boolean
        get() = selectionManager.selectedElements.size == 1 && (selectionManager.selectedElements.first() is GroupElement)

    val canEnterEditMode: Boolean
        get() = selectionManager.selectedElements.size == 1 && (selectionManager.selectedElements.first() is GroupElement || selectionManager.selectedElements.first() is ComponentInstance)

    val isSelectionEmpty: Boolean get() = selectionManager.selectedElements.isEmpty()

    fun deleteSelection() {
        if (selectionManager.selectedElements.isEmpty()) return
        saveStateForUndo()
        var changed = false
        layers.forEachIndexed { index, layer ->
            val initialSize = layer.elements.size
            layer.elements.removeAll(selectionManager.selectedElements)
            if (layer.elements.size != initialSize) {
                layers[index] = layer.copy()
                changed = true
            }
        }
        if (changed) {
            selectionManager.clearSelection()
            updateUndoRedoSupport()
        }
    }

    // --- GROUP / UNGROUP ---
    fun groupSelection() {
        val selected = selectionManager.selectedElements
        if (selected.isEmpty()) return
        
        saveStateForUndo()

        // 1. Create Group
        val group = GroupElement(
            id = UUID.randomUUID().toString(),
            elements = selected.toList(), // Copy list
            matrix = Matrix() // Start with identity
        )
        
        // 2. Remove originals from Active Layer (assuming single layer selection for now, or scan all)
        // Complexity: Selected items might be across layers? 
        // Current SelectionManager handles multi-layer? "SelectionScope { CURRENT_LAYER, ALL_LAYERS }"
        // If ALL_LAYERS, we might be pulling items from multiple layers.
        // Simplification: We add the group to the ACTIVE layer, and remove parts from their respective layers.
        
        var changed = false
        val activeLayer = layers[activeLayerIndex] // Default target for new group
        
        // Remove from source layers
        layers.forEachIndexed { index, layer ->
             if (layer.elements.removeAll(selected)) {
                 layers[index] = layer.copy() // Trigger update
                 changed = true
             }
        }
        
        if (changed || selected.isNotEmpty()) {
             // Add Group to Active Layer
             activeLayer.elements.add(group)
             layers[activeLayerIndex] = activeLayer.copy()
             
             // Update Selection
             selectionManager.clearSelection()
             selectionManager.selectedElements.add(group)
             selectionManager.recalculateBaseBounds(componentLibrary)
             
             updateUndoRedoSupport()
        }
    }



    fun ungroupSelection() {
        val selected = selectionManager.selectedElements
        // Only if we have distinct group elements selected
        val groups = selected.filterIsInstance<GroupElement>()
        if (groups.isEmpty()) return
        
        saveStateForUndo()
        
        var changed = false
        
        // For each group, we "explode" it
        groups.forEach { group ->
             // 1. Find which layer contains this group
             layers.forEachIndexed { params, layer ->
                 if (layer.elements.contains(group)) {
                     // 2. Remove Group
                     layer.elements.remove(group)
                     
                     // 3. Add Children back, with Transform applied
                     val children = group.elements.map { child ->
                         // If child is transformable, apply the group matrix
                         if (child is Transformable) {
                             // We need a deep copy or just modify if we own it now?
                             // child is technically owned by the group. 
                             // We should copy it to be safe, or just mutate if it's unique.
                             // LayerElement has `copyElement`.
                             val newChild = child.copyElement()
                             if (newChild is Transformable) {
                                  newChild.transform(group.matrix)
                             }
                             newChild
                         } else {
                             child // Should not happen based on types, but safe fallback
                         }
                     }
                     
                     layer.elements.addAll(children)
                     layers[params] = layer.copy()
                     changed = true
                     
                     // Update selection (add children)
                     // limitation: ConcurrentModification if we touch selectionManager.selectedElements here?
                     // We doing it outside loop? No, we filter before.
                 }
             }
        }
        
        if (changed) {
            selectionManager.clearSelection()
            // We could try to select the children, but finding them might be tricky unless we tracked them.
            // For now, clear selection is safe. Or we can collect all 'children' and select them.
            updateUndoRedoSupport()
        }
    }

    // --- CLIPBOARD ---
    private val clipboard = mutableListOf<LayerElement>()
    var canPaste by mutableStateOf(false)
        private set

    fun copy() {
        if (selectionManager.selectedElements.isEmpty()) return
        clipboard.clear()
        clipboard.addAll(selectionManager.selectedElements.map { it.copyElement() })
        canPaste = true
    }

    fun cut() {
        if (selectionManager.selectedElements.isEmpty()) return
        copy()
        deleteSelection()
    }

    fun paste() {
        if (clipboard.isEmpty()) return
        if (layers.isEmpty()) return
        
        saveStateForUndo()
        
        // Offset for pasted items to distinguish them
        val offset = 50f 
        val m = Matrix()
        m.postTranslate(offset, offset)
        
        val pasted = clipboard.map { 
            it.copyElement().apply { transform(m) } 
        }
        
        // Add to active layer
        val activeLayer = layers[activeLayerIndex]
        activeLayer.elements.addAll(pasted)
        layers[activeLayerIndex] = activeLayer.copy() // Trigger update
        
        // Select pasted items
        selectionManager.clearSelection()
        selectionManager.selectedElements.addAll(pasted)
        selectionManager.recalculateBaseBounds(componentLibrary)
        
        updateUndoRedoSupport()
    }

    // COLORS
    var availableColors = mutableStateListOf(Color.BLACK, Color.RED, Color.BLUE, Color.YELLOW)
    var selectedColorIndex by mutableIntStateOf(0)
    var currentColor by mutableIntStateOf(Color.BLACK)

    fun updateCurrentColorFromSlot() {
        if (selectedColorIndex in availableColors.indices) {
            currentColor = availableColors[selectedColorIndex]
        }
    }
    
    // Additional States moved from Surface
    var isFillModeEnabled by mutableStateOf(false)
    var fillModeColor by mutableIntStateOf(Color.GREEN) // Default Fill Color

    // Track last drawing tool for toggle back
    var lastDrawingTool by mutableStateOf(ToolType.PRESSURE_PEN) // Default to Pressure Pen

    // Select Tool Logic
    fun selectTool(type: ToolType) {
        // Save current config to map before switching?
        // OR do we update map immediately on setter?
        // The Prompt: "When selectTool(type) is called, update the observable UI state... with the values from that tool's config."
        // This implies the Map is the source of truth for "restoring".
        // I will implement setters to update map "live".
        
        // Update Last Drawing Tool if applicable
        if (type != ToolType.ERASER && type != ToolType.SELECTION && type != ToolType.FILL_SHAPE) {
            lastDrawingTool = type
        }

        currentTool = type
        val config = toolConfigs[type] ?: toolConfigs[ToolType.TECHNICAL_PEN]!!
        
        // Restore State
        currentSize = config.size
        currentOpacity = config.opacity
        currentSmoothing = config.smoothing
        currentSensitivity = config.sensitivity
        penMinSizeFactor = config.minSizeFactor
        
        // Color Logic for Fill Tool
        // User requested: "Preserve selected color for stroke and fill when switching tools".
        // We will NOT force a color change here.
        // The UI should use `fillModeColor` for Fill Tool actions and `currentColor` for others.
    }

    // Setters that persist
    fun setToolSize(size: Float) {
        currentSize = size
        toolConfigs[currentTool] = toolConfigs[currentTool]!!.copy(size = size)
    }
    
    fun setToolOpacity(opacity: Float) {
        currentOpacity = opacity
        toolConfigs[currentTool] = toolConfigs[currentTool]!!.copy(opacity = opacity)
    }
    
    fun setToolSmoothing(smoothing: Float) {
        currentSmoothing = smoothing
        toolConfigs[currentTool] = toolConfigs[currentTool]!!.copy(smoothing = smoothing)
    }
    
    fun setToolSensitivity(sensitivity: Float) {
        currentSensitivity = sensitivity
        toolConfigs[currentTool] = toolConfigs[currentTool]!!.copy(sensitivity = sensitivity)
    }
    
    fun setToolMinSizeFactor(factor: Float) {
        penMinSizeFactor = factor
        toolConfigs[currentTool] = toolConfigs[currentTool]!!.copy(minSizeFactor = factor)
    }

    // Vector Pen / Prediction Settings (Global or Config?)
    // Prompt said: "settings (size, opacity, smoothing) ... independent per tool".
    // "Min Size" also listed in prompt defaults.
    // "Sensitivity" also listed.
    // Prediction/Simplification seem global. I'll keep them global.
    var simplificationAngleThreshold by mutableFloatStateOf(prefs.getFloat("simplification_angle_threshold", 5f)) // 0f to 90f
    var predictionLagMs by mutableFloatStateOf(100f) // 0.0 to 100.0 (ms)
    var predictionSmoothing by mutableFloatStateOf(0.8f) // 0.0 (No Smooth) to 0.99 (Max Smooth)
    var predictionVelocityMin by mutableFloatStateOf(100f) // Threshold for Min Lag
    var predictionVelocityMax by mutableFloatStateOf(1000f) // Threshold for Max Lag
    var isDebugWireframe by mutableStateOf(false)
    var isPredictionEnabled by mutableStateOf(true) // Master toggle
    var isDebugPredictionEnabled by mutableStateOf(false)
    
    
    val cameraMatrixValues = FloatArray(9).apply { 
        Matrix().getValues(this) 
    }
    
    // --- FREEHAND SETTINGS ---
    var currentFreehandSettings by mutableStateOf(com.skecher.sketchercompanionv1.dto.FreehandSettings())
        private set
        
    fun updateFreehandSettings(newSettings: com.skecher.sketchercompanionv1.dto.FreehandSettings) {
        currentFreehandSettings = newSettings
    }
    
    // Zoom Controls
    fun resetCamera() {
        Matrix().getValues(cameraMatrixValues) // Reset to Identity
        // We need to trigger the view to update.
        // The View observes these values? 
        // In Surface: val cameraMatrix = remember { Matrix().apply { setValues(sketchViewModel.cameraMatrixValues) } }
        // The VM needs to singal a change.
        // Since we are not fully reactive with the Matrix object itself, we might need a flag or valid state.
        // But `selectTool` updates state vars.
        // For camera, I'll update the array, but I need the UI to pick it up.
        // I'll add a version counter or trigger.
        cameraUpdateTrigger++
    }
    
    var cameraUpdateTrigger by mutableIntStateOf(0)



    // SIZE PRESETS
    // Leemos 3 presets, default: 5, 15, 30
    private fun loadPresets(): List<Float> {
        return listOf(
            prefs.getFloat("size_preset_0", 5f),
            prefs.getFloat("size_preset_1", 15f),
            prefs.getFloat("size_preset_2", 30f)
        )
    }

    var brushSizePresets = mutableStateListOf<Float>().apply { addAll(loadPresets()) }
        private set

    fun updateBrushSizePreset(index: Int, size: Float) {
        if (index in 0 until brushSizePresets.size) {
            brushSizePresets[index] = size
            prefs.edit().putFloat("size_preset_$index", size).apply()
        }
    }




    // NUEVO: Guardamos el tamaño de pantalla para calcular el re-centrado
    var lastViewportWidth: Float = 0f
    var lastViewportHeight: Float = 0f
    
    // LAYERS STATE
    val layers = mutableStateListOf<Layer>().apply {
        add(Layer("layer_1", "Capa 1", mutableListOf()))
        add(Layer("layer_2", "Capa 2", mutableListOf()))
        add(Layer("layer_3", "Capa 3", mutableListOf()))
    }

    
    var activeLayerIndex by mutableIntStateOf(0)

    // LAYER MANAGEMENT
    fun toggleLayerVisibility(index: Int) {
        if (index in layers.indices) {
            val layer = layers[index]
            layers[index] = layer.copy(isVisible = !layer.isVisible)
        }
    }

    fun setLayerOpacity(index: Int, opacity: Float) {
        if (index in layers.indices) {
            layers[index] = layers[index].copy(opacity = opacity)
        }
    }

    fun setActiveLayer(index: Int) {
        if (index in layers.indices) {
            activeLayerIndex = index
        }
    }

    fun addNewLayer(toTop: Boolean) {
        saveStateForUndo()
        val newLayerName = "Capa ${layers.size + 1}"
        val newLayer = Layer("layer_${System.currentTimeMillis()}", newLayerName, mutableListOf())
        
        if (toTop) {
            layers.add(newLayer) // Add to end (Top of stack)
            activeLayerIndex = layers.lastIndex
        } else {
            layers.add(0, newLayer) // Add to start (Bottom of stack)
            activeLayerIndex = 0
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun removeActiveLayer() {
        if (layers.size <= 1) return // Prevent removing last layer
        
        saveStateForUndo()
        layers.removeAt(activeLayerIndex)
        
        // Adjust active index if out of bounds
        if (activeLayerIndex >= layers.size) {
            activeLayerIndex = layers.size - 1
        }
        
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun moveActiveLayerUp() {
        if (activeLayerIndex < layers.size - 1) {
            saveStateForUndo()
            val nextIndex = activeLayerIndex + 1
            // Swap
            val temp = layers[activeLayerIndex]
            layers[activeLayerIndex] = layers[nextIndex]
            layers[nextIndex] = temp
            
            activeLayerIndex = nextIndex
            
            redoStack.clear()
            updateUndoRedoSupport()
        }
    }

    fun moveActiveLayerDown() {
        if (activeLayerIndex > 0) {
            saveStateForUndo()
            val prevIndex = activeLayerIndex - 1
            // Swap
            val temp = layers[activeLayerIndex]
            layers[activeLayerIndex] = layers[prevIndex]
            layers[prevIndex] = temp
            
            activeLayerIndex = prevIndex
            
            redoStack.clear()
            updateUndoRedoSupport()
        }
    }

    // UNDO / REDO
    // Guardamos copias inmutables de la lista de capas
    // List<Layer> (Deep copy of contents needed)
    private val undoStack = ArrayDeque<List<Layer>>()
    private val redoStack = ArrayDeque<List<Layer>>()

    fun addStroke(stroke: Stroke) {
        saveStateForUndo()
        val finalStroke = editingContainerMatrix?.let { containerM ->
            val inverse = Matrix()
            containerM.invert(inverse)
            InkUtils.transformStroke(stroke, inverse)
        } ?: stroke
        
        activeContainer.add(AndroidInkElement(finalStroke))
        if (editingContext == null) {
            layers[activeLayerIndex] = layers[activeLayerIndex].copy()
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }
    
    fun addFill(fill: FillData) {
        saveStateForUndo() // We want to undo fills too now
        editingContainerMatrix?.let { containerM ->
            val inverse = Matrix()
            containerM.invert(inverse)
            fill.transform(inverse)
        }
        activeContainer.add(fill)
        if (editingContext == null) {
            layers[activeLayerIndex] = layers[activeLayerIndex].copy()
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }


    fun addVectorStroke(stroke: VectorStroke) {
        saveStateForUndo()
        editingContainerMatrix?.let { containerM ->
            val inverse = Matrix()
            containerM.invert(inverse)
            stroke.transform(inverse)
        }
        activeContainer.add(stroke)
        if (editingContext == null) {
            layers[activeLayerIndex] = layers[activeLayerIndex].copy()
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun insertImage(context: android.content.Context, uri: android.net.Uri) {
        // 1. Load Scaled Bitmap with Transparency
        val bitmap = com.skecher.sketchercompanionv1.utils.BitmapUtils.loadScaledBitmap(context, uri) ?: return
        
        // 2. Generate Filename (e.g. "img_<uuid>.png")
        val filename = "img_${java.util.UUID.randomUUID()}.png"
        
        insertImageWithBitmap(bitmap, filename)
    }

    fun insertSvg(context: android.content.Context, uri: android.net.Uri) {
         try {
             val inputStream = context.contentResolver.openInputStream(uri)
             val bytes = inputStream?.readBytes()
             inputStream?.close()
             
             if (bytes != null) {
                 val content = String(bytes, Charsets.UTF_8)
                 val filename = "vector_${java.util.UUID.randomUUID()}.svg"
                 
                 // Create Element
                 val svgElement = SvgElement(
                     id = java.util.UUID.randomUUID().toString(),
                     svgFileName = filename,
                     svgContent = content
                 )
                 
                 // Normalize Size (If dimensions are missing or weird)
                 val svg = svgElement.getSvg()
                 if (svg != null) {
                     // Check if document dimensions are valid (>0)
                     // If not, set ViewBox or Default
                     // AndroidSVG handles a lot, but if documentWidth is -1, we might need to set it.
                     // Our SvgElement.getBounds handles 0/0 fallback.
                 }

                 saveStateForUndo()
                 
                 if (activeLayerIndex in layers.indices) {
                     val layer = layers[activeLayerIndex]
                     
                     // Center logic
                     val matrix = android.graphics.Matrix()
                     // Get Bounds
                     val bounds = svgElement.getBounds(componentLibrary)
                     
                     if (lastViewportWidth > 0 && lastViewportHeight > 0) {
                        val cx = lastViewportWidth / 2f
                        val cy = lastViewportHeight / 2f
                        val cameraInv = android.graphics.Matrix()
                        val cameraM = android.graphics.Matrix()
                        cameraM.setValues(cameraMatrixValues)
                        cameraM.invert(cameraInv)
                        val centerPt = floatArrayOf(cx, cy)
                        cameraInv.mapPoints(centerPt)
                        
                        val wx = centerPt[0] - (bounds.width() / 2f)
                        val wy = centerPt[1] - (bounds.height() / 2f)
                        matrix.postTranslate(wx, wy)
                     }
                     
                     svgElement.transform(matrix)
                     
                     layer.elements.add(svgElement)
                     layers[activeLayerIndex] = layer.copy()
                     
                     selectionManager.clearSelection()
                     selectionManager.selectedElements.add(svgElement)
                     selectionManager.recalculateBaseBounds(componentLibrary)
                     
                     selectTool(ToolType.SELECTION)
                 }
                 redoStack.clear()
                 updateUndoRedoSupport()
             }
         } catch (e: Exception) {
             e.printStackTrace()
         }
    }

    // Interval helper or for direct bitmap usage
    fun insertImageWithBitmap(bitmap: android.graphics.Bitmap, filename: String) {
        undoStack.clear() // Or save state
        saveStateForUndo()
        
        if (activeLayerIndex in layers.indices) {
            val layer = layers[activeLayerIndex]
            
            // Center logic
            val matrix = android.graphics.Matrix()
            if (lastViewportWidth > 0 && lastViewportHeight > 0) {
                val cx = lastViewportWidth / 2f
                val cy = lastViewportHeight / 2f
                val cameraInv = android.graphics.Matrix()
                val cameraM = android.graphics.Matrix()
                cameraM.setValues(cameraMatrixValues)
                cameraM.invert(cameraInv)
                val centerPt = floatArrayOf(cx, cy)
                cameraInv.mapPoints(centerPt)
                val wx = centerPt[0] - (bitmap.width / 2f)
                val wy = centerPt[1] - (bitmap.height / 2f)
                matrix.postTranslate(wx, wy)
            }
            
            val imageElement = ImageElement(
                bitmap = bitmap,
                imageFileName = filename,
                matrix = matrix
            )
            
            layer.elements.add(imageElement)
            layers[activeLayerIndex] = layer.copy()
            
            selectionManager.clearSelection()
            selectionManager.selectedElements.add(imageElement)
            selectionManager.recalculateBaseBounds(componentLibrary)
            
            selectTool(ToolType.SELECTION)
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }
    
    // --- ZIP STORAGE METHODS ---
    // --- ZIP STORAGE METHODS ---
    fun saveProjectToZip(context: android.content.Context, uri: android.net.Uri) {
        try {
            // Generate ProjectData
            val projectData = ProjectData(
                id = projectId,
                layers = layers.map { it.toLayerJson() },
                backgroundConfig = BackgroundConfig(
                    color = backgroundColor,
                    gridConfig = gridConfig
                ),
                paletteColors = availableColors.toList(),
                toolConfigs = toolConfigs.toMap(),
                canvasMetadata = CanvasMetadata(
                    width = lastViewportWidth,
                    height = lastViewportHeight,
                    cameraMatrix = cameraMatrixValues.toList(),
                    scaleConfig = scaleConfig.copy(unitName = currentUnit.symbol)
                ),
                componentLibrary = componentLibrary.mapValues { it.value.toComponentDefinitionJson() }
            )
            
            com.skecher.sketchercompanionv1.utils.ZipStorageManager.saveProject(context, projectData, layers, uri)
            currentFileUri = uri
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadProjectFromZip(context: android.content.Context, uri: android.net.Uri) {
        try {
            val (projectData, bitmapMap, svgMap) = com.skecher.sketchercompanionv1.utils.ZipStorageManager.loadProject(context, uri)
            
            restoreProjectState(projectData, bitmapMap, svgMap)
            currentFileUri = uri
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    // --- TEMPLATE METHODS ---
    fun saveTemplate(context: android.content.Context, name: String) {
        try {
             val projectData = ProjectData(
                id = projectId, // ID is saved, but loadTemplate regenerates it
                layers = layers.map { it.toLayerJson() },
                backgroundConfig = BackgroundConfig(
                    color = backgroundColor,
                    gridConfig = gridConfig
                ),
                paletteColors = availableColors.toList(),
                toolConfigs = toolConfigs.toMap(),
                canvasMetadata = CanvasMetadata(
                    width = lastViewportWidth,
                    height = lastViewportHeight,
                    cameraMatrix = cameraMatrixValues.toList(),
                    scaleConfig = scaleConfig.copy(unitName = currentUnit.symbol)
                ),
                componentLibrary = componentLibrary.mapValues { it.value.toComponentDefinitionJson() }
            )
            TemplateManager.saveAsTemplate(context, projectData, layers, name)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun loadFromTemplate(context: android.content.Context, file: File) {
        try {
            val (projectData, bitmapMap, svgMap) = TemplateManager.loadTemplate(context, file)
            restoreProjectState(projectData, bitmapMap, svgMap)
            currentFileUri = null // Reset so it's treated as a new project
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    fun exportSvg(context: android.content.Context, uri: android.net.Uri) {
        try {
             // 1. Generate XML
             val projectData = com.skecher.sketchercompanionv1.dto.ProjectData(
                 id = projectId,
                 layers = emptyList(), // Not used for export usually, or we pass layers directly
                 backgroundConfig = BackgroundConfig(color = backgroundColor, gridConfig = gridConfig),
                 paletteColors = emptyList(),
                 toolConfigs = emptyMap(),
                 canvasMetadata = CanvasMetadata(
                     width = lastViewportWidth,
                     height = lastViewportHeight,
                     cameraMatrix = cameraMatrixValues.toList(),
                     scaleConfig = scaleConfig.copy(unitName = currentUnit.symbol)
                 ),
                 componentLibrary = componentLibrary.mapValues { it.value.toComponentDefinitionJson() }
             )
             
             // Generate content
             val svgString = com.skecher.sketchercompanionv1.utils.SvgExporter.export(projectData, layers)
             
             // 2. Write to File
             context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                 outputStream.write(svgString.toByteArray(Charsets.UTF_8))
             }
        } catch (e: Exception) {
             e.printStackTrace()
        }
    }

    private fun restoreProjectState(
        projectData: ProjectData,
        bitmapMap: Map<String, android.graphics.Bitmap>,
        svgMap: Map<String, String> = emptyMap()
    ) {
        // Clear State
        layers.clear()
        undoStack.clear()
        redoStack.clear()

        // Restore ID
        projectId = projectData.id

        // Restore Components
        componentLibrary.clear()
        projectData.componentLibrary.forEach { (id, json) ->
            componentLibrary[id] = json.toComponentDefinition(
                bitmapLoader = { fileName -> bitmapMap[fileName] },
                svgLoader = { fileName -> svgMap[fileName] }
            )
        }

        // Restore Layers
        projectData.layers.forEach { layerDto ->
            val layer = layerDto.toLayer(
                bitmapLoader = { fileName -> bitmapMap[fileName] },
                svgLoader = { fileName -> svgMap[fileName] }
            )
            layers.add(layer)
        }
        
        // Restore Global Props
        if (projectData.canvasMetadata.cameraMatrix.size == 9) {
           for (i in 0 until 9) {
               cameraMatrixValues[i] = projectData.canvasMetadata.cameraMatrix[i]
           }
        }
        
        // Restore Canvas Metadata
        lastViewportWidth = projectData.canvasMetadata.width
        lastViewportHeight = projectData.canvasMetadata.height
        val loadedScale = projectData.canvasMetadata.scaleConfig ?: ScaleConfig()
        scaleConfig = if (loadedScale.basePixelsPerMillimeter == 0f) {
             loadedScale.copy(basePixelsPerMillimeter = 5.0f)
        } else {
             loadedScale
        }
        currentUnit = DistanceUnit.fromSymbol(scaleConfig.unitName)
        
        // Restore Background & Grid
        backgroundColor = projectData.backgroundConfig.color
        val loadedGrid = projectData.backgroundConfig.gridConfig
        gridConfig = loadedGrid ?: GridConfig()

        // Restore Palette
        if (projectData.paletteColors.isNotEmpty()) {
            availableColors.clear()
            availableColors.addAll(projectData.paletteColors)
            selectedColorIndex = 0
            updateCurrentColorFromSlot()
        }

        // Restore Tool Configs
        if (projectData.toolConfigs.isNotEmpty()) {
            toolConfigs.putAll(projectData.toolConfigs)
            // Refresh current tool
            selectTool(currentTool)
        }
        
        if (layers.isNotEmpty()) {
            activeLayerIndex = 0
            // Ensure bounds selection is cleared or updated
             selectionManager.clearSelection()
        }

        updateUndoRedoSupport()
        // Trigger Camera Reset or Update?
        cameraUpdateTrigger++
    }



    fun removeStroke(stroke: Stroke) {
        saveStateForUndo()
        // Find and remove
        for (i in layers.indices) {
             val layer = layers[i]
             // We need to find the Wrapper with this stroke
             val iterator = layer.elements.iterator()
             var found = false
             while (iterator.hasNext()) {
                 val el = iterator.next()
                 if (el is AndroidInkElement && el.stroke == stroke) {
                     iterator.remove()
                     found = true
                     break
                 }
             }
             
             if (found) {
                 // Fix: Replace layer with copy to trigger Compose Recomposition
                 layers[i] = layer.copy()
                 break
             }
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun removeFill(fill: FillData) {
        saveStateForUndo()
        for (i in layers.indices) {
            val layer = layers[i]
            if (layer.elements.remove(fill)) {
                // Fix: Replace layer with copy to trigger Compose Recomposition
                layers[i] = layer.copy()
                break
            }
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }


    fun removeVectorStroke(stroke: VectorStroke) {
        saveStateForUndo()
        for (i in layers.indices) {
            val layer = layers[i]
            if (layer.elements.remove(stroke)) {
                // Fix: Replace layer with copy to trigger Compose Recomposition
                layers[i] = layer.copy()
                break
            }
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun erase(x: Float, y: Float, radius: Float): Boolean {
        if (activeLayerIndex !in layers.indices) return false
        
        val activeLayer = layers[activeLayerIndex]
        val iterator = activeLayer.elements.listIterator(activeLayer.elements.size)
        var erased = false
        
        while (iterator.hasPrevious()) {
            val element = iterator.previous()
            var shouldRemove = false
            
            when (element) {
                is VectorStroke -> {
                    // Use existing stroke hit logic (assuming VectorUtils or similar exists, or implementing custom)
                    // Previous code didn't have specific VectorStroke hit test in Utils, but used StrokeGeometry for Ink.
                    // For VectorStroke, we can check points or path.
                    // Let's implement a basic hit test: distance to any point < radius + width/2
                    // OR better: check against segments.
                    // We'll use a simple point proximity check for now or path bounds.
                    // User snippet: VectorUtils.isStrokeHit(element, x, y, radius)
                    // I need to ADD isStrokeHit to VectorUtils OR use local logic.
                    // To avoid editing VectorUtils again if not needed, I'll inline a simple check or call a helper.
                    // Actually, let's assume I check segments.
                    
                    // Simple Segment Check
                    shouldRemove = isVectorStrokeHit(element, x, y, radius)
                }
                is FillData -> {
                    // Use new fill hit logic
                     shouldRemove = com.skecher.sketchercompanionv1.utils.VectorUtils.isFillHit(element, x, y, radius)
                }
                is AndroidInkElement -> {
                    // Use Android Ink's internal hit test
                    // StrokeGeometry.isStrokeTouched(element.stroke, x, y)
                     shouldRemove = com.skecher.sketchercompanionv1.StrokeGeometry.isStrokeTouched(element.stroke, x, y, radius)
                }
                is ImageElement -> {
                    val bounds = element.getBounds(componentLibrary)
                    shouldRemove = bounds.contains(x, y)
                }
                is SvgElement -> {
                    val bounds = element.getBounds(componentLibrary)
                    shouldRemove = bounds.contains(x, y)
                }
                is GroupElement -> {
                    val bounds = element.getBounds(componentLibrary)
                    shouldRemove = bounds.contains(x, y)
                }
                is ComponentInstance -> {
                    val bounds = element.getBounds(componentLibrary)
                    shouldRemove = bounds.contains(x, y)
                }
                else -> {}
            }

            if (shouldRemove) {
                saveStateForUndo()
                iterator.remove()
                // Update State to trigger UI
                layers[activeLayerIndex] = activeLayer.copy()
                erased = true
                redoStack.clear()
                updateUndoRedoSupport()
                return true // Erase one at a time (closest to top)
            }
        }
        return false
    }

    private fun isVectorStrokeHit(stroke: VectorStroke, x: Float, y: Float, radius: Float): Boolean {
        val hitThreshold = radius + (stroke.maxWidth / 2f)
        
        // Quick Bounds Check
        val bounds = android.graphics.RectF()
        stroke.path.computeBounds(bounds, true)
        bounds.inset(-hitThreshold, -hitThreshold)
        if (!bounds.contains(x, y)) return false

        // Detailed Segment Check
        if (stroke.points.size < 2) return false
        
        for (i in 0 until stroke.points.size - 1) {
            val p1 = stroke.points[i]
            val p2 = stroke.points[i + 1]
            
            // Distance from point (x,y) to segment (p1, p2)
            val dist = distanceFromPointToSegment(x, y, p1.x, p1.y, p2.x, p2.y)
            if (dist <= hitThreshold) return true
        }
        return false
    }

    private fun distanceFromPointToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) {
            return kotlin.math.hypot(px - x1, py - y1)
        }
        
        // Project point onto line, clamped to segment
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
        val tClamped = t.coerceIn(0f, 1f)
        
        val nearestX = x1 + tClamped * dx
        val nearestY = y1 + tClamped * dy
        
        return kotlin.math.hypot(px - nearestX, py - nearestY)
    }

    private fun saveStateForUndo() {
        undoStack.push(createLayersSnapshot())
        // Limit stack size if needed (e.g. 50 steps)
        if (undoStack.size > 50) undoStack.removeLast()
    }


    fun undo() {
        if (undoStack.isEmpty()) return
        
        saveCurrentStateToRedo()
        val previousState = undoStack.pop()
        
        restoreState(previousState)
        
        updateUndoRedoSupport()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        
        saveCurrentStateToUndoStacksOnly() // Don't clear redo
        val futureState = redoStack.pop()
        
        restoreState(futureState)
        
        updateUndoRedoSupport()
    }
    
    private fun saveCurrentStateToRedo() {
        redoStack.push(createLayersSnapshot())
    }


    private fun saveCurrentStateToUndoStacksOnly() {
        undoStack.push(createLayersSnapshot())
    }

    private fun createLayersSnapshot(): List<Layer> {
        return layers.map { layer ->
            layer.copy(
                elements = layer.elements.map { it.copyElement() }.toMutableList()
            )
        }
    }

    
    private fun restoreState(state: List<Layer>) {
        layers.clear()
        state.forEach { savedLayer ->
            // Deep copy back
            layers.add(savedLayer.copy(
                elements = ArrayList(savedLayer.elements)
            ))
        }
    }


    private fun updateUndoRedoSupport() {
        canUndo = undoStack.isNotEmpty()
        canRedo = redoStack.isNotEmpty()
    }
    
    // Setting Updates
    fun updateInterfaceScale(scale: Float) {
        interfaceScale = scale
        prefs.edit().putFloat("interface_scale", scale).apply()
    }

    fun updateToolbarBackgroundColor(color: Int) {
        toolbarBackgroundColor = color
        prefs.edit().putInt("toolbar_background_color", color).apply()
    }

    fun updateToolbarAlpha(alpha: Float) {
        toolbarAlpha = alpha
        prefs.edit().putFloat("toolbar_alpha", alpha).apply()
    }

    fun toggleToolbarBlur() {
        isToolbarBlurEnabled = !isToolbarBlurEnabled
        prefs.edit().putBoolean("toolbar_blur_enabled", isToolbarBlurEnabled).apply()
    }
    
    fun updateScaleConfig(unit: String, basePixelsPerMillimeter: Float) {
        scaleConfig = ScaleConfig(unit, basePixelsPerMillimeter)
        currentUnit = DistanceUnit.fromSymbol(unit)
    }

    fun updateGridConfig(isVisible: Boolean, spacing: Float, color: Int, secondaryColor: Int, tertiaryColor: Int) {
        gridConfig = GridConfig(isVisible, spacing, color, secondaryColor, tertiaryColor)
    }
    
    fun setUnit(unit: DistanceUnit) {
        currentUnit = unit
        // Sync scale config unit name
        scaleConfig = scaleConfig.copy(unitName = unit.symbol)
    }

    fun toggleRotationLock() {
        isRotationLocked = !isRotationLocked
        prefs.edit().putBoolean("rotation_lock", isRotationLocked).apply()
    }
    
    fun togglePalmRejection() {
        isPalmRejectionEnabled = !isPalmRejectionEnabled
        prefs.edit().putBoolean("palm_rejection", isPalmRejectionEnabled).apply()
    }

    fun saveCameraState(matrix: Matrix) {
        matrix.getValues(cameraMatrixValues)
    }
    
    // NUEVO: Actualiza dimensiones
    fun saveDimensions(w: Float, h: Float) {
        lastViewportWidth = w
        lastViewportHeight = h
    }

    fun fitContent() {
        if (layers.all { it.elements.isEmpty() }) {
            resetCamera()
            return
        }

        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var hasContent = false

        layers.forEach { layer ->
            layer.elements.forEach { element ->
                val bounds = element.getBounds(componentLibrary)
                if (bounds.left < minX) minX = bounds.left
                if (bounds.right > maxX) maxX = bounds.right
                if (bounds.top < minY) minY = bounds.top
                if (bounds.bottom > maxY) maxY = bounds.bottom
                hasContent = true
            }
        }

        if (!hasContent) {
            resetCamera()
            return
        }
        
        // Add Padding (10%)
        val contentW = maxX - minX
        val contentH = maxY - minY
        
        if (contentW <= 0 || contentH <= 0 || lastViewportWidth <= 0 || lastViewportHeight <= 0) {
            return
        }
        
        val paddingX = contentW * 0.1f
        val paddingY = contentH * 0.1f
        val targetW = contentW + paddingX * 2
        val targetH = contentH + paddingY * 2
        
        val scaleX = lastViewportWidth / targetW
        val scaleY = lastViewportHeight / targetH
        val scale = kotlin.math.min(scaleX, scaleY).coerceAtMost(3.0f).coerceAtLeast(0.1f)
        
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        
        // Calculate Matrix: Translate Center to 0, Scale, Translate to Viewport Center
        val m = Matrix()
        m.postTranslate(-centerX, -centerY)
        m.postScale(scale, scale)
        m.postTranslate(lastViewportWidth / 2f, lastViewportHeight / 2f)
        
        m.getValues(cameraMatrixValues)
        cameraUpdateTrigger++
    }

    fun clear() {
        // No Undo for Clear (Destructive) - Or maybe we should? 
        // User asked: "Reset the app state to start a fresh drawing"
        // Usually "New File" clears history.
        
        layers.clear()
        // Default layers
        layers.add(Layer("layer_${System.currentTimeMillis()}_1", "Capa 1", mutableListOf()))
        layers.add(Layer("layer_${System.currentTimeMillis()}_2", "Capa 2", mutableListOf()))
        layers.add(Layer("layer_${System.currentTimeMillis()}_3", "Capa 3", mutableListOf()))

        
        activeLayerIndex = 0
        
        // Reset Camera
        Matrix().getValues(cameraMatrixValues)
        
        // Reset Background
        backgroundColor = Color.WHITE

        // Reset Scale
        scaleConfig = ScaleConfig()
        
        undoStack.clear()
        redoStack.clear()
        updateUndoRedoSupport()
    }

    // --- SAVE AND LOAD ---
    

}


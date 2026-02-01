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
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.ink.strokes.Stroke
import com.google.gson.Gson
import com.skecher.sketchercompanionv1.dto.ProjectJson
import com.skecher.sketchercompanionv1.dto.LayerJson
import com.skecher.sketchercompanionv1.dto.ScaleConfig
import com.skecher.sketchercompanionv1.dto.GridConfig
import com.skecher.sketchercompanionv1.dto.DistanceUnit
import com.skecher.sketchercompanionv1.utils.toLayerJson
import com.skecher.sketchercompanionv1.utils.toLayer
import java.util.ArrayDeque

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

    // GRID CONFIG
    var gridConfig by mutableStateOf(GridConfig())

    // SETTINGS
    var isRotationLocked by mutableStateOf(prefs.getBoolean("rotation_lock", false))
    var isPalmRejectionEnabled by mutableStateOf(prefs.getBoolean("palm_rejection", false)) // "Stylus Only" Mode
    
    // Interface Scale (Persisted)
    var interfaceScale by mutableStateOf(prefs.getFloat("interface_scale", 1.0f))
        private set

    // BACKGROUND COLOR
    var backgroundColor by mutableIntStateOf(Color.WHITE)

    // VECTOR PEN SETTINGS
    var penMinSizeFactor by mutableStateOf(0.0f) // 0.0 to 1.0 (0% to 100% min width)
    var simplificationAngleThreshold by mutableStateOf(prefs.getFloat("simplification_angle_threshold", 5f)) // 0f to 90f
    var predictionLagMs by mutableStateOf(100f) // 0.0 to 100.0 (ms)
    var predictionSmoothing by mutableStateOf(0.8f) // 0.0 (No Smooth) to 0.99 (Max Smooth)
    var predictionVelocityMin by mutableStateOf(100f) // Threshold for Min Lag
    var predictionVelocityMax by mutableStateOf(1000f) // Threshold for Max Lag
    var isDebugWireframe by mutableStateOf(false)
    var isPredictionEnabled by mutableStateOf(true) // Master toggle
    var isDebugPredictionEnabled by mutableStateOf(false)

    // POLYGON SWEEPER SETTINGS
    var polygonSides by mutableIntStateOf(5) // Range: 3 to 10 (3=Triangle, 4=Square, 5=Pentagon, 6=Hexagon, etc.)
    var polygonRotationSpeed by mutableStateOf(0.5f) // Radians per pixel traveled
    var isPolygonRandomRotation by mutableStateOf(false) // Add jitter to rotation for organic effects

    val cameraMatrixValues = FloatArray(9).apply { 
        Matrix().getValues(this) 
    }

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
        add(Layer("layer_1", "Capa 1", mutableListOf(), mutableListOf()))
        add(Layer("layer_2", "Capa 2", mutableListOf(), mutableListOf()))
        add(Layer("layer_3", "Capa 3", mutableListOf(), mutableListOf()))
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
        val newLayer = Layer("layer_${System.currentTimeMillis()}", newLayerName, mutableListOf(), mutableListOf())
        
        if (toTop) {
            layers.add(newLayer) // Add to end (Top of stack)
            activeLayerIndex = layers.lastIndex
        } else {
            layers.add(0, newLayer) // Add to start (Bottom of stack)
            activeLayerIndex = 0
            // activeLayerIndex shifts for others? If I was at 0, I'm now at 1. 
            // Better behavior: Select the new layer.
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
        if (activeLayerIndex in layers.indices) {
            val layer = layers[activeLayerIndex]
            layer.inkStrokes.add(stroke)
            // Fix: Replace layer with copy to trigger Compose Recomposition
            layers[activeLayerIndex] = layer.copy()
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }
    
    fun addFill(fill: FillData) {
        saveStateForUndo() // We want to undo fills too now
        if (activeLayerIndex in layers.indices) {
            val layer = layers[activeLayerIndex]
            layer.fills.add(fill)
            // Fix: Replace layer with copy to trigger Compose Recomposition
            layers[activeLayerIndex] = layer.copy()
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun addVectorStroke(stroke: VectorStroke) {
        saveStateForUndo()
        if (activeLayerIndex in layers.indices) {
            val layer = layers[activeLayerIndex]
            layer.vectorStrokes.add(stroke)
            layers[activeLayerIndex] = layer.copy()
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun removeStroke(stroke: Stroke) {
        saveStateForUndo()
        // Find and remove
        for (i in layers.indices) {
             val layer = layers[i]
             if (layer.inkStrokes.remove(stroke)) {
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
            if (layer.fills.remove(fill)) {
                // Fix: Replace layer with copy to trigger Compose Recomposition
                layers[i] = layer.copy()
                break
            }
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    private fun saveStateForUndo() {
        val snapshot = layers.map { layer ->
            layer.copy(
                inkStrokes = ArrayList(layer.inkStrokes),
                fills = ArrayList(layer.fills),
                vectorStrokes = ArrayList(layer.vectorStrokes)
                // properties copied automatically
            )
        }
        undoStack.push(snapshot)
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
         val snapshot = layers.map { layer ->
            layer.copy(
                inkStrokes = ArrayList(layer.inkStrokes),
                fills = ArrayList(layer.fills),
                vectorStrokes = ArrayList(layer.vectorStrokes)
            )
        }
        redoStack.push(snapshot)
    }

    private fun saveCurrentStateToUndoStacksOnly() {
         val snapshot = layers.map { layer ->
            layer.copy(
                inkStrokes = ArrayList(layer.inkStrokes),
                fills = ArrayList(layer.fills),
                vectorStrokes = ArrayList(layer.vectorStrokes)
            )
        }
        undoStack.push(snapshot)
    }
    
    private fun restoreState(state: List<Layer>) {
        layers.clear()
        state.forEach { savedLayer ->
            // Deep copy back
            layers.add(savedLayer.copy(
                inkStrokes = ArrayList(savedLayer.inkStrokes),
                fills = ArrayList(savedLayer.fills),
                vectorStrokes = ArrayList(savedLayer.vectorStrokes)
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

    fun clear() {
        // No Undo for Clear (Destructive) - Or maybe we should? 
        // User asked: "Reset the app state to start a fresh drawing"
        // Usually "New File" clears history.
        
        layers.clear()
        // Default layers
        layers.add(Layer("layer_${System.currentTimeMillis()}_1", "Capa 1", mutableListOf(), mutableListOf()))
        layers.add(Layer("layer_${System.currentTimeMillis()}_2", "Capa 2", mutableListOf(), mutableListOf()))
        layers.add(Layer("layer_${System.currentTimeMillis()}_3", "Capa 3", mutableListOf(), mutableListOf()))
        
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
    
    fun getProjectJson(): String {
        // Create Snapshot of current state
        val projectDto = ProjectJson(
            version = 1,
            canvasWidth = lastViewportWidth,
            canvasHeight = lastViewportHeight,
            cameraMatrix = cameraMatrixValues.toList(),
            layers = layers.map { it.toLayerJson() },
            backgroundColor = backgroundColor,
            scaleConfig = scaleConfig.copy(unitName = currentUnit.symbol), // Ensure sync
            gridConfig = gridConfig
        )

        
        return Gson().toJson(projectDto)
    }

    fun loadProjectFromJson(json: String) {
        try {
            val projectDto = Gson().fromJson(json, ProjectJson::class.java)
            
            // Validate Version if needed
            
            // Clear current state NO undo for load (it's a reset)
            layers.clear()
            undoStack.clear()
            redoStack.clear()
            
            // Restore Layers
            projectDto.layers.forEach { layerDto ->
                layers.add(layerDto.toLayer())
            }
            
            // Restore Camera
            // We need to notify the View to update its matrix. 
            // The ViewModel holds the *values*, but the View holds the Matrix object.
            // We'll update the values here, and exposed them.
            // Ideally, we'd have a StateFlow for camera, but for now we update the array
            // and maybe expose a 'cameraResetTrigger'.
            // Actually, we can just update the array. The View might need to pull it.
            // Or better: The prompt implies just updating state. 
            // Verification step will check if this is sufficient.
            
            if (projectDto.cameraMatrix.size == 9) {
               for (i in 0 until 9) {
                   cameraMatrixValues[i] = projectDto.cameraMatrix[i]
               }
            }
            
            // Restore Background Color
            backgroundColor = projectDto.backgroundColor
            
            // Restore Scale
            // Restore Scale
            val loadedScale = projectDto.scaleConfig ?: ScaleConfig()
            // Migration: If basePixelsPerMillimeter is 0 (legacy json), force default
            scaleConfig = if (loadedScale.basePixelsPerMillimeter == 0f) {
                loadedScale.copy(basePixelsPerMillimeter = 5.0f)
            } else {
                loadedScale
            }
            currentUnit = DistanceUnit.fromSymbol(scaleConfig.unitName)
            
            // Restore Grid
            gridConfig = projectDto.gridConfig ?: GridConfig()

            // Restore Dimensions
            lastViewportWidth = projectDto.canvasWidth
            lastViewportHeight = projectDto.canvasHeight
            
            // Restore Active Index
            if (layers.isNotEmpty()) {
                activeLayerIndex = 0
            }

            updateUndoRedoSupport()
            
        } catch (e: Exception) {
            e.printStackTrace()
            // Handle error (maybe show toast via side effect)
        }
    }
}


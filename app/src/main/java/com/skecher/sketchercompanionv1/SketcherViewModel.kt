package com.skecher.sketchercompanionv1

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.ink.strokes.Stroke
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

    // SETTINGS
    var isRotationLocked by mutableStateOf(prefs.getBoolean("rotation_lock", false))
    var isPalmRejectionEnabled by mutableStateOf(prefs.getBoolean("palm_rejection", false)) // "Stylus Only" Mode
    
    // Interface Scale (Persisted)
    var interfaceScale by mutableStateOf(prefs.getFloat("interface_scale", 1.0f))
        private set

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
        add(Layer("Capa 1", mutableListOf(), mutableListOf()))
        add(Layer("Capa 2", mutableListOf(), mutableListOf()))
        add(Layer("Capa 3", mutableListOf(), mutableListOf()))
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
        val newLayer = Layer(newLayerName, mutableListOf(), mutableListOf())
        
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
            layers[activeLayerIndex].strokes.add(stroke)
            // Force update to trigger recomposition if needed?
            // MutableList inside MutableStateList triggers update? 
            // Not automatically for list content changes unless we notify.
            // But canvasView uses references. CanvasView.invalidate() handles visual update.
            // Compose UI might not need to know about stroke content changes, only layer list changes.
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }
    
    fun addFill(fill: FillData) {
        saveStateForUndo() // We want to undo fills too now
        if (activeLayerIndex in layers.indices) {
            layers[activeLayerIndex].fills.add(fill)
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun removeStroke(stroke: Stroke) {
        saveStateForUndo()
        // Find and remove
        for (layer in layers) {
             if (layer.strokes.remove(stroke)) break
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun removeFill(fill: FillData) {
        saveStateForUndo()
        for (layer in layers) {
            if (layer.fills.remove(fill)) break
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }

    private fun saveStateForUndo() {
        val snapshot = layers.map { layer ->
            layer.copy(
                strokes = ArrayList(layer.strokes),
                fills = ArrayList(layer.fills)
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
                strokes = ArrayList(layer.strokes),
                fills = ArrayList(layer.fills)
            )
        }
        redoStack.push(snapshot)
    }

    private fun saveCurrentStateToUndoStacksOnly() {
         val snapshot = layers.map { layer ->
            layer.copy(
                strokes = ArrayList(layer.strokes),
                fills = ArrayList(layer.fills)
            )
        }
        undoStack.push(snapshot)
    }
    
    private fun restoreState(state: List<Layer>) {
        layers.clear()
        state.forEach { savedLayer ->
            // Deep copy back
            layers.add(savedLayer.copy(
                strokes = ArrayList(savedLayer.strokes),
                fills = ArrayList(savedLayer.fills)
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
        saveStateForUndo()
        layers.forEach { 
            it.strokes.clear()
            it.fills.clear() 
        }
        redoStack.clear()
        updateUndoRedoSupport()
    }
}


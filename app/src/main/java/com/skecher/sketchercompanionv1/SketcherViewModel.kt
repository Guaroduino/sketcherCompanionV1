package com.skecher.sketchercompanionv1

import android.app.Application
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Matrix
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.ink.strokes.Stroke
import java.util.ArrayDeque

class SketcherViewModel(application: Application) : AndroidViewModel(application) {
    // Shared Prefs (Must be first)
    private val prefs = application.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE)

    // STATE
    private val _strokes = mutableListOf<Stroke>()
    val strokes: List<Stroke> get() = _strokes

    // UNDO / REDO
    // Guardamos copias inmutables de la lista de trazos
    private val undoStack = ArrayDeque<List<Stroke>>()
    private val redoStack = ArrayDeque<List<Stroke>>()

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

    // NUEVO: Guardamos el tamaño de pantalla para calcular el re-centrado
    var lastViewportWidth: Float = 0f
    var lastViewportHeight: Float = 0f

    fun addStroke(stroke: Stroke) {
        saveStateForUndo()
        _strokes.add(stroke)
        redoStack.clear()
        updateUndoRedoSupport()
    }

    fun removeStroke(stroke: Stroke) {
        saveStateForUndo()
        _strokes.remove(stroke)
        redoStack.clear()
        updateUndoRedoSupport()
    }

    private fun saveStateForUndo() {
        undoStack.push(ArrayList(_strokes))
        // Limit stack size if needed (e.g. 50 steps)
        if (undoStack.size > 50) undoStack.removeLast()
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        
        redoStack.push(ArrayList(_strokes))
        val previousState = undoStack.pop()
        
        _strokes.clear()
        _strokes.addAll(previousState)
        
        updateUndoRedoSupport()
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        
        undoStack.push(ArrayList(_strokes))
        val futureState = redoStack.pop()
        
        _strokes.clear()
        _strokes.addAll(futureState)
        
        updateUndoRedoSupport()
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
        _strokes.clear()
        redoStack.clear()
        updateUndoRedoSupport()
    }
}
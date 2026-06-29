# -*- coding: utf-8 -*-
import sys

with open('app/src/main/java/com/sketcher/sketchercompanionv1/SketcherViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

target = '''    fun updateTheme(newConfig: UiThemeConfig) {
        _themeConfig.value = newConfig
    }

    fun groupSelection() {'''

replacement = '''    fun updateTheme(newConfig: UiThemeConfig) {
        _themeConfig.value = newConfig
        themeRepository.saveTheme(newConfig)
    }

    // --- COMPONENTS & ISOLATION ---
    val componentLibrary = mutableMapOf<String, ComponentDefinition>()
    
    var editingContext by mutableStateOf<MutableList<LayerElement>?>(null)
        private set

    val activeContainer: MutableList<LayerElement>
        get() = editingContext ?: layerManager.activeElements()

    var editingContainerMatrix by mutableStateOf<Matrix?>(null)
        private set

    var editingParent by mutableStateOf<LayerElement?>(null)
        private set

    private var editingBackupElements: List<LayerElement>? = null

    val clipboard = mutableListOf<LayerElement>()

    var lastExportPngConfig by mutableStateOf(ExportPngConfig(transparentBackground = false, useHomeView = true, width = 1920, height = 1080))
    var lastExportSvgConfig by mutableStateOf(ExportSvgConfig(includeBackground = true, useHomeView = true, width = 1920f, height = 1080f))
    var dxfExportConfig by mutableStateOf(DxfExportConfig("", false))

    // --- TOOL STATE & CONFIG (Delegated to ToolManager) ---
    val currentTool: ToolType get() = toolManager.currentTool
    val currentStrokeType: StrokeType get() = toolManager.currentStrokeType
    
    val brushSize = toolManager.brushSize
    val brushOpacity = toolManager.brushOpacity
    val currentSize: Float get() = toolManager.currentSize
    val currentOpacity: Float get() = toolManager.currentOpacity
    val currentFreehandSettings: FreehandSettings get() = toolManager.currentFreehandSettings

    val strokeColor = toolManager.strokeColor
    val fillColor = toolManager.fillColor
    val isStrokeActive = toolManager.isStrokeActive
    val isFillActive = toolManager.isFillActive

    var isGeometricStrokeInProgress by mutableStateOf(false)
        private set

    fun updateGeometricStrokeInProgress(inProgress: Boolean) {
        isGeometricStrokeInProgress = inProgress
    }

    fun updateBrushSize(newSize: Float) = toolManager.updateBrushSize(newSize)
    fun updateBrushOpacity(newAlpha: Float) = toolManager.updateBrushOpacity(newAlpha)
    fun updateFillOpacity(opacity: Float) = toolManager.updateFillOpacity(opacity)
    fun updateStrokeType(type: StrokeType) = toolManager.updateStrokeType(type)
    
    fun setStrokeColor(color: Int) = toolManager.setStrokeColor(color)
    fun setFillColor(color: Int) = toolManager.setFillColor(color)
    fun toggleStroke(enabled: Boolean) = toolManager.toggleStroke(enabled)
    fun toggleFill(enabled: Boolean) = toolManager.toggleFill(enabled)
    
    fun selectTool(type: ToolType) {
        if (currentTool == ToolType.SELECTION && type != ToolType.SELECTION) {
            if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
                confirmTransform()
            }
        }
        toolManager.selectTool(type)
    }
    val brushPresets = toolManager.brushPresets
    val selectedPresetIndex = toolManager.selectedPresetIndex
    fun saveBrushPreset(index: Int) = toolManager.saveBrushPreset(index)
    fun selectBrushPreset(index: Int) = toolManager.selectBrushPreset(index)
    fun isPresetModified(index: Int): Boolean = toolManager.isPresetModified(index)

    // --- EXPOSED CONFIGS ---
    var fingerModeActive by mutableStateOf(false)
        private set
    var fingerOffsetXValue by mutableFloatStateOf(0f)
        private set
    var fingerOffsetYValue by mutableFloatStateOf(50f)
        private set

    var hasPreferencesBackup by mutableStateOf(false)
        private set

    init {
        hasPreferencesBackup = application.getSharedPreferences("sketcher_prefs_backup", Context.MODE_PRIVATE).all.isNotEmpty()
        selectTool(currentTool)
        
        val loaded = toolbarRepository.loadLayout()
        if (loaded != null) {
            _assignedTools.value = loaded.assignedMap
            _assignedToolColors.value = loaded.toolColors
            
            val toolsWithActions = loaded.tools.mapValues { (_, list) ->
                list.map { tool -> bindToolActions(tool) }
            }
            _toolbarState.value = toolsWithActions
            
            val contextualWithActions = loaded.contextualTools.map { tool ->
                tool.copy(onClick = {
                    getActionForTool(tool.registryId).invoke()
                })
            }
            _contextualToolbar.value = contextualWithActions
        } else {
            initToolbarState()
        }
    }

    fun updateContextualToolbar(newList: List<StudioTool>) {
        _contextualToolbar.value = newList
        toolbarRepository.saveLayout(_toolbarState.value, _assignedTools.value, _assignedToolColors.value, _contextualToolbar.value)
    }

    fun setFingerMode(enabled: Boolean) {
        fingerModeActive = enabled
        toolManager.setFingerMode(enabled)
    }

    fun setFingerOffset(x: Float, y: Float) {
        fingerOffsetXValue = x
        fingerOffsetYValue = y
        toolManager.setFingerOffset(x, y)
    }

    // --- SELECTION STATE ---
    enum class SelectionMode { RECTANGLE, FREEHAND, POLYGON, TRANSFORM_BOX }
    enum class SelectionScope { CURRENT_LAYER, ALL_LAYERS }
    var currentSelectionMode by mutableStateOf(SelectionMode.RECTANGLE)
    var selectionScope by mutableStateOf(SelectionScope.CURRENT_LAYER)
    var isSelectionAspectRatioLocked by mutableStateOf(true)

    val isGroupSelected: Boolean
        get() = selectionManager.selectedElements.size == 1 && (selectionManager.selectedElements.first() is GroupElement)

    val canEnterEditMode: Boolean
        get() = selectionManager.selectedElements.size == 1 && (selectionManager.selectedElements.first() is GroupElement || selectionManager.selectedElements.first() is ComponentInstance)

    val isSelectionEmpty: Boolean get() = selectionManager.selectedElements.isEmpty()
    val hasSelection: Boolean get() = selectionManager.hasSelection.value
    val selectionCount: Int get() = selectionManager.selectionCount.value

    fun clearSelection() {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
            confirmTransform()
        }
        selectionManager.clearSelection()
    }

    fun deleteSelection() {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
            confirmTransform()
        }
        val selected = selectionManager.selectedElements.toList()
        if (selected.isEmpty()) return
        
        performSnapshotAction("Borrar Seleccion") {
            if (editingContext != null) {
                activeContainer.removeAll(selected)
            } else {
                val currentLayers = layers.toMutableList()
                currentLayers.forEachIndexed { index, layer ->
                    val remaining = layer.elements.filter { it !in selected }
                    if (remaining.size != layer.elements.size) {
                        currentLayers[index] = layer.copy(elements = remaining.toMutableStateList())
                    }
                }
                layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            }
            selectionManager.clearSelection()
        }
    }

    fun duplicateSelection() { copySelectionToClipboard(); pasteFromClipboard() }

    fun copySelectionToClipboard() {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) confirmTransform()
        val selected = selectionManager.selectedElements.toList()
        if (selected.isEmpty()) return
        clipboard.clear()
        clipboard.addAll(selected.map { it.copyElement() })
    }
    
    fun cutSelectionToClipboard() {
        copySelectionToClipboard()
        deleteSelection()
    }
    
    fun pasteFromClipboard() {
        if (clipboard.isEmpty()) return
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) confirmTransform()
        
        performSnapshotAction("Pegar") {
            val offsetMatrix = Matrix().apply { postTranslate(20f, 20f) }
            val duplicatedElements = clipboard.map { 
                val copy = it.copyElement()
                if (copy is Transformable) copy.transform(offsetMatrix)
                copy
            }
            activeContainer.addAll(duplicatedElements)
            
            if (editingContext == null) {
                val currentLayers = layers.toMutableList()
                val activeLayer = currentLayers[activeLayerIndex]
                currentLayers[activeLayerIndex] = activeLayer.copy(
                    elements = (activeLayer.elements + duplicatedElements).toMutableStateList()
                )
                layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            } else {
                notifyLayersChanged()
            }
            
            selectionManager.selectedElements.clear()
            selectionManager.selectedElements.addAll(duplicatedElements)
            selectionManager.selectionMatrix.reset()
            selectionManager.recalculateBaseBounds(componentLibrary)
            
            clipboard.clear()
            clipboard.addAll(duplicatedElements.map { it.copyElement() })
        }
        enterTransformMode()
    }

    fun flipHorizontal() {
        if (selectionManager.selectedElements.isEmpty()) return
        if (currentSelectionMode != SelectionMode.TRANSFORM_BOX) {
            enterTransformMode()
        }
        selectionManager.flipHorizontal()
        notifyLayersChanged()
    }

    fun flipVertical() {
        if (selectionManager.selectedElements.isEmpty()) return
        if (currentSelectionMode != SelectionMode.TRANSFORM_BOX) {
            enterTransformMode()
        }
        selectionManager.flipVertical()
        notifyLayersChanged()
    }

    private var layersSnapshotBeforeTransform: List<Layer>? = null

    fun enterTransformMode() {
        if (selectionManager.selectedElements.isEmpty()) return
        selectTool(ToolType.SELECTION)
        layersSnapshotBeforeTransform = createLayersSnapshot()
        selectionManager.backupOriginalElements()
        currentSelectionMode = SelectionMode.TRANSFORM_BOX
    }

    fun confirmTransform() {
        val activeIndex = activeLayerIndex
        if (layers.isNotEmpty() && activeIndex in layers.indices) {
            selectionManager.commitTransformSession(layers[activeIndex], componentLibrary)
        }
        val before = layersSnapshotBeforeTransform
        if (before != null) {
            val activeIndexBefore = activeLayerIndex
            val after = createLayersSnapshot()
            performAction(SnapshotCommand("Transformar", before, after, activeIndexBefore))
        }
        layersSnapshotBeforeTransform = null
        selectionManager.clearBackup()
        currentSelectionMode = SelectionMode.FREEHAND
    }

    fun cancelTransform() {
        val activeIndex = activeLayerIndex
        if (layers.isNotEmpty() && activeIndex in layers.indices) {
            val activeLayer = layers[activeIndex]
            selectionManager.cancelTransformSession(activeLayer, componentLibrary)
        }
        layersSnapshotBeforeTransform = null
        selectionManager.clearBackup()
        currentSelectionMode = SelectionMode.FREEHAND
        notifyLayersChanged()
    }

    fun makeComponent() {
        if (selectionManager.selectedElements.isEmpty()) return
        
        performSnapshotAction("Crear Componente") {
            val elementsToComponent = selectionManager.selectedElements.toList()
            val defId = "comp_"
            val definition = ComponentDefinition(defId, elementsToComponent.map { it.copyElement() }.toMutableList())
            componentLibrary[defId] = definition
            
            activeContainer.removeAll(elementsToComponent)
            val instance = ComponentInstance(
                id = "inst_",
                definitionId = defId
            )
            activeContainer.add(instance)
            
            selectionManager.clearSelection()
            if (editingContext == null) {
                val newList = layers.toMutableList()
                newList[activeLayerIndex] = newList[activeLayerIndex].copy()
                layerManager.internalUpdateLayers(newList, activeLayerIndex)
            }
        }
    }

    fun enterEditMode() {
        if (selectionManager.selectedElements.size != 1) return
        val selected = selectionManager.selectedElements.first()
        
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
            confirmTransform()
        }
        
        if (selected is GroupElement) {
            editingContext = selected.elements.map { it.copyElement() }.toMutableList()
            editingBackupElements = selected.elements.map { it.copyElement() }
            editingParent = selected
            editingContainerMatrix = Matrix(selected.matrix)
            selectionManager.clearSelection()
            currentSelectionMode = SelectionMode.FREEHAND
            notifyLayersChanged()
        } else if (selected is ComponentInstance) {
            val definition = componentLibrary[selected.definitionId]
            if (definition != null) {
                editingContext = definition.elements
                editingBackupElements = definition.elements.map { it.copyElement() }
                editingParent = selected
                editingContainerMatrix = Matrix(selected.matrix)
                selectionManager.clearSelection()
                currentSelectionMode = SelectionMode.FREEHAND
                notifyLayersChanged()
            }
        }
    }

    fun exitEditMode() { 
        editingContext = null
        editingParent = null
        editingContainerMatrix = null
        editingBackupElements = null
        selectionManager.clearSelection()
        notifyLayersChanged()
    }

    fun cancelComponentEdit() {
        val backup = editingBackupElements
        val parent = editingParent
        if (backup != null && parent != null) {
            if (parent is GroupElement) {
                // Not saving changes
            } else if (parent is ComponentInstance) {
                val definition = componentLibrary[parent.definitionId]
                if (definition != null) {
                    definition.elements.clear()
                    definition.elements.addAll(backup)
                }
            }
        }
        editingBackupElements = null
        exitEditMode()
    }

    fun confirmComponentEdit() {
        editingBackupElements = null
        val parent = editingParent
        val ctx = editingContext
        if (parent is GroupElement && ctx != null) {
            val oldGroup = parent
            val newGroup = GroupElement(oldGroup.id, ctx.toList(), oldGroup.matrix)
            // Replace in container
            if (activeContainer.contains(oldGroup)) {
                val idx = activeContainer.indexOf(oldGroup)
                activeContainer[idx] = newGroup
            } else {
                // Should not happen normally
            }
        }
        exitEditMode()
    }

    fun groupSelection() {'''

if target in content:
    content = content.replace(target, replacement)
    with open('app/src/main/java/com/sketcher/sketchercompanionv1/SketcherViewModel.kt', 'w', encoding='utf-8') as f:
        f.write(content)
    print("Successfully restored missing ViewModel logic!")
else:
    print("Target not found.")


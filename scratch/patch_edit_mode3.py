# -*- coding: utf-8 -*-
import sys

with open('app/src/main/java/com/sketcher/sketchercompanionv1/SketcherViewModel.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Fix enterEditMode
old_enter_edit = '''    fun enterEditMode() {
        if (selectionManager.selectedElements.size != 1) return
        val selected = selectionManager.selectedElements.first()
        
        if (selected is GroupElement) {
            editingContext = selected.elements as? MutableList<LayerElement>
            editingBackupElements = selected.elements.map { it.copyElement() }
            editingParent = selected
            editingContainerMatrix = Matrix(selected.matrix)
            selectionManager.clearSelection()
            notifyLayersChanged()
        } else if (selected is ComponentInstance) {
            val definition = componentLibrary[selected.definitionId]
            if (definition != null) {
                editingContext = definition.elements
                editingBackupElements = definition.elements.map { it.copyElement() }
                editingParent = selected
                editingContainerMatrix = Matrix(selected.matrix)
                selectionManager.clearSelection()
                notifyLayersChanged()
            }
        }
    }'''

new_enter_edit = '''    fun enterEditMode() {
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
    }'''

if old_enter_edit in content:
    content = content.replace(old_enter_edit, new_enter_edit)
    print("enterEditMode patched.")
else:
    print("enterEditMode not found!")

# Add clipboard property
if "val clipboard = mutableListOf<LayerElement>()" not in content:
    content = content.replace('var isSingleImageSelected by mutableStateOf(false)\n        private set\n\n    var editingContext by mutableStateOf<MutableList<LayerElement>?>(null)', 'var isSingleImageSelected by mutableStateOf(false)\n        private set\n\n    val clipboard = mutableListOf<LayerElement>()\n\n    var editingContext by mutableStateOf<MutableList<LayerElement>?>(null)')
    print("Clipboard property added.")

# Replace duplicateSelection with copy/cut/paste
old_duplicate = '''    fun duplicateSelection() {
        if (currentSelectionMode == SelectionMode.TRANSFORM_BOX) {
            confirmTransform()
        }
        val selected = selectionManager.selectedElements.toList()
        if (selected.isEmpty()) return
        
        performSnapshotAction("Duplicar Selección") {
            val offsetMatrix = Matrix().apply { postTranslate(20f, 20f) }
            val duplicatedElements = selected.map { 
                val copy = it.copyElement()
                copy.transform(offsetMatrix)
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
        }
        enterTransformMode()
    }'''

new_clipboard = '''    fun duplicateSelection() { copySelectionToClipboard(); pasteFromClipboard() }

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
                copy.transform(offsetMatrix)
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
            
            // Re-copy to clipboard to allow multiple pastes with offset
            clipboard.clear()
            clipboard.addAll(duplicatedElements.map { it.copyElement() })
        }
        enterTransformMode()
    }'''

if old_duplicate in content:
    content = content.replace(old_duplicate, new_clipboard)
    print("Clipboard functions added.")
else:
    print("duplicateSelection not found!")

with open('app/src/main/java/com/sketcher/sketchercompanionv1/SketcherViewModel.kt', 'w', encoding='utf-8') as f:
    f.write(content)

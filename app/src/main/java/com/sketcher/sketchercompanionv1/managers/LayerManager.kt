package com.sketcher.sketchercompanionv1.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.sketcher.sketchercompanionv1.Layer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LayerManager(
    private val performSnapshotAction: (String, () -> Unit) -> Unit
) {
    private val _layers = MutableStateFlow<List<Layer>>(
        listOf(Layer("layer_1", "Capa 1", mutableListOf()))
    )
    val layers: StateFlow<List<Layer>> = _layers.asStateFlow()

    var activeLayerIndex by mutableIntStateOf(0)
        private set

    /**
     * Initializes the layers without triggering a snapshot operation, generally used for loading state.
     */
    fun internalUpdateLayers(newList: List<Layer>, activeIndex: Int) {
        _layers.value = newList
        activeLayerIndex = activeIndex.coerceIn(newList.indices)
    }

    fun toggleLayerVisibility(index: Int) {
        val currentList = _layers.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(isVisible = !currentList[index].isVisible)
            _layers.value = currentList
        }
    }

    fun setLayerOpacity(index: Int, opacity: Float) {
        val currentList = _layers.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(opacity = opacity)
            _layers.value = currentList
        }
    }

    fun setActiveLayer(index: Int) {
        if (index in _layers.value.indices) activeLayerIndex = index
    }

    fun addLayer() {
        addNewLayer(toTop = true)
    }

    fun addNewLayer(toTop: Boolean) {
        performSnapshotAction("Nueva Capa") {
            val newList = _layers.value.toMutableList()
            val l = Layer("l_${System.currentTimeMillis()}", "Capa ${newList.size + 1}", mutableListOf())
            if (toTop) {
                newList.add(l)
                activeLayerIndex = newList.lastIndex
            } else {
                newList.add(0, l)
                activeLayerIndex = 0
            }
            _layers.value = newList
        }
    }

    fun removeLayer(index: Int) {
        if (_layers.value.size <= 1) return
        performSnapshotAction("Eliminar Capa") {
            val newList = _layers.value.toMutableList()
            if (index in newList.indices) {
                newList.removeAt(index)
                _layers.value = newList
                if (activeLayerIndex >= _layers.value.size) {
                    activeLayerIndex = _layers.value.size - 1
                }
            }
        }
    }

    fun removeActiveLayer() {
        removeLayer(activeLayerIndex)
    }

    fun toggleLayerLock(index: Int) {
        val currentList = _layers.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(isLocked = !currentList[index].isLocked)
            _layers.value = currentList
        }
    }

    fun renameLayer(index: Int, newName: String) {
        val currentList = _layers.value.toMutableList()
        if (index in currentList.indices) {
            currentList[index] = currentList[index].copy(name = newName)
            _layers.value = currentList
        }
    }

    fun duplicateLayer(index: Int) {
        val currentList = _layers.value.toMutableList()
        if (index in currentList.indices) {
            performSnapshotAction("Duplicar Capa") {
                val source = currentList[index]
                val copy = source.copy(
                    id = "l_${System.currentTimeMillis()}",
                    name = "${source.name} (Copy)",
                    elements = source.elements.map { it.copyElement() }.toMutableList()
                )
                val newList = _layers.value.toMutableList()
                newList.add(index + 1, copy)
                _layers.value = newList
                activeLayerIndex = index + 1
            }
        }
    }

    fun moveLayer(fromIndex: Int, toIndex: Int) {
        val currentList = _layers.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices) {
            performSnapshotAction("Mover Capa") {
                val item = currentList.removeAt(fromIndex)
                currentList.add(toIndex, item)
                _layers.value = currentList
                // Keep the same layer active
                if (activeLayerIndex == fromIndex) activeLayerIndex = toIndex
                else if (fromIndex < activeLayerIndex && toIndex >= activeLayerIndex) activeLayerIndex--
                else if (fromIndex > activeLayerIndex && toIndex <= activeLayerIndex) activeLayerIndex++
            }
        }
    }

    fun moveLayerUp(index: Int) {
        moveLayer(index, index + 1)
    }

    fun moveLayerDown(index: Int) {
        moveLayer(index, index - 1)
    }

    fun addLayerAbove(index: Int) {
        performSnapshotAction("Nueva Capa Arriba") {
            val newList = _layers.value.toMutableList()
            if (index in newList.indices) {
                val l = Layer("l_${System.currentTimeMillis()}", "Capa ${newList.size + 1}", mutableListOf())
                newList.add(index + 1, l)
                _layers.value = newList
                activeLayerIndex = index + 1
            }
        }
    }

    fun addLayerBelow(index: Int) {
        performSnapshotAction("Nueva Capa Abajo") {
            val newList = _layers.value.toMutableList()
            if (index in newList.indices) {
                val l = Layer("l_${System.currentTimeMillis()}", "Capa ${newList.size + 1}", mutableListOf())
                newList.add(index, l)
                _layers.value = newList
                activeLayerIndex = index
            }
        }
    }

    fun mergeLayers(fromIndex: Int, toIndex: Int) {
        val currentList = _layers.value.toMutableList()
        if (fromIndex in currentList.indices && toIndex in currentList.indices && fromIndex != toIndex) {
            performSnapshotAction("Fusionar Capas") {
                val fromLayer = currentList[fromIndex]
                val toLayer = currentList[toIndex]

                // Transfer elements
                toLayer.elements.addAll(fromLayer.elements.map { it.copyElement() })

                // Remove source
                currentList.removeAt(fromIndex)
                _layers.value = currentList

                // Update active index
                val newToIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex
                activeLayerIndex = newToIndex.coerceIn(currentList.indices)
            }
        }
    }

    fun mergeLayerUp(index: Int) {
        mergeLayers(index, index + 1)
    }

    fun mergeLayerDown(index: Int) {
        mergeLayers(index, index - 1)
    }

    /**
     * For internal ViewModel logic when directly mutating elements in the current active layer.
     */
    fun activeElements(): MutableList<com.sketcher.sketchercompanionv1.LayerElement> {
        return _layers.value[activeLayerIndex].elements
    }

    /**
     * Re-assigns the shallow copy for triggering recompositions properly.
     */
    fun triggerLayerEmission() {
        _layers.value = _layers.value.toList()
    }
}

package com.sketcher.sketchercompanionv1.managers

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.LayerElement
import com.sketcher.sketchercompanionv1.VectorStroke
import com.sketcher.sketchercompanionv1.FillData
import com.sketcher.sketchercompanionv1.TextElement
import com.sketcher.sketchercompanionv1.GroupElement
import com.sketcher.sketchercompanionv1.dto.copyWithOpacity
class LayerManager(
    private val performSnapshotAction: (String, () -> Unit) -> Unit
) {
    val layers = mutableStateListOf<Layer>(
        Layer("layer_1", "Capa 1", mutableStateListOf())
    )

    var activeLayerIndex by mutableIntStateOf(0)
        private set

    /**
     * Initializes the layers without triggering a snapshot operation, generally used for loading state.
     */
    fun internalUpdateLayers(newList: List<Layer>, activeIndex: Int) {
        layers.clear()
        layers.addAll(newList)
        activeLayerIndex = if (newList.isEmpty()) 0 else activeIndex.coerceIn(newList.indices)
    }

    fun toggleLayerVisibility(index: Int) {
        if (index in layers.indices) {
            layers[index] = layers[index].copy(isVisible = !layers[index].isVisible)
        }
    }

    fun toggleLayerClientVisibility(index: Int) {
        if (index in layers.indices) {
            layers[index] = layers[index].copy(isVisibleOnClient = !layers[index].isVisibleOnClient)
        }
    }

    fun setLayerOpacity(index: Int, opacity: Float) {
        if (index in layers.indices) {
            layers[index] = layers[index].copy(opacity = opacity)
        }
    }

    fun setActiveLayer(index: Int) {
        if (index in layers.indices) activeLayerIndex = index
    }

    fun addLayer() {
        addNewLayer(toTop = true)
    }

    fun addNewLayer(toTop: Boolean) {
        performSnapshotAction("Nueva Capa") {
            val l = Layer("l_${System.currentTimeMillis()}", "Capa ${layers.size + 1}", mutableStateListOf())
            if (toTop) {
                layers.add(l)
                activeLayerIndex = layers.lastIndex
            } else {
                layers.add(0, l)
                activeLayerIndex = 0
            }
        }
    }

    fun removeLayer(index: Int) {
        if (layers.size <= 1) return
        performSnapshotAction("Eliminar Capa") {
            if (index in layers.indices) {
                layers.removeAt(index)
                if (activeLayerIndex >= layers.size) {
                    activeLayerIndex = layers.size - 1
                }
            }
        }
    }

    fun removeActiveLayer() {
        removeLayer(activeLayerIndex)
    }

    fun toggleLayerLock(index: Int) {
        if (index in layers.indices) {
            layers[index] = layers[index].copy(isLocked = !layers[index].isLocked)
        }
    }

    fun renameLayer(index: Int, newName: String) {
        if (index in layers.indices) {
            layers[index] = layers[index].copy(name = newName)
        }
    }

    fun duplicateLayer(index: Int) {
        if (index in layers.indices) {
            performSnapshotAction("Duplicar Capa") {
                val source = layers[index]
                val copiedElements = mutableStateListOf<com.sketcher.sketchercompanionv1.LayerElement>()
                source.elements.forEach { copiedElements.add(it.copyElement()) }
                val copy = source.copy(
                    id = "l_${System.currentTimeMillis()}",
                    name = "${source.name} (Copy)",
                    elements = copiedElements
                )
                layers.add(index + 1, copy)
                activeLayerIndex = index + 1
            }
        }
    }

    fun moveLayer(fromIndex: Int, toIndex: Int) {
        if (fromIndex in layers.indices && toIndex in layers.indices) {
            performSnapshotAction("Mover Capa") {
                val item = layers.removeAt(fromIndex)
                layers.add(toIndex, item)
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
            if (index in layers.indices) {
                val l = Layer("l_${System.currentTimeMillis()}", "Capa ${layers.size + 1}", mutableStateListOf())
                layers.add(index + 1, l)
                activeLayerIndex = index + 1
            }
        }
    }

    fun addLayerBelow(index: Int) {
        performSnapshotAction("Nueva Capa Abajo") {
            if (index in layers.indices) {
                val l = Layer("l_${System.currentTimeMillis()}", "Capa ${layers.size + 1}", mutableStateListOf())
                layers.add(index, l)
                activeLayerIndex = index
            }
        }
    }

    fun mergeLayers(fromIndex: Int, toIndex: Int) {
        if (fromIndex in layers.indices && toIndex in layers.indices && fromIndex != toIndex) {
            performSnapshotAction("Fusionar Capas") {
                val fromLayer = layers[fromIndex]
                val toLayer = layers[toIndex]

                val opacityFactor = fromLayer.opacity

                // Transfer elements
                toLayer.elements.addAll(fromLayer.elements.map { 
                    val copied = it.copyElement()
                    if (opacityFactor < 1f) {
                        applyOpacityToElement(copied, opacityFactor)
                    } else {
                        copied
                    }
                })

                // Remove source
                layers.removeAt(fromIndex)

                // Update active index
                val newToIndex = if (fromIndex < toIndex) toIndex - 1 else toIndex
                activeLayerIndex = newToIndex.coerceIn(layers.indices)
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
    fun activeElements(): androidx.compose.runtime.snapshots.SnapshotStateList<com.sketcher.sketchercompanionv1.LayerElement> {
        return layers[activeLayerIndex].elements
    }

    private fun applyOpacityToElement(element: LayerElement, layerOpacity: Float): LayerElement {
        if (layerOpacity >= 1f) return element
        return when (element) {
            is VectorStroke -> element.copy(
                fillStyle = element.fillStyle.copyWithOpacity(element.fillStyle.opacity * layerOpacity),
                strokeStyle = element.strokeStyle.copyWithOpacity(element.strokeStyle.opacity * layerOpacity)
            )
            is FillData -> element.copy(
                fillStyle = element.fillStyle.copyWithOpacity(element.fillStyle.opacity * layerOpacity)
            )
            is TextElement -> {
                val a = android.graphics.Color.alpha(element.defaultTextColor)
                val r = android.graphics.Color.red(element.defaultTextColor)
                val g = android.graphics.Color.green(element.defaultTextColor)
                val b = android.graphics.Color.blue(element.defaultTextColor)
                val newA = (a * layerOpacity).toInt().coerceIn(0, 255)
                val newColor = android.graphics.Color.argb(newA, r, g, b)
                element.copy(defaultTextColor = newColor)
            }
            is GroupElement -> element.copy(
                elements = element.elements.map { applyOpacityToElement(it, layerOpacity) }.toMutableList()
            )
            else -> element
        }
    }
}

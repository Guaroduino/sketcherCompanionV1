import re
import sys

file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\SketcherViewModel.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Imports
imports_to_add = '''import android.content.Context
import com.sketcher.sketchercompanionv1.managers.LibraryManager
import com.sketcher.sketchercompanionv1.LibraryItem
import com.sketcher.sketchercompanionv1.LibraryFolder
import com.sketcher.sketchercompanionv1.LibraryComponent
'''

content = content.replace('import kotlinx.coroutines.flow.asStateFlow', 'import kotlinx.coroutines.flow.asStateFlow\n' + imports_to_add)

# 2. Modify makeComponent()
old_make_comp = '''            activeContainer.removeAll(elementsToComponent)
            val instance = ComponentInstance(
                id = "inst_" + java.util.UUID.randomUUID().toString(),
                definitionId = defId
            )
            activeContainer.add(instance)
            
            selectionManager.clearSelection()
            if (editingContext == null) {
                val newList = layers.toMutableList()
                newList[activeLayerIndex] = newList[activeLayerIndex].copy()
                layerManager.internalUpdateLayers(newList, activeLayerIndex)
            }'''

new_make_comp = '''            layers.forEach { layer ->
                layer.elements.removeAll(elementsToComponent)
            }
            if (editingContext != null) {
                activeContainer.removeAll(elementsToComponent)
            }
            
            val instance = ComponentInstance(
                id = "inst_" + java.util.UUID.randomUUID().toString(),
                definitionId = defId
            )
            activeContainer.add(instance)
            
            selectionManager.clearSelection()
            if (editingContext == null) {
                val newList = layers.toMutableList()
                // Must recreate the entire list because elements could have been removed from multiple layers
                for (i in newList.indices) {
                    newList[i] = newList[i].copy()
                }
                layerManager.internalUpdateLayers(newList, activeLayerIndex)
            }'''

content = content.replace(old_make_comp, new_make_comp)

# 3. Add Global Library Methods at the end before last closing brace
new_methods = '''
    private val _globalLibraryItems = MutableStateFlow<List<LibraryItem>>(emptyList())
    val globalLibraryItems: StateFlow<List<LibraryItem>> = _globalLibraryItems.asStateFlow()

    fun loadGlobalLibrary(context: Context) {
        viewModelScope.launch {
            _globalLibraryItems.value = LibraryManager.loadLibrary(context)
        }
    }

    fun saveGlobalLibrary(context: Context) {
        viewModelScope.launch {
            LibraryManager.saveLibrary(context, _globalLibraryItems.value)
        }
    }

    fun addToGlobalLibrary(context: Context, name: String, parentId: String?) {
        val selected = selectionManager.selectedElements.toList()
        if (selected.size == 1 && selected.first() is ComponentInstance) {
            val instance = selected.first() as ComponentInstance
            val definition = componentLibrary[instance.definitionId] ?: return
            val newId = "lib_comp_" + java.util.UUID.randomUUID().toString()
            val newItem = LibraryComponent(newId, name, parentId, definition)
            _globalLibraryItems.value = _globalLibraryItems.value + newItem
            saveGlobalLibrary(context)
        }
    }

    fun createLibraryFolder(context: Context, name: String, parentId: String?) {
        val newId = "lib_folder_" + java.util.UUID.randomUUID().toString()
        val newItem = LibraryFolder(newId, name, parentId)
        _globalLibraryItems.value = _globalLibraryItems.value + newItem
        saveGlobalLibrary(context)
    }

    fun deleteLibraryItem(context: Context, id: String) {
        fun getChildrenIds(parentId: String): List<String> {
            val children = _globalLibraryItems.value.filter { it.parentId == parentId }
            return children.map { it.id } + children.flatMap { getChildrenIds(it.id) }
        }
        val toDelete = setOf(id) + getChildrenIds(id)
        _globalLibraryItems.value = _globalLibraryItems.value.filterNot { it.id in toDelete }
        saveGlobalLibrary(context)
    }

    fun moveLibraryItem(context: Context, id: String, newParentId: String?) {
        _globalLibraryItems.value = _globalLibraryItems.value.map {
            if (it.id == id) {
                when (it) {
                    is LibraryFolder -> it.copy(parentId = newParentId)
                    is LibraryComponent -> it.copy(parentId = newParentId)
                    else -> it
                }
            } else it
        }
        saveGlobalLibrary(context)
    }

    fun renameLibraryItem(context: Context, id: String, newName: String) {
        _globalLibraryItems.value = _globalLibraryItems.value.map {
            if (it.id == id) {
                when (it) {
                    is LibraryFolder -> it.copy(name = newName)
                    is LibraryComponent -> it.copy(name = newName)
                    else -> it
                }
            } else it
        }
        saveGlobalLibrary(context)
    }

    fun instantiateFromGlobalLibrary(component: LibraryComponent) {
        performSnapshotAction("Insertar de Librería") {
            val defId = "comp_" + java.util.UUID.randomUUID().toString()
            val definition = ComponentDefinition(defId, component.definition.elements.map { it.copyElement() }.toMutableList())
            componentLibrary[defId] = definition
            
            val instance = ComponentInstance(
                id = "inst_" + java.util.UUID.randomUUID().toString(),
                definitionId = defId
            )
            activeContainer.add(instance)
            selectionManager.clearSelection()
            selectionManager.selectedElements.add(instance)
            selectionManager.recalculateBaseBounds(componentLibrary)
            if (editingContext == null) {
                notifyLayersChanged()
            }
        }
    }
}'''

content = re.sub(r'}\s*$', new_methods, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated SketcherViewModel.kt")

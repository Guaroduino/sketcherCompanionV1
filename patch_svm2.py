import re

file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\SketcherViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Replace addToGlobalLibrary
old_add = '''    fun addToGlobalLibrary(context: Context, name: String, parentId: String?) {
        val selected = selectionManager.selectedElements.toList()
        if (selected.size == 1 && selected.first() is ComponentInstance) {
            val instance = selected.first() as ComponentInstance
            val definition = componentLibrary[instance.definitionId] ?: return
            val newId = "lib_comp_" + java.util.UUID.randomUUID().toString()
            val newItem = LibraryComponent(newId, name, parentId, definition)
            _globalLibraryItems.value = _globalLibraryItems.value + newItem
            saveGlobalLibrary(context)
        }
    }'''

new_add = '''    fun addToGlobalLibrary(context: Context, name: String, parentId: String?) {
        val selected = selectionManager.selectedElements.toList()
        if (selected.size == 1 && selected.first() is ComponentInstance) {
            val instance = selected.first() as ComponentInstance
            val definition = componentLibrary[instance.definitionId] ?: return
            
            var thumbnailName: String? = null
            val bounds = instance.getBoundingBox(componentLibrary)
            if (!bounds.isEmpty) {
                try {
                    val size = 256
                    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    
                    val scaleX = size / bounds.width()
                    val scaleY = size / bounds.height()
                    val scale = java.lang.Math.min(scaleX, scaleY) * 0.8f
                    
                    val dx = (size - bounds.width() * scale) / 2f
                    val dy = (size - bounds.height() * scale) / 2f
                    
                    val m = android.graphics.Matrix()
                    m.postTranslate(-bounds.left, -bounds.top)
                    m.postScale(scale, scale)
                    m.postTranslate(dx, dy)
                    
                    com.sketcher.sketchercompanionv1.RenderEngine.drawElementRecursive(
                        canvas,
                        instance,
                        componentLibrary,
                        m,
                        1f
                    )
                    
                    val thumbFile = "thumb_" + java.util.UUID.randomUUID().toString() + ".png"
                    val assetsDir = java.io.File(context.filesDir, "library_assets")
                    if (!assetsDir.exists()) assetsDir.mkdirs()
                    val out = java.io.FileOutputStream(java.io.File(assetsDir, thumbFile))
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    out.close()
                    bitmap.recycle()
                    
                    thumbnailName = thumbFile
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            val newId = "lib_comp_" + java.util.UUID.randomUUID().toString()
            val newItem = LibraryComponent(newId, name, parentId, definition, thumbnailName)
            _globalLibraryItems.value = _globalLibraryItems.value + newItem
            saveGlobalLibrary(context)
        }
    }'''

content = content.replace(old_add, new_add)

# Replace instantiateFromGlobalLibrary
old_inst = '''    fun instantiateFromGlobalLibrary(component: LibraryComponent) {
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
    }'''

new_inst = '''    fun instantiateFromGlobalLibrary(component: LibraryComponent) {
        performSnapshotAction("Insertar de Librería") {
            val defId = "comp_" + java.util.UUID.randomUUID().toString()
            val definition = ComponentDefinition(defId, component.definition.elements.map { it.copyElement() }.toMutableList())
            componentLibrary[defId] = definition
            
            val viewportCenter = floatArrayOf(lastViewportWidth / 2f, lastViewportHeight / 2f)
            val inverseCamera = android.graphics.Matrix()
            if (_cameraMatrix.value.invert(inverseCamera)) {
                inverseCamera.mapPoints(viewportCenter)
            }
            
            val dummyInstance = ComponentInstance("dummy", defId)
            val bounds = dummyInstance.getBoundingBox(componentLibrary)
            
            val dx = viewportCenter[0] - bounds.centerX()
            val dy = viewportCenter[1] - bounds.centerY()
            
            val instance = ComponentInstance(
                id = "inst_" + java.util.UUID.randomUUID().toString(),
                definitionId = defId
            )
            
            val newTransform = android.graphics.Matrix()
            newTransform.setValues(instance.transform)
            newTransform.postTranslate(dx, dy)
            val newValues = FloatArray(9)
            newTransform.getValues(newValues)
            instance.transform = newValues
            
            activeContainer.add(instance)
            selectionManager.clearSelection()
            selectionManager.selectedElements.add(instance)
            selectionManager.recalculateBaseBounds(componentLibrary)
            if (editingContext == null) {
                notifyLayersChanged()
            }
        }
    }'''

content = content.replace(old_inst, new_inst)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated SketcherViewModel.kt")

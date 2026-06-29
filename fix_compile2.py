import re

# 1. Fix SketcherViewModel.kt
file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\SketcherViewModel.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix RenderEngine instantiation
old_render = '''                    com.sketcher.sketchercompanionv1.RenderEngine.drawElementRecursive(
                        canvas,
                        instance,
                        componentLibrary,
                        m,
                        1f
                    )'''
new_render = '''                    com.sketcher.sketchercompanionv1.RenderEngine().drawElementRecursive(
                        canvas,
                        instance,
                        componentLibrary,
                        m,
                        1f
                    )'''
content = content.replace(old_render, new_render)

# Fix ComponentInstance transform
old_transform = '''            val newTransform = android.graphics.Matrix()
            newTransform.setValues(instance.transform)
            newTransform.postTranslate(dx, dy)
            val newValues = FloatArray(9)
            newTransform.getValues(newValues)
            instance.transform = newValues'''

new_transform = '''            instance.matrix.postTranslate(dx, dy)'''

content = content.replace(old_transform, new_transform)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

# 2. Fix LibraryPanel.kt
panel_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\ui\panels\LibraryPanel.kt'
with open(panel_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace('theme.activeColor', 'theme.highlightColor')
content = content.replace('theme.toolbarBg', 'Color.Transparent')

with open(panel_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed compile errors")

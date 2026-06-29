import re
import sys

# 1. Fix SketcherViewModel.kt
file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\SketcherViewModel.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_lines = []
context_imports = 0
for line in lines:
    if line.strip() == 'import android.content.Context':
        context_imports += 1
        if context_imports > 1:
            continue # Skip duplicate
    new_lines.append(line)

with open(file_path, 'w', encoding='utf-8') as f:
    f.writelines(new_lines)


# 2. Fix LibraryManager.kt
lib_mgr_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\managers\LibraryManager.kt'
with open(lib_mgr_path, 'r', encoding='utf-8') as f:
    content = f.read()

old_code = "val definition = defJson.toComponentDefinition(bitmapMap, svgMap, mutableMapOf()) // We need to create this mapping or pass empty componentLibrary"
new_code = """val definition = defJson.toComponentDefinition(
                            bitmapLoader = { fileName -> bitmapMap[fileName] },
                            svgLoader = { fileName -> svgMap[fileName] }
                        )"""

content = content.replace(old_code, new_code)
with open(lib_mgr_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Fixed compile errors")

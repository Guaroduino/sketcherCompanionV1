import re
import sys

file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\ui\layout\StudioLayout.kt'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Add import if not present
if 'import com.sketcher.sketchercompanionv1.ui.panels.LibraryPanel' not in content:
    content = content.replace('import com.sketcher.sketchercompanionv1.ui.panels.OutlinerPanel', 'import com.sketcher.sketchercompanionv1.ui.panels.OutlinerPanel\nimport com.sketcher.sketchercompanionv1.ui.panels.LibraryPanel')

# Replace placeholder with LibraryPanel
old_placeholder = '''                        Text("LIBRARY", color = theme.iconColor, modifier = Modifier.align(Alignment.Center))'''
new_placeholder = '''                        LibraryPanel(viewModel)'''

content = content.replace(old_placeholder, new_placeholder)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated StudioLayout.kt")

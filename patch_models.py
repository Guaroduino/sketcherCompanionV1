import re

# 1. Update LibraryModels.kt
file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\LibraryModels.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'val definition: ComponentDefinition',
    'val definition: ComponentDefinition,\n    val thumbnailFileName: String? = null'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

# 2. Update ProjectDTOs.kt
file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\dto\ProjectDTOs.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'val componentDefinition: ComponentDefinitionJson? = null',
    'val componentDefinition: ComponentDefinitionJson? = null,\n    val thumbnailFileName: String? = null'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print("Updated Data Models")

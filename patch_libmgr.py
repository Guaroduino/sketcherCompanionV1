import re

file_path = r'C:\Users\Guaroduino\Documents\GitHub\sketcherCompanionV1\app\src\main\java\com\sketcher\sketchercompanionv1\managers\LibraryManager.kt'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

content = content.replace(
    'componentDefinition = item.definition.toComponentDefinitionJson()',
    'componentDefinition = item.definition.toComponentDefinitionJson(),\n                            thumbnailFileName = item.thumbnailFileName'
)

content = content.replace(
    'val definition = defJson.toComponentDefinition(',
    'val definition = defJson.toComponentDefinition('
)
content = content.replace(
    'LibraryComponent(itemJson.id, itemJson.name, itemJson.parentId, definition)',
    'LibraryComponent(itemJson.id, itemJson.name, itemJson.parentId, definition, itemJson.thumbnailFileName)'
)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)
print("Updated LibraryManager.kt")

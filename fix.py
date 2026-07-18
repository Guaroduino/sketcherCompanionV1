import re

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern to remove else if (tool.registryId == 'settings') { PersonalizationMenu(...) }
pattern = r'\} else if \(\w+\.registryId == "settings"\) \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([\s\S]*?onShowIconEditor = \{[\s\S]*?\}\s*\)\s*\}'
content = re.sub(pattern, '}', content)

# Remove 'showPersonalizationDialog = showPersonalizationDialog,'
content = re.sub(r'showPersonalizationDialog\s*=\s*showPersonalizationDialog,?', '', content)

# Remove 'showPersonalizationDialog: Boolean,'
content = re.sub(r'showPersonalizationDialog:\s*Boolean,?', '', content)

# Replace remaining 'viewModel.setShowPersonalizationDialog(true)' with 'viewModel.showWorkspaceWorkshopDialog = true'
content = content.replace('viewModel.setShowPersonalizationDialog(true)', 'viewModel.showWorkspaceWorkshopDialog = true')
content = content.replace('viewModel.setShowPersonalizationDialog(false)', 'viewModel.showWorkspaceWorkshopDialog = false')

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'w', encoding='utf-8') as f:
    f.write(content)

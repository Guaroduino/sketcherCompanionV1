import re

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace PersonalizationMenu calls with an empty block in settings menu
content = re.sub(r'else if \(tool\.registryId == "settings"\) \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([^)]*\)\s*\}', '', content)

# Remove personalizationMenuContent parameter from FloatingToolLayout and others
content = re.sub(r'personalizationMenuContent = \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([^)]*\)\s*\},?', '', content)
content = re.sub(r'personalizationMenuContent:\s*\(\)\s*->\s*Unit,?', '', content)
content = re.sub(r'personalizationMenuContent\(\)', '', content)
content = re.sub(r'showPersonalizationDialog = showPersonalizationDialog,?', '', content)
content = re.sub(r'showPersonalizationDialog:\s*Boolean,?', '', content)

# Remove the hoisted showPersonalizationDialog
content = re.sub(r'\s*val showPersonalizationDialog by viewModel\.showPersonalizationDialog\.collectAsState\(\)', '', content)

# Replace the bottom declaration
pattern_bottom = r'if \(showPersonalizationDialog\) \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([^)]*\)\s*\}'
replacement_bottom = '''if (viewModel.showWorkspaceWorkshopDialog) {
        com.sketcher.sketchercompanionv1.ui.dialogs.WorkspaceWorkshopDialog(
            viewModel = viewModel,
            theme = theme,
            onDismiss = { viewModel.showWorkspaceWorkshopDialog = false }
        )
    }'''
content = re.sub(pattern_bottom, replacement_bottom, content)

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'w', encoding='utf-8') as f:
    f.write(content)

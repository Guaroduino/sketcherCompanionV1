import re

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Replace PersonalizationMenu calls with an empty block in settings menu
pattern_if = r'else if \(tool\.registryId == \"settings\"\) \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([\s\S]*?onShowIconEditor = \{[\s\S]*?\}\s*\)\s*\}'
content = re.sub(pattern_if, '', content)

# Instead of removing the entire parameter, let's just replace the content of personalizationMenuContent to be empty.
# In StudioLayout invocation:
pattern_content1 = r'personalizationMenuContent = \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([\s\S]*?onShowIconEditor = \{[\s\S]*?\}\s*\)\s*\}'
content = re.sub(pattern_content1, 'personalizationMenuContent = {}', content)

# Remove the hoisted variable
content = re.sub(r'\s*val showPersonalizationDialog by viewModel\.showPersonalizationDialog\.collectAsState\(\)', '', content)

# Replace the bottom declaration
pattern_bottom = r'if \(showPersonalizationDialog\) \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([\s\S]*?onShowIconEditor = \{[\s\S]*?\}\s*\)\s*\}'
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

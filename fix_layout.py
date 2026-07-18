import re

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Pattern to match the else if block for 'settings' and its contents. We know the exact structure ends with two closing braces } } from the layout
pattern = r'\} else if \(\w+\.registryId == "settings"\) \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([\s\S]*?onShowIconEditor = \{[\s\S]*?\}\s*\)\s*\}'

new_content = re.sub(pattern, '}', content)

# Remove the hoisted showPersonalizationDialog variable
pattern2 = r'\s*val showPersonalizationDialog by viewModel\.showPersonalizationDialog\.collectAsState\(\)'
new_content = re.sub(pattern2, '', new_content)

# Remove the bottom block for PersonalizationMenu
pattern3 = r'if \(showPersonalizationDialog\) \{\s*com\.sketcher\.sketchercompanionv1\.ui\.dialogs\.PersonalizationMenu\([\s\S]*?onShowIconEditor = \{[\s\S]*?\}\s*\)\s*\}'

workspace_workshop_block = '''if (viewModel.showWorkspaceWorkshopDialog) {
        com.sketcher.sketchercompanionv1.ui.dialogs.WorkspaceWorkshopDialog(
            viewModel = viewModel,
            theme = theme,
            onDismiss = { viewModel.showWorkspaceWorkshopDialog = false }
        )
    }'''

new_content = re.sub(pattern3, workspace_workshop_block, new_content)

with open('app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt', 'w', encoding='utf-8') as f:
    f.write(new_content)

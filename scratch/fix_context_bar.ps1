$path = "app/src/main/java/com/sketcher/sketchercompanionv1/ui/layout/StudioLayout.kt"
$content = [System.IO.File]::ReadAllText($path, [System.Text.Encoding]::UTF8)

# 1. Remove incorrect insertion
$incorrectBlock = '(?s)\s*// Undo Last Point Button\s*com\.sketcher\.sketchercompanionv1\.ui\.components\.SketcherIconButton\(\s*onClick = \{ canvasViewRef\.value\?.undoLastGeometricPoint\(\) \},\s*icon = Icons\.Default\.Undo,\s*contentDescription = "Undo Last Point",\s*isActive = false,\s*highlightColor = theme\.highlightColor,\s*buttonColor = Color\.Transparent,\s*iconColor = theme\.iconColor,\s*shape = CircleShape,\s*iconSize = scaler\.baseIconSize\s*\)\s*'

if ($content -match $incorrectBlock) {
    $content = [regex]::Replace($content, $incorrectBlock, "")
    Write-Host "Removed incorrect Undo button insertion."
}

# 2. Insert Undo button correctly in the context bar
$cancelButtonPattern = '(?s)com\.sketcher\.sketchercompanionv1\.ui\.components\.SketcherIconButton\(\s*onClick = \{ canvasViewRef\.value\?.cancelGeometricStroke\(\) \},.*?iconSize = scaler\.baseIconSize\s*\)'

$newUndoButton = 'com.sketcher.sketchercompanionv1.ui.components.SketcherIconButton(
                    onClick = { canvasViewRef.value?.cancelGeometricStroke() },
                    icon = Icons.Default.Close,
                    contentDescription = "Cancel Shape",
                    isActive = false,
                    highlightColor = theme.highlightColor,
                    buttonColor = Color.Transparent, 
                    iconColor = theme.iconColor,
                    shape = CircleShape,
                    iconSize = scaler.baseIconSize
                )
                
                // Undo Last Point Button
                com.sketcher.sketchercompanionv1.ui.components.SketcherIconButton(
                    onClick = { canvasViewRef.value?.undoLastGeometricPoint() },
                    icon = Icons.Default.Undo,
                    contentDescription = "Undo Last Point",
                    isActive = false,
                    highlightColor = theme.highlightColor,
                    buttonColor = Color.Transparent, 
                    iconColor = theme.iconColor,
                    shape = CircleShape,
                    iconSize = scaler.baseIconSize
                )'

if ($content -match $cancelButtonPattern) {
    $content = [regex]::Replace($content, $cancelButtonPattern, $newUndoButton)
    Write-Host "Successfully added Undo button to Context Bar."
} else {
    Write-Host "Cancel button pattern not found."
}

[System.IO.File]::WriteAllText($path, $content, [System.Text.Encoding]::UTF8)

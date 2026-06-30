package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.dto.BrushPreset
import com.sketcher.sketchercompanionv1.dto.ToolType
import com.sketcher.sketchercompanionv1.ui.FreehandSettingsContent
import com.sketcher.sketchercompanionv1.ui.EraserSettingsContent

@Composable
fun SizeOpacityPopup(
    viewModel: SketcherViewModel,
    onDismiss: () -> Unit,
    theme: UiThemeConfig
) {
    val brushSize by viewModel.brushSize.collectAsState()
    val brushOpacity by viewModel.brushOpacity.collectAsState()
    val presets by viewModel.brushPresets.collectAsState()
    val selectedIndex by viewModel.selectedPresetIndex.collectAsState()
    var isToolSettingsExpanded by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = theme.highlightColor,
                surface = theme.barBackgroundColor.copy(alpha = 1f),
                onSurface = theme.iconColor
            )
        ) {
            Surface(
                shape = theme.panelShape(),
                color = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .width(340.dp)
                    .heightIn(max = 600.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dialogTitle = when (viewModel.currentTool) {
                            ToolType.FREEHAND -> "Pencil Settings"
                            ToolType.PAINT -> "Paint Settings"
                            ToolType.PEN -> "Pen Settings"
                            ToolType.PLUMA -> "Pluma Settings"
                            else -> "Brush Settings"
                        }
                        Text(
                            text = dialogTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = theme.iconColor.copy(alpha = 0.6f)
                            )
                        }
                    }

                    // Presets Section
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Presets (Long press to save current)",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.iconColor.copy(alpha = 0.6f)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            presets.forEachIndexed { index, preset ->
                                val isSelected = selectedIndex == index
                                val isModified = if (isSelected) viewModel.isPresetModified(index) else false
                                PresetButton(
                                    size = preset.size,
                                    isSelected = isSelected,
                                    isModified = isModified,
                                    theme = theme,
                                    onClick = { viewModel.selectBrushPreset(index) },
                                    onLongClick = { viewModel.saveBrushPreset(index) }
                                )
                            }
                        }
                        
                        // Overwrite/Sync Button
                        if (selectedIndex != null) {
                            val activeIndex = selectedIndex!!
                            val isModified = viewModel.isPresetModified(activeIndex)
                            Button(
                                onClick = { viewModel.saveBrushPreset(activeIndex) },
                                enabled = isModified,
                                modifier = Modifier.fillMaxWidth(),
                                shape = theme.panelShape(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = theme.highlightColor,
                                    contentColor = theme.barBackgroundColor,
                                    disabledContainerColor = theme.menuButtonColor.copy(alpha = 0.3f),
                                    disabledContentColor = theme.iconColor.copy(alpha = 0.4f)
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = "Save",
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (isModified) "Sobrescribir Preset ${activeIndex + 1}" else "Preset ${activeIndex + 1} Guardado",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // Size Slider
                    val unit = viewModel.currentUnit
                    val formattedSize = String.format("%.1f", brushSize)
                    
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Size", style = MaterialTheme.typography.bodyMedium)
                            Text("$formattedSize ${unit.symbol}", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = brushSize,
                            onValueChange = { viewModel.updateBrushSize(it) },
                            valueRange = if (unit == com.sketcher.sketchercompanionv1.dto.DistanceUnit.MM) 0.1f..50f else 1f..100f
                        )
                    }

                    // Border Stroke Thickness (ONLY for PAINT, above Stroke Opacity)
                    if (viewModel.currentTool == ToolType.PAINT) {
                        val freehandSettings = viewModel.currentFreehandSettings
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Border Stroke Thickness", style = MaterialTheme.typography.bodyMedium)
                                Text(String.format("%.1f px", freehandSettings.paintOutlineWidth), style = MaterialTheme.typography.bodySmall)
                            }
                            Slider(
                                value = freehandSettings.paintOutlineWidth,
                                onValueChange = { viewModel.updateFreehandSettings(freehandSettings.copy(paintOutlineWidth = it)) },
                                valueRange = 0.5f..15f
                            )
                        }
                    }

                    // Stroke Opacity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Stroke Opacity", style = MaterialTheme.typography.bodyMedium)
                            Text("${(brushOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = brushOpacity,
                            onValueChange = { viewModel.updateBrushOpacity(it) },
                            valueRange = 0f..1f
                        )
                    }

                    // Stabilization Slider
                    val globalStabilization by viewModel.globalStabilization.collectAsState()
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Stabilization", style = MaterialTheme.typography.bodyMedium)
                            Text("${(globalStabilization * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = globalStabilization,
                            onValueChange = { viewModel.updateGlobalStabilization(it) },
                            valueRange = 0f..0.90f
                        )
                    }

                    // Fill Opacity Slider
                    val isFillActive by viewModel.isFillActive.collectAsState()
                    if (isFillActive) {
                        val fillColor by viewModel.fillColor.collectAsState()
                        val fillOpacity = ((fillColor shr 24) and 0xFF) / 255f
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Fill Opacity", style = MaterialTheme.typography.bodyMedium)
                                Text("${(fillOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                            }
                            Slider(
                                value = fillOpacity,
                                onValueChange = { viewModel.updateFillOpacity(it) },
                                valueRange = 0f..1f
                            )
                        }
                    }

                    // Cumulative Opacity Toggle (ONLY for FREEHAND)
                    val freehandSettings = viewModel.currentFreehandSettings
                    if (viewModel.currentTool == ToolType.FREEHAND) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Cumulative Opacity", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Darkens overlaps on self-crossings",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = theme.iconColor.copy(alpha = 0.6f)
                                )
                            }
                            Switch(
                                checked = freehandSettings.isCumulativeOpacity,
                                onCheckedChange = { isChecked ->
                                    viewModel.updateFreehandSettings(freehandSettings.copy(isCumulativeOpacity = isChecked))
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = theme.highlightColor,
                                    checkedTrackColor = theme.highlightColor.copy(alpha = 0.5f)
                                )
                            )
                        }
                    }

                    // Tool Settings Collapsible Section
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { isToolSettingsExpanded = !isToolSettingsExpanded }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val settingsTitle = when (viewModel.currentTool) {
                                ToolType.FREEHAND -> "Pencil Settings"
                                ToolType.PAINT -> "Paint Settings"
                                ToolType.PEN -> "Pen Settings"
                                ToolType.PLUMA -> "Pluma Settings"
                                else -> "Tool Settings"
                            }
                            Text(
                                text = settingsTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = theme.highlightColor
                            )
                            Icon(
                                imageVector = if (isToolSettingsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (isToolSettingsExpanded) "Collapse" else "Expand",
                                tint = theme.highlightColor
                            )
                        }
                        
                        if (isToolSettingsExpanded) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                when (viewModel.currentTool) {
                                    ToolType.FREEHAND -> {
                                        FreehandSettingsContent(
                                            currentSettings = viewModel.currentFreehandSettings,
                                            onSettingsChanged = { viewModel.updateFreehandSettings(it) },
                                            isFlattenedOuterStrokeEnabled = viewModel.toolManager.isFlattenedOuterStrokeEnabled,
                                            onToggleFlattenedOuterStroke = { viewModel.toolManager.toggleFlattenedOuterStroke() },
                                            showFlatStrokeOption = false,
                                            showCapsOption = true,
                                            showPolygonOption = true,
                                            title = "Ajustes de Lápiz"
                                        )
                                    }
                                    ToolType.PAINT -> {
                                        FreehandSettingsContent(
                                            currentSettings = viewModel.currentFreehandSettings,
                                            onSettingsChanged = { viewModel.updateFreehandSettings(it) },
                                            isFlattenedOuterStrokeEnabled = viewModel.toolManager.isFlattenedOuterStrokeEnabled,
                                            onToggleFlattenedOuterStroke = { viewModel.toolManager.toggleFlattenedOuterStroke() },
                                            showFlatStrokeOption = false,
                                            showCapsOption = false,
                                            showPolygonOption = false,
                                            title = "Ajustes de Pintura"
                                        )
                                    }
                                    ToolType.PLUMA -> {
                                        FreehandSettingsContent(
                                            currentSettings = viewModel.currentFreehandSettings,
                                            onSettingsChanged = { viewModel.updateFreehandSettings(it) },
                                            isFlattenedOuterStrokeEnabled = false,
                                            onToggleFlattenedOuterStroke = {},
                                            showFlatStrokeOption = false,
                                            showCapsOption = true,
                                            showPolygonOption = true,
                                            title = "Pluma Settings"
                                        )
                                    }
                                    ToolType.ERASER -> {
                                        EraserSettingsContent(
                                            selectionScope = viewModel.selectionScope,
                                            onToggleSelectionScope = {
                                                viewModel.selectionScope = if (viewModel.selectionScope == SketcherViewModel.SelectionScope.CURRENT_LAYER)
                                                    SketcherViewModel.SelectionScope.ALL_LAYERS else SketcherViewModel.SelectionScope.CURRENT_LAYER
                                            }
                                        )
                                    }
                                    else -> {
                                        Text(
                                            text = "No additional properties for this tool.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = theme.iconColor.copy(alpha = 0.6f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetButton(
    size: Float,
    isSelected: Boolean,
    isModified: Boolean,
    theme: UiThemeConfig,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val border = if (isSelected && isModified) {
        BorderStroke(2.dp, theme.highlightColor)
    } else {
        null
    }
    val background = if (isSelected && !isModified) {
        theme.highlightColor
    } else {
        theme.buttonColor
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            val radiusDp = 2f + ((size.coerceIn(1f, 100f) - 1f) / 99f) * 14f
            drawCircle(
                color = if (isSelected && !isModified) theme.buttonColor else theme.iconColor,
                radius = radiusDp.dp.toPx()
            )
        }
    }
}

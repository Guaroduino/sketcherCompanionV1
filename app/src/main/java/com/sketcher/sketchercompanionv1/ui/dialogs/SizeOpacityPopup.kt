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
import androidx.compose.material.icons.filled.AddBox
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.dto.BrushPreset
import com.sketcher.sketchercompanionv1.dto.ToolType
import com.sketcher.sketchercompanionv1.ui.FreehandSettingsContent
import com.sketcher.sketchercompanionv1.ui.EraserSettingsContent
import com.sketcher.sketchercompanionv1.ui.SettingSlider
import com.sketcher.sketchercompanionv1.ui.AppIconButton
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.draw.scale
import com.sketcher.sketchercompanionv1.ui.components.ColorPickerDialog
import com.sketcher.sketchercompanionv1.ui.FillStylePickerDialog
import com.sketcher.sketchercompanionv1.dto.FillStyle
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.shape.RoundedCornerShape

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

    var showStrokePickerLocal by remember { mutableStateOf(false) }
    var showFillPickerLocal by remember { mutableStateOf(false) }
    var showSaveCustomToolDialog by remember { mutableStateOf(false) }
    var showSaveConfirmation by remember { mutableStateOf(false) }
    val isStrokeActive by viewModel.isStrokeActive.collectAsState()
    val isFillActive by viewModel.isFillActive.collectAsState()
    val strokeColorVal by viewModel.strokeColor.collectAsState()
    val strokeStyleVal by viewModel.strokeStyle.collectAsState()
    val fillStyleVal by viewModel.fillStyle.collectAsState()

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
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val dialogTitle = if (viewModel.activeCustomToolId != null) {
                            val ct = viewModel.toolManager.customTools.value.find { it.id == viewModel.activeCustomToolId }
                            if (ct != null) "${ct.name} Settings" else "Brush Settings"
                        } else {
                            when (viewModel.currentTool) {
                                ToolType.FREEHAND -> "Pencil Settings"
                                ToolType.PAINT -> "Paint Settings"
                                ToolType.PEN -> "Pen Settings"
                                ToolType.PLUMA -> "Pluma Settings"
                                ToolType.ERASER -> "Ajustes de Borrador de Trazo"
                                ToolType.POINT_ERASER -> "Ajustes de Borrador de Puntos"
                                ToolType.CUT_ERASER -> "Ajustes de Borrador de Corte"
                                else -> "Brush Settings"
                            }
                        }
                        Text(
                            text = dialogTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AppIconButton(
                            onClick = onDismiss,
                            icon = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.iconColor.copy(alpha = 0.6f),
                            buttonSize = 24.dp
                        )
                    }

                    // Scrollable Content
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                    val isEraser = viewModel.currentTool == ToolType.ERASER || viewModel.currentTool == ToolType.POINT_ERASER || viewModel.currentTool == ToolType.CUT_ERASER
                    if (!isEraser) {
                        // Presets Section
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (viewModel.activeCustomToolId == null) {
                                Text(
                                    text = "Presets",
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
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            PresetButton(
                                                preset = preset,
                                                index = index,
                                                isSelected = isSelected,
                                                isModified = isModified,
                                                theme = theme,
                                                onClick = { viewModel.selectBrushPreset(index) },
                                                onLongClick = { viewModel.saveBrushPreset(index) }
                                            )
                                            Text(
                                                text = "${preset.size.toInt()}",
                                                fontSize = 10.sp,
                                                color = if (isSelected) theme.highlightColor else theme.iconColor.copy(alpha = 0.6f),
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                            )
                                        }
                                    }
                                }
                            }
                        // Preview & Save button Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.buttonColor.copy(alpha = 0.15f))
                                    .border(1.dp, theme.iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                StrokePreviewCanvas(
                                    brushSize = brushSize,
                                    opacity = brushOpacity,
                                    strokeColor = Color(strokeColorVal),
                                    strokeStyle = strokeStyleVal,
                                    isStrokeActive = isStrokeActive,
                                    fillStyle = fillStyleVal,
                                    isFillActive = isFillActive,
                                    toolType = viewModel.currentTool,
                                    paintOutlineWidth = when (val s = viewModel.currentFreehandSettings) {
                                        is com.sketcher.sketchercompanionv1.tools.PaintSettings -> s.paintOutlineWidth
                                        is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> s.paintOutlineWidth
                                        else -> 2f
                                    }
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.buttonColor.copy(alpha = 0.15f))
                                    .border(1.dp, theme.iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                FillPreviewCanvas(
                                    strokeSize = brushSize,
                                    strokeOpacity = brushOpacity,
                                    strokeColor = Color(strokeColorVal),
                                    strokeStyle = strokeStyleVal,
                                    isStrokeActive = isStrokeActive,
                                    fillStyle = fillStyleVal,
                                    isFillActive = isFillActive
                                )
                            }
                        }

                        val activeCustomToolIdVal = viewModel.activeCustomToolId
                        if (activeCustomToolIdVal != null) {
                            val isModified = viewModel.isCustomToolModified(activeCustomToolIdVal)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.saveActiveCustomToolChanges() },
                                    enabled = isModified,
                                    shape = theme.panelShape(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.highlightColor,
                                        contentColor = theme.barBackgroundColor,
                                        disabledContainerColor = theme.buttonColor.copy(alpha = 0.1f),
                                        disabledContentColor = theme.iconColor.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Guardar Cambios",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Guardar",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.revertCustomToolChanges() },
                                    enabled = isModified,
                                    shape = theme.panelShape(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = theme.iconColor,
                                        disabledContentColor = theme.iconColor.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isModified) theme.iconColor.copy(alpha = 0.4f) else theme.iconColor.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "Restablecer",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        if (selectedIndex != null) {
                            val activeIndex = selectedIndex!!
                            val isModified = viewModel.isPresetModified(activeIndex)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.saveBrushPreset(activeIndex) },
                                    enabled = isModified,
                                    shape = theme.panelShape(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.highlightColor,
                                        contentColor = theme.barBackgroundColor,
                                        disabledContainerColor = theme.buttonColor.copy(alpha = 0.1f),
                                        disabledContentColor = theme.iconColor.copy(alpha = 0.3f)
                                    ),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Save,
                                        contentDescription = "Save Preset",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Guardar P${activeIndex + 1}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                OutlinedButton(
                                    onClick = { viewModel.revertBrushPreset(activeIndex) },
                                    enabled = isModified,
                                    shape = theme.panelShape(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = theme.iconColor,
                                        disabledContentColor = theme.iconColor.copy(alpha = 0.3f)
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (isModified) theme.iconColor.copy(alpha = 0.4f) else theme.iconColor.copy(alpha = 0.1f)
                                    ),
                                    modifier = Modifier.weight(1f).height(34.dp),
                                    contentPadding = PaddingValues(vertical = 0.dp)
                                ) {
                                    Text(
                                        text = "Restablecer",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }

                                Button(
                                    onClick = { showSaveCustomToolDialog = true },
                                    shape = theme.panelShape(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = theme.highlightColor,
                                        contentColor = theme.barBackgroundColor
                                    ),
                                    modifier = Modifier.weight(1.5f).height(34.dp), // slightly wider to fit name
                                    contentPadding = PaddingValues(vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddBox,
                                        contentDescription = "Como Herramienta",
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Como Herramienta",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                    } // end isEraser check for Presets

                    if (!isEraser) {
                        // Stroke & Fill Settings Section inside Popup
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Divider(color = theme.iconColor.copy(alpha = 0.1f))
                        Text(
                            text = "Stroke & Fill",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.iconColor.copy(alpha = 0.6f)
                        )

                        // Stroke setting row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = isStrokeActive,
                                    onCheckedChange = { viewModel.toggleStroke(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = theme.highlightColor,
                                        uncheckedThumbColor = theme.iconColor.copy(alpha = 0.5f),
                                        uncheckedTrackColor = theme.iconColor.copy(alpha = 0.1f)
                                    )
                                )
                                Text("Trazo (Stroke)", fontSize = 14.sp)
                            }

                            if (isStrokeActive) {
                                com.sketcher.sketchercompanionv1.ui.components.FillStylePreviewBox(
                                    style = strokeStyleVal,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .border(1.dp, theme.iconColor.copy(alpha = 0.3f), CircleShape)
                                        .clickable { showStrokePickerLocal = true },
                                    drawBorder = false
                                )
                            }
                        }

                        // Fill setting row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Switch(
                                    checked = isFillActive,
                                    onCheckedChange = { viewModel.toggleFill(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = theme.highlightColor,
                                        uncheckedThumbColor = theme.iconColor.copy(alpha = 0.5f),
                                        uncheckedTrackColor = theme.iconColor.copy(alpha = 0.1f)
                                    )
                                )
                                Text("Relleno (Fill)", fontSize = 14.sp)
                            }

                            val currentStyle = fillStyleVal
                            if (isFillActive) {
                                com.sketcher.sketchercompanionv1.ui.components.FillStylePreviewBox(
                                    style = currentStyle,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .border(1.dp, theme.iconColor.copy(alpha = 0.3f), CircleShape)
                                        .clickable { showFillPickerLocal = true },
                                    drawBorder = false
                                )
                            }
                        }
                    }

                    // Local Color dialog launchers
                    if (showStrokePickerLocal) {
                        val fillPresets by viewModel.fillPresets.collectAsState()
                        FillStylePickerDialog(
                            initialStyle = strokeStyleVal,
                            theme = theme,
                            presets = fillPresets,
                            onPresetOverwritten = { index, style ->
                                viewModel.saveFillPreset(index, style)
                            },
                            onDismiss = { showStrokePickerLocal = false },
                            onStyleSelected = { style ->
                                viewModel.setStrokeStyle(style)
                                if (style is FillStyle.Solid) {
                                    viewModel.updateLastActiveToolColor(style.color)
                                }
                                showStrokePickerLocal = false
                            },
                            onDisable = { viewModel.toggleStroke(false); showStrokePickerLocal = false },
                            onRevertToPreset = {
                                viewModel.revertToPresetColor(isStroke = true)
                                showStrokePickerLocal = false
                            }
                        )
                    }

                    if (showFillPickerLocal) {
                        val fillPresets by viewModel.fillPresets.collectAsState()
                        FillStylePickerDialog(
                            initialStyle = fillStyleVal,
                            theme = theme,
                            presets = fillPresets,
                            onPresetOverwritten = { index, style ->
                                viewModel.saveFillPreset(index, style)
                            },
                            onDismiss = { showFillPickerLocal = false },
                            onStyleSelected = { style ->
                                viewModel.setFillStyle(style)
                                if (style is FillStyle.Solid) {
                                    viewModel.updateLastActiveToolColor(style.color)
                                }
                                showFillPickerLocal = false
                            },
                            onDisable = { viewModel.toggleFill(false); showFillPickerLocal = false },
                            onRevertToPreset = {
                                viewModel.revertToPresetColor(isStroke = false)
                                showFillPickerLocal = false
                            }
                        )
                    }
                    } // end isEraser check for Stroke & Fill

                    // Size Slider
                    val unit = viewModel.currentUnit
                    val formattedSize = String.format("%.1f", brushSize)
                    SettingSlider(
                        label = "Size",
                        value = brushSize,
                        onValueChange = { viewModel.updateBrushSize(it) },
                        valueRange = if (unit == com.sketcher.sketchercompanionv1.dto.DistanceUnit.MM) 0.1f..50f else 1f..100f,
                        showValueOnRight = true,
                        valueFormatter = { "$formattedSize ${unit.symbol}" },
                        exponent = 2f
                    )

                    if (isEraser) {
                        Text(
                            text = "Forma del Borrador",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            val activeShape = viewModel.currentEraserShape
                            com.sketcher.sketchercompanionv1.dto.EraserShape.values().forEach { shape ->
                                val selected = activeShape == shape
                                val btnColor = if (selected) MaterialTheme.colorScheme.primary else Color.DarkGray
                                val textColor = if (selected) Color.White else Color.LightGray
                                Button(
                                    onClick = { viewModel.setEraserShape(shape) },
                                    colors = ButtonDefaults.buttonColors(containerColor = btnColor),
                                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                                ) {
                                    Text(
                                        text = when(shape) {
                                            com.sketcher.sketchercompanionv1.dto.EraserShape.CIRCLE -> "Círculo"
                                            com.sketcher.sketchercompanionv1.dto.EraserShape.SQUARE -> "Cuadrado"
                                        },
                                        color = textColor,
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Selection Scope for Eraser
                        Text(
                            text = "Afectación",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        EraserSettingsContent(
                            selectionScope = viewModel.selectionScope,
                            onToggleSelectionScope = {
                                viewModel.selectionScope = if (viewModel.selectionScope == SketcherViewModel.SelectionScope.CURRENT_LAYER)
                                    SketcherViewModel.SelectionScope.ALL_LAYERS else SketcherViewModel.SelectionScope.CURRENT_LAYER
                            }
                        )
                    }

                    // Stroke Opacity Slider
                    if (viewModel.currentTool != com.sketcher.sketchercompanionv1.dto.ToolType.WATERCOLOR) {
                        SettingSlider(
                            label = "Stroke Opacity",
                            value = brushOpacity,
                            onValueChange = { viewModel.updateBrushOpacity(it) },
                            valueRange = 0f..1f,
                            showValueOnRight = true,
                            valueFormatter = { "${(it * 100).toInt()}%" }
                        )
                    }

                    // Stabilization Slider
                    val globalStabilization by viewModel.globalStabilization.collectAsState()
                    SettingSlider(
                        label = "Stabilization",
                        value = globalStabilization,
                        onValueChange = { viewModel.updateGlobalStabilization(it) },
                        valueRange = 0f..0.90f,
                        showValueOnRight = true,
                        valueFormatter = { "${(it * 100).toInt()}%" }
                    )

                    // Fill Opacity Slider
                    val isFillActive by viewModel.isFillActive.collectAsState()
                    if (isFillActive) {
                        val fillOpacity by viewModel.fillOpacity.collectAsState()
                        SettingSlider(
                            label = "Fill Opacity",
                            value = fillOpacity,
                            onValueChange = { viewModel.updateFillOpacity(it) },
                            valueRange = 0f..1f,
                            showValueOnRight = true,
                            valueFormatter = { "${(it * 100).toInt()}%" }
                        )
                    }
                    // Cumulative Opacity Toggle (ONLY for PencilSettings)
                    val freehandSettings = viewModel.currentFreehandSettings
                    if (freehandSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings && viewModel.currentTool == ToolType.PENCIL_CUMULATIVE) {
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
                                ToolType.WATERCOLOR -> "Watercolor Settings"
                                ToolType.PEN -> "Pen Settings"
                                ToolType.PLUMA -> "Pluma Settings"
                                ToolType.PENCIL_CUMULATIVE -> "Lápiz Acumulativo Settings"
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
                                val toolType = viewModel.currentTool
                                when {
                                    toolType == ToolType.FREEHAND || toolType == ToolType.PENCIL_CUMULATIVE || toolType == ToolType.PEN || toolType == ToolType.PLUMA || toolType == ToolType.PAINT || toolType == ToolType.WATERCOLOR -> {
                                        FreehandSettingsContent(
                                            currentSettings = viewModel.currentFreehandSettings,
                                            onSettingsChanged = { viewModel.updateFreehandSettings(it) },
                                            isFlattenedOuterStrokeEnabled = viewModel.toolManager.isFlattenedOuterStrokeEnabled,
                                            onToggleFlattenedOuterStroke = { viewModel.toolManager.toggleFlattenedOuterStroke() },
                                            showFlatStrokeOption = false,
                                            title = if (viewModel.activeCustomToolId != null) {
                                                val ct = viewModel.toolManager.customTools.value.find { it.id == viewModel.activeCustomToolId }
                                                if (ct != null) "Ajustes de ${ct.name}" else "Ajustes de Pincel"
                                            } else {
                                                when(toolType) {
                                                    ToolType.FREEHAND -> "Ajustes de Lápiz"
                                                    ToolType.PENCIL_CUMULATIVE -> "Ajustes de Lápiz Acumulativo"
                                                    ToolType.PEN -> "Ajustes de Pluma Estilográfica"
                                                    ToolType.PLUMA -> "Ajustes de Pluma Caligráfica"
                                                    ToolType.PAINT -> "Ajustes de Pintura"
                                                    ToolType.WATERCOLOR -> "Ajustes de Acuarela"
                                                    else -> "Ajustes de Pincel"
                                                }
                                            }
                                        )
                                    }
                                    toolType == ToolType.ERASER -> {
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
                    } // end scrollable column

                    // Action Buttons
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedButton(
                            onClick = { 
                                if (viewModel.activeCustomToolId != null) {
                                    viewModel.activeCustomToolId = viewModel.activeCustomToolId
                                } else {
                                    selectedIndex?.let { viewModel.revertBrushPreset(it) }
                                }
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.iconColor)
                        ) {
                            Text("Restaurar")
                        }
                        
                        Button(
                            onClick = { 
                                showSaveConfirmation = true
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor, contentColor = Color.White)
                        ) {
                            Text("Guardar")
                        }
                    }
                }
            }
        if (showSaveCustomToolDialog && selectedIndex != null) {
            val activeIndex = selectedIndex!!
            SaveCustomToolDialog(
                viewModel = viewModel,
                preset = presets[activeIndex],
                baseToolType = viewModel.currentTool,
                theme = theme,
                onDismiss = { showSaveCustomToolDialog = false },
                onConfirm = { customTool, customIconJson ->
                    viewModel.addCustomTool(customTool)
                    if (customIconJson != null) {
                        viewModel.saveGlobalIcon(customTool.id, customIconJson)
                    }
                    showSaveCustomToolDialog = false
                }
            )
        }
        if (showSaveConfirmation) {
            AlertDialog(
                onDismissRequest = { showSaveConfirmation = false },
                containerColor = theme.barBackgroundColor,
                titleContentColor = theme.iconColor,
                textContentColor = theme.iconColor,
                title = { Text("Sobrescribir Ajustes Globales") },
                text = { Text("Se van a guardar estos ajustes como el estado Global (por defecto) para esta herramienta. ¿Estás seguro?") },
                confirmButton = {
                    Button(
                        onClick = {
                            if (viewModel.activeCustomToolId != null) {
                                viewModel.updateActiveCustomTool()
                            } else {
                                selectedIndex?.let { viewModel.saveBrushPreset(it) }
                            }
                            showSaveConfirmation = false
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor, contentColor = Color.White)
                    ) {
                        Text("Sí, Guardar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveConfirmation = false }) {
                        Text("Cancelar", color = theme.iconColor.copy(alpha = 0.8f))
                    }
                }
            )
        }
        }
    }
}

@Composable
private fun PresetButton(
    preset: BrushPreset,
    index: Int,
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

    val textColor = if (isSelected && !isModified) {
        Color.White
    } else if (isSelected && isModified) {
        theme.highlightColor
    } else {
        theme.iconColor.copy(alpha = 0.8f)
    }

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(background)
            .then(if (border != null) Modifier.border(border, CircleShape) else Modifier)
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "${index + 1}",
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StrokePreviewCanvas(
    brushSize: Float,
    opacity: Float,
    strokeColor: Color,
    strokeStyle: FillStyle,
    isStrokeActive: Boolean,
    fillStyle: FillStyle,
    isFillActive: Boolean,
    toolType: ToolType,
    paintOutlineWidth: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        val w = this.size.width
        val h = this.size.height
        val width = brushSize.coerceIn(1f, 100f)
        val thickness = 2f + ((width - 1f) / 99f) * 14f
        val thicknessPx = thickness.dp.toPx()

        // Generate points along the wavy bezier curve
        val centerPointsX = FloatArray(31)
        val centerPointsY = FloatArray(31)
        val steps = 30
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val p0x = 0f
            val p0y = h / 2f
            val p1x = w / 4f
            val p1y = h / 2f - 10f.dp.toPx()
            val p2x = w * 3f / 4f
            val p2y = h / 2f + 10f.dp.toPx()
            val p3x = w
            val p3y = h / 2f
            
            val mt = 1f - t
            val x = mt*mt*mt*p0x + 3f*mt*mt*t*p1x + 3f*mt*t*t*p2x + t*t*t*p3x
            val y = mt*mt*mt*p0y + 3f*mt*mt*t*p1y + 3f*mt*t*t*p2y + t*t*t*p3y
            centerPointsX[i] = x
            centerPointsY[i] = y
        }

        // Generate left and right offset points
        val leftPointsX = FloatArray(31)
        val leftPointsY = FloatArray(31)
        val rightPointsX = FloatArray(31)
        val rightPointsY = FloatArray(31)
        for (i in 0..steps) {
            val tx: Float
            val ty: Float
            if (i < steps) {
                tx = centerPointsX[i + 1] - centerPointsX[i]
                ty = centerPointsY[i + 1] - centerPointsY[i]
            } else {
                tx = centerPointsX[i] - centerPointsX[i - 1]
                ty = centerPointsY[i] - centerPointsY[i - 1]
            }
            var len = kotlin.math.sqrt(tx * tx + ty * ty)
            if (len == 0f) len = 1f
            val nx = -ty / len
            val ny = tx / len

            val halfW = thicknessPx / 2f
            leftPointsX[i] = centerPointsX[i] + nx * halfW
            leftPointsY[i] = centerPointsY[i] + ny * halfW
            rightPointsX[i] = centerPointsX[i] - nx * halfW
            rightPointsY[i] = centerPointsY[i] - ny * halfW
        }

        val thickPath = android.graphics.Path().apply {
            moveTo(leftPointsX[0], leftPointsY[0])
            for (i in 1..steps) {
                lineTo(leftPointsX[i], leftPointsY[i])
            }
            for (i in steps downTo 0) {
                lineTo(rightPointsX[i], rightPointsY[i])
            }
            close()
        }

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            if (toolType == ToolType.PAINT || toolType == ToolType.WATERCOLOR) {
                // Draw Fill (if active)
                if (isFillActive) {
                    val fillPaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.FILL
                    }
                    applyFillStyleToNativePaint(fillPaint, fillStyle, alpha = 1f)
                    nativeCanvas.drawPath(thickPath, fillPaint)
                }
                // Draw Outline Stroke (if active)
                if (isStrokeActive) {
                    val strokePaint = android.graphics.Paint().apply {
                        isAntiAlias = true
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = paintOutlineWidth.dp.toPx()
                    }
                    applyFillStyleToNativePaint(strokePaint, strokeStyle, alpha = opacity)
                    nativeCanvas.drawPath(thickPath, strokePaint)
                }
            } else {
                // Normal tools (Pencil, Pen, Eraser, etc.) - draw filled thick path with strokeStyle
                val strokePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.FILL
                }
                applyFillStyleToNativePaint(strokePaint, strokeStyle, alpha = opacity)
                nativeCanvas.drawPath(thickPath, strokePaint)
            }
        }
    }
}

private fun applyFillStyleToNativePaint(paint: android.graphics.Paint, style: FillStyle, alpha: Float) {
    paint.shader = null
    when (style) {
        is FillStyle.Solid -> {
            val origColor = style.color
            val origAlpha = android.graphics.Color.alpha(origColor)
            val newAlpha = (origAlpha * alpha).toInt().coerceIn(0, 255)
            paint.color = (origColor and 0x00FFFFFF) or (newAlpha shl 24)
        }
        is FillStyle.SvgPattern -> {
            val bitmap = com.sketcher.sketchercompanionv1.utils.SvgPatternCache.getOrCreate(style)
            if (bitmap != null) {
                val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
                val matrix = android.graphics.Matrix().apply {
                    postScale(style.scaleX, style.scaleY)
                    postRotate(style.rotation)
                    postTranslate(style.offsetX, style.offsetY)
                }
                shader.setLocalMatrix(matrix)
                paint.shader = shader
                val finalAlpha = (style.opacity * alpha * 255).toInt().coerceIn(0, 255)
                paint.color = android.graphics.Color.argb(finalAlpha, 255, 255, 255)
            } else {
                paint.color = android.graphics.Color.TRANSPARENT
            }
        }
        is FillStyle.MathTexture -> {
            val bitmap = com.sketcher.sketchercompanionv1.utils.MathTextureCache.getOrCreate(style)
            if (bitmap != null) {
                val shader = android.graphics.BitmapShader(bitmap, android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT)
                val matrix = android.graphics.Matrix().apply {
                    postRotate(style.angle)
                }
                shader.setLocalMatrix(matrix)
                paint.shader = shader
                val finalAlpha = (style.opacity * alpha * 255).toInt().coerceIn(0, 255)
                paint.color = android.graphics.Color.argb(finalAlpha, 255, 255, 255)
            } else {
                paint.color = android.graphics.Color.TRANSPARENT
            }
        }
        else -> {
            paint.color = android.graphics.Color.TRANSPARENT
        }
    }
}

@Composable
fun FillPreviewCanvas(
    strokeSize: Float,
    strokeOpacity: Float,
    strokeColor: Color,
    strokeStyle: FillStyle,
    isStrokeActive: Boolean,
    fillStyle: FillStyle,
    isFillActive: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.fillMaxSize().padding(8.dp)) {
        val w = size.width
        val h = size.height

        val path = android.graphics.Path().apply {
            moveTo(w * 0.35f, h * 0.5f)
            cubicTo(w * 0.35f, h * 0.25f, w * 0.65f, h * 0.25f, w * 0.65f, h * 0.5f)
            cubicTo(w * 0.65f, h * 0.75f, w * 0.42f, h * 0.75f, w * 0.38f, h * 0.65f)
        }

        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas

            if (isFillActive) {
                val fillPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.FILL
                }
                applyFillStyleToNativePaint(fillPaint, fillStyle, alpha = 1f)
                nativeCanvas.drawPath(path, fillPaint)
            }

            if (isStrokeActive) {
                val strokeWidthCoerced = strokeSize.coerceIn(1f, 100f)
                val thickness = 1f + ((strokeWidthCoerced - 1f) / 99f) * 11f
                val strokePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = thickness.dp.toPx()
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                }
                applyFillStyleToNativePaint(strokePaint, strokeStyle, alpha = strokeOpacity)
                nativeCanvas.drawPath(path, strokePaint)
            }
        }
    }
}

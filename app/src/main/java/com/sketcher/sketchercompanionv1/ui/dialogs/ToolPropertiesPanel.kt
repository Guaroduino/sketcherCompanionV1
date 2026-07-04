package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.dto.ToolType
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.foundation.layout.size
import com.sketcher.sketchercompanionv1.ui.EraserSettingsContent
import com.sketcher.sketchercompanionv1.ui.FreehandSettingsContent
import com.sketcher.sketchercompanionv1.ui.SettingSlider
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import androidx.compose.foundation.BorderStroke

@Composable
fun ToolPropertiesPanel(
    viewModel: SketcherViewModel,
    onDismiss: () -> Unit
) {
    val theme = viewModel.themeConfig.value
    
    // Override MaterialTheme locally to enforce user colors on Sliders/Switches
    // We map all "accent" colors to highlightColor to ensure full compliance with the user's pick.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = theme.highlightColor,
            secondary = theme.highlightColor,
            tertiary = theme.highlightColor,
            onPrimary = theme.iconColor,
            onSecondary = theme.iconColor,
            onTertiary = theme.iconColor,
            surface = theme.barBackgroundColor.copy(alpha = 1f), // Solid surface
            onSurface = theme.iconColor,
            onSurfaceVariant = theme.iconColor.copy(alpha = 0.8f),
            outline = theme.iconColor.copy(alpha = 0.5f),
            outlineVariant = theme.iconColor.copy(alpha = 0.3f)
        )
    ) {
        Surface(
            shape = theme.panelShape(),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
            tonalElevation = 8.dp,
            modifier = Modifier
                .width(340.dp)
                .heightIn(max = 600.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary // Use highlight color for title
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close, 
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Apply theme to legacy components via LocalContentColor or specific params if they support it.
                    // Assuming they use MaterialTheme locals, we wrapped them in Surface with contentColor, so they should inherit.
                    when (viewModel.currentTool) {
                        ToolType.FREEHAND -> {
                            // REUSE LEGACY COMPONENT (Mirror functionality)
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
                        ToolType.PENCIL_CUMULATIVE -> {
                            FreehandSettingsContent(
                                currentSettings = viewModel.currentFreehandSettings,
                                onSettingsChanged = { viewModel.updateFreehandSettings(it) },
                                isFlattenedOuterStrokeEnabled = false,
                                onToggleFlattenedOuterStroke = {},
                                showFlatStrokeOption = false,
                                showCapsOption = true,
                                showPolygonOption = true,
                                title = "Ajustes de Lápiz Acumulativo"
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
                                showJoinPreviousOption = true,
                                title = "Ajustes de Pintura"
                            )
                        }
                        ToolType.WATERCOLOR -> {
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                FreehandSettingsContent(
                                    currentSettings = viewModel.currentFreehandSettings,
                                    onSettingsChanged = { viewModel.updateFreehandSettings(it) },
                                    isFlattenedOuterStrokeEnabled = viewModel.toolManager.isFlattenedOuterStrokeEnabled,
                                    onToggleFlattenedOuterStroke = { viewModel.toolManager.toggleFlattenedOuterStroke() },
                                    showFlatStrokeOption = false,
                                    showCapsOption = false,
                                    showPolygonOption = false,
                                    showJoinPreviousOption = true,
                                    title = "Ajustes de Acuarela"
                                )
                                
                                val freehandSettings = viewModel.currentFreehandSettings
                                HorizontalDivider()
                                Text("Afectación de Acuarela", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                
                                // Jitter Deviation Slider
                                val jitterDev = freehandSettings.watercolorJitterDeviation
                                SettingSlider(
                                    label = "Dispersión del borde (Jitter): ${String.format("%.1f px", jitterDev)}",
                                    value = jitterDev,
                                    onValueChange = { viewModel.updateFreehandSettings(freehandSettings.copy(watercolorJitterDeviation = it)) },
                                    valueRange = 0f..15f,
                                    exponent = 2f
                                )
                                
                                // Jitter Segment Slider
                                val jitterSeg = freehandSettings.watercolorJitterSegment
                                SettingSlider(
                                    label = "Frecuencia del borde: ${String.format("%.1f px", jitterSeg)}",
                                    value = jitterSeg,
                                    onValueChange = { viewModel.updateFreehandSettings(freehandSettings.copy(watercolorJitterSegment = it)) },
                                    valueRange = 3f..50f,
                                    exponent = 2f
                                )
                                
                                // Blur Radius Slider
                                val blurRad = freehandSettings.watercolorBlurRadius
                                SettingSlider(
                                    label = "Difuminado (Blur): ${String.format("%.1f px", blurRad)}",
                                    value = blurRad,
                                    onValueChange = { viewModel.updateFreehandSettings(freehandSettings.copy(watercolorBlurRadius = it)) },
                                    valueRange = 0f..25f,
                                    exponent = 2f
                                )

                                // Center Opacity Slider
                                val centerOp = freehandSettings.watercolorCenterOpacity
                                SettingSlider(
                                    label = "Opacidad Central (Center): ${(centerOp * 100).toInt()}%",
                                    value = centerOp,
                                    onValueChange = { viewModel.updateFreehandSettings(freehandSettings.copy(watercolorCenterOpacity = it)) },
                                    valueRange = 0f..1f
                                )
                                
                                // Edge Ring Opacity Slider
                                val ringOp = freehandSettings.watercolorEdgeRingOpacity
                                SettingSlider(
                                    label = "Opacidad de Anillo (Ring): ${(ringOp * 100).toInt()}%",
                                    value = ringOp,
                                    onValueChange = { viewModel.updateFreehandSettings(freehandSettings.copy(watercolorEdgeRingOpacity = it)) },
                                    valueRange = 0f..1f
                                )
                                
                                // Edge Ring Width Slider
                                val ringWidth = freehandSettings.watercolorEdgeRingWidth
                                SettingSlider(
                                    label = "Grosor de Anillo (Ring Width): ${String.format("%.1f px", ringWidth)}",
                                    value = ringWidth,
                                    onValueChange = { viewModel.updateFreehandSettings(freehandSettings.copy(watercolorEdgeRingWidth = it)) },
                                    valueRange = 0f..20f,
                                    exponent = 2f
                                )

                                // Edge Mode Selector
                                Text("Modo del Contorno (Edge Mode)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.values().forEach { mode ->
                                        val selected = freehandSettings.watercolorEdgeMode == mode
                                        val btnColor = if (selected) MaterialTheme.colorScheme.primary else Color.DarkGray
                                        val textColor = if (selected) Color.White else Color.LightGray
                                        androidx.compose.material3.Button(
                                            onClick = {
                                                viewModel.updateFreehandSettings(freehandSettings.copy(watercolorEdgeMode = mode))
                                            },
                                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = btnColor),
                                            modifier = Modifier.weight(1f).padding(horizontal = 2.dp),
                                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text(text = mode.name, color = textColor, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
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
                        ToolType.ERASER, ToolType.POINT_ERASER, ToolType.CUT_ERASER -> {
                            // REUSE LEGACY COMPONENT (Mirror functionality)
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
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

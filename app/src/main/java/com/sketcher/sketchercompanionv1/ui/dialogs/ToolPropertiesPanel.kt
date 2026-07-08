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
                    verticalArrangement = Arrangement.spacedBy(16.dp)                ) {
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
                        toolType == ToolType.ERASER || toolType == ToolType.POINT_ERASER || toolType == ToolType.CUT_ERASER -> {
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

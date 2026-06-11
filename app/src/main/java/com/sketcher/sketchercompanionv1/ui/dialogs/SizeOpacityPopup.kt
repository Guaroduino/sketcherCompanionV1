package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

@Composable
fun SizeOpacityPopup(
    viewModel: SketcherViewModel,
    onDismiss: () -> Unit,
    theme: UiThemeConfig
) {
    val brushSize by viewModel.brushSize.collectAsState()
    val brushOpacity by viewModel.brushOpacity.collectAsState()
    val presets by viewModel.sizePresets.collectAsState()

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
                    .width(300.dp)
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Brush Settings",
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

                    // Presets Row
                    Text(
                        text = "Presets (Long press to save current)",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.iconColor.copy(alpha = 0.6f)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        presets.forEachIndexed { index, presetSize ->
                            PresetButton(
                                size = presetSize,
                                isSelected = brushSize == presetSize,
                                theme = theme,
                                onClick = { viewModel.updateBrushSize(presetSize) },
                                onLongClick = { viewModel.saveSizePreset(index, brushSize) }
                            )
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

                    // Opacity Slider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Opacity", style = MaterialTheme.typography.bodyMedium)
                            Text("${(brushOpacity * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Slider(
                            value = brushOpacity,
                            onValueChange = { viewModel.updateBrushOpacity(it) },
                            valueRange = 0f..1f
                        )
                    }

                    // Cumulative Opacity Toggle
                    val freehandSettings = viewModel.currentFreehandSettings
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
            }
        }
    }
}

@Composable
private fun PresetButton(
    size: Float,
    isSelected: Boolean,
    theme: UiThemeConfig,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(if (isSelected) theme.highlightColor else theme.buttonColor)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(32.dp)) {
            // Mapping for preset button visual dot
            val radiusDp = 2f + ((size.coerceIn(1f, 100f) - 1f) / 99f) * 14f
            drawCircle(
                color = theme.iconColor,
                radius = radiusDp.dp.toPx()
            )
        }
    }
}

package com.sketcher.sketchercompanionv1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.dto.FreehandSettings
import com.sketcher.sketchercompanionv1.dto.ToolType
import com.sketcher.sketchercompanionv1.dto.StrokeEndOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import com.sketcher.sketchercompanionv1.SketcherViewModel
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


@Composable
fun ToolSettingsPopup(
    toolType: ToolType,
    unit: com.sketcher.sketchercompanionv1.dto.DistanceUnit = com.sketcher.sketchercompanionv1.dto.DistanceUnit.MM, // Added Unit
    // Freehand Specs
    freehandSettings: com.sketcher.sketchercompanionv1.tools.ToolSettings,
    onFreehandSettingsChanged: (com.sketcher.sketchercompanionv1.tools.ToolSettings) -> Unit,
    isFlattenedOuterStrokeEnabled: Boolean = false,
    onToggleFlattenedOuterStroke: () -> Unit = {},
    // Eraser Specs
    selectionScope: SketcherViewModel.SelectionScope = SketcherViewModel.SelectionScope.CURRENT_LAYER,
    onToggleSelectionScope: () -> Unit = {},
    
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement =Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Configuración de Herramienta", 
                    style = MaterialTheme.typography.titleLarge
                )

                when {
                    toolType == ToolType.FREEHAND || toolType == ToolType.PENCIL_CUMULATIVE || toolType == ToolType.PEN || toolType == ToolType.PLUMA || toolType == ToolType.PAINT || toolType == ToolType.WATERCOLOR -> {
                        FreehandSettingsContent(
                            freehandSettings,
                            onFreehandSettingsChanged,
                            isFlattenedOuterStrokeEnabled,
                            onToggleFlattenedOuterStroke
                        )
                    }
                    toolType == ToolType.ERASER -> {
                        EraserSettingsContent(selectionScope, onToggleSelectionScope)
                    }
                    else -> {
                        Text("No hay ajustes disponibles para esta herramienta.")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
fun FreehandSettingsContent(
    currentSettings: com.sketcher.sketchercompanionv1.tools.ToolSettings,
    onSettingsChanged: (com.sketcher.sketchercompanionv1.tools.ToolSettings) -> Unit,
    isFlattenedOuterStrokeEnabled: Boolean,
    onToggleFlattenedOuterStroke: () -> Unit,
    showFlatStrokeOption: Boolean = true,
    title: String = "Ajustes de Pincel"
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("Básico", "Forma", "Efectos", "Avanzado")

    Column(modifier = Modifier.fillMaxWidth()) {
        ScrollableTabRow(
            selectedTabIndex = selectedTabIndex,
            edgePadding = 0.dp,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = { Text(title, style = MaterialTheme.typography.labelMedium) }
                )
            }
        }

        when (selectedTabIndex) {
            0 -> BasicTab(currentSettings, onSettingsChanged)
            1 -> ShapeTab(currentSettings, onSettingsChanged)
            2 -> EffectsTab(currentSettings, onSettingsChanged)
            3 -> AdvancedTab(currentSettings, onSettingsChanged, isFlattenedOuterStrokeEnabled, onToggleFlattenedOuterStroke, showFlatStrokeOption)
        }
    }
}

@Composable
fun BasicTab(
    currentSettings: com.sketcher.sketchercompanionv1.tools.ToolSettings,
    onSettingsChanged: (com.sketcher.sketchercompanionv1.tools.ToolSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val thinning = when(currentSettings) {
            is com.sketcher.sketchercompanionv1.tools.PencilSettings -> currentSettings.thinning
            is com.sketcher.sketchercompanionv1.tools.PenSettings -> currentSettings.thinning
            is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> currentSettings.thinning
            is com.sketcher.sketchercompanionv1.tools.PaintSettings -> currentSettings.thinning
            is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> currentSettings.thinning
            else -> null
        }
        if (thinning != null) {
            SettingSlider(
                label = "Adelgazamiento (Presión): ${(thinning * 100).toInt()}%",
                value = thinning,
                onValueChange = { 
                    when(currentSettings) {
                        is com.sketcher.sketchercompanionv1.tools.PencilSettings -> onSettingsChanged(currentSettings.copy(thinning = it))
                        is com.sketcher.sketchercompanionv1.tools.PenSettings -> onSettingsChanged(currentSettings.copy(thinning = it))
                        is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> onSettingsChanged(currentSettings.copy(thinning = it))
                        is com.sketcher.sketchercompanionv1.tools.PaintSettings -> onSettingsChanged(currentSettings.copy(thinning = it))
                        is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> onSettingsChanged(currentSettings.copy(thinning = it))
                    }
                }
            )
        }

        val velocityThinning = when(currentSettings) {
            is com.sketcher.sketchercompanionv1.tools.PencilSettings -> currentSettings.velocityThinning
            is com.sketcher.sketchercompanionv1.tools.PenSettings -> currentSettings.velocityThinning
            is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> currentSettings.velocityThinning
            is com.sketcher.sketchercompanionv1.tools.PaintSettings -> currentSettings.velocityThinning
            is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> currentSettings.velocityThinning
            else -> null
        }
        if (velocityThinning != null) {
            SettingSlider(
                label = "Adelgazamiento (Velocidad): ${(velocityThinning * 100).toInt()}%",
                value = velocityThinning,
                onValueChange = { 
                    when(currentSettings) {
                        is com.sketcher.sketchercompanionv1.tools.PencilSettings -> onSettingsChanged(currentSettings.copy(velocityThinning = it))
                        is com.sketcher.sketchercompanionv1.tools.PenSettings -> onSettingsChanged(currentSettings.copy(velocityThinning = it))
                        is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> onSettingsChanged(currentSettings.copy(velocityThinning = it))
                        is com.sketcher.sketchercompanionv1.tools.PaintSettings -> onSettingsChanged(currentSettings.copy(velocityThinning = it))
                        is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> onSettingsChanged(currentSettings.copy(velocityThinning = it))
                    }
                }
            )
        }

        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings) {
            SettingSlider(
                label = "Sensibilidad (Velocidad): ${String.format("%.1f", currentSettings.velocityMaxInput)} px/ms",
                value = currentSettings.velocityMaxInput,
                valueRange = 0.1f..5.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(velocityMaxInput = it)) }
            )
            
            SettingSlider(
                label = "Grosor Mínimo: ${(currentSettings.minWidthRatio * 100).toInt()}%",
                value = currentSettings.minWidthRatio,
                valueRange = 0f..1.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(minWidthRatio = it)) }
            )
        }

        val smoothing = when(currentSettings) {
            is com.sketcher.sketchercompanionv1.tools.PencilSettings -> currentSettings.smoothing
            is com.sketcher.sketchercompanionv1.tools.PenSettings -> currentSettings.smoothing
            is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> currentSettings.smoothing
            is com.sketcher.sketchercompanionv1.tools.PaintSettings -> currentSettings.smoothing
            is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> currentSettings.smoothing
            else -> null
        }
        if (smoothing != null) {
            SettingSlider(
                label = "Suavizado (Bordes): ${(smoothing * 100).toInt()}%",
                value = smoothing,
                valueRange = 0f..1.0f,
                onValueChange = { 
                    when(currentSettings) {
                        is com.sketcher.sketchercompanionv1.tools.PencilSettings -> onSettingsChanged(currentSettings.copy(smoothing = it))
                        is com.sketcher.sketchercompanionv1.tools.PenSettings -> onSettingsChanged(currentSettings.copy(smoothing = it))
                        is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> onSettingsChanged(currentSettings.copy(smoothing = it))
                        is com.sketcher.sketchercompanionv1.tools.PaintSettings -> onSettingsChanged(currentSettings.copy(smoothing = it))
                        is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> onSettingsChanged(currentSettings.copy(smoothing = it))
                    }
                }
            )
        }

        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings || currentSettings is com.sketcher.sketchercompanionv1.tools.PlumaSettings) {
            val simulatePressure = if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings) currentSettings.simulatePressure else (currentSettings as com.sketcher.sketchercompanionv1.tools.PlumaSettings).simulatePressure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Simular Presión (Velocidad)")
                Switch(
                    checked = simulatePressure,
                    onCheckedChange = { 
                        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings) {
                            onSettingsChanged(currentSettings.copy(simulatePressure = it))
                        } else if (currentSettings is com.sketcher.sketchercompanionv1.tools.PlumaSettings) {
                            onSettingsChanged(currentSettings.copy(simulatePressure = it))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ShapeTab(
    currentSettings: com.sketcher.sketchercompanionv1.tools.ToolSettings,
    onSettingsChanged: (com.sketcher.sketchercompanionv1.tools.ToolSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val start = when(currentSettings) {
            is com.sketcher.sketchercompanionv1.tools.PencilSettings -> currentSettings.start
            is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> currentSettings.start
            is com.sketcher.sketchercompanionv1.tools.PaintSettings -> currentSettings.start
            is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> currentSettings.start
            else -> null
        }
        val end = when(currentSettings) {
            is com.sketcher.sketchercompanionv1.tools.PencilSettings -> currentSettings.end
            is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> currentSettings.end
            is com.sketcher.sketchercompanionv1.tools.PaintSettings -> currentSettings.end
            is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> currentSettings.end
            else -> null
        }

        if (start != null && end != null) {
            TaperingSettings(
                start = start,
                end = end,
                onStartChanged = { 
                    when(currentSettings) {
                        is com.sketcher.sketchercompanionv1.tools.PencilSettings -> onSettingsChanged(currentSettings.copy(start = it))
                        is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> onSettingsChanged(currentSettings.copy(start = it))
                        is com.sketcher.sketchercompanionv1.tools.PaintSettings -> onSettingsChanged(currentSettings.copy(start = it))
                        is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> onSettingsChanged(currentSettings.copy(start = it))
                    }
                },
                onEndChanged = { 
                    when(currentSettings) {
                        is com.sketcher.sketchercompanionv1.tools.PencilSettings -> onSettingsChanged(currentSettings.copy(end = it))
                        is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> onSettingsChanged(currentSettings.copy(end = it))
                        is com.sketcher.sketchercompanionv1.tools.PaintSettings -> onSettingsChanged(currentSettings.copy(end = it))
                        is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> onSettingsChanged(currentSettings.copy(end = it))
                    }
                }
            )
        } else {
            Text("No hay opciones de forma para esta herramienta.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun EffectsTab(
    currentSettings: com.sketcher.sketchercompanionv1.tools.ToolSettings,
    onSettingsChanged: (com.sketcher.sketchercompanionv1.tools.ToolSettings) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PaintSettings || currentSettings is com.sketcher.sketchercompanionv1.tools.WatercolorSettings) {
            val paintOutlineWidthRatio = if (currentSettings is com.sketcher.sketchercompanionv1.tools.PaintSettings) currentSettings.paintOutlineWidthRatio else (currentSettings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings).paintOutlineWidthRatio
            val paintJoinCurrent = if (currentSettings is com.sketcher.sketchercompanionv1.tools.PaintSettings) currentSettings.paintJoinCurrent else (currentSettings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings).paintJoinCurrent
            val paintJoinPrevious = if (currentSettings is com.sketcher.sketchercompanionv1.tools.PaintSettings) currentSettings.paintJoinPrevious else (currentSettings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings).paintJoinPrevious

            SettingSlider(
                label = "Grosor del Contorno: ${(paintOutlineWidthRatio * 100).toInt()}%",
                value = paintOutlineWidthRatio,
                valueRange = 0f..1f,
                onValueChange = { 
                    if (currentSettings is com.sketcher.sketchercompanionv1.tools.PaintSettings) onSettingsChanged(currentSettings.copy(paintOutlineWidthRatio = it))
                    else onSettingsChanged((currentSettings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings).copy(paintOutlineWidthRatio = it))
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fusionar con trazo actual")
                }
                Switch(
                    checked = paintJoinCurrent,
                    onCheckedChange = { isChecked ->
                        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PaintSettings) {
                            onSettingsChanged(currentSettings.copy(paintJoinCurrent = isChecked, paintJoinPrevious = if (isChecked) currentSettings.paintJoinPrevious else false))
                        } else {
                            onSettingsChanged((currentSettings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings).copy(paintJoinCurrent = isChecked, paintJoinPrevious = if (isChecked) currentSettings.paintJoinPrevious else false))
                        }
                    }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Fusionar con trazos anteriores",
                        color = if (paintJoinCurrent) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
                Switch(
                    checked = paintJoinCurrent && paintJoinPrevious,
                    enabled = paintJoinCurrent,
                    onCheckedChange = { isChecked ->
                        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PaintSettings) {
                            onSettingsChanged(currentSettings.copy(paintJoinPrevious = isChecked))
                        } else {
                            onSettingsChanged((currentSettings as com.sketcher.sketchercompanionv1.tools.WatercolorSettings).copy(paintJoinPrevious = isChecked))
                        }
                    }
                )
            }
        }
        
        if (currentSettings is com.sketcher.sketchercompanionv1.tools.WatercolorSettings) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Ajustes de Acuarela", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // Lock toggle and Brightness offset slider
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                val isLocked = currentSettings.linkStrokeToFill
                IconButton(
                    onClick = {
                        onSettingsChanged(currentSettings.copy(linkStrokeToFill = !isLocked))
                    }
                ) {
                    Icon(
                        imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                        contentDescription = "Link Stroke to Fill Color",
                        tint = if (isLocked) MaterialTheme.colorScheme.primary else Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    SettingSlider(
                        label = if (isLocked) {
                            "Stroke offset: ${if (currentSettings.strokeBrightnessOffset >= 0) "+" else ""}${(currentSettings.strokeBrightnessOffset * 100).toInt()}% brightness"
                        } else {
                            "Stroke offset (desvinculado)"
                        },
                        value = currentSettings.strokeBrightnessOffset,
                        onValueChange = {
                            onSettingsChanged(currentSettings.copy(strokeBrightnessOffset = it))
                        },
                        valueRange = -1.0f..1.0f,
                        showValueOnRight = true,
                        valueFormatter = { String.format("%+d%%", (it * 100).toInt()) },
                        enabled = isLocked
                    )
                }
            }

            SettingSlider(
                label = "Desviación del Jitter: ${(currentSettings.watercolorJitterDeviationRatio * 100).toInt()}%",
                value = currentSettings.watercolorJitterDeviationRatio,
                valueRange = 0f..1f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorJitterDeviationRatio = it)) }
            )
            SettingSlider(
                label = "Segmento de Jitter: ${(currentSettings.watercolorJitterSegmentRatio * 100).toInt()}%",
                value = currentSettings.watercolorJitterSegmentRatio,
                valueRange = 0f..1f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorJitterSegmentRatio = it)) }
            )
            SettingSlider(
                label = "Radio de Desenfoque: ${(currentSettings.watercolorBlurRadiusRatio * 100).toInt()}%",
                value = currentSettings.watercolorBlurRadiusRatio,
                valueRange = 0f..1f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorBlurRadiusRatio = it)) }
            )
            SettingSlider(
                label = "Opacidad de Centro: ${(currentSettings.watercolorCenterOpacity * 100).toInt()}%",
                value = currentSettings.watercolorCenterOpacity,
                valueRange = 0f..1f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorCenterOpacity = it)) }
            )
            SettingSlider(
                label = "Opacidad de Anillo: ${(currentSettings.watercolorEdgeRingOpacity * 100).toInt()}%",
                value = currentSettings.watercolorEdgeRingOpacity,
                valueRange = 0f..1f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorEdgeRingOpacity = it)) }
            )
            SettingSlider(
                label = "Grosor de Anillo: ${(currentSettings.watercolorEdgeRingWidthRatio * 100).toInt()}%",
                value = currentSettings.watercolorEdgeRingWidthRatio,
                valueRange = 0f..1f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorEdgeRingWidthRatio = it)) }
            )
            Text("Modo de Borde", style = MaterialTheme.typography.bodyMedium)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                com.sketcher.sketchercompanionv1.dto.WatercolorEdgeMode.entries.forEach { mode ->
                    val selected = currentSettings.watercolorEdgeMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { onSettingsChanged(currentSettings.copy(watercolorEdgeMode = mode)) },
                        label = { Text(mode.name) }
                    )
                }
            }
        }
        
        if (currentSettings !is com.sketcher.sketchercompanionv1.tools.PaintSettings && currentSettings !is com.sketcher.sketchercompanionv1.tools.WatercolorSettings) {
            Text("No hay efectos adicionales para esta herramienta.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun AdvancedTab(
    currentSettings: com.sketcher.sketchercompanionv1.tools.ToolSettings,
    onSettingsChanged: (com.sketcher.sketchercompanionv1.tools.ToolSettings) -> Unit,
    isFlattenedOuterStrokeEnabled: Boolean,
    onToggleFlattenedOuterStroke: () -> Unit,
    showFlatStrokeOption: Boolean
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings) {
            SettingSlider(
                label = "Predicción de Latencia (Fallback): ${currentSettings.predictionLatency} ms",
                value = currentSettings.predictionLatency.toFloat(),
                valueRange = 0f..50f,
                steps = 50,
                onValueChange = { onSettingsChanged(currentSettings.copy(predictionLatency = it.toLong())) }
            )
        }

        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings || currentSettings is com.sketcher.sketchercompanionv1.tools.PlumaSettings) {
            val useCurve = if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings) currentSettings.useCurveForPolygon else (currentSettings as com.sketcher.sketchercompanionv1.tools.PlumaSettings).useCurveForPolygon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Curvas Poligonales")
                Switch(
                    checked = useCurve,
                    onCheckedChange = { 
                        if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings) onSettingsChanged(currentSettings.copy(useCurveForPolygon = it))
                        else onSettingsChanged((currentSettings as com.sketcher.sketchercompanionv1.tools.PlumaSettings).copy(useCurveForPolygon = it))
                    }
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Compresión (Salida)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            val tolerance = if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings) currentSettings.simplificationTolerance else (currentSettings as com.sketcher.sketchercompanionv1.tools.PlumaSettings).simplificationTolerance
            val toleranceLabel = if (tolerance == 0f) "Desactivado (Raw)" else String.format("%.1f", tolerance)
            
            SettingSlider(
                label = "Simplificación: $toleranceLabel",
                value = tolerance,
                valueRange = 0.0f..2.0f,
                steps = 19,
                onValueChange = { 
                    val enabled = it > 0f
                    if (currentSettings is com.sketcher.sketchercompanionv1.tools.PencilSettings) onSettingsChanged(currentSettings.copy(simplificationTolerance = it, isSimplificationEnabled = enabled))
                    else onSettingsChanged((currentSettings as com.sketcher.sketchercompanionv1.tools.PlumaSettings).copy(simplificationTolerance = it, isSimplificationEnabled = enabled))
                }
            )
            
            if (tolerance == 0f) {
                Text("Modo RAW: Se guardan todos los puntos del evento.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else if (tolerance < 0.2f) {
                Text("Máxima fidelidad (Muchos puntos)", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            } else if (tolerance > 1.0f) {
                Text("Alta compresión (Formas geométricas)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Equilibrado - Recomendado", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        if (showFlatStrokeOption) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Experimento: Modo Trazo Plano", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Trazo Optimizado (Plano)")
                }
                Switch(
                    checked = isFlattenedOuterStrokeEnabled,
                    onCheckedChange = { onToggleFlattenedOuterStroke() }
                )
            }
        }

        if (currentSettings !is com.sketcher.sketchercompanionv1.tools.PencilSettings && currentSettings !is com.sketcher.sketchercompanionv1.tools.PlumaSettings && !showFlatStrokeOption) {
            Text("No hay opciones avanzadas para esta herramienta.", color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        }
    }
}


@Composable
fun EraserSettingsContent(
    selectionScope: SketcherViewModel.SelectionScope,
    onToggleSelectionScope: () -> Unit
) {
    Text("Ajustes de Borrador", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Borrar en Todas las Capas")
            Text(
                text = "Borrar elementos de todas las capas visibles",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
        Switch(
            checked = selectionScope == SketcherViewModel.SelectionScope.ALL_LAYERS,
            onCheckedChange = { onToggleSelectionScope() }
        )
    }
}

@Composable
fun TaperingSettings(
    start: StrokeEndOptions,
    end: StrokeEndOptions,
    onStartChanged: (StrokeEndOptions) -> Unit,
    onEndChanged: (StrokeEndOptions) -> Unit
) {
    HorizontalDivider()
    
    Text("Opciones de Punta (Afilado/Taper)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    // Taper
    SettingSlider(
        label = "Afilado de Inicio: ${(start.customTaper ?: 0f).toInt()}px",
        value = start.customTaper ?: 0f,
        valueRange = 0f..150f,
        onValueChange = { 
            val taperVal = if (it == 0f) null else it
            onStartChanged(start.copy(customTaper = taperVal, taperEnabled = taperVal != null)) 
        }
    )
    
    SettingSlider(
        label = "Afilado de Fin: ${(end.customTaper ?: 0f).toInt()}px",
        value = end.customTaper ?: 0f,
        valueRange = 0f..150f,
        onValueChange = { 
            val taperVal = if (it == 0f) null else it
            onEndChanged(end.copy(customTaper = taperVal, taperEnabled = taperVal != null)) 
        }
    )

    // Caps
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Punta Redonda (Inicio)")
        Switch(
            checked = start.cap,
            onCheckedChange = { onStartChanged(start.copy(cap = it)) }
        )
    }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Punta Redonda (Fin)")
        Switch(
            checked = end.cap,
            onCheckedChange = { onEndChanged(end.copy(cap = it)) }
        )
    }
}

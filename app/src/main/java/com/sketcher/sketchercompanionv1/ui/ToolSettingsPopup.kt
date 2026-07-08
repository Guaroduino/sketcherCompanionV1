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
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    when (currentSettings) {
        is com.sketcher.sketchercompanionv1.tools.PencilSettings -> {
            // 1. Thinning
            SettingSlider(
                label = "Adelgazamiento (Presión): ${(currentSettings.thinning * 100).toInt()}%",
                value = currentSettings.thinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(thinning = it)) }
            )
            
            SettingSlider(
                label = "Adelgazamiento (Velocidad): ${(currentSettings.velocityThinning * 100).toInt()}%",
                value = currentSettings.velocityThinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(velocityThinning = it)) }
            )
            
            SettingSlider(
                label = "Sensibilidad (Velocidad): ${String.format("%.1f", currentSettings.velocityMaxInput)} px/ms",
                value = currentSettings.velocityMaxInput,
                valueRange = 0.1f..5.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(velocityMaxInput = it)) }
            )

            // 3. Smoothing
            SettingSlider(
                label = "Suavizado (Bordes)",
                value = currentSettings.smoothing,
                valueRange = 0f..1.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(smoothing = it)) }
            )
            
            // Min Width
            SettingSlider(
                label = "Grosor Mínimo: ${(currentSettings.minWidthRatio * 100).toInt()}%",
                value = currentSettings.minWidthRatio,
                valueRange = 0f..1.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(minWidthRatio = it)) }
            )

            // 4. Simulate Pressure
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Simular Presión (Velocidad)")
                Switch(
                    checked = currentSettings.simulatePressure,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(simulatePressure = it)) }
                )
            }

            HorizontalDivider()
            
            Text("Opciones de Punta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

            // Taper
            SettingSlider(
                label = "Afilado de Inicio: ${currentSettings.taperStart.toInt()}px",
                value = currentSettings.taperStart,
                valueRange = 0f..150f,
                onValueChange = { onSettingsChanged(currentSettings.copy(taperStart = it)) }
            )
            
            SettingSlider(
                label = "Afilado de Fin: ${currentSettings.taperEnd.toInt()}px",
                value = currentSettings.taperEnd,
                valueRange = 0f..150f,
                onValueChange = { onSettingsChanged(currentSettings.copy(taperEnd = it)) }
            )

            // Caps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Punta Redonda (Inicio)")
                Switch(
                    checked = currentSettings.capStart,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(capStart = it)) }
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Punta Redonda (Fin)")
                Switch(
                    checked = currentSettings.capEnd,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(capEnd = it)) }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Curvas Poligonales")
                Switch(
                    checked = currentSettings.useCurveForPolygon,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(useCurveForPolygon = it)) }
                )
            }

            SettingSlider(
                label = "Predicción de Latencia (Fallback): ${currentSettings.predictionLatency} ms",
                value = currentSettings.predictionLatency.toFloat(),
                valueRange = 0f..50f,
                steps = 50,
                onValueChange = { onSettingsChanged(currentSettings.copy(predictionLatency = it.toLong())) }
            )

            HorizontalDivider()
            Text("Compresión (Salida)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            val tolerance = currentSettings.simplificationTolerance
            val toleranceLabel = if (tolerance == 0f) "Desactivado (Raw)" else String.format("%.1f", tolerance)
            
            SettingSlider(
                label = "Simplificación: $toleranceLabel",
                value = tolerance,
                valueRange = 0.0f..2.0f,
                steps = 19,
                onValueChange = { 
                    val enabled = it > 0f
                    onSettingsChanged(currentSettings.copy(simplificationTolerance = it, isSimplificationEnabled = enabled))
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
        is com.sketcher.sketchercompanionv1.tools.PenSettings -> {
            SettingSlider(
                label = "Adelgazamiento (Presión): ${(currentSettings.thinning * 100).toInt()}%",
                value = currentSettings.thinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(thinning = it)) }
            )
            SettingSlider(
                label = "Adelgazamiento (Velocidad): ${(currentSettings.velocityThinning * 100).toInt()}%",
                value = currentSettings.velocityThinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(velocityThinning = it)) }
            )
            SettingSlider(
                label = "Suavizado (Bordes): ${(currentSettings.smoothing * 100).toInt()}%",
                value = currentSettings.smoothing,
                valueRange = 0f..1.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(smoothing = it)) }
            )
        }
        is com.sketcher.sketchercompanionv1.tools.PlumaSettings -> {
            SettingSlider(
                label = "Adelgazamiento (Presión): ${(currentSettings.thinning * 100).toInt()}%",
                value = currentSettings.thinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(thinning = it)) }
            )
            SettingSlider(
                label = "Adelgazamiento (Velocidad): ${(currentSettings.velocityThinning * 100).toInt()}%",
                value = currentSettings.velocityThinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(velocityThinning = it)) }
            )
            SettingSlider(
                label = "Suavizado (Bordes)",
                value = currentSettings.smoothing,
                valueRange = 0f..1.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(smoothing = it)) }
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Simular Presión (Velocidad)")
                Switch(
                    checked = currentSettings.simulatePressure,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(simulatePressure = it)) }
                )
            }
            HorizontalDivider()
            Text("Opciones de Punta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            SettingSlider(
                label = "Afilado de Inicio: ${currentSettings.taperStart.toInt()}px",
                value = currentSettings.taperStart,
                valueRange = 0f..150f,
                onValueChange = { onSettingsChanged(currentSettings.copy(taperStart = it)) }
            )
            SettingSlider(
                label = "Afilado de Fin: ${currentSettings.taperEnd.toInt()}px",
                value = currentSettings.taperEnd,
                valueRange = 0f..150f,
                onValueChange = { onSettingsChanged(currentSettings.copy(taperEnd = it)) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Punta Redonda (Inicio)")
                Switch(
                    checked = currentSettings.capStart,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(capStart = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Punta Redonda (Fin)")
                Switch(
                    checked = currentSettings.capEnd,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(capEnd = it)) }
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Curvas Poligonales")
                Switch(
                    checked = currentSettings.useCurveForPolygon,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(useCurveForPolygon = it)) }
                )
            }
            HorizontalDivider()
            Text("Compresión (Salida)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            val tolerance = currentSettings.simplificationTolerance
            val toleranceLabel = if (tolerance == 0f) "Desactivado (Raw)" else String.format("%.1f", tolerance)
            
            SettingSlider(
                label = "Simplificación: $toleranceLabel",
                value = tolerance,
                valueRange = 0.0f..2.0f,
                steps = 19,
                onValueChange = { 
                    val enabled = it > 0f
                    onSettingsChanged(currentSettings.copy(simplificationTolerance = it, isSimplificationEnabled = enabled))
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
        is com.sketcher.sketchercompanionv1.tools.PaintSettings -> {
            SettingSlider(
                label = "Adelgazamiento (Presión): ${(currentSettings.thinning * 100).toInt()}%",
                value = currentSettings.thinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(thinning = it)) }
            )
            SettingSlider(
                label = "Adelgazamiento (Velocidad): ${(currentSettings.velocityThinning * 100).toInt()}%",
                value = currentSettings.velocityThinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(velocityThinning = it)) }
            )
            SettingSlider(
                label = "Suavizado (Bordes)",
                value = currentSettings.smoothing,
                valueRange = 0f..1.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(smoothing = it)) }
            )
            HorizontalDivider()
            Text("Contorno de Pintura", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            SettingSlider(
                label = "Grosor del Contorno: ${String.format("%.1f", currentSettings.paintOutlineWidth)} px",
                value = currentSettings.paintOutlineWidth,
                valueRange = 0.5f..15f,
                onValueChange = { onSettingsChanged(currentSettings.copy(paintOutlineWidth = it)) }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Fusionar con trazos anteriores")
                }
                Switch(
                    checked = currentSettings.paintJoinPrevious,
                    onCheckedChange = { onSettingsChanged(currentSettings.copy(paintJoinPrevious = it)) }
                )
            }
        }
        is com.sketcher.sketchercompanionv1.tools.WatercolorSettings -> {
            SettingSlider(
                label = "Adelgazamiento (Presión): ${(currentSettings.thinning * 100).toInt()}%",
                value = currentSettings.thinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(thinning = it)) }
            )
            SettingSlider(
                label = "Adelgazamiento (Velocidad): ${(currentSettings.velocityThinning * 100).toInt()}%",
                value = currentSettings.velocityThinning,
                onValueChange = { onSettingsChanged(currentSettings.copy(velocityThinning = it)) }
            )
            SettingSlider(
                label = "Suavizado (Bordes)",
                value = currentSettings.smoothing,
                valueRange = 0f..1.0f,
                onValueChange = { onSettingsChanged(currentSettings.copy(smoothing = it)) }
            )
            SettingSlider(
                label = "Grosor del Contorno: ${String.format("%.1f", currentSettings.paintOutlineWidth)} px",
                value = currentSettings.paintOutlineWidth,
                valueRange = 0.5f..15f,
                onValueChange = { onSettingsChanged(currentSettings.copy(paintOutlineWidth = it)) }
            )
            HorizontalDivider()
            Text("Ajustes de Acuarela", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            SettingSlider(
                label = "Desviación del Jitter: ${String.format("%.1f", currentSettings.watercolorJitterDeviation)} px",
                value = currentSettings.watercolorJitterDeviation,
                valueRange = 0f..20f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorJitterDeviation = it)) }
            )
            SettingSlider(
                label = "Segmento de Jitter: ${String.format("%.1f", currentSettings.watercolorJitterSegment)} px",
                value = currentSettings.watercolorJitterSegment,
                valueRange = 2f..50f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorJitterSegment = it)) }
            )
            SettingSlider(
                label = "Radio de Desenfoque: ${String.format("%.1f", currentSettings.watercolorBlurRadius)} px",
                value = currentSettings.watercolorBlurRadius,
                valueRange = 0f..30f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorBlurRadius = it)) }
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
                label = "Grosor de Anillo: ${String.format("%.1f", currentSettings.watercolorEdgeRingWidth)} px",
                value = currentSettings.watercolorEdgeRingWidth,
                valueRange = 0f..10f,
                onValueChange = { onSettingsChanged(currentSettings.copy(watercolorEdgeRingWidth = it)) }
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
    }

    if (showFlatStrokeOption) {
        HorizontalDivider()
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

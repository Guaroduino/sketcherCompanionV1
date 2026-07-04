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
    freehandSettings: FreehandSettings,
    onFreehandSettingsChanged: (FreehandSettings) -> Unit,
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
                    text = "ConfiguraciÃ³n de Herramienta", 
                    style = MaterialTheme.typography.titleLarge
                )

                when (toolType) {
                    ToolType.FREEHAND -> {
                        FreehandSettingsContent(
                            freehandSettings,
                            onFreehandSettingsChanged,
                            isFlattenedOuterStrokeEnabled,
                            onToggleFlattenedOuterStroke
                        )
                    }
                    ToolType.ERASER -> {
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
    currentSettings: FreehandSettings,
    onSettingsChanged: (FreehandSettings) -> Unit,
    isFlattenedOuterStrokeEnabled: Boolean,
    onToggleFlattenedOuterStroke: () -> Unit,
    showFlatStrokeOption: Boolean = true,
    showPaintOutlineOption: Boolean = false,
    showJoinPreviousOption: Boolean = false,
    showCapsOption: Boolean = true,
    showPolygonOption: Boolean = true,
    title: String = "Ajustes de Pincel"
) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

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
        valueRange = 0f..1.0f, // Perfect Freehand usually 0-1
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
    
    if (showPaintOutlineOption) {
        HorizontalDivider()
        Text("Contorno de Pintura", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        SettingSlider(
            label = "Grosor del Contorno: ${String.format("%.1f", currentSettings.paintOutlineWidth)} px",
            value = currentSettings.paintOutlineWidth,
            valueRange = 0.5f..15f,
            onValueChange = { onSettingsChanged(currentSettings.copy(paintOutlineWidth = it)) }
        )
    }

    if (showJoinPreviousOption) {
        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fusionar con trazos anteriores")
                Text(
                    text = "Une automáticamente los trazos nuevos con los trazos que se solapen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
            Switch(
                checked = currentSettings.paintJoinPrevious,
                onCheckedChange = { onSettingsChanged(currentSettings.copy(paintJoinPrevious = it)) }
            )
        }
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

    if (showCapsOption) {
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
    }

    if (showPolygonOption) {
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
    }

    HorizontalDivider()
    Text("CompresiÃ³n (Salida)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    
    val tolerance = currentSettings.simplificationTolerance
    val toleranceLabel = if (tolerance == 0f) "Desactivado (Raw)" else String.format("%.1f", tolerance)
    
    SettingSlider(
        label = "SimplificaciÃ³n: $toleranceLabel",
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
        Text("MÃ¡xima fidelidad (Muchos puntos)", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    } else if (tolerance > 1.0f) {
        Text("Alta compresiÃ³n (Formas geomÃ©tricas)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
    } else {
        Text("Equilibrado - Recomendado", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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
                Text(
                    text = "Procesa los puntos exteriores vectorialmente al levantar el lápiz para una mancha limpia y sin trazos internos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
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

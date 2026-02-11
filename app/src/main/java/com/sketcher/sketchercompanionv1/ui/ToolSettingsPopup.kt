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
                        FreehandSettingsContent(freehandSettings, onFreehandSettingsChanged)
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
    onSettingsChanged: (FreehandSettings) -> Unit
) {
    Text("Ajustes de Pincel (Perfect Freehand)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    // 1. Thinning
    SettingSlider(
        label = "Adelgazamiento (PresiÃ³n): ${(currentSettings.thinning * 100).toInt()}%",
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

    // 2. Streamline
    SettingSlider(
        label = "EstabilizaciÃ³n (Streamline): ${(currentSettings.streamline * 100).toInt()}%",
        value = currentSettings.streamline,
        onValueChange = { onSettingsChanged(currentSettings.copy(streamline = it)) }
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
        label = "Grosor MÃ­nimo: ${(currentSettings.minWidthRatio * 100).toInt()}%",
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
        Text("Simular PresiÃ³n (Velocidad)")
        Switch(
            checked = currentSettings.simulatePressure,
            onCheckedChange = { onSettingsChanged(currentSettings.copy(simulatePressure = it)) }
        )
    }
    

    HorizontalDivider()
    
    Text("Opciones de Punta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    // Taper
    val taperStartText = if (currentSettings.taperStart > 0) "Afilado" else if (currentSettings.taperStart < 0) "Ensanchado" else "-"
     SettingSlider(
        label = "Inicio ($taperStartText): ${currentSettings.taperStart.toInt()}px",
        value = currentSettings.taperStart,
        valueRange = -150f..150f,
        onValueChange = { onSettingsChanged(currentSettings.copy(taperStart = it)) }
    )
    
    if (currentSettings.taperStart > 0) {
        SettingSlider(
            label = "   â””â”€ Grueso Punta: ${(currentSettings.taperStartTipRatio * 100).toInt()}%",
            value = currentSettings.taperStartTipRatio,
            onValueChange = { onSettingsChanged(currentSettings.copy(taperStartTipRatio = it)) }
        )
    } else if (currentSettings.taperStart < 0) {
        SettingSlider(
            label = "   â””â”€ Intensidad: ${String.format("%.1f", currentSettings.wideningStartRatio)}x",
            value = currentSettings.wideningStartRatio,
            valueRange = 1f..5f,
            onValueChange = { onSettingsChanged(currentSettings.copy(wideningStartRatio = it)) }
        )
    }
    
    val taperEndText = if (currentSettings.taperEnd > 0) "Afilado" else if (currentSettings.taperEnd < 0) "Ensanchado" else "-"
     SettingSlider(
        label = "Fin ($taperEndText): ${currentSettings.taperEnd.toInt()}px",
        value = currentSettings.taperEnd,
        valueRange = -150f..150f,
        onValueChange = { onSettingsChanged(currentSettings.copy(taperEnd = it)) }
    )

    if (currentSettings.taperEnd > 0) {
        SettingSlider(
            label = "   â””â”€ Grueso Punta: ${(currentSettings.taperEndTipRatio * 100).toInt()}%",
            value = currentSettings.taperEndTipRatio,
            onValueChange = { onSettingsChanged(currentSettings.copy(taperEndTipRatio = it)) }
        )
    } else if (currentSettings.taperEnd < 0) {
        SettingSlider(
            label = "   â””â”€ Intensidad: ${String.format("%.1f", currentSettings.wideningEndRatio)}x",
            value = currentSettings.wideningEndRatio,
            valueRange = 1f..5f,
            onValueChange = { onSettingsChanged(currentSettings.copy(wideningEndRatio = it)) }
        )
    }

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

    HorizontalDivider()
    
    Text("PredicciÃ³n de Entrada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    // Min/Max prediction
    SettingSlider(
        label = "PredicciÃ³n: ${currentSettings.predictionLatency.toInt()}ms",
        value = currentSettings.predictionLatency,
        valueRange = 0f..50f,
        onValueChange = { onSettingsChanged(currentSettings.copy(predictionLatency = it)) }
    )

    HorizontalDivider()
    Text("CompresiÃ³n (Salida)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    
    val tolerance = currentSettings.tolerance
    val toleranceLabel = if (tolerance == 0f) "Desactivado (Raw)" else String.format("%.1f", tolerance)
    
    SettingSlider(
        label = "SimplificaciÃ³n: $toleranceLabel",
        value = tolerance,
        valueRange = 0.0f..2.0f,
        steps = 19,
        onValueChange = { 
            val enabled = it > 0f
            onSettingsChanged(currentSettings.copy(tolerance = it, isSimplificationEnabled = enabled))
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

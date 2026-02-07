package com.skecher.sketchercompanionv1.ui

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
import com.skecher.sketchercompanionv1.dto.FreehandSettings
import com.skecher.sketchercompanionv1.dto.ToolType
import com.skecher.sketchercompanionv1.SketcherViewModel
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


@Composable
fun ToolSettingsPopup(
    toolType: ToolType,
    // Freehand Specs
    freehandSettings: FreehandSettings,
    onFreehandSettingsChanged: (FreehandSettings) -> Unit,
    
    
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Configuración de Herramienta", 
                    style = MaterialTheme.typography.titleLarge
                )

                when (toolType) {
                    ToolType.FREEHAND -> {
                        FreehandSettingsContent(freehandSettings, onFreehandSettingsChanged)
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

    // 2. Streamline
    SettingSlider(
        label = "Estabilización (Streamline): ${(currentSettings.streamline * 100).toInt()}%",
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
    val taperStartText = if (currentSettings.taperStart > 0) "Afilado" else if (currentSettings.taperStart < 0) "Ensanchado" else "-"
     SettingSlider(
        label = "Inicio ($taperStartText): ${currentSettings.taperStart.toInt()}px",
        value = currentSettings.taperStart,
        valueRange = -150f..150f,
        onValueChange = { onSettingsChanged(currentSettings.copy(taperStart = it)) }
    )
    
    val taperEndText = if (currentSettings.taperEnd > 0) "Afilado" else if (currentSettings.taperEnd < 0) "Ensanchado" else "-"
     SettingSlider(
        label = "Fin ($taperEndText): ${currentSettings.taperEnd.toInt()}px",
        value = currentSettings.taperEnd,
        valueRange = -150f..150f,
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

    HorizontalDivider()
    
    Text("Predicción de Entrada", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    // Min/Max prediction
    SettingSlider(
        label = "Predicción: ${currentSettings.predictionLatency.toInt()}ms",
        value = currentSettings.predictionLatency,
        valueRange = 0f..50f,
        onValueChange = { onSettingsChanged(currentSettings.copy(predictionLatency = it)) }
    )

}

@Composable
fun InputSettingsPopup(
    viewModel: SketcherViewModel,
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
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Procesamiento de Trazo",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )

                // --- SECTION: INPUT ---
                Text("Entrada (Input)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Text("Estabilización (Lazy Stroke): ${(viewModel.globalStabilizationLevel * 100).toInt()}%")
                Slider(
                    value = viewModel.globalStabilizationLevel,
                    onValueChange = { viewModel.setGlobalStabilization(it) },
                    valueRange = 0f..0.90f
                )
                Text("Suaviza el pulso antes de dibujar.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // --- SECTION: OUTPUT ---
                Text("Salida (Compresión)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Habilitar Simplificación")
                    Switch(
                        checked = viewModel.currentFreehandSettings.isSimplificationEnabled,
                        onCheckedChange = { viewModel.setFreehandSimplificationEnabled(it) }
                    )
                }

                if (viewModel.currentFreehandSettings.isSimplificationEnabled) {
                    val tolerance = viewModel.currentFreehandSettings.tolerance
                    Text("Simplificación: ${String.format("%.1f", tolerance)}")
                    Slider(
                        value = tolerance,
                        onValueChange = { viewModel.setFreehandTolerance(it) },
                        valueRange = 0.0f..2.0f,
                        steps = 19
                    )
                    
                    if (tolerance < 0.2f) {
                        Text("Máxima fidelidad (Muchos puntos)", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                    } else if (tolerance > 1.0f) {
                        Text("Alta compresión (Formas geométricas)", color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodySmall)
                    } else {
                        Text("Equilibrado - Recomendado", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                } else {
                     Text("Modo RAW: Se guardan todos los puntos del evento.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
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

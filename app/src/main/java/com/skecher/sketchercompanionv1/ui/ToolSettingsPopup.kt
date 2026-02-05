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

    // 0. Pressure Influence
    SettingSlider(
        label = "Influencia Presión: ${(currentSettings.pressureInfluence * 100).toInt()}%",
        value = currentSettings.pressureInfluence,
        onValueChange = { onSettingsChanged(currentSettings.copy(pressureInfluence = it)) }
    )

    // 1. Velocity Influence
    SettingSlider(
        label = "Influencia Velocidad: ${(currentSettings.velocityInfluence * 100).toInt()}%",
        value = currentSettings.velocityInfluence,
        onValueChange = { onSettingsChanged(currentSettings.copy(velocityInfluence = it)) }
    )
    
    // 1b. Min Width
    SettingSlider(
        label = "Grosor Mínimo: ${(currentSettings.minWidthRatio * 100).toInt()}%",
        value = currentSettings.minWidthRatio,
        onValueChange = { onSettingsChanged(currentSettings.copy(minWidthRatio = it)) }
    )

    // 2. Smoothing
    SettingSlider(
        label = "Suavizado",
        value = currentSettings.smoothing,
        valueRange = 0f..3.0f,
        onValueChange = { onSettingsChanged(currentSettings.copy(smoothing = it)) }
    )

    HorizontalDivider()
    
    Text("Predicción de Velocidad", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    // Min Velocity (px/ms)
    SettingSlider(
        label = "Umbral Mínimo: ${String.format("%.1f", currentSettings.minPredictionVelocity)} px/ms",
        value = currentSettings.minPredictionVelocity,
        valueRange = 0.1f..2.0f,
        onValueChange = { onSettingsChanged(currentSettings.copy(minPredictionVelocity = it)) }
    )

    // Max Velocity (px/ms)
    SettingSlider(
        label = "Umbral Máximo: ${String.format("%.1f", currentSettings.maxPredictionVelocity)} px/ms",
        value = currentSettings.maxPredictionVelocity,
        valueRange = 1.0f..8.0f,
        onValueChange = { onSettingsChanged(currentSettings.copy(maxPredictionVelocity = it)) }
    )

    // 3. Streamline (Removed)

    HorizontalDivider()
    
    Text("Opciones de Punta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    // Taper
     SettingSlider(
        label = if (currentSettings.taperStart >= 0) "Inicio Afilado: ${(currentSettings.taperStart * 100).toInt()}%" 
                else "Inicio Ensanchado: ${(currentSettings.taperStart * -100).toInt()}%",
        value = currentSettings.taperStart,
        valueRange = -1f..1f,
        onValueChange = { onSettingsChanged(currentSettings.copy(taperStart = it)) }
    )
    
     SettingSlider(
        label = if (currentSettings.taperEnd >= 0) "Fin Afilado: ${(currentSettings.taperEnd * 100).toInt()}%" 
                else "Fin Ensanchado: ${(currentSettings.taperEnd * -100).toInt()}%",
        value = currentSettings.taperEnd,
        valueRange = -1f..1f,
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

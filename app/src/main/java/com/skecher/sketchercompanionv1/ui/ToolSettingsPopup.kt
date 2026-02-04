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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll


@Composable
fun ToolSettingsPopup(
    toolType: ToolType,
    // Freehand Specs
    freehandSettings: FreehandSettings,
    onFreehandSettingsChanged: (FreehandSettings) -> Unit,
    
    // Ink/Prediction Specs (Global or per-tool in VM, passed here)
    isPredictionEnabled: Boolean,
    onTogglePrediction: (Boolean) -> Unit,
    predictionLatency: Float,
    onPredictionLatencyChanged: (Float) -> Unit,
    predictionSmoothing: Float,
    onPredictionSmoothingChanged: (Float) -> Unit,
    
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

    // 3. Streamline (Removed)

    HorizontalDivider()
    
    Text("Opciones de Punta", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)

    // Taper
     SettingSlider(
        label = "Inicio Afilado",
        value = currentSettings.taperStart,
        onValueChange = { onSettingsChanged(currentSettings.copy(taperStart = it)) }
    )
    
     SettingSlider(
        label = "Fin Afilado",
        value = currentSettings.taperEnd,
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

// InkSettingsContent removed

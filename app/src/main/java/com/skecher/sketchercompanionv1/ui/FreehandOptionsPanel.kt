package com.skecher.sketchercompanionv1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.skecher.sketchercompanionv1.R
import com.skecher.sketchercompanionv1.dto.FreehandSettings

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun FreehandOptionsPanel(
    currentSettings: FreehandSettings,
    onSettingsChanged: (FreehandSettings) -> Unit,
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
                    text = "Ajustes de Mano Alzada", // TODO: Move to strings.xml
                    style = MaterialTheme.typography.titleLarge
                )

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
                
                // 1b. Min Width (Migrated from Tech Pen)
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

                // 3. Streamline (Input Stabilization)
                SettingSlider(
                    label = "Estabilización",
                    value = currentSettings.streamline,
                    valueRange = 0f..3.0f,
                    onValueChange = { onSettingsChanged(currentSettings.copy(streamline = it)) }
                )
                
                // 3b. Prediction (Lag Compensation)
                SettingSlider(
                    label = "Predicción Máxima: ${currentSettings.predictionLatency.toInt()} ms",
                    value = currentSettings.predictionLatency,
                    valueRange = 0f..50f,
                    onValueChange = { onSettingsChanged(currentSettings.copy(predictionLatency = it)) }
                )

                // 3b-2. Velocity Prediction Thresholds
                SettingSlider(
                    label = "Predicción - Velocidad Mínima: ${String.format("%.1f", currentSettings.minPredictionVelocity)} px/ms",
                    value = currentSettings.minPredictionVelocity,
                    valueRange = 0f..5.0f,
                    onValueChange = { onSettingsChanged(currentSettings.copy(minPredictionVelocity = it)) }
                )

                SettingSlider(
                    label = "Predicción - Velocidad Máxima: ${String.format("%.1f", currentSettings.maxPredictionVelocity)} px/ms",
                    value = currentSettings.maxPredictionVelocity,
                    valueRange = 2f..20.0f,
                    onValueChange = { onSettingsChanged(currentSettings.copy(maxPredictionVelocity = it)) }
                )

                // 3c. Tolerance (Decimation)
                SettingSlider(
                    label = "Simplificación: ${String.format("%.1f", currentSettings.tolerance)} px",
                    value = currentSettings.tolerance,
                    valueRange = 0.1f..5.0f,
                    onValueChange = { onSettingsChanged(currentSettings.copy(tolerance = it)) }
                )

                // 3d. Input Stabilization (Rope)
                SettingSlider(
                    label = "Estabilización de Entrada (Arrastre): ${(currentSettings.inputStabilization * 100).toInt()}%",
                    value = currentSettings.inputStabilization,
                    valueRange = 0.0f..0.95f,
                    onValueChange = { onSettingsChanged(currentSettings.copy(inputStabilization = it)) }
                )
                
                // 4. Taper Start
                 SettingSlider(
                    label = "Inicio Afilado",
                    value = currentSettings.taperStart,
                    onValueChange = { onSettingsChanged(currentSettings.copy(taperStart = it)) }
                )
                
                // 5. Taper End
                 SettingSlider(
                    label = "Fin Afilado",
                    value = currentSettings.taperEnd,
                    onValueChange = { onSettingsChanged(currentSettings.copy(taperEnd = it)) }
                )

                // 6. Caps (Start/End)
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

                // 7. Spline Smoothing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Bordes Suaves (Splines)")
                    Switch(
                        checked = currentSettings.useSplines,
                        onCheckedChange = { onSettingsChanged(currentSettings.copy(useSplines = it)) }
                    )
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
fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text(text = String.format("%.2f", value), style = MaterialTheme.typography.bodyMedium)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.SettingSlider
import com.sketcher.sketchercompanionv1.ui.AppIconButton

@Composable
fun QuickStabilizationPopup(
    stabilization: Float,
    onStabilizationChange: (Float) -> Unit,
    onRestoreStabilizationPreset: () -> Unit,
    opacity: Float,
    onOpacityChange: (Float) -> Unit,
    onRestoreOpacityPreset: () -> Unit,
    onDismiss: () -> Unit,
    theme: UiThemeConfig
) {
    Dialog(onDismissRequest = onDismiss) {
        // Enforce Local Theme Compliance
        MaterialTheme(
            colorScheme = MaterialTheme.colorScheme.copy(
                primary = theme.highlightColor,
                secondary = theme.highlightColor,
                tertiary = theme.highlightColor,
                surface = theme.barBackgroundColor.copy(alpha = 1f),
                onSurface = theme.iconColor,
                onSurfaceVariant = theme.iconColor.copy(alpha = 0.7f)
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
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Estabilización & Opacidad",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        AppIconButton(
                            onClick = onDismiss,
                            icon = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.iconColor.copy(alpha = 0.6f),
                            buttonSize = 24.dp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // --- ESTABILIZACIÓN SECTION ---
                    Text(
                        text = "Estabilización",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = theme.highlightColor,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSlider(
                        label = "",
                        value = stabilization,
                        onValueChange = onStabilizationChange,
                        valueRange = 0f..0.90f,
                        layoutHorizontal = true,
                        valueFormatter = { "${(it * 100).toInt()}%" },
                        labelStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onRestoreStabilizationPreset,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = theme.highlightColor
                        ),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "Usar preset",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f), thickness = 1.dp)
                    Spacer(modifier = Modifier.height(8.dp))

                    // --- OPACIDAD SECTION ---
                    Text(
                        text = "Opacidad",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = theme.highlightColor,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SettingSlider(
                        label = "",
                        value = opacity,
                        onValueChange = onOpacityChange,
                        valueRange = 0.0f..1.0f,
                        layoutHorizontal = true,
                        valueFormatter = { "${(it * 100).toInt()}%" },
                        labelStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    TextButton(
                        onClick = onRestoreOpacityPreset,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = theme.highlightColor
                        ),
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(
                            text = "Usar preset",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

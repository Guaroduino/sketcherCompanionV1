package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

@Composable
fun QuickStabilizationPopup(
    value: Float,
    onValueChange: (Float) -> Unit,
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
                    .width(280.dp)
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
                            text = "Estabilización",
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Slider + Value Display
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Slider(
                            value = value,
                            onValueChange = onValueChange,
                            valueRange = 0f..0.90f,
                            modifier = Modifier.weight(1f)
                        )
                        
                        Text(
                            text = "${(value * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.width(40.dp)
                        )
                    }
                }
            }
        }
    }
}

package com.sketcher.sketchercompanionv1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.sketcher.sketchercompanionv1.dto.*

@Composable
fun PaperSizeDialog(
    currentConfig: CanvasSizeConfig?,
    currentColor: Int,
    pixelsPerMm: Float = 5.0f,
    onDismiss: () -> Unit,
    onConfirm: (CanvasSizeConfig?, Int) -> Unit
) {
    var selectedTab by remember { mutableStateOf(if (currentConfig == null) 0 else if (currentConfig.preset != null) 1 else 2) }
    var selectedPreset by remember { mutableStateOf(currentConfig?.preset ?: PaperSizePreset.LETTER) }
    var selectedOrientation by remember { mutableStateOf(currentConfig?.orientation ?: PaperOrientation.PORTRAIT) }
    var selectedOrigin by remember { mutableStateOf(currentConfig?.origin ?: CoordinateOrigin.TOP_LEFT) } // Origin State
    var selectedColor by remember { mutableStateOf(Color(currentColor)) }
    
    // Custom size state
    var customWidth by remember { mutableStateOf(currentConfig?.widthInPixels?.toInt()?.toString() ?: "2550") }
    var customHeight by remember { mutableStateOf(currentConfig?.heightInPixels?.toInt()?.toString() ?: "3300") }
    var customUnit by remember { mutableStateOf(DistanceUnit.MM) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tamaño de Lienzo") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(490.dp)
            ) {
                // Tabs
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Infinito") }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Predeterminado") }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = { Text("Personalizado") }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Content based on selected tab
                when (selectedTab) {
                    0 -> {
                        // Infinite canvas
                        Text(
                            "El lienzo será infinito sin límites.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    1 -> {
                        // Predefined sizes
                        Column {
                            // Orientation selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                FilterChip(
                                    selected = selectedOrientation == PaperOrientation.PORTRAIT,
                                    onClick = { selectedOrientation = PaperOrientation.PORTRAIT },
                                    label = { Text("Vertical") },
                                    leadingIcon = if (selectedOrientation == PaperOrientation.PORTRAIT) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                                    } else null
                                )
                                FilterChip(
                                    selected = selectedOrientation == PaperOrientation.LANDSCAPE,
                                    onClick = { selectedOrientation = PaperOrientation.LANDSCAPE },
                                    label = { Text("Horizontal") },
                                    leadingIcon = if (selectedOrientation == PaperOrientation.LANDSCAPE) {
                                        { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                                    } else null
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // Origin Selector
                            Text("Origen de Coordenadas:", style = MaterialTheme.typography.bodyMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = selectedOrigin == CoordinateOrigin.TOP_LEFT,
                                    onClick = { selectedOrigin = CoordinateOrigin.TOP_LEFT }
                                )
                                Text("Superior Izquierda", modifier = Modifier.clickable { selectedOrigin = CoordinateOrigin.TOP_LEFT })
                                Spacer(modifier = Modifier.width(16.dp))
                                RadioButton(
                                    selected = selectedOrigin == CoordinateOrigin.CENTER,
                                    onClick = { selectedOrigin = CoordinateOrigin.CENTER }
                                )
                                Text("Centro", modifier = Modifier.clickable { selectedOrigin = CoordinateOrigin.CENTER })
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            // List of presets
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(PaperSizePreset.entries) { preset ->
                                    val (width, height) = preset.getUnitDimensions(DistanceUnit.MM, selectedOrientation)
                                    val isSelected = selectedPreset == preset
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { selectedPreset = preset }
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                                else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                preset.displayName,
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                            Text(
                                                "${width.toInt()} × ${height.toInt()} mm",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        if (isSelected) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    2 -> {
                        // Custom size
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                "Dimensiones personalizadas en píxeles:",
                                style = MaterialTheme.typography.bodyMedium
                            )

                            OutlinedTextField(
                                value = customWidth,
                                onValueChange = { customWidth = it.filter { c -> c.isDigit() } },
                                label = { Text("Ancho (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = customHeight,
                                onValueChange = { customHeight = it.filter { c -> c.isDigit() } },
                                label = { Text("Alto (px)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                "Nota: 300 DPI es calidad de impresión estándar",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Color del Papel:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val paperPresets = listOf(
                        Color.White,
                        Color(0xFFFFFDF6), // Off-white / Cream
                        Color(0xFFF5F5F5), // Light Gray
                        Color(0xFFE8F5E9), // Light Green
                        Color(0xFF1E3A8A), // Blueprint Blue
                        Color(0xFF1E293B), // Slate / Dark Gray
                        Color(0xFF111111)  // Almost Black
                    )

                    paperPresets.forEach { color ->
                        val isSelected = selectedColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    shape = CircleShape
                                )
                                .clickable { selectedColor = color },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = if (color == Color.White || color == Color(0xFFFFFDF6) || color == Color(0xFFF5F5F5) || color == Color(0xFFE8F5E9)) Color.Black else Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Custom Color Picker Button
                    var showCustomColorPicker by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                shape = CircleShape
                            )
                            .clickable { showCustomColorPicker = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = "Color personalizado",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (showCustomColorPicker) {
                        ColorPickerDialog(
                            initialColor = selectedColor.toArgb(),
                            onDismiss = { showCustomColorPicker = false },
                            onColorSelected = { colorInt ->
                                selectedColor = Color(colorInt)
                                showCustomColorPicker = false
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val config = when (selectedTab) {
                        0 -> null // Infinite canvas
                        1 -> CanvasSizeHelper.fromPreset(selectedPreset, selectedOrientation, pixelsPerMm).copy(origin = selectedOrigin)
                        2 -> {
                            val w = customWidth.toFloatOrNull() ?: 2550f
                            val h = customHeight.toFloatOrNull() ?: 3300f
                            CanvasSizeHelper.fromPixels(w, h).copy(origin = selectedOrigin)
                        }
                        else -> null
                    }
                    onConfirm(config, selectedColor.toArgb())
                }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}


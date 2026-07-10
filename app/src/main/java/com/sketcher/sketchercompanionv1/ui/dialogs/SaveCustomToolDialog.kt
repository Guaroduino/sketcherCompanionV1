package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.dto.BrushPreset
import com.sketcher.sketchercompanionv1.dto.CustomTool
import com.sketcher.sketchercompanionv1.dto.ToolType
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.model.VectorIcon
import com.sketcher.sketchercompanionv1.ui.components.VectorIconRenderer
import com.sketcher.sketchercompanionv1.ui.dialogs.IconEditorDialog
import com.sketcher.sketchercompanionv1.SketcherViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaveCustomToolDialog(
    viewModel: SketcherViewModel,
    preset: BrushPreset,
    baseToolType: ToolType,
    theme: UiThemeConfig,
    onDismiss: () -> Unit,
    onConfirm: (CustomTool, String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var customIconJsonState by remember { mutableStateOf<String?>(null) }
    var showIconDrawDialog by remember { mutableStateOf(false) }
    
    val iconOptions = listOf(
        "Edit" to "Lápiz",
        "Gesture" to "Pluma",
        "Brush" to "Pincel",
        "Palette" to "Acuarela",
        "AutoFixNormal" to "Borrador",
        "Title" to "Texto",
        "Build" to "Construir",
        "ColorLens" to "Color",
        "Star" to "Estrella",
        "Favorite" to "Favorito",
        "Custom" to "Personalizado"
    )
    
    var selectedIconName by remember { mutableStateOf("Edit") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                contentColor = theme.iconColor
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Guardar como Herramienta",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = theme.highlightColor
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre de la Herramienta", color = theme.iconColor.copy(alpha = 0.6f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.3f),
                        cursorColor = theme.highlightColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Selecciona un Icono:",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(iconOptions) { (iconKey, label) ->
                        val isSelected = selectedIconName == iconKey
                        val iconVector = when (iconKey) {
                            "Edit" -> Icons.Default.Edit
                            "Gesture" -> Icons.Default.Gesture
                            "Brush" -> Icons.Default.Brush
                            "Palette" -> Icons.Default.Palette
                            "AutoFixNormal" -> Icons.Default.AutoFixNormal
                            "Title" -> Icons.Default.Title
                            "Build" -> Icons.Default.Build
                            "ColorLens" -> Icons.Default.ColorLens
                            "Star" -> Icons.Default.Star
                            "Favorite" -> Icons.Default.Favorite
                            else -> Icons.Default.Edit
                        }

                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) theme.highlightColor else theme.buttonColor.copy(alpha = 0.2f)
                                )
                                .clickable {
                                    selectedIconName = iconKey
                                    if (iconKey == "Custom") {
                                        showIconDrawDialog = true
                                    }
                                }
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) theme.highlightColor else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (iconKey == "Custom") {
                                if (customIconJsonState != null) {
                                    val pathsList = remember(customIconJsonState) {
                                        try {
                                            val type = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                                            val paths: List<String> = com.google.gson.Gson().fromJson(customIconJsonState, type)
                                            VectorIcon(paths)
                                        } catch (e: Exception) {
                                            null
                                        }
                                    }
                                    if (pathsList != null) {
                                        VectorIconRenderer(
                                            vectorIcon = pathsList,
                                            tint = if (isSelected) theme.barBackgroundColor else theme.iconColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Gesture,
                                            contentDescription = label,
                                            tint = if (isSelected) theme.barBackgroundColor else theme.iconColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Gesture,
                                            contentDescription = label,
                                            tint = if (isSelected) theme.barBackgroundColor else theme.iconColor.copy(alpha = 0.5f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Dibujar",
                                            fontSize = 8.sp,
                                            color = if (isSelected) theme.barBackgroundColor else theme.iconColor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                Icon(
                                    imageVector = iconVector,
                                    contentDescription = label,
                                    tint = if (isSelected) theme.barBackgroundColor else theme.iconColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // Preset Summary
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.buttonColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Resumen de Configuración:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = theme.iconColor.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "• Herramienta Base: ${baseToolType.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.iconColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• Grosor: ${preset.size.toInt()} px",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.iconColor.copy(alpha = 0.8f)
                    )
                    Text(
                        text = "• Opacidad: ${(preset.opacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.iconColor.copy(alpha = 0.8f)
                    )
                    if (preset.stabilization != null) {
                        Text(
                            text = "• Estabilización: ${(preset.stabilization * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = theme.iconColor.copy(alpha = 0.8f)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = theme.iconColor
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.3f))
                    ) {
                        Text("Cancelar")
                    }

                    Button(
                        onClick = {
                            if (name.isNotBlank()) {
                                // Map icon name to typical drawable resource name if any
                                val iconResName = when(selectedIconName) {
                                    "Edit" -> "ic_tabler_pencil"
                                    "Gesture" -> "ic_tabler_pen"
                                    "Brush" -> "ic_tabler_paint"
                                    "Palette" -> "ic_tabler_watercolor"
                                    "AutoFixNormal" -> "ic_tabler_eraser"
                                    else -> null
                                }
                                val newCustomTool = CustomTool(
                                    id = "custom_tool_" + UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    iconName = selectedIconName,
                                    iconResName = iconResName,
                                    baseToolType = baseToolType,
                                    preset = preset,
                                    customIconJson = if (selectedIconName == "Custom") customIconJsonState else null
                                )
                                onConfirm(newCustomTool, if (selectedIconName == "Custom") customIconJsonState else null)
                            }
                        },
                        enabled = name.isNotBlank() && (selectedIconName != "Custom" || customIconJsonState != null),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.highlightColor,
                            contentColor = theme.barBackgroundColor,
                            disabledContainerColor = theme.buttonColor.copy(alpha = 0.1f),
                            disabledContentColor = theme.iconColor.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Confirmar")
                    }
                }
            }
        }
    }

    if (showIconDrawDialog) {
        IconEditorDialog(
            viewModel = viewModel,
            onDismiss = { showIconDrawDialog = false },
            initialIconJson = customIconJsonState,
            onSaveIconJson = { jsonStr ->
                customIconJsonState = jsonStr
            }
        )
    }
}

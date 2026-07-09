package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.dto.BrushPreset
import com.sketcher.sketchercompanionv1.dto.CustomTool
import com.sketcher.sketchercompanionv1.dto.ToolType
import com.sketcher.sketchercompanionv1.tools.*
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateBrushDialog(
    viewModel: SketcherViewModel,
    theme: UiThemeConfig,
    onDismiss: () -> Unit,
    onBrushCreated: (CustomTool) -> Unit
) {
    var step by remember { mutableStateOf(1) }
    var selectedEngine by remember { mutableStateOf<ToolType?>(null) }
    var brushName by remember { mutableStateOf("") }
    
    // Configs
    var size by remember { mutableFloatStateOf(10f) }
    var opacity by remember { mutableFloatStateOf(1f) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = theme.barBackgroundColor, contentColor = theme.iconColor)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                Text("Taller de Pinceles - Paso $step", fontWeight = FontWeight.Bold, color = theme.highlightColor, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(16.dp))

                if (step == 1) {
                    Text("Selecciona el Motor Base:", fontWeight = FontWeight.SemiBold)
                    Spacer(modifier = Modifier.height(8.dp))
                    val engines = listOf(
                        ToolType.FREEHAND to "Lápiz (Texturizado)",
                        ToolType.PEN to "Bolígrafo (Sólido)",
                        ToolType.PLUMA to "Pluma (Caligráfico)",
                        ToolType.PAINT to "Pincel (Pintura)",
                        ToolType.WATERCOLOR to "Acuarela (Mezcla)"
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                        engines.forEach { (type, label) ->
                            val isSelected = selectedEngine == type
                            Card(
                                modifier = Modifier.fillMaxWidth().clickable { selectedEngine = type },
                                border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, theme.highlightColor) else null,
                                colors = CardDefaults.cardColors(containerColor = if (isSelected) theme.highlightColor.copy(alpha=0.1f) else theme.buttonColor.copy(alpha=0.1f))
                            ) {
                                Text(label, modifier = Modifier.padding(16.dp), color = theme.iconColor)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cancelar", color = theme.iconColor) }
                        Button(onClick = { step = 2 }, enabled = selectedEngine != null, colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)) {
                            Text("Siguiente", color = Color.White)
                        }
                    }
                } else if (step == 2) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState()).weight(1f)) {
                        Text("Nombre del Pincel", fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = brushName,
                            onValueChange = { brushName = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = theme.highlightColor, focusedTextColor = theme.iconColor, unfocusedTextColor = theme.iconColor)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Grosor Base: ${size.toInt()}px")
                        Slider(value = size, onValueChange = { size = it }, valueRange = 1f..100f, colors = SliderDefaults.colors(thumbColor = theme.highlightColor, activeTrackColor = theme.highlightColor))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Opacidad Base: ${(opacity * 100).toInt()}%")
                        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.1f..1f, colors = SliderDefaults.colors(thumbColor = theme.highlightColor, activeTrackColor = theme.highlightColor))
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { step = 1 }) { Text("Atrás", color = theme.iconColor) }
                        Button(
                            onClick = {
                                val engine = selectedEngine!!
                                val settings = when (engine) {
                                    ToolType.PEN -> PenSettings(size = size, opacity = opacity)
                                    ToolType.PLUMA -> PlumaSettings(size = size, opacity = opacity)
                                    ToolType.PAINT -> PaintSettings(size = size, opacity = opacity)
                                    ToolType.WATERCOLOR -> WatercolorSettings(size = size, opacity = opacity)
                                    else -> PencilSettings(size = size, opacity = opacity)
                                }
                                val newBrush = CustomTool(
                                    id = "brush_" + UUID.randomUUID().toString(),
                                    name = brushName.ifBlank { "Pincel Personalizado" },
                                    iconName = "brush",
                                    iconResName = null,
                                    baseToolType = engine,
                                    preset = BrushPreset(
                                        size = size,
                                        opacity = opacity,
                                        settings = settings,
                                        stabilization = if (engine == ToolType.FREEHAND) 0.07f else 0f
                                    )
                                )
                                onBrushCreated(newBrush)
                            },
                            enabled = brushName.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)
                        ) {
                            Text("Guardar Pincel", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

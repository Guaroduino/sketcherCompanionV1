package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.utils.TemplateManager
import java.io.File

enum class PaperSizeOption(
    val displayName: String,
    val preset: com.sketcher.sketchercompanionv1.dto.PaperSizePreset?
) {
    INFINITE("Lienzo Infinito", null),
    LETTER("Carta (Letter)", com.sketcher.sketchercompanionv1.dto.PaperSizePreset.LETTER),
    LEGAL("Oficio (Legal)", com.sketcher.sketchercompanionv1.dto.PaperSizePreset.LEGAL),
    A4("A4", com.sketcher.sketchercompanionv1.dto.PaperSizePreset.A4),
    A3("A3", com.sketcher.sketchercompanionv1.dto.PaperSizePreset.A3),
    A5("A5", com.sketcher.sketchercompanionv1.dto.PaperSizePreset.A5),
    TABLOID("Tabloide", com.sketcher.sketchercompanionv1.dto.PaperSizePreset.TABLOID)
}

enum class PaperDesignOption(
    val displayName: String,
    val patternName: String?
) {
    BLANK("Blanco", null),
    NOTEBOOK("Línea sencilla con margen rojo", "NOTEBOOK"),
    MATH_GRID("Cuadrícula de matemáticas", "MATH_GRID"),
    CALLIGRAPHY("Doble línea para caligrafía con margen", "CALLIGRAPHY")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    initialName: String,
    theme: UiThemeConfig,
    onDismiss: () -> Unit,
    onConfirm: (name: String, templateFile: File?, scaleRatio: Float, canvasSizeConfig: com.sketcher.sketchercompanionv1.dto.CanvasSizeConfig?, backgroundStyle: com.sketcher.sketchercompanionv1.dto.FillStyle?) -> Unit
) {
    val context = LocalContext.current
    var projectName by remember { mutableStateOf(initialName) }
    
    val templates = remember { TemplateManager.getAvailableTemplates(context) }
    var selectedTemplate by remember { mutableStateOf<File?>(null) }
    var expandedTemplate by remember { mutableStateOf(false) }
    
    var selectedPaperSize by remember { mutableStateOf(PaperSizeOption.INFINITE) }
    var expandedPaperSize by remember { mutableStateOf(false) }
    
    var selectedPaperDesign by remember { mutableStateOf(PaperDesignOption.BLANK) }
    var expandedPaperDesign by remember { mutableStateOf(false) }
    
    var selectedScale by remember { mutableStateOf(1f) }
    var expandedScale by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor,
                contentColor = theme.iconColor
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Nuevo Dibujo",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.iconColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Nombre del proyecto", color = theme.iconColor.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                )

                // Template Dropdown
                Text(text = "Plantilla", fontSize = 14.sp, color = theme.iconColor.copy(alpha = 0.7f))
                ExposedDropdownMenuBox(
                    expanded = expandedTemplate,
                    onExpandedChange = { expandedTemplate = !expandedTemplate },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    OutlinedTextField(
                        value = selectedTemplate?.nameWithoutExtension ?: "Lienzo en blanco (Por defecto)",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedTemplate) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedTextColor = theme.iconColor,
                            unfocusedTextColor = theme.iconColor,
                            focusedBorderColor = theme.highlightColor
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedTemplate,
                        onDismissRequest = { expandedTemplate = false },
                        modifier = Modifier.background(theme.barBackgroundColor)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Lienzo en blanco (Por defecto)", color = theme.iconColor) },
                            onClick = { selectedTemplate = null; expandedTemplate = false }
                        )
                        templates.forEach { file ->
                            DropdownMenuItem(
                                text = { Text(file.nameWithoutExtension, color = theme.iconColor) },
                                onClick = { selectedTemplate = file; expandedTemplate = false }
                            )
                        }
                    }
                }

                // Paper Size Dropdown (only visible when no template is selected)
                if (selectedTemplate == null) {
                    Text(text = "Tamaño de papel", fontSize = 14.sp, color = theme.iconColor.copy(alpha = 0.7f))
                    ExposedDropdownMenuBox(
                        expanded = expandedPaperSize,
                        onExpandedChange = { expandedPaperSize = !expandedPaperSize },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedPaperSize.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPaperSize) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedTextColor = theme.iconColor,
                                unfocusedTextColor = theme.iconColor,
                                focusedBorderColor = theme.highlightColor
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPaperSize,
                            onDismissRequest = { expandedPaperSize = false },
                            modifier = Modifier.background(theme.barBackgroundColor)
                        ) {
                            PaperSizeOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName, color = theme.iconColor) },
                                    onClick = { selectedPaperSize = option; expandedPaperSize = false }
                                )
                            }
                        }
                    }
                }

                // Paper Design Dropdown (only visible when no template is selected)
                if (selectedTemplate == null) {
                    Text(text = "Diseño de papel", fontSize = 14.sp, color = theme.iconColor.copy(alpha = 0.7f))
                    ExposedDropdownMenuBox(
                        expanded = expandedPaperDesign,
                        onExpandedChange = { expandedPaperDesign = !expandedPaperDesign },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                    ) {
                        OutlinedTextField(
                            value = selectedPaperDesign.displayName,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedPaperDesign) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                                focusedTextColor = theme.iconColor,
                                unfocusedTextColor = theme.iconColor,
                                focusedBorderColor = theme.highlightColor
                            ),
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = expandedPaperDesign,
                            onDismissRequest = { expandedPaperDesign = false },
                            modifier = Modifier.background(theme.barBackgroundColor)
                        ) {
                            PaperDesignOption.values().forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.displayName, color = theme.iconColor) },
                                    onClick = { selectedPaperDesign = option; expandedPaperDesign = false }
                                )
                            }
                        }
                    }
                }

                // Global Scale Dropdown
                Text(text = "Escala Global Inicial", fontSize = 14.sp, color = theme.iconColor.copy(alpha = 0.7f))
                ExposedDropdownMenuBox(
                    expanded = expandedScale,
                    onExpandedChange = { expandedScale = !expandedScale },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    OutlinedTextField(
                        value = PREDEFINED_SCALES.find { it.first == selectedScale }?.second ?: "1:1",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedScale) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                            focusedTextColor = theme.iconColor,
                            unfocusedTextColor = theme.iconColor,
                            focusedBorderColor = theme.highlightColor
                        ),
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = expandedScale,
                        onDismissRequest = { expandedScale = false },
                        modifier = Modifier.background(theme.barBackgroundColor)
                    ) {
                        PREDEFINED_SCALES.forEach { scaleOption ->
                            DropdownMenuItem(
                                text = { Text(scaleOption.second, color = theme.iconColor) },
                                onClick = { selectedScale = scaleOption.first; expandedScale = false }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = theme.iconColor)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (projectName.isNotBlank()) {
                                val sizeConfig = if (selectedTemplate != null) null else {
                                    val preset = selectedPaperSize.preset
                                    if (preset != null) {
                                        com.sketcher.sketchercompanionv1.dto.CanvasSizeHelper.fromPreset(preset)
                                    } else null
                                }
                                val bgStyle = if (selectedTemplate != null) null else {
                                    val patternName = selectedPaperDesign.patternName
                                    if (patternName != null) {
                                        val color = when (patternName) {
                                            "NOTEBOOK" -> "#C5D0E6"
                                            "MATH_GRID" -> "#D9E1F0"
                                            "CALLIGRAPHY" -> "#A2B5CD"
                                            else -> "#C5D0E6"
                                        }
                                        com.sketcher.sketchercompanionv1.dto.FillStyle.MathTexture(
                                            patternName = patternName,
                                            primaryColor = android.graphics.Color.parseColor(color),
                                            secondaryColor = android.graphics.Color.WHITE
                                        )
                                    } else {
                                        com.sketcher.sketchercompanionv1.dto.FillStyle.Solid(android.graphics.Color.WHITE)
                                    }
                                }
                                onConfirm(projectName.trim(), selectedTemplate, selectedScale, sizeConfig, bgStyle)
                            }
                        },
                        enabled = projectName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.highlightColor,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        )
                    ) {
                        Text("Crear")
                    }
                }
            }
        }
    }
}

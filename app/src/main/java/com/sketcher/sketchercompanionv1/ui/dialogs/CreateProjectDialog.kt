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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectDialog(
    initialName: String,
    theme: UiThemeConfig,
    onDismiss: () -> Unit,
    onConfirm: (name: String, templateFile: File?, scaleRatio: Float) -> Unit
) {
    val context = LocalContext.current
    var projectName by remember { mutableStateOf(initialName) }
    
    val templates = remember { TemplateManager.getAvailableTemplates(context) }
    var selectedTemplate by remember { mutableStateOf<File?>(null) }
    var expandedTemplate by remember { mutableStateOf(false) }
    
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
                                onConfirm(projectName.trim(), selectedTemplate, selectedScale)
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

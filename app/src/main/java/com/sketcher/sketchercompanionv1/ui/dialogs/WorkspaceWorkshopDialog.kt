package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

import com.sketcher.sketchercompanionv1.ui.components.ColorPickerDialog
import androidx.compose.ui.window.Dialog

import com.sketcher.sketchercompanionv1.ui.components.ColorPickerDialog
import androidx.compose.ui.window.DialogProperties
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.model.WorkspaceProfile
import com.sketcher.sketchercompanionv1.ui.SettingSlider
import com.sketcher.sketchercompanionv1.ui.components.ColorPreviewRow


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceWorkshopDialog(
    viewModel: SketcherViewModel,
    theme: UiThemeConfig,
    onDismiss: () -> Unit
) {
    val profiles by viewModel.workspaceProfileRepository.getAllProfiles().collectAsState(initial = emptyList())
    val activeProfile = viewModel.workspaceProfile
    val isEditModeByVM by viewModel.isEditMode.collectAsState()

    var editingProfile = viewModel.editingWorkspaceProfile

    var showAddPresetDialog by remember { mutableStateOf(false) }
    var showRenamePresetDialog by remember { mutableStateOf(false) }
    var presetToRename by remember { mutableStateOf<WorkspaceProfile?>(null) }
    var newPresetName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .heightIn(max = 600.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                contentColor = theme.iconColor
            ),
            border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f))
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (editingProfile == null) "Taller de Esquemas" else "Editando: ${editingProfile?.name}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = theme.highlightColor
                    )
                    Row {
                        if (editingProfile == null) {
                            IconButton(onClick = { 
                                newPresetName = ""
                                showAddPresetDialog = true 
                            }) {
                                Icon(Icons.Default.Add, contentDescription = "Crear Esquema", tint = theme.highlightColor)
                            }
                        }
                        if (editingProfile != null) {
                            IconButton(onClick = { viewModel.editingWorkspaceProfile = null }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Volver", tint = theme.iconColor)
                            }
                        } else {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.iconColor)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (editingProfile == null) {
                    // LIST VIEW
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sortedPresets = profiles.sortedByDescending { it.id == activeProfile?.id }
                        items(sortedPresets, key = { it.id }) { preset ->
                            val isDefault = preset.isDefault
                            val isActive = preset.id == activeProfile?.id

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isActive) theme.highlightColor.copy(alpha = 0.15f) else theme.buttonColor.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                    .border(if (isActive) 2.dp else 1.dp, if (isActive) theme.highlightColor else theme.iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = preset.name,
                                    color = if (isActive) theme.highlightColor else theme.iconColor,
                                    fontSize = 14.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!isActive) {
                                        IconButton(
                                            onClick = { viewModel.loadWorkspaceProfile(preset) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Check, contentDescription = "Apply", tint = theme.iconColor, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    if (isDefault) {
                                        IconButton(
                                            onClick = { viewModel.resetDefaultWorkspaceProfile() },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Restaurar Default", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    IconButton(
                                        onClick = { 
                                            // Start Edit Mode for this Profile
                                            if (!isActive) {
                                                viewModel.loadWorkspaceProfile(preset)
                                            }
                                            viewModel.editingWorkspaceProfile = preset 
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.Build, contentDescription = "Editar Personalización", tint = theme.iconColor, modifier = Modifier.size(16.dp))
                                    }

                                    if (!isDefault) {
                                        IconButton(
                                            onClick = { 
                                                presetToRename = preset
                                                newPresetName = preset.name
                                                showRenamePresetDialog = true 
                                            },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Edit, contentDescription = "Rename", tint = theme.iconColor, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    IconButton(
                                        onClick = { 
                                            val copyName = "${preset.name} Copia"
                                            viewModel.copyWorkspaceProfile(preset, copyName)
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Copy", tint = theme.iconColor, modifier = Modifier.size(16.dp))
                                    }

                                    if (!isDefault) {
                                        IconButton(
                                            onClick = { viewModel.deleteWorkspaceProfile(preset) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // EDIT VIEW (Personalization)
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Scrollable Content
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                        Button(
                            onClick = { 
                                viewModel.toggleEditMode()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor, contentColor = theme.barBackgroundColor)
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isEditModeByVM) "Finalizar Edición de Botones" else "Editar Posición de Botones")
                        }

                        // Shape Switch
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Formas Redondeadas", color = theme.iconColor)
                            Switch(
                                checked = theme.isRound,
                                onCheckedChange = { viewModel.updateTheme(theme.copy(isRound = it)); viewModel.saveWorkspaceProfile(editingProfile!!) }
                            )
                        }

                        // UI Scale & Spacing
                        var tempScale by remember { mutableFloatStateOf(viewModel.interfaceScale) }
                        SettingSlider(
                            label = "Escala de Interfaz",
                            value = tempScale,
                            onValueChange = { tempScale = it },
                            onValueChangeFinished = { viewModel.updateInterfaceScale(tempScale); viewModel.saveWorkspaceProfile(editingProfile!!) },
                            valueRange = 0.5f..2.5f,
                            labelColor = theme.iconColor,
                            showValueOnRight = true,
                            valueFormatter = { "${(it * 100).toInt()}%" }
                        )

                        var tempSpacing by remember { mutableFloatStateOf(viewModel.buttonSpacingFactor) }
                        SettingSlider(
                            label = "Espaciado de Botones",
                            value = tempSpacing,
                            onValueChange = { tempSpacing = it },
                            onValueChangeFinished = { viewModel.updateButtonSpacingFactor(tempSpacing); viewModel.saveWorkspaceProfile(editingProfile!!) },
                            valueRange = 0.15f..2.0f,
                            labelColor = theme.iconColor,
                            showValueOnRight = true,
                            valueFormatter = { "${(it * 100).toInt()}%" }
                        )

                        // Color Previews
                        var pickingColorFor by remember { mutableStateOf<String?>(null) }
                        
                        ColorPreviewRow("Color de Barra", theme.barBackgroundColor, theme.iconColor) { pickingColorFor = "bar" }
                        ColorPreviewRow("Color de Botones", theme.buttonColor, theme.iconColor) { pickingColorFor = "button" }
                        ColorPreviewRow("Color de Íconos", theme.iconColor, theme.iconColor) { pickingColorFor = "icon" }
                        ColorPreviewRow("Color de Resalte", theme.highlightColor, theme.iconColor) { pickingColorFor = "highlight" }
                        ColorPreviewRow("Color de Canvas", theme.canvasColor, theme.iconColor) { pickingColorFor = "canvas" }

                        if (pickingColorFor != null) {
                            val initialColor = when(pickingColorFor) {
                                "bar" -> theme.barBackgroundColor
                                "button" -> theme.buttonColor
                                "icon" -> theme.iconColor
                                "highlight" -> theme.highlightColor
                                "canvas" -> theme.canvasColor
                                else -> Color.Transparent
                            }
                            ColorPickerDialog(
                                initialColor = initialColor,
                                recentColors = theme.recentColors,
                                theme = theme,
                                onDismiss = { pickingColorFor = null },
                                onColorSelected = { newColor: Color ->
                                    val newRecents = (listOf(newColor) + theme.recentColors).distinct().take(12)
                                    val updatedTheme = when(pickingColorFor) {
                                        "bar" -> theme.copy(barBackgroundColor = newColor, recentColors = newRecents)
                                        "button" -> theme.copy(buttonColor = newColor, recentColors = newRecents)
                                        "icon" -> theme.copy(iconColor = newColor, recentColors = newRecents)
                                        "highlight" -> theme.copy(highlightColor = newColor, recentColors = newRecents)
                                        "canvas" -> theme.copy(canvasColor = newColor, recentColors = newRecents)
                                        else -> theme
                                    }
                                    viewModel.updateTheme(updatedTheme)
                                    viewModel.saveWorkspaceProfile(editingProfile!!)
                                    pickingColorFor = null
                                }
                            )
                        }

                        // Opacity
                        SettingSlider(
                            label = "Opacidad de Barra",
                            value = theme.barBackgroundColor.alpha,
                            onValueChange = { 
                                val newTheme = theme.copy(barBackgroundColor = theme.barBackgroundColor.copy(alpha = it))
                                viewModel.updateTheme(newTheme)
                            },
                            onValueChangeFinished = { viewModel.saveWorkspaceProfile(editingProfile!!) },
                            valueRange = 0f..1f,
                            labelColor = theme.iconColor,
                            showValueOnRight = true,
                            valueFormatter = { "${(it * 100).toInt()}%" }
                        )

                        // Interface Mirror Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(theme.buttonColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                                .border(1.dp, theme.iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Invertir Interfaz (Zurdos)", fontSize = 13.sp, color = theme.iconColor)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Espejo Horizontal", fontSize = 14.sp, color = theme.iconColor)
                                    Switch(
                                        checked = viewModel.swapHorizontal,
                                        onCheckedChange = { 
                                            viewModel.toggleSwapHorizontal()
                                            viewModel.saveWorkspaceProfile(editingProfile!!)
                                        }
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Espejo Vertical", fontSize = 14.sp, color = theme.iconColor)
                                    Switch(
                                        checked = viewModel.swapVertical,
                                        onCheckedChange = { 
                                            viewModel.toggleSwapVertical()
                                            viewModel.saveWorkspaceProfile(editingProfile!!)
                                        }
                                    )
                                }
                            }

                        }
                        }

                        // Action Buttons
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            OutlinedButton(
                                onClick = { 
                                    // Reset to default theme
                                    val defaultTheme = com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig()
                                    viewModel.updateTheme(defaultTheme)
                                    viewModel.saveWorkspaceProfile(editingProfile!!.copy(theme = defaultTheme))
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = theme.iconColor)
                            ) {
                                Text("Restaurar")
                            }
                            
                            Button(
                                onClick = { 
                                    viewModel.editingWorkspaceProfile = null 
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor, contentColor = Color.White)
                            ) {
                                Text("Guardar")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddPresetDialog) {
        AlertDialog(
            onDismissRequest = { showAddPresetDialog = false },
            containerColor = theme.barBackgroundColor,
            titleContentColor = theme.iconColor,
            textContentColor = theme.iconColor,
            title = { Text("Guardar Esquema de Botones") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Introduce un nombre para este Esquema de Botones:", color = theme.iconColor.copy(alpha = 0.8f), fontSize = 14.sp)
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        placeholder = { Text("ej. Dibujo Técnico", color = theme.iconColor.copy(alpha = 0.5f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.iconColor,
                            unfocusedTextColor = theme.iconColor,
                            focusedBorderColor = theme.highlightColor,
                            unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f),
                            cursorColor = theme.highlightColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPresetName.trim()
                        if (trimmed.isNotEmpty()) {
                            val layout = viewModel.toolbarManager.getCurrentToolbarState()
                            viewModel.createWorkspaceProfile(trimmed, layout, theme)
                            showAddPresetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor, contentColor = Color.White)
                ) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddPresetDialog = false }) {
                    Text("Cancelar", color = theme.iconColor.copy(alpha = 0.8f))
                }
            }
        )
    }

    if (showRenamePresetDialog && presetToRename != null) {
        AlertDialog(
            onDismissRequest = { showRenamePresetDialog = false },
            containerColor = theme.barBackgroundColor,
            titleContentColor = theme.iconColor,
            textContentColor = theme.iconColor,
            title = { Text("Renombrar Esquema") },
            text = {
                OutlinedTextField(
                    value = newPresetName,
                    onValueChange = { newPresetName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f),
                        cursorColor = theme.highlightColor
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPresetName.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.renameWorkspaceProfile(presetToRename!!, trimmed)
                            showRenamePresetDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor, contentColor = Color.White)
                ) {
                    Text("Renombrar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePresetDialog = false }) {
                    Text("Cancelar", color = theme.iconColor.copy(alpha = 0.8f))
                }
            }
        )
    }
}

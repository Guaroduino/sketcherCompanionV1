package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.dto.CustomTool
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry
import com.sketcher.sketchercompanionv1.ui.components.ToolIcon
import com.sketcher.sketchercompanionv1.SketcherViewModel
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomToolsManagerDialog(
    viewModel: SketcherViewModel,
    theme: UiThemeConfig,
    onDismiss: () -> Unit
) {
    val customTools by viewModel.customTools.collectAsState()
    val context = LocalContext.current
    var showIconEditorForToolId by remember { mutableStateOf<String?>(null) }
    var showRenameDialogForTool by remember { mutableStateOf<CustomTool?>(null) }

    var showCreateBrushDialog by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
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
                    .fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Taller de Pinceles",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = theme.highlightColor
                    )
                    Row {
                        IconButton(onClick = { showCreateBrushDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Crear Pincel", tint = theme.highlightColor)
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                if (customTools.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No tienes herramientas personalizadas creadas.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.iconColor.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { viewModel.toolManager.restoreDefaultBrushes() },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)
                            ) {
                                Text("Restaurar Pinceles Base", color = theme.barBackgroundColor)
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(customTools) { tool ->
                            val studioTool = remember(tool) {
                                val composeIcon = ToolRegistry.getIconByName(tool.iconName)
                                val resId = tool.iconResName?.let {
                                    context.resources.getIdentifier(it, "drawable", context.packageName)
                                } ?: 0
                                StudioTool(
                                    id = tool.id,
                                    icon = composeIcon,
                                    contentDescription = tool.name,
                                    iconResId = if (resId != 0) resId else null,
                                    isPlaceholder = false,
                                    registryId = tool.id,
                                    parentGroupId = null
                                )
                            }

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.activateCustomTool(tool)
                                        onDismiss()
                                    },
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = theme.buttonColor.copy(alpha = 0.1f),
                                    contentColor = theme.iconColor
                                ),
                                border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.05f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(theme.buttonColor.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ToolIcon(
                                                tool = studioTool,
                                                theme = theme,
                                                tint = theme.iconColor,
                                                iconSize = 24.dp
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = tool.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = "Base: ${tool.baseToolType.name}",
                                                fontSize = 11.sp,
                                                color = theme.iconColor.copy(alpha = 0.6f)
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                showRenameDialogForTool = tool
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "Renombrar",
                                                tint = theme.iconColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val newId = "custom_tool_" + UUID.randomUUID().toString()
                                                val duplicated = tool.copy(id = newId, name = "${tool.name} Copia")
                                                viewModel.addCustomTool(duplicated)
                                                val currentTheme = viewModel.themeConfig.value
                                                val existingIcon = currentTheme.customIcons[tool.id]
                                                if (existingIcon != null) {
                                                    val updatedIcons = currentTheme.customIcons.toMutableMap().apply {
                                                        put(newId, existingIcon)
                                                    }
                                                    viewModel.updateTheme(currentTheme.copy(customIcons = updatedIcons))
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.ContentCopy,
                                                contentDescription = "Copiar",
                                                tint = theme.iconColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.activateCustomTool(tool)
                                                if (!viewModel.showPropertiesPanel) {
                                                    viewModel.togglePropertiesPanel()
                                                }
                                                onDismiss()
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tune,
                                                contentDescription = "Ajustes del Pincel",
                                                tint = theme.iconColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                showIconEditorForToolId = tool.id
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Palette,
                                                contentDescription = "Editar Icono",
                                                tint = theme.iconColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                viewModel.removeCustomTool(tool.id)
                                                val currentTheme = viewModel.themeConfig.value
                                                if (currentTheme.customIcons.containsKey(tool.id)) {
                                                    val updatedIcons = currentTheme.customIcons.toMutableMap().apply {
                                                        remove(tool.id)
                                                    }
                                                    viewModel.updateTheme(currentTheme.copy(customIcons = updatedIcons))
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Borrar",
                                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showIconEditorForToolId != null) {
        val toolId = showIconEditorForToolId!!
        val currentIconJson = theme.customIcons[toolId]
        IconEditorDialog(
            viewModel = viewModel,
            onDismiss = { showIconEditorForToolId = null },
            initialIconJson = currentIconJson,
            onSaveIconJson = { jsonStr ->
                val currentTheme = viewModel.themeConfig.value
                val updatedIcons = currentTheme.customIcons.toMutableMap().apply {
                    put(toolId, jsonStr)
                }
                viewModel.updateTheme(currentTheme.copy(customIcons = updatedIcons))
                showIconEditorForToolId = null
            }
        )
    }

    if (showCreateBrushDialog) {
        CreateBrushDialog(
            viewModel = viewModel,
            theme = theme,
            onDismiss = { showCreateBrushDialog = false },
            onBrushCreated = { newBrush ->
                viewModel.addCustomTool(newBrush)
                showCreateBrushDialog = false
            }
        )
    }

    if (showRenameDialogForTool != null) {
        val tool = showRenameDialogForTool!!
        var newName by remember(tool) { mutableStateOf(tool.name) }
        Dialog(onDismissRequest = { showRenameDialogForTool = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                    contentColor = theme.iconColor
                ),
                border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
                modifier = Modifier.padding(16.dp).fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Renombrar Pincel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.highlightColor
                    )
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = theme.highlightColor,
                            focusedTextColor = theme.iconColor,
                            unfocusedTextColor = theme.iconColor,
                            cursorColor = theme.highlightColor
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { showRenameDialogForTool = null }) {
                            Text("Cancelar", color = theme.iconColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (newName.isNotBlank()) {
                                    val updated = tool.copy(name = newName)
                                    viewModel.updateCustomTool(updated)
                                    showRenameDialogForTool = null
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor),
                            enabled = newName.isNotBlank()
                        ) {
                            Text("Guardar", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

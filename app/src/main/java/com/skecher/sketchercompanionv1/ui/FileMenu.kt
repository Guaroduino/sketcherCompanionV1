package com.skecher.sketchercompanionv1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skecher.sketchercompanionv1.R
import com.skecher.sketchercompanionv1.utils.TemplateManager
import java.io.File

@Composable
fun FileMenu(
    onNewDrawing: () -> Unit,
    onSave: () -> Unit,
    onLoad: () -> Unit,
    onImportImage: () -> Unit,
    onExportSvg: () -> Unit,
    onExportPng: () -> Unit,
    onSettingsClick: () -> Unit,
    onSaveTemplate: (String) -> Unit,
    onLoadTemplate: (File) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }
    var showLoadTemplateDialog by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(Icons.Default.Menu, contentDescription = "Project")
        }

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            // STANDARD ACTIONS
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_new)) },
                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                onClick = { onNewDrawing(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_save)) },
                leadingIcon = { Icon(Icons.Default.Save, null) },
                onClick = { onSave(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_load)) },
                leadingIcon = { Icon(Icons.Default.FolderOpen, null) },
                onClick = { onLoad(); showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("Import Image") }, // No resource yet? Using literal from original code
                leadingIcon = { Icon(Icons.Default.Image, null) },
                onClick = { onImportImage(); showMenu = false }
            )
            
            HorizontalDivider()
            
            // TEMPLATES SECTION
            // Label?
            DropdownMenuItem(
                text = { 
                    Text(
                        stringResource(R.string.section_templates), 
                        style = MaterialTheme.typography.labelSmall, 
                        color = Color.Gray 
                    ) 
                },
                onClick = { },
                enabled = false
            )
            
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_save_template)) },
                leadingIcon = { Icon(Icons.Default.Save, null) },
                onClick = { 
                    showSaveTemplateDialog = true
                    showMenu = false 
                }
            )
            
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_new_from_template)) },
                leadingIcon = { Icon(Icons.Default.Description, null) }, // Description icon sort of looks like a file/template
                onClick = { 
                    showLoadTemplateDialog = true
                    showMenu = false 
                }
            )

            HorizontalDivider()
            
            // EXPORT SECTION
            DropdownMenuItem(
                text = { 
                    Text(
                        "Exportar", 
                        style = MaterialTheme.typography.labelSmall, 
                        color = Color.Gray 
                    ) 
                },
                onClick = { },
                enabled = false
            )

            DropdownMenuItem(
                text = { Text("Exportar como PNG") },
                leadingIcon = { Icon(Icons.Default.Share, null) },
                onClick = { onExportPng(); showMenu = false }
            )

            DropdownMenuItem(
                text = { Text("Exportar como SVG") },
                leadingIcon = { Icon(Icons.Default.Share, null) },
                onClick = { onExportSvg(); showMenu = false }
            )

            HorizontalDivider()

            DropdownMenuItem(
                text = { Text(stringResource(R.string.settings_title)) },
                leadingIcon = { Icon(Icons.Default.MoreVert, null) },
                onClick = { onSettingsClick(); showMenu = false }
            )
        }
    }

    if (showSaveTemplateDialog) {
        SaveTemplateDialog(
            onDismiss = { showSaveTemplateDialog = false },
            onSave = { name ->
                onSaveTemplate(name)
                showSaveTemplateDialog = false
            }
        )
    }

    if (showLoadTemplateDialog) {
        LoadTemplateDialog(
            onDismiss = { showLoadTemplateDialog = false },
            onTemplateSelected = { file ->
                onLoadTemplate(file)
                showLoadTemplateDialog = false
            }
        )
    }
}

@Composable
fun SaveTemplateDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_template_name_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.dialog_hint_template_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onSave(text) },
                enabled = text.isNotBlank()
            ) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun LoadTemplateDialog(
    onDismiss: () -> Unit,
    onTemplateSelected: (File) -> Unit
) {
    val context = LocalContext.current
    var templates by remember { mutableStateOf<List<File>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        templates = TemplateManager.getAvailableTemplates(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dialog_select_template_title)) },
        text = {
            if (templates.isEmpty()) {
                Text("No templates found.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp) // Fixed height for list
                ) {
                    items(templates) { file ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTemplateSelected(file) }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(16.dp))
                            // Remove extension for display
                            Text(file.nameWithoutExtension, style = MaterialTheme.typography.bodyLarge)
                        }
                        Divider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

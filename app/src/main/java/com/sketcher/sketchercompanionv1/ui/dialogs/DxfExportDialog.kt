package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sketcher.sketchercompanionv1.R

@Composable
fun DxfExportDialog(
    onDismiss: () -> Unit,
    onExport: (String, Boolean) -> Unit // Filename (no ext), Export Selection Only
) {
    var filename by remember { mutableStateOf("drawing") }
    var exportSelectionOnly by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar a DXF") },
        text = {
            Column {
                OutlinedTextField(
                    value = filename,
                    onValueChange = { filename = it },
                    label = { Text("Nombre del archivo") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = exportSelectionOnly,
                        onCheckedChange = { exportSelectionOnly = it }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Solo selecciÃ³n")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (filename.isNotBlank()) {
                        onExport(filename, exportSelectionOnly)
                    }
                },
                enabled = filename.isNotBlank()
            ) {
                Text("Exportar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}


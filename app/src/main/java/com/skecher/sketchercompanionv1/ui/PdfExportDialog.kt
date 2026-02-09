package com.skecher.sketchercompanionv1.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

/**
 * Dialog for selecting PDF export bounds when canvas size is not configured
 */
@Composable
fun PdfExportDialog(
    onDismiss: () -> Unit,
    onConfirm: (useZoomExtends: Boolean) -> Unit
) {
    var selectedOption by remember { mutableStateOf(true) } // true = Zoom Extends, false = Home View

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .width(320.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title
                Text(
                    text = "Seleccionar Área de Exportación",
                    style = MaterialTheme.typography.titleLarge
                )

                Text(
                    text = "Elige qué área del lienzo exportar a PDF:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.Gray
                )

                Divider()

                // Option 1: Zoom Extends
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedOption,
                            onClick = { selectedOption = true }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedOption,
                        onClick = { selectedOption = true }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Vista Zoom Extends",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Ajustar a todo el contenido",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                // Option 2: Home View
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = !selectedOption,
                            onClick = { selectedOption = false }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = !selectedOption,
                        onClick = { selectedOption = false }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Vista de Casa",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Usar la vista guardada como inicio",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray
                        )
                    }
                }

                Divider()

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onConfirm(selectedOption) }
                    ) {
                        Text("Exportar")
                    }
                }
            }
        }
    }
}

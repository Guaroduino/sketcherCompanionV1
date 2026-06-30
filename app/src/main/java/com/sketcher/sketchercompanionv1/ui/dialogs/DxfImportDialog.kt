package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import com.sketcher.sketchercompanionv1.ui.SettingSlider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.R
import com.sketcher.sketchercompanionv1.importers.DxfImportData
import com.sketcher.sketchercompanionv1.importers.DxfImporter
import com.sketcher.sketchercompanionv1.VectorStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.min

@Composable
fun DxfImportDialog(
    uri: android.net.Uri,
    onDismiss: () -> Unit,
    onImport: (DxfImportData, Boolean, Float, Boolean, com.sketcher.sketchercompanionv1.dto.DistanceUnit) -> Unit // Data, scaleToFit, strokeWidth, fillClosedShapes, unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var importData by remember { mutableStateOf<DxfImportData?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var scaleToFit by remember { mutableStateOf(true) }
    var strokeWidth by remember { mutableStateOf(5f) }
    var fillClosedShapes by remember { mutableStateOf(false) }
    var selectedUnit by remember { mutableStateOf(com.sketcher.sketchercompanionv1.dto.DistanceUnit.MM) }
    var expandedUnitDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val data = DxfImporter.parse(inputStream)
                    withContext(Dispatchers.Main) {
                        importData = data
                        isLoading = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Importar DXF") },
        text = {
            Column {
                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val data = importData
                    if (data != null) {
                        // Info
                        Text("Size: ${String.format("%.2f", data.totalBounds.width())} x ${String.format("%.2f", data.totalBounds.height())} units")
                        Spacer(modifier = Modifier.height(8.dp))

                        // Preview
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White)
                                .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp))
                        ) {
                            Canvas(modifier = Modifier.matchParentSize()) {
                                if (data.paths.isNotEmpty()) {
                                    val bounds = data.totalBounds
                                    val pathWidth = bounds.width()
                                    val pathHeight = bounds.height()
                                    
                                    if (pathWidth > 0 && pathHeight > 0) {
                                        // Calculate scale to fit canvas
                                        val canvasWidth = size.width
                                        val canvasHeight = size.height
                                        
                                        val scaleX = canvasWidth / pathWidth
                                        val scaleY = canvasHeight / pathHeight
                                        val scale = min(scaleX, scaleY) * 0.9f // 90% fill

                                        // Center alignment
                                        val scaledWidth = pathWidth * scale
                                        val scaledHeight = pathHeight * scale
                                        val offsetX = (canvasWidth - scaledWidth) / 2f - bounds.left * scale
                                        val offsetY = (canvasHeight - scaledHeight) / 2f - bounds.top * scale

                                        translate(left = offsetX, top = offsetY) {
                                            scale(scale, pivot = Offset.Zero) {
                                                 data.paths.forEach { dp ->
                                                     val previewColor = if (dp.color != null) Color(dp.color) else Color.Black
                                                     
                                                     // If fillClosedShapes is TRUE and dp.isClosed is TRUE, use Fill style
                                                     // But for preview, let's keep it simple or reflect accurate preview?
                                                     // Reflect preview!
                                                     val style = if (fillClosedShapes && dp.isClosed) {
                                                         androidx.compose.ui.graphics.drawscope.Fill
                                                     } else {
                                                         Stroke(width = strokeWidth / scale)
                                                     }
                                                     
                                                     drawPath(
                                                         path = androidx.compose.ui.graphics.Path().apply {
                                                             addPath(dp.path.asComposePath())
                                                         },
                                                         color = previewColor,
                                                         style = style
                                                     )
                                                 }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Options
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = scaleToFit,
                                onCheckedChange = { scaleToFit = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Escalar al lienzo (Fit to Canvas)")
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = fillClosedShapes,
                                onCheckedChange = { fillClosedShapes = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Rellenar formas cerradas (Fill closed shapes)")
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Unidad del DXF original:")
                        Box {
                            TextButton(onClick = { expandedUnitDropdown = true }) {
                                Text(selectedUnit.name)
                            }
                            androidx.compose.material3.DropdownMenu(
                                expanded = expandedUnitDropdown,
                                onDismissRequest = { expandedUnitDropdown = false }
                            ) {
                                com.sketcher.sketchercompanionv1.dto.DistanceUnit.entries.forEach { unit ->
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(unit.name) },
                                        onClick = {
                                            selectedUnit = unit
                                            expandedUnitDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        SettingSlider(
                            label = "Grosor de línea base: ${strokeWidth.toInt()}px (Default)",
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 1f..50f,
                            steps = 49
                        )
                    } else {
                         Text("Error al leer el archivo.")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                     importData?.let { data ->
                         onImport(data, scaleToFit, strokeWidth, fillClosedShapes, selectedUnit)
                     }
                 },
                 enabled = !isLoading && importData != null
            ) {
                Text("Importar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}


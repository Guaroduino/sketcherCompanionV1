package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.DashboardItem
import com.sketcher.sketchercompanionv1.FolderMetadata
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.dto.FillStyleJson
import com.sketcher.sketchercompanionv1.ui.CustomThemeChip
import com.sketcher.sketchercompanionv1.ui.NotebookCover
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.utils.toFillStyle
import com.sketcher.sketchercompanionv1.utils.toFillStyleJson

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateNotebookDialog(
    initialName: String,
    viewModel: SketcherViewModel,
    theme: UiThemeConfig,
    scaleFactor: Float,
    onDismiss: () -> Unit,
    onConfirm: (name: String, coverStyle: String, coverFill: FillStyleJson?) -> Unit
) {
    var notebookName by remember { mutableStateOf(initialName) }
    var coverStyle by remember { mutableStateOf("classic") }
    var coverFillJson by remember { mutableStateOf<FillStyleJson?>(null) }
    
    var showFillPicker by remember { mutableStateOf(false) }
    val fillPresets by viewModel.fillPresets.collectAsState()

    val configuration = LocalConfiguration.current
    val maxCardHeight = (configuration.screenHeightDp * 0.9f).dp

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(320.dp * scaleFactor)
                .heightIn(max = maxCardHeight),
            shape = RoundedCornerShape(16.dp * scaleFactor),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                contentColor = theme.iconColor
            ),
            border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp * scaleFactor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp * scaleFactor),
                verticalArrangement = Arrangement.spacedBy(12.dp * scaleFactor)
            ) {
                Text(
                    text = "NUEVO CUADERNO",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.iconColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )

                OutlinedTextField(
                    value = notebookName,
                    onValueChange = { notebookName = it },
                    label = { Text("Nombre del cuaderno", color = theme.iconColor.copy(alpha = 0.7f), fontSize = 12.sp * scaleFactor) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                // Live Preview of notebook cover
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(100.dp * scaleFactor)
                        .height(140.dp * scaleFactor)
                        .background(Color(0xFFF9F9F9))
                        .border(1.dp, theme.iconColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp * scaleFactor)),
                    contentAlignment = Alignment.Center
                ) {
                    val dummyFolder = remember(notebookName, coverStyle, coverFillJson) {
                        DashboardItem.Folder(
                            name = notebookName,
                            path = "",
                            lastModified = 0L,
                            itemCount = 0,
                            metadata = FolderMetadata(
                                coverStyle = coverStyle,
                                coverFill = coverFillJson,
                                coverProject = null
                            )
                        )
                    }
                    NotebookCover(
                        folder = dummyFolder,
                        thumbnailCache = emptyMap(),
                        scaleFactor = scaleFactor,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp * scaleFactor)) {
                    Text("Estilo de Portada:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp * scaleFactor, color = theme.iconColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp * scaleFactor)
                    ) {
                        val styles = listOf(
                            Triple("classic", "Clásico", Icons.Default.Book),
                            Triple("spiral", "Espiral", Icons.Default.MenuBook),
                            Triple("minimalist", "Minimalista", Icons.Default.ImportContacts)
                        )
                        styles.forEach { (styleKey, label, icon) ->
                            CustomThemeChip(
                                selected = coverStyle == styleKey,
                                label = label,
                                icon = icon,
                                theme = theme,
                                scaleFactor = scaleFactor,
                                onClick = { coverStyle = styleKey }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp * scaleFactor)) {
                    Text("Relleno y Color de Portada:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp * scaleFactor, color = theme.iconColor)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp * scaleFactor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp * scaleFactor)
                                .clip(RoundedCornerShape(4.dp * scaleFactor))
                                .border(1.dp, theme.iconColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp * scaleFactor))
                        ) {
                            val fillStyle = remember(coverFillJson) {
                                coverFillJson.toFillStyle(android.graphics.Color.LTGRAY)
                            }
                            val renderEngine = remember { com.sketcher.sketchercompanionv1.RenderEngine() }
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                drawIntoCanvas { canvas ->
                                    val paint = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        style = android.graphics.Paint.Style.FILL
                                    }
                                    renderEngine.applyFillStyle(paint, fillStyle)
                                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                                }
                            }
                        }

                        Button(
                            onClick = { showFillPicker = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.highlightColor,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp * scaleFactor, vertical = 6.dp * scaleFactor),
                            modifier = Modifier.height(32.dp * scaleFactor)
                        ) {
                            Text("Cambiar Relleno", fontSize = 11.sp * scaleFactor)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp * scaleFactor))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = theme.iconColor.copy(alpha = 0.7f), fontSize = 14.sp * scaleFactor)
                    }
                    Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                    Button(
                        onClick = {
                            if (notebookName.isNotBlank()) {
                                onConfirm(notebookName.trim(), coverStyle, coverFillJson)
                            }
                        },
                        enabled = notebookName.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.highlightColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Crear", fontSize = 14.sp * scaleFactor)
                    }
                }
            }
        }
    }

    if (showFillPicker) {
        val initialFillStyle = remember(coverFillJson) {
            coverFillJson.toFillStyle(android.graphics.Color.LTGRAY)
        }
        com.sketcher.sketchercompanionv1.ui.FillStylePickerDialog(
            initialStyle = initialFillStyle,
            theme = theme,
            presets = fillPresets,
            onPresetOverwritten = { index, style ->
                viewModel.saveFillPreset(index, style)
            },
            onDismiss = { showFillPicker = false },
            onStyleSelected = { style ->
                coverFillJson = style.toFillStyleJson()
                showFillPicker = false
            },
            basePixelsPerMillimeter = viewModel.scaleConfig.basePixelsPerMillimeter
        )
    }
}

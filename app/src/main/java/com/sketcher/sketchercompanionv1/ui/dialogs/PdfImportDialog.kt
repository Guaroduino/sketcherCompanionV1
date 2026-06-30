package com.sketcher.sketchercompanionv1.ui.dialogs

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.sketcher.sketchercompanionv1.ui.AppIconButton
import com.sketcher.sketchercompanionv1.ui.SettingSlider

@Composable
fun PdfImportDialog(
    uri: android.net.Uri,
    fileName: String,
    onDismiss: () -> Unit,
    onImport: (Bitmap, Int, Int) -> Unit // Bitmap, PageIndex (1-based), DPI
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pageCount by remember { mutableStateOf(0) }
    var currentPage by remember { mutableStateOf(1) } // 1-based index
    var dpi by remember { mutableStateOf(150) } // Default 150 DPI

    var originalPageWidthPoints by remember { mutableStateOf(0f) }
    var originalPageHeightPoints by remember { mutableStateOf(0f) }

    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(true) }
    var isImporting by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Load preview bitmap on page or URI change
    LaunchedEffect(uri, currentPage) {
        isLoadingPreview = true
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd != null) {
                    val renderer = PdfRenderer(pfd)
                    pageCount = renderer.pageCount
                    
                    val pageIdx = (currentPage - 1).coerceIn(0, pageCount - 1)
                    val page = renderer.openPage(pageIdx)
                    
                    originalPageWidthPoints = page.width.toFloat()
                    originalPageHeightPoints = page.height.toFloat()

                    // Render preview at low-res 72 DPI (1 point = 1 pixel)
                    val previewDpi = 72
                    val scale = previewDpi / 72.0f
                    val w = (page.width * scale).toInt().coerceAtLeast(1)
                    val h = (page.height * scale).toInt().coerceAtLeast(1)

                    val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(AndroidColor.WHITE) // Background is transparent by default in PDF renderer

                    val matrix = Matrix()
                    matrix.postScale(scale, scale)
                    page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    page.close()
                    renderer.close()
                    pfd.close()

                    withContext(Dispatchers.Main) {
                        previewBitmap = bitmap
                        isLoadingPreview = false
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        errorMsg = "No se pudo abrir el archivo"
                        isLoadingPreview = false
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    errorMsg = "Error: ${e.localizedMessage}"
                    isLoadingPreview = false
                }
            }
        }
    }

    // Calculate dimensions at selected DPI
    val targetWidthPx = (originalPageWidthPoints * (dpi / 72.0f)).toInt()
    val targetHeightPx = (originalPageHeightPoints * (dpi / 72.0f)).toInt()

    AlertDialog(
        onDismissRequest = { if (!isImporting) onDismiss() },
        title = {
            Column {
                Text("Importar PDF", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = fileName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isImporting) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Renderizando página $currentPage a $dpi DPI...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Tamaño: $targetWidthPx x $targetHeightPx px",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    // Preview box
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White)
                            .border(1.dp, Color.LightGray, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } else if (isLoadingPreview) {
                            CircularProgressIndicator()
                        } else {
                            Text(
                                errorMsg ?: "Error cargando vista previa",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    // Page Navigation
                    if (pageCount > 0) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIconButton(
                                onClick = { if (currentPage > 1) currentPage-- },
                                enabled = currentPage > 1,
                                icon = Icons.Default.NavigateBefore,
                                contentDescription = "Página anterior"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Página $currentPage de $pageCount",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            AppIconButton(
                                onClick = { if (currentPage < pageCount) currentPage++ },
                                enabled = currentPage < pageCount,
                                icon = Icons.Default.NavigateNext,
                                contentDescription = "Página siguiente"
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // DPI Quick select buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(72 to "72 (Bajo)", 150 to "150 (Medio)", 300 to "300 (Alto)").forEach { (valDpi, label) ->
                            if (dpi == valDpi) {
                                Button(
                                    onClick = { dpi = valDpi },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text(label, fontSize = 11.sp)
                                }
                            } else {
                                OutlinedButton(
                                    onClick = { dpi = valDpi },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text(label, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    // DPI Slider using SettingSlider
                    SettingSlider(
                        label = "Resolución (DPI):",
                        value = dpi.toFloat(),
                        onValueChange = { dpi = it.toInt() },
                        valueRange = 50f..400f,
                        steps = 35,
                        showValueOnRight = true,
                        valueFormatter = { "${it.toInt()} DPI" }
                    )

                    // Output image stats info
                    if (originalPageWidthPoints > 0) {
                        Text(
                            text = "Tamaño final: $targetWidthPx x $targetHeightPx píxeles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    isImporting = true
                    scope.launch(Dispatchers.IO) {
                        try {
                            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            if (pfd != null) {
                                val renderer = PdfRenderer(pfd)
                                val pageIdx = (currentPage - 1).coerceIn(0, renderer.pageCount - 1)
                                val page = renderer.openPage(pageIdx)

                                val scale = dpi / 72.0f
                                val w = (page.width * scale).toInt().coerceAtLeast(1)
                                val h = (page.height * scale).toInt().coerceAtLeast(1)

                                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(AndroidColor.WHITE)

                                val matrix = Matrix()
                                matrix.postScale(scale, scale)
                                page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                                page.close()
                                renderer.close()
                                pfd.close()

                                withContext(Dispatchers.Main) {
                                    isImporting = false
                                    onImport(bitmap, currentPage, dpi)
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isImporting = false
                                errorMsg = "Error al renderizar: ${e.localizedMessage}"
                            }
                        }
                    }
                },
                enabled = !isLoadingPreview && previewBitmap != null && !isImporting
            ) {
                Text("Importar")
            }
        },
        dismissButton = {
            if (!isImporting) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    )
}

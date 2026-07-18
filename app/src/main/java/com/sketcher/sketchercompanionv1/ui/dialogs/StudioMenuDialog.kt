package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.model.ProjectActions
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.AnimatedVisibility
import com.sketcher.sketchercompanionv1.ui.SettingSlider
import com.sketcher.sketchercompanionv1.ui.components.ColorPreviewRow
import com.sketcher.sketchercompanionv1.ui.components.ColorPickerDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.LaunchedEffect

@Composable
fun StudioMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: SketcherViewModel,
    actions: ProjectActions
) {
    val theme by viewModel.themeConfig.collectAsState()
    val scaler = LocalUiScaler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxMenuHeight = (configuration.screenHeightDp * 0.8f).dp

    var isImportExpanded by remember { mutableStateOf(false) }
    var isExportExpanded by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(260.sdp)
            .heightIn(max = maxMenuHeight)
            .background(theme.barBackgroundColor)
            .border(BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.15f)), RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Text(
                text = "MENÚ",
                style = MaterialTheme.typography.labelLarge,
                color = theme.iconColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 12.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MenuSectionHeader("PROYECTO", theme.iconColor)
                MenuItem(Icons.Default.Home, "Volver al Inicio", theme.iconColor) { 
                    viewModel.exitEditorToDashboard(context)
                    onDismiss()
                }
                MenuItem(Icons.Default.Refresh, "Nuevo Dibujo", theme.iconColor) { 
                    actions.onNew()
                    onDismiss()
                }
                MenuItem(Icons.Default.Save, "Guardar Proyecto", theme.iconColor) { 
                    actions.onSave()
                    onDismiss()
                }
                MenuItem(Icons.Default.SaveAs, "Guardar Proyecto Como...", theme.iconColor) { 
                    actions.onSaveAs()
                    onDismiss()
                }
                MenuItem(Icons.Default.FolderOpen, "Cargar Proyecto", theme.iconColor) { 
                    actions.onLoad()
                    onDismiss()
                }

                Spacer(modifier = Modifier.height(8.dp))
                ExpandableMenuItem(
                    icon = Icons.Default.FolderOpen,
                    label = "Importar...",
                    tint = theme.iconColor,
                    expanded = isImportExpanded,
                    onToggle = { isImportExpanded = !isImportExpanded }
                )
                if (isImportExpanded) {
                    SubMenuItem("Importar Imagen", theme.iconColor) {
                        actions.onImportImage()
                        onDismiss()
                    }
                    SubMenuItem("Importar SVG", theme.iconColor) {
                        actions.onImportSvg()
                        onDismiss()
                    }
                    SubMenuItem("Importar DXF (CAD)", theme.iconColor) {
                        actions.onImportDxf()
                        onDismiss()
                    }
                    SubMenuItem("Importar PDF", theme.iconColor) {
                        actions.onImportPdf()
                        onDismiss()
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                ExpandableMenuItem(
                    icon = Icons.Default.Share,
                    label = "Exportar...",
                    tint = theme.iconColor,
                    expanded = isExportExpanded,
                    onToggle = { isExportExpanded = !isExportExpanded }
                )
                if (isExportExpanded) {
                    SubMenuItem("Exportar PNG", theme.iconColor) {
                        actions.onExportPng()
                        onDismiss()
                    }
                    SubMenuItem("Exportar SVG", theme.iconColor) {
                        actions.onExportSvg()
                        onDismiss()
                    }
                    SubMenuItem("Exportar PDF", theme.iconColor) {
                        actions.onExportPdf()
                        onDismiss()
                    }
                    SubMenuItem("Exportar DXF", theme.iconColor) {
                        actions.onExportDxf()
                        onDismiss()
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                MenuItem(Icons.Default.Print, "Imprimir...", theme.iconColor) {
                    actions.onPrintTrigger()
                    onDismiss()
                }

                Spacer(modifier = Modifier.height(8.dp))
                MenuItem(Icons.Default.AutoAwesome, "Generar Render (IA)", theme.iconColor) {
                    actions.onRender()
                    onDismiss()
                }

                Spacer(modifier = Modifier.height(8.dp))
                MenuSectionHeader("HERRAMIENTAS", theme.iconColor)
                MenuItem(Icons.Default.Description, "Tamaño de Papel", theme.iconColor) { 
                    actions.onPaperSize()
                    onDismiss()
                }
                MenuItem(Icons.Default.SettingsOverscan, "Escala Global", theme.iconColor) { 
                    actions.onGlobalScale()
                    onDismiss()
                }
                MenuItem(Icons.Default.Style, "Taller de Esquemas de Botones", theme.iconColor) { 
                    viewModel.showWorkspaceWorkshopDialog = true
                    onDismiss()
                }
                MenuItem(Icons.Default.Build, "Taller de Pinceles", theme.iconColor) { 
                    viewModel.showCustomToolsManagerDialog = true
                    onDismiss()
                }
                MenuItem(Icons.Default.Style, "Guardar como Plantilla", theme.iconColor) {
                    actions.onTemplatesSaveTrigger()
                    onDismiss()
                }
                MenuItem(Icons.Default.Description, "Nuevo desde Plantilla", theme.iconColor) {
                    actions.onTemplatesLoadTrigger()
                    onDismiss()
                }

                Spacer(modifier = Modifier.height(8.dp))
                MenuSectionHeader("PROYECCIÓN", theme.iconColor)
                ProjectionControlItem(viewModel, theme, onDismiss)
                Spacer(modifier = Modifier.height(8.dp))
                WirelessProjectionControlItem(viewModel, theme, onDismiss)

                Spacer(modifier = Modifier.height(8.dp))
                MenuSectionHeader("APLICACIÓN", theme.iconColor)
                val showExp = viewModel.showExperimentalTools
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { viewModel.toggleExperimentalTools() }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Science, null, tint = theme.iconColor, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Herramientas Experimentales", color = theme.iconColor, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = showExp,
                        onCheckedChange = { viewModel.toggleExperimentalTools() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.highlightColor,
                            uncheckedThumbColor = theme.iconColor.copy(alpha = 0.5f),
                            uncheckedTrackColor = theme.iconColor.copy(alpha = 0.1f)
                        )
                    )
                }
                val showStats = viewModel.showPerformanceStats
                MenuItem(
                    icon = Icons.Default.Speed,
                    label = if (showStats) "Ocultar Rendimiento" else "Mostrar Rendimiento",
                    tint = theme.iconColor
                ) {
                    viewModel.togglePerformanceStats()
                }
                MenuItem(Icons.Default.Settings, "Configuración", theme.iconColor) { 
                    actions.onSettings()
                    onDismiss()
                }
            }
        }
    }
}

@Composable
fun MenuSectionHeader(title: String, textColor: Color) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        color = textColor.copy(alpha = 0.4f),
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
fun MenuItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 14.sp)
    }
}

@Composable
fun ExpandableMenuItem(
    icon: ImageVector,
    label: String,
    tint: Color,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = tint.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
fun SubMenuItem(
    label: String,
    tint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.4f))
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(label, color = tint, fontSize = 13.sp)
    }
}

@Composable
fun ProjectionControlItem(
    viewModel: SketcherViewModel,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    onDismiss: () -> Unit
) {
    val isProjectionActive = viewModel.isProjectionActive
    val projectionUrl = viewModel.projectionUrl
    val clientCount = viewModel.projectionClientCount
    val isPaused = viewModel.isProjectionPaused
    val mode = viewModel.projectionMode
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    if (!isProjectionActive) {
        MenuItem(
            icon = Icons.Default.Cast,
            label = "Start Live Projection",
            tint = theme.iconColor
        ) {
            viewModel.startProjection()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isPaused) Color(0xFFFF9F0A).copy(alpha = 0.15f) else Color(0xFF2E7D32).copy(alpha = 0.15f))
                .border(1.dp, if (isPaused) Color(0xFFFF9F0A) else Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (isPaused) Color(0xFFFF9F0A) else Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        if (isPaused) "Projection Paused" else "Projection Active",
                        color = if (isPaused) Color(0xFFFF9F0A) else Color(0xFF4CAF50),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    "$clientCount client${if (clientCount != 1) "s" else ""}",
                    color = theme.iconColor.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            // Global Play/Pause and Sync/Fixed toggle controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pause / Play Button
                Button(
                    onClick = { viewModel.toggleProjectionPause() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isPaused) Color(0xFFE65100) else theme.menuButtonColor.copy(alpha = 0.8f),
                        contentColor = theme.iconColor
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Icon(
                        if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = theme.iconColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (isPaused) "Resume" else "Pause", fontSize = 11.sp)
                }

                // Mode Toggle Button (Sync View / Fixed View)
                Button(
                    onClick = {
                        val newMode = if (mode == "sync") "fixed" else "sync"
                        viewModel.updateProjectionMode(newMode)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.menuButtonColor.copy(alpha = 0.8f),
                        contentColor = theme.iconColor
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1.2f).height(32.dp)
                ) {
                    Icon(
                        if (mode == "sync") Icons.Default.Sync else Icons.Default.Fullscreen,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = theme.iconColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(if (mode == "sync") "Sync View" else "Fixed View", fontSize = 11.sp)
                }
            }

            if (mode == "fixed") {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val zoomMode = viewModel.fixedZoomMode
                    val isExtents = zoomMode == "fit"
                    val isHome = zoomMode == "home"

                    Button(
                        onClick = { viewModel.fixedZoomMode = "fit" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isExtents) theme.menuButtonColor else theme.menuButtonColor.copy(alpha = 0.4f),
                            contentColor = theme.iconColor
                        ),
                        border = if (isExtents) BorderStroke(1.dp, theme.iconColor) else null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        if (isExtents) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = theme.iconColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Extents", fontSize = 11.sp)
                    }

                    Button(
                        onClick = { viewModel.fixedZoomMode = "home" },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isHome) theme.menuButtonColor else theme.menuButtonColor.copy(alpha = 0.4f),
                            contentColor = theme.iconColor
                        ),
                        border = if (isHome) BorderStroke(1.dp, theme.iconColor) else null,
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.weight(1f).height(32.dp)
                    ) {
                        if (isHome) {
                            Icon(
                                Icons.Default.Check,
                                null,
                                modifier = Modifier.size(14.dp),
                                tint = theme.iconColor
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text("Home View", fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(theme.iconColor.copy(alpha = 0.05f))
                    .clickable {
                        clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(projectionUrl))
                    }
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    null,
                    tint = theme.iconColor.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    projectionUrl,
                    color = theme.iconColor,
                    fontSize = 12.sp,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = { viewModel.stopProjection() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828),
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(32.dp)
            ) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Projection", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun WirelessProjectionControlItem(
    viewModel: SketcherViewModel,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    onDismiss: () -> Unit
) {
    val isWirelessActive = viewModel.isWirelessProjectionActive
    val context = androidx.compose.ui.platform.LocalContext.current

    if (!isWirelessActive) {
        MenuItem(
            icon = Icons.Default.Tv,
            label = "Proyección Pantalla Inalámbrica",
            tint = theme.iconColor
        ) {
            viewModel.startWirelessProjection()
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF2E7D32).copy(alpha = 0.15f))
                .border(1.dp, Color(0xFF4CAF50), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF4CAF50))
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Proyección Inalámbrica Activa",
                        color = Color(0xFF4CAF50),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Text(
                "Transmite a una pantalla secundaria (TV/Proyector) desde los ajustes de Android.",
                color = theme.iconColor.copy(alpha = 0.8f),
                fontSize = 11.sp
            )
            
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent(android.provider.Settings.ACTION_CAST_SETTINGS)
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = android.content.Intent(android.provider.Settings.ACTION_DISPLAY_SETTINGS)
                                context.startActivity(intent)
                            } catch (ex: Exception) {
                                android.widget.Toast.makeText(context, "No se pudo abrir la configuración", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.menuButtonColor.copy(alpha = 0.8f),
                        contentColor = theme.iconColor
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Icon(
                        Icons.Default.Settings,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = theme.iconColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Configurar", fontSize = 11.sp)
                }

                Button(
                    onClick = { viewModel.stopWirelessProjection() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    modifier = Modifier.weight(1f).height(32.dp)
                ) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp), tint = Color.White)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Detener", fontSize = 11.sp)
                }
            }
        }
    }
}


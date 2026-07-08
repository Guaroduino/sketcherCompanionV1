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
                MenuItem(Icons.Default.Style, "Personalización de UI", theme.iconColor) { 
                    viewModel.setShowPersonalizationDialog(true)
                    onDismiss()
                }
                MenuItem(Icons.Default.Build, "Administrador de Herramientas", theme.iconColor) { 
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

@Composable
fun PersonalizationMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    viewModel: SketcherViewModel,
    swapVertical: Boolean,
    swapHorizontal: Boolean,
    interfaceScale: Float,
    onShowIconEditor: () -> Unit
) {
    if (!expanded) return

    val theme by viewModel.themeConfig.collectAsState()
    val scaler = LocalUiScaler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxMenuHeight = (configuration.screenHeightDp * 0.85f).dp

    var pickingColorFor by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor,
                contentColor = theme.iconColor
            ),
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = maxMenuHeight)
                .border(BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.15f)), RoundedCornerShape(16.dp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Text(
                    text = "Personalización",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.iconColor,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Shape Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Round Shapes", color = theme.iconColor)
                    Switch(
                        checked = theme.isRound,
                        onCheckedChange = { viewModel.updateTheme(theme.copy(isRound = it)) }
                    )
                }

                // Edit Toolbars Switch
                val isEditModeByVM by viewModel.isEditMode.collectAsState()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Edit Toolbars", color = theme.iconColor)
                    Switch(
                        checked = isEditModeByVM,
                        onCheckedChange = { viewModel.toggleEditMode() }
                    )
                }

                // UI Preset Selection & Controls
                val uiPresets by viewModel.toolbarManager.uiPresetsNames.collectAsState()
                val activeUiPreset by viewModel.toolbarManager.activeUiPresetName.collectAsState()
                var expandedPresetDropdown by remember { mutableStateOf(false) }
                var showAddPresetDialog by remember { mutableStateOf(false) }
                var newPresetName by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Esquema de Botones",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.iconColor.copy(alpha = 0.7f)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dropdown selection box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(theme.buttonColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, theme.iconColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable { expandedPresetDropdown = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeUiPreset,
                                    color = theme.iconColor,
                                    fontSize = 14.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = theme.iconColor
                                )
                            }
                            
                            DropdownMenu(
                                expanded = expandedPresetDropdown,
                                onDismissRequest = { expandedPresetDropdown = false },
                                modifier = Modifier.background(theme.barBackgroundColor)
                            ) {
                                uiPresets.forEach { presetName ->
                                    DropdownMenuItem(
                                        text = { Text(presetName, color = theme.iconColor) },
                                        onClick = {
                                            viewModel.toolbarManager.loadUiPreset(presetName)
                                            expandedPresetDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Delete button
                        IconButton(
                            onClick = {
                                viewModel.toolbarManager.deleteUiPreset(activeUiPreset)
                            },
                            enabled = activeUiPreset != "Default",
                            modifier = Modifier
                                .size(36.dp)
                                .border(
                                    1.dp, 
                                    theme.iconColor.copy(alpha = if (activeUiPreset != "Default") 0.2f else 0.05f), 
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Preset",
                                tint = if (activeUiPreset != "Default") MaterialTheme.colorScheme.error else theme.iconColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Save Current UI Layout as Preset Button
                    Button(
                        onClick = {
                            newPresetName = ""
                            showAddPresetDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.buttonColor.copy(alpha = 0.15f),
                            contentColor = theme.iconColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = theme.iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Guardar como Esquema de Botones...", fontSize = 12.sp)
                    }
                }

                // Tool Preset Selection & Controls
                val toolPresets by viewModel.toolManager.toolPresetGroupNames.collectAsState()
                val activeToolPreset by viewModel.toolManager.activeToolPresetGroupName.collectAsState()
                var expandedToolPresetDropdown by remember { mutableStateOf(false) }
                var showAddToolPresetDialog by remember { mutableStateOf(false) }
                var newToolPresetName by remember { mutableStateOf("") }

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Presets de Herramientas",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.iconColor.copy(alpha = 0.7f)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Dropdown selection box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(theme.buttonColor.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .border(1.dp, theme.iconColor.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .clickable { expandedToolPresetDropdown = true }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = activeToolPreset,
                                    color = theme.iconColor,
                                    fontSize = 14.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = theme.iconColor
                                )
                            }
                            
                            DropdownMenu(
                                expanded = expandedToolPresetDropdown,
                                onDismissRequest = { expandedToolPresetDropdown = false },
                                modifier = Modifier.background(theme.barBackgroundColor)
                            ) {
                                toolPresets.forEach { presetName ->
                                    DropdownMenuItem(
                                        text = { Text(presetName, color = theme.iconColor) },
                                        onClick = {
                                            viewModel.toolManager.loadToolPresetGroup(presetName)
                                            expandedToolPresetDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        // Delete button
                        IconButton(
                            onClick = {
                                viewModel.toolManager.deleteToolPresetGroup(activeToolPreset)
                            },
                            enabled = activeToolPreset != "Default",
                            modifier = Modifier
                                .size(36.dp)
                                .border(
                                    1.dp, 
                                    theme.iconColor.copy(alpha = if (activeToolPreset != "Default") 0.2f else 0.05f), 
                                    RoundedCornerShape(8.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Preset",
                                tint = if (activeToolPreset != "Default") MaterialTheme.colorScheme.error else theme.iconColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Save Current Tool Presets as Group Button
                    Button(
                        onClick = {
                            newToolPresetName = ""
                            showAddToolPresetDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.buttonColor.copy(alpha = 0.15f),
                            contentColor = theme.iconColor
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = theme.iconColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Save Current Tool Presets...", fontSize = 12.sp)
                    }
                }

                // Add Tool Preset dialog popup
                if (showAddToolPresetDialog) {
                    AlertDialog(
                        onDismissRequest = { showAddToolPresetDialog = false },
                        title = { Text("Guardar Presets de Herramientas", color = Color.Black) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Introduce un nombre para este Grupo de Presets de Herramientas:", color = Color.Gray, fontSize = 14.sp)
                                OutlinedTextField(
                                    value = newToolPresetName,
                                    onValueChange = { newToolPresetName = it },
                                    placeholder = { Text("ej. Set de Bocetos") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        focusedBorderColor = theme.highlightColor,
                                        unfocusedBorderColor = Color.LightGray
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val trimmed = newToolPresetName.trim()
                                    if (trimmed.isNotEmpty()) {
                                        viewModel.toolManager.saveToolPresetGroup(trimmed)
                                    }
                                    showAddToolPresetDialog = false
                                },
                                enabled = newToolPresetName.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)
                            ) {
                                Text("Guardar", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showAddToolPresetDialog = false }
                            ) {
                                Text("Cancelar", color = Color.DarkGray)
                            }
                        },
                        containerColor = Color.White
                    )
                }

                // Add Preset dialog popup
                if (showAddPresetDialog) {
                    AlertDialog(
                        onDismissRequest = { showAddPresetDialog = false },
                        title = { Text("Guardar Esquema de Botones", color = Color.Black) },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Introduce un nombre para este Esquema de Botones:", color = Color.Gray, fontSize = 14.sp)
                                OutlinedTextField(
                                    value = newPresetName,
                                    onValueChange = { newPresetName = it },
                                    placeholder = { Text("ej. Dibujo Técnico") },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = Color.Black,
                                        unfocusedTextColor = Color.Black,
                                        focusedBorderColor = theme.highlightColor,
                                        unfocusedBorderColor = Color.LightGray
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
                                        viewModel.toolbarManager.saveUiPreset(trimmed)
                                    }
                                    showAddPresetDialog = false
                                },
                                enabled = newPresetName.trim().isNotEmpty(),
                                colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)
                            ) {
                                Text("Guardar", color = Color.White)
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = { showAddPresetDialog = false }
                            ) {
                                Text("Cancelar", color = Color.DarkGray)
                            }
                        },
                        containerColor = Color.White
                    )
                }

                // UI Scale Slider
                var tempScale by remember { mutableStateOf(interfaceScale) }
                SettingSlider(
                    label = "UI Scale",
                    value = tempScale,
                    onValueChange = { tempScale = it },
                    onValueChangeFinished = { viewModel.updateInterfaceScale(tempScale) },
                    valueRange = 0.5f..1.5f,
                    labelStyle = MaterialTheme.typography.labelMedium,
                    labelColor = theme.iconColor,
                    showValueOnRight = true,
                    valueFormatter = { String.format("%.1f", it) + "x" }
                )

                // Button Spacing Slider
                var tempSpacing by remember { mutableStateOf(viewModel.buttonSpacingFactor) }
                LaunchedEffect(viewModel.buttonSpacingFactor) {
                    tempSpacing = viewModel.buttonSpacingFactor
                }
                SettingSlider(
                    label = "Button Spacing",
                    value = tempSpacing,
                    onValueChange = { tempSpacing = it },
                    onValueChangeFinished = { viewModel.updateButtonSpacingFactor(tempSpacing) },
                    valueRange = 0.15f..2.0f,
                    labelStyle = MaterialTheme.typography.labelMedium,
                    labelColor = theme.iconColor,
                    showValueOnRight = true,
                    valueFormatter = { "${(it * 100).toInt()}%" }
                )

                // Color Previews
                ColorPreviewRow(
                    label = "Bar Color",
                    color = theme.barBackgroundColor,
                    labelColor = theme.iconColor,
                    onClick = { pickingColorFor = "bar" }
                )
                ColorPreviewRow(
                    label = "Button Color",
                    color = theme.buttonColor,
                    labelColor = theme.iconColor,
                    onClick = { pickingColorFor = "button" }
                )
                ColorPreviewRow(
                    label = "Icon Color",
                    color = theme.iconColor,
                    labelColor = theme.iconColor,
                    onClick = { pickingColorFor = "icon" }
                )
                ColorPreviewRow(
                    label = "Highlight Color",
                    color = theme.highlightColor,
                    labelColor = theme.iconColor,
                    onClick = { pickingColorFor = "highlight" }
                )
                ColorPreviewRow(
                    label = "Canvas Color",
                    color = theme.canvasColor,
                    labelColor = theme.iconColor,
                    onClick = { pickingColorFor = "canvas" }
                )

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
                        onColorSelected = { newColor ->
                            val newRecents = (listOf(newColor) + theme.recentColors)
                                .distinct()
                                .take(12)
                            when(pickingColorFor) {
                                "bar" -> viewModel.updateTheme(theme.copy(barBackgroundColor = newColor, recentColors = newRecents))
                                "button" -> viewModel.updateTheme(theme.copy(buttonColor = newColor, recentColors = newRecents))
                                "icon" -> viewModel.updateTheme(theme.copy(iconColor = newColor, recentColors = newRecents))
                                "highlight" -> viewModel.updateTheme(theme.copy(highlightColor = newColor, recentColors = newRecents))
                                "canvas" -> viewModel.updateTheme(theme.copy(canvasColor = newColor, recentColors = newRecents))
                            }
                            pickingColorFor = null
                        }
                    )
                }

                // Opacity Slider
                SettingSlider(
                    label = "Bar Opacity",
                    value = theme.barBackgroundColor.alpha,
                    onValueChange = { 
                        viewModel.updateTheme(theme.copy(barBackgroundColor = theme.barBackgroundColor.copy(alpha = it))) 
                    },
                    valueRange = 0f..1f,
                    labelStyle = MaterialTheme.typography.labelMedium,
                    labelColor = theme.iconColor,
                    showValueOnRight = true,
                    valueFormatter = { "${(it * 100).toInt()}%" }
                )

                // Shadows Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Shadows", style = MaterialTheme.typography.labelMedium, color = theme.iconColor)
                    Switch(
                        checked = theme.isShadowEnabled,
                        onCheckedChange = { viewModel.updateTheme(theme.copy(isShadowEnabled = it)) }
                    )
                }

                val canShowShadowOptions = theme.isShadowEnabled && theme.barBackgroundColor.alpha == 1f
                AnimatedVisibility(visible = canShowShadowOptions) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingSlider(
                            label = "Shadow Opacity",
                            value = theme.shadowOpacity,
                            onValueChange = { viewModel.updateTheme(theme.copy(shadowOpacity = it)) },
                            valueRange = 0f..1f,
                            labelStyle = MaterialTheme.typography.labelMedium,
                            labelColor = theme.iconColor,
                            showValueOnRight = true,
                            valueFormatter = { "${(it * 100).toInt()}%" }
                        )

                        SettingSlider(
                            label = "Shadow Blur",
                            value = theme.shadowBlur.value,
                            onValueChange = { viewModel.updateTheme(theme.copy(shadowBlur = it.dp)) },
                            valueRange = 0f..24f,
                            labelStyle = MaterialTheme.typography.labelMedium,
                            labelColor = theme.iconColor,
                            showValueOnRight = true,
                            valueFormatter = { "${it.toInt()} dp" }
                        )

                        SettingSlider(
                            label = "Shadow Angle",
                            value = theme.shadowAngle,
                            onValueChange = { viewModel.updateTheme(theme.copy(shadowAngle = it)) },
                            valueRange = 0f..360f,
                            labelStyle = MaterialTheme.typography.labelMedium,
                            labelColor = theme.iconColor,
                            showValueOnRight = true,
                            valueFormatter = { "${it.toInt()}°" }
                        )
                    }
                }

                if (theme.isShadowEnabled && theme.barBackgroundColor.alpha < 1f) {
                    Text(
                        "Shadows hidden because Opacity < 100%", 
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha=0.7f)
                    )
                }

                // Interface Mirror Box
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(theme.buttonColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Interface Mirror",
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.iconColor.copy(alpha = 0.6f),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Swap Vertical", color = theme.iconColor)
                            Switch(
                                checked = swapVertical,
                                onCheckedChange = { viewModel.toggleSwapVertical() }
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Swap Horizontal", color = theme.iconColor)
                            Switch(
                                checked = swapHorizontal,
                                onCheckedChange = { viewModel.toggleSwapHorizontal() }
                            )
                        }
                    }
                }

                // Edit Button Icons Button
                Button(
                    onClick = onShowIconEditor,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.buttonColor,
                        contentColor = theme.iconColor
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = theme.iconColor,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Edit Button Icons")
                }

                // Close Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.buttonColor,
                        contentColor = theme.iconColor
                    )
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}
}

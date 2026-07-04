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

@Composable
fun StudioMenuDialog(
    viewModel: SketcherViewModel,
    actions: ProjectActions,
    onDismiss: () -> Unit
) {
    val theme by viewModel.themeConfig.collectAsState()
    val scaler = LocalUiScaler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxCardHeight = (configuration.screenHeightDp * 0.85f).dp

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(300.sdp)
                .heightIn(max = maxCardHeight),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                contentColor = theme.iconColor
            ),
            border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Text(
                    text = "MENU",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.iconColor.copy(alpha = 0.5f),
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { MenuSectionHeader("PROJECT", theme.iconColor) }
                    item {
                        MenuItem(Icons.Default.Home, "Volver al Inicio", theme.iconColor) { 
                            viewModel.exitEditorToDashboard(context)
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.Refresh, "New Drawing", theme.iconColor) { 
                            actions.onNew()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.Save, "Save Project", theme.iconColor) { 
                            actions.onSave()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.SaveAs, "Save Project As...", theme.iconColor) { 
                            actions.onSaveAs()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.FolderOpen, "Load Project", theme.iconColor) { 
                            actions.onLoad()
                            onDismiss()
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { MenuSectionHeader("IMPORT", theme.iconColor) }
                    item {
                        MenuItem(Icons.Default.Image, "Import Image", theme.iconColor) { 
                            actions.onImportImage()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.Extension, "Import SVG", theme.iconColor) { 
                            actions.onImportSvg()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.Architecture, "Import DXF (CAD)", theme.iconColor) { 
                            actions.onImportDxf()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.PictureAsPdf, "Import PDF", theme.iconColor) { 
                            actions.onImportPdf()
                            onDismiss()
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { MenuSectionHeader("EXPORT", theme.iconColor) }

                    // Legacy has sub-dialogs for export. Let's make them direct items here.
                    item {
                        MenuItem(Icons.Default.Image, "Export PNG", theme.iconColor) { 
                            actions.onExportPng()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.Extension, "Export SVG", theme.iconColor) { 
                            actions.onExportSvg()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.Description, "Export PDF", theme.iconColor) { 
                            actions.onExportPdf()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.Architecture, "Export DXF", theme.iconColor) { 
                            actions.onExportDxf()
                            onDismiss()
                        }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { MenuSectionHeader("TOOLS", theme.iconColor) }
                    item {
                        MenuItem(Icons.Default.Description, "Paper Size", theme.iconColor) { 
                            actions.onPaperSize()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.SettingsOverscan, "Escala Global", theme.iconColor) { 
                            actions.onGlobalScale()
                            onDismiss()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.GridOn, "Grid Settings", theme.iconColor) { 
                            actions.onGridSettings()
                            onDismiss()
                        }
                    }
                    item {
                         MenuItem(Icons.Default.Style, "Save as Template", theme.iconColor) {
                             actions.onTemplatesSaveTrigger()
                             onDismiss()
                         }
                    }
                    item {
                         MenuItem(Icons.Default.Description, "New from Template", theme.iconColor) {
                             actions.onTemplatesLoadTrigger()
                             onDismiss()
                         }
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { MenuSectionHeader("VIEW", theme.iconColor) }
                    item {
                        MenuItem(Icons.Default.AspectRatio, "Zoom to Fit", theme.iconColor) { 
                            actions.onZoomFit()
                            onDismiss()
                        }
                    }
                    
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { MenuSectionHeader("PROJECTION", theme.iconColor) }
                    item {
                        ProjectionControlItem(viewModel, theme, onDismiss)
                    }

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { MenuSectionHeader("APP", theme.iconColor) }
                    item {
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
                    }
                    item {
                        val showStats = viewModel.showPerformanceStats
                        MenuItem(
                            icon = Icons.Default.Speed,
                            label = if (showStats) "Ocultar Rendimiento" else "Mostrar Rendimiento",
                            tint = theme.iconColor
                        ) {
                            viewModel.togglePerformanceStats()
                        }
                    }
                    item {
                        MenuItem(Icons.Default.Settings, "Settings", theme.iconColor) { 
                            actions.onSettings()
                            onDismiss()
                        }
                    }
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

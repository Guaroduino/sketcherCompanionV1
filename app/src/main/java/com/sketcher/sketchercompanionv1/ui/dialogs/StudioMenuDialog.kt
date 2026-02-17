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

@Composable
fun StudioMenuDialog(
    viewModel: SketcherViewModel,
    actions: ProjectActions,
    onDismiss: () -> Unit
) {
    val theme by viewModel.themeConfig.collectAsState()
    val scaler = LocalUiScaler.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(300.sdp)
                .wrapContentHeight(),
            shape = theme.floatingShape(),
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
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item { MenuSectionHeader("PROJECT", theme.iconColor) }
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

                    item { Spacer(modifier = Modifier.height(8.dp)) }
                    item { MenuSectionHeader("EXPORT", theme.iconColor) }
                    item {
                        MenuItem(Icons.Default.Share, "Export PNG / SVG / PDF", theme.iconColor) { 
                             // We'll show a sub-choice or just trigger the flags
                             // For simplicity in one menu, we could separate them or launch a sub-flow.
                             // But let's follow legacy: items for each.
                        }
                    }
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
                    item { MenuSectionHeader("APP", theme.iconColor) }
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

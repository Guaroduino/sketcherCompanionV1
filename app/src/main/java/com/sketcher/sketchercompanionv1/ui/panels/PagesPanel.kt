package com.sketcher.sketchercompanionv1.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.sdp

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PagesPanel(viewModel: SketcherViewModel) {
    val theme by viewModel.themeConfig.collectAsState()
    val pages = viewModel.pages
    val activePageIndex = viewModel.activePageIndex
    val scaler = LocalUiScaler.current

    var renamingIndex by remember { mutableStateOf<Int?>(null) }
    var newName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        // --- HEADER ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(scaler.smallMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                "PÁGINAS",
                color = theme.iconColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            
            // Add Button
            OutlinerActionButton(Icons.Default.Add, "Nueva Página", theme.iconColor, backgroundColor = theme.buttonColor) {
                viewModel.addPage()
            }
        }

        HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))

        // --- LIST ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(scaler.smallMargin),
            verticalArrangement = Arrangement.spacedBy(4.sdp)
        ) {
            itemsIndexed(pages) { index, page ->
                val isActive = index == activePageIndex
                
                PageItem(
                    name = page.name,
                    isActive = isActive,
                    isFirst = index == 0,
                    isLast = index == pages.lastIndex,
                    canDelete = pages.size > 1,
                    theme = theme,
                    onClick = { viewModel.loadPage(index) },
                    onLongClick = {
                        newName = page.name
                        renamingIndex = index
                    },
                    onDelete = { viewModel.removePage(index) },
                    onDuplicate = { viewModel.duplicatePage(index) },
                    onMoveUp = { viewModel.movePageDown(index) },
                    onMoveDown = { viewModel.movePageUp(index) }
                )
            }
        }
    }

    // --- RENAMING DIALOG ---
    if (renamingIndex != null) {
        AlertDialog(
            onDismissRequest = { renamingIndex = null },
            title = { Text("Renombrar Página") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    renamingIndex?.let { viewModel.renamePage(it, newName) }
                    renamingIndex = null
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingIndex = null }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PageItem(
    name: String,
    isActive: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    canDelete: Boolean,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val activeBgColor = theme.highlightColor
    val inactiveBgColor = Color.Transparent
    val currentBgColor = if (isActive) activeBgColor else inactiveBgColor
    val contentColor = theme.iconColor

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.sdp))
            .background(currentBgColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .then(if (!isActive) Modifier.border(1.sdp, theme.iconColor.copy(alpha = 0.1f), RoundedCornerShape(8.sdp)) else Modifier)
            .padding(horizontal = 8.sdp, vertical = 6.sdp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = theme.iconColor.copy(alpha = 0.8f),
                modifier = Modifier.size(16.sdp)
            )
            Spacer(modifier = Modifier.width(8.sdp))
            Text(
                text = name,
                color = theme.iconColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }

        // Actions
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Reorder actions
            OutlinerActionButton(
                icon = Icons.Default.KeyboardArrowUp,
                description = "Subir",
                tint = theme.iconColor,
                enabled = !isFirst,
                backgroundColor = theme.buttonColor,
                onClick = onMoveUp
            )
            OutlinerActionButton(
                icon = Icons.Default.KeyboardArrowDown,
                description = "Bajar",
                tint = theme.iconColor,
                enabled = !isLast,
                backgroundColor = theme.buttonColor,
                onClick = onMoveDown
            )
            // Duplicate
            OutlinerActionButton(
                icon = Icons.Default.ContentCopy,
                description = "Duplicar",
                tint = theme.iconColor,
                backgroundColor = theme.buttonColor,
                onClick = onDuplicate
            )
            // Delete
            OutlinerActionButton(
                icon = Icons.Default.Delete,
                description = "Eliminar",
                tint = if (isActive) contentColor.copy(alpha = 0.8f) else Color(0xFFD32F2F).copy(alpha = 0.6f),
                enabled = canDelete,
                backgroundColor = theme.buttonColor,
                onClick = onDelete
            )
        }
    }
}

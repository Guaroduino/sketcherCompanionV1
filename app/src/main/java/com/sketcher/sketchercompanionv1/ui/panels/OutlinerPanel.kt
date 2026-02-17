package com.sketcher.sketchercompanionv1.ui.panels

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import com.sketcher.sketchercompanionv1.ui.components.BigTouchBox

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun OutlinerPanel(viewModel: SketcherViewModel) {
    val theme by viewModel.themeConfig.collectAsState()
    val layers by viewModel.layers.collectAsState()
    val activeLayerIndex = viewModel.activeLayerIndex
    
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
                "OUTLINER",
                color = theme.iconColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            
            // Simplified global Add button (contextual ones are in Item)
            OutlinerActionButton(Icons.Default.Add, "New Layer", theme.iconColor) {
                viewModel.addLayer()
            }
        }

        HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))

        // --- LIST ---
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(scaler.smallMargin),
            verticalArrangement = Arrangement.spacedBy(ScalerConstants.ITEM_SPACING.dp)
        ) {
            // Reverse order: Top layer first
            itemsIndexed(layers.reversed()) { revIdx, layer ->
                val actualIndex = layers.size - 1 - revIdx
                val isActive = actualIndex == activeLayerIndex

                LayerItem(
                    layer = layer,
                    isActive = isActive,
                    isFirst = actualIndex == layers.size - 1,
                    isLast = actualIndex == 0,
                    theme = theme,
                    scaler = scaler,
                    onClick = { viewModel.setActiveLayer(actualIndex) },
                    onLongClick = { 
                        newName = layer.name
                        renamingIndex = actualIndex 
                    },
                    onToggleVisibility = { viewModel.toggleLayerVisibility(actualIndex) },
                    onDelete = { viewModel.removeLayer(actualIndex) },
                    onOpacityChange = { viewModel.setLayerOpacity(actualIndex, it) },
                    // Reorder
                    onMoveUp = { viewModel.moveLayerUp(actualIndex) },
                    onMoveDown = { viewModel.moveLayerDown(actualIndex) },
                    // Merge
                    onMergeUp = { viewModel.mergeLayerUp(actualIndex) },
                    onMergeDown = { viewModel.mergeLayerDown(actualIndex) },
                    // Add
                    onAddAbove = { viewModel.addLayerAbove(actualIndex) },
                    onAddBelow = { viewModel.addLayerBelow(actualIndex) },
                    // Library
                    onSaveToLibrary = { /* Placeholder */ }
                )
            }
        }
    }

    // --- RENAMING DIALOG ---
    if (renamingIndex != null) {
        AlertDialog(
            onDismissRequest = { renamingIndex = null },
            title = { Text("Rename Layer") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    renamingIndex?.let { viewModel.renameLayer(it, newName) }
                    renamingIndex = null
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { renamingIndex = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun OutlinerActionButton(
    icon: ImageVector, 
    description: String, 
    tint: Color, 
    enabled: Boolean = true,
    backgroundColor: Color = Color.Transparent,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(28.sdp)
            .clip(RoundedCornerShape(4.sdp))
            .background(if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.5f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon, 
            description, 
            tint = if (enabled) tint else tint.copy(alpha = 0.3f), 
            modifier = Modifier.size(16.sdp)
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun LayerItem(
    layer: com.sketcher.sketchercompanionv1.Layer,
    isActive: Boolean,
    isFirst: Boolean,
    isLast: Boolean,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    scaler: com.sketcher.sketchercompanionv1.ui.theme.UiScaler,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleVisibility: () -> Unit,
    onDelete: () -> Unit,
    onOpacityChange: (Float) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMergeUp: () -> Unit,
    onMergeDown: () -> Unit,
    onAddAbove: () -> Unit,
    onAddBelow: () -> Unit,
    onSaveToLibrary: () -> Unit
) {
    // Red theme for active item as per mockup
    val activeBgColor = Color(0xFFD32F2F) // Vibrant Red
    val inactiveBgColor = Color.Transparent
    
    val currentBgColor = if (isActive) activeBgColor else inactiveBgColor
    val contentColor = if (isActive) Color.White else theme.iconColor

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.sdp))
            .background(currentBgColor)
            .then(if (!isActive) Modifier.border(1.sdp, theme.iconColor.copy(alpha = 0.1f), RoundedCornerShape(12.sdp)) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(if (isActive) 10.sdp else 8.sdp)
    ) {
        // TOP ROW: Drag handle, Name, Visibility, Delete
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.DragHandle, 
                null, 
                tint = contentColor.copy(alpha = 0.5f), 
                modifier = Modifier.size(16.sdp)
            )
            
            Spacer(modifier = Modifier.width(8.sdp))
            
            Text(
                layer.name,
                modifier = Modifier.weight(1f),
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(4.sdp)) {
                BigTouchBox(onClick = onToggleVisibility, touchSize = 32.sdp) {
                    Icon(
                        if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        null,
                        tint = contentColor,
                        modifier = Modifier.size(18.sdp)
                    )
                }
                
                BigTouchBox(onClick = onDelete, touchSize = 32.sdp) {
                    Icon(
                        Icons.Default.DeleteOutline,
                        null,
                        tint = if (isActive) Color.White.copy(alpha = 0.8f) else Color.Red.copy(alpha = 0.6f),
                        modifier = Modifier.size(18.sdp)
                    )
                }
            }
        }

        if (isActive) {
            Spacer(modifier = Modifier.height(12.sdp))
            
            // OPACITY ROW
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Opacidad",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 12.sp,
                    modifier = Modifier.width(60.sdp)
                )
                
                Slider(
                    value = layer.opacity,
                    onValueChange = onOpacityChange,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White.copy(alpha = 0.4f),
                        inactiveTrackColor = Color.Black.copy(alpha = 0.2f)
                    )
                )
            }
            
            Spacer(modifier = Modifier.height(8.sdp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
            Spacer(modifier = Modifier.height(8.sdp))
            
            // ACTION BUTTONS ROW (Mirrors mockup)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val btnBg = Color.Black.copy(alpha = 0.25f)
                val iconTint = Color.White
                
                // Reorder
                Row(horizontalArrangement = Arrangement.spacedBy(4.sdp)) {
                    OutlinerCompactAction(Icons.Default.ArrowUpward, !isFirst, btnBg, iconTint, onMoveUp)
                    OutlinerCompactAction(Icons.Default.ArrowDownward, !isLast, btnBg, iconTint, onMoveDown)
                }
                
                Box(modifier = Modifier.width(1.sdp).height(16.sdp).background(Color.White.copy(alpha = 0.15f)))

                // Merge (Placeholder icons for merge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.sdp)) {
                    OutlinerCompactAction(Icons.Default.VerticalAlignTop, !isFirst, btnBg, iconTint, onMergeUp)
                    OutlinerCompactAction(Icons.Default.VerticalAlignBottom, !isLast, btnBg, iconTint, onMergeDown)
                }

                Box(modifier = Modifier.width(1.sdp).height(16.sdp).background(Color.White.copy(alpha = 0.15f)))

                // Add Above/Below
                Row(horizontalArrangement = Arrangement.spacedBy(4.sdp)) {
                    OutlinerCompactAction(Icons.Default.LibraryAdd, true, btnBg, iconTint, onAddAbove) // Should be "Add Above" icon
                    // Using a variation for Add Below
                    OutlinerCompactAction(Icons.Default.PostAdd, true, btnBg, iconTint, onAddBelow)
                }

                Box(modifier = Modifier.width(1.sdp).height(16.sdp).background(Color.White.copy(alpha = 0.15f)))

                // Save to Library
                OutlinerCompactAction(Icons.Default.Collections, true, btnBg, iconTint, onSaveToLibrary)
            }
        }
    }
}

@Composable
fun OutlinerCompactAction(
    icon: ImageVector,
    enabled: Boolean,
    backgroundColor: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(28.sdp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(4.sdp),
        color = if (enabled) backgroundColor else backgroundColor.copy(alpha = 0.1f)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                icon, 
                null, 
                tint = if (enabled) tint else tint.copy(alpha = 0.2f), 
                modifier = Modifier.size(16.sdp)
            )
        }
    }
}

object ScalerConstants {
    const val ITEM_SPACING = 6
}

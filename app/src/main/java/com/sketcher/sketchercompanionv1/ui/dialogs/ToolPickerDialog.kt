package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow

@Composable
fun ToolPickerDialog(
    location: ToolLocation,
    index: Int?, // Null if adding a new tool
    theme: UiThemeConfig,
    onDismiss: () -> Unit,
    onToolSelected: (StudioTool) -> Unit,
    onRemove: (() -> Unit)? = null,
    slotTools: List<StudioTool> = emptyList(),
    onAddSubTool: ((StudioTool) -> Unit)? = null,
    onRemoveSubTool: ((Int) -> Unit)? = null,
    onMoveSubTool: ((Int, Int) -> Unit)? = null,
    onInsertSlotAbove: (() -> Unit)? = null,
    onInsertSlotBelow: (() -> Unit)? = null
) {
    val scaler = com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler.current
    var isAddingAdditional by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .padding(16.sdp)
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                contentColor = theme.iconColor
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.sdp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (index == null) "Add Tool to ${location.name}" else "Configure Slot in ${location.name}",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.iconColor
                )
                
                Spacer(modifier = Modifier.height(16.sdp))

                // --- SLOT CONFIGURATION SECTION ---
                if (index != null && slotTools.isNotEmpty()) {
                    Text(
                        text = "Current tools in this slot:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.iconColor.copy(alpha = 0.8f),
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.sdp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 4.sdp),
                        horizontalArrangement = Arrangement.spacedBy(8.sdp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        slotTools.forEachIndexed { i, tool ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(theme.buttonColor.copy(alpha = 0.3f))
                                    .padding(horizontal = 10.sdp, vertical = 6.sdp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.sdp)
                                ) {
                                    // Move Left Arrow
                                    if (i > 0) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowLeft,
                                            contentDescription = "Move Left",
                                            tint = theme.iconColor.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(scaler.baseIconSize)
                                                .clickable { onMoveSubTool?.invoke(i, i - 1) }
                                        )
                                    }

                                     com.sketcher.sketchercompanionv1.ui.components.ToolIcon(
                                         tool = tool,
                                         theme = theme,
                                         tint = theme.iconColor,
                                         modifier = Modifier.size(scaler.smallIconSize)
                                     )
                                    Text(
                                        text = if (i == 0) "${tool.contentDescription} (Main)" else tool.contentDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = theme.iconColor
                                    )

                                    // Move Right Arrow
                                    if (i < slotTools.size - 1) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowRight,
                                            contentDescription = "Move Right",
                                            tint = theme.iconColor.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .size(scaler.baseIconSize)
                                                .clickable { onMoveSubTool?.invoke(i, i + 1) }
                                        )
                                    }

                                    if (i > 0) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove tool",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier
                                                .size(scaler.smallIconSize)
                                                .clickable { onRemoveSubTool?.invoke(i - 1) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.sdp))
                }

                HorizontalDivider(color = theme.iconColor.copy(alpha = 0.15f))
                Spacer(modifier = Modifier.height(12.sdp))

                // --- HEADER & ADD ADDITIONAL BUTTON ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isAddingAdditional) "Select tool to add to slot:" else "Select main tool:",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.iconColor
                    )
                    
                    if (index != null) {
                        Button(
                            onClick = { isAddingAdditional = !isAddingAdditional },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAddingAdditional) theme.highlightColor else theme.menuButtonColor.copy(alpha = 0.5f),
                                contentColor = theme.iconColor
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.sdp, vertical = 6.sdp)
                        ) {
                            Text(if (isAddingAdditional) "Cancel Add" else "Add Additional")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.sdp))
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(80.sdp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.sdp),
                    horizontalArrangement = Arrangement.spacedBy(8.sdp),
                    verticalArrangement = Arrangement.spacedBy(8.sdp)
                ) {
                    val isContextSlot = location == ToolLocation.ContextBar
                    items(ToolRegistry.allTools.filter { !it.isPlaceholder && it.isContextual == isContextSlot && it.parentGroupId == null }) { tool ->
                        ToolItem(tool = tool, theme = theme, scaler = scaler) {
                            if (isAddingAdditional) {
                                onAddSubTool?.invoke(tool)
                                isAddingAdditional = false
                            } else {
                                onToolSelected(tool)
                                onDismiss()
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.sdp))

                // --- INSERT SLOT ABOVE / BELOW ---
                if (index != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.sdp)
                    ) {
                        Button(
                            onClick = {
                                onInsertSlotAbove?.invoke()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.menuButtonColor.copy(alpha = 0.5f),
                                contentColor = theme.iconColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Insert slot above")
                        }
                        Button(
                            onClick = {
                                onInsertSlotBelow?.invoke()
                                onDismiss()
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.menuButtonColor.copy(alpha = 0.5f),
                                contentColor = theme.iconColor
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Insert slot below")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.sdp))
                }
                
                if (onRemove != null && index != null) {
                    Button(
                        onClick = { 
                            onRemove()
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Remove entire slot from Bar")
                    }
                    Spacer(modifier = Modifier.height(8.sdp))
                }
                
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Cancel", color = theme.iconColor)
                }
            }
        }
    }
}

@Composable
fun ToolItem(
    tool: StudioTool,
    theme: UiThemeConfig,
    scaler: com.sketcher.sketchercompanionv1.ui.theme.UiScaler,
    onClick: () -> Unit
) {
    val backgroundColor = theme.buttonColor.copy(alpha = 0.2f)
    
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.sdp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.sdp)
                .background(backgroundColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            com.sketcher.sketchercompanionv1.ui.components.ToolIcon(
                tool = tool,
                theme = theme,
                tint = theme.iconColor,
                modifier = Modifier.size(scaler.baseIconSize)
            )
        }
        
        Spacer(modifier = Modifier.height(4.sdp))
        
        Text(
            text = tool.contentDescription,
            style = MaterialTheme.typography.labelSmall,
            color = theme.iconColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

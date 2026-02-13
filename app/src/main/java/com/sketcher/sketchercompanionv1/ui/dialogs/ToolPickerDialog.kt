package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
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

@Composable
fun ToolPickerDialog(
    location: ToolLocation,
    index: Int?, // Null if adding a new tool
    theme: UiThemeConfig,
    onDismiss: () -> Unit,
    onToolSelected: (StudioTool) -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (index == null) "Add Tool to ${location.name}" else "Replace Tool in ${location.name}",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.iconColor
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(64.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(ToolRegistry.allTools) { tool ->
                        ToolItem(tool = tool, theme = theme) {
                            onToolSelected(tool)
                            onDismiss()
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
                        Text("Remove from Bar")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
    onClick: () -> Unit
) {
    val backgroundColor = if (tool.isPlaceholder) Color.Red.copy(alpha = 0.3f) else theme.buttonColor.copy(alpha = 0.2f)
    
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(backgroundColor, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = tool.icon,
                contentDescription = tool.contentDescription,
                tint = theme.iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

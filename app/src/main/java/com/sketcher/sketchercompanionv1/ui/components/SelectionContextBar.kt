package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

@Composable
fun SelectionContextBar(
    isVisible: Boolean,
    isTransformMode: Boolean,
    theme: UiThemeConfig,
    onDeselect: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .wrapContentSize()
                .padding(bottom = 16.dp),
            shape = CircleShape,
            color = theme.barBackgroundColor.copy(alpha = 0.85f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Deselect Action
                IconButton(onClick = onDeselect) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Deselect",
                        tint = theme.iconColor
                    )
                }
                
                // Divider
                Box(
                    modifier = Modifier
                        .height(24.dp)
                        .width(1.dp)
                        .background(theme.iconColor.copy(alpha = 0.2f))
                )

                // Delete Action
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = theme.iconColor
                    )
                }

                // Duplicate Action
                IconButton(onClick = onDuplicate) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Duplicate",
                        tint = theme.iconColor
                    )
                }

                // Transform-specific Actions
                if (isTransformMode) {
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .width(1.dp)
                            .background(theme.iconColor.copy(alpha = 0.2f))
                    )
                    
                    IconButton(onClick = onFlipH) {
                        Icon(
                            imageVector = Icons.Default.Flip, // Note: You might need a specific Flip Horizontal icon or rotate this one
                            contentDescription = "Flip Horizontal",
                            tint = theme.iconColor
                        )
                    }
                    
                    IconButton(onClick = onFlipV) {
                        // For Flip Vertical, we can just rotate the flip icon 90 degrees
                        Icon(
                            imageVector = Icons.Default.Flip,
                            contentDescription = "Flip Vertical",
                            tint = theme.iconColor,
                            modifier = Modifier.rotate(90f)
                        )
                    }
                }
            }
        }
    }
}

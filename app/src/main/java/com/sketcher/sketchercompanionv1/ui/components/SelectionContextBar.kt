package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler

@Composable
fun SelectionContextBar(
    isVisible: Boolean,
    isTransformMode: Boolean,
    theme: UiThemeConfig,
    onDeselect: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onCut: () -> Unit,
    onFlipH: () -> Unit,
    onFlipV: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaler = LocalUiScaler.current
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .wrapContentSize()
                .padding(bottom = scaler.margin),
            shape = theme.floatingShape(),
            color = theme.barBackgroundColor.copy(alpha = 0.85f),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = scaler.smallMargin, vertical = scaler.smallMargin / 2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(scaler.smallButtonSpacing)
            ) {
                // Deselect Action
                SketcherIconButton(
                    onClick = onDeselect,
                    icon = Icons.Default.Close,
                    contentDescription = "Deselect",
                    isActive = false,
                    highlightColor = theme.highlightColor,
                    buttonColor = Color.Transparent,
                    iconColor = theme.iconColor,
                    shape = theme.floatingShape(),
                    iconSize = scaler.baseIconSize
                )
                
                // Divider
                Box(
                    modifier = Modifier
                        .height(24.dp * scaler.scaleFactor)
                        .width(1.dp)
                        .background(theme.iconColor.copy(alpha = 0.2f))
                )

                // Delete Action
                SketcherIconButton(
                    onClick = onDelete,
                    icon = Icons.Default.Delete,
                    contentDescription = "Delete",
                    isActive = false,
                    highlightColor = theme.highlightColor,
                    buttonColor = Color.Transparent,
                    iconColor = theme.iconColor,
                    shape = theme.floatingShape(),
                    iconSize = scaler.baseIconSize
                )

                // Copy Action
                SketcherIconButton(
                    onClick = onCopy,
                    icon = Icons.Default.ContentCopy,
                    contentDescription = "Copy",
                    isActive = false,
                    highlightColor = theme.highlightColor,
                    buttonColor = Color.Transparent,
                    iconColor = theme.iconColor,
                    shape = theme.floatingShape(),
                    iconSize = scaler.baseIconSize
                )
                
                // Cut Action
                SketcherIconButton(
                    onClick = onCut,
                    icon = Icons.Default.ContentCut,
                    contentDescription = "Cut",
                    isActive = false,
                    highlightColor = theme.highlightColor,
                    buttonColor = Color.Transparent,
                    iconColor = theme.iconColor,
                    shape = theme.floatingShape(),
                    iconSize = scaler.baseIconSize
                )

                // Transform-specific Actions
                if (isTransformMode) {
                    Box(
                        modifier = Modifier
                            .height(24.dp * scaler.scaleFactor)
                            .width(1.dp)
                            .background(theme.iconColor.copy(alpha = 0.2f))
                    )
                    
                    SketcherIconButton(
                        onClick = onFlipH,
                        icon = Icons.Default.Flip,
                        contentDescription = "Flip Horizontal",
                        isActive = false,
                        highlightColor = theme.highlightColor,
                        buttonColor = Color.Transparent,
                        iconColor = theme.iconColor,
                        shape = theme.floatingShape(),
                        iconSize = scaler.baseIconSize
                    )
                    
                    SketcherIconButton(
                        onClick = onFlipV,
                        icon = Icons.Default.Flip,
                        contentDescription = "Flip Vertical",
                        isActive = false,
                        highlightColor = theme.highlightColor,
                        buttonColor = Color.Transparent,
                        iconColor = theme.iconColor,
                        shape = theme.floatingShape(),
                        iconSize = scaler.baseIconSize,
                        iconModifier = Modifier.rotate(90f)
                    )
                }
            }
        }
    }
}

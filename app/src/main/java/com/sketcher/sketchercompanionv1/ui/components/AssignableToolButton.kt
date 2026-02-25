package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.FormatColorFill
import android.util.Log
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

// Define ToolPayload as requested
enum class ToolPayload(val label: String, val icon: ImageVector) {
    PENCIL("Pencil", Icons.Default.Edit),
    ERASER("Eraser", Icons.Default.AutoFixNormal),
    STROKE_COLOR("Stroke Color", Icons.Default.BorderColor),
    FILL_COLOR("Fill Color", Icons.Default.FormatColorFill)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssignableToolButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    isEditMode: Boolean = false,
    highlightColor: Color,
    buttonColor: Color,
    iconColor: Color,
    shape: Shape = CircleShape,
    iconSize: Dp = 24.dp,
    location: ToolLocation? = null,
    theme: UiThemeConfig? = null,
    colorPreview: Color? = null,
    payload: ToolPayload? = null,
    isSelected: Boolean = false,
    subTools: List<StudioTool> = emptyList(),
    onSubToolClick: ((StudioTool) -> Unit)? = null
) {
    val backgroundColor = if (isActive || isSelected) highlightColor else buttonColor
    val drawBorder = isSelected // Show border if selected
    var showSubMenu by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .size(48.dp) // Touch size matching BigTouchBox
            .combinedClickable(
                onClick = {
                    Log.d("AssignableButton", "onClick: desc=$contentDescription isActive=$isActive, subCount=${subTools.size}")
                    if (isActive && !isEditMode && subTools.isNotEmpty()) {
                        Log.d("AssignableButton", "Showing submenu!")
                        showSubMenu = true
                    } else {
                        Log.d("AssignableButton", "Delegating to original onClick")
                        onClick()
                    }
                },
                onLongClick = {
                    if (!isEditMode && subTools.isNotEmpty()) {
                        showSubMenu = true
                    } else {
                        onLongClick?.invoke()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Visual Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .background(backgroundColor)
                .then(
                    if (drawBorder) Modifier.border(2.dp, theme?.iconColor ?: Color.White, shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (colorPreview != null) {
                if (payload == ToolPayload.STROKE_COLOR) {
                    // Hollow circle for Stroke
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .border(width = 3.dp, color = colorPreview, shape = CircleShape)
                    )
                } else {
                    // Solid circle for Fill (and others if any)
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colorPreview)
                    )
                }
            } else {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconColor,
                    modifier = Modifier
                        .size(iconSize)
                        .then(if (isEditMode) Modifier.alpha(0.6f) else Modifier)
                )
            }
            
            // Debug text
            if (subTools.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .offset(x = 12.dp, y = 12.dp)
                        .clip(CircleShape)
                        .background(Color.Red)
                )
            }
        }

        if (showSubMenu && subTools.isNotEmpty()) {
            DropdownMenu(
                expanded = showSubMenu,
                onDismissRequest = { showSubMenu = false },
                modifier = Modifier.background(theme?.barBackgroundColor ?: Color.DarkGray)
            ) {
                subTools.forEach { subTool ->
                    DropdownMenuItem(
                        text = { Text(subTool.contentDescription, color = theme?.iconColor ?: Color.White) },
                        leadingIcon = { Icon(subTool.icon, contentDescription = null, tint = theme?.iconColor ?: Color.White) },
                        onClick = {
                            showSubMenu = false
                            onSubToolClick?.invoke(subTool)
                        }
                    )
                }
            }
        }
    }
}

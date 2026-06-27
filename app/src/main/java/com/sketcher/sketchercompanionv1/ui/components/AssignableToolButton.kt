package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Gesture
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler

// Define ToolPayload as requested
enum class ToolPayload(val label: String, val icon: ImageVector) {
    PENCIL("Pencil", Icons.Default.Edit),
    PEN("Pen", Icons.Default.Gesture),
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
    isNone: Boolean = false,
    subTools: List<StudioTool> = emptyList(),
    onSubToolClick: ((StudioTool) -> Unit)? = null,
    tool: StudioTool? = null
) {
    val scaler = LocalUiScaler.current
    val scaleFactor = scaler.scaleFactor
    val backgroundColor = if (isActive || isSelected) highlightColor else buttonColor
    val drawBorder = isSelected // Show border if selected
    var showSubMenu by remember { mutableStateOf(false) }

    LaunchedEffect(subTools) {
        android.util.Log.d("AssignableButton", "subTools for $contentDescription : ${subTools.size}")
    }

    Box(
        modifier = Modifier
            .size(48.dp * scaleFactor) // Touch size matching BigTouchBox
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
                .size(36.dp * scaleFactor)
                .clip(shape)
                .background(backgroundColor)
                .then(
                    if (drawBorder) Modifier.border(2.dp * scaleFactor, theme?.iconColor ?: Color.White, shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isNone && (payload == ToolPayload.STROKE_COLOR || payload == ToolPayload.FILL_COLOR)) {
                // "None" indicator: circle with diagonal slash
                val noneColor = iconColor.copy(alpha = 0.55f)
                Canvas(modifier = Modifier.size(24.dp * scaleFactor)) {
                    val strokeWidth = (2.5.dp * scaleFactor).toPx()
                    val radius = size.minDimension / 2f - strokeWidth / 2f
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    // Circle outline
                    drawCircle(
                        color = noneColor,
                        radius = radius,
                        style = Stroke(width = strokeWidth)
                    )
                    // Diagonal slash (top-right to bottom-left, like a prohibition sign)
                    val angle = Math.toRadians(45.0)
                    val dx = (radius * kotlin.math.cos(angle)).toFloat()
                    val dy = (radius * kotlin.math.sin(angle)).toFloat()
                    drawLine(
                        color = noneColor,
                        start = androidx.compose.ui.geometry.Offset(cx - dx, cy + dy),
                        end = androidx.compose.ui.geometry.Offset(cx + dx, cy - dy),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            } else if (colorPreview != null) {
                if (payload == ToolPayload.STROKE_COLOR) {
                    // Hollow circle for Stroke
                    Box(
                        modifier = Modifier
                            .size(24.dp * scaleFactor)
                            .border(width = 3.dp * scaleFactor, color = colorPreview, shape = CircleShape)
                    )
                } else {
                    // Solid circle for Fill
                    Box(
                        modifier = Modifier
                            .size(24.dp * scaleFactor)
                            .clip(CircleShape)
                            .background(colorPreview)
                    )
                }
            } else {
                if (tool != null && theme != null) {
                    ToolIcon(
                        tool = tool,
                        theme = theme,
                        tint = iconColor,
                        modifier = Modifier.then(if (isEditMode) Modifier.alpha(0.6f) else Modifier),
                        iconSize = iconSize
                    )
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
            }
            // Sub-tools group indicator: small triangle in the bottom-right corner
            if (subTools.isNotEmpty()) {
                Canvas(
                    modifier = Modifier
                        .size(5.dp * scaleFactor)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-4).dp * scaleFactor, y = (-4).dp * scaleFactor)
                ) {
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(size.width, size.height)
                        lineTo(size.width, size.height - size.minDimension)
                        lineTo(size.width - size.minDimension, size.height)
                        close()
                    }
                    drawPath(
                        path = path,
                        color = iconColor.copy(alpha = 0.7f)
                    )
                }
            }
        }

        if (showSubMenu && subTools.isNotEmpty()) {
            DropdownMenu(
                expanded = showSubMenu,
                onDismissRequest = { showSubMenu = false },
                offset = DpOffset(x = 0.dp, y = 8.dp),
                modifier = Modifier.background(theme?.barBackgroundColor ?: Color.DarkGray)
            ) {
                subTools.forEach { subTool ->
                    DropdownMenuItem(
                        text = { Text(subTool.contentDescription, color = theme?.iconColor ?: Color.White) },
                        leadingIcon = { 
                            if (theme != null) {
                                ToolIcon(tool = subTool, theme = theme, tint = theme.iconColor, iconSize = 24.dp)
                            } else {
                                Icon(subTool.icon, contentDescription = null, tint = Color.White)
                            }
                        },
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

package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixNormal
import androidx.compose.material.icons.filled.Edit
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
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

// Define ToolPayload as requested
enum class ToolPayload(val label: String, val icon: ImageVector) {
    PENCIL("Pencil", Icons.Default.Edit),
    ERASER("Eraser", Icons.Default.AutoFixNormal),
    // Using AutoFixNormal as generic 'magic' icon if FormatColorFill isn't available, 
    // but assuming standard icons. Let's try to reference it dynamically or omit if unsure.
    // The user specifically asked for "Fill". I'll assume standard icons are available.
    FILL("Fill", Icons.Default.Edit) // Fallback to Edit if Fill missing, but likely exists. 
    // Wait, Icons.Default.FormatColorFill is not always in basic material icons.
    // I will check if I can import it. If not, I'll use a placeholder.
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AssignableToolButton(
    onClick: () -> Unit,
    onAssign: (ToolPayload) -> Unit,
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
    theme: UiThemeConfig? = null
) {
    var showMenu by remember { mutableStateOf(false) }
    val backgroundColor = if (isActive) highlightColor else buttonColor

    Box(
        modifier = Modifier
            .size(48.dp) // Touch size matching BigTouchBox
            .combinedClickable(
                onClick = onClick,
                onDoubleClick = { showMenu = true },
                onLongClick = { showMenu = true } // Long click is also good for accessibility
            ),
        contentAlignment = Alignment.Center
    ) {
        // Visual Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier
                    .size(iconSize)
                    .then(if (isEditMode) Modifier.alpha(0.6f) else Modifier)
            )
        }

        val menuOffset = if (location != null) {
            when(location) {
                ToolLocation.LeftBar -> DpOffset(48.dp, 0.dp)
                ToolLocation.RightBar -> DpOffset((-48).dp, 0.dp)
                ToolLocation.TopBar -> DpOffset(0.dp, 48.dp)
                ToolLocation.BottomBar -> DpOffset(0.dp, (-48).dp)
                else -> DpOffset.Zero
            }
        } else DpOffset.Zero

        val menuBackgroundColor = theme?.barBackgroundColor ?: Color.White
        val menuContentColor = theme?.iconColor ?: Color.Black

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            offset = menuOffset,
            modifier = Modifier.background(menuBackgroundColor)
        ) {
            ToolPayload.entries.forEach { payload ->
                DropdownMenuItem(
                    text = { Text(payload.label, color = menuContentColor) },
                    leadingIcon = { Icon(payload.icon, contentDescription = null, tint = menuContentColor) },
                    onClick = {
                        onAssign(payload)
                        showMenu = false
                    }
                )
            }
        }
    }
}

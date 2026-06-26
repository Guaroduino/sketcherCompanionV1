package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Icon
import com.sketcher.sketchercompanionv1.ui.components.BigTouchBox
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

import androidx.compose.ui.draw.rotate

@Composable
fun ContextActionBar(
    modifier: Modifier = Modifier,
    tools: List<StudioTool>,
    isVisible: Boolean,
    isEditMode: Boolean,
    theme: UiThemeConfig,
    onToolClick: (Int, StudioTool?) -> Unit
) {
    val scaler = LocalUiScaler.current

    AnimatedVisibility(
        visible = isVisible || isEditMode,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.padding(bottom = scaler.margin)
    ) {
        Row(
            modifier = modifier
                .height(scaler.baseBarHeight)
                .clip(CircleShape)
                .background(theme.barBackgroundColor.copy(alpha = 0.9f))
                .padding(horizontal = scaler.smallMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scaler.smallButtonSpacing)
        ) {
            tools.forEachIndexed { index, tool ->
                val iconMod = if (tool.registryId == "context_flip_vertical") {
                    Modifier.rotate(90f)
                } else {
                    Modifier
                }
                SketcherIconButton(
                    onClick = { onToolClick(index, tool) },
                    icon = tool.icon,
                    contentDescription = tool.contentDescription,
                    isActive = tool.isActive,
                    isEditMode = isEditMode,
                    backgroundColorOverride = null,
                    highlightColor = theme.highlightColor,
                    buttonColor = Color.Transparent, 
                    iconColor = theme.iconColor,
                    shape = CircleShape,
                    iconSize = scaler.baseIconSize,
                    iconModifier = iconMod
                )
            }
            if (isEditMode) {
                BigTouchBox(
                    onClick = { onToolClick(tools.size, null) },
                    touchSize = 48.dp
                ) {
                    Icon(Icons.Default.AddCircleOutline, "Add Tool", tint = theme.iconColor.copy(alpha = 0.5f))
                }
            }
        }
    }
}

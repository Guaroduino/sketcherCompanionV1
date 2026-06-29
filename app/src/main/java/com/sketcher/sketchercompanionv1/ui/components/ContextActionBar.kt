package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll

@Composable
fun ContextActionBar(
    modifier: Modifier = Modifier,
    tools: List<StudioTool>,
    isVisible: Boolean,
    isEditMode: Boolean,
    theme: UiThemeConfig,
    onToolClick: (Int, StudioTool?) -> Unit,
    onSubToolClick: ((Int, StudioTool) -> Unit)? = null
) {
    val scaler = LocalUiScaler.current
    val scrollState = rememberScrollState()

    AnimatedVisibility(
        visible = isVisible || isEditMode,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = Modifier.padding(bottom = scaler.margin)
    ) {
        Row(
            modifier = modifier
                .height(scaler.baseBarHeight)
                .horizontalScroll(scrollState)
                .clip(theme.floatingShape())
                .background(theme.barBackgroundColor.copy(alpha = 0.9f))
                .padding(horizontal = scaler.smallMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(scaler.smallButtonSpacing)
        ) {
            tools.forEachIndexed { index, tool ->
                if (tool.registryId == "divider") {
                    Box(
                        modifier = Modifier
                            .width(6.dp * scaler.scaleFactor)
                            .height(24.dp * scaler.scaleFactor)
                            .background(Color.Transparent)
                            .clickable(enabled = isEditMode) { onToolClick(index, tool) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.dp * scaler.scaleFactor)
                                .height(24.dp * scaler.scaleFactor)
                                .background(theme.iconColor.copy(alpha = 0.3f))
                        )
                    }
                } else {
                    val displayedSubTools = if (!isEditMode) {
                        if (tool.subTools.isNotEmpty()) tool.subTools 
                        else com.sketcher.sketchercompanionv1.ui.model.ToolRegistry.getSubToolsFor(tool.registryId)
                    } else {
                        emptyList()
                    }

                    val iconMod = if (tool.registryId == "context_flip_vertical") {
                        Modifier.rotate(90f)
                    } else {
                        Modifier
                    }

                    AssignableToolButton(
                        onClick = { onToolClick(index, tool) },
                        icon = tool.icon,
                        contentDescription = tool.contentDescription,
                        isActive = tool.isActive,
                        isEditMode = isEditMode,
                        highlightColor = theme.highlightColor,
                        buttonColor = Color.Transparent, 
                        iconColor = theme.iconColor,
                        shape = theme.floatingShape(),
                        iconSize = scaler.baseIconSize,
                        location = com.sketcher.sketchercompanionv1.ui.model.ToolLocation.ContextBar,
                        theme = theme,
                        tool = tool,
                        subTools = displayedSubTools,
                        onSubToolClick = { subTool -> onSubToolClick?.invoke(index, subTool) }
                    )
                }
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

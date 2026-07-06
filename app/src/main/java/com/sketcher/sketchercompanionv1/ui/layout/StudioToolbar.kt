package com.sketcher.sketchercompanionv1.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import com.sketcher.sketchercompanionv1.ui.components.ToolPayload
import com.sketcher.sketchercompanionv1.ui.components.AssignableToolButton
import com.sketcher.sketchercompanionv1.ui.components.SketcherIconButton
import com.sketcher.sketchercompanionv1.ui.components.DynamicSizeButton
import com.sketcher.sketchercompanionv1.ui.components.ToolIcon
import com.sketcher.sketchercompanionv1.ui.components.BigTouchBox
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.theme.UiScaler
import com.sketcher.sketchercompanionv1.ui.theme.advancedShadow

@Composable
fun StudioToolButton(
    tool: StudioTool,
    idx: Int,
    location: ToolLocation,
    isEditMode: Boolean,
    assignedToolsMap: Map<String, ToolPayload>,
    assignedColorsMap: Map<String, Int>,
    strokeColorVal: Int,
    fillColorVal: Int,
    isStrokeActiveVal: Boolean,
    isFillActiveVal: Boolean,
    fillStyleVal: com.sketcher.sketchercompanionv1.dto.FillStyle,
    lastActiveColorToolId: String?,
    theme: UiThemeConfig,
    scaler: UiScaler,
    brushSize: Float,
    resolveIsActive: (StudioTool) -> Boolean,
    resolveIsActionButton: (StudioTool) -> Boolean,
    onSlotEditClick: (ToolLocation, Int) -> Unit,
    onShowSizeOpacity: (String) -> Unit,
    onShowSnapConfig: () -> Unit,
    onToolClick: (StudioTool) -> Unit,
    onEditTool: (ToolPayload, String) -> Unit,
    onSubToolClick: (ToolLocation, Int, StudioTool) -> Unit
) {
    val isActionButton = resolveIsActionButton(tool)
    val isRealAction = !tool.isPlaceholder || isActionButton

    if (tool.registryId == "divider") {
        Box(
            modifier = Modifier
                .height(2.dp)
                .width(24.dp)
                .background(theme.iconColor.copy(alpha = 0.3f))
                .clickable { if (isEditMode) onSlotEditClick(location, idx) }
        )
    } else {
        if (tool.registryId == StudioTool.SIZE_OPACITY_TOOL_ID) {
            DynamicSizeButton(
                onClick = {
                    if (isEditMode) onSlotEditClick(location, idx)
                    else onShowSizeOpacity(tool.registryId)
                },
                brushSize = brushSize,
                isActive = resolveIsActive(tool),
                isEditMode = isEditMode,
                backgroundColorOverride = if (tool.isPlaceholder) Color.Red.copy(alpha = 0.3f) else null,
                highlightColor = theme.highlightColor,
                buttonColor = theme.buttonColor,
                iconColor = theme.iconColor,
                shape = theme.floatingShape()
            )
        } else if (tool.isPlaceholder || tool.registryId.contains("zoom") || tool.registryId == "home_view") {
            val bgColor = if (isActionButton) null else Color.Red.copy(alpha = 0.3f)
            SketcherIconButton(
                onClick = {
                    if (isEditMode) onSlotEditClick(location, idx)
                    else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) onShowSizeOpacity(tool.registryId)
                    else if (isRealAction) onToolClick(tool)
                },
                icon = tool.icon,
                contentDescription = tool.contentDescription,
                isActive = resolveIsActive(tool),
                isEditMode = isEditMode,
                backgroundColorOverride = bgColor,
                highlightColor = theme.highlightColor,
                buttonColor = theme.buttonColor,
                iconColor = theme.iconColor,
                shape = theme.floatingShape(),
                iconSize = scaler.smallIconSize,
                tool = tool,
                theme = theme
            )
        } else {
            AssignableToolButton(
                onClick = {
                    if (isEditMode) onSlotEditClick(location, idx)
                    else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) onShowSizeOpacity(tool.registryId)
                    else if (isRealAction) onToolClick(tool)
                },
                onLongClick = {
                    if (!isEditMode) {
                        if (tool.registryId == "toggle_snap") {
                            onShowSnapConfig()
                        } else {
                            val p = assignedToolsMap[tool.id]
                            if (p != null) onEditTool(p, tool.id)
                        }
                    }
                },
                icon = tool.icon,
                contentDescription = tool.contentDescription,
                isActive = resolveIsActive(tool),
                isEditMode = isEditMode,
                highlightColor = theme.highlightColor,
                buttonColor = theme.buttonColor,
                iconColor = theme.iconColor,
                shape = theme.floatingShape(),
                iconSize = scaler.smallIconSize,
                tool = tool,
                theme = theme,
                location = location,
                payload = assignedToolsMap[tool.id],
                colorPreview = when (assignedToolsMap[tool.id]) {
                    ToolPayload.STROKE_COLOR -> assignedColorsMap[tool.id]?.let { Color(it) } ?: Color(strokeColorVal)
                    ToolPayload.FILL_COLOR -> assignedColorsMap[tool.id]?.let { Color(it) } ?: Color(fillColorVal)
                    else -> null
                },
                fillStylePreview = if (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR) {
                    if (tool.id == lastActiveColorToolId || (lastActiveColorToolId == null && isFillActiveVal)) fillStyleVal else null
                } else null,
                isSelected = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && isStrokeActiveVal) ||
                             (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && isFillActiveVal),
                isNone = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && !isStrokeActiveVal) ||
                         (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && !isFillActiveVal),
                subTools = if (!isEditMode) {
                    if (tool.subTools.isNotEmpty()) tool.subTools 
                    else com.sketcher.sketchercompanionv1.ui.model.ToolRegistry.getSubToolsFor(tool.registryId)
                } else emptyList(),
                onSubToolClick = { subTool -> onSubToolClick(location, idx, subTool) }
            )
        }
    }
}

@Composable
fun StudioCornerButton(
    tool: StudioTool?,
    location: ToolLocation,
    isEditMode: Boolean,
    theme: UiThemeConfig,
    scaler: UiScaler,
    shadowAlpha: Float,
    shadowBlur: Dp,
    shadowOffsetX: Dp,
    shadowOffsetY: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(scaler.baseButtonSize)
            .advancedShadow(
                color = Color.Black,
                alpha = shadowAlpha,
                cornersRadius = if (theme.isRound) scaler.baseButtonSize / 2 else 8.dp,
                shadowBlurRadius = shadowBlur,
                offsetX = shadowOffsetX,
                offsetY = shadowOffsetY
            )
            .then(
                if (tool?.isActive == true || isEditMode || tool?.isPlaceholder == true) {
                    Modifier.clip(theme.floatingShape()).background(
                        when {
                            tool?.isActive == true -> theme.highlightColor
                            isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                            tool?.isPlaceholder == true -> Color.Red.copy(alpha = 0.3f)
                            else -> theme.barBackgroundColor
                        }
                    )
                } else {
                    Modifier.clip(theme.floatingShape()).background(theme.barBackgroundColor)
                }
            )
            .then(
                if (isEditMode) Modifier.border(1.dp, theme.iconColor, theme.floatingShape())
                else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        if (tool != null) {
            ToolIcon(tool = tool, theme = theme, tint = theme.iconColor)
        } else if (isEditMode) {
            Icon(Icons.Default.Add, "Add Tool", tint = theme.iconColor)
        }
    }
}

@Composable
fun BoxScope.StudioLeftBar(
    leftTools: List<StudioTool>,
    isEditMode: Boolean,
    swapHorizontal: Boolean,
    startPadding: Dp,
    endPadding: Dp,
    scaler: UiScaler,
    oppositePanelAlign: Alignment,
    shadowAlpha: Float,
    shadowBlur: Dp,
    shadowOffsetX: Dp,
    shadowOffsetY: Dp,
    theme: UiThemeConfig,
    assignedToolsMap: Map<String, ToolPayload>,
    assignedColorsMap: Map<String, Int>,
    strokeColorVal: Int,
    fillColorVal: Int,
    isStrokeActiveVal: Boolean,
    isFillActiveVal: Boolean,
    fillStyleVal: com.sketcher.sketchercompanionv1.dto.FillStyle,
    lastActiveColorToolId: String?,
    brushSizeVal: Float,
    resolveIsActive: (StudioTool) -> Boolean,
    resolveIsActionButton: (StudioTool) -> Boolean,
    onEditSlot: (Int?) -> Unit,
    onShowSizeOpacity: (String) -> Unit,
    onShowSnapConfig: () -> Unit,
    onToolClick: (StudioTool) -> Unit,
    onEditTool: (ToolPayload, String) -> Unit,
    onSubToolClick: (ToolLocation, Int, StudioTool) -> Unit
) {
    if (leftTools.isNotEmpty() || isEditMode) {
        Box(
            modifier = Modifier
                .padding(start = if (swapHorizontal) 0.dp else startPadding, end = if (swapHorizontal) endPadding else 0.dp)
                .width(scaler.floatingBarWidth)
                .align(oppositePanelAlign)
                .advancedShadow(
                    color = Color.Black,
                    alpha = shadowAlpha,
                    cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                    shadowBlurRadius = shadowBlur,
                    offsetX = shadowOffsetX,
                    offsetY = shadowOffsetY
                )
                .background(theme.barBackgroundColor, theme.floatingShape())
        ) {
            Column(
                modifier = Modifier.padding(vertical = scaler.smallMargin),
                verticalArrangement = Arrangement.spacedBy(scaler.buttonSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                leftTools.forEachIndexed { idx, tool ->
                    StudioToolButton(
                        tool = tool,
                        idx = idx,
                        location = ToolLocation.LeftBar,
                        isEditMode = isEditMode,
                        assignedToolsMap = assignedToolsMap,
                        assignedColorsMap = assignedColorsMap,
                        strokeColorVal = strokeColorVal,
                        fillColorVal = fillColorVal,
                        isStrokeActiveVal = isStrokeActiveVal,
                        isFillActiveVal = isFillActiveVal,
                        fillStyleVal = fillStyleVal,
                        lastActiveColorToolId = lastActiveColorToolId,
                        theme = theme,
                        scaler = scaler,
                        brushSize = brushSizeVal,
                        resolveIsActive = resolveIsActive,
                        resolveIsActionButton = resolveIsActionButton,
                        onSlotEditClick = { _, i -> onEditSlot(i) },
                        onShowSizeOpacity = onShowSizeOpacity,
                        onShowSnapConfig = onShowSnapConfig,
                        onToolClick = onToolClick,
                        onEditTool = onEditTool,
                        onSubToolClick = onSubToolClick
                    )
                }

                if (isEditMode) {
                    BigTouchBox(
                        onClick = { onEditSlot(null) },
                        touchSize = 48.dp
                    ) {
                        Icon(Icons.Default.AddCircleOutline, "Add", tint = theme.iconColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.StudioRightBar(
    rightTools: List<StudioTool>,
    isEditMode: Boolean,
    swapHorizontal: Boolean,
    startPadding: Dp,
    endPadding: Dp,
    scaler: UiScaler,
    panelAlign: Alignment,
    shadowAlpha: Float,
    shadowBlur: Dp,
    shadowOffsetX: Dp,
    shadowOffsetY: Dp,
    theme: UiThemeConfig,
    assignedToolsMap: Map<String, ToolPayload>,
    assignedColorsMap: Map<String, Int>,
    strokeColorVal: Int,
    fillColorVal: Int,
    isStrokeActiveVal: Boolean,
    isFillActiveVal: Boolean,
    fillStyleVal: com.sketcher.sketchercompanionv1.dto.FillStyle,
    lastActiveColorToolId: String?,
    brushSizeVal: Float,
    resolveIsActive: (StudioTool) -> Boolean,
    resolveIsActionButton: (StudioTool) -> Boolean,
    onEditSlot: (Int?) -> Unit,
    onShowSizeOpacity: (String) -> Unit,
    onShowSnapConfig: () -> Unit,
    onToolClick: (StudioTool) -> Unit,
    onEditTool: (ToolPayload, String) -> Unit,
    onSubToolClick: (ToolLocation, Int, StudioTool) -> Unit
) {
    if (rightTools.isNotEmpty() || isEditMode) {
        Box(
            modifier = Modifier
                .padding(end = if (swapHorizontal) 0.dp else endPadding, start = if (swapHorizontal) startPadding else 0.dp)
                .width(scaler.floatingBarWidth)
                .align(panelAlign)
                .advancedShadow(
                    color = Color.Black,
                    alpha = shadowAlpha,
                    cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                    shadowBlurRadius = shadowBlur,
                    offsetX = shadowOffsetX,
                    offsetY = shadowOffsetY
                )
                .background(theme.barBackgroundColor, theme.floatingShape())
        ) {
            Column(
                modifier = Modifier.padding(vertical = scaler.smallMargin),
                verticalArrangement = Arrangement.spacedBy(scaler.buttonSpacing),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                rightTools.forEachIndexed { idx, tool ->
                    StudioToolButton(
                        tool = tool,
                        idx = idx,
                        location = ToolLocation.RightBar,
                        isEditMode = isEditMode,
                        assignedToolsMap = assignedToolsMap,
                        assignedColorsMap = assignedColorsMap,
                        strokeColorVal = strokeColorVal,
                        fillColorVal = fillColorVal,
                        isStrokeActiveVal = isStrokeActiveVal,
                        isFillActiveVal = isFillActiveVal,
                        fillStyleVal = fillStyleVal,
                        lastActiveColorToolId = lastActiveColorToolId,
                        theme = theme,
                        scaler = scaler,
                        brushSize = brushSizeVal,
                        resolveIsActive = resolveIsActive,
                        resolveIsActionButton = resolveIsActionButton,
                        onSlotEditClick = { _, i -> onEditSlot(i) },
                        onShowSizeOpacity = onShowSizeOpacity,
                        onShowSnapConfig = onShowSnapConfig,
                        onToolClick = onToolClick,
                        onEditTool = onEditTool,
                        onSubToolClick = onSubToolClick
                    )
                }

                if (isEditMode) {
                    BigTouchBox(
                        onClick = { onEditSlot(null) },
                        touchSize = 48.dp
                    ) {
                        Icon(Icons.Default.AddCircleOutline, "Add", tint = theme.iconColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

@Composable
fun BoxScope.StudioTopBar(
    topTools: List<StudioTool>,
    isEditMode: Boolean,
    swapHorizontal: Boolean,
    swapVertical: Boolean,
    animHorizontalOffset: Dp,
    animTopOffset: Dp,
    animBottomOffset: Dp,
    scaler: UiScaler,
    shadowAlpha: Float,
    shadowBlur: Dp,
    shadowOffsetX: Dp,
    shadowOffsetY: Dp,
    theme: UiThemeConfig,
    assignedToolsMap: Map<String, ToolPayload>,
    assignedColorsMap: Map<String, Int>,
    strokeColorVal: Int,
    fillColorVal: Int,
    isStrokeActiveVal: Boolean,
    isFillActiveVal: Boolean,
    fillStyleVal: com.sketcher.sketchercompanionv1.dto.FillStyle,
    lastActiveColorToolId: String?,
    brushSizeVal: Float,
    resolveIsActive: (StudioTool) -> Boolean,
    resolveIsActionButton: (StudioTool) -> Boolean,
    onEditSlot: (Int?) -> Unit,
    onShowSizeOpacity: (String) -> Unit,
    onShowSnapConfig: () -> Unit,
    onToolClick: (StudioTool) -> Unit,
    onEditTool: (ToolPayload, String) -> Unit,
    onSubToolClick: (ToolLocation, Int, StudioTool) -> Unit
) {
    if (topTools.isNotEmpty() || isEditMode) {
        Box(
            modifier = Modifier
                .offset(x = if (swapHorizontal) (animHorizontalOffset / 2) else -(animHorizontalOffset / 2))
                .padding(top = if (swapVertical) animBottomOffset else animTopOffset)
                .height(scaler.floatingBarWidth)
                .align(if (swapVertical) Alignment.BottomCenter else Alignment.TopCenter)
                .advancedShadow(
                    color = Color.Black,
                    alpha = shadowAlpha,
                    cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                    shadowBlurRadius = shadowBlur,
                    offsetX = shadowOffsetX,
                    offsetY = shadowOffsetY
                )
                .background(theme.barBackgroundColor, theme.floatingShape())
        ) {
            Row(
                modifier = Modifier.padding(horizontal = scaler.smallMargin),
                horizontalArrangement = Arrangement.spacedBy(scaler.buttonSpacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                topTools.forEachIndexed { idx, tool ->
                    StudioToolButton(
                        tool = tool,
                        idx = idx,
                        location = ToolLocation.TopBar,
                        isEditMode = isEditMode,
                        assignedToolsMap = assignedToolsMap,
                        assignedColorsMap = assignedColorsMap,
                        strokeColorVal = strokeColorVal,
                        fillColorVal = fillColorVal,
                        isStrokeActiveVal = isStrokeActiveVal,
                        isFillActiveVal = isFillActiveVal,
                        fillStyleVal = fillStyleVal,
                        lastActiveColorToolId = lastActiveColorToolId,
                        theme = theme,
                        scaler = scaler,
                        brushSize = brushSizeVal,
                        resolveIsActive = resolveIsActive,
                        resolveIsActionButton = resolveIsActionButton,
                        onSlotEditClick = { _, i -> onEditSlot(i) },
                        onShowSizeOpacity = onShowSizeOpacity,
                        onShowSnapConfig = onShowSnapConfig,
                        onToolClick = onToolClick,
                        onEditTool = onEditTool,
                        onSubToolClick = onSubToolClick
                    )
                }

                if (isEditMode) {
                    BigTouchBox(
                        onClick = { onEditSlot(null) },
                        touchSize = 48.dp
                    ) {
                        Icon(Icons.Default.AddCircleOutline, "Add", tint = theme.iconColor.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}

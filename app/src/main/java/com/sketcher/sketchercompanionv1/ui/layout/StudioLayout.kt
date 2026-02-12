package com.sketcher.sketchercompanionv1.ui.layout

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.SketcherCanvasView
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.theme.UiScaler
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import com.sketcher.sketchercompanionv1.ui.components.BigTouchBox
import com.sketcher.sketchercompanionv1.ui.components.ColorPickerDialog
import com.sketcher.sketchercompanionv1.ui.components.ColorPreviewRow
import com.sketcher.sketchercompanionv1.ui.theme.advancedShadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.ToolLocation
import com.sketcher.sketchercompanionv1.ui.dialogs.ToolPickerDialog
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check

@Composable
fun StudioLayout(
    viewModel: SketcherViewModel,
    uiCollapsed: Boolean,
    onToggleUi: () -> Unit,
    swapVertical: Boolean,
    swapHorizontal: Boolean
) {
    // 1. Config & State
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val scaler = LocalUiScaler.current
    val interfaceScale = viewModel.interfaceScale
    
    val theme by viewModel.themeConfig.collectAsState()
    val currentLayers by viewModel.layers.collectAsState()
    val tools by viewModel.toolbarState.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()

    // Tool Picker State
    var toolPickerTarget by remember { mutableStateOf<Pair<ToolLocation, Int?>?>(null) }
    
    // Panel Internal States (Independent Folding)
    var showTopBar by remember { mutableStateOf(true) }
    var showBottomBar by remember { mutableStateOf(true) }
    var showRightPanel by remember { mutableStateOf(true) }
    
    // Sync external uiCollapsed trigger (optional: if user presses global toggle, collapse/expand all)
    LaunchedEffect(uiCollapsed) {
        if (uiCollapsed) {
            showTopBar = false
            showBottomBar = false
            showRightPanel = false
        } else {
            showTopBar = true
            showBottomBar = true
            showRightPanel = true
        }
    }

    // Panel Size State
    var rightPanelWidth by remember(scaler) { mutableStateOf(scaler.sidePanelWidth) }
    var layersPanelWeight by remember { mutableFloatStateOf(0.5f) }
    var showPersonalizationDialog by remember { mutableStateOf(false) }

    // --- SWAP STATES (Moved to MainActivity) ---

    // --- DYNAMIC ALIGNMENTS ---
    val mainBarAlign = if (swapVertical) Alignment.BottomCenter else Alignment.TopCenter
    val secondaryBarAlign = if (swapVertical) Alignment.TopCenter else Alignment.BottomCenter
    val panelAlign = if (swapHorizontal) Alignment.CenterStart else Alignment.CenterEnd
    val oppositePanelAlign = if (swapHorizontal) Alignment.CenterEnd else Alignment.CenterStart

    // --- PHYSICS-BASED ANIMATIONS ---
    // Common Spec for Synchronization
    val animSpec = spring<Dp>(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
    val floatAnimSpec = spring<Float>(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
    val intOffsetAnimSpec = spring<IntOffset>(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)

    // Top Offset: Dependent on showTopBar
    // Gap Logic: When bar is visible, offset is barHeight + panelGap. When hidden, it's just panelGap (to clear the toggle button).
    val animTopOffset by animateDpAsState(
        targetValue = if (!showTopBar) scaler.panelGap else (scaler.baseBarHeight + scaler.panelGap),
        animationSpec = animSpec,
        label = "TopOffset"
    )

    // Bottom Offset: Dependent on showBottomBar
    val animBottomOffset by animateDpAsState(
        targetValue = if (!showBottomBar) scaler.panelGap else (scaler.baseBarHeight + scaler.panelGap),
        animationSpec = animSpec,
        label = "BottomOffset"
    )

    // Toggle Offsets (Synchronized with Bars)
    val animTopToggleOffset by animateDpAsState(
        targetValue = if (showTopBar) scaler.baseBarHeight else 0.dp,
        animationSpec = animSpec,
        label = "TopToggleOffset"
    )
    
    val animBottomToggleOffset by animateDpAsState(
        targetValue = if (showBottomBar) scaler.baseBarHeight else 0.dp,
        animationSpec = animSpec,
        label = "BottomToggleOffset"
    )

    // Right Panel Vertical Expansion Logic
    // If Top Bar is hidden, top padding is 0. If visible, baseBarHeight.
    val animRightPanelTopPadding by animateDpAsState(
        targetValue = if (showTopBar) scaler.baseBarHeight else 0.dp,
        animationSpec = animSpec,
        label = "RightPanelTop"
    )
    
    val animRightPanelBottomPadding by animateDpAsState(
        targetValue = if (showBottomBar) scaler.baseBarHeight else 0.dp,
        animationSpec = animSpec,
        label = "RightPanelBottom"
    )

    // Horizontal Offset: Dependent on showRightPanel
    val animHorizontalOffset by animateDpAsState(
        targetValue = if (!showRightPanel) scaler.panelGap else (rightPanelWidth + scaler.panelGap),
        animationSpec = animSpec,
        label = "HorizontalOffset"
    )

    // Dynamic Padding Base for Floating HUD
    // Increase separation to avoid side toggles
    val hudSideGap = scaler.panelGap
    val panelWidthOffset = if (!showRightPanel) hudSideGap else (rightPanelWidth + hudSideGap)
    val startPadding = if (swapHorizontal) panelWidthOffset else hudSideGap
    val endPadding = if (!swapHorizontal) panelWidthOffset else hudSideGap

    // --- ANIMATED ROTATIONS FOR TOGGLES ---
    val topRotation by animateFloatAsState(targetValue = if (showTopBar) 0f else 180f, animationSpec = floatAnimSpec, label = "TopRotation")
    val bottomRotation by animateFloatAsState(targetValue = if (showBottomBar) 0f else 180f, animationSpec = floatAnimSpec, label = "BottomRotation")
    val sideRotation by animateFloatAsState(targetValue = if (showRightPanel) 0f else 180f, animationSpec = floatAnimSpec, label = "SideRotation")

    // Correction for BigTouchBox internal padding (TouchSize 64dp - VisualSize)
    // We want the VISUAL button to align with the margin, so we pull the box out by this amount.
    val touchCorrection = ((64.dp - scaler.baseButtonSize) / 2).coerceAtLeast(0.dp)

    // Shadow Logic: Only if fully opaque (alpha=1.0).
    // Shadow Logic: Only if fully opaque (alpha=1.0).
    val isEffectivelyShowingShadow = theme.isShadowEnabled && theme.barBackgroundColor.alpha == 1f
    val shadowRad = (theme.shadowAngle * PI / 180).toFloat()

    // Use shadowBlur for both blur and to derive offset
    val shadowDistance = theme.shadowBlur.value
    val shadowOffsetX = if (isEffectivelyShowingShadow) (shadowDistance * 0.5f * cos(shadowRad)).dp else 0.dp
    val shadowOffsetY = if (isEffectivelyShowingShadow) (shadowDistance * 0.5f * sin(shadowRad)).dp else 0.dp
    val shadowBlur = if (isEffectivelyShowingShadow) theme.shadowBlur else 0.dp
    val shadowAlpha = if (isEffectivelyShowingShadow) theme.shadowOpacity else 0f

    Box(modifier = Modifier.fillMaxSize()) {
        
        // --- LAYER 1: CANVAS ---
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                SketcherCanvasView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    this.onStrokeCompleted = { stroke -> viewModel.addVectorStroke(stroke) }
                    this.onHybridStrokeCompleted = { s, f -> viewModel.addHybridStroke(s, f) }
                    this.canvasBackgroundColor = viewModel.backgroundColor
                }
            },
            update = { view ->
                view.currentTool = viewModel.currentTool
                view.activeColor = viewModel.currentColor
                view.activeSize = viewModel.currentSize
                view.activeStrokeType = viewModel.currentStrokeType
                view.isFillModeEnabled = viewModel.isFillModeEnabled
                view.fillModeColor = viewModel.fillModeColor
                view.gridConfig = viewModel.gridConfig
                view.scaleConfig = viewModel.scaleConfig
                view.currentUnit = viewModel.currentUnit
                if (currentLayers.isNotEmpty()) {
                    view.setLayers(currentLayers, viewModel.componentLibrary, viewModel.editingContext)
                }
                view.invalidate()
            }
        )

        // --- LAYER 2: COLLAPSIBLE FRAME (The "Yellow/Cyan" Zones) ---
        
        // MAIN BAR (User/Menu)
        AnimatedVisibility(
            visible = showTopBar,
            enter = if (swapVertical) slideInVertically(initialOffsetY = { it }, animationSpec = intOffsetAnimSpec) else slideInVertically(initialOffsetY = { -it }, animationSpec = intOffsetAnimSpec),
            exit = if (swapVertical) slideOutVertically(targetOffsetY = { it }, animationSpec = intOffsetAnimSpec) else slideOutVertically(targetOffsetY = { -it }, animationSpec = intOffsetAnimSpec),
            modifier = Modifier.align(mainBarAlign)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaler.baseBarHeight)
                    .background(theme.barBackgroundColor, theme.panelShape())
            ) {
                 Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(horizontal = scaler.margin)
                        .fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(scaler.margin)
                ) {
                    // Update Button (Placeholder for UI Toggle logic if needed in bar)
                    BigTouchBox(
                        onClick = onToggleUi,
                        touchSize = 48.dp
                    ) {
                        Icon(Icons.Default.Refresh, "Toggle UI", tint = theme.iconColor)
                    }
                    
                    // User Profile
                    Surface(
                        modifier = Modifier.size(32.sdp),
                        shape = CircleShape,
                        color = theme.buttonColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("U", color = theme.iconColor, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    
                    // Menu
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        BigTouchBox(
                            onClick = { menuExpanded = true },
                            touchSize = 48.dp
                        ) {
                            Icon(Icons.Default.MoreVert, "Menu", tint = theme.iconColor)
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Personalization") },
                                onClick = { 
                                    menuExpanded = false
                                    showPersonalizationDialog = true 
                                }
                            )
                        }
                    }
                }
            }
        }
        
        // SECONDARY BAR
        AnimatedVisibility(
            visible = showBottomBar,
            enter = if (swapVertical) slideInVertically(initialOffsetY = { -it }, animationSpec = intOffsetAnimSpec) else slideInVertically(initialOffsetY = { it }, animationSpec = intOffsetAnimSpec),
            exit = if (swapVertical) slideOutVertically(targetOffsetY = { -it }, animationSpec = intOffsetAnimSpec) else slideOutVertically(targetOffsetY = { it }, animationSpec = intOffsetAnimSpec),
            modifier = Modifier.align(secondaryBarAlign)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaler.baseBarHeight)
                    .background(theme.barBackgroundColor, theme.panelShape())
            )
        }

        // SIDE PANEL (Layers/Library)
        AnimatedVisibility(
            visible = showRightPanel,
            enter = if (swapHorizontal) slideInHorizontally(initialOffsetX = { -it }, animationSpec = intOffsetAnimSpec) else slideInHorizontally(initialOffsetX = { it }, animationSpec = intOffsetAnimSpec),
            exit = if (swapHorizontal) slideOutHorizontally(targetOffsetX = { -it }, animationSpec = intOffsetAnimSpec) else slideOutHorizontally(targetOffsetX = { it }, animationSpec = intOffsetAnimSpec),
            modifier = Modifier.align(panelAlign)
        ) {
            Box(
                modifier = Modifier
                    .width(rightPanelWidth)
                    .fillMaxHeight()
                    .padding(
                        top = if (swapVertical) animRightPanelBottomPadding else animRightPanelTopPadding, 
                        bottom = if (swapVertical) animRightPanelTopPadding else animRightPanelBottomPadding
                    ) 
                    .background(theme.barBackgroundColor, theme.panelShape())
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // TOP: LAYERS
                    Box(
                        modifier = Modifier
                            .weight(layersPanelWeight)
                            .fillMaxWidth()
                            .padding(scaler.smallMargin)
                            .border(1.sdp, theme.iconColor.copy(alpha = 0.2f), RoundedCornerShape(4.sdp))
                    ) {
                        Text("LAYERS", color = theme.iconColor, modifier = Modifier.align(Alignment.Center))
                    }
                    
                    // DIVIDER (RESIZE HANDLES)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.sdp)
                            .background(theme.iconColor.copy(alpha = 0.05f)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Horizontal Grip (Width)
                        Box(
                            modifier = Modifier
                                .width(32.sdp)
                                .fillMaxHeight()
                                .clickable { } // Consume clicks
                                .draggable(
                                    orientation = Orientation.Horizontal,
                                    state = rememberDraggableState { delta ->
                                        // If swapped (Start), dragging right (+) increases width. 
                                        // If not swapped (End), dragging left (-) increases width.
                                        val adjustedDelta = if (swapHorizontal) with(density) { delta.toDp() } else -with(density) { delta.toDp() }
                                        val newWidth = rightPanelWidth + adjustedDelta
                                        // Apply scale to min/max limits
                                        val minWidth = 200.dp * interfaceScale
                                        val maxWidth = 450.dp * interfaceScale
                                        rightPanelWidth = newWidth.coerceIn(minWidth, maxWidth)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                             Icon(Icons.Default.DragHandle, "Resize Width", tint = theme.iconColor.copy(alpha = 0.5f), modifier = Modifier.size(16.sdp))
                        }
                        
                        // Vertical Grip (Split Weight)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .draggable(
                                    orientation = Orientation.Vertical,
                                    state = rememberDraggableState { delta ->
                                        val totalHeightPx = with(density) { (config.screenHeightDp.dp - (scaler.baseBarHeight * 2)).toPx() }
                                        if (totalHeightPx > 0) {
                                            val change = delta / totalHeightPx
                                            layersPanelWeight = (layersPanelWeight + change).coerceIn(0.2f, 0.8f)
                                        }
                                    }
                                )
                                .background(Color.Transparent), // Hit area
                            contentAlignment = Alignment.Center
                        ) {
                             Box(modifier = Modifier.fillMaxWidth().height(1.sdp).background(theme.iconColor.copy(alpha = 0.2f)))
                        }
                    }
                    
                    // BOTTOM: LIBRARY
                    Box(
                        modifier = Modifier
                            .weight(1f - layersPanelWeight) // Fill remaining
                            .fillMaxWidth()
                            .padding(scaler.smallMargin)
                            .border(1.sdp, theme.iconColor.copy(alpha = 0.2f), RoundedCornerShape(4.sdp))
                    ) {
                        Text("LIBRARY", color = theme.iconColor, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }

        // MAIN BAR TOGGLE
        Box(
            modifier = Modifier
                .align(mainBarAlign)
                .offset(
                    y = if (swapVertical) -animTopToggleOffset else animTopToggleOffset, 
                    x = if (swapHorizontal) (animHorizontalOffset / 2) else -(animHorizontalOffset / 2)
                )
                .width(scaler.toggleLength)
                .height(scaler.panelGap)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showTopBar = !showTopBar }
                ),
            contentAlignment = if (swapVertical) Alignment.BottomCenter else Alignment.TopCenter
        ) {
            Box(
                modifier = Modifier
                    .width(scaler.toggleLength)
                    .height(scaler.toggleThickness)
                    .clip(if (swapVertical) RoundedCornerShape(topStart = 8.sdp, topEnd = 8.sdp) else RoundedCornerShape(bottomStart = 8.sdp, bottomEnd = 8.sdp))
                    .background(theme.barBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (swapVertical) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle Main Bar",
                    tint = theme.iconColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(scaler.smallIconSize)
                        .rotate(topRotation)
                )
            }
        }

        // SECONDARY BAR TOGGLE
        Box(
            modifier = Modifier
                .align(secondaryBarAlign)
                .offset(
                    y = if (swapVertical) animBottomToggleOffset else -animBottomToggleOffset, 
                    x = if (swapHorizontal) (animHorizontalOffset / 2) else -(animHorizontalOffset / 2)
                )
                .width(scaler.toggleLength)
                .height(scaler.panelGap)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showBottomBar = !showBottomBar }
                ),
            contentAlignment = if (swapVertical) Alignment.TopCenter else Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(scaler.toggleLength)
                    .height(scaler.toggleThickness)
                    .clip(if (swapVertical) RoundedCornerShape(bottomStart = 8.sdp, bottomEnd = 8.sdp) else RoundedCornerShape(topStart = 8.sdp, topEnd = 8.sdp))
                    .background(theme.barBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (swapVertical) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Secondary Bar",
                    tint = theme.iconColor.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(scaler.smallIconSize)
                        .rotate(bottomRotation)
                )
            }
        }

        // Right Panel Toggle
        // Position Logic:
        // The button should be aligned to CenterEnd.
        // It needs to be offset to the left of the panel.
        // Distance from Right Edge = (Panel Width if visible)
        // Since panel width animates (via visibility?), no, panel width is state.
        // We want it to move with the panel.
        // The simplest way to "attach" it is to use the same offset logic as the HUD but minus the gap?
        // Let's use `animRightOffset`.
        // `animRightOffset` target is `PanelWidth + Gap` (Expanded) or `Gap` (Collapsed).
        // If we offset by `-animRightOffset`, we are at `Right - (Panel + Gap)`.
        // The button thickness is small. 
        // We want Button at `Right - PanelWidth - ButtonThickness`.
        // If we use `-animRightOffset`, we are at `Right - PanelWidth - Gap`.
        // If Button is inside the Gap, we can align it to the *right* of that gap space?
        // Let's try `offset(x = -(animRightOffset - scaler.panelGap))` -> This puts it at `Right - PanelWidth`.
        // Then subtract button thickness? Or just align it there.
        // SIDE PANEL TOGGLE
        Box(
            modifier = Modifier
                .align(panelAlign)
                .offset(x = if (swapHorizontal) (animHorizontalOffset - scaler.panelGap) else -(animHorizontalOffset - scaler.panelGap))
                .width(hudSideGap)
                .height(scaler.toggleLength)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showRightPanel = !showRightPanel }
                ),
            contentAlignment = if (swapHorizontal) Alignment.CenterStart else Alignment.CenterEnd
        ) {
             Box(
                 modifier = Modifier
                     .width(scaler.toggleThickness)
                     .height(scaler.toggleLength)
                     .clip(if (swapHorizontal) RoundedCornerShape(topEnd = 8.sdp, bottomEnd = 8.sdp) else RoundedCornerShape(topStart = 8.sdp, bottomStart = 8.sdp))
                     .background(theme.barBackgroundColor),
                 contentAlignment = Alignment.Center
             ) {
                 Icon(
                     imageVector = if (swapHorizontal) Icons.Default.KeyboardArrowLeft else Icons.Default.KeyboardArrowRight,
                     contentDescription = "Toggle Side Panel",
                     tint = theme.iconColor.copy(alpha = 0.7f),
                     modifier = Modifier
                         .size(scaler.smallIconSize)
                         .rotate(sideRotation)
                 )
             }
        }


        // --- LAYER 4: FLOATING HUD (The "Magenta/Green" Zones) ---
        // Linked to Physics Animations for Offset. The offsets now include the "Panel Gap".
        
// 1. Top-Left Button (Menu/Back)
        val topLeftTool = tools[ToolLocation.TopLeftCorner]?.firstOrNull()
        if (topLeftTool != null || isEditMode) {
            BigTouchBox(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = (if (swapVertical) animBottomOffset else animTopOffset) - touchCorrection, 
                        start = startPadding - touchCorrection
                    ),
                onClick = { 
                    if (isEditMode) toolPickerTarget = ToolLocation.TopLeftCorner to 0
                    else topLeftTool?.onClick?.invoke()
                },
                touchSize = 64.dp
            ) {
                Box(
                    modifier = Modifier
                        .size(scaler.baseButtonSize)
                        .advancedShadow(
                            color = Color.Black,
                            alpha = shadowAlpha,
                            cornersRadius = if (theme.isRound) scaler.baseButtonSize / 2 else 8.dp,
                            shadowBlurRadius = shadowBlur,
                            offsetX = shadowOffsetX,
                            offsetY = shadowOffsetY
                        )
                        .clip(theme.floatingShape())
                        .background(
                            when {
                                topLeftTool?.isActive == true -> theme.highlightColor
                                isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                                else -> theme.barBackgroundColor
                            }
                        )
                        .then(
                            if (isEditMode) Modifier.border(1.dp, theme.iconColor, theme.floatingShape())
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (topLeftTool != null) {
                        Icon(topLeftTool.icon, topLeftTool.contentDescription, tint = theme.iconColor)
                    } else if (isEditMode) {
                        Icon(Icons.Default.Add, "Add Tool", tint = theme.iconColor)
                    }
                }
            }
        }

        // 2. Top-Right Button (Settings)
        val topRightTool = tools[ToolLocation.TopRightCorner]?.firstOrNull()
        if (topRightTool != null || isEditMode) {
            BigTouchBox(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = (if (swapVertical) animBottomOffset else animTopOffset) - touchCorrection, 
                        end = endPadding - touchCorrection
                    ),
                onClick = { 
                    if (isEditMode) toolPickerTarget = ToolLocation.TopRightCorner to 0
                    else if (topRightTool?.id == "settings") showPersonalizationDialog = true 
                    else topRightTool?.onClick?.invoke()
                },
                touchSize = 64.dp
            ) {
                Box(
                    modifier = Modifier
                        .size(scaler.baseButtonSize)
                        .advancedShadow(
                            color = Color.Black,
                            alpha = shadowAlpha,
                            cornersRadius = if (theme.isRound) scaler.baseButtonSize / 2 else 8.dp,
                            shadowBlurRadius = shadowBlur,
                            offsetX = shadowOffsetX,
                            offsetY = shadowOffsetY
                        )
                        .clip(theme.floatingShape())
                        .background(
                            when {
                                topRightTool?.isActive == true -> theme.highlightColor
                                isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                                else -> theme.barBackgroundColor
                            }
                        )
                        .then(
                            if (isEditMode) Modifier.border(1.dp, theme.iconColor, theme.floatingShape())
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (topRightTool != null) {
                        Icon(topRightTool.icon, topRightTool.contentDescription, tint = theme.iconColor)
                    } else if (isEditMode) {
                        Icon(Icons.Default.Add, "Add Tool", tint = theme.iconColor)
                    }
                }
            }
        }
        
        // 3. Bottom-Left Button (Undo)
        val bottomLeftTool = tools[ToolLocation.BottomLeftCorner]?.firstOrNull()
        if (bottomLeftTool != null || isEditMode) {
            BigTouchBox(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(
                        bottom = (if (swapVertical) animTopOffset else animBottomOffset) - touchCorrection, 
                        start = startPadding - touchCorrection
                    ),
                onClick = { 
                    if (isEditMode) toolPickerTarget = ToolLocation.BottomLeftCorner to 0
                    else bottomLeftTool?.onClick?.invoke()
                },
                touchSize = 64.dp
            ) {
                Box(
                    modifier = Modifier
                        .size(scaler.baseButtonSize)
                        .advancedShadow(
                            color = Color.Black,
                            alpha = shadowAlpha,
                            cornersRadius = if (theme.isRound) scaler.baseButtonSize / 2 else 8.dp,
                            shadowBlurRadius = shadowBlur,
                            offsetX = shadowOffsetX,
                            offsetY = shadowOffsetY
                        )
                        .clip(theme.floatingShape())
                        .background(
                            when {
                                bottomLeftTool?.isActive == true -> theme.highlightColor
                                isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                                else -> theme.barBackgroundColor
                            }
                        )
                        .then(
                            if (isEditMode) Modifier.border(1.dp, theme.iconColor, theme.floatingShape())
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (bottomLeftTool != null) {
                        Icon(bottomLeftTool.icon, bottomLeftTool.contentDescription, tint = theme.iconColor)
                    } else if (isEditMode) {
                        Icon(Icons.Default.Add, "Add Tool", tint = theme.iconColor)
                    }
                }
            }
        }
        
        // 4. Bottom-Right Button (Redo)
        val bottomRightTool = tools[ToolLocation.BottomRightCorner]?.firstOrNull()
        if (bottomRightTool != null || isEditMode) {
            BigTouchBox(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        bottom = (if (swapVertical) animTopOffset else animBottomOffset) - touchCorrection, 
                        end = endPadding - touchCorrection
                    ),
                onClick = { 
                    if (isEditMode) toolPickerTarget = ToolLocation.BottomRightCorner to 0
                    else bottomRightTool?.onClick?.invoke()
                },
                touchSize = 64.dp
            ) {
                Box(
                    modifier = Modifier
                        .size(scaler.baseButtonSize)
                        .advancedShadow(
                            color = Color.Black,
                            alpha = shadowAlpha,
                            cornersRadius = if (theme.isRound) scaler.baseButtonSize / 2 else 8.dp,
                            shadowBlurRadius = shadowBlur,
                            offsetX = shadowOffsetX,
                            offsetY = shadowOffsetY
                        )
                        .clip(theme.floatingShape())
                        .background(
                            when {
                                bottomRightTool?.isActive == true -> theme.highlightColor
                                isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                                else -> theme.barBackgroundColor
                            }
                        )
                        .then(
                            if (isEditMode) Modifier.border(1.dp, theme.iconColor, theme.floatingShape())
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (bottomRightTool != null) {
                        Icon(bottomRightTool.icon, bottomRightTool.contentDescription, tint = theme.iconColor)
                    } else if (isEditMode) {
                        Icon(Icons.Default.Add, "Add Tool", tint = theme.iconColor)
                    }
                }
            }
        }
        
        // 5. Side Floating Bar 1 (Tools) - LeftBar
        val leftTools = tools[ToolLocation.LeftBar] ?: emptyList()
        if (leftTools.isNotEmpty() || isEditMode) {
            Box(
                modifier = Modifier
                    .padding(start = startPadding)
                    .width(scaler.floatingBarWidth)
                    .align(Alignment.CenterStart)
                    .advancedShadow(
                        color = Color.Black,
                        alpha = shadowAlpha,
                        cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                        shadowBlurRadius = shadowBlur,
                        offsetX = shadowOffsetX,
                        offsetY = shadowOffsetY
                    )
                    .clip(theme.floatingShape())
                    .background(theme.barBackgroundColor)
            ) {
                Column(
                    modifier = Modifier.padding(vertical = scaler.smallMargin),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    leftTools.forEachIndexed { idx, tool ->
                         if (tool.id == "divider") {
                             Box(
                                 modifier = Modifier
                                     .height(2.dp)
                                     .width(24.dp)
                                     .background(theme.iconColor.copy(alpha = 0.3f))
                                     .clickable { if (isEditMode) toolPickerTarget = ToolLocation.LeftBar to idx }
                             )
                         } else {
                             BigTouchBox(
                                onClick = {
                                    if (isEditMode) toolPickerTarget = ToolLocation.LeftBar to idx
                                    else tool.onClick()
                                },
                                touchSize = 48.dp
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.contentDescription,
                                    tint = if (tool.isActive) theme.highlightColor else theme.iconColor,
                                    modifier = Modifier
                                        .size(scaler.smallIconSize)
                                        .then(if (isEditMode) Modifier.alpha(0.6f) else Modifier)
                                )
                            }
                         }
                    }
                    if (isEditMode) {
                        BigTouchBox(
                            onClick = { toolPickerTarget = ToolLocation.LeftBar to null },
                            touchSize = 48.dp
                        ) {
                            Icon(Icons.Default.AddCircleOutline, "Add", tint = theme.iconColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
        
        // 6. Side Floating Bar 2 (Properties) - RightBar
        val rightTools = tools[ToolLocation.RightBar] ?: emptyList()
        if (rightTools.isNotEmpty() || isEditMode) {
            Box(
                modifier = Modifier
                    .padding(end = endPadding)
                    .width(scaler.floatingBarWidth)
                    .align(Alignment.CenterEnd)
                    .advancedShadow(
                        color = Color.Black,
                        alpha = shadowAlpha,
                        cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                        shadowBlurRadius = shadowBlur,
                        offsetX = shadowOffsetX,
                        offsetY = shadowOffsetY
                    )
                    .clip(theme.floatingShape())
                    .background(theme.barBackgroundColor)
            ) {
                 Column(
                    modifier = Modifier.padding(vertical = scaler.smallMargin),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    rightTools.forEachIndexed { idx, tool ->
                         if (tool.id == "divider") {
                             Box(
                                 modifier = Modifier
                                     .height(2.dp)
                                     .width(24.dp)
                                     .background(theme.iconColor.copy(alpha = 0.3f))
                                     .clickable { if (isEditMode) toolPickerTarget = ToolLocation.RightBar to idx }
                             )
                         } else {
                             BigTouchBox(
                                onClick = {
                                    if (isEditMode) toolPickerTarget = ToolLocation.RightBar to idx
                                    else tool.onClick()
                                },
                                touchSize = 48.dp
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.contentDescription,
                                    tint = if (tool.isActive) theme.highlightColor else theme.iconColor,
                                    modifier = Modifier
                                        .size(scaler.smallIconSize)
                                        .then(if (isEditMode) Modifier.alpha(0.6f) else Modifier)
                                )
                            }
                         }
                    }
                    if (isEditMode) {
                        BigTouchBox(
                            onClick = { toolPickerTarget = ToolLocation.RightBar to null },
                            touchSize = 48.dp
                        ) {
                            Icon(Icons.Default.AddCircleOutline, "Add", tint = theme.iconColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // 7. Top Floating Bar (Center) - TopBar
        val topTools = tools[ToolLocation.TopBar] ?: emptyList()
        if (topTools.isNotEmpty() || isEditMode) {
            Box(
                modifier = Modifier
                    .offset(x = if (swapHorizontal) (animHorizontalOffset / 2) else -(animHorizontalOffset / 2))
                    .padding(top = if (swapVertical) animBottomOffset else animTopOffset)
                    .height(scaler.floatingBarWidth)
                    .align(Alignment.TopCenter)
                    .advancedShadow(
                        color = Color.Black,
                        alpha = shadowAlpha,
                        cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                        shadowBlurRadius = shadowBlur,
                        offsetX = shadowOffsetX,
                        offsetY = shadowOffsetY
                    )
                    .clip(theme.floatingShape())
                    .background(theme.barBackgroundColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = scaler.smallMargin, vertical = 4.dp), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topTools.forEachIndexed { idx, tool ->
                         if (tool.id == "divider") {
                             Box(
                                 modifier = Modifier
                                     .width(2.dp)
                                     .height(24.dp)
                                     .background(theme.iconColor.copy(alpha = 0.3f))
                                     .clickable { if (isEditMode) toolPickerTarget = ToolLocation.TopBar to idx }
                             )
                         } else {
                             BigTouchBox(
                                onClick = {
                                    if (isEditMode) toolPickerTarget = ToolLocation.TopBar to idx
                                    else tool.onClick()
                                },
                                touchSize = 48.dp
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.contentDescription,
                                    tint = if (tool.isActive) theme.highlightColor else theme.iconColor,
                                    modifier = Modifier
                                        .size(scaler.smallIconSize)
                                        .then(if (isEditMode) Modifier.alpha(0.6f) else Modifier)
                                )
                            }
                         }
                    }
                    if (isEditMode) {
                        BigTouchBox(
                            onClick = { toolPickerTarget = ToolLocation.TopBar to null },
                            touchSize = 48.dp
                        ) {
                            Icon(Icons.Default.AddCircleOutline, "Add", tint = theme.iconColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // 8. Bottom Floating Bar (Center) - BottomBar
        val bottomTools = tools[ToolLocation.BottomBar] ?: emptyList()
        if (bottomTools.isNotEmpty() || isEditMode) {
            Box(
                modifier = Modifier
                    .offset(x = if (swapHorizontal) (animHorizontalOffset / 2) else -(animHorizontalOffset / 2))
                    .padding(bottom = if (swapVertical) animTopOffset else animBottomOffset)
                    .height(scaler.floatingBarWidth)
                    .align(Alignment.BottomCenter)
                    .advancedShadow(
                        color = Color.Black,
                        alpha = shadowAlpha,
                        cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                        shadowBlurRadius = shadowBlur,
                        offsetX = shadowOffsetX,
                        offsetY = shadowOffsetY
                    )
                    .clip(theme.floatingShape())
                    .background(theme.barBackgroundColor)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = scaler.smallMargin, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    bottomTools.forEachIndexed { idx, tool ->
                         if (tool.id == "divider") {
                             Box(
                                 modifier = Modifier
                                     .width(2.dp)
                                     .height(24.dp)
                                     .background(theme.iconColor.copy(alpha = 0.3f))
                                     .clickable { if (isEditMode) toolPickerTarget = ToolLocation.BottomBar to idx }
                             )
                         } else {
                             BigTouchBox(
                                onClick = {
                                    if (isEditMode) toolPickerTarget = ToolLocation.BottomBar to idx
                                    else tool.onClick()
                                },
                                touchSize = 48.dp
                            ) {
                                Icon(
                                    imageVector = tool.icon,
                                    contentDescription = tool.contentDescription,
                                    tint = if (tool.isActive) theme.highlightColor else theme.iconColor,
                                    modifier = Modifier
                                        .size(scaler.smallIconSize)
                                        .then(if (isEditMode) Modifier.alpha(0.6f) else Modifier)
                                )
                            }
                         }
                    }
                    if (isEditMode) {
                        BigTouchBox(
                            onClick = { toolPickerTarget = ToolLocation.BottomBar to null },
                            touchSize = 48.dp
                        ) {
                            Icon(Icons.Default.AddCircleOutline, "Add", tint = theme.iconColor.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }

        // --- EXIT EDIT MODE BUTTON ---
        if (isEditMode) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 100.dp), // Offset from center to not block drawing area too much
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = { viewModel.toggleEditMode() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.highlightColor,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, "Done")
                        Text("Finish Customization", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }

    // --- TOOL PICKER DIALOG ---
    toolPickerTarget?.let { (location, index) ->
        ToolPickerDialog(
            location = location,
            index = index,
            theme = theme,
            onDismiss = { toolPickerTarget = null },
            onToolSelected = { newTool ->
                if (index == null) viewModel.addTool(location, newTool)
                else viewModel.replaceTool(location, index, newTool)
            },
            onRemove = {
                index?.let { viewModel.removeTool(location, it) }
            }
        )
    }

    // --- DIALOGS ---
    if (showPersonalizationDialog) {
        Dialog(onDismissRequest = { showPersonalizationDialog = false }) {
             Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(16.dp).width(300.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Customize Theme", style = MaterialTheme.typography.titleLarge, color = theme.iconColor)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Shape Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Round Shapes", color = theme.iconColor)
                        Switch(
                            checked = theme.isRound,
                            onCheckedChange = { viewModel.updateTheme(theme.copy(isRound = it)) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Edit Toolbars Switch
                    val isEditModeByVM by viewModel.isEditMode.collectAsState()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Edit Toolbars", color = theme.iconColor)
                        Switch(
                            checked = isEditModeByVM,
                            onCheckedChange = { viewModel.toggleEditMode() }
                        )
                    }
 
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // UI Scale Slider
                    var tempScale by remember { mutableStateOf(interfaceScale) }
                    Text("UI Scale: ${String.format("%.1f", tempScale)}x", color = theme.iconColor, style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = tempScale,
                        onValueChange = { tempScale = it },
                        onValueChangeFinished = { viewModel.updateInterfaceScale(tempScale) },
                        valueRange = 0.5f..1.5f,
                        modifier = Modifier.fillMaxWidth()
                    )
 
                    Spacer(modifier = Modifier.height(24.dp))
                    // --- SEPARATE COLOR PICKERS ---
                    var pickingColorFor by remember { mutableStateOf<String?>(null) }
                    
                    ColorPreviewRow(
                        label = "Bar Color",
                        color = theme.barBackgroundColor,
                        labelColor = theme.iconColor,
                        onClick = { pickingColorFor = "bar" }
                    )
                    
                    ColorPreviewRow(
                        label = "Button Color",
                        color = theme.buttonColor,
                        labelColor = theme.iconColor,
                        onClick = { pickingColorFor = "button" }
                    )
                    
                    ColorPreviewRow(
                        label = "Icon Color",
                        color = theme.iconColor,
                        labelColor = theme.iconColor,
                        onClick = { pickingColorFor = "icon" }
                    )
                    
                    ColorPreviewRow(
                        label = "Highlight Color",
                        color = theme.highlightColor,
                        labelColor = theme.iconColor,
                        onClick = { pickingColorFor = "highlight" }
                    )

                    if (pickingColorFor != null) {
                        val initialColor = when(pickingColorFor) {
                            "bar" -> theme.barBackgroundColor
                            "button" -> theme.buttonColor
                            "icon" -> theme.iconColor
                            "highlight" -> theme.highlightColor
                            else -> Color.Transparent
                        }
                        
                        ColorPickerDialog(
                            initialColor = initialColor,
                            recentColors = theme.recentColors,
                            onDismiss = { pickingColorFor = null },
                            onColorSelected = { newColor ->
                                // Update recent colors
                                val newRecents = (listOf(newColor) + theme.recentColors)
                                    .distinct()
                                    .take(12)
                                
                                when(pickingColorFor) {
                                    "bar" -> viewModel.updateTheme(theme.copy(barBackgroundColor = newColor, recentColors = newRecents))
                                    "button" -> viewModel.updateTheme(theme.copy(buttonColor = newColor, recentColors = newRecents))
                                    "icon" -> viewModel.updateTheme(theme.copy(iconColor = newColor, recentColors = newRecents))
                                    "highlight" -> viewModel.updateTheme(theme.copy(highlightColor = newColor, recentColors = newRecents))
                                }
                                pickingColorFor = null
                            }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Opacity Slider (Optional now since color picker has alpha, but keeping for direct access)
                    Text("Bar Opacity: ${(theme.barBackgroundColor.alpha * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = theme.iconColor)
                    Slider(
                        value = theme.barBackgroundColor.alpha,
                        onValueChange = { 
                            viewModel.updateTheme(theme.copy(barBackgroundColor = theme.barBackgroundColor.copy(alpha = it))) 
                        },
                        valueRange = 0f..1f
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    Spacer(modifier = Modifier.height(12.dp))

                    // --- SHADOW CONTROLS ---
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Shadows", style = MaterialTheme.typography.labelMedium, color = theme.iconColor)
                        Switch(
                            checked = theme.isShadowEnabled,
                            onCheckedChange = { viewModel.updateTheme(theme.copy(isShadowEnabled = it)) }
                        )
                    }

                    // Shadow Options UI: Only show if shadows enabled AND opacity is 100%
                    val canShowShadowOptions = theme.isShadowEnabled && theme.barBackgroundColor.alpha == 1f
                    
                    AnimatedVisibility(visible = canShowShadowOptions) {
                        Column {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Shadow Opacity Slider
                            Text("Shadow Opacity: ${(theme.shadowOpacity * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = theme.iconColor)
                            Slider(
                                value = theme.shadowOpacity,
                                onValueChange = { 
                                    viewModel.updateTheme(theme.copy(shadowOpacity = it)) 
                                },
                                valueRange = 0f..1f
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            // Shadow Blur Slider
                            Text("Shadow Blur: ${theme.shadowBlur.value.toInt()} dp", style = MaterialTheme.typography.labelMedium, color = theme.iconColor)
                            Slider(
                                value = theme.shadowBlur.value,
                                onValueChange = { 
                                    viewModel.updateTheme(theme.copy(shadowBlur = it.dp)) 
                                },
                                valueRange = 0f..24f
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))

                            // Angle Slider
                            Text("Shadow Angle: ${theme.shadowAngle.toInt()}°", style = MaterialTheme.typography.labelMedium, color = theme.iconColor)
                            Slider(
                                value = theme.shadowAngle,
                                onValueChange = { 
                                    viewModel.updateTheme(theme.copy(shadowAngle = it)) 
                                },
                                valueRange = 0f..360f
                            )
                        }
                    }

                    if (theme.isShadowEnabled && theme.barBackgroundColor.alpha < 1f) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Shadows hidden because Opacity < 100%", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error.copy(alpha=0.7f)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showPersonalizationDialog = false },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.buttonColor,
                            contentColor = theme.iconColor
                        )
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

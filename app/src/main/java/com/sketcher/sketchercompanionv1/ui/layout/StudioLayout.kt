package com.sketcher.sketchercompanionv1.ui.layout

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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

@Composable
fun StudioLayout(
    viewModel: SketcherViewModel,
    userScale: Float, // Still passed but can be derived from LocalUiScaler
    uiCollapsed: Boolean,
    onToggleUi: () -> Unit
) {
    // 1. Config & State
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val scaler = LocalUiScaler.current
    
    val theme by viewModel.themeConfig.collectAsState()
    val currentLayers by viewModel.layers.collectAsState()
    
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

    // --- PHYSICS-BASED ANIMATIONS ---
    // Common Spec for Synchronization
    val animSpec = spring<Dp>(stiffness = Spring.StiffnessLow, dampingRatio = Spring.DampingRatioNoBouncy)
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

    // Right Offset: Dependent on showRightPanel
    val animRightOffset by animateDpAsState(
        targetValue = if (!showRightPanel) scaler.panelGap else (rightPanelWidth + scaler.panelGap),
        animationSpec = animSpec,
        label = "RightOffset"
    )

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
        
        // TOP BAR
        AnimatedVisibility(
            visible = showTopBar,
            enter = slideInVertically(initialOffsetY = { -it }, animationSpec = intOffsetAnimSpec),
            exit = slideOutVertically(targetOffsetY = { -it }, animationSpec = intOffsetAnimSpec),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaler.baseBarHeight)
                    .background(theme.barBackgroundColor)
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
                    IconButton(onClick = onToggleUi) {
                        Icon(Icons.Default.Refresh, "Toggle UI", tint = Color.White)
                    }
                    
                    // User Profile
                    Surface(
                        modifier = Modifier.size(32.sdp),
                        shape = CircleShape,
                        color = theme.accentColor
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("U", color = Color.Black, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    
                    // Menu
                    Box {
                        var menuExpanded by remember { mutableStateOf(false) }
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, "Menu", tint = Color.White)
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
        
        // BOTTOM BAR
        AnimatedVisibility(
            visible = showBottomBar,
            enter = slideInVertically(initialOffsetY = { it }, animationSpec = intOffsetAnimSpec),
            exit = slideOutVertically(targetOffsetY = { it }, animationSpec = intOffsetAnimSpec),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(scaler.baseBarHeight)
                    .background(theme.barBackgroundColor)
            )
        }

        // RIGHT PANEL
        AnimatedVisibility(
            visible = showRightPanel,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = intOffsetAnimSpec),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = intOffsetAnimSpec),
            modifier = Modifier.align(Alignment.CenterEnd)
        ) {
            Box(
                modifier = Modifier
                    .width(rightPanelWidth)
                    .fillMaxHeight()
                    .padding(top = animRightPanelTopPadding, bottom = animRightPanelBottomPadding) 
                    .background(theme.barBackgroundColor)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // TOP: LAYERS
                    Box(
                        modifier = Modifier
                            .weight(layersPanelWeight)
                            .fillMaxWidth()
                            .padding(scaler.smallMargin)
                            .border(1.sdp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.sdp))
                    ) {
                        Text("LAYERS", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                    }
                    
                    // DIVIDER (RESIZE HANDLES)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.sdp)
                            .background(Color.White.copy(alpha = 0.05f)),
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
                                        val newWidth = rightPanelWidth - with(density) { delta.toDp() }
                                        // Apply scale to min/max limits
                                        val minWidth = 200.dp * userScale
                                        val maxWidth = 450.dp * userScale
                                        rightPanelWidth = newWidth.coerceIn(minWidth, maxWidth)
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                             Icon(Icons.Default.DragHandle, "Resize Width", tint = Color.Gray, modifier = Modifier.size(16.sdp))
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
                             Box(modifier = Modifier.fillMaxWidth().height(1.sdp).background(Color.Gray))
                        }
                    }
                    
                    // BOTTOM: LIBRARY
                    Box(
                        modifier = Modifier
                            .weight(1f - layersPanelWeight) // Fill remaining
                            .fillMaxWidth()
                            .padding(scaler.smallMargin)
                            .border(1.sdp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.sdp))
                    ) {
                        Text("LIBRARY", color = Color.Gray, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }

        // --- LAYER 3: TOGGLE BUTTONS (Minimalist) ---
        // Top Toggle
        Box(
            modifier = Modifier
                .offset(y = animTopToggleOffset, x = -(animRightOffset / 2)) // Center in available space 
                .width(scaler.toggleLength)
                .height(scaler.toggleThickness)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(bottomStart = 8.sdp, bottomEnd = 8.sdp))
                .background(theme.barBackgroundColor.copy(alpha = 0.8f))
                .clickable { showTopBar = !showTopBar },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (showTopBar) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle Top Bar",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(scaler.smallIconSize)
            )
        }

        // Bottom Toggle
        Box(
            modifier = Modifier
                // For bottom, offset is negative from bottom? No, align BottomCenter.
                // If visible, offset = -BarHeight. If hidden, offset = 0.
                .offset(y = -animBottomToggleOffset, x = -(animRightOffset / 2)) // Center in available space
                .width(scaler.toggleLength)
                .height(scaler.toggleThickness)
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 8.sdp, topEnd = 8.sdp))
                .background(theme.barBackgroundColor.copy(alpha = 0.8f))
                .clickable { showBottomBar = !showBottomBar },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (showBottomBar) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                contentDescription = "Toggle Bottom Bar",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(scaler.smallIconSize)
            )
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
        Box(
             modifier = Modifier
                 .offset(x = -(animRightOffset - scaler.panelGap)) // Moves it to the left edge of the Panel (or Screen Edge if collapsed)
                 .width(scaler.toggleThickness)
                 .height(scaler.toggleLength)
                 .align(Alignment.CenterEnd)
                 .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                 .background(theme.barBackgroundColor.copy(alpha = 0.8f))
                 .clickable { showRightPanel = !showRightPanel },
             contentAlignment = Alignment.Center
         ) {
             Icon(
                 if (showRightPanel) Icons.Default.KeyboardArrowRight else Icons.Default.KeyboardArrowLeft,
                 contentDescription = "Toggle Right Panel",
                 tint = Color.White.copy(alpha = 0.7f),
                 modifier = Modifier.size(scaler.smallIconSize)
             )
         }


        // --- LAYER 4: FLOATING HUD (The "Magenta/Green" Zones) ---
        // Linked to Physics Animations for Offset. The offsets now include the "Panel Gap".
        
        // 1. Top-Left Button (Menu/Back) - Use Top Offset
        Box(
            modifier = Modifier
                .padding(top = animTopOffset, start = scaler.margin)
                .size(scaler.baseButtonSize)
                .align(Alignment.TopStart)
                .clip(theme.shape())
                .background(theme.buttonColor)
        )

        // 2. Top-Right Button (Settings) - Use Top & Right Offset
        Box(
            modifier = Modifier
                .padding(top = animTopOffset, end = animRightOffset)
                .size(scaler.baseButtonSize)
                .align(Alignment.TopEnd)
                .clip(theme.shape())
                .background(theme.buttonColor)
        )
        
        // 3. Bottom-Left Button (Undo) - Use Bottom Offset
        Box(
            modifier = Modifier
                .padding(bottom = animBottomOffset, start = scaler.margin)
                .size(scaler.baseButtonSize)
                .align(Alignment.BottomStart)
                .clip(theme.shape())
                .background(theme.buttonColor)
        )
        
        // 4. Bottom-Right Button (Redo) - Use Bottom & Right Offset
        Box(
            modifier = Modifier
                .padding(bottom = animBottomOffset, end = animRightOffset)
                .size(scaler.baseButtonSize)
                .align(Alignment.BottomEnd)
                .clip(theme.shape())
                .background(theme.buttonColor)
        )
        
        // 5. Left Floating Bar (Tools) - Static left padding, but vertical centering could be improved? 
        // For now, center vertically.
        Box(
            modifier = Modifier
                .padding(start = scaler.margin)
                .width(scaler.floatingBarWidth)
                .height(200.dp * userScale)
                .align(Alignment.CenterStart)
                .clip(theme.shape())
                .background(theme.barBackgroundColor)
        )
        
        // 6. Right Floating Bar (Properties) - Use Right Offset
        Box(
            modifier = Modifier
                .padding(end = animRightOffset)
                .width(scaler.floatingBarWidth)
                .height(200.dp * userScale)
                .align(Alignment.CenterEnd)
                .clip(theme.shape())
                .background(theme.barBackgroundColor)
        )

        // 7. Top Floating Bar (Center) - Use Top Offset
        Box(
            modifier = Modifier
                .offset(x = -(animRightOffset / 2)) // Center in available space
                .padding(top = animTopOffset)
                .width(200.dp * userScale) // Dynamic Width
                .height(scaler.floatingBarWidth) // Thinner like floating bar thickness
                .align(Alignment.TopCenter)
                .clip(theme.shape())
                .background(theme.barBackgroundColor)
        )

        // 8. Bottom Floating Bar (Center) - Use Bottom Offset
        Box(
            modifier = Modifier
                .offset(x = -(animRightOffset / 2)) // Center in available space
                .padding(bottom = animBottomOffset)
                .width(200.dp * userScale)
                .height(scaler.floatingBarWidth)
                .align(Alignment.BottomCenter)
                .clip(theme.shape())
                .background(theme.barBackgroundColor)
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
                    Text("Customize Theme", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Shape Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Round Shapes")
                        Switch(
                            checked = theme.isRound,
                            onCheckedChange = { viewModel.updateTheme(theme.copy(isRound = it)) }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Bar Color", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Color Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        val colors = listOf(
                            Color.Black.copy(alpha=0.7f),
                            Color(0xFF1A237E).copy(alpha=0.8f), // Dark Blue
                            Color(0xFF1B5E20).copy(alpha=0.8f), // Dark Green
                            Color(0xFF3E2723).copy(alpha=0.8f)  // Dark Brown
                        )
                        
                        colors.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(color, CircleShape)
                                    .clickable { 
                                        viewModel.updateTheme(theme.copy(barBackgroundColor = color))
                                    }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { showPersonalizationDialog = false },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

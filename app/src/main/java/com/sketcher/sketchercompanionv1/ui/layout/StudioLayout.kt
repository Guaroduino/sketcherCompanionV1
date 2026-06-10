package com.sketcher.sketchercompanionv1.ui.layout

import android.view.ViewGroup
import android.widget.FrameLayout
import android.graphics.Matrix
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.window.Dialog
import android.os.Build
import android.view.WindowManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
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
import com.sketcher.sketchercompanionv1.ui.components.ToolPayload
import com.sketcher.sketchercompanionv1.ui.components.AssignableToolButton
import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import com.sketcher.sketchercompanionv1.ui.dialogs.ToolPropertiesPanel
import com.sketcher.sketchercompanionv1.ui.dialogs.QuickSmoothingPopup
import com.sketcher.sketchercompanionv1.ui.dialogs.SizeOpacityPopup
import com.sketcher.sketchercompanionv1.ui.components.DynamicSizeButton
import com.sketcher.sketchercompanionv1.ui.components.SketcherIconButton
import com.sketcher.sketchercompanionv1.ui.components.SelectionContextBar
import com.sketcher.sketchercompanionv1.ui.components.ContextActionBar
import com.sketcher.sketchercompanionv1.ui.panels.OutlinerPanel
import com.sketcher.sketchercompanionv1.dto.ToolType
import androidx.core.view.drawToBitmap

@Composable
fun StudioLayout(
    viewModel: SketcherViewModel,
    uiCollapsed: Boolean,
    onToggleUi: () -> Unit,
    swapVertical: Boolean,
    swapHorizontal: Boolean,
    projectActions: com.sketcher.sketchercompanionv1.ui.model.ProjectActions
) {
    // 1. Config & State
    val config = androidx.compose.ui.platform.LocalConfiguration.current
    val density = LocalDensity.current
    val scaler = LocalUiScaler.current
    val interfaceScale = viewModel.interfaceScale
    
    val theme by viewModel.themeConfig.collectAsState()
    val currentLayers = viewModel.layers
    val tools by viewModel.toolbarState.collectAsState()
    val assignedToolsMap by viewModel.assignedTools.collectAsState()
    val assignedColorsMap by viewModel.assignedToolColors.collectAsState()
    val isEditMode by viewModel.isEditMode.collectAsState()
    val cameraMatrix by viewModel.cameraMatrix.collectAsState()

    val currentTool = viewModel.toolManager.currentTool
    val resolveIsActive: (StudioTool) -> Boolean = { tool ->
        val payload = assignedToolsMap[tool.id]
        if (payload == ToolPayload.PENCIL || tool.registryId == "pencil" || tool.registryId == "brush") {
            currentTool == ToolType.FREEHAND
        } else if (payload == ToolPayload.ERASER || tool.registryId == "eraser") {
            currentTool == ToolType.ERASER
        } else if (tool.registryId.startsWith("tool_selection") || tool.registryId == "tool_transform") {
            currentTool == ToolType.SELECTION
        } else {
            tool.isActive
        }
    }

    val resolveIsActionButton: (StudioTool) -> Boolean = { tool ->
        tool.registryId == "undo" || tool.registryId == "redo" || tool.registryId == "play" || tool.registryId == "pause" || 
        tool.registryId == "zoom_in" || tool.registryId == "zoom_out" || tool.registryId == "zoom_fit" || tool.registryId == "home_view" ||
        tool.registryId.startsWith("tool_selection") || tool.registryId == "tool_transform"
    }

    // Tool Picker State
    var toolPickerTarget by remember { mutableStateOf<Pair<ToolLocation, Int?>?>(null) }

    val canvasViewRef = remember { mutableStateOf<SketcherCanvasView?>(null) }
    val isProjectionActive = viewModel.isProjectionActive

    LaunchedEffect(isProjectionActive) {
        if (!isProjectionActive) return@LaunchedEffect
        var frameCount = 0
        while (viewModel.isProjectionActive) {
            if (viewModel.projectionMode == "sync") {
                canvasViewRef.value?.let { view ->
                    val livePoints = view.getLiveStrokePoints()
                    val livePath = view.getLiveStrokePath()?.let { android.graphics.Path(it) }
                    val committedPath = view.getLiveCommittedPath()
                    val liveFillPath = view.getLiveFillPath()?.let { android.graphics.Path(it) }
                    val liveRadius = view.getLiveGeneratedRadius()
                    viewModel.renderAndSendSyncFrame(livePoints, livePath, committedPath, liveFillPath, liveRadius)
                } ?: run {
                    viewModel.renderAndSendSyncFrame(null, null, null, null, 0f)
                }
            }
            frameCount++
            if (frameCount % 8 == 0) {
                if (viewModel.projectionMode == "fixed") {
                    canvasViewRef.value?.let { view ->
                        val livePoints = view.getLiveStrokePoints()
                        val livePath = view.getLiveStrokePath()?.let { android.graphics.Path(it) }
                        val committedPath = view.getLiveCommittedPath()
                        val liveFillPath = view.getLiveFillPath()?.let { android.graphics.Path(it) }
                        val liveRadius = view.getLiveGeneratedRadius()
                        viewModel.renderAndSendFixedSnapshot(livePoints, livePath, committedPath, liveFillPath, liveRadius)
                    } ?: run {
                        viewModel.renderAndSendFixedSnapshot(null, null, null, null, 0f)
                    }
                }
            }
            kotlinx.coroutines.delay(66)
        }
    }
    
    // Panel Internal States (Independent Folding)
    var showTopBar by remember { mutableStateOf(false) }
    var showBottomBar by remember { mutableStateOf(false) }
    
    val showStrokeColorPicker by viewModel.showStrokeColorPicker.collectAsState()
    val showFillColorPicker by viewModel.showFillColorPicker.collectAsState()

    val strokeColorVal by viewModel.strokeColor.collectAsState()
    val fillColorVal by viewModel.fillColor.collectAsState()
    val isStrokeActiveVal by viewModel.isStrokeActive.collectAsState()
    val isFillActiveVal by viewModel.isFillActive.collectAsState()
    var showRightPanel by remember { mutableStateOf(false) }
    
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
    var rightPanelWidth by remember(scaler) { mutableStateOf(scaler.sidePanelWidth * 1.4f) }
    var layersPanelWeight by remember { mutableFloatStateOf(0.5f) }
    var showPersonalizationDialog by remember { mutableStateOf(false) }
    var showStabilizationPopup by remember { mutableStateOf(false) }
    var showSizeOpacityPopup by remember { mutableStateOf(false) }
    var showStudioMenu by remember { mutableStateOf(false) }

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
        AndroidView<SketcherCanvasView>(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                SketcherCanvasView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, 
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    onStrokeCompleted = { stroke -> viewModel.addVectorStroke(stroke) }
                    onHybridStrokeCompleted = { s, f -> viewModel.addHybridStroke(s, f) }
                    // Delegar el borrado al sistema de comandos del ViewModel para evitar
                    // ConcurrentModificationException: el ViewModel muta la lista de forma
                    // segura a través de EraseCommand, sin usar iteradores en el hilo de UI.
                    onRequestErase = { worldX, worldY, diameterPx ->
                        viewModel.erase(worldX, worldY, diameterPx)
                    }
                    onCameraMatrixChanged = { matrix: android.graphics.Matrix -> viewModel.saveCameraState(matrix) }
                    canvasBackgroundColor = viewModel.backgroundColor
                    canvasViewRef.value = this
                }

            },
            update = { view: SketcherCanvasView ->
                if (canvasViewRef.value != view) {
                    canvasViewRef.value = view
                }
                view.projectionViewports = viewModel.projectionViewports
                view.currentTool = viewModel.currentTool
                
                view.activeStrokeColor = strokeColorVal
                view.activeFillColor = fillColorVal
                view.isStrokeActive = isStrokeActiveVal
                view.isFillActive = isFillActiveVal
                
                view.activeSize = viewModel.currentSize
                view.activeStrokeType = viewModel.currentStrokeType
                view.activeFreehandSettings = viewModel.currentFreehandSettings
                view.isFlattenedOuterStrokeEnabled = viewModel.toolManager.isFlattenedOuterStrokeEnabled
                view.isFingerMode = viewModel.fingerModeActive
                view.fingerOffsetX = viewModel.fingerOffsetXValue
                view.fingerOffsetY = viewModel.fingerOffsetYValue
                view.isPalmRejectionEnabled = viewModel.isPalmRejectionEnabled
                view.isDebugWireframe = viewModel.isDebugWireframe
                view.canvasBackgroundColor = viewModel.backgroundColor
                
                view.gridConfig = viewModel.gridConfig
                view.scaleConfig = viewModel.scaleConfig
                view.currentUnit = viewModel.currentUnit
                view.globalStabilizationLevel = viewModel.globalStabilizationLevel
                view.isSnapToGridEnabled = viewModel.isSnapToGridEnabled
                view.selectionManager = viewModel.selectionManager
                view.currentSelectionMode = viewModel.currentSelectionMode
                // Synchronize Camera Matrix (Studio UI Activation)
                // BREAK FEEDBACK LOOP: Only update if the camera actually changed from an external source
                if (!view.isCameraEqual(cameraMatrix)) {
                    view.setCameraMatrix(cameraMatrix)
                }
                
                // Keep ViewModel updated with viewport size for center-zoom calculation
                viewModel.saveDimensions(view.width.toFloat(), view.height.toFloat())

                // Force update when layer content changes (internal mutation)
                val updateTrigger = viewModel.layerUpdateTrigger
                
                if (currentLayers.isNotEmpty()) {
                    view.setLayers(
                        newLayers = currentLayers,
                        library = viewModel.componentLibrary,
                        editingCtx = viewModel.editingContext,
                        updateTrigger = updateTrigger,
                        activeIndex = viewModel.activeLayerIndex
                    )
                }
                view.invalidate()
            }
        )
        
        // --- 0. BACKGROUND UI LAYERS (Scale Indicator) ---
        // We get current zoom from the ViewModel which tracks it via onCameraMatrixChanged
        val currentCameraMatrix by viewModel.cameraMatrix.collectAsState()
        val matrixValues = FloatArray(9)
        currentCameraMatrix.getValues(matrixValues)
        val currentZoom = matrixValues[Matrix.MSCALE_X]
        
        // Position below the Top-Left Corner button
        // The corner button is at 'animTopOffset' (visually). Its size is 'scaler.baseButtonSize'.
        // We add a margin to place the indicator below it.
        val indicatorTopOffset = (if (swapVertical) animBottomOffset else animTopOffset) + scaler.baseButtonSize + scaler.smallMargin
        
        com.sketcher.sketchercompanionv1.ui.ScaleIndicator(
            scaleConfig = viewModel.scaleConfig,
            currentUnit = viewModel.currentUnit,
            currentZoom = currentZoom,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = startPadding, top = indicatorTopOffset) 
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
                    .glassmorphicBackground(theme, theme.panelShape())
            ) {
                 Row(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
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
                    .glassmorphicBackground(theme, theme.panelShape())
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
                    .glassmorphicBackground(theme, theme.panelShape())
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
                        OutlinerPanel(viewModel)
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
                                        val maxWidth = 630.dp * interfaceScale
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
                    val shape = if (swapVertical) RoundedCornerShape(topStart = 8.sdp, topEnd = 8.sdp) else RoundedCornerShape(bottomStart = 8.sdp, bottomEnd = 8.sdp)
                    Box(
                        modifier = Modifier
                            .width(scaler.toggleLength)
                            .height(scaler.toggleThickness)
                            .glassmorphicBackground(theme, shape),
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
                    val shape = if (swapVertical) RoundedCornerShape(bottomStart = 8.sdp, bottomEnd = 8.sdp) else RoundedCornerShape(topStart = 8.sdp, topEnd = 8.sdp)
                    Box(
                        modifier = Modifier
                            .width(scaler.toggleLength)
                            .height(scaler.toggleThickness)
                            .glassmorphicBackground(theme, shape),
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
             val shape = if (swapHorizontal) RoundedCornerShape(topEnd = 8.sdp, bottomEnd = 8.sdp) else RoundedCornerShape(topStart = 8.sdp, bottomStart = 8.sdp)
             Box(
                 modifier = Modifier
                     .width(scaler.toggleThickness)
                     .height(scaler.toggleLength)
                     .glassmorphicBackground(theme, shape),
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
                        top = ((if (swapVertical) animBottomOffset else animTopOffset) - touchCorrection).coerceAtLeast(0.dp), 
                        start = (startPadding - touchCorrection).coerceAtLeast(0.dp)
                    ),
                onClick = { 
                    if (isEditMode) toolPickerTarget = ToolLocation.TopLeftCorner to 0
                    else {
                        if (topLeftTool?.id == "menu") showStudioMenu = true
                        else topLeftTool?.onClick?.invoke()
                    }
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
                        .then(
                            if (topLeftTool?.isActive == true || isEditMode || topLeftTool?.isPlaceholder == true) {
                                Modifier.clip(theme.floatingShape()).background(
                                    when {
                                        topLeftTool?.isActive == true -> theme.highlightColor
                                        isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                                        topLeftTool?.isPlaceholder == true -> Color.Red.copy(alpha = 0.3f)
                                        else -> theme.barBackgroundColor
                                    }
                                )
                            } else {
                                Modifier.glassmorphicBackground(theme, theme.floatingShape())
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
                        top = ((if (swapVertical) animBottomOffset else animTopOffset) - touchCorrection).coerceAtLeast(0.dp), 
                        end = (endPadding - touchCorrection).coerceAtLeast(0.dp)
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
                        .then(
                            if (topRightTool?.isActive == true || isEditMode || topRightTool?.isPlaceholder == true) {
                                Modifier.clip(theme.floatingShape()).background(
                                    when {
                                        topRightTool?.isActive == true -> theme.highlightColor
                                        isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                                        topRightTool?.isPlaceholder == true -> Color.Red.copy(alpha = 0.3f)
                                        else -> theme.barBackgroundColor
                                    }
                                )
                            } else {
                                Modifier.glassmorphicBackground(theme, theme.floatingShape())
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
                        bottom = ((if (swapVertical) animTopOffset else animBottomOffset) - touchCorrection).coerceAtLeast(0.dp), 
                        start = (startPadding - touchCorrection).coerceAtLeast(0.dp)
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
                        .then(
                            if (bottomLeftTool?.isActive == true || isEditMode || bottomLeftTool?.isPlaceholder == true) {
                                Modifier.clip(theme.floatingShape()).background(
                                    when {
                                        bottomLeftTool?.isActive == true -> theme.highlightColor
                                        isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                                        bottomLeftTool?.isPlaceholder == true -> Color.Red.copy(alpha = 0.3f)
                                        else -> theme.barBackgroundColor
                                    }
                                )
                            } else {
                                Modifier.glassmorphicBackground(theme, theme.floatingShape())
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
                        bottom = ((if (swapVertical) animTopOffset else animBottomOffset) - touchCorrection).coerceAtLeast(0.dp), 
                        end = (endPadding - touchCorrection).coerceAtLeast(0.dp)
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
                        .then(
                            if (bottomRightTool?.isActive == true || isEditMode || bottomRightTool?.isPlaceholder == true) {
                                Modifier.clip(theme.floatingShape()).background(
                                    when {
                                        bottomRightTool?.isActive == true -> theme.highlightColor
                                        isEditMode -> theme.barBackgroundColor.copy(alpha = 0.5f)
                                        bottomRightTool?.isPlaceholder == true -> Color.Red.copy(alpha = 0.3f)
                                        else -> theme.barBackgroundColor
                                    }
                                )
                            } else {
                                Modifier.glassmorphicBackground(theme, theme.floatingShape())
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
                    .glassmorphicBackground(theme, theme.floatingShape())
            ) {
                Column(
                    modifier = Modifier.padding(vertical = scaler.smallMargin),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    leftTools.forEachIndexed { idx, tool ->
                         val isActionButton = resolveIsActionButton(tool)
                         val isRealAction = !tool.isPlaceholder || isActionButton
                         if (tool.id == "divider") {
                             Box(
                                 modifier = Modifier
                                     .height(2.dp)
                                     .width(24.dp)
                                     .background(theme.iconColor.copy(alpha = 0.3f))
                                     .clickable { if (isEditMode) toolPickerTarget = ToolLocation.LeftBar to idx }
                             )
                         } else {
                             if (tool.registryId == StudioTool.SIZE_OPACITY_TOOL_ID) {
                                 val currentSizeVal by viewModel.brushSize.collectAsState()
                                 DynamicSizeButton(
                                     onClick = {
                                         if (isEditMode) toolPickerTarget = ToolLocation.LeftBar to idx
                                         else showSizeOpacityPopup = true
                                     },
                                     brushSize = currentSizeVal,
                                     isActive = resolveIsActive(tool),
                                     isEditMode = isEditMode,
                                     backgroundColorOverride = if (tool.isPlaceholder) Color.Red.copy(alpha = 0.3f) else null,
                                     highlightColor = theme.highlightColor,
                                     buttonColor = theme.buttonColor,
                                     iconColor = theme.iconColor,
                                     shape = theme.floatingShape()
                                 )
                             } else if (tool.isPlaceholder || tool.registryId.contains("zoom") || tool.registryId == "home_view") {
                                  val isActionButton = resolveIsActionButton(tool); val isRealAction = !tool.isPlaceholder || isActionButton
                                  val bgColor = if (isActionButton) null else androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.3f)
                                  com.sketcher.sketchercompanionv1.ui.components.SketcherIconButton(
                                      onClick = {
                                          if (isEditMode) toolPickerTarget = ToolLocation.LeftBar to idx
                                          else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) showStabilizationPopup = true
                                          else if (isRealAction) tool.onClick()
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
                                      iconSize = scaler.smallIconSize
                                  )
                             } else {
                                     com.sketcher.sketchercompanionv1.ui.components.AssignableToolButton(
                                         onClick = {
                                             if (isEditMode) toolPickerTarget = ToolLocation.LeftBar to idx
                                             else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) showStabilizationPopup = true
                                             else if (isRealAction) tool.onClick()
                                         },
                                         onLongClick = {
                                             if (!isEditMode) {
                                                 val p = assignedToolsMap[tool.id]
                                                 if (p != null) viewModel.editTool(p)
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
                                          location = ToolLocation.LeftBar,
                                          theme = theme,
                                          payload = assignedToolsMap[tool.id],
                                          colorPreview = when (assignedToolsMap[tool.id]) {
                                              ToolPayload.STROKE_COLOR -> Color(strokeColorVal)
                                              ToolPayload.FILL_COLOR -> Color(fillColorVal)
                                              else -> assignedColorsMap[tool.id]?.let { Color(it) }
                                          },
                                          isSelected = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && isStrokeActiveVal) ||
                                                       (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && isFillActiveVal),
                                       isNone = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && !isStrokeActiveVal) ||
                                                (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && !isFillActiveVal),
                                           subTools = if (!isEditMode) com.sketcher.sketchercompanionv1.ui.model.ToolRegistry.getSubToolsFor(tool.registryId) else emptyList(),
                                           onSubToolClick = { subTool -> 
                                               viewModel.replaceTool(ToolLocation.LeftBar, idx, subTool)
                                               viewModel.getActionForTool(subTool.id).invoke() 
                                           }
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
                    .glassmorphicBackground(theme, theme.floatingShape())
            ) {
                 Column(
                    modifier = Modifier.padding(vertical = scaler.smallMargin),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    rightTools.forEachIndexed { idx, tool ->
                         val isActionButton = resolveIsActionButton(tool)
                         val isRealAction = !tool.isPlaceholder || isActionButton
                         if (tool.id == "divider") {
                             Box(
                                 modifier = Modifier
                                     .height(2.dp)
                                     .width(24.dp)
                                     .background(theme.iconColor.copy(alpha = 0.3f))
                                     .clickable { if (isEditMode) toolPickerTarget = ToolLocation.RightBar to idx }
                             )
                         } else {
                             if (tool.registryId == StudioTool.SIZE_OPACITY_TOOL_ID) {
                                 val currentSizeVal by viewModel.brushSize.collectAsState()
                                 DynamicSizeButton(
                                     onClick = {
                                         if (isEditMode) toolPickerTarget = ToolLocation.RightBar to idx
                                         else showSizeOpacityPopup = true
                                     },
                                     brushSize = currentSizeVal,
                                     isActive = resolveIsActive(tool),
                                     isEditMode = isEditMode,
                                     backgroundColorOverride = if (tool.isPlaceholder) Color.Red.copy(alpha = 0.3f) else null,
                                     highlightColor = theme.highlightColor,
                                     buttonColor = theme.buttonColor,
                                     iconColor = theme.iconColor,
                                     shape = theme.floatingShape()
                                 )
                             } else if (tool.isPlaceholder || tool.registryId.contains("zoom") || tool.registryId == "home_view") {
                                  val isActionButton = resolveIsActionButton(tool); val isRealAction = !tool.isPlaceholder || isActionButton
                                  val bgColor = if (isActionButton) null else androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.3f)
                                  com.sketcher.sketchercompanionv1.ui.components.SketcherIconButton(
                                      onClick = {
                                          if (isEditMode) toolPickerTarget = ToolLocation.RightBar to idx
                                          else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) showStabilizationPopup = true
                                          else if (isRealAction) tool.onClick()
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
                                      iconSize = scaler.smallIconSize
                                  )
                             } else {
                                 com.sketcher.sketchercompanionv1.ui.components.AssignableToolButton(
                                     onClick = {
                                         if (isEditMode) toolPickerTarget = ToolLocation.RightBar to idx
                                         else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) showStabilizationPopup = true
                                         else if (isRealAction) tool.onClick()
                                     },
                                     onLongClick = {
                                         if (!isEditMode) {
                                             val p = assignedToolsMap[tool.id]
                                             if (p != null) viewModel.editTool(p)
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
                                      location = ToolLocation.RightBar,
                                      theme = theme,
                                      payload = assignedToolsMap[tool.id],
                                       colorPreview = when (assignedToolsMap[tool.id]) {
                                           ToolPayload.STROKE_COLOR -> Color(strokeColorVal)
                                           ToolPayload.FILL_COLOR -> Color(fillColorVal)
                                           else -> assignedColorsMap[tool.id]?.let { Color(it) }
                                       },
                                       isSelected = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && isStrokeActiveVal) ||
                                                    (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && isFillActiveVal),
                                       isNone = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && !isStrokeActiveVal) ||
                                                (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && !isFillActiveVal),
                                      subTools = if (!isEditMode) com.sketcher.sketchercompanionv1.ui.model.ToolRegistry.getSubToolsFor(tool.registryId) else emptyList(),
                                      onSubToolClick = { subTool -> 
                                          viewModel.replaceTool(ToolLocation.RightBar, idx, subTool)
                                          viewModel.getActionForTool(subTool.id).invoke() 
                                      }
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
                    .align(if (swapVertical) Alignment.BottomCenter else Alignment.TopCenter)
                    .advancedShadow(
                        color = Color.Black,
                        alpha = shadowAlpha,
                        cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                        shadowBlurRadius = shadowBlur,
                        offsetX = shadowOffsetX,
                        offsetY = shadowOffsetY
                    )
                    .glassmorphicBackground(theme, theme.floatingShape())
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = scaler.smallMargin, vertical = 4.dp), 
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    topTools.forEachIndexed { idx, tool ->
                         val isActionButton = resolveIsActionButton(tool)
                         val isRealAction = !tool.isPlaceholder || isActionButton
                         if (tool.id == "divider") {
                             Box(
                                 modifier = Modifier
                                     .width(2.dp)
                                     .height(24.dp)
                                     .background(theme.iconColor.copy(alpha = 0.3f))
                                     .clickable { if (isEditMode) toolPickerTarget = ToolLocation.TopBar to idx }
                             )
                         } else {
                             if (tool.registryId == StudioTool.SIZE_OPACITY_TOOL_ID) {
                                 val currentSizeVal by viewModel.brushSize.collectAsState()
                                 DynamicSizeButton(
                                     onClick = {
                                         if (isEditMode) toolPickerTarget = ToolLocation.TopBar to idx
                                         else showSizeOpacityPopup = true
                                     },
                                     brushSize = currentSizeVal,
                                     isActive = resolveIsActive(tool),
                                     isEditMode = isEditMode,
                                     backgroundColorOverride = if (tool.isPlaceholder) Color.Red.copy(alpha = 0.3f) else null,
                                     highlightColor = theme.highlightColor,
                                     buttonColor = theme.buttonColor,
                                     iconColor = theme.iconColor,
                                     shape = theme.floatingShape()
                                 )
                             } else if (tool.isPlaceholder || tool.registryId.contains("zoom") || tool.registryId == "home_view") {
                                  val isActionButton = resolveIsActionButton(tool); val isRealAction = !tool.isPlaceholder || isActionButton
                                  val bgColor = if (isActionButton) null else androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.3f)
                                  com.sketcher.sketchercompanionv1.ui.components.SketcherIconButton(
                                      onClick = {
                                          if (isEditMode) toolPickerTarget = ToolLocation.TopBar to idx
                                          else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) showStabilizationPopup = true
                                          else if (isRealAction) tool.onClick()
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
                                      iconSize = scaler.smallIconSize
                                  )
                             } else {
                                 com.sketcher.sketchercompanionv1.ui.components.AssignableToolButton(
                                     onClick = {
                                         if (isEditMode) toolPickerTarget = ToolLocation.TopBar to idx
                                         else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) showStabilizationPopup = true
                                         else if (isRealAction) tool.onClick()
                                     },
                                     onLongClick = {
                                         if (!isEditMode) {
                                             val p = assignedToolsMap[tool.id]
                                             if (p != null) viewModel.editTool(p)
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
                                      location = ToolLocation.TopBar,
                                      theme = theme,
                                      payload = assignedToolsMap[tool.id],
                                       colorPreview = when (assignedToolsMap[tool.id]) {
                                           ToolPayload.STROKE_COLOR -> Color(strokeColorVal)
                                           ToolPayload.FILL_COLOR -> Color(fillColorVal)
                                           else -> assignedColorsMap[tool.id]?.let { Color(it) }
                                       },
                                       isSelected = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && isStrokeActiveVal) ||
                                                    (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && isFillActiveVal),
                                       isNone = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && !isStrokeActiveVal) ||
                                                (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && !isFillActiveVal),
                                      subTools = if (!isEditMode) com.sketcher.sketchercompanionv1.ui.model.ToolRegistry.getSubToolsFor(tool.registryId) else emptyList(),
                                      onSubToolClick = { subTool -> 
                                          viewModel.replaceTool(ToolLocation.TopBar, idx, subTool)
                                          viewModel.getActionForTool(subTool.id).invoke() 
                                      }
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
                    .align(if (swapVertical) Alignment.TopCenter else Alignment.BottomCenter)
                    .advancedShadow(
                        color = Color.Black,
                        alpha = shadowAlpha,
                        cornersRadius = if (theme.isRound) (scaler.floatingBarWidth / 2) else 8.dp, 
                        shadowBlurRadius = shadowBlur,
                        offsetX = shadowOffsetX,
                        offsetY = shadowOffsetY
                    )
                    .glassmorphicBackground(theme, theme.floatingShape())
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = scaler.smallMargin, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    bottomTools.forEachIndexed { idx, tool ->
                         val isActionButton = resolveIsActionButton(tool)
                         val isRealAction = !tool.isPlaceholder || isActionButton
                         if (tool.id == "divider") {
                             Box(
                                 modifier = Modifier
                                     .width(2.dp)
                                     .height(24.dp)
                                     .background(theme.iconColor.copy(alpha = 0.3f))
                                     .clickable { if (isEditMode) toolPickerTarget = ToolLocation.BottomBar to idx }
                             )
                         } else {
                             if (tool.registryId == StudioTool.SIZE_OPACITY_TOOL_ID) {
                                 val currentSizeVal by viewModel.brushSize.collectAsState()
                                 DynamicSizeButton(
                                     onClick = {
                                         if (isEditMode) toolPickerTarget = ToolLocation.BottomBar to idx
                                         else showSizeOpacityPopup = true
                                     },
                                     brushSize = currentSizeVal,
                                     isActive = resolveIsActive(tool),
                                     isEditMode = isEditMode,
                                     backgroundColorOverride = if (tool.isPlaceholder) Color.Red.copy(alpha = 0.3f) else null,
                                     highlightColor = theme.highlightColor,
                                     buttonColor = theme.buttonColor,
                                     iconColor = theme.iconColor,
                                     shape = theme.floatingShape()
                                 )
                             } else if (tool.isPlaceholder || tool.registryId.contains("zoom") || tool.registryId == "home_view") {
                                  val isActionButton = resolveIsActionButton(tool); val isRealAction = !tool.isPlaceholder || isActionButton
                                  val bgColor = if (isActionButton) null else androidx.compose.ui.graphics.Color.Red.copy(alpha = 0.3f)
                                  com.sketcher.sketchercompanionv1.ui.components.SketcherIconButton(
                                      onClick = {
                                          if (isEditMode) toolPickerTarget = ToolLocation.BottomBar to idx
                                          else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) showStabilizationPopup = true
                                          else if (isRealAction) tool.onClick()
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
                                      iconSize = scaler.smallIconSize
                                  )
                             } else {
                                 com.sketcher.sketchercompanionv1.ui.components.AssignableToolButton(
                                     onClick = {
                                         if (isEditMode) toolPickerTarget = ToolLocation.BottomBar to idx
                                         else if (tool.registryId == StudioTool.STABILIZATION_TOOL_ID) showStabilizationPopup = true
                                         else if (isRealAction) tool.onClick()
                                     },
                                     onLongClick = {
                                         if (!isEditMode) {
                                             val p = assignedToolsMap[tool.id]
                                             if (p != null) viewModel.editTool(p)
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
                                      location = ToolLocation.BottomBar,
                                      theme = theme,
                                      payload = assignedToolsMap[tool.id],
                                       colorPreview = when (assignedToolsMap[tool.id]) {
                                           ToolPayload.STROKE_COLOR -> Color(strokeColorVal)
                                           ToolPayload.FILL_COLOR -> Color(fillColorVal)
                                           else -> assignedColorsMap[tool.id]?.let { Color(it) }
                                       },
                                       isSelected = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && isStrokeActiveVal) ||
                                                    (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && isFillActiveVal),
                                       isNone = (assignedToolsMap[tool.id] == ToolPayload.STROKE_COLOR && !isStrokeActiveVal) ||
                                                (assignedToolsMap[tool.id] == ToolPayload.FILL_COLOR && !isFillActiveVal),
                                      subTools = if (!isEditMode) com.sketcher.sketchercompanionv1.ui.model.ToolRegistry.getSubToolsFor(tool.registryId) else emptyList(),
                                      onSubToolClick = { subTool -> 
                                          viewModel.replaceTool(ToolLocation.BottomBar, idx, subTool)
                                          viewModel.getActionForTool(subTool.id).invoke() 
                                      }
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

        // --- CONTEXT ACTION BAR ---
        val isContextSelectionActive = viewModel.hasSelection
        val isContextBarVisible = isContextSelectionActive || viewModel.currentTool == ToolType.SELECTION
        val rawContextTools by viewModel.contextualToolbar.collectAsState(initial = emptyList())
        val isTransformMode = viewModel.currentSelectionMode == com.sketcher.sketchercompanionv1.SketcherViewModel.SelectionMode.TRANSFORM_BOX
        val contextTools = if (isTransformMode) {
            listOf(
                StudioTool(
                    id = "context_cancel_transform",
                    icon = Icons.Default.Close,
                    contentDescription = "Cancelar",
                    isPlaceholder = false,
                    onClick = { viewModel.cancelTransform() }
                ),
                StudioTool(
                    id = "context_confirm_transform",
                    icon = Icons.Default.Check,
                    contentDescription = "Confirmar",
                    isPlaceholder = false,
                    onClick = { viewModel.confirmTransform() }
                )
            )
        } else {
            rawContextTools
        }
        
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (if (swapVertical) animTopOffset else animBottomOffset) + scaler.floatingBarWidth + scaler.margin)
        ) {
            ContextActionBar(
                modifier = Modifier.advancedShadow(
                    color = Color.Black,
                    alpha = shadowAlpha,
                    cornersRadius = scaler.baseBarHeight / 2,
                    shadowBlurRadius = shadowBlur,
                    offsetX = shadowOffsetX,
                    offsetY = shadowOffsetY
                ),
                tools = contextTools,
                isVisible = isContextBarVisible,
                isEditMode = isEditMode,
                theme = theme,
                onToolClick = { index, tool ->
                    if (isEditMode) {
                        val isNew = tool == null
                        toolPickerTarget = Pair(ToolLocation.ContextBar, if (isNew) null else index)
                    } else {
                        tool?.onClick?.invoke()
                    }
                }
            )
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



        if (viewModel.showPropertiesPanel) {
            Dialog(onDismissRequest = { viewModel.togglePropertiesPanel() }) {
                ToolPropertiesPanel(viewModel = viewModel, onDismiss = { viewModel.togglePropertiesPanel() })
            }
        }

        // --- QUICK STABILIZATION POPUP ---
        if (showStabilizationPopup) {
            val currentSmoothing by viewModel.smoothing.collectAsState()
            QuickSmoothingPopup(
                value = currentSmoothing,
                onValueChange = { viewModel.updateSmoothing(it) },
                onDismiss = { showStabilizationPopup = false },
                theme = theme
            )
        }

        // --- BRUSH SIZE & OPACITY POPUP ---
        if (showSizeOpacityPopup) {
            SizeOpacityPopup(
                viewModel = viewModel,
                onDismiss = { showSizeOpacityPopup = false },
                theme = theme
            )
        }

        // --- NEW INDEPENDENT COLOR DIALOGS ---
        if (showStrokeColorPicker) {
            ColorPickerDialog(
                initialColor = Color(strokeColorVal),
                recentColors = theme.recentColors,
                onColorSelected = { 
                    viewModel.setStrokeColor(it.toArgb())
                    viewModel.updateLastActiveToolColor(it.toArgb())
                    viewModel.setShowStrokeColorPicker(false) 
                },
                onDismiss = { viewModel.setShowStrokeColorPicker(false) },
                onDisable = { viewModel.toggleStroke(false); viewModel.setShowStrokeColorPicker(false) }
            )
        }

        if (showFillColorPicker) {
            ColorPickerDialog(
                initialColor = Color(fillColorVal),
                recentColors = theme.recentColors,
                onColorSelected = { 
                    viewModel.setFillColor(it.toArgb())
                    viewModel.updateLastActiveToolColor(it.toArgb())
                    viewModel.setShowFillColorPicker(false) 
                },
                onDismiss = { viewModel.setShowFillColorPicker(false) },
                onDisable = { viewModel.toggleFill(false); viewModel.setShowFillColorPicker(false) }
            )
        }

        if (viewModel.showPerformanceStats) {
            val topBarOffset = if (swapVertical) animBottomOffset else animTopOffset
            PerformanceStatsOverlay(
                viewModel = viewModel,
                canvasView = canvasViewRef.value,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = topBarOffset + scaler.baseBarHeight + 8.dp)
            )
        }
    }

    if (showPersonalizationDialog) {
        Dialog(onDismissRequest = { showPersonalizationDialog = false }) {
             Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                    contentColor = theme.iconColor
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
                modifier = Modifier.padding(16.dp).width(300.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
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
                            Text("Shadow Angle: ${theme.shadowAngle.toInt()}Â°", style = MaterialTheme.typography.labelMedium, color = theme.iconColor)
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

                    // --- INTERFACE MIRROR ---
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(theme.buttonColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Column {
                            Text(
                                "Interface Mirror",
                                style = MaterialTheme.typography.labelSmall,
                                color = theme.iconColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Swap Vertical", color = theme.iconColor)
                                Switch(
                                    checked = swapVertical,
                                    onCheckedChange = { viewModel.toggleSwapVertical() }
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Swap Horizontal", color = theme.iconColor)
                                Switch(
                                    checked = swapHorizontal,
                                    onCheckedChange = { viewModel.toggleSwapHorizontal() }
                                )
                            }
                        }
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

    if (showStudioMenu) {
        com.sketcher.sketchercompanionv1.ui.dialogs.StudioMenuDialog(
            viewModel = viewModel,
            actions = projectActions,
            onDismiss = { showStudioMenu = false }
        )
    }
}

fun Modifier.glassmorphicBackground(
    theme: UiThemeConfig,
    shape: androidx.compose.ui.graphics.Shape
): Modifier = this.background(theme.barBackgroundColor, shape)

@Composable
fun PerformanceStatsOverlay(
    viewModel: SketcherViewModel,
    canvasView: SketcherCanvasView?,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.themeConfig.collectAsState()
    val fps = canvasView?.fpsState?.value ?: 0
    val lastRedraw = canvasView?.lastRedrawTimeMs?.value ?: 0L
    val strokeCount = viewModel.strokeCount
    val pointCount = viewModel.pointCount
    val livePoints = canvasView?.getLiveStrokePoints()?.size ?: 0

    Card(
        modifier = modifier
            .width(180.dp)
            .shadow(6.dp, RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.8f),
            contentColor = Color.White
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Rendimiento",
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = theme.highlightColor
                )
                Text(
                    text = "$fps FPS",
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = when {
                        fps >= 55 -> Color.Green
                        fps >= 30 -> Color.Yellow
                        else -> Color.Red
                    }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Color.White.copy(alpha = 0.15f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Trazos:", fontSize = 9.sp, color = Color.LightGray)
                Text(text = "$strokeCount", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Puntos Totales:", fontSize = 9.sp, color = Color.LightGray)
                Text(text = "$pointCount", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
            if (livePoints > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Puntos Activos:", fontSize = 9.sp, color = theme.highlightColor)
                    Text(text = "$livePoints", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = theme.highlightColor)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Cache Bake:", fontSize = 9.sp, color = Color.LightGray)
                Text(text = "${lastRedraw}ms", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            }
        }
    }
}


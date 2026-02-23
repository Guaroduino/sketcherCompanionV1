package com.sketcher.sketchercompanionv1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.SideEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.platform.LocalConfiguration
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfImportDialog
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfExportDialog
import com.sketcher.sketchercompanionv1.ui.dialogs.StudioMenuDialog
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                androidx.compose.runtime.SideEffect {
                    val window = (this@MainActivity).window
                    androidx.core.view.WindowCompat.getInsetsController(window, view)?.let { controller ->
                        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            
            val sketchViewModel: SketcherViewModel = viewModel()
            val context = LocalContext.current
            
            val interfaceScale by androidx.compose.runtime.remember { 
                androidx.compose.runtime.derivedStateOf { sketchViewModel.interfaceScale } 
            }
            
            // HOISTED STATE
            var uiCollapsed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            
            // SWAP STATES (Hoisted)
            var swapVertical by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var swapHorizontal by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            // DIALOG STATES (Hoisted)
            var showSettingsPopup by remember { mutableStateOf(false) }
            var showPaperSizeDialog by remember { mutableStateOf(false) }
            var showGridSettings by remember { mutableStateOf(false) }
            var showSaveTemplateDialog by remember { mutableStateOf(false) }
            var showLoadTemplateDialog by remember { mutableStateOf(false) }
            var showDxfImportDialog by remember { mutableStateOf(false) }
            var dxfImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var showDxfExportDialog by remember { mutableStateOf(false) }
            var showExportPngDialog by remember { mutableStateOf(false) }
            var showExportSvgDialog by remember { mutableStateOf(false) }
            var showPdfExportDialog by remember { mutableStateOf(false) }

            // SAF LAUNCHERS (Hoisted)
            val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
                uri?.let { sketchViewModel.saveProjectToZip(context, it) }
            }
            val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { sketchViewModel.loadProjectFromZip(context, it) }
            }
            val importImageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let { sketchViewModel.insertImage(context, it) }
            }
            val importSvgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                uri?.let { sketchViewModel.insertSvg(context, it) }
            }
            val dxfImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { dxfImportUri = it; showDxfImportDialog = true }
            }
            val exportPngLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")) { uri ->
                uri?.let { sketchViewModel.exportPng(context, it, sketchViewModel.lastExportPngConfig) }
            }
            val exportSvgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/svg+xml")) { uri ->
                uri?.let { sketchViewModel.exportSvg(context, it, sketchViewModel.lastExportSvgConfig) }
            }
            val exportPdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
                uri?.let { sketchViewModel.exportPdf(context, it) }
            }
            val dxfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/dxf")) { uri ->
                uri?.let { sketchViewModel.exportDxf(context, it) }
            }

            val projectActions = remember {
                com.sketcher.sketchercompanionv1.ui.model.ProjectActions(
                    onNew = { sketchViewModel.clear() },
                    onSave = { saveLauncher.launch("sketch.zip") },
                    onLoad = { loadLauncher.launch(arrayOf("application/zip", "application/octet-stream")) },
                    onImportImage = { importImageLauncher.launch("image/*") },
                    onImportSvg = { importSvgLauncher.launch("*/*") },
                    onImportDxf = { dxfImportLauncher.launch(arrayOf("*/*")) },
                    onExportPng = { showExportPngDialog = true },
                    onExportSvg = { showExportSvgDialog = true },
                    onExportPdf = { 
                        if (sketchViewModel.canvasSizeConfig != null) exportPdfLauncher.launch("drawing.pdf")
                        else showPdfExportDialog = true
                    },
                    onExportDxf = { showDxfExportDialog = true },
                    onPaperSize = { showPaperSizeDialog = true },
                    onGridSettings = { showGridSettings = true },
                    onTemplatesSaveTrigger = { showSaveTemplateDialog = true },
                    onTemplatesLoadTrigger = { showLoadTemplateDialog = true },
                    onTemplatesSave = { name -> sketchViewModel.saveTemplate(context, name) },
                    onTemplatesLoad = { file -> sketchViewModel.loadFromTemplate(context, file) },
                    onSettings = { showSettingsPopup = true },
                    onZoomFit = { sketchViewModel.fitContent() }
                )
            }
  
            // UI SCALER PROVIDER
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val screenHeight = configuration.screenHeightDp.dp
            
            val scaler = remember(interfaceScale, screenWidth, screenHeight) {
                com.sketcher.sketchercompanionv1.ui.theme.UiScaler(interfaceScale, screenWidth, screenHeight)
            }
  
            androidx.compose.runtime.CompositionLocalProvider(
                com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler provides scaler
            ) {
                com.sketcher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme {
                    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        // 1. MAIN UI LAYER
                        com.sketcher.sketchercompanionv1.ui.layout.StudioLayout(
                            viewModel = sketchViewModel,
                            uiCollapsed = uiCollapsed,
                            onToggleUi = { uiCollapsed = !uiCollapsed },
                            swapVertical = swapVertical,
                            swapHorizontal = swapHorizontal,
                            projectActions = projectActions
                        )
                        
                        // 2. DEBUG OVERLAY (Persistent)
                        com.sketcher.sketchercompanionv1.ui.layout.DebugOverlay(
                            userScale = interfaceScale,
                            onScaleChange = { sketchViewModel.updateInterfaceScale(it) },
                            uiCollapsed = uiCollapsed,
                            onToggleUi = { uiCollapsed = !uiCollapsed },
                            swapVertical = swapVertical,
                            swapHorizontal = swapHorizontal,
                            onToggleSwapVertical = { swapVertical = !swapVertical },
                            onToggleSwapHorizontal = { swapHorizontal = !swapHorizontal }
                        )

                        // 3. COMMON DIALOGS
                        if (showSettingsPopup) {
                            SettingsDialog(
                                onDismiss = { showSettingsPopup = false },
                                isRotationLocked = sketchViewModel.isRotationLocked,
                                onToggleRotationLock = { sketchViewModel.toggleRotationLock() },
                                isPalmRejectionEnabled = sketchViewModel.isPalmRejectionEnabled,
                                onTogglePalmRejection = { sketchViewModel.togglePalmRejection() },
                                interfaceScale = sketchViewModel.interfaceScale,
                                onInterfaceScaleChanged = { scale -> sketchViewModel.updateInterfaceScale(scale) },
                                isDebugWireframe = sketchViewModel.isDebugWireframe,
                                onToggleDebugWireframe = { sketchViewModel.isDebugWireframe = !sketchViewModel.isDebugWireframe },
                                currentScaleConfig = sketchViewModel.scaleConfig,
                                onUpdateProjectConfig = { unit, resolution -> 
                                    sketchViewModel.updateScaleConfig(unit, resolution)
                                },
                                toolbarBackgroundColor = sketchViewModel.toolbarBackgroundColor,
                                onToolbarBackgroundColorChanged = { color -> sketchViewModel.updateToolbarBackgroundColor(color) },
                                toolbarAlpha = sketchViewModel.toolbarAlpha,
                                onToolbarAlphaChanged = { alpha -> sketchViewModel.updateToolbarAlpha(alpha) },
                                isToolbarBlurEnabled = sketchViewModel.isToolbarBlurEnabled,
                                onToggleToolbarBlur = { sketchViewModel.toggleToolbarBlur() },
                                showTooltips = sketchViewModel.showTooltips,
                                onToggleTooltips = { sketchViewModel.toggleTooltips() }
                            )
                        }

                        if (showPaperSizeDialog) {
                            com.sketcher.sketchercompanionv1.ui.PaperSizeDialog(
                                currentConfig = sketchViewModel.canvasSizeConfig,
                                onConfirm = { config -> 
                                    sketchViewModel.updateCanvasSize(config)
                                    showPaperSizeDialog = false
                                },
                                onDismiss = { showPaperSizeDialog = false }
                            )
                        }

                        if (showGridSettings) {
                            GridSettingsDialog(
                                currentGridConfig = sketchViewModel.gridConfig,
                                isSnapEnabled = sketchViewModel.isSnapToGridEnabled,
                                currentUnit = sketchViewModel.currentUnit,
                                onUpdateGrid = { visible, spacing, c1, c2, c3 ->
                                    sketchViewModel.gridConfig = sketchViewModel.gridConfig.copy(
                                        isVisible = visible,
                                        spacing = spacing,
                                        color = c1,
                                        secondaryColor = c2,
                                        tertiaryColor = c3
                                    )
                                },
                                onUpdateSnap = { sketchViewModel.isSnapToGridEnabled = it },
                                onUpdateUnit = { sketchViewModel.currentUnit = it },
                                onDismiss = { showGridSettings = false }
                            )
                        }

                        if (showSaveTemplateDialog) {
                            com.sketcher.sketchercompanionv1.ui.SaveTemplateDialog(
                                onDismiss = { showSaveTemplateDialog = false },
                                onSave = { name -> 
                                    projectActions.onTemplatesSave(name)
                                    showSaveTemplateDialog = false
                                }
                            )
                        }

                        if (showLoadTemplateDialog) {
                            com.sketcher.sketchercompanionv1.ui.LoadTemplateDialog(
                                onDismiss = { showLoadTemplateDialog = false },
                                onTemplateSelected = { file ->
                                    projectActions.onTemplatesLoad(file)
                                    showLoadTemplateDialog = false
                                }
                            )
                        }

                        if (showDxfImportDialog && dxfImportUri != null) {
                            com.sketcher.sketchercompanionv1.ui.dialogs.DxfImportDialog(
                                uri = dxfImportUri!!,
                                onDismiss = { showDxfImportDialog = false },
                                onImport = { data, scaleToFit, defaultStrokeWidth, fillClosedShapes ->
                                    sketchViewModel.addImportedDxfData(data, scaleToFit, defaultStrokeWidth, fillClosedShapes)
                                    showDxfImportDialog = false
                                }
                            )
                        }

                        if (showDxfExportDialog) {
                            com.sketcher.sketchercompanionv1.ui.dialogs.DxfExportDialog(
                                onDismiss = { showDxfExportDialog = false },
                                onExport = { filename, _ ->
                                    dxfExportLauncher.launch(filename)
                                    showDxfExportDialog = false
                                }
                            )
                        }

                        if (showExportPngDialog) {
                            ExportPngDialog(
                                viewModel = sketchViewModel,
                                onExport = { config ->
                                    sketchViewModel.lastExportPngConfig = config
                                    exportPngLauncher.launch("drawing.png")
                                    showExportPngDialog = false
                                },
                                onDismiss = { showExportPngDialog = false }
                            )
                        }

                        if (showExportSvgDialog) {
                            ExportSvgDialog(
                                viewModel = sketchViewModel,
                                onExport = { config ->
                                    sketchViewModel.lastExportSvgConfig = config
                                    exportSvgLauncher.launch("drawing.svg")
                                    showExportSvgDialog = false
                                },
                                onDismiss = { showExportSvgDialog = false }
                            )
                        }

                        if (showPdfExportDialog) {
                            com.sketcher.sketchercompanionv1.ui.PdfExportDialog(
                                onDismiss = { showPdfExportDialog = false },
                                onConfirm = { useZoomExtends ->
                                     sketchViewModel.setPdfExportBoundsMode(useZoomExtends)
                                     exportPdfLauncher.launch("drawing.pdf")
                                     showPdfExportDialog = false
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.sketcher.sketchercompanionv1.utils.UpdateManager
import com.sketcher.sketchercompanionv1.utils.UpdateInfo
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfImportDialog
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfExportDialog
import com.sketcher.sketchercompanionv1.dto.FillStyle
import java.io.File
import android.print.PrintManager
import android.widget.Toast
import com.sketcher.sketchercompanionv1.utils.PdfPrintAdapter
import com.sketcher.sketchercompanionv1.utils.PdfExporter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
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

            // Wireless Projection Manager initialization & lifecycle binding
            val wirelessProjectionManager = remember { com.sketcher.sketchercompanionv1.projection.WirelessProjectionManager(context) }
            sketchViewModel.wirelessProjectionManager = wirelessProjectionManager

            val isWirelessProjectionActive = sketchViewModel.isWirelessProjectionActive
            LaunchedEffect(isWirelessProjectionActive) {
                if (isWirelessProjectionActive) {
                    wirelessProjectionManager.start()
                } else {
                    wirelessProjectionManager.stop()
                }
            }

            DisposableEffect(Unit) {
                onDispose {
                    wirelessProjectionManager.stop()
                    sketchViewModel.wirelessProjectionManager = null
                }
            }
            
            // Check for updates
            var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
            var showUpdateDialog by remember { mutableStateOf(false) }
            var isDownloadingUpdate by remember { mutableStateOf(false) }
            var downloadProgress by remember { mutableStateOf(0f) }
            val updateManager = remember { UpdateManager(context) }
            val coroutineScope = rememberCoroutineScope()
            
            LaunchedEffect(Unit) {
                // Copy default textures from assets to local storage in background
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.sketcher.sketchercompanionv1.utils.ImageTextureCache.init(context)
                    com.sketcher.sketchercompanionv1.utils.ImageTextureCache.copyDefaultTexturesFromAssets(context)
                }

                try {
                    val info = updateManager.checkForUpdates()
                    if (info != null) {
                        updateInfo = info
                        showUpdateDialog = true
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            
            val interfaceScale by androidx.compose.runtime.remember { 
                androidx.compose.runtime.derivedStateOf { sketchViewModel.interfaceScale } 
            }
            
            // Check autosave on launch
            var showAutosaveRestoreDialog by remember { mutableStateOf(false) }
            val autosaveFile = java.io.File(context.cacheDir, "autosave.skc")
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (autosaveFile.exists() && sketchViewModel.currentFileUri == null) {
                    showAutosaveRestoreDialog = true
                }
            }
            
            if (showAutosaveRestoreDialog) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showAutosaveRestoreDialog = false },
                    title = { androidx.compose.material3.Text("Restaurar Autoguardado") },
                    text = { androidx.compose.material3.Text("Se encontró un archivo de autoguardado. ¿Deseas restaurarlo?") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { 
                            sketchViewModel.loadProjectFromZip(context, android.net.Uri.fromFile(autosaveFile))
                            sketchViewModel.showDashboard = false
                            showAutosaveRestoreDialog = false
                        }) { androidx.compose.material3.Text("Restaurar") }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { 
                            autosaveFile.delete()
                            showAutosaveRestoreDialog = false
                        }) { androidx.compose.material3.Text("Descartar") }
                    }
                )
            }

            // Update Dialog
            if (showUpdateDialog) {
                updateInfo?.let { info ->
                    androidx.compose.material3.AlertDialog(
                        onDismissRequest = { 
                            if (!info.forceUpdate && !isDownloadingUpdate) {
                                showUpdateDialog = false 
                            }
                        },
                        title = { androidx.compose.material3.Text("Actualización Disponible (v${info.versionName})") },
                        text = {
                            Column {
                                androidx.compose.material3.Text(info.releaseNotes)
                                if (isDownloadingUpdate) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    androidx.compose.material3.LinearProgressIndicator(
                                        progress = { downloadProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    androidx.compose.material3.Text(
                                        text = "Descargando: ${(downloadProgress * 100).toInt()}%",
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            if (!isDownloadingUpdate) {
                                androidx.compose.material3.TextButton(onClick = {
                                    isDownloadingUpdate = true
                                    coroutineScope.launch {
                                        val file = updateManager.downloadApk(info.apkUrl) { progress ->
                                            downloadProgress = progress
                                        }
                                        isDownloadingUpdate = false
                                        if (file != null) {
                                            updateManager.installApk(file)
                                            showUpdateDialog = false
                                        } else {
                                            android.widget.Toast.makeText(context, "Error al descargar la actualización", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }) {
                                    androidx.compose.material3.Text("Descargar e Instalar")
                                }
                            }
                        },
                        dismissButton = {
                            if (!info.forceUpdate && !isDownloadingUpdate) {
                                androidx.compose.material3.TextButton(onClick = { showUpdateDialog = false }) {
                                    androidx.compose.material3.Text("Más Tarde")
                                }
                            }
                        }
                    )
                }
            }
            
            // HOISTED STATE
            var uiCollapsed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            val theme by sketchViewModel.themeConfig.collectAsState()
            
            val authViewModel: com.sketcher.sketchercompanionv1.ui.auth.AuthViewModel = viewModel()
            val currentUser by authViewModel.currentUser.collectAsState()
            var skipLogin by remember { mutableStateOf(false) }

            if (currentUser == null && !skipLogin) {
                com.sketcher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme(
                    theme = theme
                ) {
                    com.sketcher.sketchercompanionv1.ui.auth.LoginScreen(
                        theme = theme,
                        onLoginSuccess = { skipLogin = false },
                        onSkipLogin = { skipLogin = true },
                        viewModel = authViewModel
                    )
                }
                return@setContent
            }
            
            LaunchedEffect(currentUser) {
                sketchViewModel.initLocalProjects(context)
                if (currentUser != null) {
                    sketchViewModel.autoSyncCloud(context)
                }
            }
            
            // SWAP STATES (now backed by ViewModel with persistence)
            val swapVertical = sketchViewModel.swapVertical
            val swapHorizontal = sketchViewModel.swapHorizontal

            // DIALOG STATES (Hoisted)
            var showSettingsPopup by remember { mutableStateOf(false) }
            var showPaperSizeDialog by remember { mutableStateOf(false) }
            var showGridSettings by remember { mutableStateOf(false) }
            var showSaveTemplateDialog by remember { mutableStateOf(false) }
            var showSaveProjectDialog by remember { mutableStateOf(false) }
            var showLoadTemplateDialog by remember { mutableStateOf(false) }
            var showDxfImportDialog by remember { mutableStateOf(false) }
            var dxfImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var showPdfImportDialog by remember { mutableStateOf(false) }
            var pdfImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
            var pdfOriginalFileName by remember { mutableStateOf("document.pdf") }
            var showDxfExportDialog by remember { mutableStateOf(false) }
            var showExportPngDialog by remember { mutableStateOf(false) }
            var showExportSvgDialog by remember { mutableStateOf(false) }
            var showPdfExportDialog by remember { mutableStateOf(false) }
            var showPdfPrintDialog by remember { mutableStateOf(false) }
            var showGlobalScaleDialog by remember { mutableStateOf(false) }
            var showRenderBottomSheet by remember { mutableStateOf(false) }

            // SAF LAUNCHERS (Hoisted)
            val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
                uri?.let { sketchViewModel.saveProjectToZip(context, it) }
            }
            val loadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { 
                    if (!sketchViewModel.showDashboard) {
                        sketchViewModel.hasUnsavedChangesSinceLastAutosave = true
                        sketchViewModel.saveCurrentProjectLocal(context)
                    }
                    sketchViewModel.loadProjectFromZip(context, it) 
                }
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
            val pdfImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                uri?.let { 
                    pdfImportUri = it
                    pdfOriginalFileName = com.sketcher.sketchercompanionv1.utils.BitmapUtils.getFileNameFromUri(context, it) ?: "document.pdf"
                    showPdfImportDialog = true
                }
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
                    onNew = { 
                        sketchViewModel.hasUnsavedChangesSinceLastAutosave = true
                        sketchViewModel.saveCurrentProjectLocal(context)
                        sketchViewModel.clear() 
                    },
                    onSave = {
                        if (sketchViewModel.currentFileUri != null) {
                            sketchViewModel.saveProjectToZip(context, sketchViewModel.currentFileUri!!)
                        } else {
                            showSaveProjectDialog = true
                        }
                    },
                    onSaveAs = { showSaveProjectDialog = true },
                    onLoad = { loadLauncher.launch(arrayOf("*/*")) },
                    onImportImage = { importImageLauncher.launch("image/*") },
                    onImportSvg = { importSvgLauncher.launch("*/*") },
                    onImportDxf = { dxfImportLauncher.launch(arrayOf("*/*")) },
                    onImportPdf = { pdfImportLauncher.launch(arrayOf("application/pdf")) },
                    onExportPng = { showExportPngDialog = true },
                    onExportSvg = { showExportSvgDialog = true },
                    onExportPdf = { 
                        if (sketchViewModel.canvasSizeConfig != null) exportPdfLauncher.launch("drawing.pdf")
                        else showPdfExportDialog = true
                    },
                    onExportDxf = { showDxfExportDialog = true },
                    onPaperSize = { showPaperSizeDialog = true },
                    onGlobalScale = { showGlobalScaleDialog = true },
                    onGridSettings = { showGridSettings = true },
                    onTemplatesSaveTrigger = { showSaveTemplateDialog = true },
                    onTemplatesLoadTrigger = { showLoadTemplateDialog = true },
                    onTemplatesSave = { name -> sketchViewModel.saveTemplate(context, name) },
                    onTemplatesLoad = { file -> sketchViewModel.loadFromTemplate(context, file) },
                    onSettings = { showSettingsPopup = true },
                    onZoomFit = { sketchViewModel.fitContent() },
                    onRender = { showRenderBottomSheet = true },
                    onPrintTrigger = {
                        if (sketchViewModel.canvasSizeConfig != null) {
                            executePrint(context, sketchViewModel, PdfExporter.BoundsMode.CANVAS_SIZE)
                        } else {
                            showPdfPrintDialog = true
                        }
                    }
                )
            }
  
            // UI SCALER PROVIDER
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val screenHeight = configuration.screenHeightDp.dp
            
            val buttonSpacingFactor by androidx.compose.runtime.remember {
                androidx.compose.runtime.derivedStateOf { sketchViewModel.buttonSpacingFactor }
            }
            
            val scaler = remember(interfaceScale, buttonSpacingFactor, screenWidth, screenHeight) {
                com.sketcher.sketchercompanionv1.ui.theme.UiScaler(
                    scaleFactor = interfaceScale,
                    buttonSpacingFactor = buttonSpacingFactor,
                    screenWidth = screenWidth,
                    screenHeight = screenHeight
                )
            }
  
            val globalCustomIcons by sketchViewModel.globalCustomIcons.collectAsState()

            androidx.compose.runtime.CompositionLocalProvider(
                com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler provides scaler,
                com.sketcher.sketchercompanionv1.ui.components.LocalGlobalCustomIcons provides globalCustomIcons
            ) {
                com.sketcher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme(theme = theme) {
                    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        // Initialize local projects directory
                        androidx.compose.runtime.LaunchedEffect(Unit) {
                            sketchViewModel.initLocalProjects(context)
                        }

                        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
                        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                                if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                                    if (!sketchViewModel.showDashboard) {
                                        sketchViewModel.hasUnsavedChangesSinceLastAutosave = true
                                    }
                                    sketchViewModel.saveCurrentProjectLocal(context)
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)
                            onDispose {
                                lifecycleOwner.lifecycle.removeObserver(observer)
                            }
                        }


                        // Intercept back button gestures
                        androidx.activity.compose.BackHandler(enabled = true) {
                            if (!sketchViewModel.showDashboard) {
                                sketchViewModel.exitEditorToDashboard(context)
                            } else {
                                val handled = sketchViewModel.navigateUp(context)
                                if (!handled) {
                                    (context as? android.app.Activity)?.moveTaskToBack(true)
                                }
                            }
                        }

                        if (sketchViewModel.showDashboard) {
                            com.sketcher.sketchercompanionv1.ui.DashboardScreen(
                                viewModel = sketchViewModel,
                                theme = theme,
                                onOpenProject = { project ->
                                    sketchViewModel.loadLocalProject(context, project)
                                },
                                versionName = updateManager.getCurrentVersionName(),
                                updateAvailable = (updateInfo != null),
                                onUpdateClick = {
                                    coroutineScope.launch {
                                        try {
                                            val info = updateManager.checkForUpdates()
                                            if (info != null) {
                                                updateInfo = info
                                                showUpdateDialog = true
                                            } else {
                                                android.widget.Toast.makeText(context, "No hay actualizaciones disponibles", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                            android.widget.Toast.makeText(context, "Error al buscar actualizaciones", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                onSignOut = { skipLogin = false }
                            )
                        } else {
                            // 1. MAIN UI LAYER
                            com.sketcher.sketchercompanionv1.ui.layout.StudioLayout(
                                viewModel = sketchViewModel,
                                uiCollapsed = uiCollapsed,
                                onToggleUi = { uiCollapsed = !uiCollapsed },
                                swapVertical = swapVertical,
                                swapHorizontal = swapHorizontal,
                                projectActions = projectActions
                            )
                        }
                        
                        // 3. COMMON DIALOGS
                        if (showSettingsPopup) {
                            SettingsDialog(
                                theme = theme,
                                onDismiss = { showSettingsPopup = false },
                                isPalmRejectionEnabled = sketchViewModel.isPalmRejectionEnabled,
                                onTogglePalmRejection = { sketchViewModel.togglePalmRejection() },
                                interfaceScale = sketchViewModel.interfaceScale,
                                onInterfaceScaleChanged = { scale -> sketchViewModel.updateInterfaceScale(scale) },
                                isDebugWireframe = sketchViewModel.isDebugWireframe,
                                onToggleDebugWireframe = { sketchViewModel.isDebugWireframe = !sketchViewModel.isDebugWireframe },
                                showPerformanceStats = sketchViewModel.showPerformanceStats,
                                onTogglePerformanceStats = { sketchViewModel.togglePerformanceStats() },
                                currentScaleConfig = sketchViewModel.scaleConfig,
                                onUpdateProjectConfig = { unit, resolution -> 
                                    sketchViewModel.updateScaleConfig(unit, resolution)
                                },
                                onBackupPreferences = { sketchViewModel.backupPreferences() },
                                onRestorePreferences = { sketchViewModel.restorePreferences() },
                                onResetPreferences = { sketchViewModel.resetPreferencesToDefault() },
                                hasBackup = sketchViewModel.hasPreferencesBackup,
                                onCloudBackup = { sketchViewModel.triggerCloudBackup(context) },
                                onCloudRestore = { sketchViewModel.triggerCloudRestore(context) },
                                isSyncingCloud = sketchViewModel.isSyncingCloud,
                                cloudSyncMessage = sketchViewModel.cloudSyncMessage
                            )
                        }

                        if (showPaperSizeDialog) {
                            com.sketcher.sketchercompanionv1.ui.PaperSizeDialog(
                                currentConfig = sketchViewModel.canvasSizeConfig,
                                currentStyle = sketchViewModel.backgroundStyle,
                                theme = theme,
                                fillPresets = sketchViewModel.fillPresets.value,
                                onPresetOverwritten = { index, style -> sketchViewModel.saveFillPreset(index, style) },
                                onConfirm = { config, style -> 
                                    sketchViewModel.updateCanvasSize(config)
                                    sketchViewModel.backgroundStyle = style
                                    sketchViewModel.backgroundColor = if (style is FillStyle.Solid) style.color else android.graphics.Color.WHITE
                                    showPaperSizeDialog = false
                                },
                                onDismiss = { showPaperSizeDialog = false }
                            )
                        }

                        if (showGlobalScaleDialog) {
                            com.sketcher.sketchercompanionv1.ui.dialogs.GlobalScaleDialog(
                                viewModel = sketchViewModel,
                                onDismiss = { showGlobalScaleDialog = false }
                            )
                        }

                        if (showGridSettings) {
                            GridSettingsDialog(
                                theme = theme,
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

                        if (showSaveProjectDialog) {
                            com.sketcher.sketchercompanionv1.ui.SaveProjectDialog(
                                onDismiss = { showSaveProjectDialog = false },
                                onSave = { name ->
                                    sketchViewModel.saveAsLocalProject(context, name)
                                    showSaveProjectDialog = false
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
                                onImport = { data, scaleToFit, defaultStrokeWidth, fillClosedShapes, selectedUnit ->
                                    sketchViewModel.addImportedDxfData(data, scaleToFit, defaultStrokeWidth, fillClosedShapes, selectedUnit)
                                    showDxfImportDialog = false
                                }
                            )
                        }

                        if (showPdfImportDialog && pdfImportUri != null) {
                            com.sketcher.sketchercompanionv1.ui.dialogs.PdfImportDialog(
                                uri = pdfImportUri!!,
                                fileName = pdfOriginalFileName,
                                onDismiss = { showPdfImportDialog = false },
                                onImport = { bitmap, pageIndex, dpi ->
                                    sketchViewModel.insertPdfPageBitmap(context, bitmap, pageIndex, dpi, pdfOriginalFileName)
                                    showPdfImportDialog = false
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

                        if (showPdfPrintDialog) {
                            com.sketcher.sketchercompanionv1.ui.PdfExportDialog(
                                onDismiss = { showPdfPrintDialog = false },
                                onConfirm = { useZoomExtends ->
                                     val mode = if (useZoomExtends) PdfExporter.BoundsMode.ZOOM_EXTENDS else PdfExporter.BoundsMode.HOME_VIEW
                                     executePrint(context, sketchViewModel, mode)
                                     showPdfPrintDialog = false
                                }
                            )
                        }

                        if (showRenderBottomSheet) {
                            com.sketcher.sketchercompanionv1.ui.dialogs.RenderOptionsBottomSheet(
                                viewModel = sketchViewModel,
                                authViewModel = authViewModel,
                                onDismiss = { showRenderBottomSheet = false }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun executePrint(
        context: android.content.Context,
        sketchViewModel: SketcherViewModel,
        boundsMode: PdfExporter.BoundsMode
    ) {
        val tempFile = File(context.cacheDir, "print_job_${System.currentTimeMillis()}.pdf")
        sketchViewModel.generateTempPdfForPrinting(context, tempFile, boundsMode) { success ->
            if (success) {
                try {
                    val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as PrintManager
                    val jobName = "Sketcher Drawing ${sketchViewModel.projectId ?: ""}"
                    val adapter = PdfPrintAdapter(context, tempFile, "$jobName.pdf")
                    printManager.print(jobName, adapter, null)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(context, "Error al iniciar servicio de impresión", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(context, "Error al preparar documento para imprimir", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try {
            val viewModel = androidx.lifecycle.ViewModelProvider(this)[SketcherViewModel::class.java]
            viewModel.autoSyncCloud(this)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

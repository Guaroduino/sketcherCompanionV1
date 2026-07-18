package com.sketcher.sketchercompanionv1.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.DashboardItem
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.AppIconButton
import com.sketcher.sketchercompanionv1.ui.panels.OutlinerActionButton
import com.sketcher.sketchercompanionv1.utils.toFillStyle
import com.sketcher.sketchercompanionv1.utils.toFillStyleJson
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: SketcherViewModel,
    theme: UiThemeConfig,
    onOpenProject: (DashboardItem.Project) -> Unit,
    versionName: String = "",
    updateAvailable: Boolean = false,
    onUpdateClick: () -> Unit = {},
    onSignOut: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val scaler = LocalUiScaler.current
    val scaleFactor = scaler.scaleFactor

    val currentDir = viewModel.currentDirectory
    val items = viewModel.localItems
    val thumbnailCache = viewModel.thumbnailCache

    var splitterPosition by remember { mutableStateOf(0.6f) }
    var containerHeightPx by remember { mutableStateOf(0f) }

    // Dialog state
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<DashboardItem?>(null) }
    var showMoveDialog by remember { mutableStateOf<DashboardItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<DashboardItem?>(null) }
    var showCustomizeCoverDialog by remember { mutableStateOf<DashboardItem.Folder?>(null) }
    var showHistoryDialog by remember { mutableStateOf<DashboardItem.Project?>(null) }
    var showWipeConfirmationDialog by remember { mutableStateOf(false) }

    // SAF launcher for importing external files
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importExternalProject(context, it) }
    }

    val scope = rememberCoroutineScope()

    // Refresh items on launch or folder change
    LaunchedEffect(currentDir) {
        viewModel.refreshLocalItems()
    }

    val scaffoldBgColor = theme.barBackgroundColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp * scaleFactor)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Image(
                                    painter = androidx.compose.ui.res.painterResource(id = com.sketcher.sketchercompanionv1.R.drawable.logo),
                                    contentDescription = "Logo",
                                    modifier = Modifier.size(32.dp * scaleFactor)
                                )
                                Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                                Text(
                                    text = "Sketcher",
                                    fontWeight = FontWeight.Light,
                                    fontSize = 20.sp * scaleFactor,
                                    color = theme.iconColor
                                )
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 8.dp * scaleFactor)
                                        .height(20.dp * scaleFactor)
                                        .width(1.dp)
                                        .background(theme.iconColor.copy(alpha = 0.5f))
                                )
                                Text(
                                    text = "Companion",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 20.sp * scaleFactor,
                                    color = theme.iconColor
                                )
                                if (versionName.isNotEmpty()) {
                                    Spacer(modifier = Modifier.width(6.dp * scaleFactor))
                                    Text(
                                        text = "v$versionName",
                                        fontSize = 12.sp * scaleFactor,
                                        color = theme.iconColor.copy(alpha = 0.6f),
                                        modifier = Modifier.padding(top = 2.dp * scaleFactor)
                                    )
                                }
                                if (updateAvailable) {
                                    Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                                    TextButton(
                                        onClick = onUpdateClick,
                                        contentPadding = PaddingValues(horizontal = 8.dp * scaleFactor, vertical = 2.dp * scaleFactor),
                                        modifier = Modifier.height(26.dp * scaleFactor),
                                        colors = ButtonDefaults.textButtonColors(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    ) {
                                        Text(
                                            text = "¡Actualizar!",
                                            fontSize = 11.sp * scaleFactor,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            val userName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: currentUser?.email?.substringBefore("@")
                            if (userName != null) {
                                Text(
                                    text = "Hola, $userName",
                                    fontSize = 12.sp * scaleFactor,
                                    color = theme.iconColor.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                },
                actions = {
                    var showSettingsPopup by remember { mutableStateOf(false) }

                    Box {
                        IconButton(
                            onClick = { showSettingsPopup = true },
                            modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Configuración",
                                modifier = Modifier.size(24.dp * scaleFactor),
                                tint = theme.iconColor
                            )
                        }

                        DropdownMenu(
                            expanded = showSettingsPopup,
                            onDismissRequest = { showSettingsPopup = false },
                            modifier = Modifier.background(theme.barBackgroundColor)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Buscar Actualización", fontSize = 14.sp * scaleFactor, color = theme.iconColor) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.SystemUpdate,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp * scaleFactor),
                                        tint = theme.iconColor
                                    )
                                },
                                onClick = {
                                    showSettingsPopup = false
                                    onUpdateClick()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mostrar Librería Pública", fontSize = 14.sp * scaleFactor, color = theme.iconColor) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Public,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp * scaleFactor),
                                        tint = theme.iconColor
                                    )
                                },
                                trailingIcon = {
                                    Checkbox(
                                        checked = viewModel.showPublicLibrary,
                                        onCheckedChange = null,
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = theme.highlightColor,
                                            checkmarkColor = theme.iconColor
                                        )
                                    )
                                },
                                onClick = {
                                    viewModel.togglePublicLibrary()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Borrar Datos en la Nube", fontSize = 14.sp * scaleFactor, color = Color(0xFFC62828)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp * scaleFactor),
                                        tint = Color(0xFFC62828)
                                    )
                                },
                                onClick = {
                                    showSettingsPopup = false
                                    showWipeConfirmationDialog = true
                                }
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            if (user != null) {
                                viewModel.autoSyncCloud(context)
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = "Sincronizar",
                            modifier = Modifier.size(24.dp * scaleFactor),
                            tint = theme.iconColor
                        )
                    }

                    IconButton(
                        onClick = {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                            onSignOut?.invoke()
                        },
                        modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            modifier = Modifier.size(24.dp * scaleFactor),
                            tint = theme.iconColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.barBackgroundColor,
                    titleContentColor = theme.iconColor,
                    actionIconContentColor = theme.iconColor,
                    navigationIconContentColor = theme.iconColor
                )
            )
        },
        containerColor = scaffoldBgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .onGloballyPositioned { coordinates ->
                    containerHeightPx = coordinates.size.height.toFloat()
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(splitterPosition)
            ) {
                HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val rootDir = viewModel.getProjectsRootDir(context)
                    val isAtRoot = currentDir?.absolutePath == rootDir.absolutePath

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isAtRoot) {
                            AppIconButton(
                                onClick = { 
                                    if (currentDir != null && currentDir.absolutePath != rootDir.absolutePath) {
                                        viewModel.navigateToFolder(currentDir.parentFile ?: rootDir)
                                    }
                                },
                                icon = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Volver",
                                tint = theme.iconColor,
                                buttonSize = 24.dp
                            )
                            Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                        }
                        Text(
                            text = if (isAtRoot) "CUADERNOS Y PROYECTOS" else currentDir?.name?.uppercase(Locale.getDefault()) ?: "CUADERNOS Y PROYECTOS",
                            color = theme.iconColor,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp * scaleFactor), verticalAlignment = Alignment.CenterVertically) {
                        OutlinerActionButton(Icons.Default.Add, "Nuevo Dibujo", theme.iconColor) {
                            showCreateProjectDialog = true
                        }
                        OutlinerActionButton(Icons.Default.CreateNewFolder, "Nuevo Cuaderno", theme.iconColor) {
                            showCreateFolderDialog = true
                        }
                        OutlinerActionButton(Icons.Default.FileUpload, "Importar", theme.iconColor) {
                            importLauncher.launch(arrayOf("*/*"))
                        }
                    }
                }
                
                HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))

                Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(12.dp * scaleFactor)) {
                    // Grid of items
                if (items.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(16.dp * scaleFactor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                modifier = Modifier.size(64.dp * scaleFactor)
                            )
                            Spacer(modifier = Modifier.height(8.dp * scaleFactor))
                            Text(
                                text = "Esta carpeta está vacía",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 15.sp * scaleFactor
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 160.dp * scaleFactor),
                        horizontalArrangement = Arrangement.spacedBy(16.dp * scaleFactor),
                        verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(items) { item ->
                            when (item) {
                                is DashboardItem.Folder -> {
                                    val rootDir = viewModel.getProjectsRootDir(context)
                                    val relPath = remember(item.path) {
                                        try { File(item.path).toRelativeString(rootDir) } catch(e: Exception) { item.name }
                                    }
                                    val isSynced = viewModel.isFolderSynced(context, relPath, item.lastModified)
                                    FolderCard(
                                        folder = item,
                                        thumbnailCache = thumbnailCache,
                                        scaleFactor = scaleFactor,
                                        theme = theme,
                                        isSynced = isSynced,
                                        onClick = { viewModel.navigateToFolder(File(item.path)) },
                                        onRename = { showRenameDialog = item },
                                        onCustomize = { showCustomizeCoverDialog = item },
                                        onDelete = { showDeleteConfirmDialog = item }
                                    )
                                }
                                is DashboardItem.Project -> {
                                    val thumbnail = thumbnailCache[item.path]
                                    val pId = remember(item.path) { viewModel.getProjectId(File(item.path)) ?: "" }
                                    val isSynced = viewModel.isProjectSynced(context, pId, item.lastModified)
                                    ProjectCard(
                                        project = item,
                                        thumbnail = thumbnail,
                                        scaleFactor = scaleFactor,
                                        theme = theme,
                                        isSynced = isSynced,
                                        onClick = { onOpenProject(item) },
                                        onRename = { showRenameDialog = item },
                                        onMove = { showMoveDialog = item },
                                        onDelete = { showDeleteConfirmDialog = item },
                                        onShowHistory = { showHistoryDialog = item }
                                    )
                                }
                            }
                        }
                    }
                }
                } // End of Box
            } // End of Column

            // Draggable splitter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp * scaleFactor)
                    .background(theme.barBackgroundColor)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (containerHeightPx > 0) {
                                val deltaFraction = delta / containerHeightPx
                                splitterPosition = (splitterPosition + deltaFraction).coerceIn(0.2f, 0.8f)
                            }
                        }
                    )
                    .border(1.dp, theme.iconColor.copy(alpha = 0.15f)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp * scaleFactor)
                        .height(4.dp * scaleFactor)
                        .clip(RoundedCornerShape(2.dp * scaleFactor))
                        .background(theme.iconColor.copy(alpha = 0.4f))
                )
            }

            // Object Library Bottom Panel
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f - splitterPosition)
                    .background(theme.barBackgroundColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(if (viewModel.showPublicLibrary) 0.5f else 1f)
                ) {
                    com.sketcher.sketchercompanionv1.ui.panels.LibraryPanel(viewModel = viewModel)
                }

                if (viewModel.showPublicLibrary) {
                    HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(0.5f)
                            .padding(horizontal = 12.dp * scaleFactor)
                    ) {
                        PublicLibraryPlaceholder(theme = theme, scaleFactor = scaleFactor)
                    }
                }
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        com.sketcher.sketchercompanionv1.ui.dialogs.CreateNotebookDialog(
            initialName = remember(items) { getUniqueFolderName(items) },
            viewModel = viewModel,
            theme = theme,
            scaleFactor = scaleFactor,
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name, coverStyle, coverFill ->
                viewModel.createLocalFolder(context, name, coverStyle, coverFill)
                showCreateFolderDialog = false
            }
        )
    }

    if (showCreateProjectDialog) {
        val uiPresets by viewModel.workspaceProfileRepository.getAllProfiles().collectAsState(initial = emptyList())
        com.sketcher.sketchercompanionv1.ui.dialogs.CreateProjectDialog(
            initialName = remember(items) { getUniqueProjectName(items) },
            theme = theme,
            uiPresets = uiPresets,
            onDismiss = { showCreateProjectDialog = false },
            onConfirm = { name, templateFile, scaleRatio, canvasSizeConfig, backgroundStyle, workspaceProfile ->
                viewModel.createLocalProject(context, name, templateFile, scaleRatio, canvasSizeConfig, backgroundStyle, workspaceProfile)
                showCreateProjectDialog = false
            }
        )
    }

    val projectForHistory = showHistoryDialog
    if (projectForHistory != null) {
        val pId = remember(projectForHistory.path) { viewModel.getProjectId(File(projectForHistory.path)) ?: "" }
        LaunchedEffect(pId) {
            viewModel.fetchProjectVersions(pId)
        }
        ProjectHistoryDialog(
            project = projectForHistory,
            projectId = pId,
            theme = theme,
            viewModel = viewModel,
            onDismiss = { showHistoryDialog = null }
        )
    }

    val itemToRename = showRenameDialog
    if (itemToRename != null) {
        InputDialog(
            theme = theme,
            title = "Renombrar",
            hint = "Nuevo nombre",
            initialValue = if (itemToRename is DashboardItem.Project) itemToRename.name else itemToRename.name,
            onDismiss = { showRenameDialog = null },
            onConfirm = { newName ->
                viewModel.renameLocalItem(context, itemToRename, newName)
                showRenameDialog = null
            }
        )
    }

    val itemToDelete = showDeleteConfirmDialog
    if (itemToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = null },
            title = { Text("Confirmar eliminación", fontSize = 18.sp * scaleFactor, color = theme.iconColor) },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas borrar \"${itemToDelete.name}\"? " +
                            if (itemToDelete is DashboardItem.Folder) "Se borrarán todos los proyectos contenidos en él." else "Esta acción no se puede deshacer.",
                    fontSize = 14.sp * scaleFactor,
                    color = theme.iconColor.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteLocalItem(context, itemToDelete)
                        showDeleteConfirmDialog = null
                    }
                ) {
                    Text("Borrar", fontSize = 14.sp * scaleFactor, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)
                ) {
                    Text("Cancelar", fontSize = 14.sp * scaleFactor)
                }
            },
            containerColor = theme.barBackgroundColor
        )
    }

    if (showWipeConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showWipeConfirmationDialog = false },
            title = { Text("Confirmar eliminación de la nube", fontSize = 18.sp * scaleFactor, color = theme.iconColor) },
            text = {
                Text(
                    text = "¿Está seguro de que desea eliminar todos los proyectos, carpetas, biblioteca y configuraciones sincronizados en Firebase? Esta acción borrará la nube permanentemente y no afectará tus dibujos locales en esta tableta.",
                    fontSize = 14.sp * scaleFactor,
                    color = theme.iconColor.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        showWipeConfirmationDialog = false
                        viewModel.wipeCloudProjects(context)
                    }
                ) {
                    Text("Borrar Todo", fontSize = 14.sp * scaleFactor, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showWipeConfirmationDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)
                ) {
                    Text("Cancelar", fontSize = 14.sp * scaleFactor)
                }
            },
            containerColor = theme.barBackgroundColor
        )
    }

    val itemToMove = showMoveDialog
    if (itemToMove != null) {
        MoveItemDialog(
            theme = theme,
            item = itemToMove,
            currentDir = currentDir ?: viewModel.getProjectsRootDir(context),
            rootDir = viewModel.getProjectsRootDir(context),
            scaleFactor = scaleFactor,
            onDismiss = { showMoveDialog = null },
            onConfirm = { targetFolder ->
                viewModel.moveLocalItem(context, itemToMove, targetFolder)
                showMoveDialog = null
            }
        )
    }

    val folderToCustomize = showCustomizeCoverDialog
    if (folderToCustomize != null) {
        EditNotebookCoverDialog(
            folder = folderToCustomize,
            viewModel = viewModel,
            theme = theme,
            scaleFactor = scaleFactor,
            onDismiss = { showCustomizeCoverDialog = null },
            onSave = { coverStyle, coverFill, coverProject ->
                viewModel.updateFolderMetadata(context, folderToCustomize.path, coverStyle, coverFill, coverProject)
                showCustomizeCoverDialog = null
            }
        )
    }
}

@Composable
fun CloudSyncStatusIndicator(
    isSynced: Boolean,
    scaleFactor: Float,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(20.dp * scaleFactor)
            .background(
                color = Color.Black.copy(alpha = 0.4f),
                shape = RoundedCornerShape(10.dp * scaleFactor)
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isSynced) Icons.Default.CloudDone else Icons.Default.CloudQueue,
            contentDescription = if (isSynced) "Sincronizado" else "No sincronizado",
            tint = if (isSynced) Color(0xFF81C784) else Color(0xFFE0E0E0),
            modifier = Modifier.size(14.dp * scaleFactor)
        )
    }
}

@Composable
fun FolderCard(
    folder: DashboardItem.Folder,
    thumbnailCache: Map<String, android.graphics.Bitmap?>,
    scaleFactor: Float,
    theme: UiThemeConfig,
    isSynced: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onCustomize: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = theme.iconColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp * scaleFactor)
            ),
        shape = RoundedCornerShape(8.dp * scaleFactor),
        colors = CardDefaults.cardColors(
            containerColor = theme.buttonColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp * scaleFactor
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp * scaleFactor)
                    .background(theme.barBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                NotebookCover(
                    folder = folder,
                    thumbnailCache = thumbnailCache,
                    scaleFactor = scaleFactor,
                    modifier = Modifier.fillMaxSize()
                )
                
                CloudSyncStatusIndicator(
                    isSynced = isSynced,
                    scaleFactor = scaleFactor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp * scaleFactor)
                )
            }

            Column(
                modifier = Modifier.padding(10.dp * scaleFactor)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = folder.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp * scaleFactor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = theme.iconColor,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp * scaleFactor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                modifier = Modifier.size(16.dp * scaleFactor),
                                tint = theme.iconColor
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(theme.barBackgroundColor)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renombrar", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Personalizar Portada", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                                onClick = {
                                    showMenu = false
                                    onCustomize()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Borrar", fontSize = 13.sp * scaleFactor, color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp * scaleFactor))

                Text(
                    text = "${folder.itemCount} dibujos",
                    fontSize = 11.sp * scaleFactor,
                    color = theme.iconColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun ProjectCard(
    project: DashboardItem.Project,
    thumbnail: android.graphics.Bitmap?,
    scaleFactor: Float,
    theme: UiThemeConfig,
    isSynced: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit,
    onShowHistory: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    val formattedDate = remember(project.lastModified) {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        sdf.format(Date(project.lastModified))
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = theme.iconColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp * scaleFactor)
            ),
        shape = RoundedCornerShape(8.dp * scaleFactor),
        colors = CardDefaults.cardColors(
            containerColor = theme.buttonColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp * scaleFactor
        )
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp * scaleFactor)
                    .background(theme.barBackgroundColor),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnail != null) {
                    Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Previsualización",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Description,
                        contentDescription = null,
                        tint = theme.iconColor.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp * scaleFactor)
                    )
                }
                
                CloudSyncStatusIndicator(
                    isSynced = isSynced,
                    scaleFactor = scaleFactor,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp * scaleFactor)
                )
                
                // Scale Badge
                val scaleRatioStr = if (project.globalScaleRatio.rem(1) == 0f) {
                    project.globalScaleRatio.toInt().toString()
                } else {
                    "%.1f".format(project.globalScaleRatio)
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp * scaleFactor)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp * scaleFactor))
                        .padding(horizontal = 6.dp * scaleFactor, vertical = 2.dp * scaleFactor)
                ) {
                    Text(
                        text = "Esc: 1:$scaleRatioStr",
                        color = Color.White,
                        fontSize = 10.sp * scaleFactor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Column(
                modifier = Modifier.padding(10.dp * scaleFactor)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = project.name,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp * scaleFactor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = theme.iconColor,
                        modifier = Modifier.weight(1f)
                    )

                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(24.dp * scaleFactor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
                                modifier = Modifier.size(16.dp * scaleFactor),
                                tint = theme.iconColor
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(theme.barBackgroundColor)
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renombrar", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mover a Cuaderno", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                                onClick = {
                                    showMenu = false
                                    onMove()
                                }
                            )
                            if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
                                DropdownMenuItem(
                                    text = { Text("Historial de Versiones", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                                    onClick = {
                                        showMenu = false
                                        onShowHistory()
                                    }
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Borrar", fontSize = 13.sp * scaleFactor, color = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp * scaleFactor))

                Text(
                    text = formattedDate,
                    fontSize = 11.sp * scaleFactor,
                    color = theme.iconColor.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun InputDialog(
    theme: UiThemeConfig,
    title: String,
    hint: String,
    initialValue: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initialValue) }
    val scaler = LocalUiScaler.current
    val scaleFactor = scaler.scaleFactor

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 18.sp * scaleFactor, color = theme.iconColor) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(hint, fontSize = 13.sp * scaleFactor, color = theme.iconColor.copy(alpha = 0.7f)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedLabelColor = theme.highlightColor,
                        unfocusedLabelColor = theme.iconColor.copy(alpha = 0.7f),
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = theme.buttonColor,
                    contentColor = theme.iconColor,
                    disabledContainerColor = theme.buttonColor.copy(alpha = 0.5f),
                    disabledContentColor = theme.iconColor.copy(alpha = 0.5f)
                )
            ) {
                Text("Confirmar", fontSize = 14.sp * scaleFactor, color = theme.iconColor)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = theme.iconColor
                )
            ) {
                Text("Cancelar", fontSize = 14.sp * scaleFactor)
            }
        },
        containerColor = theme.barBackgroundColor
    )
}

@Composable
fun MoveItemDialog(
    theme: UiThemeConfig,
    item: DashboardItem,
    currentDir: File,
    rootDir: File,
    scaleFactor: Float,
    onDismiss: () -> Unit,
    onConfirm: (File) -> Unit
) {
    val folders = remember(currentDir) {
        val list = mutableListOf<File>()
        if (currentDir.absolutePath != rootDir.absolutePath) {
            val parent = currentDir.parentFile
            if (parent != null) {
                list.add(parent)
            }
        }
        currentDir.listFiles { f -> f.isDirectory }?.forEach {
            list.add(it)
        }
        list
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover \"${item.name}\" a...", fontSize = 18.sp * scaleFactor, color = theme.iconColor) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp * scaleFactor)
            ) {
                if (folders.isEmpty()) {
                    Text(
                        text = "No hay carpetas de destino disponibles en este nivel.",
                        fontSize = 14.sp * scaleFactor,
                        color = theme.iconColor.copy(alpha = 0.7f)
                    )
                } else {
                    folders.forEach { folder ->
                        val isParent = folder.absolutePath == currentDir.parentFile?.absolutePath
                        val displayName = if (isParent) {
                            "Subir carpeta (.. / ${folder.name})"
                        } else {
                            folder.name
                        }
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onConfirm(folder) }
                                .padding(vertical = 12.dp * scaleFactor, horizontal = 8.dp * scaleFactor)
                        ) {
                            Icon(
                                imageVector = if (isParent) Icons.Default.ArrowUpward else Icons.Default.Folder,
                                contentDescription = null,
                                tint = theme.highlightColor,
                                modifier = Modifier.size(24.dp * scaleFactor)
                            )
                            Spacer(modifier = Modifier.width(12.dp * scaleFactor))
                            Text(
                                text = displayName,
                                fontSize = 14.sp * scaleFactor,
                                color = theme.iconColor
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = theme.iconColor
                )
            ) {
                Text("Cancelar", fontSize = 14.sp * scaleFactor)
            }
        },
        containerColor = theme.barBackgroundColor
    )
}

@Composable
fun EditNotebookCoverDialog(
    folder: DashboardItem.Folder,
    viewModel: SketcherViewModel,
    theme: UiThemeConfig,
    scaleFactor: Float,
    onDismiss: () -> Unit,
    onSave: (coverStyle: String, coverFill: com.sketcher.sketchercompanionv1.dto.FillStyleJson?, coverProject: String?) -> Unit
) {
    var coverStyle by remember { mutableStateOf(folder.metadata.coverStyle) }
    var coverFillJson by remember { mutableStateOf(folder.metadata.coverFill) }
    var coverProject by remember { mutableStateOf(folder.metadata.coverProject) }
    
    var fillMode by remember { mutableStateOf(if (coverProject != null) "drawing" else "fill") }

    var showFillPicker by remember { mutableStateOf(false) }
    val fillPresets by viewModel.fillPresets.collectAsState()
    
    val projects = remember(viewModel.localItems) {
        viewModel.localItems.filterIsInstance<DashboardItem.Project>()
    }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val maxCardHeight = (configuration.screenHeightDp * 0.85f).dp

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .width(320.dp * scaleFactor)
                .heightIn(max = maxCardHeight),
            shape = RoundedCornerShape(16.dp * scaleFactor),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                contentColor = theme.iconColor
            ),
            border = BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp * scaleFactor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp * scaleFactor),
                verticalArrangement = Arrangement.spacedBy(16.dp * scaleFactor)
            ) {
                Text(
                    text = "PERSONALIZAR PORTADA",
                    style = MaterialTheme.typography.labelLarge,
                    color = theme.iconColor.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )

                Column(verticalArrangement = Arrangement.spacedBy(6.dp * scaleFactor)) {
                    Text("Estilo de Cuaderno:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp * scaleFactor, color = theme.iconColor)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
                    ) {
                        val styles = listOf(
                            Triple("classic", "Clásico", Icons.Default.Book),
                            Triple("spiral", "Espiral", Icons.Default.MenuBook),
                            Triple("minimalist", "Minimalista", Icons.Default.ImportContacts)
                        )
                        styles.forEach { (styleKey, label, icon) ->
                            CustomThemeChip(
                                selected = coverStyle == styleKey,
                                label = label,
                                icon = icon,
                                theme = theme,
                                scaleFactor = scaleFactor,
                                onClick = { coverStyle = styleKey }
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp * scaleFactor)) {
                    Text("Tipo de Fondo:", fontWeight = FontWeight.SemiBold, fontSize = 13.sp * scaleFactor, color = theme.iconColor)
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
                    ) {
                        CustomThemeChip(
                            selected = fillMode == "fill",
                            label = "Patrón/Color",
                            icon = Icons.Default.Palette,
                            theme = theme,
                            scaleFactor = scaleFactor,
                            onClick = { 
                                fillMode = "fill"
                                coverProject = null
                            }
                        )
                        CustomThemeChip(
                            selected = fillMode == "drawing",
                            label = "Usar Dibujo",
                            icon = Icons.Default.Image,
                            theme = theme,
                            scaleFactor = scaleFactor,
                            onClick = { 
                                fillMode = "drawing"
                                if (coverProject == null && projects.isNotEmpty()) {
                                    coverProject = projects.first().path
                                }
                            }
                        )
                    }
                }

                if (fillMode == "fill") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp * scaleFactor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp * scaleFactor)
                                .clip(RoundedCornerShape(4.dp * scaleFactor))
                                .border(1.dp, theme.iconColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp * scaleFactor))
                        ) {
                            val fillStyle = remember(coverFillJson) {
                                coverFillJson.toFillStyle(android.graphics.Color.LTGRAY)
                            }
                            val renderEngine = remember { com.sketcher.sketchercompanionv1.RenderEngine() }
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                drawIntoCanvas { canvas ->
                                    val paint = android.graphics.Paint().apply {
                                        isAntiAlias = true
                                        style = android.graphics.Paint.Style.FILL
                                    }
                                    renderEngine.applyFillStyle(paint, fillStyle)
                                    canvas.nativeCanvas.drawRect(0f, 0f, size.width, size.height, paint)
                                }
                            }
                        }

                        Button(
                            onClick = { showFillPicker = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.highlightColor,
                                contentColor = Color.White
                            )
                        ) {
                            Text("Cambiar Relleno", fontSize = 12.sp * scaleFactor)
                        }
                    }
                } else {
                    if (projects.isEmpty()) {
                        Text(
                            text = "No hay dibujos en este cuaderno para usar de portada.",
                            color = theme.iconColor.copy(alpha = 0.6f),
                            fontSize = 12.sp * scaleFactor
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp * scaleFactor))
                                    .background(theme.buttonColor)
                                    .clickable { dropdownExpanded = true }
                                    .border(1.dp, theme.iconColor.copy(alpha = 0.15f), RoundedCornerShape(8.dp * scaleFactor))
                                    .padding(12.dp * scaleFactor),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val selectedProjectName = projects.find { it.path == coverProject }?.name ?: "Seleccionar Dibujo"
                                Text(selectedProjectName, color = theme.iconColor, fontSize = 13.sp * scaleFactor)
                                Icon(Icons.Default.ArrowDropDown, null, tint = theme.iconColor)
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier.background(theme.barBackgroundColor)
                            ) {
                                projects.forEach { proj ->
                                    DropdownMenuItem(
                                        text = { Text(proj.name, color = theme.iconColor, fontSize = 13.sp * scaleFactor) },
                                        onClick = {
                                            coverProject = proj.path
                                            dropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp * scaleFactor))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = theme.iconColor.copy(alpha = 0.7f), fontSize = 14.sp * scaleFactor)
                    }
                    Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                    Button(
                        onClick = { onSave(coverStyle, coverFillJson, coverProject) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.highlightColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Guardar", fontSize = 14.sp * scaleFactor)
                    }
                }
            }
        }
    }

    if (showFillPicker) {
        val initialFillStyle = remember(coverFillJson) {
            coverFillJson.toFillStyle(android.graphics.Color.LTGRAY)
        }
        com.sketcher.sketchercompanionv1.ui.FillStylePickerDialog(
            initialStyle = initialFillStyle,
            theme = theme,
            presets = fillPresets,
            onPresetOverwritten = { index, style ->
                viewModel.saveFillPreset(index, style)
            },
            onDismiss = { showFillPicker = false },
            onStyleSelected = { style ->
                coverFillJson = style.toFillStyleJson()
                showFillPicker = false
            },
            basePixelsPerMillimeter = viewModel.scaleConfig.basePixelsPerMillimeter
        )
    }
}

@Composable
fun CustomThemeChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    theme: UiThemeConfig,
    scaleFactor: Float,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp * scaleFactor))
            .background(if (selected) theme.highlightColor else theme.buttonColor)
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = if (selected) theme.highlightColor else theme.iconColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(8.dp * scaleFactor)
            )
            .padding(horizontal = 12.dp * scaleFactor, vertical = 8.dp * scaleFactor),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.iconColor,
                modifier = Modifier.size(16.dp * scaleFactor)
            )
            Spacer(modifier = Modifier.width(6.dp * scaleFactor))
            Text(
                text = label,
                color = theme.iconColor,
                fontSize = 12.sp * scaleFactor,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun NotebookCover(
    folder: DashboardItem.Folder,
    thumbnailCache: Map<String, android.graphics.Bitmap?>,
    scaleFactor: Float,
    modifier: Modifier = Modifier
) {
    val renderEngine = remember { com.sketcher.sketchercompanionv1.RenderEngine() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    val coverProjectBmp = remember(folder.metadata.coverProject, thumbnailCache) {
        folder.metadata.coverProject?.let { thumbnailCache[it] }
    }

    val fillStyle = remember(folder.metadata.coverFill) {
        folder.metadata.coverFill.toFillStyle(android.graphics.Color.LTGRAY)
    }

    androidx.compose.foundation.Canvas(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp * scaleFactor))
    ) {
        val width = size.width
        val height = size.height
        
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val paint = android.graphics.Paint().apply {
                isAntiAlias = true
                this.style = android.graphics.Paint.Style.FILL
            }
            
            if (coverProjectBmp != null) {
                val bmpW = coverProjectBmp.width.toFloat()
                val bmpH = coverProjectBmp.height.toFloat()
                val targetRatio = width / height
                val bmpRatio = bmpW / bmpH

                val srcLeft: Float
                val srcTop: Float
                val srcRight: Float
                val srcBottom: Float

                if (bmpRatio > targetRatio) {
                    val newWidth = bmpH * targetRatio
                    srcLeft = (bmpW - newWidth) / 2f
                    srcTop = 0f
                    srcRight = srcLeft + newWidth
                    srcBottom = bmpH
                } else {
                    val newHeight = bmpW / targetRatio
                    srcLeft = 0f
                    srcTop = (bmpH - newHeight) / 2f
                    srcRight = bmpW
                    srcBottom = srcTop + newHeight
                }

                val src = android.graphics.Rect(srcLeft.toInt(), srcTop.toInt(), srcRight.toInt(), srcBottom.toInt())
                val dest = android.graphics.RectF(0f, 0f, width, height)
                nativeCanvas.drawBitmap(coverProjectBmp, src, dest, paint)
            } else {
                renderEngine.applyFillStyle(paint, fillStyle)
                
                val rect = android.graphics.RectF(0f, 0f, width, height)
                val rx = with(density) { (8.dp * scaleFactor).toPx() }
                val ry = with(density) { (8.dp * scaleFactor).toPx() }
                nativeCanvas.drawRoundRect(rect, rx, ry, paint)
            }
        }

        when (folder.metadata.coverStyle) {
            "spiral" -> {
                val spineWidth = 14.dp * scaleFactor
                val spineWidthPx = with(density) { spineWidth.toPx() }
                
                drawRect(
                    color = Color.Black.copy(alpha = 0.15f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(spineWidthPx, height)
                )
                
                val loopCount = 10
                val startY = 12.dp * scaleFactor
                val endY = 168.dp * scaleFactor
                val startYPx = with(density) { startY.toPx() }
                val endYPx = with(density) { endY.toPx() }
                val step = (endYPx - startYPx) / (loopCount - 1)
                
                val loopW = with(density) { 10.dp.toPx() }
                val loopH = with(density) { 6.dp.toPx() }
                val loopX = with(density) { 2.dp.toPx() }
                
                for (i in 0 until loopCount) {
                    val y = startYPx + i * step
                    drawRoundRect(
                        color = Color.LightGray,
                        topLeft = androidx.compose.ui.geometry.Offset(loopX, y - loopH / 2),
                        size = androidx.compose.ui.geometry.Size(loopW, loopH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
                    )
                    drawRoundRect(
                        color = Color.DarkGray,
                        topLeft = androidx.compose.ui.geometry.Offset(loopX, y - loopH / 2),
                        size = androidx.compose.ui.geometry.Size(loopW, loopH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 0.5.dp.toPx())
                    )
                }
            }
            "classic" -> {
                val stripeW = 20.dp * scaleFactor
                val stripeWPx = with(density) { stripeW.toPx() }
                
                drawRect(
                    color = Color.Black.copy(alpha = 0.25f),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(stripeWPx, height)
                )
                
                drawRect(
                    color = Color(0xFFFFD700),
                    topLeft = androidx.compose.ui.geometry.Offset(stripeWPx, 0f),
                    size = androidx.compose.ui.geometry.Size(with(density) { 1.5.dp.toPx() }, height)
                )
                
                val beltH = with(density) { 16.dp.toPx() }
                val beltWPx = with(density) { 40.dp.toPx() }
                val beltY = height / 2 - beltH / 2
                
                drawRoundRect(
                    color = Color(0xFF3E2723),
                    topLeft = androidx.compose.ui.geometry.Offset(stripeWPx, beltY),
                    size = androidx.compose.ui.geometry.Size(beltWPx, beltH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color(0xFFFFD700),
                    topLeft = androidx.compose.ui.geometry.Offset(stripeWPx + beltWPx - with(density) { 8.dp.toPx() }, beltY + with(density) { 2.dp.toPx() }),
                    size = androidx.compose.ui.geometry.Size(with(density) { 6.dp.toPx() }, beltH - with(density) { 4.dp.toPx() }),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
                )
            }
            "minimalist" -> {
                val borderPx = with(density) { 4.dp.toPx() }
                
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.1f),
                    topLeft = androidx.compose.ui.geometry.Offset(borderPx, borderPx),
                    size = androidx.compose.ui.geometry.Size(width - 2 * borderPx, height - 2 * borderPx),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx(), 6.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
                
                val badgeW = width * 0.6f
                val badgeH = with(density) { 32.dp.toPx() }
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.9f),
                    topLeft = androidx.compose.ui.geometry.Offset(width / 2 - badgeW / 2, height * 0.3f),
                    size = androidx.compose.ui.geometry.Size(badgeW, badgeH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.2f),
                    topLeft = androidx.compose.ui.geometry.Offset(width / 2 - badgeW / 2, height * 0.3f),
                    size = androidx.compose.ui.geometry.Size(badgeW, badgeH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
        }
    }
}

private fun getUniqueProjectName(items: List<DashboardItem>, prefix: String = "Dibujo"): String {
    var index = 1
    while (true) {
        val candidate = "$prefix $index"
        val exists = items.any { it is DashboardItem.Project && it.name == candidate }
        if (!exists) return candidate
        index++
    }
}

private fun getUniqueFolderName(items: List<DashboardItem>, prefix: String = "Cuaderno"): String {
    var index = 1
    while (true) {
        val candidate = "$prefix $index"
        val exists = items.any { it is DashboardItem.Folder && it.name == candidate }
        if (!exists) return candidate
        index++
    }
}

@Composable
fun PublicLibraryPlaceholder(theme: UiThemeConfig, scaleFactor: Float) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp * scaleFactor),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "LIBRERÍA PÚBLICA",
                color = theme.iconColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
        HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp * scaleFactor)
            ) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(48.dp * scaleFactor)
                )
                Spacer(modifier = Modifier.height(8.dp * scaleFactor))
                Text(
                    text = "Próximamente",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp * scaleFactor,
                    color = Color(0xFF1C1B1F)
                )
                Spacer(modifier = Modifier.height(4.dp * scaleFactor))
                Text(
                    text = "Esta sección te permitirá compartir tus dibujos y componentes públicamente con la comunidad.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontSize = 12.sp * scaleFactor,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ProjectHistoryDialog(
    project: DashboardItem.Project,
    projectId: String,
    theme: UiThemeConfig,
    viewModel: SketcherViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scaler = LocalUiScaler.current
    val scaleFactor = scaler.scaleFactor
    val versions = viewModel.projectVersionsList
    val isLoading = viewModel.isLoadingVersions

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Historial de Versiones",
                    fontSize = 18.sp * scaleFactor,
                    color = theme.iconColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = project.name,
                    fontSize = 12.sp * scaleFactor,
                    color = theme.iconColor.copy(alpha = 0.6f)
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp * scaleFactor),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = theme.highlightColor)
                } else if (versions.isEmpty()) {
                    Text(
                        text = "No hay versiones disponibles en la nube.",
                        color = theme.iconColor.copy(alpha = 0.6f),
                        fontSize = 14.sp * scaleFactor,
                        modifier = Modifier.padding(16.dp * scaleFactor)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp * scaleFactor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        itemsIndexed(versions) { index, version ->
                            val vId = version["versionId"] as? String ?: ""
                            val ts = (version["timestamp"] as? Number)?.toLong() ?: 0L
                            val deviceName = version["deviceName"] as? String ?: "Dispositivo desconocido"
                            val deviceUid = version["deviceUid"] as? String ?: ""
                            val fileUrl = version["fileUrl"] as? String ?: ""
                            val fileSize = (version["fileSize"] as? Number)?.toLong() ?: 0L
                            
                            val isLatest = index == 0
                            val isThisDevice = deviceUid == viewModel.getDeviceUid(context)
                            
                            val formattedDate = remember(ts) {
                                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                                sdf.format(Date(ts))
                            }
                            
                            val formattedSize = remember(fileSize) {
                                if (fileSize <= 0) "Tamaño desconocido"
                                else if (fileSize < 1024 * 1024) "${fileSize / 1024} KB"
                                else "%.1f MB".format(fileSize.toFloat() / (1024 * 1024))
                            }

                            Card(
                                shape = RoundedCornerShape(8.dp * scaleFactor),
                                colors = CardDefaults.cardColors(
                                    containerColor = theme.barBackgroundColor
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(
                                        width = 1.dp,
                                        color = if (isLatest) theme.highlightColor.copy(alpha = 0.5f) else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp * scaleFactor)
                                    )
                                    .padding(1.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp * scaleFactor)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "v${versions.size - index}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp * scaleFactor,
                                                color = theme.iconColor
                                            )
                                            if (isLatest) {
                                                Box(
                                                    modifier = Modifier
                                                        .padding(start = 6.dp * scaleFactor)
                                                        .background(theme.highlightColor.copy(alpha = 0.2f), RoundedCornerShape(4.dp * scaleFactor))
                                                        .padding(horizontal = 4.dp * scaleFactor, vertical = 1.dp * scaleFactor)
                                                ) {
                                                    Text(
                                                        text = "Última",
                                                        color = theme.highlightColor,
                                                        fontSize = 9.sp * scaleFactor,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                        Text(
                                            text = formattedDate,
                                            fontSize = 11.sp * scaleFactor,
                                            color = theme.iconColor.copy(alpha = 0.6f)
                                        )
                                    }
                                    
                                    Spacer(modifier = Modifier.height(4.dp * scaleFactor))
                                    
                                    Text(
                                        text = "$deviceName" + if (isThisDevice) " (Este dispositivo)" else "",
                                        fontSize = 12.sp * scaleFactor,
                                        color = theme.iconColor.copy(alpha = 0.8f)
                                    )
                                    
                                    Text(
                                        text = formattedSize,
                                        fontSize = 10.sp * scaleFactor,
                                        color = theme.iconColor.copy(alpha = 0.5f)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp * scaleFactor))
                                    
                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(
                                            onClick = {
                                                viewModel.createProjectCopyFromVersion(context, project.name, projectId, fileUrl)
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = theme.highlightColor),
                                            modifier = Modifier.padding(end = 4.dp * scaleFactor)
                                        ) {
                                            Text("Abrir como Copia", fontSize = 11.sp * scaleFactor)
                                        }
                                        
                                        Button(
                                            onClick = {
                                                val file = File(project.path)
                                                val rootDir = viewModel.getProjectsRootDir(context)
                                                val relPath = try { file.toRelativeString(rootDir) } catch(e: Exception) { file.name }
                                                viewModel.restoreProjectVersion(context, projectId, relPath, fileUrl, ts)
                                                onDismiss()
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = theme.buttonColor,
                                                contentColor = theme.iconColor
                                            )
                                        ) {
                                            Text("Restaurar", fontSize = 11.sp * scaleFactor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)
            ) {
                Text("Cerrar", fontSize = 14.sp * scaleFactor)
            }
        },
        containerColor = theme.barBackgroundColor
    )
}

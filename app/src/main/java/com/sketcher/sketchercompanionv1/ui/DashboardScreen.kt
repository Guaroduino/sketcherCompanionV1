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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    onOpenProject: (DashboardItem.Project) -> Unit
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

    val scaffoldBgColor = Color.White

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 4.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Architecture,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp * scaleFactor)
                        )
                        Spacer(modifier = Modifier.width(8.dp * scaleFactor))
                        Column {
                            Text(
                                text = "Sketcher Companion",
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp * scaleFactor,
                                color = Color(0xFF1C1B1F)
                            )
                            val currentUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            val userName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: currentUser?.email?.substringBefore("@")
                            if (userName != null) {
                                Text(
                                    text = "Hola, $userName",
                                    fontSize = 12.sp * scaleFactor,
                                    color = Color(0xFF49454F)
                                )
                            }
                        }
                    }
                },
                actions = {
                    var showSettingsPopup by remember { mutableStateOf(false) }

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
                            tint = Color(0xFF49454F)
                        )
                    }

                    IconButton(
                        onClick = {
                            com.google.firebase.auth.FirebaseAuth.getInstance().signOut()
                            scope.launch {
                                try {
                                    androidx.credentials.CredentialManager.create(context)
                                        .clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
                                } catch (e: Exception) {
                                    // Ignore
                                }
                            }
                        },
                        modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Cerrar Sesión",
                            modifier = Modifier.size(24.dp * scaleFactor),
                            tint = Color(0xFF49454F)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.White,
                    titleContentColor = Color(0xFF1C1B1F),
                    actionIconContentColor = Color(0xFF49454F),
                    navigationIconContentColor = Color(0xFF49454F)
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val rootDir = File(context.filesDir, "projects")
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
                        OutlinerActionButton(Icons.Default.CreateNewFolder, "Nuevo Cuaderno", theme.iconColor) {
                            showCreateFolderDialog = true
                        }
                        OutlinerActionButton(Icons.Default.Add, "Nuevo Dibujo", theme.iconColor) {
                            showCreateProjectDialog = true
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
                                    FolderCard(
                                        folder = item,
                                        thumbnailCache = thumbnailCache,
                                        scaleFactor = scaleFactor,
                                        theme = theme,
                                        onClick = { viewModel.navigateToFolder(File(item.path)) },
                                        onRename = { showRenameDialog = item },
                                        onCustomize = { showCustomizeCoverDialog = item },
                                        onDelete = { showDeleteConfirmDialog = item }
                                    )
                                }
                                is DashboardItem.Project -> {
                                    val thumbnail = thumbnailCache[item.path]
                                    ProjectCard(
                                        project = item,
                                        thumbnail = thumbnail,
                                        scaleFactor = scaleFactor,
                                        theme = theme,
                                        onClick = { onOpenProject(item) },
                                        onRename = { showRenameDialog = item },
                                        onMove = { showMoveDialog = item },
                                        onDelete = { showDeleteConfirmDialog = item }
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
                    .background(Color(0xFFF5F5F5))
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            if (containerHeightPx > 0) {
                                val deltaFraction = delta / containerHeightPx
                                splitterPosition = (splitterPosition + deltaFraction).coerceIn(0.2f, 0.8f)
                            }
                        }
                    )
                    .border(1.dp, Color(0xFFE0E0E0)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp * scaleFactor)
                        .height(4.dp * scaleFactor)
                        .clip(RoundedCornerShape(2.dp * scaleFactor))
                        .background(Color.Gray.copy(alpha = 0.5f))
                )
            }

            // Object Library Bottom Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f - splitterPosition)
                    .background(Color.White)
            ) {
                com.sketcher.sketchercompanionv1.ui.panels.LibraryPanel(viewModel = viewModel)
            }
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        InputDialog(
            title = "Nuevo Cuaderno",
            hint = "Nombre de la carpeta",
            initialValue = remember(items) { getUniqueFolderName(items) },
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                viewModel.createLocalFolder(context, name)
                showCreateFolderDialog = false
            }
        )
    }

    if (showCreateProjectDialog) {
        com.sketcher.sketchercompanionv1.ui.dialogs.CreateProjectDialog(
            initialName = remember(items) { getUniqueProjectName(items) },
            theme = theme,
            onDismiss = { showCreateProjectDialog = false },
            onConfirm = { name, templateFile, scaleRatio ->
                viewModel.createLocalProject(context, name, templateFile, scaleRatio)
                showCreateProjectDialog = false
            }
        )
    }

    val itemToRename = showRenameDialog
    if (itemToRename != null) {
        InputDialog(
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
            title = { Text("Confirmar eliminación", fontSize = 18.sp * scaleFactor) },
            text = {
                Text(
                    text = "¿Estás seguro de que deseas borrar \"${itemToDelete.name}\"? " +
                            if (itemToDelete is DashboardItem.Folder) "Se borrarán todos los proyectos contenidos en él." else "Esta acción no se puede deshacer.",
                    fontSize = 14.sp * scaleFactor
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
                TextButton(onClick = { showDeleteConfirmDialog = null }) {
                    Text("Cancelar", fontSize = 14.sp * scaleFactor)
                }
            }
        )
    }

    val itemToMove = showMoveDialog
    if (itemToMove != null) {
        MoveItemDialog(
            item = itemToMove,
            currentDir = currentDir ?: File(context.filesDir, "projects"),
            rootDir = File(context.filesDir, "projects"),
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
fun FolderCard(
    folder: DashboardItem.Folder,
    thumbnailCache: Map<String, android.graphics.Bitmap?>,
    scaleFactor: Float,
    theme: UiThemeConfig,
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
                color = Color(0xFFE5E5E5),
                shape = RoundedCornerShape(8.dp * scaleFactor)
            ),
        shape = RoundedCornerShape(8.dp * scaleFactor),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
                    .background(Color(0xFFF9F9F9)),
                contentAlignment = Alignment.Center
            ) {
                NotebookCover(
                    folder = folder,
                    thumbnailCache = thumbnailCache,
                    scaleFactor = scaleFactor,
                    modifier = Modifier.fillMaxSize()
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
                        color = Color(0xFF1C1B1F),
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
                                tint = Color(0xFF49454F)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renombrar", fontSize = 13.sp * scaleFactor) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Personalizar Portada", fontSize = 13.sp * scaleFactor) },
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
                    color = Color(0xFF49454F).copy(alpha = 0.7f)
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
    onClick: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
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
                color = Color(0xFFE5E5E5),
                shape = RoundedCornerShape(8.dp * scaleFactor)
            ),
        shape = RoundedCornerShape(8.dp * scaleFactor),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
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
                    .background(Color(0xFFF9F9F9)),
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
                        tint = Color(0xFF49454F).copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp * scaleFactor)
                    )
                }
                
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
                        color = Color(0xFF1C1B1F),
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
                                tint = Color(0xFF49454F)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Renombrar", fontSize = 13.sp * scaleFactor) },
                                onClick = {
                                    showMenu = false
                                    onRename()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Mover a Cuaderno", fontSize = 13.sp * scaleFactor) },
                                onClick = {
                                    showMenu = false
                                    onMove()
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
                    text = formattedDate,
                    fontSize = 11.sp * scaleFactor,
                    color = Color(0xFF49454F).copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun InputDialog(
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
        title = { Text(title, fontSize = 18.sp * scaleFactor) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(hint, fontSize = 13.sp * scaleFactor) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (text.isNotBlank()) onConfirm(text) },
                enabled = text.isNotBlank()
            ) {
                Text("Confirmar", fontSize = 14.sp * scaleFactor)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", fontSize = 14.sp * scaleFactor)
            }
        }
    )
}

@Composable
fun MoveItemDialog(
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
        title = { Text("Mover \"${item.name}\" a...", fontSize = 18.sp * scaleFactor) },
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp * scaleFactor)
                            )
                            Spacer(modifier = Modifier.width(12.dp * scaleFactor))
                            Text(
                                text = displayName,
                                fontSize = 14.sp * scaleFactor,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", fontSize = 14.sp * scaleFactor)
            }
        }
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
            }
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
                tint = if (selected) Color.White else theme.iconColor,
                modifier = Modifier.size(16.dp * scaleFactor)
            )
            Spacer(modifier = Modifier.width(6.dp * scaleFactor))
            Text(
                text = label,
                color = if (selected) Color.White else theme.iconColor,
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
                val src = android.graphics.Rect(0, 0, coverProjectBmp.width, coverProjectBmp.height)
                val dest = android.graphics.RectF(0f, 0f, width, height)
                nativeCanvas.drawBitmap(coverProjectBmp, src, dest, paint)
            } else {
                val fillStyle = folder.metadata.coverFill.toFillStyle(android.graphics.Color.LTGRAY)
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

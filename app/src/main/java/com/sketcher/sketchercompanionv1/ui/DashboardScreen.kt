package com.sketcher.sketchercompanionv1.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.DashboardItem
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

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

    // Dialog state
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showCreateProjectDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<DashboardItem?>(null) }
    var showMoveDialog by remember { mutableStateOf<DashboardItem?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf<DashboardItem?>(null) }

    // SAF launcher for importing external files
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importExternalProject(context, it) }
    }

    // Refresh items on launch or folder change
    LaunchedEffect(currentDir) {
        viewModel.refreshLocalItems()
    }

    val scaffoldBgColor = if (MaterialTheme.colorScheme.background != Color.Unspecified) {
        MaterialTheme.colorScheme.background
    } else {
        Color(0xFFF5F5F5)
    }

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
                        Text(
                            text = "Sketcher Companion",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp * scaleFactor,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Minimalist Action Buttons
                    TextButton(
                        onClick = { showCreateFolderDialog = true },
                        modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
                    ) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp * scaleFactor))
                        Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                        Text("Nuevo Cuaderno", fontSize = 13.sp * scaleFactor)
                    }

                    Button(
                        onClick = { showCreateProjectDialog = true },
                        modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp * scaleFactor))
                        Spacer(modifier = Modifier.width(4.dp * scaleFactor))
                        Text("Nuevo Dibujo", fontSize = 13.sp * scaleFactor)
                    }

                    IconButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.padding(horizontal = 4.dp * scaleFactor)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = "Importar externo",
                            modifier = Modifier.size(24.dp * scaleFactor),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = theme.barBackgroundColor,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        containerColor = scaffoldBgColor
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp * scaleFactor)
        ) {
            // Breadcrumbs / Folder navigation path
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp * scaleFactor)
            ) {
                val rootDir = File(context.filesDir, "projects")
                val isAtRoot = currentDir?.absolutePath == rootDir.absolutePath

                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Raíz",
                    tint = if (isAtRoot) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .size(20.dp * scaleFactor)
                        .clickable(enabled = !isAtRoot) {
                            viewModel.navigateToFolder(rootDir)
                        }
                )

                if (!isAtRoot && currentDir != null) {
                    val relativePath = currentDir.absolutePath.removePrefix(rootDir.absolutePath)
                    val parts = relativePath.split(File.separator).filter { it.isNotEmpty() }
                    
                    var tempPath = rootDir.absolutePath
                    parts.forEach { part ->
                        tempPath += File.separator + part
                        val targetPath = File(tempPath)
                        
                        Text(
                            text = " / ",
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            fontSize = 14.sp * scaleFactor
                        )
                        Text(
                            text = part,
                            color = if (targetPath.absolutePath == currentDir.absolutePath) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            fontSize = 14.sp * scaleFactor,
                            fontWeight = if (targetPath.absolutePath == currentDir.absolutePath) FontWeight.SemiBold else FontWeight.Normal,
                            modifier = Modifier.clickable(enabled = targetPath.absolutePath != currentDir.absolutePath) {
                                viewModel.navigateToFolder(targetPath)
                            }
                        )
                    }
                }
            }

            // Grid of items
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
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
                    modifier = Modifier.weight(1f)
                ) {
                    items(items) { item ->
                        when (item) {
                            is DashboardItem.Folder -> {
                                FolderCard(
                                    folder = item,
                                    scaleFactor = scaleFactor,
                                    theme = theme,
                                    onClick = { viewModel.navigateToFolder(File(item.path)) },
                                    onRename = { showRenameDialog = item },
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
        }
    }

    // Dialogs
    if (showCreateFolderDialog) {
        InputDialog(
            title = "Nuevo Cuaderno",
            hint = "Nombre de la carpeta",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = { name ->
                viewModel.createLocalFolder(context, name)
                showCreateFolderDialog = false
            }
        )
    }

    if (showCreateProjectDialog) {
        InputDialog(
            title = "Nuevo Dibujo",
            hint = "Nombre del proyecto",
            onDismiss = { showCreateProjectDialog = false },
            onConfirm = { name ->
                viewModel.createLocalProject(context, name)
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
}

@Composable
fun FolderCard(
    folder: DashboardItem.Folder,
    scaleFactor: Float,
    theme: UiThemeConfig,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp * scaleFactor)
            ),
        shape = RoundedCornerShape(8.dp * scaleFactor),
        colors = CardDefaults.cardColors(
            containerColor = theme.buttonColor
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp * scaleFactor)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    modifier = Modifier.size(36.dp * scaleFactor)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                            text = { Text("Borrar", fontSize = 13.sp * scaleFactor, color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp * scaleFactor))

            Text(
                text = folder.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp * scaleFactor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "${folder.itemCount} dibujos",
                fontSize = 11.sp * scaleFactor,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
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
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                shape = RoundedCornerShape(8.dp * scaleFactor)
            ),
        shape = RoundedCornerShape(8.dp * scaleFactor),
        colors = CardDefaults.cardColors(
            containerColor = theme.buttonColor
        )
    ) {
        Column {
            // Preview thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp * scaleFactor)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.03f)),
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.15f),
                        modifier = Modifier.size(48.dp * scaleFactor)
                    )
                }
            }

            // Info and options
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
                        color = MaterialTheme.colorScheme.onSurface,
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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

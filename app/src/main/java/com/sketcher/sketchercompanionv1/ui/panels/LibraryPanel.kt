package com.sketcher.sketchercompanionv1.ui.panels

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.ComponentInstance
import com.sketcher.sketchercompanionv1.LibraryComponent
import com.sketcher.sketchercompanionv1.LibraryFolder
import com.sketcher.sketchercompanionv1.LibraryItem
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.SvgElement
import com.sketcher.sketchercompanionv1.GroupElement
import com.sketcher.sketchercompanionv1.dto.ImageEditState
import com.sketcher.sketchercompanionv1.ui.dialogs.ImageEditDialog
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfImportDialog
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import com.sketcher.sketchercompanionv1.ui.AppIconButton
import com.sketcher.sketchercompanionv1.ui.CloudSyncStatusIndicator
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudQueue
import java.io.File
import android.graphics.BitmapFactory

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryPanel(viewModel: SketcherViewModel) {
    val theme by viewModel.themeConfig.collectAsState()
    val scaler = LocalUiScaler.current
    val context = LocalContext.current
    val libraryItems by viewModel.globalLibraryItems.collectAsState()
    
    val selection = viewModel.selectionManager.selectedElements
    val canAddToLibrary = selection.size == 1 && selection.first() is ComponentInstance

    var currentFolderId by remember { mutableStateOf<String?>(null) }
    var selectedItemIds by remember { mutableStateOf(setOf<String>()) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var isGridView by remember { mutableStateOf(true) }
    
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<LibraryItem?>(null) }
    var newName by remember { mutableStateOf("") }
    
    var importImageEditState by remember { mutableStateOf<ImageEditState?>(null) }
    var componentToScale by remember { mutableStateOf<LibraryComponent?>(null) }
    var dxfImportUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var showDxfImportDialog by remember { mutableStateOf(false) }

    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { fileUri ->
            val contentResolver = context.contentResolver
            val mimeType = contentResolver.getType(fileUri)
            val extension = fileUri.path?.substringAfterLast('.')?.lowercase(java.util.Locale.ROOT)
            
            if (mimeType?.startsWith("image/") == true || extension in listOf("png", "jpg", "jpeg", "webp")) {
                try {
                        val originalBmp = com.sketcher.sketchercompanionv1.utils.BitmapUtils.loadScaledBitmap(context, fileUri, 1024)
                        if (originalBmp != null) {
                            importImageEditState = ImageEditState(
                                isNewImport = true,
                                elementId = null,
                                originalBitmap = originalBmp,
                                filename = "imported_lib_img.png"
                            )
                        }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (extension == "svg") {
                try {
                    contentResolver.openInputStream(fileUri)?.use { stream ->
                        val bytes = stream.readBytes()
                        val content = String(bytes, Charsets.UTF_8)
                        val svgElement = SvgElement("svg_${java.util.UUID.randomUUID()}", "import.svg", content)
                        viewModel.addSvgToGlobalLibrary(context, "SVG Importado", svgElement, currentFolderId)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else if (extension == "dxf") {
                dxfImportUri = fileUri
                showDxfImportDialog = true
            } else {
                android.widget.Toast.makeText(context, "Formato no soportado", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    val currentItems = libraryItems.filter { it.parentId == currentFolderId }
    val currentFolder = libraryItems.find { it.id == currentFolderId } as? LibraryFolder

    // Clear selection when changing folders
    LaunchedEffect(currentFolderId) {
        selectedItemIds = emptySet()
        isSelectionMode = false
    }

    LaunchedEffect(Unit) {
        viewModel.loadGlobalLibrary(context)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(scaler.smallMargin),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isSelectionMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIconButton(
                        onClick = { 
                            isSelectionMode = false
                            selectedItemIds = emptySet()
                        },
                        icon = Icons.Default.Close,
                        contentDescription = "Cancelar",
                        tint = theme.iconColor,
                        buttonSize = 24.dp
                    )
                    Spacer(modifier = Modifier.width(4.sdp))
                    Text(
                        "${selectedItemIds.size} seleccionados",
                        color = theme.iconColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.sdp), verticalAlignment = Alignment.CenterVertically) {
                    if (selectedItemIds.isNotEmpty()) {
                        OutlinerActionButton(Icons.Default.DriveFileMove, "Mover", theme.iconColor, backgroundColor = theme.buttonColor) {
                            showMoveDialog = true
                        }
                        OutlinerActionButton(Icons.Default.Delete, "Eliminar", MaterialTheme.colorScheme.error, backgroundColor = theme.buttonColor) {
                            showDeleteConfirmDialog = true
                        }
                    }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (currentFolderId != null) {
                        AppIconButton(
                            onClick = { currentFolderId = currentFolder?.parentId },
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = theme.iconColor,
                            buttonSize = 24.dp
                        )
                        Spacer(modifier = Modifier.width(4.sdp))
                    }
                    Text(
                        currentFolder?.name ?: "LIBRERÍA",
                        color = theme.iconColor,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(4.sdp), verticalAlignment = Alignment.CenterVertically) {
                    if (!viewModel.showDashboard && canAddToLibrary) {
                        OutlinerActionButton(Icons.Default.Add, "Añadir a Librería", theme.iconColor, backgroundColor = theme.buttonColor) {
                            viewModel.addToGlobalLibrary(context, "Nuevo Componente", currentFolderId)
                        }
                    }

                    AppIconButton(
                        onClick = { isGridView = !isGridView },
                        icon = if (isGridView) Icons.Default.ViewList else Icons.Default.GridView,
                        contentDescription = "Toggle Grid/List",
                        tint = theme.iconColor,
                        buttonSize = 24.dp
                    )
                    
                    OutlinerActionButton(Icons.Default.CreateNewFolder, "Nueva Carpeta", theme.iconColor, backgroundColor = theme.buttonColor) {
                        showNewFolderDialog = true
                        newName = "Nueva Carpeta"
                    }

                    OutlinerActionButton(Icons.Default.FileUpload, "Subir", theme.iconColor, backgroundColor = theme.buttonColor) {
                        uploadLauncher.launch(arrayOf("*/*"))
                    }
                }
            }
        }
        
        HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))
        
        // --- ADD TO CANVAS BUTTON (Only shows if a component is selected) ---
        val selectedItem = if (!isSelectionMode && selectedItemIds.size == 1) currentItems.find { it.id == selectedItemIds.first() } else null
        if (selectedItem != null && selectedItem is LibraryComponent && !viewModel.showDashboard) {
            Button(
                onClick = { viewModel.instantiateFromGlobalLibrary(selectedItem) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = scaler.smallMargin, vertical = 4.sdp).height(32.sdp),
                contentPadding = PaddingValues(horizontal = 8.sdp, vertical = 0.sdp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)
            ) {
                Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(14.sdp), tint = theme.barBackgroundColor)
                Spacer(Modifier.width(4.sdp))
                Text("Insertar", style = MaterialTheme.typography.labelSmall, color = theme.barBackgroundColor)
            }
        }

        val isLibrarySynced = viewModel.isLibrarySynced(context)

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.sdp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(scaler.smallMargin),
                horizontalArrangement = Arrangement.spacedBy(ScalerConstants.ITEM_SPACING.dp),
                verticalArrangement = Arrangement.spacedBy(ScalerConstants.ITEM_SPACING.dp)
            ) {
                gridItems(currentItems) { item ->
                    val assetsDir = viewModel.getLibraryAssetsDir(context)
                    LibraryItemGridCell(
                        item = item,
                        theme = theme,
                        isSelected = item.id in selectedItemIds,
                        isSynced = isLibrarySynced,
                        onClick = {
                            if (isSelectionMode) {
                                selectedItemIds = if (item.id in selectedItemIds) selectedItemIds - item.id else selectedItemIds + item.id
                                if (selectedItemIds.isEmpty()) isSelectionMode = false
                            } else {
                                if (item is LibraryFolder) {
                                    currentFolderId = item.id
                                } else {
                                    selectedItemIds = if (selectedItemIds.contains(item.id)) emptySet() else setOf(item.id)
                                }
                            }
                        },
                        onLongClick = {
                            isSelectionMode = true
                            selectedItemIds = selectedItemIds + item.id
                        },
                        onRename = { 
                            showRenameDialog = item
                            newName = item.name 
                        },
                        onDelete = { viewModel.deleteLibraryItem(context, item.id) },
                        onEdit = if (item is LibraryComponent) {
                            {
                                val imageElement = item.definition.elements.firstOrNull { it is com.sketcher.sketchercompanionv1.ImageElement } as? com.sketcher.sketchercompanionv1.ImageElement
                                if (imageElement != null) {
                                    importImageEditState = ImageEditState(
                                        isNewImport = false,
                                        elementId = item.id,
                                        originalBitmap = imageElement.bitmap,
                                        filename = imageElement.imageFileName
                                    )
                                } else {
                                    componentToScale = item
                                }
                            }
                        } else null,
                        assetsDir = assetsDir
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(scaler.smallMargin),
                verticalArrangement = Arrangement.spacedBy(ScalerConstants.ITEM_SPACING.dp)
            ) {
                items(currentItems) { item ->
                    val assetsDir = viewModel.getLibraryAssetsDir(context)
                    LibraryItemListCell(
                        item = item,
                        theme = theme,
                        isSelected = item.id in selectedItemIds,
                        isSynced = isLibrarySynced,
                        onClick = {
                            if (isSelectionMode) {
                                selectedItemIds = if (item.id in selectedItemIds) selectedItemIds - item.id else selectedItemIds + item.id
                                if (selectedItemIds.isEmpty()) isSelectionMode = false
                            } else {
                                if (item is LibraryFolder) {
                                    currentFolderId = item.id
                                } else {
                                    selectedItemIds = if (selectedItemIds.contains(item.id)) emptySet() else setOf(item.id)
                                }
                            }
                        },
                        onLongClick = {
                            isSelectionMode = true
                            selectedItemIds = selectedItemIds + item.id
                        },
                        onRename = { 
                            showRenameDialog = item
                            newName = item.name 
                        },
                        onDelete = { viewModel.deleteLibraryItem(context, item.id) },
                        onEdit = if (item is LibraryComponent) {
                            {
                                val imageElement = item.definition.elements.firstOrNull { it is com.sketcher.sketchercompanionv1.ImageElement } as? com.sketcher.sketchercompanionv1.ImageElement
                                if (imageElement != null) {
                                    importImageEditState = ImageEditState(
                                        isNewImport = false,
                                        elementId = item.id,
                                        originalBitmap = imageElement.bitmap,
                                        filename = imageElement.imageFileName
                                    )
                                } else {
                                    componentToScale = item
                                }
                            }
                        } else null,
                        assetsDir = assetsDir
                    )
                }
            }
        }
        
        if (currentItems.isEmpty()) {
            Text(
                "Carpeta vacía",
                color = theme.iconColor.copy(alpha = 0.5f),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
    
    // --- DIALOGS ---
    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            title = { Text("Nueva Carpeta", color = theme.iconColor) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createLibraryFolder(context, newName, currentFolderId)
                        showNewFolderDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.buttonColor,
                        contentColor = theme.iconColor
                    )
                ) { Text("Crear") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showNewFolderDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)
                ) { Text("Cancelar") }
            },
            containerColor = theme.barBackgroundColor
        )
    }
    
    showRenameDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Renombrar", color = theme.iconColor) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameLibraryItem(context, item.id, newName)
                        showRenameDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.buttonColor,
                        contentColor = theme.iconColor
                    )
                ) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRenameDialog = null },
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)
                ) { Text("Cancelar") }
            },
            containerColor = theme.barBackgroundColor
        )
    }
    
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Eliminar elementos", color = theme.iconColor) },
            text = { Text("¿Estás seguro de que deseas eliminar los ${selectedItemIds.size} elementos seleccionados?", color = theme.iconColor) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteLibraryItems(context, selectedItemIds)
                        selectedItemIds = emptySet()
                        isSelectionMode = false
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = Color.White)
                ) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)
                ) { Text("Cancelar") }
            },
            containerColor = theme.barBackgroundColor
        )
    }

    if (showMoveDialog) {
        MoveItemsDialog(
            theme = theme,
            libraryItems = libraryItems,
            onDismiss = { showMoveDialog = false },
            onMove = { targetFolderId ->
                viewModel.moveLibraryItems(context, selectedItemIds, targetFolderId)
                selectedItemIds = emptySet()
                isSelectionMode = false
                showMoveDialog = false
            }
        )
    }

    // --- IMPORT DIALOGS ---
    val imgState = importImageEditState
    if (imgState != null) {
        ImageEditDialog(
            state = imgState,
            theme = theme,
            scaleConfig = viewModel.scaleConfig,
            currentUnit = viewModel.currentUnit,
            onDismiss = { importImageEditState = null },
            onConfirm = { processedBmp, transColors, tol, cropRect, cropPath, transTols, rotation, flipH, flipV, scale ->
                if (imgState.isNewImport) {
                    viewModel.addImageToGlobalLibrary(
                        context = context,
                        name = "Imagen Importada",
                        bitmap = processedBmp,
                        transparentColors = transColors,
                        tolerance = tol,
                        cropRect = cropRect,
                        cropPath = cropPath,
                        transparentColorTolerances = transTols,
                        rotation = rotation,
                        flipHorizontal = flipH,
                        flipVertical = flipV,
                        calibrationScaleFactor = scale,
                        parentId = currentFolderId
                    )
                } else {
                    val itemId = imgState.elementId
                    if (itemId != null) {
                        viewModel.updateImageInGlobalLibrary(
                            context = context,
                            itemId = itemId,
                            bitmap = processedBmp,
                            transparentColors = transColors,
                            tolerance = tol,
                            cropRect = cropRect,
                            cropPath = cropPath,
                            transparentColorTolerances = transTols,
                            rotation = rotation,
                            flipHorizontal = flipH,
                            flipVertical = flipV,
                            calibrationScaleFactor = scale
                        )
                    }
                }
                importImageEditState = null
            }
        )
    }

    if (showDxfImportDialog && dxfImportUri != null) {
        DxfImportDialog(
            uri = dxfImportUri!!,
            onDismiss = { showDxfImportDialog = false },
            onImport = { dxfData, scaleToFit, defaultStrokeWidth, fillClosedShapes, selectedUnit ->
                viewModel.addDxfToGlobalLibrary(
                    context = context,
                    name = "DXF Importado",
                    data = dxfData,
                    scaleToFit = scaleToFit,
                    defaultStrokeWidth = defaultStrokeWidth,
                    fillClosedShapes = fillClosedShapes,
                    sourceUnit = selectedUnit,
                    parentId = currentFolderId
                )
                showDxfImportDialog = false
            }
        )
    }
    
    componentToScale?.let { comp ->
        com.sketcher.sketchercompanionv1.ui.dialogs.ComponentScaleDialog(
            component = comp,
            componentLibrary = viewModel.componentLibrary,
            currentUnit = viewModel.currentUnit,
            basePixelsPerMillimeter = viewModel.scaleConfig.basePixelsPerMillimeter,
            theme = theme,
            onDismiss = { componentToScale = null },
            onConfirm = { newScale ->
                viewModel.updateComponentScaleInGlobalLibrary(context, comp.id, newScale)
                componentToScale = null
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemGridCell(
    item: LibraryItem,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    isSelected: Boolean,
    isSynced: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    assetsDir: File
) {
    val scaler = LocalUiScaler.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.sdp))
            .background(if (isSelected) theme.highlightColor.copy(alpha = 0.2f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(4.sdp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (item is LibraryFolder) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = theme.iconColor,
                        modifier = Modifier.size(40.sdp)
                    )
                } else if (item is LibraryComponent) {
                    var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
                    LaunchedEffect(item.thumbnailFileName) {
                        if (item.thumbnailFileName != null) {
                            val file = File(assetsDir, item.thumbnailFileName)
                            if (file.exists()) {
                                bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                            }
                        }
                    }
                    
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!,
                            contentDescription = item.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Icon(
                            Icons.Default.Widgets,
                            contentDescription = null,
                            tint = theme.iconColor,
                            modifier = Modifier.size(32.sdp)
                        )
                    }
                }
            }
            
            Text(
                item.name,
                color = theme.iconColor,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        
        // Menu button in top right
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            AppIconButton(
                onClick = { showMenu = true },
                icon = Icons.Default.MoreVert,
                contentDescription = "Menu",
                tint = theme.iconColor,
                buttonSize = 16.dp,
                touchSize = 24.dp
            )
            
            val scaleFactor = scaler.scaleFactor
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(theme.barBackgroundColor)
            ) {
                if (onEdit != null) {
                    DropdownMenuItem(
                        text = { Text("Editar", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                        onClick = { showMenu = false; onEdit() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Renombrar", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                    onClick = { showMenu = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Eliminar", fontSize = 13.sp * scaleFactor, color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }

        CloudSyncStatusIndicator(
            isSynced = isSynced,
            scaleFactor = scaler.scaleFactor,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.sdp)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryItemListCell(
    item: LibraryItem,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    isSelected: Boolean,
    isSynced: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onEdit: (() -> Unit)? = null,
    assetsDir: File
) {
    val scaler = LocalUiScaler.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.sdp))
            .background(if (isSelected) theme.highlightColor.copy(alpha = 0.2f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(8.sdp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (item is LibraryFolder) {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = theme.iconColor,
                modifier = Modifier.size(24.sdp)
            )
        } else if (item is LibraryComponent) {
            var bitmap by remember { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
            LaunchedEffect(item.thumbnailFileName) {
                if (item.thumbnailFileName != null) {
                    val file = File(assetsDir, item.thumbnailFileName)
                    if (file.exists()) {
                        bitmap = BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
                    }
                }
            }
            
            if (bitmap != null) {
                Image(
                    bitmap = bitmap!!,
                    contentDescription = item.name,
                    modifier = Modifier.size(24.sdp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    Icons.Default.Widgets,
                    contentDescription = null,
                    tint = theme.iconColor,
                    modifier = Modifier.size(24.sdp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(8.sdp))
        
        Text(
            item.name,
            color = theme.iconColor,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium
        )

        CloudSyncStatusIndicator(
            isSynced = isSynced,
            scaleFactor = scaler.scaleFactor
        )

        Spacer(modifier = Modifier.width(4.sdp))
        
        Box {
            AppIconButton(
                onClick = { showMenu = true },
                icon = Icons.Default.MoreVert,
                contentDescription = "Menu",
                tint = theme.iconColor,
                buttonSize = 24.dp
            )
            
            val scaleFactor = scaler.scaleFactor
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(theme.barBackgroundColor)
            ) {
                if (onEdit != null) {
                    DropdownMenuItem(
                        text = { Text("Editar", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                        onClick = { showMenu = false; onEdit() }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Renombrar", fontSize = 13.sp * scaleFactor, color = theme.iconColor) },
                    onClick = { showMenu = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Eliminar", fontSize = 13.sp * scaleFactor, color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}

@Composable
fun MoveItemsDialog(
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    libraryItems: List<LibraryItem>,
    onDismiss: () -> Unit,
    onMove: (String?) -> Unit
) {
    val folders = libraryItems.filterIsInstance<LibraryFolder>()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mover a...", color = theme.iconColor) },
        text = {
            LazyColumn {
                item {
                    Text(
                        text = "📁 Raíz",
                        color = theme.iconColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMove(null) }
                            .padding(vertical = 12.dp)
                    )
                }
                items(folders) { folder ->
                    Text(
                        text = "📁 ${folder.name}",
                        color = theme.iconColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMove(folder.id) }
                            .padding(vertical = 12.dp, horizontal = 16.dp)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)
            ) { Text("Cancelar") }
        },
        containerColor = theme.barBackgroundColor
    )
}

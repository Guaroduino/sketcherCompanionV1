package com.sketcher.sketchercompanionv1.ui.panels

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.sketcher.sketchercompanionv1.ComponentInstance
import com.sketcher.sketchercompanionv1.LibraryComponent
import com.sketcher.sketchercompanionv1.LibraryFolder
import com.sketcher.sketchercompanionv1.LibraryItem
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.sdp
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
    var selectedLibraryItemId by remember { mutableStateOf<String?>(null) }
    var isGridView by remember { mutableStateOf(true) }
    
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<LibraryItem?>(null) }
    var newName by remember { mutableStateOf("") }
    
    val currentItems = libraryItems.filter { it.parentId == currentFolderId }
    val currentFolder = libraryItems.find { it.id == currentFolderId } as? LibraryFolder

    // Clear selection when changing folders
    LaunchedEffect(currentFolderId) {
        selectedLibraryItemId = null
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentFolderId != null) {
                    IconButton(onClick = { currentFolderId = currentFolder?.parentId }, modifier = Modifier.size(24.sdp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = theme.iconColor)
                    }
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
                // Toggle Grid/List
                IconButton(onClick = { isGridView = !isGridView }, modifier = Modifier.size(24.sdp)) {
                    Icon(if (isGridView) Icons.Default.ViewList else Icons.Default.GridView, null, tint = theme.iconColor)
                }
                
                OutlinerActionButton(Icons.Default.CreateNewFolder, "Nueva Carpeta", theme.iconColor) {
                    showNewFolderDialog = true
                    newName = "Nueva Carpeta"
                }
                
                OutlinerActionButton(Icons.Default.Add, "Añadir a Librería", theme.iconColor, enabled = canAddToLibrary) {
                    viewModel.addToGlobalLibrary(context, "Nuevo Componente", currentFolderId)
                }
            }
        }
        
        HorizontalDivider(color = theme.iconColor.copy(alpha = 0.1f))
        
        // --- ADD TO CANVAS BUTTON (Only shows if a component is selected) ---
        val selectedItem = currentItems.find { it.id == selectedLibraryItemId }
        if (selectedItem != null && selectedItem is LibraryComponent) {
            Button(
                onClick = { viewModel.instantiateFromGlobalLibrary(selectedItem) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = scaler.smallMargin, vertical = 4.sdp),
                colors = ButtonDefaults.buttonColors(containerColor = theme.activeColor)
            ) {
                Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(16.sdp))
                Spacer(Modifier.width(8.dp))
                Text("Insertar en Lienzo")
            }
        }

        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(80.sdp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(scaler.smallMargin),
                horizontalArrangement = Arrangement.spacedBy(ScalerConstants.ITEM_SPACING.dp),
                verticalArrangement = Arrangement.spacedBy(ScalerConstants.ITEM_SPACING.dp)
            ) {
                gridItems(currentItems) { item ->
                    LibraryItemGridCell(
                        item = item,
                        theme = theme,
                        isSelected = item.id == selectedLibraryItemId,
                        onClick = {
                            if (item is LibraryFolder) {
                                currentFolderId = item.id
                            } else {
                                selectedLibraryItemId = if (selectedLibraryItemId == item.id) null else item.id
                            }
                        },
                        onRename = { 
                            showRenameDialog = item
                            newName = item.name 
                        },
                        onDelete = { viewModel.deleteLibraryItem(context, item.id) }
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
                    LibraryItemListCell(
                        item = item,
                        theme = theme,
                        isSelected = item.id == selectedLibraryItemId,
                        onClick = {
                            if (item is LibraryFolder) {
                                currentFolderId = item.id
                            } else {
                                selectedLibraryItemId = if (selectedLibraryItemId == item.id) null else item.id
                            }
                        },
                        onRename = { 
                            showRenameDialog = item
                            newName = item.name 
                        },
                        onDelete = { viewModel.deleteLibraryItem(context, item.id) }
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
            title = { Text("Nueva Carpeta") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.createLibraryFolder(context, newName, currentFolderId)
                    showNewFolderDialog = false
                }) { Text("Crear") }
            },
            dismissButton = {
                TextButton(onClick = { showNewFolderDialog = false }) { Text("Cancelar") }
            }
        )
    }
    
    showRenameDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showRenameDialog = null },
            title = { Text("Renombrar") },
            text = {
                TextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.renameLibraryItem(context, item.id, newName)
                    showRenameDialog = null
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun LibraryItemGridCell(
    item: LibraryItem,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val scaler = LocalUiScaler.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.sdp))
            .background(if (isSelected) theme.activeColor.copy(alpha = 0.2f) else theme.toolbarBg)
            .clickable(onClick = onClick)
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
                            val file = File(File(context.filesDir, "library_assets"), item.thumbnailFileName)
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
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(24.sdp)
            ) {
                Icon(Icons.Default.MoreVert, null, tint = theme.iconColor, modifier = Modifier.size(16.sdp))
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(text = { Text("Renombrar") }, onClick = { showMenu = false; onRename() })
                DropdownMenuItem(text = { Text("Eliminar") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

@Composable
fun LibraryItemListCell(
    item: LibraryItem,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val scaler = LocalUiScaler.current
    val context = LocalContext.current
    var showMenu by remember { mutableStateOf(false) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.sdp))
            .background(if (isSelected) theme.activeColor.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
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
                    val file = File(File(context.filesDir, "library_assets"), item.thumbnailFileName)
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
        
        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(24.sdp)) {
                Icon(Icons.Default.MoreVert, null, tint = theme.iconColor)
            }
            
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(text = { Text("Renombrar") }, onClick = { showMenu = false; onRename() })
                DropdownMenuItem(text = { Text("Eliminar") }, onClick = { showMenu = false; onDelete() })
            }
        }
    }
}

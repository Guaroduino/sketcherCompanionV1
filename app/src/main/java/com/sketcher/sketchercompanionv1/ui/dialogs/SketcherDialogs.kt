package com.sketcher.sketchercompanionv1

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.PointF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.view.WindowCompat
import com.sketcher.sketchercompanionv1.ui.FileMenu
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfImportDialog // Import
import com.sketcher.sketchercompanionv1.ui.dialogs.DxfExportDialog // Import
import com.sketcher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme
import com.sketcher.sketchercompanionv1.GroupElement

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.core.net.toUri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Save // Save Icon
import androidx.compose.material.icons.filled.FolderOpen // Load Icon
import androidx.compose.material.icons.filled.FormatPaint // Fill Toggle Icon
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Palette // Background Color Icon
import androidx.compose.material.icons.filled.Straighten // Scale Icon
import com.sketcher.sketchercompanionv1.ui.ColorPickerDialog
import com.sketcher.sketchercompanionv1.ui.ScaleIndicator
import com.sketcher.sketchercompanionv1.ui.ToolSettingsPopup
import com.sketcher.sketchercompanionv1.ui.LazyStrokePopup
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.UnitUtils
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Grid4x4
import kotlin.math.round
import kotlin.math.roundToInt
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Close

import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Schema
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Build
import androidx.compose.ui.res.stringResource
import android.graphics.BitmapFactory
import java.io.InputStream
import androidx.compose.material.icons.filled.Image // Import Icon

fun decodeSampledBitmapFromUri(context: Context, uri: android.net.Uri, reqWidth: Int, reqHeight: Int): android.graphics.Bitmap? {
    try {
        var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
        
        // First decode with inJustDecodeBounds=true to check dimensions
        val options = BitmapFactory.Options()
        options.inJustDecodeBounds = true
        BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()

        // Calculate inSampleSize
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)

        // Decode bitmap with inSampleSize set
        options.inJustDecodeBounds = false
        inputStream = context.contentResolver.openInputStream(uri)
        val bitmap = BitmapFactory.decodeStream(inputStream, null, options)
        inputStream?.close()
        return bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    // Raw height and width of image
    val (height: Int, width: Int) = options.run { outHeight to outWidth }
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight: Int = height / 2
        val halfWidth: Int = width / 2

        // Calculate the largest inSampleSize value that is a power of 2 and keeps both
        // height and width larger than the requested height and width.
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}


@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    isRotationLocked: Boolean,
    onToggleRotationLock: () -> Unit,
    isPalmRejectionEnabled: Boolean,
    onTogglePalmRejection: () -> Unit,
    interfaceScale: Float,
    onInterfaceScaleChanged: (Float) -> Unit,
    isDebugWireframe: Boolean,
    onToggleDebugWireframe: () -> Unit,
    currentScaleConfig: ScaleConfig,
    onUpdateProjectConfig: (String, Float) -> Unit,
    toolbarBackgroundColor: Int,
    onToolbarBackgroundColorChanged: (Int) -> Unit,
    toolbarAlpha: Float,
    onToolbarAlphaChanged: (Float) -> Unit,
    isToolbarBlurEnabled: Boolean,
    onToggleToolbarBlur: () -> Unit,
    showTooltips: Boolean,
    onToggleTooltips: () -> Unit
) {
    var resolutionText by remember { mutableStateOf(currentScaleConfig.basePixelsPerMillimeter.toString()) }
    var selectedUnit by remember { mutableStateOf(DistanceUnit.fromSymbol(currentScaleConfig.unitName)) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("${stringResource(R.string.settings_title)} & ${stringResource(R.string.project_settings)}", style = MaterialTheme.typography.titleLarge)
            
            // --- PROJECT CONFIGURATION ---
            Text(stringResource(R.string.project_settings), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            // Resolution
            Column {
                Text(stringResource(R.string.settings_base_resolution), style = MaterialTheme.typography.labelMedium)
                OutlinedTextField(
                    value = resolutionText,
                    onValueChange = { resolutionText = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(stringResource(R.string.settings_resolution_hint), fontSize = 10.sp, color = Color.Gray)
            }
            
            // Unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_unit))
                Row {
                    DistanceUnit.entries.forEach { unit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier
                                .clickable { selectedUnit = unit }
                                .padding(4.dp)
                        ) {
                            RadioButton(
                                selected = (selectedUnit == unit),
                                onClick = { selectedUnit = unit }
                            )
                            Text(text = unit.symbol, modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
            }
            
            // Info Calculation
            val resolution = resolutionText.toFloatOrNull() ?: 0f
            if (resolution > 0) {
                 val unitMm = selectedUnit.toMillimeters
                 val pixels = unitMm * resolution
                 Text(
                     text = "1 ${selectedUnit.symbol} = ${pixels.toInt()} px",
                     style = MaterialTheme.typography.bodySmall,
                     fontWeight = FontWeight.Bold
                 )
            }
            
            HorizontalDivider()
            
            // --- APP SETTINGS ---
            Text(stringResource(R.string.app_prefs), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_lock_rotation))
                Switch(checked = isRotationLocked, onCheckedChange = { onToggleRotationLock() })
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_stylus_only))
                Switch(checked = isPalmRejectionEnabled, onCheckedChange = { onTogglePalmRejection() })
            }
            
            Column {
                Text("${stringResource(R.string.settings_interface_scale)}: ${(interfaceScale * 100).toInt()}%")
                Slider(
                    value = interfaceScale,
                    onValueChange = onInterfaceScaleChanged,
                    valueRange = 0.5f..2.0f
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mostrar Ayudas (Tooltips)")
                Switch(checked = showTooltips, onCheckedChange = { onToggleTooltips() })
            }

            HorizontalDivider()
            
            // --- TOOLBAR APPEARANCE ---
            Text("Apariencia de Barras", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            
            var showToolbarColorPicker by remember { mutableStateOf(false) }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color de Fondo")
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(toolbarBackgroundColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showToolbarColorPicker = true }
                )
            }
            
            if (showToolbarColorPicker) {
                ColorPickerDialog(
                    initialColor = toolbarBackgroundColor,
                    onDismiss = { showToolbarColorPicker = false },
                    onColorSelected = { 
                        onToolbarBackgroundColorChanged(it)
                        showToolbarColorPicker = false
                    }
                )
            }
            
            Column {
                Text("Transparencia: ${(toolbarAlpha * 100).toInt()}%")
                Slider(
                    value = toolbarAlpha,
                    onValueChange = onToolbarAlphaChanged,
                    valueRange = 0.0f..1.0f
                )
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Efecto de Desenfoque (Blur)")
                Switch(checked = isToolbarBlurEnabled, onCheckedChange = { onToggleToolbarBlur() })
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.settings_debug_wireframe))
                Switch(checked = isDebugWireframe, onCheckedChange = { onToggleDebugWireframe() })
            }
            
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(onClick = onDismiss, colors = ButtonDefaults.textButtonColors()) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val res = resolutionText.toFloatOrNull()
                    if (res != null) {
                        onUpdateProjectConfig(selectedUnit.symbol, res)
                    }
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}

@Composable
fun LayerManagerDialog(
    layers: List<Layer>,
    activeLayerIndex: Int,
    onToggleVisibility: (Int) -> Unit,
    onOpacityChanged: (Int, Float) -> Unit,
    onActiveLayerChanged: (Int) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.layer_title), style = MaterialTheme.typography.titleLarge)
                IconButton(onClick = onDismiss) {
                    Text("X", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
            }

            Divider()

            // Layers List (Reversed visual order)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
            ) {
                // Display in reverse order so top layer is at top of list
                // We need to map index correctly back to original list
                val reversedIndices = layers.indices.reversed().toList()
                
                itemsIndexed(reversedIndices) { _, originalIndex ->
                    val layer = layers[originalIndex]
                    val isActive = originalIndex == activeLayerIndex
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isActive) Color.LightGray.copy(alpha = 0.5f) else Color.Transparent)
                            .clickable { onActiveLayerChanged(originalIndex) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Visibility
                        IconButton(
                            onClick = { onToggleVisibility(originalIndex) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                if (layer.isVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "Toggle Visibility",
                                tint = if (layer.isVisible) Color.Black else Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Name
                        Text(
                            text = layer.id,
                            modifier = Modifier.weight(1f),
                            fontWeight = if (isActive) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                        )
                        
                        // Opacity
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${(layer.opacity * 100).toInt()}%", fontSize = 10.sp)
                            Slider(
                                value = layer.opacity,
                                onValueChange = { onOpacityChanged(originalIndex, it) },
                                valueRange = 0f..1f,
                                modifier = Modifier.width(80.dp).height(20.dp)
                            )
                        }
                    }
                }
            }

            Divider()

            // Footer Controls
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // Add
                IconButton(onClick = onAddLayer) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.layer_add))
                }
                
                // Move Up (Visual Up = Higher Index)
                IconButton(onClick = onMoveUp) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                }
                
                // Move Down (Visual Down = Lower Index)
                IconButton(onClick = onMoveDown) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                }
                
                // Delete
                IconButton(onClick = onDeleteLayer) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Active", tint = Color.Red)
                }
            }
        }
    }
}



@Composable
fun GridSettingsDialog(
    currentGridConfig: GridConfig,
    isSnapEnabled: Boolean,
    currentUnit: DistanceUnit,
    onUpdateGrid: (Boolean, Float, Int, Int, Int) -> Unit,
    onUpdateSnap: (Boolean) -> Unit,
    onUpdateUnit: (DistanceUnit) -> Unit,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(currentGridConfig.isVisible) }
    var spacingText by remember { mutableStateOf(currentGridConfig.spacing.toString()) }
    var expandedUnit by remember { mutableStateOf(false) }
    
    // Colors
    var primaryColor by remember { mutableIntStateOf(currentGridConfig.color) }
    var secondaryColor by remember { mutableIntStateOf(currentGridConfig.secondaryColor) }
    var tertiaryColor by remember { mutableIntStateOf(currentGridConfig.tertiaryColor) }

    // Color Pickers State
    var showPrimaryPicker by remember { mutableStateOf(false) }
    var showSecondaryPicker by remember { mutableStateOf(false) }
    var showTertiaryPicker by remember { mutableStateOf(false) }

    val updateConfig = {
        val spacing = spacingText.toFloatOrNull() ?: 1f
        if (spacing > 0) {
            onUpdateGrid(isVisible, spacing, primaryColor, secondaryColor, tertiaryColor)
        }
    }

    if (showPrimaryPicker) {
        ColorPickerDialog(
            initialColor = primaryColor,
            onDismiss = { showPrimaryPicker = false },
            onColorSelected = { 
                primaryColor = it
                showPrimaryPicker = false
                updateConfig()
            }
        )
    }
    if (showSecondaryPicker) {
        ColorPickerDialog(
            initialColor = secondaryColor,
            onDismiss = { showSecondaryPicker = false },
            onColorSelected = { 
                secondaryColor = it
                showSecondaryPicker = false
                updateConfig()
            }
        )
    }
    if (showTertiaryPicker) {
        ColorPickerDialog(
            initialColor = tertiaryColor,
            onDismiss = { showTertiaryPicker = false },
            onColorSelected = { 
                tertiaryColor = it
                showTertiaryPicker = false
                updateConfig()
            }
        )
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(340.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.White)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.grid_title), style = MaterialTheme.typography.titleLarge)
            
            // Grid Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.grid_show))
                Switch(
                    checked = isVisible, 
                    onCheckedChange = { 
                        isVisible = it
                        updateConfig()
                    }
                )
            }

            // Snap Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.grid_snap))
                Switch(
                    checked = isSnapEnabled, 
                    onCheckedChange = { onUpdateSnap(it) }
                )
            }
            
            HorizontalDivider()

            // Unit Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.label_unit))
                Box {
                    Button(onClick = { expandedUnit = true }) {
                        Text(currentUnit.symbol)
                    }
                    DropdownMenu(expanded = expandedUnit, onDismissRequest = { expandedUnit = false }) {
                        DistanceUnit.entries.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit.symbol) },
                                onClick = {
                                    onUpdateUnit(unit)
                                    expandedUnit = false
                                }
                            )
                        }
                    }
                }
            }

            // Spacing Input
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${stringResource(R.string.grid_spacing)} (${currentUnit.symbol})")
                OutlinedTextField(
                    value = spacingText,
                    onValueChange = { 
                        spacingText = it
                        updateConfig()
                    },
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }
            
            HorizontalDivider()
            
            Text(stringResource(R.string.label_line_colors), style = MaterialTheme.typography.labelMedium)
            
            // Primary Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.grid_primary))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(primaryColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showPrimaryPicker = true }
                )
            }
            
            // Secondary Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.grid_secondary))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(secondaryColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showSecondaryPicker = true }
                )
            }
            
            // Tertiary Color
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.grid_tertiary))
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(tertiaryColor))
                        .border(1.dp, Color.Gray, CircleShape)
                        .clickable { showTertiaryPicker = true }
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    updateConfig()
                    onDismiss()
                }) {
                    Text(stringResource(R.string.action_apply))
                }
            }
        }
    }
}

// EXTENSIONS


private fun adjustPressure(pressure: Float, sensitivity: Float): Float {
    if (pressure <= 0f) return 0f
    if (pressure >= 1f) return 1f
    return try {
        java.lang.Math.pow(pressure.toDouble(), (1.0 / sensitivity.coerceAtLeast(0.01f)).toDouble()).toFloat()
    } catch (e: Exception) {
        pressure
    }
}
@Composable
fun ExportPngDialog(
    viewModel: SketcherViewModel, // Need VM for preview generation
    onDismiss: () -> Unit,
    onExport: (ExportPngConfig) -> Unit
) {
    var transparent by remember { mutableStateOf(false) }
    var useHomeView by remember { mutableStateOf(true) }
    
    // Resolution State
    var widthText by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf("") }
    var aspectRatio by remember { mutableFloatStateOf(1f) }
    
    // Preview State
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(false) }

    // Init Logic
    LaunchedEffect(Unit) {
        val defaults = viewModel.getExportDefaults(useHomeView)
        widthText = defaults.first.toString()
        heightText = defaults.second.toString()
        aspectRatio = defaults.first.toFloat() / defaults.second.toFloat()
    }
    
    // Update defaults when mode changes
    LaunchedEffect(useHomeView) {
        val defaults = viewModel.getExportDefaults(useHomeView)
        widthText = defaults.first.toString()
        heightText = defaults.second.toString()
        aspectRatio = defaults.first.toFloat() / defaults.second.toFloat()
    }
    
    // Generate Preview Effect
    LaunchedEffect(transparent, useHomeView) {
        isLoadingPreview = true
        // Generate a small preview
        val defaults = viewModel.getExportDefaults(useHomeView)
        val previewWidth = 400
        val previewHeight = (previewWidth / (defaults.first.toFloat() / defaults.second.toFloat())).toInt()
        
        val config = ExportPngConfig(transparent, useHomeView, previewWidth, previewHeight)
        
        // Run on IO/Generic
        launch(kotlinx.coroutines.Dispatchers.Default) {
            val bmp = viewModel.renderExportBitmap(config)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                previewBitmap = bmp
                isLoadingPreview = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar como PNG") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // --- PREVIEW ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (transparent) Color.LightGray else Color.Black) // Checkerboard logic would be better but simple gray for now
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = previewBitmap!!.asImageBitmap(),
                            contentDescription = "Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = androidx.compose.ui.layout.ContentScale.Fit
                        )
                    }
                    if (isLoadingPreview) {
                        CircularProgressIndicator()
                    }
                }
                
                HorizontalDivider()

                // --- OPTIONS ---
                Text("Opciones de fondo:", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                     Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { transparent = false }) {
                        RadioButton(selected = !transparent, onClick = { transparent = false })
                        Text("Sólido")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { transparent = true }) {
                        RadioButton(selected = transparent, onClick = { transparent = true })
                        Text("Transparente")
                    }
                }

                HorizontalDivider()

                Text("Área a exportar:", style = MaterialTheme.typography.labelMedium)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useHomeView = true }) {
                        RadioButton(selected = useHomeView, onClick = { useHomeView = true })
                        Text("Vista Home (Lo que ves)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useHomeView = false }) {
                        RadioButton(selected = !useHomeView, onClick = { useHomeView = false })
                        Text("Ajustar a contenido (Todo)")
                    }
                }
                
                HorizontalDivider()
                
                // --- RESOLUTION ---
                Text("Resolución:", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = widthText,
                        onValueChange = { 
                            widthText = it.filter { c -> c.isDigit() }
                            val newW = widthText.toFloatOrNull()
                            if (newW != null && aspectRatio > 0) {
                                heightText = (newW / aspectRatio).toInt().toString()
                            }
                        },
                        label = { Text("Ancho (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = { 
                            heightText = it.filter { c -> c.isDigit() }
                            val newH = heightText.toFloatOrNull()
                            if (newH != null && aspectRatio > 0) {
                                widthText = (newH * aspectRatio).toInt().toString()
                            }
                        },
                        label = { Text("Alto (px)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)

                    )
                }
                 val w = widthText.toIntOrNull() ?: 0
                 val h = heightText.toIntOrNull() ?: 0
                 if (w > 0 && h > 0) {
                     val sizeMb = w * h * 4 / 1024 / 1024.0
                     Text("Tamaño aprox: ${"%.2f".format(sizeMb)} MB", fontSize = 11.sp, color = Color.Gray)
                 }

            }
        },
        confirmButton = {
            Button(onClick = { 
                val w = widthText.toIntOrNull() ?: 1920
                val h = heightText.toIntOrNull() ?: 1080
                onExport(ExportPngConfig(transparent, useHomeView, w, h)) 
            }) {
                Text("Exportar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
fun ExportSvgDialog(
    viewModel: SketcherViewModel,
    onDismiss: () -> Unit,
    onExport: (ExportSvgConfig) -> Unit
) {
    var includeBackground by remember { mutableStateOf(viewModel.lastExportSvgConfig.includeBackground) }
    var useHomeView by remember { mutableStateOf(viewModel.lastExportSvgConfig.useHomeView) }
    
    // Preview Logic
    var previewBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoadingPreview by remember { mutableStateOf(true) }
    
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(includeBackground, useHomeView) {
        isLoadingPreview = true
        // Generate a small bitmap preview for the SVG (visualizing the content)
        val defaults = viewModel.getExportDefaults(useHomeView)
        val previewWidth = 400
        val previewHeight = (previewWidth / (defaults.first.toFloat() / defaults.second.toFloat())).toInt()
        
        val pngConfigForPreview = ExportPngConfig(!includeBackground, useHomeView, previewWidth, previewHeight)
        
        launch(kotlinx.coroutines.Dispatchers.Default) {
             val bmp = viewModel.renderExportBitmap(pngConfigForPreview)
             kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                 previewBitmap = bmp
                 isLoadingPreview = false
             }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Exportar como SVG") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // --- PREVIEW ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.LightGray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingPreview) {
                        CircularProgressIndicator()
                    } else {
                        if (previewBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = previewBitmap!!.asImageBitmap(),
                                contentDescription = "Preview",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Fit
                            )
                        } else {
                            Text("No hay contenido para exportar", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
                
                HorizontalDivider()
                
                // --- OPTIONS ---
                Text("Opciones de exportación:", style = MaterialTheme.typography.labelMedium)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = includeBackground, onCheckedChange = { includeBackground = it })
                    Text("Incluir color de fondo")
                }
                
                Text("Área de exportación:", style = MaterialTheme.typography.labelMedium)
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useHomeView = true }) {
                        RadioButton(selected = useHomeView, onClick = { useHomeView = true })
                        Text("Vista Home (Lo que ves)")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { useHomeView = false }) {
                        RadioButton(selected = !useHomeView, onClick = { useHomeView = false })
                        Text("Ajustar a contenido (Todo)")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { 
                val defaults = viewModel.getExportDefaults(useHomeView)
                onExport(ExportSvgConfig(includeBackground, useHomeView, defaults.first.toFloat(), defaults.second.toFloat())) 
            }) {
                Text("Exportar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipWrapper(
    text: String,
    enabled: Boolean,
    content: @Composable () -> Unit
) {
    if (enabled && text.isNotEmpty()) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
            tooltip = {
                PlainTooltip {
                    Text(text)
                }
            },
            state = rememberTooltipState()
        ) {
            content()
        }
    } else {
        content()
    }
}



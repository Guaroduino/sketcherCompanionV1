package com.sketcher.sketchercompanionv1.ui.dialogs

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sketcher.sketchercompanionv1.dto.ImageEditState
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.utils.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class EditMode {
    CHROMA_KEY,
    CROP_RECT,
    CROP_FREEHAND,
    CALIBRATE_SCALE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageEditDialog(
    state: ImageEditState,
    theme: UiThemeConfig,
    scaleConfig: com.sketcher.sketchercompanionv1.dto.ScaleConfig = com.sketcher.sketchercompanionv1.dto.ScaleConfig(),
    currentUnit: com.sketcher.sketchercompanionv1.dto.DistanceUnit = com.sketcher.sketchercompanionv1.dto.DistanceUnit.MM,
    onDismiss: () -> Unit,
    onConfirm: (Bitmap, List<Int>, Float, RectF?, List<PointF>?, List<Float>, Float, Boolean, Boolean, Float) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val scaler = LocalUiScaler.current
    
    // Dialog parameters states
    var transparentColors by remember { mutableStateOf(state.initialTransparentColors) }
    var transparentColorTolerances by remember {
        mutableStateOf(
            if (state.initialTransparentColorTolerances.size == state.initialTransparentColors.size) {
                state.initialTransparentColorTolerances
            } else {
                state.initialTransparentColors.map { state.initialTolerance }
            }
        )
    }
    
    var overallTolerance by remember { mutableFloatStateOf(state.initialTolerance) }
    var cropRect by remember { mutableStateOf(state.initialCropRect) }
    var cropPath by remember { mutableStateOf(state.initialCropPath) }
    
    var rotation by remember { mutableFloatStateOf(state.initialRotation) }
    var flipHorizontal by remember { mutableStateOf(state.initialFlipHorizontal) }
    var flipVertical by remember { mutableStateOf(state.initialFlipVertical) }
    
    var currentMode by remember { mutableStateOf(EditMode.CHROMA_KEY) }
    var selectedColorIndex by remember { mutableStateOf(if (transparentColors.isNotEmpty()) 0 else -1) }
    
    // Display sizing
    var containerWidth by remember { mutableFloatStateOf(0f) }
    var containerHeight by remember { mutableFloatStateOf(0f) }
    
    // Live processed bitmap preview
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    
    // Calibration states
    var calibrationPoint1 by remember { mutableStateOf<Offset?>(null) }
    var calibrationPoint2 by remember { mutableStateOf<Offset?>(null) }
    var calibrationScaleFactor by remember { mutableFloatStateOf(1.0f) }
    var showCalibrationDialog by remember { mutableStateOf(false) }
    
    // Process image in the background when parameters change
    LaunchedEffect(transparentColors, transparentColorTolerances, cropRect, cropPath, rotation, flipHorizontal, flipVertical) {
        coroutineScope.launch(Dispatchers.Default) {
            val result = ImageProcessor.processImage(
                original = state.originalBitmap,
                rotation = rotation,
                flipHorizontal = flipHorizontal,
                flipVertical = flipVertical,
                transparentColors = transparentColors,
                transparentColorTolerances = transparentColorTolerances,
                cropRect = cropRect,
                cropPath = cropPath
            )
            withContext(Dispatchers.Main) {
                processedBitmap = result
            }
        }
    }
    
    // Calculate the transformed dimensions (swapping width and height if rotation is 90 or 270 deg)
    val isSwapped = (rotation / 90f).toInt() % 2 != 0
    val transformedWidth = if (isSwapped) state.originalBitmap.height else state.originalBitmap.width
    val transformedHeight = if (isSwapped) state.originalBitmap.width else state.originalBitmap.height
    
    // Helper to map screen coordinate -> bitmap pixel
    fun mapScreenToBitmap(screenX: Float, screenY: Float): Pair<Int, Int>? {
        if (containerWidth <= 0f || containerHeight <= 0f) return null
        val tw = transformedWidth
        val th = transformedHeight
        val scale = Math.min(containerWidth / tw, containerHeight / th)
        val dx = (containerWidth - tw * scale) / 2f
        val dy = (containerHeight - th * scale) / 2f
        
        val bitmapX = ((screenX - dx) / scale).toInt()
        val bitmapY = ((screenY - dy) / scale).toInt()
        
        if (bitmapX in 0 until tw && bitmapY in 0 until th) {
            return Pair(bitmapX, bitmapY)
        }
        return null
    }

    // Helper to map bitmap pixel -> screen coordinate
    fun mapBitmapToScreen(pixelX: Float, pixelY: Float): Offset {
        if (containerWidth <= 0f || containerHeight <= 0f) return Offset.Zero
        val tw = transformedWidth
        val th = transformedHeight
        val scale = Math.min(containerWidth / tw, containerHeight / th)
        val dx = (containerWidth - tw * scale) / 2f
        val dy = (containerHeight - th * scale) / 2f
        
        val sx = pixelX * scale + dx
        val sy = pixelY * scale + dy
        return Offset(sx, sy)
    }
    
    // Temporary variables for drawing overlays in CROP modes
    var dragStartOffset by remember { mutableStateOf<Offset?>(null) }
    var dragCurrentOffset by remember { mutableStateOf<Offset?>(null) }
    var freehandDrawingPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding((16 * scaler.scaleFactor).dp),
            shape = RoundedCornerShape((16 * scaler.scaleFactor).dp),
            color = theme.barBackgroundColor,
            contentColor = theme.iconColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding((16 * scaler.scaleFactor).dp)
            ) {
                // Header row containing Title and Rotation/Flip controls
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = (8 * scaler.scaleFactor).dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (state.isNewImport) "Importar e Editar Imagen" else "Editar Imagen",
                        fontSize = (18 * scaler.scaleFactor).sp,
                        style = MaterialTheme.typography.titleLarge,
                        color = theme.iconColor
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy((4 * scaler.scaleFactor).dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                rotation = (rotation - 90f) % 360f
                                if (rotation < 0f) rotation += 360f
                            },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.iconColor),
                            modifier = Modifier.size(scaler.baseButtonSize)
                        ) {
                            Icon(
                                Icons.Default.RotateLeft, 
                                contentDescription = "Rotar Izquierda",
                                modifier = Modifier.size(scaler.baseIconSize)
                            )
                        }
                        IconButton(
                            onClick = {
                                rotation = (rotation + 90f) % 360f
                            },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.iconColor),
                            modifier = Modifier.size(scaler.baseButtonSize)
                        ) {
                            Icon(
                                Icons.Default.RotateRight, 
                                contentDescription = "Rotar Derecha",
                                modifier = Modifier.size(scaler.baseIconSize)
                            )
                        }
                        IconButton(
                            onClick = {
                                flipHorizontal = !flipHorizontal
                            },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.iconColor),
                            modifier = Modifier.size(scaler.baseButtonSize)
                        ) {
                            Icon(
                                Icons.Default.Flip, 
                                contentDescription = "Reflejo Horizontal",
                                modifier = Modifier.size(scaler.baseIconSize)
                            )
                        }
                        IconButton(
                            onClick = {
                                flipVertical = !flipVertical
                            },
                            colors = IconButtonDefaults.iconButtonColors(contentColor = theme.iconColor),
                            modifier = Modifier.size(scaler.baseButtonSize).rotate(90f)
                        ) {
                            Icon(
                                Icons.Default.Flip, 
                                contentDescription = "Reflejo Vertical",
                                modifier = Modifier.size(scaler.baseIconSize)
                            )
                        }
                    }
                }
                
                // Mode selector chips (Scrollable row)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy((8 * scaler.scaleFactor).dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = currentMode == EditMode.CHROMA_KEY,
                        onClick = { currentMode = EditMode.CHROMA_KEY },
                        label = { Text("Color Transparente", fontSize = (12 * scaler.scaleFactor).sp) },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.ColorLens, 
                                contentDescription = null,
                                modifier = Modifier.size(scaler.smallIconSize)
                            ) 
                        },
                        modifier = Modifier.height(scaler.baseButtonSize),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.highlightColor.copy(alpha = 0.3f),
                            selectedLabelColor = theme.iconColor,
                            selectedLeadingIconColor = theme.iconColor,
                            labelColor = theme.iconColor.copy(alpha = 0.7f),
                            iconColor = theme.iconColor.copy(alpha = 0.7f)
                        )
                    )
                    FilterChip(
                        selected = currentMode == EditMode.CROP_RECT,
                        onClick = { currentMode = EditMode.CROP_RECT },
                        label = { Text("Recorte Rectángulo", fontSize = (12 * scaler.scaleFactor).sp) },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.AspectRatio, 
                                contentDescription = null,
                                modifier = Modifier.size(scaler.smallIconSize)
                            ) 
                        },
                        modifier = Modifier.height(scaler.baseButtonSize),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.highlightColor.copy(alpha = 0.3f),
                            selectedLabelColor = theme.iconColor,
                            selectedLeadingIconColor = theme.iconColor,
                            labelColor = theme.iconColor.copy(alpha = 0.7f),
                            iconColor = theme.iconColor.copy(alpha = 0.7f)
                        )
                    )
                    FilterChip(
                        selected = currentMode == EditMode.CROP_FREEHAND,
                        onClick = { currentMode = EditMode.CROP_FREEHAND },
                        label = { Text("Recorte Libre", fontSize = (12 * scaler.scaleFactor).sp) },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.ContentCut, 
                                contentDescription = null,
                                modifier = Modifier.size(scaler.smallIconSize)
                            ) 
                        },
                        modifier = Modifier.height(scaler.baseButtonSize),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.highlightColor.copy(alpha = 0.3f),
                            selectedLabelColor = theme.iconColor,
                            selectedLeadingIconColor = theme.iconColor,
                            labelColor = theme.iconColor.copy(alpha = 0.7f),
                            iconColor = theme.iconColor.copy(alpha = 0.7f)
                        )
                    )
                    FilterChip(
                        selected = currentMode == EditMode.CALIBRATE_SCALE,
                        onClick = { currentMode = EditMode.CALIBRATE_SCALE },
                        label = { Text("Calibrar Tamaño", fontSize = (12 * scaler.scaleFactor).sp) },
                        leadingIcon = { 
                            Icon(
                                Icons.Default.AspectRatio, 
                                contentDescription = null,
                                modifier = Modifier.size(scaler.smallIconSize)
                            ) 
                        },
                        modifier = Modifier.height(scaler.baseButtonSize),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = theme.highlightColor.copy(alpha = 0.3f),
                            selectedLabelColor = theme.iconColor,
                            selectedLeadingIconColor = theme.iconColor,
                            labelColor = theme.iconColor.copy(alpha = 0.7f),
                            iconColor = theme.iconColor.copy(alpha = 0.7f)
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height((12 * scaler.scaleFactor).dp))
                
                // Main Image Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape((8 * scaler.scaleFactor).dp))
                        .background(Color.White)
                        .border(1.dp, Color.Gray.copy(alpha = 0.3f), RoundedCornerShape((8 * scaler.scaleFactor).dp))
                        .onGloballyPositioned { coordinates ->
                            containerWidth = coordinates.size.width.toFloat()
                            containerHeight = coordinates.size.height.toFloat()
                        }
                        .pointerInput(currentMode, rotation, flipHorizontal, flipVertical) {
                            detectTapGestures { offset ->
                                if (currentMode == EditMode.CHROMA_KEY) {
                                    val coords = mapScreenToBitmap(offset.x, offset.y)
                                    if (coords != null) {
                                        // Sample color from transformed original
                                        val matrix = android.graphics.Matrix()
                                        if (flipHorizontal) {
                                            matrix.postScale(-1f, 1f, state.originalBitmap.width / 2f, state.originalBitmap.height / 2f)
                                        }
                                        if (flipVertical) {
                                            matrix.postScale(1f, -1f, state.originalBitmap.width / 2f, state.originalBitmap.height / 2f)
                                        }
                                        if (rotation != 0f) {
                                            matrix.postRotate(rotation)
                                        }
                                        try {
                                            val transformed = Bitmap.createBitmap(
                                                state.originalBitmap,
                                                0, 0,
                                                state.originalBitmap.width,
                                                state.originalBitmap.height,
                                                matrix,
                                                true
                                            )
                                            if (coords.first in 0 until transformed.width && coords.second in 0 until transformed.height) {
                                                val pixelColor = transformed.getPixel(coords.first, coords.second)
                                                if (pixelColor !in transparentColors) {
                                                    transparentColors = transparentColors + pixelColor
                                                    transparentColorTolerances = transparentColorTolerances + 10f
                                                    selectedColorIndex = transparentColors.size - 1
                                                }
                                            }
                                        } catch (e: Exception) {
                                            e.printStackTrace()
                                        }
                                    }
                                } else if (currentMode == EditMode.CALIBRATE_SCALE) {
                                    val coords = mapScreenToBitmap(offset.x, offset.y)
                                    if (coords != null) {
                                        val pt = Offset(coords.first.toFloat(), coords.second.toFloat())
                                        if (calibrationPoint1 == null || calibrationPoint2 != null) {
                                            calibrationPoint1 = pt
                                            calibrationPoint2 = null
                                        } else {
                                            calibrationPoint2 = pt
                                            showCalibrationDialog = true
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(currentMode, rotation, flipHorizontal, flipVertical) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    if (currentMode == EditMode.CROP_RECT) {
                                        dragStartOffset = offset
                                        dragCurrentOffset = offset
                                    } else if (currentMode == EditMode.CROP_FREEHAND) {
                                        freehandDrawingPoints = listOf(offset)
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (currentMode == EditMode.CROP_RECT) {
                                        dragCurrentOffset = (dragCurrentOffset ?: dragStartOffset ?: Offset.Zero) + dragAmount
                                    } else if (currentMode == EditMode.CROP_FREEHAND) {
                                        freehandDrawingPoints = freehandDrawingPoints + ((freehandDrawingPoints.lastOrNull() ?: Offset.Zero) + dragAmount)
                                    }
                                },
                                onDragEnd = {
                                    if (currentMode == EditMode.CROP_RECT) {
                                        val start = dragStartOffset
                                        val end = dragCurrentOffset
                                        if (start != null && end != null) {
                                            val startPx = mapScreenToBitmap(start.x, start.y)
                                            val endPx = mapScreenToBitmap(end.x, end.y)
                                            if (startPx != null && endPx != null) {
                                                val left = Math.min(startPx.first, endPx.first).toFloat()
                                                val top = Math.min(startPx.second, endPx.second).toFloat()
                                                val right = Math.max(startPx.first, endPx.first).toFloat()
                                                val bottom = Math.max(startPx.second, endPx.second).toFloat()
                                                
                                                if (right - left > 5 && bottom - top > 5) {
                                                    cropRect = RectF(left, top, right, bottom)
                                                    cropPath = null
                                                }
                                            }
                                        }
                                        dragStartOffset = null
                                        dragCurrentOffset = null
                                    } else if (currentMode == EditMode.CROP_FREEHAND) {
                                        if (freehandDrawingPoints.size >= 3) {
                                            val mappedPath = freehandDrawingPoints.mapNotNull { pt ->
                                                val coords = mapScreenToBitmap(pt.x, pt.y)
                                                coords?.let { PointF(it.first.toFloat(), it.second.toFloat()) }
                                            }
                                            if (mappedPath.size >= 3) {
                                                cropPath = mappedPath
                                                cropRect = null
                                            }
                                        }
                                        freehandDrawingPoints = emptyList()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    // 1. Checkerboard Background
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cellSize = 30f
                        val cols = (size.width / cellSize).toInt() + 1
                        val rows = (size.height / cellSize).toInt() + 1
                        for (c in 0..cols) {
                            for (r in 0..rows) {
                                if ((c + r) % 2 == 0) {
                                    drawRect(
                                        color = Color.LightGray.copy(alpha = 0.5f),
                                        topLeft = Offset(c * cellSize, r * cellSize),
                                        size = Size(cellSize, cellSize)
                                    )
                                }
                            }
                        }
                    }
                    
                    // 2. Transformed Image Preview
                    processedBitmap?.let { bmp ->
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val scale = Math.min(size.width / bmp.width, size.height / bmp.height)
                            val w = bmp.width * scale
                            val h = bmp.height * scale
                            val dx = (size.width - w) / 2f
                            val dy = (size.height - h) / 2f
                            
                            drawImage(
                                image = bmp.asImageBitmap(),
                                dstOffset = androidx.compose.ui.unit.IntOffset(dx.toInt(), dy.toInt()),
                                dstSize = androidx.compose.ui.unit.IntSize(w.toInt(), h.toInt())
                            )
                        }
                    }
                    
                    // 3. Crop Active Drag Overlays (Removed blue completed outline overlays as requested)
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        if (currentMode == EditMode.CROP_RECT) {
                            val start = dragStartOffset
                            val end = dragCurrentOffset
                            if (start != null && end != null) {
                                val l = Math.min(start.x, end.x)
                                val t = Math.min(start.y, end.y)
                                val r = Math.max(start.x, end.x)
                                val b = Math.max(start.y, end.y)
                                drawRect(
                                    color = Color.Red,
                                    topLeft = Offset(l, t),
                                    size = Size(r - l, b - t),
                                    style = Stroke(width = 4f)
                                )
                            }
                        } else if (currentMode == EditMode.CROP_FREEHAND) {
                            if (freehandDrawingPoints.size >= 2) {
                                val p = Path()
                                p.moveTo(freehandDrawingPoints[0].x, freehandDrawingPoints[0].y)
                                for (i in 1 until freehandDrawingPoints.size) {
                                    p.lineTo(freehandDrawingPoints[i].x, freehandDrawingPoints[i].y)
                                }
                                drawPath(
                                    path = p,
                                    color = Color.Red,
                                    style = Stroke(width = 4f)
                                )
                            }
                        } else if (currentMode == EditMode.CALIBRATE_SCALE) {
                            val p1 = calibrationPoint1
                            val p2 = calibrationPoint2
                            if (p1 != null) {
                                val s1 = mapBitmapToScreen(p1.x, p1.y)
                                drawCircle(color = Color.Blue, radius = 8f, center = s1)
                                if (p2 != null) {
                                    val s2 = mapBitmapToScreen(p2.x, p2.y)
                                    drawCircle(color = Color.Blue, radius = 8f, center = s2)
                                    drawLine(color = Color.Blue, start = s1, end = s2, strokeWidth = 4f)
                                    
                                    val dx = p2.x - p1.x
                                    val dy = p2.y - p1.y
                                    val pixelDistance = kotlin.math.sqrt(dx * dx + dy * dy)
                                    
                                    val canvasPixels = pixelDistance * calibrationScaleFactor
                                    val rawUnits = com.sketcher.sketchercompanionv1.utils.UnitUtils.pixelsToProjectUnits(
                                        canvasPixels,
                                        currentUnit,
                                        scaleConfig.basePixelsPerMillimeter
                                    )
                                    val niceUnitsStr = "%.2f".format(rawUnits).removeSuffix("0").removeSuffix("0").removeSuffix(".")
                                    val textToDisplay = "$niceUnitsStr ${currentUnit.symbol}"
                                    
                                    val paint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.BLUE
                                        textSize = 36f
                                        isAntiAlias = true
                                        textAlign = android.graphics.Paint.Align.CENTER
                                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                                    }
                                    val rectPaint = android.graphics.Paint().apply {
                                        color = android.graphics.Color.WHITE
                                        alpha = 204
                                        style = android.graphics.Paint.Style.FILL
                                    }
                                    val textWidth = paint.measureText(textToDisplay)
                                    val fontMetrics = paint.fontMetrics
                                    
                                    val midX = (s1.x + s2.x) / 2f
                                    val midY = (s1.y + s2.y) / 2f
                                    
                                    drawContext.canvas.nativeCanvas.drawRect(
                                        midX - textWidth / 2f - 10f,
                                        midY - 30f + fontMetrics.top - 5f,
                                        midX + textWidth / 2f + 10f,
                                        midY - 30f + fontMetrics.bottom + 5f,
                                        rectPaint
                                    )
                                    
                                    drawContext.canvas.nativeCanvas.drawText(
                                        textToDisplay,
                                        midX,
                                        midY - 30f,
                                        paint
                                    )
                                }
                            }
                        }
                    }
                    
                    if (containerWidth > 0f && containerHeight > 0f) {
                        val previewScale = Math.min(containerWidth / transformedWidth, containerHeight / transformedHeight)
                        val previewZoom = previewScale / calibrationScaleFactor
                        
                        com.sketcher.sketchercompanionv1.ui.ScaleIndicator(
                            scaleConfig = scaleConfig,
                            currentUnit = currentUnit,
                            currentZoom = previewZoom,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding((8 * scaler.scaleFactor).dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height((12 * scaler.scaleFactor).dp))
                
                // Control parameters (Tolerances or crop controls)
                if (currentMode == EditMode.CHROMA_KEY) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        if (transparentColors.isNotEmpty()) {
                            Text(
                                text = "Colores seleccionados (toca para editar tolerancia):",
                                fontSize = (13 * scaler.scaleFactor).sp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.iconColor,
                                modifier = Modifier.padding(bottom = (4 * scaler.scaleFactor).dp)
                            )
                            
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy((8 * scaler.scaleFactor).dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((48 * scaler.scaleFactor).dp)
                            ) {
                                itemsIndexed(transparentColors) { index, colorValue ->
                                    val isSelected = index == selectedColorIndex
                                    Box(
                                        modifier = Modifier
                                            .size((36 * scaler.scaleFactor).dp)
                                            .clip(CircleShape)
                                            .background(Color(colorValue))
                                            .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color = if (isSelected) theme.highlightColor else Color.White,
                                                shape = CircleShape
                                            )
                                            .clickable {
                                                selectedColorIndex = index
                                            }
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height((8 * scaler.scaleFactor).dp))
                            
                            if (selectedColorIndex in transparentColors.indices) {
                                val activeTolerance = transparentColorTolerances[selectedColorIndex]
                                val activeColor = transparentColors[selectedColorIndex]
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy((8 * scaler.scaleFactor).dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size((24 * scaler.scaleFactor).dp)
                                            .clip(CircleShape)
                                            .background(Color(activeColor))
                                            .border(1.dp, Color.White, CircleShape)
                                    )
                                    
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Tolerancia: ${activeTolerance.toInt()}",
                                            fontSize = (13 * scaler.scaleFactor).sp,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = theme.iconColor
                                        )
                                        Slider(
                                            value = activeTolerance,
                                            onValueChange = { newTol ->
                                                val updated = transparentColorTolerances.toMutableList()
                                                updated[selectedColorIndex] = newTol
                                                transparentColorTolerances = updated
                                                if (transparentColors.size == 1) {
                                                    overallTolerance = newTol
                                                }
                                            },
                                            valueRange = 0f..100f,
                                            colors = SliderDefaults.colors(
                                                thumbColor = theme.highlightColor,
                                                activeTrackColor = theme.highlightColor
                                            )
                                        )
                                    }
                                    
                                    IconButton(
                                        onClick = {
                                            val updatedColors = transparentColors.toMutableList()
                                            val updatedTols = transparentColorTolerances.toMutableList()
                                            updatedColors.removeAt(selectedColorIndex)
                                            updatedTols.removeAt(selectedColorIndex)
                                            transparentColors = updatedColors
                                            transparentColorTolerances = updatedTols
                                            selectedColorIndex = if (updatedColors.isNotEmpty()) updatedColors.size - 1 else -1
                                        },
                                        modifier = Modifier.size(scaler.baseButtonSize)
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Eliminar color",
                                            tint = Color.Red.copy(alpha = 0.8f),
                                            modifier = Modifier.size(scaler.baseIconSize)
                                        )
                                    }
                                }
                            }
                        } else {
                            Text(
                                text = "Toca la imagen para seleccionar un color transparente",
                                fontSize = (13 * scaler.scaleFactor).sp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.iconColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = (12 * scaler.scaleFactor).dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Start
                    ) {
                        if (cropRect != null || cropPath != null) {
                            Button(
                                onClick = {
                                    cropRect = null
                                    cropPath = null
                                },
                                modifier = Modifier.height(scaler.baseButtonSize),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.Gray.copy(alpha = 0.2f),
                                    contentColor = theme.iconColor
                                )
                            ) {
                                Text("Limpiar Recorte", fontSize = (13 * scaler.scaleFactor).sp)
                            }
                        } else {
                            Text(
                                text = if (currentMode == EditMode.CROP_RECT) 
                                    "Arrastra sobre la imagen para definir el recorte rectangular" 
                                    else "Dibuja un trazo cerrado sobre la imagen para definir el recorte libre",
                                fontSize = (13 * scaler.scaleFactor).sp,
                                style = MaterialTheme.typography.bodyMedium,
                                color = theme.iconColor.copy(alpha = 0.6f),
                                modifier = Modifier.padding(vertical = (12 * scaler.scaleFactor).dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height((16 * scaler.scaleFactor).dp))
                
                // Confirm/Cancel Footer buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.height(scaler.baseButtonSize)
                    ) {
                        Text("Cancelar", fontSize = (13 * scaler.scaleFactor).sp, color = theme.iconColor.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width((8 * scaler.scaleFactor).dp))
                    Button(
                        onClick = {
                            val finalBitmap = processedBitmap
                            if (finalBitmap != null) {
                                onConfirm(
                                    finalBitmap,
                                    transparentColors,
                                    overallTolerance,
                                    cropRect,
                                    cropPath,
                                    transparentColorTolerances,
                                    rotation,
                                    flipHorizontal,
                                    flipVertical,
                                    calibrationScaleFactor
                                )
                            } else {
                                onDismiss()
                            }
                        },
                        modifier = Modifier.height(scaler.baseButtonSize),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.highlightColor,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Confirmar", fontSize = (13 * scaler.scaleFactor).sp)
                    }
                }
            }
        }
    }

    if (showCalibrationDialog) {
        var realDistanceStr by remember { mutableStateOf("") }
        var importScaleDenominatorStr by remember { mutableStateOf("1") }
        Dialog(onDismissRequest = {
            showCalibrationDialog = false
            calibrationPoint1 = null
            calibrationPoint2 = null
        }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = theme.barBackgroundColor),
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                ) {
                    Text(
                        text = "Calibrar Tamaño",
                        fontSize = 18.sp,
                        color = theme.iconColor,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = realDistanceStr,
                        onValueChange = { realDistanceStr = it },
                        label = { Text("Distancia real del objeto (${currentUnit.symbol})", color = theme.iconColor.copy(alpha = 0.5f)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.iconColor,
                            unfocusedTextColor = theme.iconColor,
                            focusedBorderColor = theme.highlightColor,
                            unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importScaleDenominatorStr,
                        onValueChange = { importScaleDenominatorStr = it },
                        label = { Text("Denominador escala de importación (ej. 50 para 1:50)", color = theme.iconColor.copy(alpha = 0.5f)) },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.iconColor,
                            unfocusedTextColor = theme.iconColor,
                            focusedBorderColor = theme.highlightColor,
                            unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            showCalibrationDialog = false
                            calibrationPoint1 = null
                            calibrationPoint2 = null
                        }) {
                            Text("Cancelar", color = theme.iconColor)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val realDistance = realDistanceStr.toFloatOrNull()
                                val importScaleDenominator = importScaleDenominatorStr.toFloatOrNull() ?: 1.0f
                                if (realDistance != null && realDistance > 0 && importScaleDenominator > 0 && calibrationPoint1 != null && calibrationPoint2 != null) {
                                    val dx = calibrationPoint2!!.x - calibrationPoint1!!.x
                                    val dy = calibrationPoint2!!.y - calibrationPoint1!!.y
                                    val pixelDistance = kotlin.math.sqrt(dx * dx + dy * dy)
                                    
                                    val importScale = 1.0f / importScaleDenominator
                                    val targetProjectUnits = realDistance * importScale
                                    val targetCanvasPixels = com.sketcher.sketchercompanionv1.utils.UnitUtils.projectUnitsToPixels(targetProjectUnits, currentUnit, scaleConfig.basePixelsPerMillimeter)
                                    
                                    calibrationScaleFactor = targetCanvasPixels / pixelDistance
                                    showCalibrationDialog = false
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor)
                        ) {
                            Text("Aplicar", color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

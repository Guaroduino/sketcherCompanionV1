package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.LibraryComponent
import com.sketcher.sketchercompanionv1.RenderHelper
import com.sketcher.sketchercompanionv1.dto.DistanceUnit
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.utils.UnitUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComponentScaleDialog(
    component: LibraryComponent,
    componentLibrary: Map<String, ComponentDefinition>,
    currentUnit: DistanceUnit,
    basePixelsPerMillimeter: Float,
    theme: UiThemeConfig,
    onDismiss: () -> Unit,
    onConfirm: (Float) -> Unit
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var containerWidth by remember { mutableStateOf(1f) }
    var containerHeight by remember { mutableStateOf(1f) }

    var boundsWidthPx by remember { mutableStateOf(0f) }
    var boundsHeightPx by remember { mutableStateOf(0f) }
    var boundsCenterX by remember { mutableStateOf(0f) }
    var boundsCenterY by remember { mutableStateOf(0f) }

    var calibrationPoint1 by remember { mutableStateOf<Offset?>(null) }
    var calibrationPoint2 by remember { mutableStateOf<Offset?>(null) }
    var showCalibrationDialog by remember { mutableStateOf(false) }

    LaunchedEffect(component) {
        val bounds = android.graphics.RectF()
        component.definition.elements.forEach { el ->
            val elBounds = el.getBoundingBox(componentLibrary)
            if (!elBounds.isEmpty) {
                if (bounds.isEmpty) bounds.set(elBounds)
                else bounds.union(elBounds)
            }
        }
        boundsWidthPx = bounds.width()
        boundsHeightPx = bounds.height()
        boundsCenterX = bounds.centerX()
        boundsCenterY = bounds.centerY()
    }

    // Initialize scale to fit
    LaunchedEffect(containerWidth, containerHeight, boundsWidthPx, boundsHeightPx) {
        if (containerWidth > 1f && containerHeight > 1f && boundsWidthPx > 0f && boundsHeightPx > 0f) {
            val scaleX = containerWidth * 0.8f / boundsWidthPx
            val scaleY = containerHeight * 0.8f / boundsHeightPx
            scale = minOf(scaleX, scaleY)
            offset = Offset.Zero
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = theme.barBackgroundColor),
            modifier = Modifier
                .fillMaxSize(0.95f)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Box(modifier = Modifier.fillMaxWidth().background(theme.barBackgroundColor).padding(16.dp)) {
                    Text(
                        "Calibrar Escala (Toca 2 puntos)", 
                        color = theme.iconColor, 
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                }

                // Canvas area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFE0E0E0)) // Checkerboard or light gray
                        .onGloballyPositioned { coordinates ->
                            containerWidth = coordinates.size.width.toFloat()
                            containerHeight = coordinates.size.height.toFloat()
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale *= zoom
                                offset += pan
                            }
                        }
                        .pointerInput(Unit) {
                            detectTapGestures { tapOffset ->
                                // Map tap to component space
                                val centerScreenX = containerWidth / 2f
                                val centerScreenY = containerHeight / 2f

                                val dx = tapOffset.x - centerScreenX - offset.x
                                val dy = tapOffset.y - centerScreenY - offset.y

                                val compX = dx / scale + boundsCenterX
                                val compY = dy / scale + boundsCenterY

                                if (calibrationPoint1 == null) {
                                    calibrationPoint1 = Offset(compX, compY)
                                } else if (calibrationPoint2 == null) {
                                    calibrationPoint2 = Offset(compX, compY)
                                    showCalibrationDialog = true
                                } else {
                                    calibrationPoint1 = Offset(compX, compY)
                                    calibrationPoint2 = null
                                }
                            }
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val centerScreenX = containerWidth / 2f
                        val centerScreenY = containerHeight / 2f

                        drawIntoCanvas { canvas ->
                            val nativeCanvas = canvas.nativeCanvas
                            nativeCanvas.save()
                            nativeCanvas.translate(centerScreenX + offset.x, centerScreenY + offset.y)
                            nativeCanvas.scale(scale, scale)
                            nativeCanvas.translate(-boundsCenterX, -boundsCenterY)

                            // Draw component elements
                            component.definition.elements.forEach { el ->
                                RenderHelper.drawElementRecursive(nativeCanvas, el, componentLibrary)
                            }
                            nativeCanvas.restore()
                        }

                        // Draw calibration points and line
                        val mapToScreen: (Offset) -> Offset = { pt ->
                            val sx = (pt.x - boundsCenterX) * scale + centerScreenX + offset.x
                            val sy = (pt.y - boundsCenterY) * scale + centerScreenY + offset.y
                            Offset(sx, sy)
                        }

                        calibrationPoint1?.let { pt1 ->
                            val screenPt1 = mapToScreen(pt1)
                            drawCircle(color = theme.highlightColor, radius = 8.dp.toPx(), center = screenPt1)
                            drawCircle(color = Color.White, radius = 8.dp.toPx(), center = screenPt1, style = Stroke(width = 2.dp.toPx()))

                            calibrationPoint2?.let { pt2 ->
                                val screenPt2 = mapToScreen(pt2)
                                drawCircle(color = theme.highlightColor, radius = 8.dp.toPx(), center = screenPt2)
                                drawCircle(color = Color.White, radius = 8.dp.toPx(), center = screenPt2, style = Stroke(width = 2.dp.toPx()))

                                drawLine(
                                    color = theme.highlightColor,
                                    start = screenPt1,
                                    end = screenPt2,
                                    strokeWidth = 3.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                )
                            }
                        }
                    }

                    // Reset buttons
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                calibrationPoint1 = null
                                calibrationPoint2 = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.barBackgroundColor.copy(alpha=0.7f))
                        ) {
                            Text("Limpiar Puntos", color = theme.iconColor)
                        }
                    }
                }

                // Bottom bar
                Row(
                    modifier = Modifier.fillMaxWidth().background(theme.barBackgroundColor).padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val currentW = if (boundsWidthPx > 0f) UnitUtils.pixelsToProjectUnits(boundsWidthPx * component.definition.creationScale, currentUnit, basePixelsPerMillimeter) else 0f
                    val currentH = if (boundsHeightPx > 0f) UnitUtils.pixelsToProjectUnits(boundsHeightPx * component.definition.creationScale, currentUnit, basePixelsPerMillimeter) else 0f
                    Text("Tamaño actual: ${"%.2f".format(java.util.Locale.US, currentW)} x ${"%.2f".format(java.util.Locale.US, currentH)} ${currentUnit.symbol}", color = theme.iconColor)
                    
                    Row {
                        TextButton(onClick = onDismiss) {
                            Text("Cancelar", color = theme.iconColor)
                        }
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
                        label = { Text("Distancia real en el plano (${currentUnit.symbol})", color = theme.iconColor.copy(alpha = 0.5f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                        label = { Text("Denominador escala (ej. 50 para 1:50)", color = theme.iconColor.copy(alpha = 0.5f)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
                                    val pixelDistance = kotlin.math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
                                    
                                    val importScale = 1.0f / importScaleDenominator
                                    val targetProjectUnits = realDistance * importScale
                                    val targetCanvasPixels = UnitUtils.projectUnitsToPixels(targetProjectUnits, currentUnit, basePixelsPerMillimeter)
                                    
                                    val newCreationScale = targetCanvasPixels / pixelDistance
                                    onConfirm(newCreationScale)
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

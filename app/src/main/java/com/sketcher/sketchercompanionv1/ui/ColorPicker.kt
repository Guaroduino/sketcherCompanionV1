package com.sketcher.sketchercompanionv1.ui
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import com.sketcher.sketchercompanionv1.ui.theme.ssp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun ColorPickerDialog(
    initialColor: Int,
    theme: UiThemeConfig = UiThemeConfig(),
    onDismiss: () -> Unit,
    onColorSelected: (Int) -> Unit,
    onDisable: (() -> Unit)? = null
) {
    // HSV State
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    var isGridView by remember { mutableStateOf(false) }

    val colorMatrix = remember {
        listOf(
            // Row 1: Grayscale / Neutrals
            Color(0xFFFFFFFF), Color(0xFFE0E0E0), Color(0xFFBDBDBD), Color(0xFF9E9E9E), Color(0xFF757575), Color(0xFF616161), Color(0xFF424242), Color(0xFF000000),
            // Row 2: Reds & Pinks
            Color(0xFFFFEBEE), Color(0xFFFFCDD2), Color(0xFFEF9A9A), Color(0xFFE57373), Color(0xFFEF5350), Color(0xFFF44336), Color(0xFFE53935), Color(0xFFD32F2F),
            // Row 3: Oranges & Yellows
            Color(0xFFFFF8E1), Color(0xFFFFECB3), Color(0xFFFFE082), Color(0xFFFFD54F), Color(0xFFFFCA28), Color(0xFFFFB300), Color(0xFFFFA000), Color(0xFFFF8F00),
            // Row 4: Greens
            Color(0xFFE8F5E9), Color(0xFFC8E6C9), Color(0xFFA5D6A7), Color(0xFF81C784), Color(0xFF66BB6A), Color(0xFF4CAF50), Color(0xFF43A047), Color(0xFF388E3C),
            // Row 5: Blues / Cyans
            Color(0xFFE3F2FD), Color(0xFFBBDEFB), Color(0xFF90CAF9), Color(0xFF64B5F6), Color(0xFF42A5F5), Color(0xFF2196F3), Color(0xFF1E88E5), Color(0xFF1565C0),
            // Row 6: Purples / Magentas
            Color(0xFFF3E5F5), Color(0xFFE1BEE7), Color(0xFFCE93D8), Color(0xFFBA68C8), Color(0xFFAB47BC), Color(0xFF9C27B0), Color(0xFF8E24AA), Color(0xFF7B1FA2)
        )
    }
    
    // Convert initial color to HSV once
    LaunchedEffect(Unit) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColor, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
        if (value < 0.05f) {
            value = 0.5f
        }
    }

    val currentColor = remember(hue, saturation, value) {
        Color.hsv(hue, saturation, value)
    }

    // Presets - We use a snapshot list for session persistence
    val presets = remember { mutableStateListOf(
        Color.Red, Color.Green, Color.Blue, Color.Yellow
    ) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(450.sdp)
                .heightIn(max = 500.dp)
                .clip(RoundedCornerShape(16.sdp)),
            shape = RoundedCornerShape(16.sdp),
            color = theme.barBackgroundColor,
            contentColor = theme.iconColor
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.sdp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.sdp)
                ) {
                    // Current Color Preview
                    Box(
                        modifier = Modifier
                            .size(50.sdp)
                            .clip(CircleShape)
                            .background(currentColor)
                            .border(2.sdp, Color.Gray, CircleShape)
                    )

                    // Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.sdp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isGridView) "Palette Grid" else "Color Wheel",
                            fontSize = 11.ssp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = theme.iconColor
                        )
                        TextButton(
                            onClick = { isGridView = !isGridView },
                            colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor),
                            border = BorderStroke(1.sdp, theme.iconColor.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(8.sdp),
                            modifier = Modifier.height(28.sdp),
                            contentPadding = PaddingValues(horizontal = 12.sdp, vertical = 2.sdp)
                        ) {
                            Text(if (isGridView) "Show Wheel" else "Show Grid", fontSize = 10.ssp)
                        }
                    }

                    if (isGridView) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.sdp),
                            verticalArrangement = Arrangement.spacedBy(8.sdp)
                        ) {
                            val columns = 8
                            val colorRows = colorMatrix.chunked(columns)
                            colorRows.forEach { rowColors ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    rowColors.forEach { cellColor ->
                                        val isSelected = cellColor.toArgb() == currentColor.toArgb()
                                        Box(
                                            modifier = Modifier
                                                .size(28.sdp)
                                                .clip(CircleShape)
                                                .background(cellColor)
                                                .border(
                                                    width = if (isSelected) 2.sdp else 1.sdp,
                                                    color = if (isSelected) theme.iconColor else Color.LightGray,
                                                    shape = CircleShape
                                                )
                                                .clickable {
                                                    val cellHsv = FloatArray(3)
                                                    AndroidColor.colorToHSV(cellColor.toArgb(), cellHsv)
                                                    hue = cellHsv[0]
                                                    saturation = cellHsv[1]
                                                    value = cellHsv[2]
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Color Wheel (Hue + Saturation)
                        ColorWheel(
                            hue = hue,
                            saturation = saturation,
                            onColorChange = { h, s -> 
                                hue = h
                                saturation = s
                                if (value < 0.1f) {
                                    value = 0.5f
                                }
                            }
                        )
                    }
                    
                    // Saturation Slider
                    SaturationSlider(
                        saturation = saturation,
                        hue = hue,
                        value = value,
                        onSaturationChange = { saturation = it }
                    )

                    // Value Slider
                    ValueSlider(
                        value = value,
                        hue = hue,
                        saturation = saturation,
                        onValueChange = { value = it }
                    )

                    Text("Presets (Long press to Save)", style = androidx.compose.material3.MaterialTheme.typography.bodySmall.copy(fontSize = 11.ssp))
                    
                    // Presets Row
                    Row(horizontalArrangement = Arrangement.spacedBy(12.sdp)) {
                        presets.forEachIndexed { index, color ->
                            PresetCircle(
                                color = color,
                                onClick = { 
                                    val hsv = FloatArray(3)
                                    AndroidColor.colorToHSV(color.toArgb(), hsv)
                                    hue = hsv[0]
                                    saturation = hsv[1]
                                    value = hsv[2]
                                },
                                onLongClick = {
                                    presets[index] = currentColor
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.sdp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.sdp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)) { Text("Cancel", fontSize = 13.ssp) }
                    if (onDisable != null) {
                        TextButton(onClick = { onDisable(); onDismiss() }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)) { Text("None", fontSize = 13.ssp) }
                    }
                    Button(
                        onClick = { onColorSelected(currentColor.toArgb()) },
                        shape = RoundedCornerShape(8.sdp),
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = theme.buttonColor, contentColor = theme.iconColor)
                    ) {
                        Text("Select", fontSize = 13.ssp)
                    }
                }
            }
        }
    }
}

@Composable
fun ColorWheel(
    hue: Float,
    saturation: Float,
    onColorChange: (Float, Float) -> Unit
) {
    val size = 200.sdp
    val scaler = com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler.current
    
    Box(modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val center = Offset(size.toPx() / 2, size.toPx() / 2)
                    val dx = change.position.x - center.x
                    val dy = change.position.y - center.y
                    val radius = sqrt(dx*dx + dy*dy)
                    val maxRadius = size.toPx() / 2
                    
                    var theta = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (theta < 0) theta += 360f
                    
                    val s = (radius / maxRadius).coerceIn(0f, 1f)
                    onColorChange(theta, s)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val center = Offset(size.toPx() / 2, size.toPx() / 2)
                    val dx = offset.x - center.x
                    val dy = offset.y - center.y
                    val radius = sqrt(dx*dx + dy*dy)
                    val maxRadius = size.toPx() / 2
                    
                    var theta = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
                    if (theta < 0) theta += 360f
                    
                    val s = (radius / maxRadius).coerceIn(0f, 1f)
                    onColorChange(theta, s)
                }
            }
        ) {
            val center = center
            val radius = size.toPx() / 2
            
            // Spectrum
            drawCircle(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, 
                        Color.Blue, Color.Magenta, Color.Red
                    ),
                    center = center
                ),
                radius = radius
            )
            
            // Saturation White Overlay
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color.White, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius
            )
            
            // Selector Thumb
            val thetaRad = Math.toRadians(hue.toDouble())
            val selectorR = saturation * radius
            val selectorX = center.x + selectorR * cos(thetaRad).toFloat()
            val selectorY = center.y + selectorR * sin(thetaRad).toFloat()
            
            val outerRadius = (8.dp * scaler.scaleFactor).toPx()
            val innerRadius = (6.dp * scaler.scaleFactor).toPx()
            val strokeThickness = (2.dp * scaler.scaleFactor).toPx()

            drawCircle(Color.Black, radius = outerRadius, center = Offset(selectorX, selectorY), style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeThickness))
            drawCircle(Color.White, radius = innerRadius, center = Offset(selectorX, selectorY))
        }
    }
}

@Composable
fun ValueSlider(value: Float, hue: Float, saturation: Float, onValueChange: (Float) -> Unit) {
    // Gradient from Black to (Current Hue/Sat at Value=1)
    val endColor = Color.hsv(hue, saturation, 1f)
    
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.sdp)
            .clip(RoundedCornerShape(15.sdp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Black, endColor)
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val width = size.width.toFloat()
                    val newValue = (change.position.x / width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val newValue = (offset.x / width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
    ) {
        val width = maxWidth
        val offsetX = width * value
        
        // VISIBLE THUMB
        Box(
            modifier = Modifier
                .offset(x = offsetX - 10.sdp) // Center thumb (-10sdp is half of 20sdp)
                .size(20.sdp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.sdp, Color.Black, CircleShape)
        )
    }
}

@Composable
fun SaturationSlider(saturation: Float, hue: Float, value: Float, onSaturationChange: (Float) -> Unit) {
    // Gradient from White (Sat=0) to Pure Hue (Sat=1) at current Value
    val startColor = Color.hsv(hue, 0f, value)
    val endColor = Color.hsv(hue, 1f, value)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.sdp)
            .clip(RoundedCornerShape(15.sdp))
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(startColor, endColor)
                )
            )
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val width = size.width.toFloat()
                    val newSat = (change.position.x / width).coerceIn(0f, 1f)
                    onSaturationChange(newSat)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val newSat = (offset.x / width).coerceIn(0f, 1f)
                    onSaturationChange(newSat)
                }
            }
    ) {
        val width = maxWidth
        val offsetX = width * saturation

        // VISIBLE THUMB
        Box(
            modifier = Modifier
                .offset(x = offsetX - 10.sdp)
                .size(20.sdp)
                .clip(CircleShape)
                .background(Color.White)
                .border(2.sdp, Color.Black, CircleShape)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PresetCircle(color: Color, onClick: () -> Unit, onLongClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.sdp)
            .clip(CircleShape)
            .background(color)
            .border(1.sdp, Color.Gray, CircleShape)
            // Use combinedClickable for more reliable long press handling
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    )
}


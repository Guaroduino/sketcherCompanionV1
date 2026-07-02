package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import com.sketcher.sketchercompanionv1.ui.theme.ssp
import kotlin.math.max
import kotlin.math.min

@Composable
fun ColorPickerDialog(
    initialColor: Color,
    recentColors: List<Color>,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig = com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig(),
    onDismiss: () -> Unit,
    onColorSelected: (Color) -> Unit,
    onDisable: (() -> Unit)? = null,
    onRevertToPreset: (() -> Unit)? = null
) {
    var currentColor by remember { mutableStateOf(initialColor) }
    
    // HSV State
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }
    
    // Init logic
    LaunchedEffect(Unit) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor.toArgb(), hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    // Update color from HSV
    fun updateColor() {
        val hsv = floatArrayOf(hue, saturation, value)
        val androidColor = android.graphics.Color.HSVToColor(hsv)
        currentColor = Color(androidColor)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.sdp),
            color = theme.barBackgroundColor,
            contentColor = theme.iconColor,
            modifier = Modifier
                .width(450.sdp)
                .heightIn(max = 600.sdp)
                .clip(RoundedCornerShape(16.sdp))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.sdp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Pick a Color", style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.ssp), color = theme.iconColor)
                Spacer(modifier = Modifier.height(12.sdp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 2D Saturation/Value Box
                    SaturationValueBox(
                        hue = hue,
                        saturation = saturation,
                        value = value,
                        onSaturationValueChanged = { s, v ->
                            saturation = s
                            value = v
                            updateColor()
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(12.sdp))

                    // Hue Bar
                    HueBar(
                        hue = hue,
                        onHueChanged = { h ->
                            hue = h
                            updateColor()
                        }
                    )

                    Spacer(modifier = Modifier.height(12.sdp))

                    // Preview & Current Params
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.sdp)
                                .clip(CircleShape)
                                .background(currentColor)
                                .border(1.sdp, Color.Gray, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(16.sdp))
                        Column {
                            Text("H: ${hue.toInt()}°", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.ssp))
                            Text("S: ${(saturation * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.ssp))
                            Text("V: ${(value * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.ssp))
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.sdp))
                    
                    // Recent Colors
                    if (recentColors.isNotEmpty()) {
                        Text("Recent Colors", style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.ssp), modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(8.sdp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.sdp)
                        ) {
                            recentColors.take(6).forEach { color ->
                                Box(
                                    modifier = Modifier
                                        .size(32.sdp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(1.sdp, Color.Gray.copy(alpha=0.3f), CircleShape)
                                        .clickable { 
                                             // Set internal state
                                             val hsv = FloatArray(3)
                                             android.graphics.Color.colorToHSV(color.toArgb(), hsv)
                                             hue = hsv[0]
                                             saturation = hsv[1]
                                             value = hsv[2]
                                             updateColor()
                                        }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.sdp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onRevertToPreset != null) {
                        Button(
                            onClick = onRevertToPreset,
                            shape = RoundedCornerShape(8.sdp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.highlightColor, contentColor = theme.barBackgroundColor)
                        ) {
                            Text("Preset", fontSize = 14.ssp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss, colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)) {
                            Text("Cancel", fontSize = 14.ssp)
                        }
                        if (onDisable != null) {
                            Spacer(modifier = Modifier.width(8.sdp))
                            TextButton(onClick = { onDisable(); onDismiss() }, colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)) {
                                Text("None", fontSize = 14.ssp)
                            }
                        }
                        Spacer(modifier = Modifier.width(8.sdp))
                        Button(
                            onClick = { onColorSelected(currentColor) },
                            shape = RoundedCornerShape(8.sdp),
                            colors = ButtonDefaults.buttonColors(containerColor = theme.buttonColor, contentColor = theme.iconColor)
                        ) {
                            Text("Select", fontSize = 14.ssp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaturationValueBox(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChanged: (Float, Float) -> Unit
) {
    val hsv = floatArrayOf(hue, 1f, 1f)
    val pureHueColor = Color(android.graphics.Color.HSVToColor(hsv))
    val scaler = com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler.current

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.sdp)
            .clip(RoundedCornerShape(8.sdp))
            .background(Color.Black) // Base is black (Value=0)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                         val s = (offset.x / size.width).coerceIn(0f, 1f)
                         val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                         onSaturationValueChanged(s, v)
                    },
                    onDrag = { change, _ ->
                         val s = (change.position.x / size.width).coerceIn(0f, 1f)
                         val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                         onSaturationValueChanged(s, v)
                    }
                )
            }
    ) {
        // Layer 1: Hue Color (Horizontal Gradient: White -> PureHue)
        // Wait, standard SV box is:
        // X: Saturation (White -> Color)
        // Y: Value (Black -> Transparent)
        // Actually, usually it's composed of:
        // Base: Hue Color
        // Overlay 1: White Gradient (Horizontal, Left to Right, Opacity decreases? No)
        // Standard Model:
        // Bottom-Left: White? No.
        // Horizontal: White -> Hue Color
        // Vertical: Transparent -> Black (Top to Bottom? No, Bottom is Black)
        
        // Let's us Compose Gradients.
        // 1. Solid Pure Hue
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(color = pureHueColor)
            
            // 2. Saturation Gradient (White to Transparent - Left to Right)
            // Wait, Saturation 0 = White (if Value 1).
            // Actually simpler:
            // Layer 1: White to Pure Hue (Horizontal)
            // Layer 2: Black (Transparent at Top to Opaque at Bottom)
            
            // Correct approach for S/V Box:
            // 1. Fill with Pure Hue
            // 2. Apply White Gradient (Horizontal, Left->Right, Opaque->Transparent) <- This mimics Saturation
            //    Left (Sat 0) = White. Right (Sat 1) = Hue.
            // 3. Apply Black Gradient (Vertical, Top->Bottom, Transparent->Opaque) <- This mimics Value
            //    Top (Val 1) = Color. Bottom (Val 0) = Black.
            
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent)
                )
            )
            
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )
            
            // Draw Indicator
            val px = saturation * size.width
            val py = (1f - value) * size.height
            
            val outerRadius = (8.dp * scaler.scaleFactor).toPx()
            val strokeThickness = (2.dp * scaler.scaleFactor).toPx()
            val innerRadius = (6.dp * scaler.scaleFactor).toPx()

            drawCircle(
                color = Color.White,
                radius = outerRadius,
                center = Offset(px, py),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeThickness)
            )
             drawCircle(
                color = if (value > 0.5f) Color.Black else Color.White,
                radius = innerRadius,
                center = Offset(px, py),
             )
        }
    }
}

@Composable
fun HueBar(
    hue: Float,
    onHueChanged: (Float) -> Unit
) {
    val scaler = com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(24.sdp)
            .clip(RoundedCornerShape(12.sdp))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { offset ->
                         val h = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                         onHueChanged(h)
                    },
                    onDrag = { change, _ ->
                         val h = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                         onHueChanged(h)
                    }
                )
            }
    ) {
        // Rainbow Gradient
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red
                    )
                )
            )
            
            // Indicator
            val px = (hue / 360f) * size.width
            val strokeThickness = (3.dp * scaler.scaleFactor).toPx()
            drawLine(
                color = Color.White,
                start = Offset(px, 0f),
                end = Offset(px, size.height),
                strokeWidth = strokeThickness
            )
        }
    }
}

@Composable
fun ColorPreviewRow(
    label: String,
    color: Color,
    labelColor: Color = Color.Gray,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.sdp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.ssp), color = labelColor)
        Box(
            modifier = Modifier
                .size(40.sdp)
                .clip(CircleShape)
                .background(color)
                .border(1.sdp, Color.Gray, CircleShape)
        )
    }
}

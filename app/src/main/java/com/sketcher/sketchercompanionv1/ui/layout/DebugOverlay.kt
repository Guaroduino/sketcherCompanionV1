package com.sketcher.sketchercompanionv1.ui.layout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

@Composable
fun DebugOverlay(
    userScale: Float,
    onScaleChange: (Float) -> Unit,
    uiCollapsed: Boolean,
    onToggleUi: () -> Unit,
    onSwitchToLegacy: () -> Unit,
    isStudioMode: Boolean,
    swapVertical: Boolean,
    swapHorizontal: Boolean,
    onToggleSwapVertical: () -> Unit,
    onToggleSwapHorizontal: () -> Unit
) {
    // State for drag offsets
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomEnd
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y
                    }
                }
                .width(180.dp) // Slightly wider for swap buttons
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(8.dp)) { // Reduced padding
                Text("Debug", style = MaterialTheme.typography.labelSmall) // Smaller text
                Spacer(modifier = Modifier.height(4.dp))
                
                // Scale Slider
                var tempScale by remember(userScale) { mutableStateOf(userScale) }
                Text("Scale: ${String.format("%.1f", tempScale)}x", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = tempScale,
                    onValueChange = { tempScale = it },
                    onValueChangeFinished = { onScaleChange(tempScale) },
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.width(100.dp).height(24.dp) // Smaller width/height
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // UI Toggle Buttons
                Row(
                   modifier = Modifier.fillMaxWidth(),
                   horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onToggleUi, 
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (uiCollapsed) "Show" else "Hide", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onSwitchToLegacy, 
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (isStudioMode) "Legacy" else "Studio", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Swap Logic Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onToggleSwapVertical, 
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (swapVertical) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Swap V", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = onToggleSwapHorizontal, 
                        modifier = Modifier.weight(1f).height(32.dp),
                        contentPadding = PaddingValues(0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (swapHorizontal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Swap H", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

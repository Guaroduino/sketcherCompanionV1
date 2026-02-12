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
    isStudioMode: Boolean
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
                .width(150.dp) // Explicit width restriction
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(8.dp)) { // Reduced padding
                Text("Debug", style = MaterialTheme.typography.labelSmall) // Smaller text
                Spacer(modifier = Modifier.height(4.dp))
                
                // Scale Slider
                Text("Scale: ${String.format("%.1f", userScale)}x", style = MaterialTheme.typography.labelSmall)
                Slider(
                    value = userScale,
                    onValueChange = onScaleChange,
                    valueRange = 0.5f..1.5f,
                    modifier = Modifier.width(100.dp).height(24.dp) // Smaller width/height
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Buttons
                Button(
                    onClick = onToggleUi, 
                    modifier = Modifier.fillMaxWidth().height(32.dp), // Compact height
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (uiCollapsed) "Show UI" else "Hide UI", style = MaterialTheme.typography.labelSmall)
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Button(
                    onClick = onSwitchToLegacy, 
                    modifier = Modifier.fillMaxWidth().height(32.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(if (isStudioMode) "Legacy" else "Studio", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

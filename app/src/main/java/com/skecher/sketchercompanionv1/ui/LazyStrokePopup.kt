package com.skecher.sketchercompanionv1.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints

@Composable
fun LazyStrokePopup(
    value: Float,
    onValueChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    val density = LocalDensity.current
    val yOffsetPx = with(density) { (-380).dp.roundToPx() }

    Popup(
        alignment = Alignment.TopCenter,
        offset = IntOffset(0, yOffsetPx),
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            modifier = Modifier
                .width(40.dp)
                .height(350.dp) // Taller container
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(20.dp)
                ),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 2.dp,
            shadowElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Percentage at the top
                Text(
                    text = "${(value * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    // THE FIX: Proper vertical rotation with layout swap
                    Slider(
                        value = value,
                        onValueChange = onValueChange,
                        valueRange = 0f..0.90f,
                        modifier = Modifier
                            .graphicsLayer {
                                rotationZ = -90f
                            }
                            .layout { measurable, constraints ->
                                // Swap constraints for rotation
                                val placeable = measurable.measure(
                                    Constraints(
                                        minWidth = constraints.minHeight,
                                        maxWidth = constraints.maxHeight,
                                        minHeight = constraints.minWidth,
                                        maxHeight = constraints.maxWidth
                                    )
                                )
                                layout(placeable.height, placeable.width) {
                                    placeable.place(- (placeable.width - placeable.height) / 2, - (placeable.height - placeable.width) / 2)
                                }
                            }
                            .fillMaxSize(), // Now it fills the available vertical space correctly
                        colors = SliderDefaults.colors(
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                            thumbColor = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "STAB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

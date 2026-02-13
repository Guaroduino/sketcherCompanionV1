package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape

@Composable
fun DynamicSizeButton(
    onClick: () -> Unit,
    brushSize: Float,
    isActive: Boolean,
    isEditMode: Boolean = false,
    backgroundColorOverride: Color? = null,
    highlightColor: Color,
    buttonColor: Color,
    iconColor: Color,
    shape: Shape = CircleShape
) {
    val backgroundColor = backgroundColorOverride ?: if (isActive) highlightColor else buttonColor

    BigTouchBox(
        onClick = onClick,
        touchSize = 48.dp
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(36.dp)) {
                // Map brushSize (assume 1-100 range) to 2dp - 18dp radius
                val clampedSize = brushSize.coerceIn(1f, 100f)
                val radiusDp = 2f + ((clampedSize - 1f) / 99f) * 16f
                
                drawCircle(
                    color = iconColor,
                    radius = radiusDp.dp.toPx(),
                    alpha = if (isEditMode) 0.6f else 1f
                )
            }
        }
    }
}

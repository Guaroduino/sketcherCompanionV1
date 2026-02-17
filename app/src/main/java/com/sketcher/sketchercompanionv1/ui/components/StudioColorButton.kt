package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

@Composable
fun StudioColorButton(
    onClick: () -> Unit,
    color: Color,
    isEnabled: Boolean,
    label: String,
    highlightColor: Color,
    buttonColor: Color,
    iconColor: Color,
    shape: Shape = CircleShape
) {
    BigTouchBox(
        onClick = onClick,
        touchSize = 48.dp
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(shape)
                .background(buttonColor)
                .border(if (isEnabled) 2.dp else 1.dp, if (isEnabled) highlightColor else iconColor.copy(alpha = 0.3f), shape),
            contentAlignment = Alignment.Center
        ) {
            if (isEnabled) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(color)
                        .border(1.dp, iconColor.copy(alpha = 0.2f), CircleShape)
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Disabled",
                    modifier = Modifier.size(20.dp),
                    tint = iconColor.copy(alpha = 0.4f)
                )
            }
        }
    }
}

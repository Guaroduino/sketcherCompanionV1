package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Shape
import androidx.compose.foundation.shape.CircleShape

/**
 * A helper component that decouples the visual size of an element from its touch area.
 * Useful for small icons or toggles that need to be easily tappable (~48dp-64dp)
 * while maintaining a minimalist visual appearance.
 */
@Composable
fun BigTouchBox(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    touchSize: Dp = 64.dp,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .size(touchSize)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null, // No ripple on the outer box to keep it minimalist
                onClick = onClick
            ),
        contentAlignment = contentAlignment
    ) {
        content()
    }
}

@Composable
fun SketcherIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    isActive: Boolean,
    isEditMode: Boolean = false,
    backgroundColorOverride: Color? = null,
    highlightColor: Color,
    buttonColor: Color,
    iconColor: Color,
    shape: Shape = CircleShape,
    iconSize: Dp = 24.dp
) {
    val backgroundColor = backgroundColorOverride ?: if (isActive) highlightColor else buttonColor

    BigTouchBox(
        onClick = onClick,
        touchSize = 48.dp
    ) {
        Box(
            modifier = Modifier
                .size(36.dp) // Standard button size within the 48dp touch area
                .clip(shape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier
                    .size(iconSize)
                    .then(if (isEditMode) Modifier.alpha(0.6f) else Modifier)
            )
        }
    }
}



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

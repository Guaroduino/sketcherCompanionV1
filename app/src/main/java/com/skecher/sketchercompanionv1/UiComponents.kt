package com.skecher.sketchercompanionv1

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Enums
enum class ToolType { PEN, ERASER }
enum class BrushFamily { PEN, MARKER, HIGHLIGHTER }

// Botón de Herramienta (Icono)
@Composable
fun ToolButton(
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) Color.Black else Color.Transparent
    val iconColor = if (isSelected) Color.White else Color.Black

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = iconColor)
    }
}

// NUEVO: Botón de Vista Previa de Tamaño
@Composable
fun SizeButton(
    currentSize: Float,
    currentColor: Color,
    isOpen: Boolean,
    onClick: () -> Unit
) {
    // El contenedor es fijo (48dp), pero el círculo interno cambia según currentSize
    // Limitamos visualmente el círculo interno a 40dp para que no se salga del botón
    val displaySize = (currentSize.coerceIn(2f, 40f)).dp

    val backgroundColor = if (isOpen) Color(0xFFE0E0E0) else Color.Transparent

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        // Círculo de Preview
        Box(
            modifier = Modifier
                .size(displaySize)
                .clip(CircleShape)
                .background(currentColor)
        )

        // Si es blanco, ponemos un borde gris para que se vea
        if (currentColor == Color.White) {
            Box(
                modifier = Modifier
                    .size(displaySize)
                    .clip(CircleShape)
                    .border(1.dp, Color.Gray, CircleShape)
            )
        }
    }
}

fun Modifier.scale(scale: Float): Modifier = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)
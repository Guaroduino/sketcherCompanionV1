package com.sketcher.sketchercompanionv1.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class UiThemeConfig(
    val barBackgroundColor: Color = Color.Black.copy(alpha = 0.7f),
    val buttonColor: Color = Color.DarkGray.copy(alpha = 0.5f),
    val iconColor: Color = Color.White,
    val highlightColor: Color = Color.Cyan,
    val barElevation: androidx.compose.ui.unit.Dp = 8.dp,
    val isRound: Boolean = false,
    val shadowAngle: Float = 45f,
    val recentColors: List<Color> = listOf(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta, Color.Yellow),
    val isShadowEnabled: Boolean = true,
    val shadowOpacity: Float = 0.5f,
    val shadowBlur: androidx.compose.ui.unit.Dp = 8.dp
) {
    fun floatingShape(): Shape {
        return if (isRound) CircleShape else RoundedCornerShape(8.dp)
    }

    fun panelShape(): Shape {
        return androidx.compose.ui.graphics.RectangleShape
    }
}

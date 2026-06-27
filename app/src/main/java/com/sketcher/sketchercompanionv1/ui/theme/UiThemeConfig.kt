package com.sketcher.sketchercompanionv1.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class UiThemeConfig(
    val barBackgroundColor: Color = Color.White,
    val buttonColor: Color = Color.White,
    val iconColor: Color = Color(0xFF424242),
    val highlightColor: Color = Color(0xFFE0E0E0),
    val barElevation: androidx.compose.ui.unit.Dp = 8.dp,
    val isRound: Boolean = false,
    val shadowAngle: Float = 24f,
    val recentColors: List<Color> = listOf(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta, Color.Yellow),
    val isShadowEnabled: Boolean = true,
    val shadowOpacity: Float = 0.27f,
    val shadowBlur: androidx.compose.ui.unit.Dp = 2.dp,
    val customIcons: Map<String, String> = emptyMap()
) {
    fun floatingShape(): Shape {
        return if (isRound) CircleShape else RoundedCornerShape(8.dp)
    }

    fun panelShape(): Shape {
        return androidx.compose.ui.graphics.RectangleShape
    }
}

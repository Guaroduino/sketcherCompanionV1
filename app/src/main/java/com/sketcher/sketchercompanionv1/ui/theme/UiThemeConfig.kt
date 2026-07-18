package com.sketcher.sketchercompanionv1.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp

data class UiThemeConfig(
    val barBackgroundColor: Color = Color.White,
    val buttonColor: Color = Color.White,
    val menuButtonColor: Color = Color.White,
    val iconColor: Color = Color(0xFF424242),
    val highlightColor: Color = Color(0xFFE0E0E0),
    val canvasColor: Color = Color(0xFFEEEEEE),
    val barElevation: androidx.compose.ui.unit.Dp = 8.dp,
    val isRound: Boolean = false,
    val shadowAngle: Float = 24f,
    val recentColors: List<Color> = listOf(Color.Red, Color.Green, Color.Blue, Color.Cyan, Color.Magenta, Color.Yellow),
    val isShadowEnabled: Boolean = true,
    val shadowOpacity: Float = 0.27f,
    val shadowBlur: androidx.compose.ui.unit.Dp = 2.dp
) {
    fun floatingShape(): Shape {
        return if (isRound) CircleShape else RoundedCornerShape(8.dp)
    }

    fun panelShape(): Shape {
        return androidx.compose.ui.graphics.RectangleShape
    }
}

fun UiThemeConfig.toThemeJson(): com.sketcher.sketchercompanionv1.dto.ThemeJson {
    return com.sketcher.sketchercompanionv1.dto.ThemeJson(
        barBackgroundColor = this.barBackgroundColor.toArgb(),
        buttonColor = this.buttonColor.toArgb(),
        menuButtonColor = this.menuButtonColor.toArgb(),
        iconColor = this.iconColor.toArgb(),
        highlightColor = this.highlightColor.toArgb(),
        canvasColor = this.canvasColor.toArgb(),
        barElevation = this.barElevation.value,
        isRound = this.isRound,
        shadowAngle = this.shadowAngle,
        recentColors = this.recentColors.map { it.toArgb() },
        isShadowEnabled = this.isShadowEnabled,
        shadowOpacity = this.shadowOpacity,
        shadowBlur = this.shadowBlur.value
    )
}

fun com.sketcher.sketchercompanionv1.dto.ThemeJson.toDomain(): UiThemeConfig {
    return UiThemeConfig(
        barBackgroundColor = Color(this.barBackgroundColor),
        buttonColor = Color(this.buttonColor),
        menuButtonColor = if (this.menuButtonColor != null) Color(this.menuButtonColor) else Color(this.buttonColor),
        iconColor = Color(this.iconColor),
        highlightColor = Color(this.highlightColor),
        canvasColor = if (this.canvasColor != null) Color(this.canvasColor) else Color(0xFFEEEEEE),
        barElevation = this.barElevation.dp,
        isRound = this.isRound,
        shadowAngle = this.shadowAngle,
        recentColors = this.recentColors.map { Color(it) },
        isShadowEnabled = this.isShadowEnabled,
        shadowOpacity = this.shadowOpacity,
        shadowBlur = this.shadowBlur.dp
    )
}

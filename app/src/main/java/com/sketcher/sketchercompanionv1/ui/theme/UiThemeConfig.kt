package com.sketcher.sketchercompanionv1.ui.theme

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

data class UiThemeConfig(
    val barBackgroundColor: Color = Color.Black.copy(alpha = 0.7f),
    val buttonColor: Color = Color.DarkGray.copy(alpha = 0.5f),
    val accentColor: Color = Color.Cyan,
    val isRound: Boolean = false
) {
    fun shape(): Shape {
        return if (isRound) CircleShape else RoundedCornerShape(8.dp)
    }
}

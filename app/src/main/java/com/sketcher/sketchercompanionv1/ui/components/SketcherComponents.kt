package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.ui.theme.UiScaler

import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

// --- Data Models ---

enum class ToolType {
    ACTION, // Trigger a one-off action (e.g. Undo, Save)
    TOGGLE, // Toggle a state (e.g. Grid On/Off)
    SLIDER  // Adjust a value (e.g. Opacity)
}

data class UiTool(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val type: ToolType = ToolType.ACTION
)

// --- Atomic Components ---


@Composable
fun SketcherIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    scaler: UiScaler,
    theme: UiThemeConfig,
    isActive: Boolean = false,
    modifier: Modifier = Modifier,
    tint: Color? = null // Optional override
) {
    val backgroundColor = if (isActive) {
        theme.accentColor
    } else {
        theme.buttonColor
    }
    
    // Default to White/Black based on activity if no specific icon color in theme (we removed it for simplicity)
    val defaultIconColor = if (isActive) Color.Black else Color.White
    val iconColor = tint ?: defaultIconColor

    Box(
        modifier = modifier
            .size(scaler.baseButtonSize)
            .clip(theme.shape())
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(scaler.baseButtonSize * 0.5f) // Icon is 50% of button size
        )
    }
}

/**
 * A compact slider with a label, designed for the HUD.
 */
@Composable
fun SketcherActionSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    scaler: UiScaler,
    theme: UiThemeConfig,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = theme.accentColor,
                activeTrackColor = theme.accentColor.copy(alpha = 0.8f),
                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
            ),
            modifier = Modifier.height(20.dp) // Compact height
        )
    }
}

/**
 * A stylized header for panels (Layers, Library).
 */
@Composable
fun SketcherPanelHeader(
    title: String,
    scaler: UiScaler,
    theme: UiThemeConfig,
    modifier: Modifier = Modifier
) {
    Text(
        text = title.uppercase(),
        color = Color.White.copy(alpha = 0.6f),
        fontSize = 10.sp, // Small caption style
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = modifier.padding(vertical = 8.dp)
    )
}

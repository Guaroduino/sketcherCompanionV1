package com.sketcher.sketchercompanionv1.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.components.BigTouchBox

@Composable
fun SettingSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    labelStyle: TextStyle = MaterialTheme.typography.bodyMedium,
    labelColor: Color = Color.Unspecified,
    sliderColors: SliderColors = SliderDefaults.colors(),
    showValueOnRight: Boolean = false,
    valueFormatter: (Float) -> String = { "${(it * 100).toInt()}%" },
    layoutHorizontal: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    exponent: Float = 1f
) {
    val scaler = LocalUiScaler.current
    val scaleFactor = scaler.scaleFactor

    val min = valueRange.start
    val max = valueRange.endInclusive
    val clampedValue = value.coerceIn(min, max)
    val linearProgress = if (max > min) (clampedValue - min) / (max - min) else 0f
    val internalValue = if (exponent != 1f && linearProgress > 0f) Math.pow(linearProgress.toDouble(), 1.0 / exponent.toDouble()).toFloat() else linearProgress

    val internalOnValueChange: (Float) -> Unit = { newInternal ->
        val actualProgress = if (exponent != 1f && newInternal > 0f) Math.pow(newInternal.toDouble(), exponent.toDouble()).toFloat() else newInternal
        val actualVal = min + actualProgress * (max - min)
        onValueChange(actualVal)
    }

    val scaledLabelStyle = if (labelStyle.fontSize.isSp) {
        labelStyle.copy(fontSize = (labelStyle.fontSize.value * scaleFactor).sp)
    } else {
        labelStyle
    }

    if (layoutHorizontal) {
        Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp * scaleFactor)
        ) {
            Slider(
                value = internalValue,
                onValueChange = internalOnValueChange,
                valueRange = 0f..1f,
                steps = steps,
                colors = sliderColors,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueFormatter(value),
                style = scaledLabelStyle,
                color = labelColor,
                modifier = Modifier.width(48.dp * scaleFactor)
            )
        }
    } else {
        Column(modifier = modifier) {
            if (label.isNotEmpty()) {
                if (showValueOnRight) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = label, style = scaledLabelStyle, color = labelColor)
                        val valueStyle = MaterialTheme.typography.bodySmall
                        val scaledValueStyle = if (valueStyle.fontSize.isSp) {
                            valueStyle.copy(fontSize = (valueStyle.fontSize.value * scaleFactor).sp)
                        } else {
                            valueStyle
                        }
                        Text(text = valueFormatter(value), style = scaledValueStyle, color = labelColor)
                    }
                } else {
                    Text(text = label, style = scaledLabelStyle, color = labelColor)
                }
            }
            Slider(
                value = internalValue,
                onValueChange = internalOnValueChange,
                valueRange = 0f..1f,
                steps = steps,
                colors = sliderColors,
                onValueChangeFinished = onValueChangeFinished,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


@Composable
fun AppIconButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
    buttonSize: Dp = 24.dp,
    touchSize: Dp = 48.dp,
    shape: Shape = CircleShape,
    backgroundColor: Color = Color.Transparent,
    enabled: Boolean = true
) {
    val scaler = LocalUiScaler.current
    val scaleFactor = scaler.scaleFactor

    BigTouchBox(
        onClick = { if (enabled) onClick() },
        touchSize = touchSize * scaleFactor,
        modifier = modifier
    ) {
        val contentAlpha = if (enabled) 1f else 0.38f
        Box(
            modifier = Modifier
                .size(buttonSize * scaleFactor)
                .clip(shape)
                .background(backgroundColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint.copy(alpha = tint.alpha * contentAlpha),
                modifier = Modifier.size(buttonSize * scaleFactor)
            )
        }
    }
}


// Ensure we don't conflict or recurse. 
// If generic HorizontalDivider is needed but M3 one requires parameters, we wrap it.
// Assuming M3 has HorizontalDivider.
@Composable
fun AppHorizontalDivider(modifier: Modifier = Modifier) {
    androidx.compose.material3.HorizontalDivider(
        modifier = modifier, 
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    )
}




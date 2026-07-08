package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatAlignCenter
import androidx.compose.material.icons.filled.FormatAlignLeft
import androidx.compose.material.icons.filled.FormatAlignRight
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.TextElement
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

@Composable
fun TextFormatContextBar(
    element: TextElement,
    theme: UiThemeConfig,
    onEditTextClick: () -> Unit,
    onStyleChange: (String) -> Unit,       // "TITLE", "SUBTITLE", "BODY", "CODE"
    onAlignmentChange: (String) -> Unit,   // "LEFT", "CENTER", "RIGHT"
    onSizeChange: (Float) -> Unit,         // Change base size
    onFontChange: (String) -> Unit,        // "sans-serif", "serif", "monospace"
    onColorClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scaler = LocalUiScaler.current
    val scaleFactor = scaler.scaleFactor

    var showStyleMenu by remember { mutableStateOf(false) }
    var showFontMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .padding(8.dp * scaleFactor)
            .wrapContentSize(),
        shape = RoundedCornerShape(12.dp * scaleFactor),
        color = theme.barBackgroundColor.copy(alpha = 0.95f),
        tonalElevation = 6.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, theme.highlightColor)
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp * scaleFactor, vertical = 6.dp * scaleFactor),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp * scaleFactor)
        ) {
            // Edit Text content
            IconButton(
                onClick = onEditTextClick,
                modifier = Modifier.size(36.dp * scaleFactor)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar contenido",
                    tint = theme.iconColor,
                    modifier = Modifier.size(20.dp * scaleFactor)
                )
            }

            Divider(modifier = Modifier.height(24.dp * scaleFactor).width(1.dp), color = theme.highlightColor)

            // Styles Selector (Title, Subtitle, Body, Code)
            Box {
                val currentStyleLabel = when (element.styleTemplateName) {
                    "TITLE" -> "Título"
                    "SUBTITLE" -> "Subtítulo"
                    "CODE" -> "Código"
                    else -> "Cuerpo"
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp * scaleFactor))
                        .clickable { showStyleMenu = true }
                        .padding(horizontal = 8.dp * scaleFactor, vertical = 4.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentStyleLabel,
                        color = theme.iconColor,
                        fontSize = (13 * scaleFactor).sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = theme.iconColor,
                        modifier = Modifier.size(16.dp * scaleFactor)
                    )
                }

                DropdownMenu(
                    expanded = showStyleMenu,
                    onDismissRequest = { showStyleMenu = false },
                    modifier = Modifier.background(theme.barBackgroundColor)
                ) {
                    val styles = listOf(
                        "TITLE" to "Título (28sp, Bold)",
                        "SUBTITLE" to "Subtítulo (20sp, Medium)",
                        "BODY" to "Cuerpo (14sp, Normal)",
                        "CODE" to "Código (13sp, Monospace)"
                    )
                    styles.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = theme.iconColor, fontSize = 14.sp) },
                            onClick = {
                                onStyleChange(key)
                                showStyleMenu = false
                            }
                        )
                    }
                }
            }

            Divider(modifier = Modifier.height(24.dp * scaleFactor).width(1.dp), color = theme.highlightColor)

            // Fonts Selector (Sans, Serif, Monospace)
            Box {
                val currentFontLabel = when (element.fontFamilyName) {
                    "serif" -> "Serif"
                    "monospace" -> "Mono"
                    else -> "Sans"
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp * scaleFactor))
                        .clickable { showFontMenu = true }
                        .padding(horizontal = 8.dp * scaleFactor, vertical = 4.dp * scaleFactor),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = currentFontLabel,
                        color = theme.iconColor,
                        fontSize = (13 * scaleFactor).sp
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        tint = theme.iconColor,
                        modifier = Modifier.size(16.dp * scaleFactor)
                    )
                }

                DropdownMenu(
                    expanded = showFontMenu,
                    onDismissRequest = { showFontMenu = false },
                    modifier = Modifier.background(theme.barBackgroundColor)
                ) {
                    val fonts = listOf(
                        "sans-serif" to "Sans-Serif",
                        "serif" to "Serif",
                        "monospace" to "Monospace"
                    )
                    fonts.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label, color = theme.iconColor, fontSize = 14.sp) },
                            onClick = {
                                onFontChange(key)
                                showFontMenu = false
                            }
                        )
                    }
                }
            }

            Divider(modifier = Modifier.height(24.dp * scaleFactor).width(1.dp), color = theme.highlightColor)

            // Font Size Controls (+ and -)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp * scaleFactor)
            ) {
                IconButton(
                    onClick = { if (element.defaultTextSize > 4f) onSizeChange(element.defaultTextSize - 2f) },
                    modifier = Modifier.size(28.dp * scaleFactor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Reducir tamaño",
                        tint = theme.iconColor,
                        modifier = Modifier.size(16.dp * scaleFactor)
                    )
                }

                Text(
                    text = "${element.defaultTextSize.toInt()}pt",
                    color = theme.iconColor,
                    fontSize = (12 * scaleFactor).sp
                )

                IconButton(
                    onClick = { onSizeChange(element.defaultTextSize + 2f) },
                    modifier = Modifier.size(28.dp * scaleFactor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Aumentar tamaño",
                        tint = theme.iconColor,
                        modifier = Modifier.size(16.dp * scaleFactor)
                    )
                }
            }

            Divider(modifier = Modifier.height(24.dp * scaleFactor).width(1.dp), color = theme.highlightColor)

            // Alignment buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp * scaleFactor)
            ) {
                listOf(
                    "LEFT" to Icons.Default.FormatAlignLeft,
                    "CENTER" to Icons.Default.FormatAlignCenter,
                    "RIGHT" to Icons.Default.FormatAlignRight
                ).forEach { (align, icon) ->
                    val isSelected = element.alignment == align
                    val background = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else Color.Transparent
                    IconButton(
                        onClick = { onAlignmentChange(align) },
                        modifier = Modifier
                            .size(30.dp * scaleFactor)
                            .clip(RoundedCornerShape(4.dp * scaleFactor))
                            .background(background)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = align,
                            tint = if (isSelected) MaterialTheme.colorScheme.primary else theme.iconColor,
                            modifier = Modifier.size(18.dp * scaleFactor)
                        )
                    }
                }
            }

            Divider(modifier = Modifier.height(24.dp * scaleFactor).width(1.dp), color = theme.highlightColor)

            // Color picker trigger
            Box(
                modifier = Modifier
                    .size(28.dp * scaleFactor)
                    .clip(RoundedCornerShape(6.dp * scaleFactor))
                    .background(Color(element.defaultTextColor))
                    .border(1.dp, theme.iconColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp * scaleFactor))
                    .clickable { onColorClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Palette,
                    contentDescription = "Color de texto",
                    tint = if (Color(element.defaultTextColor) == Color.White) Color.Black else Color.White,
                    modifier = Modifier.size(16.dp * scaleFactor)
                )
            }
        }
    }
}

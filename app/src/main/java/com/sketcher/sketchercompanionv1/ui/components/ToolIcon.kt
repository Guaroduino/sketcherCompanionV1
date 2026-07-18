package com.sketcher.sketchercompanionv1.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.VectorIcon
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

@Composable
fun VectorIconRenderer(
    vectorIcon: VectorIcon,
    tint: Color,
    modifier: Modifier = Modifier,
    strokeWidth: Float = 6f
) {
    val composePaths = remember(vectorIcon) {
        vectorIcon.toComposePaths()
    }

    Canvas(modifier = modifier) {
        val scaleX = size.width / 100f
        val scaleY = size.height / 100f
        
        withTransform({
            scale(scaleX, scaleY, pivot = Offset.Zero)
        }) {
            composePaths.forEach { path ->
                drawPath(
                    path = path,
                    color = tint,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
}

val LocalGlobalCustomIcons = androidx.compose.runtime.compositionLocalOf<Map<String, String>> { emptyMap() }

@Composable
fun ToolIcon(
    tool: StudioTool,
    theme: UiThemeConfig,
    tint: Color,
    modifier: Modifier = Modifier,
    iconSize: Dp = 24.dp,
    globalCustomIcons: Map<String, String> = LocalGlobalCustomIcons.current
) {
    // Try to find a custom icon in globalCustomIcons
    val customIconJson = globalCustomIcons[tool.registryId] ?: globalCustomIcons[tool.id]
    if (customIconJson != null) {
        val vectorIcon = remember(customIconJson) {
            try {
                val typeToken = object : TypeToken<List<String>>() {}.type
                val paths: List<String> = Gson().fromJson(customIconJson, typeToken)
                VectorIcon(paths)
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        
        if (vectorIcon != null && vectorIcon.paths.isNotEmpty()) {
            VectorIconRenderer(
                vectorIcon = vectorIcon,
                tint = tint,
                modifier = modifier.size(iconSize)
            )
        } else {
            RenderFallbackIcon(tool, tint, modifier, iconSize)
        }
    } else {
        RenderFallbackIcon(tool, tint, modifier, iconSize)
    }
}

@Composable
private fun RenderFallbackIcon(
    tool: StudioTool,
    tint: Color,
    modifier: Modifier,
    iconSize: Dp
) {
    if (tool.iconResId != null) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = tool.iconResId),
            contentDescription = tool.contentDescription,
            tint = tint,
            modifier = modifier.size(iconSize)
        )
    } else {
        Icon(
            imageVector = tool.icon,
            contentDescription = tool.contentDescription,
            tint = tint,
            modifier = modifier.size(iconSize)
        )
    }
}

package com.sketcher.sketchercompanionv1.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

@Composable
fun SketcherCompanionV1Theme(
    theme: UiThemeConfig = UiThemeConfig(),
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        else -> {
            val primaryColor = theme.highlightColor
            val onPrimaryColor = if (theme.iconColor != Color.Unspecified) theme.iconColor else Color.Black
            val surfaceColor = theme.barBackgroundColor
            val onSurfaceColor = theme.iconColor
            val secondaryColor = theme.buttonColor

            if (darkTheme) {
                darkColorScheme(
                    primary = primaryColor,
                    onPrimary = onPrimaryColor,
                    primaryContainer = primaryColor.copy(alpha = 0.2f),
                    onPrimaryContainer = onPrimaryColor,
                    secondary = secondaryColor,
                    onSecondary = onPrimaryColor,
                    secondaryContainer = onSurfaceColor.copy(alpha = 0.12f),
                    onSecondaryContainer = onSurfaceColor,
                    background = surfaceColor,
                    onBackground = onSurfaceColor,
                    surface = surfaceColor,
                    onSurface = onSurfaceColor,
                    surfaceVariant = surfaceColor,
                    onSurfaceVariant = onSurfaceColor,
                    outline = onSurfaceColor.copy(alpha = 0.5f)
                )
            } else {
                lightColorScheme(
                    primary = primaryColor,
                    onPrimary = onPrimaryColor,
                    primaryContainer = primaryColor.copy(alpha = 0.2f),
                    onPrimaryContainer = onPrimaryColor,
                    secondary = secondaryColor,
                    onSecondary = onPrimaryColor,
                    secondaryContainer = onSurfaceColor.copy(alpha = 0.12f),
                    onSecondaryContainer = onSurfaceColor,
                    background = surfaceColor,
                    onBackground = onSurfaceColor,
                    surface = surfaceColor,
                    onSurface = onSurfaceColor,
                    surfaceVariant = surfaceColor,
                    onSurfaceVariant = onSurfaceColor,
                    outline = onSurfaceColor.copy(alpha = 0.5f)
                )
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


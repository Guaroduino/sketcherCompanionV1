package com.sketcher.sketchercompanionv1.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.gson.Gson
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

class ThemeRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_theme", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val KEY_THEME = "saved_theme"

    fun saveTheme(config: UiThemeConfig) {
        val dto = config.toDto()
        val json = gson.toJson(dto)
        prefs.edit().putString(KEY_THEME, json).apply()
    }

    fun getTheme(): UiThemeConfig {
        val json = prefs.getString(KEY_THEME, null) ?: return UiThemeConfig()
        return try {
            val dto = gson.fromJson(json, ThemeDto::class.java)
            dto.toDomain()
        } catch (e: Exception) {
            e.printStackTrace()
            UiThemeConfig()
        }
    }

    // DTO for serialization to avoid issues with Compose classes (Color, Dp)
    private data class ThemeDto(
        val barBackgroundColor: Int,
        val buttonColor: Int,
        val iconColor: Int,
        val highlightColor: Int,
        val barElevation: Float,
        val isRound: Boolean,
        val shadowAngle: Float,
        val recentColors: List<Int>,
        val isShadowEnabled: Boolean,
        val shadowOpacity: Float,
        val shadowBlur: Float,
        val customIcons: Map<String, String>? = emptyMap()
    )

    private fun UiThemeConfig.toDto(): ThemeDto {
        return ThemeDto(
            barBackgroundColor = this.barBackgroundColor.toArgb(),
            buttonColor = this.buttonColor.toArgb(),
            iconColor = this.iconColor.toArgb(),
            highlightColor = this.highlightColor.toArgb(),
            barElevation = this.barElevation.value,
            isRound = this.isRound,
            shadowAngle = this.shadowAngle,
            recentColors = this.recentColors.map { it.toArgb() },
            isShadowEnabled = this.isShadowEnabled,
            shadowOpacity = this.shadowOpacity,
            shadowBlur = this.shadowBlur.value,
            customIcons = this.customIcons
        )
    }

    private fun ThemeDto.toDomain(): UiThemeConfig {
        return UiThemeConfig(
            barBackgroundColor = Color(this.barBackgroundColor),
            buttonColor = Color(this.buttonColor),
            iconColor = Color(this.iconColor),
            highlightColor = Color(this.highlightColor),
            barElevation = this.barElevation.dp,
            isRound = this.isRound,
            shadowAngle = this.shadowAngle,
            recentColors = this.recentColors.map { Color(it) },
            isShadowEnabled = this.isShadowEnabled,
            shadowOpacity = this.shadowOpacity,
            shadowBlur = this.shadowBlur.dp,
            customIcons = this.customIcons ?: emptyMap()
        )
    }
}

package com.sketcher.sketchercompanionv1.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable

data class UiScaler(
    val scaleFactor: Float,
    val screenWidth: Dp,
    val screenHeight: Dp
) {
    val baseButtonSize: Dp get() = 40.dp * scaleFactor
    val baseBarHeight: Dp get() = 48.dp * scaleFactor
    
    // Responsive width: 25% of screen width, clamped between 200dp and 350dp
    val sidePanelWidth: Dp get() = (screenWidth * 0.25f).coerceIn(200.dp, 350.dp) * scaleFactor
    
    val floatingBarWidth: Dp get() = 42.dp * scaleFactor
    val floatingBarThickness: Dp get() = 42.dp * scaleFactor // Used for bar thickness
    
    val margin: Dp get() = 8.dp * scaleFactor
    val smallMargin: Dp get() = 4.dp * scaleFactor
    val panelGap: Dp get() = 24.dp * scaleFactor // Gap for toggle buttons
    val toggleLength: Dp get() = 160.dp * scaleFactor
    val toggleThickness: Dp get() = 12.dp * scaleFactor
    val smallIconSize: Dp get() = 16.dp * scaleFactor
    val baseIconSize: Dp get() = 24.dp * scaleFactor
    
    // Derived Calculations using dynamic values
    val hudVerticalTargetPadding: Dp get() = baseBarHeight + (10.dp * scaleFactor)
    val hudVerticalCollapsedPadding: Dp get() = 10.dp * scaleFactor
}

val LocalUiScaler = androidx.compose.runtime.staticCompositionLocalOf<UiScaler> {
    error("No UiScaler provided")
}

val Int.sdp: Dp
    @Composable
    get() = with(LocalUiScaler.current) { this@sdp.dp * scaleFactor }

val Double.sdp: Dp
    @Composable
    get() = with(LocalUiScaler.current) { this@sdp.dp * scaleFactor }

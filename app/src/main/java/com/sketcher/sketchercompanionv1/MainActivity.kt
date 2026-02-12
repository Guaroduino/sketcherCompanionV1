package com.sketcher.sketchercompanionv1

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Opcional: Esto ayuda a que la app use toda la pantalla detrÃ¡s de las barras
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        // HIDE SYSTEM BARS (Immersive Mode)
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        insetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        
        setContent {
            val sketchViewModel: SketcherViewModel = viewModel()
            
            // HOISTED STATE
            var isStudioMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            var userScale by androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(1f) }
            var uiCollapsed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            
            // SWAP STATES (Hoisted)
            var swapVertical by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var swapHorizontal by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

            // UI SCALER PROVIDER
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val screenHeight = configuration.screenHeightDp.dp
            val scaler = remember(userScale, screenWidth, screenHeight) {
                com.sketcher.sketchercompanionv1.ui.theme.UiScaler(userScale, screenWidth, screenHeight)
            }

            androidx.compose.runtime.CompositionLocalProvider(
                com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler provides scaler
            ) {
                Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                    // 1. MAIN UI LAYER
                    if (isStudioMode) {
                        com.sketcher.sketchercompanionv1.ui.layout.StudioLayout(
                            viewModel = sketchViewModel,
                            userScale = userScale,
                            uiCollapsed = uiCollapsed,
                            onToggleUi = { uiCollapsed = !uiCollapsed },
                            swapVertical = swapVertical,
                            swapHorizontal = swapHorizontal
                        )
                    } else {
                        SketcherSurface(sketchViewModel)
                    }
                    
                    // 2. DEBUG OVERLAY (Persistent)
                    com.sketcher.sketchercompanionv1.ui.layout.DebugOverlay(
                        userScale = userScale,
                        onScaleChange = { userScale = it },
                        uiCollapsed = uiCollapsed,
                        onToggleUi = { uiCollapsed = !uiCollapsed },
                        onSwitchToLegacy = { isStudioMode = !isStudioMode },
                        isStudioMode = isStudioMode,
                        swapVertical = swapVertical,
                        swapHorizontal = swapHorizontal,
                        onToggleSwapVertical = { swapVertical = !swapVertical },
                        onToggleSwapHorizontal = { swapHorizontal = !swapHorizontal }
                    )
                }
            }
        }
    }
}

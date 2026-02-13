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
import androidx.compose.runtime.SideEffect

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        
        setContent {
            val view = androidx.compose.ui.platform.LocalView.current
            if (!view.isInEditMode) {
                androidx.compose.runtime.SideEffect {
                    val window = (this@MainActivity).window
                    androidx.core.view.WindowCompat.getInsetsController(window, view)?.let { controller ->
                        controller.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        controller.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
            
            val sketchViewModel: SketcherViewModel = viewModel()
            val interfaceScale by androidx.compose.runtime.remember { 
                androidx.compose.runtime.derivedStateOf { sketchViewModel.interfaceScale } 
            }
            
            // HOISTED STATE
            var isStudioMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            var uiCollapsed by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(true) }
            
            // SWAP STATES (Hoisted)
            var swapVertical by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
            var swapHorizontal by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
 
            // UI SCALER PROVIDER
            val configuration = androidx.compose.ui.platform.LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val screenHeight = configuration.screenHeightDp.dp
            
            val scaler = remember(interfaceScale, screenWidth, screenHeight) {
                com.sketcher.sketchercompanionv1.ui.theme.UiScaler(interfaceScale, screenWidth, screenHeight)
            }
 
            androidx.compose.runtime.CompositionLocalProvider(
                com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler provides scaler
            ) {
                com.sketcher.sketchercompanionv1.ui.theme.SketcherCompanionV1Theme {
                    Box(modifier = androidx.compose.ui.Modifier.fillMaxSize()) {
                        // 1. MAIN UI LAYER
                        if (isStudioMode) {
                            com.sketcher.sketchercompanionv1.ui.layout.StudioLayout(
                                viewModel = sketchViewModel,
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
                            userScale = interfaceScale,
                            onScaleChange = { sketchViewModel.updateInterfaceScale(it) },
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
}

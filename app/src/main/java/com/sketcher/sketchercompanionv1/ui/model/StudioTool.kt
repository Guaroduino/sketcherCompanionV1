package com.sketcher.sketchercompanionv1.ui.model

import androidx.compose.ui.graphics.vector.ImageVector

data class StudioTool(
    val id: String,
    val icon: ImageVector,
    val contentDescription: String,
    val isActive: Boolean = false,
    val onClick: () -> Unit = {}
)

package com.sketcher.sketchercompanionv1

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList

data class Layer(
    val id: String, 
    val name: String,
    val elements: SnapshotStateList<LayerElement> = mutableStateListOf(), 
    var isVisible: Boolean = true,
    var opacity: Float = 1f,
    var isLocked: Boolean = false
)


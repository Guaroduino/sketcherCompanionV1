package com.skecher.sketchercompanionv1

data class Layer(
    val id: String, 
    val name: String,
    val elements: MutableList<LayerElement> = mutableListOf(), 
    var isVisible: Boolean = true,
    var opacity: Float = 1f
)

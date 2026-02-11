package com.sketcher.sketchercompanionv1

data class ComponentDefinition(
    val id: String,
    val elements: MutableList<LayerElement> // The actual strokes
)


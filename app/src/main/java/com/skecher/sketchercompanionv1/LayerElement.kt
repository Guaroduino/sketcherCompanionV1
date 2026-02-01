package com.skecher.sketchercompanionv1

import android.graphics.Path

sealed interface LayerElement

data class AndroidInkElement(val stroke: androidx.ink.strokes.Stroke) : LayerElement
data class FillData(val path: Path, val color: Int) : LayerElement

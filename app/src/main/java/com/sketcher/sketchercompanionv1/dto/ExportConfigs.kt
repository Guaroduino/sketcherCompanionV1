package com.sketcher.sketchercompanionv1.dto

data class ExportPngConfig(
    val transparentBackground: Boolean,
    val useHomeView: Boolean,
    val width: Int,
    val height: Int
)

data class ExportSvgConfig(
    val includeBackground: Boolean,
    val useHomeView: Boolean,
    val width: Float,
    val height: Float
)

data class DxfExportConfig(
    val filename: String,
    val exportSelectionOnly: Boolean
)

package com.sketcher.sketchercompanionv1.dto

data class TextEditState(
    val isNewText: Boolean,
    val elementId: String,
    val textHtml: String,
    val defaultTextColor: Int,
    val defaultTextSize: Float,
    val fontFamilyName: String,
    val alignment: String,
    val styleTemplateName: String?,
    val initialX: Float = 0f,
    val initialY: Float = 0f
)

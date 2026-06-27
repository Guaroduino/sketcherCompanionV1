package com.sketcher.sketchercompanionv1.ui.model

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.toPath

data class VectorIcon(
    val paths: List<String>
) {
    fun toComposePaths(): List<Path> {
        return paths.mapNotNull { pathStr ->
            try {
                if (pathStr.trim().isEmpty()) return@mapNotNull null
                PathParser().parsePathString(pathStr).toPath()
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
}

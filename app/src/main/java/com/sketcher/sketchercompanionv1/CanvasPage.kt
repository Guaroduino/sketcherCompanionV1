package com.sketcher.sketchercompanionv1

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.sketcher.sketchercompanionv1.command.UndoCommand
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.toLayerJson
import com.sketcher.sketchercompanionv1.utils.toFillStyleJson
import java.util.ArrayDeque

data class CanvasPage(
    val id: String,
    var name: String,
    val layers: SnapshotStateList<Layer> = mutableStateListOf(),
    var activeLayerIndex: Int = 0,
    var backgroundColor: Int = android.graphics.Color.WHITE,
    var backgroundStyle: FillStyle = FillStyle.Solid(backgroundColor),
    var gridConfig: GridConfig = GridConfig(),
    var canvasSizeConfig: CanvasSizeConfig? = null,
    val cameraMatrixValues: FloatArray = FloatArray(9).apply { android.graphics.Matrix().getValues(this) },
    var scaleConfig: ScaleConfig = ScaleConfig(),
    var currentUnit: DistanceUnit = DistanceUnit.MM,
    val undoStack: java.util.ArrayDeque<UndoCommand> = java.util.ArrayDeque(),
    val redoStack: java.util.ArrayDeque<UndoCommand> = java.util.ArrayDeque()
) {
    fun toPageJson(): PageJson {
        return PageJson(
            id = id,
            name = name,
            layers = layers.map { it.toLayerJson() },
            backgroundConfig = BackgroundConfig(
                color = backgroundColor,
                gridConfig = gridConfig,
                fillStyle = backgroundStyle.toFillStyleJson()
            ),
            canvasMetadata = CanvasMetadata(
                width = 0f,
                height = 0f,
                cameraMatrix = cameraMatrixValues.toList(),
                scaleConfig = scaleConfig.copy(unitName = currentUnit.symbol)
            ),
            canvasSizeConfig = canvasSizeConfig
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CanvasPage

        if (id != other.id) return false

        return true
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

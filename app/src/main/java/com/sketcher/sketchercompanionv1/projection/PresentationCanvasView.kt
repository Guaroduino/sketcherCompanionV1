package com.sketcher.sketchercompanionv1.projection

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.RenderEngine
import com.sketcher.sketchercompanionv1.StrokePoint
import com.sketcher.sketchercompanionv1.dto.FillStyle

class PresentationCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val renderEngine = RenderEngine()

    private var layersSnapshot: List<Layer> = emptyList()
    private var componentLibrarySnapshot: Map<String, ComponentDefinition> = emptyMap()
    private var backgroundStyleSnapshot: FillStyle = FillStyle.Solid(Color.WHITE)
    private var cameraMatrixValuesSnapshot: FloatArray = FloatArray(9) { if (it == 0 || it == 4 || it == 8) 1f else 0f }
    
    private var phoneW: Float = 0f
    private var phoneH: Float = 0f

    // Live preview states
    private var livePoints: List<StrokePoint>? = null
    private var livePath: Path? = null
    private var committedPath: Path? = null
    private var liveFillPath: Path? = null
    private var liveRadius: Float = 0f
    private var strokeColor: Int = Color.BLACK
    private var fillColor: Int = Color.TRANSPARENT
    private var isStrokeActive: Boolean = true
    private var isFillActive: Boolean = false
    private var fillStyle: FillStyle? = null
    private var strokeStyle: FillStyle? = null

    fun updateState(
        layers: List<Layer>,
        componentLibrary: Map<String, ComponentDefinition>,
        backgroundStyle: FillStyle,
        cameraMatrixValues: FloatArray,
        phoneW: Float,
        phoneH: Float,
        strokeColor: Int,
        fillColor: Int,
        isStrokeActive: Boolean,
        isFillActive: Boolean,
        fillStyle: FillStyle?,
        strokeStyle: FillStyle?,
        livePoints: List<StrokePoint>?,
        livePath: Path?,
        committedPath: Path?,
        liveFillPath: Path?,
        liveRadius: Float
    ) {
        this.layersSnapshot = layers
        this.componentLibrarySnapshot = componentLibrary
        this.backgroundStyleSnapshot = backgroundStyle
        this.cameraMatrixValuesSnapshot = cameraMatrixValues.clone()
        this.phoneW = phoneW
        this.phoneH = phoneH
        this.strokeColor = strokeColor
        this.fillColor = fillColor
        this.isStrokeActive = isStrokeActive
        this.isFillActive = isFillActive
        this.fillStyle = fillStyle
        this.strokeStyle = strokeStyle
        this.livePoints = livePoints
        this.livePath = livePath?.let { Path(it) }
        this.committedPath = committedPath?.let { Path(it) }
        this.liveFillPath = liveFillPath?.let { Path(it) }
        this.liveRadius = liveRadius
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bgSolidColor = if (backgroundStyleSnapshot is FillStyle.Solid) {
            (backgroundStyleSnapshot as FillStyle.Solid).color
        } else {
            Color.WHITE
        }
        canvas.drawColor(bgSolidColor)

        val outW = width.toFloat()
        val outH = height.toFloat()
        if (outW <= 0f || outH <= 0f) return

        val fitMatrix = Matrix()
        
        // Calculate dynamic scaling matching the aspect ratio of the phone screen
        val pW = phoneW.coerceAtLeast(1f)
        val pH = phoneH.coerceAtLeast(1f)
        val phoneAR = pW / pH
        val clientAR = outW / outH

        val scale: Float
        val tx: Float
        val ty: Float
        if (clientAR > phoneAR) {
            scale = outH / pH
            tx = (outW - pW * scale) / 2f
            ty = 0f
        } else {
            scale = outW / pW
            tx = 0f
            ty = (outH - pH * scale) / 2f
        }

        val phoneCameraMatrix = Matrix()
        phoneCameraMatrix.setValues(cameraMatrixValuesSnapshot)

        fitMatrix.set(phoneCameraMatrix)
        fitMatrix.postScale(scale, scale)
        fitMatrix.postTranslate(tx, ty)

        renderEngine.canvasBackgroundStyle = backgroundStyleSnapshot
        renderEngine.canvasBackgroundColor = bgSolidColor
        
        // Draw layers on secondary screen
        renderEngine.drawLayers(
            canvas = canvas,
            layers = layersSnapshot,
            viewMatrix = fitMatrix,
            componentLibrary = componentLibrarySnapshot,
            selectedElements = null,
            isTransformActive = false,
            drawGrid = false,
            clientMode = true
        )

        // Draw committed preview stroke if any
        if (committedPath != null && isStrokeActive) {
            canvas.save()
            canvas.concat(fitMatrix)
            renderEngine.drawCommittedPreview(
                canvas = canvas,
                committedPath = committedPath!!,
                strokeColor = strokeColor,
                fillColor = fillColor,
                isStrokeActive = isStrokeActive,
                isFillActive = isFillActive,
                fillStyle = fillStyle,
                strokeStyle = strokeStyle
            )
            canvas.restore()
        }

        // Draw live active stroke if any
        if (livePath != null || livePoints != null) {
            renderEngine.drawLiveStroke(
                canvas = canvas,
                previewPoints = livePoints,
                previewPath = livePath,
                previewColor = strokeColor,
                fillPath = liveFillPath,
                fillColor = fillColor,
                isFillActive = isFillActive,
                isStrokeActive = isStrokeActive,
                currentLiveGeneratedRadius = liveRadius,
                viewMatrix = fitMatrix,
                isDrawing = true,
                fillStyle = fillStyle,
                strokeStyle = strokeStyle
            )
        }
    }
}

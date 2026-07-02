package com.sketcher.sketchercompanionv1.projection

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.toMutableStateList
import com.sketcher.sketchercompanionv1.ComponentDefinition
import com.sketcher.sketchercompanionv1.Layer
import com.sketcher.sketchercompanionv1.RenderEngine
import com.sketcher.sketchercompanionv1.StrokePoint
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.dto.FillStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Controller that decouples projection server lifecycle, frame rendering,
 * and viewport computations from the main ViewModel.
 */
class LiveProjectionController(
    private val scope: CoroutineScope,
    private val onClientCountChanged: (Int) -> Unit,
    private val onUrlChanged: (String) -> Unit,
    private val onActiveChanged: (Boolean) -> Unit,
    private val onViewportsChanged: (List<SketcherViewModel.ProjectionViewport>) -> Unit
) {

    var isProjectionActive = false
        private set(value) {
            field = value
            onActiveChanged(value)
        }

    var projectionUrl = ""
        private set(value) {
            field = value
            onUrlChanged(value)
        }

    var projectionClientCount = 0
        private set(value) {
            field = value
            onClientCountChanged(value)
        }

    var isProjectionPaused = false
        private set

    var projectionMode = "sync" // "sync" | "fixed"
        private set

    var fixedZoomMode = "fit" // "fit" | "home"
        private set

    var projectionViewports: List<SketcherViewModel.ProjectionViewport> = emptyList()
        private set(value) {
            field = value
            onViewportsChanged(value)
        }

    private var projectionServer: LiveProjectionServer? = null
    
    // Viewport layout calculations
    private var lastViewportWidth: Float = 0f
    private var lastViewportHeight: Float = 0f

    private val viewportColors = listOf(
        Color.parseColor("#00E5FF"),  // cyan
        Color.parseColor("#FF6D00"),  // orange
        Color.parseColor("#D500F9"),  // magenta
        Color.parseColor("#76FF03"),  // lime
    )

    fun start() {
        if (isProjectionActive) return
        val ip = getLocalIpAddress() ?: run {
            Log.w("Projection", "No WiFi IP found")
            return
        }
        val port = 8080
        try {
            val server = LiveProjectionServer(
                port = port,
                getCurrentMode = { projectionMode },
                onClientCountChanged = { count ->
                    scope.launch(Dispatchers.Main) {
                        projectionClientCount = count
                        updateProjectionViewports()
                    }
                },
                onClientUpdated = {
                    scope.launch(Dispatchers.Main) {
                        updateProjectionViewports()
                    }
                }
            )
            server.start(0, false)
            projectionServer = server
            projectionUrl = "http://$ip:$port"
            isProjectionActive = true
            Log.d("Projection", "Server started at $projectionUrl")
        } catch (e: Exception) {
            Log.e("Projection", "Failed to start server", e)
        }
    }

    fun stop() {
        projectionServer?.stop()
        projectionServer = null
        isProjectionActive = false
        isProjectionPaused = false
        projectionMode = "sync"
        projectionUrl = ""
        projectionClientCount = 0
        projectionViewports = emptyList()
    }

    fun togglePause() {
        isProjectionPaused = !isProjectionPaused
        updateProjectionViewports()
    }

    fun updateMode(mode: String) {
        if (mode == "sync" || mode == "fixed") {
            projectionMode = mode
            projectionServer?.clients?.forEach { client ->
                client.mode = mode
            }
            updateProjectionViewports()
        }
    }

    fun updateFixedZoomMode(mode: String) {
        fixedZoomMode = mode
    }

    fun updateViewportDimensions(width: Float, height: Float) {
        lastViewportWidth = width
        lastViewportHeight = height
        updateProjectionViewports()
    }

    fun renderAndSendSyncFrame(
        layers: List<Layer>,
        componentLibrary: Map<String, ComponentDefinition>,
        backgroundStyle: FillStyle,
        cameraMatrixValues: FloatArray,
        strokeColor: Int,
        fillColor: Int,
        isStrokeActive: Boolean,
        isFillActive: Boolean,
        livePoints: List<StrokePoint>?,
        livePath: android.graphics.Path?,
        committedPath: android.graphics.Path?,
        liveFillPath: android.graphics.Path?,
        liveRadius: Float
    ) {
        val server = projectionServer ?: return
        val clients = server.clients
        if (clients.isEmpty() || isProjectionPaused) return

        // Take snapshot copies of states to release locks quickly
        val layersSnapshot = layers.map { layer ->
            layer.copy(elements = layer.elements.toMutableStateList())
        }
        val compLibSnapshot = HashMap(componentLibrary)
        val cameraMatrixValuesSnapshot = cameraMatrixValues.clone()
        val phoneW = lastViewportWidth
        val phoneH = lastViewportHeight

        scope.launch(Dispatchers.Default) {
            try {
                val jpegByClient = mutableMapOf<Int, ByteArray>()
                for (client in clients) {
                    val outW = client.clientWidth.coerceAtLeast(320)
                    val outH = client.clientHeight.coerceAtLeast(240)

                    val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    val bgSolidColor = if (backgroundStyle is FillStyle.Solid) backgroundStyle.color else Color.WHITE
                    canvas.drawColor(bgSolidColor)

                    val fitMatrix = Matrix()
                    val pW = phoneW.coerceAtLeast(1f)
                    val pH = phoneH.coerceAtLeast(1f)
                    val phoneAR = pW / pH
                    val clientAR = outW.toFloat() / outH.toFloat()

                    val scale: Float
                    val tx: Float
                    val ty: Float
                    if (clientAR > phoneAR) {
                        scale = outW.toFloat() / pW
                        tx = 0f
                        ty = (outH - pH * scale) / 2f
                    } else {
                        scale = outH.toFloat() / pH
                        tx = (outW - pW * scale) / 2f
                        ty = 0f
                    }

                    val phoneCameraMatrix = Matrix()
                    phoneCameraMatrix.setValues(cameraMatrixValuesSnapshot)

                    fitMatrix.set(phoneCameraMatrix)
                    fitMatrix.postScale(scale, scale)
                    fitMatrix.postTranslate(tx, ty)

                    val renderEngine = RenderEngine()
                    renderEngine.canvasBackgroundStyle = backgroundStyle
                    renderEngine.canvasBackgroundColor = if (backgroundStyle is FillStyle.Solid) backgroundStyle.color else Color.WHITE
                    renderEngine.drawLayers(
                        canvas = canvas,
                        layers = layersSnapshot,
                        viewMatrix = fitMatrix,
                        componentLibrary = compLibSnapshot,
                        selectedElements = null,
                        isTransformActive = false,
                        drawGrid = false,
                        clientMode = true
                    )

                    if (committedPath != null && isStrokeActive) {
                        canvas.save()
                        canvas.concat(fitMatrix)
                        renderEngine.drawCommittedPreview(canvas, committedPath, strokeColor)
                        canvas.restore()
                    }

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
                            isDrawing = true
                        )
                    }

                    val out = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
                    bitmap.recycle()
                    jpegByClient[client.id] = out.toByteArray()
                }

                if (jpegByClient.isNotEmpty()) {
                    server.broadcastSyncFrames(jpegByClient)
                }
            } catch (e: Exception) {
                Log.e("Projection", "Error rendering sync frame", e)
            }
        }
    }

    fun renderAndSendFixedSnapshot(
        layers: List<Layer>,
        componentLibrary: Map<String, ComponentDefinition>,
        backgroundStyle: FillStyle,
        homeCameraMatrixValues: FloatArray,
        strokeColor: Int,
        fillColor: Int,
        isStrokeActive: Boolean,
        isFillActive: Boolean,
        livePoints: List<StrokePoint>?,
        livePath: android.graphics.Path?,
        committedPath: android.graphics.Path?,
        liveFillPath: android.graphics.Path?,
        liveRadius: Float
    ) {
        val server = projectionServer ?: return
        val clients = server.clients
        if (clients.isEmpty() || isProjectionPaused) return

        val client = clients.firstOrNull() ?: return
        val outW = client.clientWidth.coerceAtLeast(320)
        val outH = client.clientHeight.coerceAtLeast(240)
        
        val layersSnapshot = layers.map { layer ->
            layer.copy(elements = layer.elements.toMutableStateList())
        }
        val compLibSnapshot = HashMap(componentLibrary)
        val homeMatrixValuesSnapshot = homeCameraMatrixValues.clone()
        val phoneW = lastViewportWidth
        val phoneH = lastViewportHeight

        scope.launch(Dispatchers.Default) {
            try {
                val bitmap = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val bgSolidColor = if (backgroundStyle is FillStyle.Solid) backgroundStyle.color else Color.WHITE
                canvas.drawColor(bgSolidColor)

                val fitMatrix = Matrix()
                if (fixedZoomMode == "home") {
                    val homeMatrix = Matrix()
                    homeMatrix.setValues(homeMatrixValuesSnapshot)
                    
                    val pW = phoneW.coerceAtLeast(1f)
                    val pH = phoneH.coerceAtLeast(1f)
                    val scale = minOf(outW.toFloat() / pW, outH.toFloat() / pH)
                    
                    fitMatrix.postTranslate(-pW / 2f, -pH / 2f)
                    fitMatrix.postScale(scale, scale)
                    fitMatrix.postTranslate(outW / 2f, outH / 2f)
                    fitMatrix.preConcat(homeMatrix)
                } else {
                    val allBounds = RectF()
                    var first = true
                    for (layer in layersSnapshot) {
                        if (!layer.isVisible || !layer.isVisibleOnClient) continue
                        for (element in layer.elements) {
                            val b = element.getBoundingBox(compLibSnapshot)
                            if (b.isEmpty) continue
                            if (first) { allBounds.set(b); first = false } else allBounds.union(b)
                        }
                    }

                    if (!allBounds.isEmpty) {
                        val scaleX = outW / allBounds.width()
                        val scaleY = outH / allBounds.height()
                        val scale = minOf(scaleX, scaleY) * 0.9f
                        val tx = (outW - allBounds.width() * scale) / 2f - allBounds.left * scale
                        val ty = (outH - allBounds.height() * scale) / 2f - allBounds.top * scale
                        fitMatrix.setScale(scale, scale)
                        fitMatrix.postTranslate(tx, ty)
                    }
                }

                val renderEngine = RenderEngine()
                renderEngine.canvasBackgroundStyle = backgroundStyle
                renderEngine.canvasBackgroundColor = if (backgroundStyle is FillStyle.Solid) backgroundStyle.color else Color.WHITE
                renderEngine.drawLayers(
                    canvas = canvas,
                    layers = layersSnapshot,
                    viewMatrix = fitMatrix,
                    componentLibrary = compLibSnapshot,
                    selectedElements = null,
                    isTransformActive = false,
                    drawGrid = false,
                    clientMode = true
                )

                if (committedPath != null && isStrokeActive) {
                    canvas.save()
                    canvas.concat(fitMatrix)
                    renderEngine.drawCommittedPreview(canvas, committedPath, strokeColor)
                    canvas.restore()
                }

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
                        isDrawing = true
                    )
                }

                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                bitmap.recycle()
                server.broadcastFixedSnapshot(out.toByteArray())
            } catch (e: Exception) {
                Log.e("Projection", "Error rendering fixed snapshot", e)
            }
        }
    }

    private fun updateProjectionViewports() {
        val server = projectionServer ?: run { projectionViewports = emptyList(); return }
        if (isProjectionPaused || projectionMode == "fixed" || server.clients.isEmpty()) {
            projectionViewports = emptyList()
            return
        }
        val vW = lastViewportWidth
        val vH = lastViewportHeight
        if (vW <= 0 || vH <= 0) return

        val vAR = vW / vH
        val newViewports = mutableListOf<SketcherViewModel.ProjectionViewport>()
        server.clients.forEachIndexed { index, client ->
            val color = viewportColors[index % viewportColors.size]
            val label = "Cliente ${index + 1}"
            val clientAR = client.clientWidth.toFloat() / client.clientHeight.toFloat()

            val (rL, rT, rR, rB) = if (kotlin.math.abs(clientAR - vAR) < 0.05f) {
                listOf(0f, 0f, vW, vH)
            } else if (clientAR > vAR) {
                val h = vW / clientAR
                val top = (vH - h) / 2f
                listOf(0f, top, vW, top + h)
            } else {
                val w = vH * clientAR
                val left = (vW - w) / 2f
                listOf(left, 0f, left + w, vH)
            }
            newViewports.add(SketcherViewModel.ProjectionViewport(rL, rT, rR, rB, color, label))
        }
        projectionViewports = newViewports
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = java.util.Collections.list(NetworkInterface.getNetworkInterfaces())
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (name.contains("wlan") || name.contains("wifi") || name.contains("ap")) {
                    for (addr in intf.inetAddresses) {
                        if (!addr.isLoopbackAddress && addr is Inet4Address) {
                            return addr.hostAddress
                        }
                    }
                }
            }
            for (intf in interfaces) {
                val name = intf.name.lowercase()
                if (name.contains("rmnet") || name.contains("ccmni") || name.contains("p2p") || name.contains("dummy")) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: continue
                        if (!ip.startsWith("10.0.2") && !ip.startsWith("127.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Projection", "getLocalIpAddress failed", e)
        }
        return null
    }
}

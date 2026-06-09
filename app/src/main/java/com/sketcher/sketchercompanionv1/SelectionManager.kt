package com.sketcher.sketchercompanionv1

import android.graphics.Matrix
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.State
import com.sketcher.sketchercompanionv1.managers.LayerManager

class SelectionManager {
    val selectedElements = mutableStateListOf<LayerElement>()
    
    // Observable Compose States
    val hasSelection: State<Boolean> = derivedStateOf { selectedElements.isNotEmpty() }
    val selectionCount: State<Int> = derivedStateOf { selectedElements.size }

    val selectionMatrix = Matrix()
    var activeTransform: Matrix? = null
    var baseBounds = RectF()

    // --- SELECTION STATE ---
    val lassoPath = Path()
    var startX = 0f
    var startY = 0f
    var isRectangleMode = false

    // --- DRAG TRANSFORMATION STATE ---
    enum class DragMode {
        NONE, TRANSLATE, 
        SCALE_TL, SCALE_TR, SCALE_BL, SCALE_BR, 
        SCALE_T, SCALE_B, SCALE_L, SCALE_R, 
        ROTATE
    }
    
    var currentDragMode = DragMode.NONE
        private set

    private val startSelectionMatrix = Matrix()
    private var startWorldX = 0f
    private var startWorldY = 0f
    private var rotateCenterX = 0f
    private var rotateCenterY = 0f
    private var startAngle = 0f
    private var startDistance = 0f
    private var startScalePivotX = 0f
    private var startScalePivotY = 0f
    private var startScalePivotXLocal = 0f
    private var startScalePivotYLocal = 0f
    private val startSelectionMatrixInverse = Matrix()
    private var startLocalTouchX = 0f
    private var startLocalTouchY = 0f
    
    fun selectSingleAt(x: Float, y: Float, layer: Layer, library: Map<String, ComponentDefinition>, addToSelection: Boolean = false): Boolean {
        val iterator = layer.elements.listIterator(layer.elements.size)
        while (iterator.hasPrevious()) {
            val element = iterator.previous()
            if (isHit(element, x, y, library)) {
                if (!addToSelection) {
                    selectedElements.clear()
                    selectionMatrix.reset()
                }
                selectedElements.add(element)
                recalculateBaseBounds(library)
                return true
            }
        }
        if (!addToSelection) clearSelection()
        return false
    }

    private fun distanceToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        if (dx == 0f && dy == 0f) {
            return kotlin.math.hypot(px - x1, py - y1)
        }
        val t = ((px - x1) * dx + (py - y1) * dy) / (dx * dx + dy * dy)
        return if (t < 0f) {
            kotlin.math.hypot(px - x1, py - y1)
        } else if (t > 1f) {
            kotlin.math.hypot(px - x2, py - y2)
        } else {
            val nearestX = x1 + t * dx
            val nearestY = y1 + t * dy
            kotlin.math.hypot(px - nearestX, py - nearestY)
        }
    }

    private fun isElementInSelection(
        element: LayerElement,
        selectionPath: Path,
        selectionRegion: android.graphics.Region,
        library: Map<String, ComponentDefinition>
    ): Boolean {
        when (element) {
            is VectorStroke -> {
                // 1. Check if any stroke point is inside the selection region
                for (p in element.points) {
                    if (selectionRegion.contains(p.x.toInt(), p.y.toInt())) {
                        return true
                    }
                }
                // 2. Also check if the element path intersects the selection path
                if (element.points.size >= 2) {
                    val intersect = Path()
                    try {
                        if (intersect.op(selectionPath, element.path, Path.Op.INTERSECT)) {
                            if (!intersect.isEmpty) return true
                        }
                    } catch (e: Exception) {
                        // Fallback
                    }
                }
                return false
            }
            is GroupElement -> {
                return element.elements.any { isElementInSelection(it, selectionPath, selectionRegion, library) }
            }
            is FillData -> {
                val intersect = Path()
                try {
                    if (intersect.op(selectionPath, element.path, Path.Op.INTERSECT)) {
                        return !intersect.isEmpty
                    }
                } catch (e: Exception) {
                    // Fallback
                }
                return false
            }
            else -> {
                val rect = element.getBoundingBox(library)
                val selectionBounds = RectF()
                selectionPath.computeBounds(selectionBounds, true)
                if (!RectF.intersects(selectionBounds, rect)) return false
                
                // If bounding boxes intersect, check if any corner or center is in selection region
                if (selectionRegion.contains(rect.left.toInt(), rect.top.toInt())) return true
                if (selectionRegion.contains(rect.right.toInt(), rect.top.toInt())) return true
                if (selectionRegion.contains(rect.left.toInt(), rect.bottom.toInt())) return true
                if (selectionRegion.contains(rect.right.toInt(), rect.bottom.toInt())) return true
                if (selectionRegion.contains(rect.centerX().toInt(), rect.centerY().toInt())) return true
                
                return false
            }
        }
    }

    fun selectArea(selectionPath: Path, layer: Layer, library: Map<String, ComponentDefinition>, addToSelection: Boolean = false) {
        if (!addToSelection) {
            selectedElements.clear()
            selectionMatrix.reset()
        }
        val selectionBounds = RectF()
        selectionPath.computeBounds(selectionBounds, true)

        val selectionRegion = android.graphics.Region()
        val clipRect = android.graphics.Rect(
            selectionBounds.left.toInt(),
            selectionBounds.top.toInt(),
            selectionBounds.right.toInt(),
            selectionBounds.bottom.toInt()
        )
        selectionRegion.setPath(selectionPath, android.graphics.Region(clipRect))

        layer.elements.forEach { element ->
            if (isElementInSelection(element, selectionPath, selectionRegion, library)) {
                selectedElements.add(element)
            }
        }
        recalculateBaseBounds(library)
    }

    fun recalculateBaseBounds(library: Map<String, ComponentDefinition>) {
        baseBounds.setEmpty()
        if (selectedElements.isEmpty()) return
        
        selectedElements.forEachIndexed { index, element ->
            if (index == 0) {
                baseBounds.set(element.getBoundingBox(library))
            } else {
                baseBounds.union(element.getBoundingBox(library))
            }
        }
    }

    fun getSelectionBounds(): RectF {
        val rect = RectF(baseBounds)
        selectionMatrix.mapRect(rect)
        return rect
    }

    fun applyTransform(matrix: Matrix) {
        if (activeTransform == null) activeTransform = Matrix()
        activeTransform!!.postConcat(matrix)
        selectionMatrix.postConcat(matrix)
    }
    
    fun commitTransform() {
        activeTransform?.let { transform ->
             selectedElements.forEach { it.transform(transform) }
             activeTransform = null
        }
    }

    val originalElementsBackup = mutableListOf<LayerElement>()

    fun backupOriginalElements() {
        originalElementsBackup.clear()
        originalElementsBackup.addAll(selectedElements.map { it.copyElement() })
    }

    fun clearBackup() {
        originalElementsBackup.clear()
    }

    fun restoreOriginalElements(activeLayer: Layer) {
        if (originalElementsBackup.isEmpty()) return
        
        selectedElements.forEachIndexed { idx, selected ->
            val backup = originalElementsBackup.getOrNull(idx) ?: return@forEachIndexed
            val layerIdx = activeLayer.elements.indexOfFirst { it === selected }
            if (layerIdx != -1) {
                activeLayer.elements[layerIdx] = backup
            }
        }
        
        selectedElements.clear()
        selectedElements.addAll(originalElementsBackup)
        originalElementsBackup.clear()
    }
    
    // --- TOUCH SELECTION GESTURES ---
    fun startSelection(worldX: Float, worldY: Float) {
        lassoPath.reset()
        lassoPath.moveTo(worldX, worldY)
        startX = worldX
        startY = worldY
    }

    fun updateSelection(worldX: Float, worldY: Float) {
        if (isRectangleMode) {
            lassoPath.reset()
            val left = minOf(startX, worldX)
            val top = minOf(startY, worldY)
            val right = maxOf(startX, worldX)
            val bottom = maxOf(startY, worldY)
            lassoPath.addRect(left, top, right, bottom, Path.Direction.CW)
        } else {
            lassoPath.lineTo(worldX, worldY)
        }
    }

    fun finalizeSelection(activeLayer: Layer, library: Map<String, ComponentDefinition>) {
        if (!isRectangleMode) {
            lassoPath.close()
        }
        selectArea(lassoPath, activeLayer, library)
        lassoPath.reset()
    }

    // --- TRANSFORM BOX TOUCH GESTURES ---
    fun handleTransformDown(worldX: Float, worldY: Float, eventX: Float, eventY: Float, viewMatrix: Matrix) {
        if (selectedElements.isEmpty()) {
            currentDragMode = DragMode.NONE
            return
        }

        val left = baseBounds.left
        val top = baseBounds.top
        val right = baseBounds.right
        val bottom = baseBounds.bottom
        val centerX = baseBounds.centerX()
        val centerY = baseBounds.centerY()
        
        val values = FloatArray(9)
        viewMatrix.getValues(values)
        val zoom = kotlin.math.sqrt(values[Matrix.MSCALE_X] * values[Matrix.MSCALE_X] + values[Matrix.MSKEW_X] * values[Matrix.MSKEW_X]).coerceAtLeast(0.001f)
        val stemLength = 30f / zoom

        val localPts = floatArrayOf(
            left, top,       // TL (0)
            centerX, top,    // T  (1)
            right, top,      // TR (2)
            right, centerY,  // R  (3)
            right, bottom,   // BR (4)
            centerX, bottom, // B  (5)
            left, bottom,    // BL (6)
            left, centerY,   // L  (7)
            centerX, top - stemLength // ROT (8)
        )
        
        val screenPts = FloatArray(18)
        val combinedMatrix = Matrix()
        combinedMatrix.set(viewMatrix)
        combinedMatrix.preConcat(selectionMatrix)
        combinedMatrix.mapPoints(screenPts, localPts)

        // 48 pixels touch target size (~16-24dp tolerance)
        val tolerance = 48f
        var selectedHandle = -1
        var minDist = Float.MAX_VALUE
        
        for (i in 0 until 9) {
            val hx = screenPts[i * 2]
            val hy = screenPts[i * 2 + 1]
            val dist = kotlin.math.hypot(eventX - hx, eventY - hy)
            if (dist < tolerance && dist < minDist) {
                minDist = dist
                selectedHandle = i
            }
        }

        if (selectedHandle == -1) {
            // Check inside box
            val selectionMatrixInverse = Matrix()
            if (selectionMatrix.invert(selectionMatrixInverse)) {
                val localPt = floatArrayOf(worldX, worldY)
                selectionMatrixInverse.mapPoints(localPt)
                if (baseBounds.contains(localPt[0], localPt[1])) {
                    currentDragMode = DragMode.TRANSLATE
                } else {
                    currentDragMode = DragMode.NONE
                }
            } else {
                currentDragMode = DragMode.NONE
            }
        } else {
            currentDragMode = when (selectedHandle) {
                0 -> DragMode.SCALE_TL
                1 -> DragMode.SCALE_T
                2 -> DragMode.SCALE_TR
                3 -> DragMode.SCALE_R
                4 -> DragMode.SCALE_BR
                5 -> DragMode.SCALE_B
                6 -> DragMode.SCALE_BL
                7 -> DragMode.SCALE_L
                8 -> DragMode.ROTATE
                else -> DragMode.NONE
            }
        }

        if (currentDragMode != DragMode.NONE) {
            startSelectionMatrix.set(selectionMatrix)
            startWorldX = worldX
            startWorldY = worldY
            
            val startCenter = floatArrayOf(centerX, centerY)
            startSelectionMatrix.mapPoints(startCenter)
            rotateCenterX = startCenter[0]
            rotateCenterY = startCenter[1]
            
            if (currentDragMode == DragMode.ROTATE) {
                startAngle = kotlin.math.atan2(worldY - rotateCenterY, worldX - rotateCenterX)
            } else if (currentDragMode in listOf(DragMode.SCALE_TL, DragMode.SCALE_TR, DragMode.SCALE_BL, DragMode.SCALE_BR, DragMode.SCALE_T, DragMode.SCALE_B, DragMode.SCALE_L, DragMode.SCALE_R)) {
                val pivotLocalX = centerX
                val pivotLocalY = centerY
                
                startScalePivotXLocal = pivotLocalX
                startScalePivotYLocal = pivotLocalY
                
                val pivotWorld = floatArrayOf(pivotLocalX, pivotLocalY)
                startSelectionMatrix.mapPoints(pivotWorld)
                startScalePivotX = pivotWorld[0]
                startScalePivotY = pivotWorld[1]
                
                startSelectionMatrix.invert(startSelectionMatrixInverse)
                val localTouch = floatArrayOf(worldX, worldY)
                startSelectionMatrixInverse.mapPoints(localTouch)
                startLocalTouchX = localTouch[0]
                startLocalTouchY = localTouch[1]
                
                startDistance = kotlin.math.hypot(worldX - startScalePivotX, worldY - startScalePivotY)
            }
        }
    }

    fun handleTransformMove(worldX: Float, worldY: Float) {
        if (currentDragMode == DragMode.NONE) return
        
        val tempTransform = Matrix()
        when (currentDragMode) {
            DragMode.TRANSLATE -> {
                val dx = worldX - startWorldX
                val dy = worldY - startWorldY
                tempTransform.postTranslate(dx, dy)
                
                if (activeTransform == null) activeTransform = Matrix()
                activeTransform!!.set(tempTransform)
                
                selectionMatrix.set(startSelectionMatrix)
                selectionMatrix.postConcat(tempTransform)
            }
            DragMode.ROTATE -> {
                val currentAngle = kotlin.math.atan2(worldY - rotateCenterY, worldX - rotateCenterX)
                val diffRad = currentAngle - startAngle
                val degrees = Math.toDegrees(diffRad.toDouble()).toFloat()
                
                tempTransform.postRotate(degrees, rotateCenterX, rotateCenterY)
                
                if (activeTransform == null) activeTransform = Matrix()
                activeTransform!!.set(tempTransform)
                
                selectionMatrix.set(startSelectionMatrix)
                selectionMatrix.postConcat(tempTransform)
            }
            DragMode.SCALE_TL, DragMode.SCALE_TR, DragMode.SCALE_BL, DragMode.SCALE_BR,
            DragMode.SCALE_T, DragMode.SCALE_B, DragMode.SCALE_L, DragMode.SCALE_R -> {
                val localTouch = floatArrayOf(worldX, worldY)
                startSelectionMatrixInverse.mapPoints(localTouch)
                val currLocalTouchX = localTouch[0]
                val currLocalTouchY = localTouch[1]
                
                val scaleX: Float
                val scaleY: Float
                
                val isCorner = currentDragMode in listOf(DragMode.SCALE_TL, DragMode.SCALE_TR, DragMode.SCALE_BL, DragMode.SCALE_BR)
                if (isCorner) {
                    val startDist = kotlin.math.hypot(startLocalTouchX - startScalePivotXLocal, startLocalTouchY - startScalePivotYLocal).coerceAtLeast(0.001f)
                    val currDist = kotlin.math.hypot(currLocalTouchX - startScalePivotXLocal, currLocalTouchY - startScalePivotYLocal)
                    val s = currDist / startDist
                    
                    val dotProduct = (currLocalTouchX - startScalePivotXLocal) * (startLocalTouchX - startScalePivotXLocal) +
                                     (currLocalTouchY - startScalePivotYLocal) * (startLocalTouchY - startScalePivotYLocal)
                    val sign = if (dotProduct < 0f) -1f else 1f
                    
                    val sFinal = s * sign
                    val signCoerced = if (sFinal < 0f) -1f else 1f
                    val sCoerced = if (kotlin.math.abs(sFinal) < 0.05f) 0.05f * signCoerced else sFinal
                    
                    scaleX = sCoerced
                    scaleY = sCoerced
                } else {
                    val widthStart = startLocalTouchX - startScalePivotXLocal
                    val heightStart = startLocalTouchY - startScalePivotYLocal
                    
                    val widthCurr = currLocalTouchX - startScalePivotXLocal
                    val heightCurr = currLocalTouchY - startScalePivotYLocal
                    
                    if (currentDragMode in listOf(DragMode.SCALE_L, DragMode.SCALE_R)) {
                        val denomX = if (kotlin.math.abs(widthStart) < 0.001f) {
                            0.001f * (if (widthStart < 0f) -1f else 1f)
                        } else {
                            widthStart
                        }
                        val sX = widthCurr / denomX
                        val signCoercedX = if (sX < 0f) -1f else 1f
                        scaleX = if (kotlin.math.abs(sX) < 0.05f) 0.05f * signCoercedX else sX
                        scaleY = 1f
                    } else {
                        scaleX = 1f
                        val denomY = if (kotlin.math.abs(heightStart) < 0.001f) {
                            0.001f * (if (heightStart < 0f) -1f else 1f)
                        } else {
                            heightStart
                        }
                        val sY = heightCurr / denomY
                        val signCoercedY = if (sY < 0f) -1f else 1f
                        scaleY = if (kotlin.math.abs(sY) < 0.05f) 0.05f * signCoercedY else sY
                    }
                }
                
                val tempTransformLocal = Matrix()
                tempTransformLocal.postScale(scaleX, scaleY, startScalePivotXLocal, startScalePivotYLocal)
                
                tempTransform.set(startSelectionMatrix)
                tempTransform.preConcat(tempTransformLocal)
                tempTransform.preConcat(startSelectionMatrixInverse)
                
                if (activeTransform == null) activeTransform = Matrix()
                activeTransform!!.set(tempTransform)
                
                selectionMatrix.set(startSelectionMatrix)
                selectionMatrix.preConcat(tempTransformLocal)
            }
            else -> {}
        }
    }

    fun handleTransformUp() {
        if (currentDragMode == DragMode.NONE) return
        activeTransform = null
        currentDragMode = DragMode.NONE
    }

    fun commitTransformSession(library: Map<String, ComponentDefinition> = emptyMap()) {
        if (selectedElements.isEmpty()) return
        selectedElements.forEach { it.transform(selectionMatrix) }
        recalculateBaseBounds(library)
        selectionMatrix.reset()
        activeTransform = null
    }

    fun cancelTransformSession(activeLayer: Layer, library: Map<String, ComponentDefinition> = emptyMap()) {
        restoreOriginalElements(activeLayer)
        selectionMatrix.reset()
        activeTransform = null
        recalculateBaseBounds(library)
    }

    fun clearSelection() {
        selectedElements.clear()
        selectionMatrix.reset()
        activeTransform = null
        baseBounds.setEmpty()
    }

    fun deleteSelected(layerManager: LayerManager, activeLayerIndex: Int, onPerformSnapshot: (String, () -> Unit) -> Unit) {
        if (selectedElements.isEmpty()) return
        onPerformSnapshot("Borrar Selección") {
            val currentLayers = layerManager.layers.toMutableList()
            currentLayers.forEachIndexed { index, layer ->
                val remaining = layer.elements.filter { it !in selectedElements }
                if (remaining.size != layer.elements.size) {
                    currentLayers[index] = layer.copy(elements = remaining.toMutableStateList())
                }
            }
            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            clearSelection()
        }
    }

    fun duplicateSelected(layerManager: LayerManager, activeLayerIndex: Int, onPerformSnapshot: (String, () -> Unit) -> Unit) {
        if (selectedElements.isEmpty()) return
        onPerformSnapshot("Duplicar Selección") {
            val currentLayers = layerManager.layers.toMutableList()
            val activeLayer = currentLayers[activeLayerIndex]
            
            val offsetMatrix = Matrix().apply { postTranslate(20f, 20f) }
            val duplicatedElements = selectedElements.map { 
                val copy = it.copyElement()
                copy.transform(offsetMatrix)
                copy
            }
            
            currentLayers[activeLayerIndex] = activeLayer.copy(
                elements = (activeLayer.elements + duplicatedElements).toMutableStateList()
            )
            layerManager.internalUpdateLayers(currentLayers, activeLayerIndex)
            
            selectedElements.clear()
            selectedElements.addAll(duplicatedElements)
            selectionMatrix.reset()
            recalculateBaseBounds(emptyMap())
        }
    }

    private fun isHit(element: LayerElement, x: Float, y: Float, library: Map<String, ComponentDefinition>): Boolean {
        when (element) {
            is VectorStroke -> {
                if (element.points.isEmpty()) return false
                // Touch tolerance of 15dp / screen units, plus half width
                val tolerance = (element.maxWidth * 0.5f + 15f).coerceAtLeast(15f)
                if (element.points.size == 1) {
                    return kotlin.math.hypot(x - element.points[0].x, y - element.points[0].y) < tolerance
                }
                for (i in 0 until element.points.size - 1) {
                    val p1 = element.points[i]
                    val p2 = element.points[i + 1]
                    if (distanceToSegment(x, y, p1.x, p1.y, p2.x, p2.y) < tolerance) {
                        return true
                    }
                }
                return false
            }
            is GroupElement -> {
                return element.elements.any { isHit(it, x, y, library) }
            }
            is FillData -> {
                val rect = element.getBoundingBox(library)
                if (!rect.contains(x, y)) return false
                val region = android.graphics.Region()
                val clip = android.graphics.Region(
                    rect.left.toInt(),
                    rect.top.toInt(),
                    rect.right.toInt(),
                    rect.bottom.toInt()
                )
                region.setPath(element.path, clip)
                return region.contains(x.toInt(), y.toInt())
            }
            else -> {
                return element.getBoundingBox(library).contains(x, y)
            }
        }
    }
}

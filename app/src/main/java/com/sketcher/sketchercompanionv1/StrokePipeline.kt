package com.sketcher.sketchercompanionv1

import android.view.MotionEvent
import android.graphics.Path
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import com.sketcher.sketchercompanionv1.dto.*
import com.sketcher.sketchercompanionv1.utils.StrokeSimplifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Encapsulates the logic for processing raw touch events into vector strokes.
 * Handles:
 * - Palm Rejection (Basic) - passed down
 * - Stabilization (Lazy Stroke)
 * - Point History / Smoothing
 * - Geometric Shape Interpolation
 * - Perfect Freehand Mesh Generation (via Generator)
 */
class StrokePipeline(
    private val onUpdate: (PipelineUpdate) -> Unit,
    private val onStrokeCompleted: (VectorStroke, FillData?) -> Unit
) {

    // --- Configuration ---
    var activeStrokeType: StrokeType = StrokeType.FREEHAND
    var activeFreehandSettings: FreehandSettings = FreehandSettings()
    var activeSize: Float = 10f
    var activeColor: Int = Color.BLACK
    var activeStrokeColor: Int = Color.BLACK
    var activeFillColor: Int = Color.TRANSPARENT
    var isStrokeActive: Boolean = true
    var isFillActive: Boolean = false
    var isFlattenedOuterStrokeEnabled: Boolean = true

    var globalStabilizationLevel: Float = 0f

    var isFingerMode: Boolean = false
    var fingerOffsetX: Float = 0f
    var fingerOffsetY: Float = 50f

    private val pipelineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val consolidationMutex = Mutex()

    var canvasViewMatrix: android.graphics.Matrix = android.graphics.Matrix() // Needed for World Transform
    private val reusableInverseMatrix = android.graphics.Matrix()
    private val reusablePointBuffer = FloatArray(2)
    var currentZoom: Float = 1.0f

    // --- Object Pooling & Caching ---
    private val reusablePreviewPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private val reusableFillPath = Path().apply { fillType = Path.FillType.EVEN_ODD }
    private var liveSettingsCache = FreehandSettings()
    private var lastBaseSettings: FreehandSettings? = null

    // --- Incremental Live Preview ---
    // Instead of re-running PerfectFreehandGenerator on all N points every frame,
    // we keep a cached "committed" path for all points up to commitHead, and only
    // regenerate the last INCREMENTAL_TAIL_SIZE points as the live tip.
    private companion object {
        // How many new points before we re-bake the committed head.
        // Smaller = more frequent baking (slightly more work) but shorter constant-width tail.
        const val INCREMENTAL_TAIL_SIZE = 16
        // How many points back from commitHeadCount the tail starts.
        // Must be enough to cover the end cap of the committed polygon (a few px of overlap).
        // Larger values increase the "constant-width" overlap zone and make the seam more visible.
        const val COMMIT_OVERLAP = 5
        // How many points to bake in one chunk for the committed path
        const val BAKE_CHUNK_SIZE = 20
        // Minimum stroke points before we activate incremental mode
        const val INCREMENTAL_MIN_POINTS = BAKE_CHUNK_SIZE + INCREMENTAL_TAIL_SIZE + 10
    }
    private val committedPath = Path().apply { fillType = Path.FillType.EVEN_ODD }       // Baked head of the stroke
    private val mergedPaths = mutableListOf<Path>()
    private var commitHeadCount = 0          // How many points are baked into committedPath
    private var committedLastRadius = 0f     // Radius at the end of the last committed bake
    private val combinedPreviewPath = Path() // (unused after seam fix, kept for safety)


    private val currentStrokePoints = mutableListOf<StrokePoint>()
    val currentStrokePointsList: List<StrokePoint> get() = currentStrokePoints
    private var isDrawing: Boolean = false
    private var currentStrokeId: Int = 0

    // Incremental Cumulative Opacity Cache
    private val committedChunks = mutableListOf<Path>()
    private val committedChunkBounds = mutableListOf<RectF>()
    private val committedIntersections = mutableListOf<Path>()
    private val committedPathBounds = RectF()
    private val tempBounds1 = RectF()
    private val tempBounds2 = RectF()

    // Stabilizer State
    private var stabilizerX: Float = 0f
    private var stabilizerY: Float = 0f
    private var lastRecordedX: Float = 0f
    private var lastRecordedY: Float = 0f
    private var lastPointTimestamp: Long = 0L

    // Geometric State
    var isMultiStepInProgress: Boolean = false

    // Dependencies
    private val inputHandler = StrokeInputHandler
    private val predictor = StrokePredictor

    data class PipelineUpdate(
        val previewPath: Path?,
        val previewPoints: List<StrokePoint>?,
        val centerPoints: List<PointF>?,
        val outlinePoints: List<PointF>?,
        val lastRadius: Float,
        val fillPath: Path?,
        val fillColor: Int,
        // Separate committed head path (drawn under previewPath to avoid seam artifacts)
        val committedPreviewPath: Path? = null,
        val intersections: List<Path> = emptyList(),
        val bounds: RectF? = null,
        val isMultiStepInProgress: Boolean = false
    )

    fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        canvasViewMatrix.invert(reusableInverseMatrix)

        // 1. Process Event -> Raw Points
        val rawEventPoints = inputHandler.processEvent(event)
        val stabilizedPoints = mutableListOf<StrokePoint>()

        if (action == MotionEvent.ACTION_DOWN) {
           isDrawing = true
           if (!isMultiStepInProgress) {
               currentStrokeId++
               currentStrokePoints.clear()
               committedPath.rewind()
               committedPathBounds.setEmpty()
               commitHeadCount = 0
               committedLastRadius = 0f
               combinedPreviewPath.rewind()
               lastPointTimestamp = 0L
           }

           if (rawEventPoints.isNotEmpty()) {
               var startX = rawEventPoints.first().x
               var startY = rawEventPoints.first().y
               if (isFingerMode) {
                   startX -= fingerOffsetX
                   startY -= fingerOffsetY
               }
               stabilizerX = startX
               stabilizerY = startY

               lastRecordedX = startX
               lastRecordedY = startY
               lastPointTimestamp = rawEventPoints.first().timestamp
           }
        }

        // 2. Apply Stabilization
        val stabilization = globalStabilizationLevel.coerceIn(0f, 0.98f)
        val lagAmount = stabilization * 60f
        val factor = 1f / (1f + lagAmount)

        for (p in rawEventPoints) {
            var targetX = p.x
            var targetY = p.y
            if (isFingerMode) {
                targetX -= fingerOffsetX
                targetY -= fingerOffsetY
            }

            if (stabilization > 0f) {
                stabilizerX += (targetX - stabilizerX) * factor
                stabilizerY += (targetY - stabilizerY) * factor
            } else {
                stabilizerX = targetX
                stabilizerY = targetY
            }

            // Snap to Grid (if enabled)
            var snapX = stabilizerX
            var snapY = stabilizerY
            snapFunction?.let { snap ->
                val snapped = snap(stabilizerX, stabilizerY)
                snapX = snapped.first
                snapY = snapped.second
            }

            // Transform stabilized point to world BEFORE filtering
            reusablePointBuffer[0] = snapX
            reusablePointBuffer[1] = snapY
            reusableInverseMatrix.mapPoints(reusablePointBuffer)
            val worldP = StrokePoint(reusablePointBuffer[0], reusablePointBuffer[1], p.pressure, p.timestamp)

            // 2.5 Filter in World Space (Fixed world distance instead of screen pixels)
            val dx = worldP.x - lastRecordedX
            val dy = worldP.y - lastRecordedY
            val distSq = dx * dx + dy * dy

            // 0.5 world units (0.1mm at 5px/mm) is a good stable threshold for most zooms
            val minDistSq = 0.25f

            val isStart = (action == MotionEvent.ACTION_DOWN && stabilizedPoints.isEmpty())

            if (distSq > minDistSq || isStart) {
                var sanitizedTime = p.timestamp
                if (sanitizedTime <= lastPointTimestamp) {
                    sanitizedTime = lastPointTimestamp + 1
                }
                lastPointTimestamp = sanitizedTime

                val stabilizedWorldPoint = StrokePoint(worldP.x, worldP.y, p.pressure, sanitizedTime)
                stabilizedPoints.add(stabilizedWorldPoint)
                lastRecordedX = worldP.x
                lastRecordedY = worldP.y
            }
        }

        // 3. Add to World Points (already transformed in loop above)
        stabilizedPoints.forEach { p ->
            updateGeometricPoints(action, p)
        }

        // 4. Update & Render
        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                if (stabilizedPoints.isNotEmpty() || (action==MotionEvent.ACTION_DOWN)) {
                    updatePreview()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                finalizeStroke()
                return true
            }
        }

        return false
    }

    private fun updateGeometricPoints(action: Int, worldP: StrokePoint) {
          when (activeStrokeType) {
            StrokeType.FREEHAND, StrokeType.PEN, StrokeType.PAINT, StrokeType.PLUMA -> {
                currentStrokePoints.add(worldP)
            }
            StrokeType.LINE, StrokeType.CIRCLE -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    currentStrokePoints.clear()
                    currentStrokePoints.add(worldP)
                } else if (action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
                    if (currentStrokePoints.isNotEmpty()) {
                        val start = currentStrokePoints.first()
                        currentStrokePoints.clear()
                        currentStrokePoints.add(start)
                        currentStrokePoints.add(worldP)
                    } else {
                        currentStrokePoints.add(worldP)
                    }
                }
            }
            StrokeType.POLYLINE, StrokeType.SPLINE -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!isMultiStepInProgress) {
                        currentStrokePoints.clear()
                        isMultiStepInProgress = true
                        currentStrokePoints.add(worldP)
                        currentStrokePoints.add(worldP)
                    } else {
                        // Check if we are snapping back to the start point to close/finish the shape!
                        val firstPt = currentStrokePoints.firstOrNull()
                        if (firstPt != null && currentStrokePoints.size > 2) {
                            val dx = worldP.x - firstPt.x
                            val dy = worldP.y - firstPt.y
                            val distSq = dx * dx + dy * dy
                            val threshold = 12f / currentZoom
                            if (distSq < threshold * threshold) {
                                // Update the active preview point to close the shape
                                currentStrokePoints[currentStrokePoints.size - 1] = firstPt
                                forceFinishGeometric()
                                return
                            }
                        }
                        currentStrokePoints.add(worldP)
                    }
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (currentStrokePoints.isNotEmpty()) {
                        currentStrokePoints[currentStrokePoints.size - 1] = worldP
                    }
                }
            }
            StrokeType.ARC, StrokeType.ELLIPSE -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!isMultiStepInProgress || currentStrokePoints.size >= 3) {
                        currentStrokePoints.clear()
                        isMultiStepInProgress = true
                        currentStrokePoints.add(worldP)
                        currentStrokePoints.add(worldP)
                    } else {
                        currentStrokePoints.add(worldP)
                    }
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (currentStrokePoints.isNotEmpty()) {
                        currentStrokePoints[currentStrokePoints.size - 1] = worldP
                    }
                }
            }
            StrokeType.BEZIER -> {
                if (action == MotionEvent.ACTION_DOWN) {
                    if (!isMultiStepInProgress) {
                        currentStrokePoints.clear()
                        isMultiStepInProgress = true
                        currentStrokePoints.add(worldP)
                        currentStrokePoints.add(worldP)
                    } else {
                        val firstPt = currentStrokePoints.firstOrNull()
                        if (firstPt != null && currentStrokePoints.size > 2) {
                            val dx = worldP.x - firstPt.x
                            val dy = worldP.y - firstPt.y
                            val distSq = dx * dx + dy * dy
                            val threshold = 12f / currentZoom
                            if (distSq < threshold * threshold) {
                                val outTan = currentStrokePoints[1]
                                val inTanX = 2 * firstPt.x - outTan.x
                                val inTanY = 2 * firstPt.y - outTan.y
                                val inTan = StrokePoint(inTanX, inTanY, firstPt.pressure, firstPt.timestamp)
                                currentStrokePoints.add(inTan)
                                currentStrokePoints.add(firstPt)
                                forceFinishGeometric()
                                return
                            }
                        }
                        currentStrokePoints.add(worldP)
                        currentStrokePoints.add(worldP)
                        currentStrokePoints.add(worldP)
                    }
                } else if (action == MotionEvent.ACTION_MOVE) {
                    if (currentStrokePoints.size == 2) {
                        currentStrokePoints[1] = worldP
                    } else if (currentStrokePoints.size >= 5) {
                        val activeNodeIndex = (currentStrokePoints.size - 2) / 3
                        val anchorIdx = activeNodeIndex * 3
                        if (anchorIdx < currentStrokePoints.size) {
                            val anchor = currentStrokePoints[anchorIdx]
                            val dx = worldP.x - anchor.x
                            val dy = worldP.y - anchor.y
                            val outIdx = anchorIdx + 1
                            if (outIdx < currentStrokePoints.size) {
                                currentStrokePoints[outIdx] = StrokePoint(anchor.x + dx, anchor.y + dy, worldP.pressure, worldP.timestamp)
                            }
                            val inIdx = anchorIdx - 1
                            if (inIdx >= 0) {
                                currentStrokePoints[inIdx] = StrokePoint(anchor.x - dx, anchor.y - dy, worldP.pressure, worldP.timestamp)
                            }
                        }
                    }
                }
            }
        }
    }



    private fun updatePreview() {
        val livePoints: List<StrokePoint> = getInterpolatedPoints(isFinal = false)

        if (livePoints.isEmpty()) return

        val isCad = activeStrokeType != StrokeType.FREEHAND && activeStrokeType != StrokeType.PAINT && activeStrokeType != StrokeType.PLUMA
        if (isCad) {
            val centerline = com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(activeStrokeType, livePoints)
            val totalBounds = RectF()
            centerline.computeBounds(totalBounds, true)
            val pad = (activeSize * 2f).coerceAtLeast(10f)
            totalBounds.inset(-pad, -pad)

            onUpdate(PipelineUpdate(
                previewPath = centerline,
                previewPoints = livePoints,
                centerPoints = livePoints.map { android.graphics.PointF(it.x, it.y) },
                outlinePoints = emptyList(),
                lastRadius = activeSize / 2f,
                fillPath = if (isFillActive) centerline else null,
                fillColor = if (isFillActive) activeFillColor else 0,
                committedPreviewPath = null,
                intersections = emptyList(),
                bounds = totalBounds,
                isMultiStepInProgress = isMultiStepInProgress
            ))
            return
        }

        // Update settings cache if base changed
        if (activeFreehandSettings !== lastBaseSettings) {
            liveSettingsCache = activeFreehandSettings
            lastBaseSettings = activeFreehandSettings
            // Settings changed: invalidate committed cache
            committedPath.rewind()
            committedPathBounds.setEmpty()
            commitHeadCount = 0
            committedChunks.clear()
            committedChunkBounds.clear()
            committedIntersections.clear()
        }

        val settings = liveSettingsCache.copy(size = activeSize, isComplete = false, streamline = globalStabilizationLevel * 0.8f)

        // 2. Incremental preview for FREEHAND only when stroke is long enough
        val result: PerfectFreehandGenerator.FreehandResult
        var committedPathToSend: Path? = if (activeStrokeType == StrokeType.PAINT) committedPath else null

        if ((activeStrokeType == StrokeType.FREEHAND || activeStrokeType == StrokeType.PAINT || activeStrokeType == StrokeType.PLUMA) &&
            livePoints.size >= INCREMENTAL_MIN_POINTS) {

            // -- Bake head if we have enough new points since last commit --
            val uncommitted = livePoints.size - commitHeadCount
            if (uncommitted >= BAKE_CHUNK_SIZE + INCREMENTAL_TAIL_SIZE) {
                // New commit boundary: bake exactly BAKE_CHUNK_SIZE points
                val newHeadEnd = commitHeadCount + BAKE_CHUNK_SIZE
                
                // Segment points: from commitHeadCount to newHeadEnd
                val segmentPoints = livePoints.subList(commitHeadCount, newHeadEnd + 1)
                val segmentPath = Path()
                PerfectFreehandGenerator.generate(
                    segmentPoints,
                    settings.copy(isComplete = false),
                    currentZoom,
                    segmentPath
                )
                
                val segmentBounds = RectF()
                segmentPath.computeBounds(segmentBounds, true)
                
                if (activeFreehandSettings.isCumulativeOpacity && activeStrokeType == StrokeType.FREEHAND) {
                    val numPrev = committedChunks.size
                    for (i in 0 until numPrev - 2) { // ignore the last two chunks (adjacent + near)
                        val prev = committedChunks[i]
                        val prevBounds = committedChunkBounds[i]
                        if (RectF.intersects(segmentBounds, prevBounds)) {
                            val intersect = Path()
                            if (intersect.op(segmentPath, prev, Path.Op.INTERSECT)) {
                                if (!intersect.isEmpty) {
                                    committedIntersections.add(intersect)
                                }
                            }
                        }
                    }
                }
                
                committedChunks.add(segmentPath)
                committedChunkBounds.add(segmentBounds)
                
                val headPoints = livePoints.subList(0, newHeadEnd)
                val headResult = PerfectFreehandGenerator.generate(
                    headPoints,
                    settings.copy(isComplete = false),
                    currentZoom,
                    committedPath // rewind+fill in-place
                )
                for (p in mergedPaths) {
                    committedPath.addPath(p)
                }
                committedPath.computeBounds(committedPathBounds, true)
                commitHeadCount = newHeadEnd
                // Capture the radius the committed head ended at — the tail must start at this width
                committedLastRadius = headResult.lastRadius
            }

            // -- Generate live tail with minimal overlap into the committed region --
            // COMMIT_OVERLAP: just enough points back to cover the committed end cap visually.
            // Keeping overlap small minimizes the zone where constant-width tail is visible
            // over the varying-width committed head, eliminating the residual artifact.
            // capStart = false → no start cap (avoids double-cap with committed end cap)
            // taperStart = 0f → no taper at tail start (avoids width mismatch)
            // thinning/velocityThinning = 0 → constant width = committedLastRadius
            val tailStart = (commitHeadCount - COMMIT_OVERLAP).coerceAtLeast(0)
            val tailPoints = livePoints.subList(tailStart, livePoints.size)
            val tailRadius = if (committedLastRadius > 0f) committedLastRadius else settings.size / 2f
            val tailSettings = settings.copy(
                capStart = false,
                taperStart = 0f,
                thinning = 0f,
                velocityThinning = 0f,
                simulatePressure = false,
                size = tailRadius * 2f
            )
            result = PerfectFreehandGenerator.generate(
                tailPoints,
                tailSettings,
                currentZoom,
                reusablePreviewPath
            )

            // Pass the committed path separately so the canvas draws it FIRST (underneath)
            committedPathToSend = committedPath

        } else {
            // Full regeneration for short strokes or geometric types
            result = PerfectFreehandGenerator.generate(
                livePoints,
                settings,
                currentZoom,
                reusablePreviewPath
            )
        }

        // 3. Fill Preview
        var fillPath: Path? = null
        if (isFillActive && livePoints.size >= 3 && activeStrokeType != StrokeType.PAINT && activeStrokeType != StrokeType.PLUMA) {
            fillPath = reusableFillPath.apply { rewind() }
            fillPath.moveTo(livePoints[0].x, livePoints[0].y)
            for (i in 1 until livePoints.size) {
                fillPath.lineTo(livePoints[i].x, livePoints[i].y)
            }
            fillPath.close()
        }

        val liveIntersections = mutableListOf<Path>()
        if (activeFreehandSettings.isCumulativeOpacity && activeStrokeType == StrokeType.FREEHAND) {
            if (livePoints.size < INCREMENTAL_MIN_POINTS) {
                // Short stroke: run the full check (fast because points list is short)
                liveIntersections.addAll(PerfectFreehandGenerator.generateCumulativeChunks(livePoints, settings, currentZoom))
            } else {
                // Long stroke: use cached committed intersections
                liveIntersections.addAll(committedIntersections)
                
                // Intersect tail path with committed chunks
                val tailPath = result.path
                tailPath.computeBounds(tempBounds1, true)
                val numPrev = committedChunks.size
                for (i in 0 until numPrev - 2) { // ignore the last two committed chunks (adjacent + near)
                    val prev = committedChunks[i]
                    val prevBounds = committedChunkBounds[i]
                    if (RectF.intersects(tempBounds1, prevBounds)) {
                        val intersect = Path()
                        if (intersect.op(tailPath, prev, Path.Op.INTERSECT)) {
                            if (!intersect.isEmpty) {
                                liveIntersections.add(intersect)
                            }
                        }
                    }
                }
            }
        }

        val totalBounds = RectF()
        var hasBounds = false
        if (committedPathToSend != null && !committedPathBounds.isEmpty) {
            totalBounds.set(committedPathBounds)
            hasBounds = true
        }
        val tailPath = result.path
        tailPath.computeBounds(tempBounds1, true)
        if (!tempBounds1.isEmpty) {
            if (hasBounds) {
                totalBounds.union(tempBounds1)
            } else {
                totalBounds.set(tempBounds1)
                hasBounds = true
            }
        } else if (livePoints.isNotEmpty()) {
            val firstPoint = livePoints.first()
            val pointRect = RectF(firstPoint.x, firstPoint.y, firstPoint.x, firstPoint.y)
            if (hasBounds) {
                totalBounds.union(pointRect)
            } else {
                totalBounds.set(pointRect)
                hasBounds = true
            }
        }
        if (hasBounds) {
            val pad = (activeSize * 2f).coerceAtLeast(10f)
            totalBounds.inset(-pad, -pad)
        }

        onUpdate(PipelineUpdate(
            previewPath = result.path,
            previewPoints = livePoints,
            centerPoints = result.center,
            outlinePoints = result.left + result.right,
            lastRadius = result.lastRadius,
            fillPath = fillPath,
            fillColor = if (isFillActive) activeFillColor else 0,
            committedPreviewPath = committedPathToSend,
            intersections = liveIntersections,
            bounds = if (hasBounds) totalBounds else null,
            isMultiStepInProgress = isMultiStepInProgress
        ))
    }

    private fun finalizeStroke() {
        isDrawing = false
        if (currentStrokePoints.isEmpty()) {
            reset()
            return
        }

        // Handle Multi-step continuation
        if (activeStrokeType == StrokeType.POLYLINE || activeStrokeType == StrokeType.SPLINE || activeStrokeType == StrokeType.BEZIER ||
            ((activeStrokeType == StrokeType.ARC || activeStrokeType == StrokeType.ELLIPSE) && currentStrokePoints.size < 3)) {
             updatePreview()
             isMultiStepInProgress = true
             return
        }

        // Interpolate Final
        val finalPointsRaw = getInterpolatedPoints(isFinal = true)

        if (finalPointsRaw.isEmpty()) {
             reset()
             return
        }

        val isCad = activeStrokeType != StrokeType.FREEHAND && activeStrokeType != StrokeType.PAINT && activeStrokeType != StrokeType.PLUMA
        if (isCad) {
            val centerline = com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(activeStrokeType, finalPointsRaw)
            val stroke = VectorStroke(
                points = finalPointsRaw,
                strokeColor = activeStrokeColor,
                fillColor = activeFillColor,
                isStrokeEnabled = isStrokeActive,
                isFillEnabled = isFillActive,
                maxWidth = activeSize,
                path = centerline,
                fillPath = if (isFillActive) centerline else null,
                brushType = "CAD",
                strokeType = activeStrokeType,
                isCadGeometry = true
            )
            val fill = if (isFillActive) FillData(centerline, activeFillColor) else null
            onStrokeCompleted(stroke, fill)
            reset()
            return
        }

        // Pre-simulate pressure on finalPointsRaw if simulatePressure is true
        val finalPointsWithPressure = if (activeFreehandSettings.simulatePressure && activeFreehandSettings.thinning > 0f) {
            simulatePressureOnPoints(finalPointsRaw, activeSize)
        } else {
            finalPointsRaw
        }

        // Simplify
        val tolerance = activeFreehandSettings.simplificationTolerance.coerceAtLeast(0.01f)
        val isSimplified = activeFreehandSettings.isSimplificationEnabled

        val finalPoints = if (isSimplified && finalPointsWithPressure.size > 2) {
             StrokeSimplifier.simplify(finalPointsWithPressure, tolerance, activeSize * 2.0f)
        } else {
             finalPointsWithPressure
        }

        // Generate High Fidelity Path (pass simulatePressure = false since we pre-simulated it!)
        val genResult = PerfectFreehandGenerator.generate(
            finalPoints, 
            activeFreehandSettings.copy(size = activeSize, isComplete = true, simulatePressure = false, streamline = globalStabilizationLevel * 0.8f), 
            currentZoom
        )
        val rawPath = Path(genResult.path)

        if (activeStrokeType == StrokeType.PAINT) {
            val combinedFinal = Path(rawPath)
            if (!committedPath.isEmpty) {
                combinedFinal.addPath(committedPath)
            }
            val path = flattenOuterStroke(combinedFinal)
            
            val step = (8f / currentZoom).coerceAtLeast(1.0f)
            val epsilon = (1.5f / currentZoom).coerceAtLeast(0.2f)
            val outlinePointsPointF = com.sketcher.sketchercompanionv1.utils.GeometryUtils.flattenPath(path, step = step)
            val outlineStrokePoints = outlinePointsPointF.map { pt -> StrokePoint(pt.x, pt.y, 0.5f) }
            val simplifiedPoints = if (outlineStrokePoints.size > 2) {
                com.sketcher.sketchercompanionv1.utils.StrokeSimplifier.simplify(outlineStrokePoints, epsilon, 20f)
            } else {
                outlineStrokePoints
            }

            val stroke = VectorStroke(
                points = simplifiedPoints,
                strokeColor = activeStrokeColor,
                fillColor = activeFillColor,
                isStrokeEnabled = isStrokeActive,
                isFillEnabled = isFillActive,
                maxWidth = activeSize,
                path = path,
                fillPath = if (isFillActive) path else null,
                brushType = "PAINT",
                strokeType = StrokeType.PAINT,
                isFlattened = false,
                paintOutlineWidth = activeFreehandSettings.paintOutlineWidth
            )

            onStrokeCompleted(stroke, null)
            reset()
            return
        }

        if (isFlattenedOuterStrokeEnabled && activeStrokeType == StrokeType.FREEHAND && !activeFreehandSettings.isCumulativeOpacity) {
            val activeStrokeTypeSnap = activeStrokeType
            val activeStrokeColorSnap = activeStrokeColor
            val activeFillColorSnap = activeFillColor
            val isStrokeActiveSnap = isStrokeActive
            val isFillActiveSnap = isFillActive
            val activeSizeSnap = activeSize
            val genResultLeftSnap = genResult.left.toList()
            val genResultRightSnap = genResult.right.toList()
            val finalPointsSnap = finalPoints.toList()
            val strokeIdSnap = currentStrokeId

            // Generate temporary fill path for preview during async processing (connect center points)
            var fillPath: Path? = null
            if (isFillActiveSnap && finalPointsSnap.size >= 3) {
                fillPath = Path().apply {
                    moveTo(finalPointsSnap[0].x, finalPointsSnap[0].y)
                    for (i in 1 until finalPointsSnap.size) {
                        lineTo(finalPointsSnap[i].x, finalPointsSnap[i].y)
                    }
                    close()
                }
            }

            val finalBounds = RectF()
            rawPath.computeBounds(finalBounds, true)
            val pad = (activeSizeSnap * 2f).coerceAtLeast(10f)
            finalBounds.inset(-pad, -pad)

            // Immediately show the finalized stroke preview (with correct final path/fill)
            // to avoid any blinking or blank frames while processing
            onUpdate(PipelineUpdate(
                previewPath = Path(rawPath),
                previewPoints = finalPointsSnap,
                centerPoints = genResult.center,
                outlinePoints = genResult.left + genResult.right,
                lastRadius = genResult.lastRadius,
                fillPath = fillPath,
                fillColor = if (isFillActiveSnap) activeFillColorSnap else 0,
                committedPreviewPath = null,
                bounds = finalBounds,
                isMultiStepInProgress = isMultiStepInProgress
            ))

            pipelineScope.launch {
                consolidationMutex.withLock {
                    val path = flattenOuterStroke(rawPath)
                    
                    // Committed fill path should connect center points (finalPointsSnap) instead of perimeter
                    var fPath: Path? = null
                    if (isFillActiveSnap && finalPointsSnap.size >= 3) {
                         fPath = Path()
                         fPath.moveTo(finalPointsSnap[0].x, finalPointsSnap[0].y)
                         for (i in 1 until finalPointsSnap.size) {
                             fPath.lineTo(finalPointsSnap[i].x, finalPointsSnap[i].y)
                         }
                         fPath.close()
                    }

                    val stroke = VectorStroke(
                        points = finalPointsSnap,
                        strokeColor = activeStrokeColorSnap,
                        fillColor = activeFillColorSnap,
                        isStrokeEnabled = isStrokeActiveSnap,
                        isFillEnabled = false,
                        maxWidth = activeSizeSnap,
                        path = path,
                        fillPath = fPath,
                        brushType = "FREEHAND",
                        strokeType = activeStrokeTypeSnap,
                        leftPoints = genResultLeftSnap,
                        rightPoints = genResultRightSnap,
                        isFlattened = true
                    )

                    var fill: FillData? = null
                    if (isFillActiveSnap && finalPointsSnap.size >= 3) {
                         val fillPathToUse = fPath ?: Path().apply {
                             moveTo(finalPointsSnap[0].x, finalPointsSnap[0].y)
                             for (i in 1 until finalPointsSnap.size) {
                                 this.lineTo(finalPointsSnap[i].x, finalPointsSnap[i].y)
                             }
                             this.close()
                         }
                         fill = FillData(fillPathToUse, activeFillColorSnap)
                    }

                    withContext(Dispatchers.Main) {
                        onStrokeCompleted(stroke, fill)
                        // Only reset if no new stroke drawing session has started
                        if (currentStrokeId == strokeIdSnap) {
                            reset()
                        }
                    }
                }
            }
        } else {
            val (path, strokePoints) = rawPath to finalPoints
            var fPath: Path? = null
            if (isFillActive && strokePoints.size >= 3) {
                 fPath = Path()
                 fPath.moveTo(strokePoints[0].x, strokePoints[0].y)
                 for (i in 1 until strokePoints.size) {
                     fPath.lineTo(strokePoints[i].x, strokePoints[i].y)
                 }
                 fPath.close()
            }

            val chunkPaths = if (activeFreehandSettings.isCumulativeOpacity && activeStrokeType == StrokeType.FREEHAND) {
                PerfectFreehandGenerator.generateCumulativeChunks(
                    finalPoints,
                    activeFreehandSettings.copy(size = activeSize, isComplete = true, simulatePressure = false, streamline = globalStabilizationLevel * 0.8f),
                    currentZoom
                )
            } else {
                emptyList()
            }

            val stroke = VectorStroke(
                points = strokePoints,
                strokeColor = activeStrokeColor,
                fillColor = activeFillColor,
                isStrokeEnabled = isStrokeActive,
                isFillEnabled = false,
                maxWidth = activeSize,
                path = path,
                fillPath = fPath,
                brushType = "FREEHAND",
                strokeType = activeStrokeType,
                leftPoints = genResult.left,
                rightPoints = genResult.right,
                paths = chunkPaths
            )

            var fill: FillData? = null
            if (isFillActive && strokePoints.size >= 3) {
                 val fillPathToUse = fPath ?: Path().apply {
                     moveTo(strokePoints[0].x, strokePoints[0].y)
                     for (i in 1 until strokePoints.size) {
                         lineTo(strokePoints[i].x, strokePoints[i].y)
                     }
                     close()
                 }
                 fill = FillData(fillPathToUse, activeFillColor)
            }

            onStrokeCompleted(stroke, fill)
            reset()
        }
    }

    fun forceFinishGeometric() {
        if (!isMultiStepInProgress || currentStrokePoints.isEmpty()) return
        
        val finalPointsRaw = getInterpolatedPoints(isFinal = true)
        if (finalPointsRaw.isEmpty()) {
            reset()
            return
        }
        
        val isCad = activeStrokeType != StrokeType.FREEHAND && activeStrokeType != StrokeType.PAINT && activeStrokeType != StrokeType.PLUMA
        if (isCad) {
            val centerline = com.sketcher.sketchercompanionv1.utils.GeometryUtils.buildCenterlinePath(activeStrokeType, finalPointsRaw)
            val stroke = VectorStroke(
                points = finalPointsRaw,
                strokeColor = activeStrokeColor,
                fillColor = activeFillColor,
                isStrokeEnabled = isStrokeActive,
                isFillEnabled = isFillActive,
                maxWidth = activeSize,
                path = centerline,
                fillPath = if (isFillActive) centerline else null,
                brushType = "CAD",
                strokeType = activeStrokeType,
                isCadGeometry = true
            )
            val fill = if (isFillActive) FillData(centerline, activeFillColor) else null
            onStrokeCompleted(stroke, fill)
            reset()
            return
        }
        
        // Pre-simulate pressure on finalPointsRaw if simulatePressure is true
        val finalPointsWithPressure = if (activeFreehandSettings.simulatePressure && activeFreehandSettings.thinning > 0f) {
            simulatePressureOnPoints(finalPointsRaw, activeSize)
        } else {
            finalPointsRaw
        }

        // Simplify if enabled
        val tolerance = activeFreehandSettings.simplificationTolerance.coerceAtLeast(0.01f)
        val isSimplified = activeFreehandSettings.isSimplificationEnabled

        val finalPoints = if (isSimplified && finalPointsWithPressure.size > 2) {
             StrokeSimplifier.simplify(finalPointsWithPressure, tolerance, activeSize * 2.0f)
        } else {
             finalPointsWithPressure
        }

        // Duplicate Logic (pass simulatePressure = false since we pre-simulated it!)
        val genResult = PerfectFreehandGenerator.generate(
             finalPoints, 
             activeFreehandSettings.copy(size = activeSize, isComplete = true, simulatePressure = false, streamline = globalStabilizationLevel * 0.8f), 
             currentZoom
        )
        val path = Path(genResult.path)
         
        var fPath: Path? = null
        if (isFillActive && finalPoints.size >= 3) {
             fPath = Path()
             fPath.moveTo(finalPoints[0].x, finalPoints[0].y)
             for (i in 1 until finalPoints.size) {
                 fPath.lineTo(finalPoints[i].x, finalPoints[i].y)
             }
             fPath.close()
        }

        val chunkPaths = if (activeFreehandSettings.isCumulativeOpacity && activeStrokeType == StrokeType.FREEHAND) {
            PerfectFreehandGenerator.generateCumulativeChunks(
                finalPoints,
                activeFreehandSettings.copy(size = activeSize, isComplete = true, simulatePressure = false, streamline = globalStabilizationLevel * 0.8f),
                currentZoom
            )
        } else {
            emptyList()
        }

        val stroke = VectorStroke(
             points = finalPoints,
             strokeColor = activeStrokeColor,
             fillColor = activeFillColor,
             isStrokeEnabled = isStrokeActive,
             isFillEnabled = false,
             maxWidth = activeSize,
             path = path,
             fillPath = fPath,
             brushType = "FREEHAND",
             strokeType = activeStrokeType,
             leftPoints = genResult.left,
             rightPoints = genResult.right,
             paths = chunkPaths
        )

        var fill: FillData? = null
        if (isFillActive && finalPoints.size >= 3) {
             val fillPathToUse = fPath ?: Path().apply {
                 moveTo(finalPoints[0].x, finalPoints[0].y)
                 for (i in 1 until finalPoints.size) {
                     lineTo(finalPoints[i].x, finalPoints[i].y)
                 }
                 close()
             }
             fill = FillData(fillPathToUse, activeFillColor)
        }

        onStrokeCompleted(stroke, fill)
        reset()
    }

    fun undoLastPoint() {
        if (currentStrokePoints.isEmpty()) return
        
        when (activeStrokeType) {
            StrokeType.FREEHAND, StrokeType.PEN, StrokeType.PAINT, StrokeType.PLUMA -> {}
            StrokeType.LINE, StrokeType.CIRCLE -> {
                reset()
            }
            StrokeType.POLYLINE, StrokeType.SPLINE -> {
                if (currentStrokePoints.size <= 2) {
                    reset()
                } else {
                    currentStrokePoints.removeAt(currentStrokePoints.size - 1)
                    updatePreview()
                }
            }
            StrokeType.BEZIER -> {
                if (currentStrokePoints.size <= 2) {
                    reset()
                } else {
                    currentStrokePoints.removeAt(currentStrokePoints.size - 1)
                    currentStrokePoints.removeAt(currentStrokePoints.size - 1)
                    currentStrokePoints.removeAt(currentStrokePoints.size - 1)
                    updatePreview()
                }
            }
            StrokeType.ARC, StrokeType.ELLIPSE -> {
                if (currentStrokePoints.size <= 2) {
                    reset()
                } else {
                    currentStrokePoints.removeAt(currentStrokePoints.size - 1)
                    updatePreview()
                }
            }
        }
    }

    fun mergePath(externalPath: Path) {
        mergedPaths.add(Path(externalPath))
        committedPath.addPath(externalPath)
        committedPathBounds.setEmpty()
        committedPath.computeBounds(committedPathBounds, true)
    }

    fun triggerUpdatePreview() {
        updatePreview()
    }

    fun reset() {
        currentStrokePoints.clear()
        committedPath.rewind()
        committedPathBounds.setEmpty()
        commitHeadCount = 0
        committedLastRadius = 0f
        combinedPreviewPath.rewind()
        committedChunks.clear()
        committedChunkBounds.clear()
        committedIntersections.clear()
        mergedPaths.clear()
        isDrawing = false
        isMultiStepInProgress = false
        onUpdate(PipelineUpdate(null, null, null, null, 0f, null, 0))
    }

    private fun getInterpolatedPoints(isFinal: Boolean): List<StrokePoint> {
        return when (activeStrokeType) {
            StrokeType.FREEHAND, StrokeType.PEN, StrokeType.PAINT, StrokeType.PLUMA -> {
                if (!isFinal) {
                    val latencyMs = activeFreehandSettings.predictionLatency.toLong()
                    val predictedPt = predictor.getPredictedPoint(
                        points = currentStrokePoints,
                        predictionLatencyMillis = latencyMs,
                        currentZoom = currentZoom
                    )
                    if (predictedPt != null) currentStrokePoints + predictedPt else currentStrokePoints.toList()
                } else {
                    currentStrokePoints.toList()
                }
            }
            else -> currentStrokePoints.toList()
        }
    }
    
    // Interpolation Helpers (Copied from View)
    private fun interpolateLine(p1: StrokePoint, p2: StrokePoint): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val dist = kotlin.math.sqrt(dx * dx + dy * dy)
        if (dist < 1f) return listOf(p1, p2)
        
        val steps = (dist / 2f).toInt().coerceIn(2, 500)
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = p1.x + dx * t
            val y = p1.y + dy * t
            val pressure = p1.pressure + (p2.pressure - p1.pressure) * t
            val time = p1.timestamp + ((p2.timestamp - p1.timestamp) * t).toLong()
            points.add(StrokePoint(x, y, pressure, time))
        }
        return points
    }

    private fun interpolateCircle(center: StrokePoint, edge: StrokePoint): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val dx = edge.x - center.x
        val dy = edge.y - center.y
        val radius = kotlin.math.sqrt(dx * dx + dy * dy)
        if (radius < 1f) return listOf(center, edge)
        
        val circumference = 2 * kotlin.math.PI * radius
        val steps = (circumference / 5f).toInt().coerceIn(12, 1000)
        
        for (i in 0..steps) {
            val angle = (2 * kotlin.math.PI * i) / steps
            val x = center.x + (radius * kotlin.math.cos(angle)).toFloat()
            val y = center.y + (radius * kotlin.math.sin(angle)).toFloat()
            val pressure = edge.pressure
            val time = edge.timestamp 
            points.add(StrokePoint(x, y, pressure, time))
        }
        return points
    }

    private fun interpolatePolyline(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 2) return points
        val result = mutableListOf<StrokePoint>()
        for (i in 0 until points.size - 1) {
            val segment = interpolateLine(points[i], points[i+1])
            if (i > 0) result.addAll(segment.drop(1))
            else result.addAll(segment)
        }
        return result
    }

    private fun interpolateArc(p1: StrokePoint, p2: StrokePoint, p3: StrokePoint): List<StrokePoint> {
        val x1 = p1.x
        val y1 = p1.y
        val x2 = p2.x
        val y2 = p2.y
        val x3 = p3.x
        val y3 = p3.y

        val D = 2 * (x1 * (y2 - y3) + x2 * (y3 - y1) + x3 * (y1 - y2))
        if (kotlin.math.abs(D) < 0.001f) {
            return interpolatePolyline(listOf(p1, p2, p3))
        }

        val Ux = ((x1 * x1 + y1 * y1) * (y2 - y3) + (x2 * x2 + y2 * y2) * (y3 - y1) + (x3 * x3 + y3 * y3) * (y1 - y2)) / D
        val Uy = ((x1 * x1 + y1 * y1) * (x3 - x2) + (x2 * x2 + y2 * y2) * (x1 - x3) + (x3 * x3 + y3 * y3) * (x2 - x1)) / D
        
        val dx1 = x1 - Ux
        val dy1 = y1 - Uy
        val radius = kotlin.math.sqrt(dx1 * dx1 + dy1 * dy1)

        val angle1 = kotlin.math.atan2(y1 - Uy, x1 - Ux).toDouble()
        val angle2 = kotlin.math.atan2(y2 - Uy, x2 - Ux).toDouble()
        val angle3 = kotlin.math.atan2(y3 - Uy, x3 - Ux).toDouble()

        var startAngle = angle1
        var midAngle = angle2
        var endAngle = angle3

        var sweep = endAngle - startAngle
        while (sweep < -kotlin.math.PI) sweep += 2 * kotlin.math.PI
        while (sweep > kotlin.math.PI) sweep -= 2 * kotlin.math.PI
        
        var diffMid = midAngle - startAngle
        while (diffMid < -kotlin.math.PI) diffMid += 2 * kotlin.math.PI
        while (diffMid > kotlin.math.PI) diffMid -= 2 * kotlin.math.PI
        
        if (kotlin.math.sign(diffMid) != kotlin.math.sign(sweep)) {
            sweep = if (sweep > 0) sweep - 2 * kotlin.math.PI else sweep + 2 * kotlin.math.PI
        }

        val points = mutableListOf<StrokePoint>()
        val arcLength = kotlin.math.abs(sweep) * radius
        val steps = (arcLength / 2f).toInt().coerceIn(12, 1000)

        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val angle = startAngle + sweep * t
            val x = Ux + (radius * kotlin.math.cos(angle)).toFloat()
            val y = Uy + (radius * kotlin.math.sin(angle)).toFloat()
            
            val pressure = if (t < 0.5f) {
                p1.pressure + (p2.pressure - p1.pressure) * (t * 2)
            } else {
                p2.pressure + (p3.pressure - p2.pressure) * ((t - 0.5f) * 2)
            }
            val time = if (t < 0.5f) {
                p1.timestamp + ((p2.timestamp - p1.timestamp) * (t * 2)).toLong()
            } else {
                p2.timestamp + ((p3.timestamp - p2.timestamp) * ((t - 0.5f) * 2)).toLong()
            }
            
            points.add(StrokePoint(x, y, pressure, time.toLong()))
        }
        return points
    }

    // --- ASYNC OPERATIONS ---
    
    /**
     * Prepares for Fill Tool. 
     * This will be called from ViewModel inside a coroutine.
     */
    suspend fun performFloodFill(
        x: Float, 
        y: Float, 
        targetColor: Int, 
        layers: List<Layer>,
        tolerance: Float
    ): FillData? {
        // TODO: Implement Flood Fill Algorithm on Dispatchers.Default
        return kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
             // 1. Render Scene to Bitmap (if needed) or use Vector clipping
             // 2. Compute fill path
             null // Return null for now until implemented
        }
    }

    private fun flattenOuterStroke(outlinePath: Path): Path {
        // Union to collapse self-intersections into clean filled blob
        val cleanPath = Path()
        cleanPath.op(outlinePath, outlinePath, Path.Op.UNION)
        return cleanPath
    }

    private fun simulatePressureOnPoints(points: List<StrokePoint>, size: Float): List<StrokePoint> {
        if (points.isEmpty()) return points
        val result = ArrayList<StrokePoint>(points.size)
        
        // Simulating the exact logic from PerfectFreehandGenerator / PerfectFreehandUtils
        var prevPressure = if (points[0].pressure >= 0) points[0].pressure else 0.5f
        
        // Pre-run first 10 points for initial pressure average to match computeInitialPressure
        val count = kotlin.math.min(10, points.size)
        var initialAcc = prevPressure
        for (i in 0 until count) {
            val curr = points[i]
            val d = if (i == 0) 0f else {
                val dx = curr.x - points[i-1].x
                val dy = curr.y - points[i-1].y
                kotlin.math.hypot(dx, dy)
            }
            val sp = kotlin.math.min(1f, d / size)
            val rp = kotlin.math.min(1f, 1f - sp)
            val simulated = kotlin.math.min(1f, initialAcc + (rp - initialAcc) * (sp * PerfectFreehandUtils.RATE_OF_PRESSURE_CHANGE))
            initialAcc = (initialAcc + simulated) / 2f
        }
        
        var currentPrevPressure = initialAcc
        for (i in points.indices) {
            val curr = points[i]
            val d = if (i == 0) 0f else {
                val dx = curr.x - points[i-1].x
                val dy = curr.y - points[i-1].y
                kotlin.math.hypot(dx, dy)
            }
            val sp = kotlin.math.min(1f, d / size)
            val rp = kotlin.math.min(1f, 1f - sp)
            val pressure = kotlin.math.min(1f, currentPrevPressure + (rp - currentPrevPressure) * (sp * PerfectFreehandUtils.RATE_OF_PRESSURE_CHANGE))
            result.add(curr.copy(pressure = pressure))
            currentPrevPressure = pressure
        }
        
        return result
    }

    // Callbacks
    var snapFunction: ((Float, Float) -> Pair<Float, Float>)? = null
}

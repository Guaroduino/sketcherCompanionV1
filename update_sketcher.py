
import os

file_path = r"c:\Users\corad\OneDrive\Documentos\GitHub\sketcherCompanionV1\app\src\main\java\com\skecher\SketcherSurface.kt"

with open(file_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Indices are 0-based.
# We want to replace lines 836 to 1213 (1-based).
# Line 836 is index 835.
# Line 1213 is index 1212.
# We want to KEEP lines[:835] (lines 1..835)
# We want to KEEP lines[1213:] (lines 1214..End)
# So we remove indices 835 to 1212.

start_idx = 835
end_idx = 1213 # This is the index of line 1214, so slice starts here.

new_logic = r"""
                    if (state.toolType == ToolType.ERASER) {
                        // OBJECT ERASER LOGIC (Robust activePointerId)
                        if (action == MotionEvent.ACTION_DOWN) {
                             activePointerId = event.getPointerId(0)
                        }
                        
                        // Only process if this is the active pointer
                        val pointerIndex = event.findPointerIndex(activePointerId)
                        if (pointerIndex != -1) {
                             if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                                val touchPts = floatArrayOf(event.getX(pointerIndex), event.getY(pointerIndex))
                                inverseMatrix.mapPoints(touchPts)
                                val worldX = touchPts[0]
                                val worldY = touchPts[1]
                                
                                val erased = sketchViewModel.erase(worldX, worldY, state.size)
                                
                                if (erased) {
                                     canvasView.setLayers(sketchViewModel.layers)
                                     canvasView.redrawAllCache()
                                }
                             }
                        }
                        
                        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
                            if (event.getPointerId(event.actionIndex) == activePointerId) {
                                activePointerId = -1
                            }
                        }
                        if (action == MotionEvent.ACTION_CANCEL) {
                            activePointerId = -1
                        }

                    } else if (state.brushFamily != null || isVectorTool) { 
                        // DRAWING LOGIC (Ink & Vector)
                        
                        when (action) {
                            MotionEvent.ACTION_DOWN -> {
                                activePointerId = event.getPointerId(0)
                                v.invalidate()
                                canvasView.invalidate()

                                // Effective Coordinates
                                val rawTouchPts = floatArrayOf(event.x, event.y)
                                inverseMatrix.mapPoints(rawTouchPts)
                                var effectiveX = rawTouchPts[0]
                                var effectiveY = rawTouchPts[1]

                                if (sketchViewModel.isSnapToGridEnabled) {
                                    val gridStepPx = UnitUtils.projectUnitsToPixels(
                                        value = sketchViewModel.gridConfig.spacing,
                                        unit = sketchViewModel.currentUnit,
                                        basePxPerMm = sketchViewModel.scaleConfig.basePixelsPerMillimeter
                                    )
                                    if (gridStepPx > 0) {
                                        effectiveX = (kotlin.math.round(effectiveX / gridStepPx) * gridStepPx)
                                        effectiveY = (kotlin.math.round(effectiveY / gridStepPx) * gridStepPx)
                                    }
                                }

                                lastInputX = effectiveX
                                lastInputY = effectiveY
                                lastInputPressure = event.pressure
                                
                                state.lastScreenX = event.x
                                state.lastScreenY = event.y
                                state.lastEventTime = event.eventTime
                                state.smoothedVelocityX = 0f
                                state.smoothedVelocityY = 0f

                                stabilizer.reset(effectiveX, effectiveY)
                                
                                // Vector/Fill Init
                                if (shouldCaptureVector) {
                                    state.vectorPoints.clear()
                                    var pressure = adjustPressure(event.pressure, sketchViewModel.currentSensitivity)
                                    state.vectorPoints.add(com.skecher.sketchercompanionv1.dto.StrokePoint(effectiveX, effectiveY, pressure))
                                    
                                    if (state.toolType == ToolType.TECHNICAL_PEN) {
                                        val (path, _, _) = com.skecher.sketchercompanionv1.utils.PathGenerator.generateStrokePath(state.vectorPoints, state.size, sketchViewModel.penMinSizeFactor)
                                        val alpha = (state.opacity * 255).toInt()
                                        val colorWithAlpha = androidx.core.graphics.ColorUtils.setAlphaComponent(state.color, alpha)
                                        canvasView.updateCurrentVectorPreview(path, state.vectorPoints.toList(), colorWithAlpha, state.size, sketchViewModel.penMinSizeFactor)
                                    }

                                    if (sketchViewModel.isFillModeEnabled || state.toolType == ToolType.FILL_SHAPE) {
                                        state.fillPath.reset()
                                        state.fillPath.moveTo(effectiveX, effectiveY)
                                        canvasView.updateCurrentFill(state.fillPath, if(state.toolType == ToolType.FILL_SHAPE) state.color else sketchViewModel.fillModeColor)
                                    }
                                }
                                
                                // INK Init
                                if (isInkTool) { 
                                    val snapScreenPts = floatArrayOf(effectiveX, effectiveY)
                                    cameraMatrix.mapPoints(snapScreenPts)
                                    
                                    // Construct Event
                                    val props = arrayOf(MotionEvent.PointerProperties())
                                    props[0] = MotionEvent.PointerProperties()
                                    event.getPointerProperties(0, props[0])
                                    val coords = arrayOf(MotionEvent.PointerCoords())
                                    coords[0] = MotionEvent.PointerCoords()
                                    event.getPointerCoords(0, coords[0])
                                    coords[0].x = snapScreenPts[0]
                                    coords[0].y = snapScreenPts[1]
                                    if (state.toolType == ToolType.PRESSURE_PEN) {
                                        coords[0].pressure = adjustPressure(coords[0].pressure, sketchViewModel.currentSensitivity)
                                    }
                                    
                                    val snappedEvent = MotionEvent.obtain(
                                        event.downTime, event.eventTime, event.action, 1, props, coords,
                                        event.metaState, event.buttonState, event.xPrecision, event.yPrecision,
                                        event.deviceId, event.edgeFlags, event.source, event.flags
                                    )
                                    try {
                                        state.activeBrush?.let { brush ->
                                            strokeIdMap[activePointerId] = wetView.startStroke(snappedEvent, activePointerId, brush)
                                        }
                                    } finally {
                                        snappedEvent.recycle()
                                    }
                                }
                            }
                            MotionEvent.ACTION_MOVE -> {
                                val pointerIndex = event.findPointerIndex(activePointerId)
                                if (pointerIndex == -1 || event.pointerCount > 1) {
                                    // INTERRUPTED
                                    if (activePointerId != -1) {
                                         if (strokeIdMap.containsKey(activePointerId)) {
                                            wetView.cancelStroke(strokeIdMap[activePointerId]!!, event)
                                            strokeIdMap.remove(activePointerId)
                                         }
                                         if (shouldCaptureVector) {
                                             state.vectorPoints.clear()
                                             canvasView.updateCurrentVectorPreview(null, null, 0)
                                             if (sketchViewModel.isFillModeEnabled) canvasView.updateCurrentFill(null, 0)
                                         }
                                         activePointerId = -1
                                    }
                                } else {
                                    // Process
                                    val rawTouchPts = floatArrayOf(event.getX(pointerIndex), event.getY(pointerIndex))
                                    inverseMatrix.mapPoints(rawTouchPts)
                                    var effectiveX = rawTouchPts[0]
                                    var effectiveY = rawTouchPts[1]

                                    if (sketchViewModel.isSnapToGridEnabled) {
                                        val gridStepPx = UnitUtils.projectUnitsToPixels(sketchViewModel.gridConfig.spacing, sketchViewModel.currentUnit, sketchViewModel.scaleConfig.basePixelsPerMillimeter)
                                        if (gridStepPx > 0) {
                                            effectiveX = (kotlin.math.round(effectiveX / gridStepPx) * gridStepPx)
                                            effectiveY = (kotlin.math.round(effectiveY / gridStepPx) * gridStepPx)
                                        }
                                    }

                                    val dist = kotlin.math.hypot(effectiveX - lastInputX, effectiveY - lastInputY)
                                    // ... threshold ...
                                    if (dist > 1.0f) {
                                        lastInputX = effectiveX
                                        lastInputY = effectiveY
                                        lastInputPressure = event.getPressure(pointerIndex)
                                        
                                        val stabilizedPoint = stabilizer.update(effectiveX, effectiveY, sketchViewModel.currentSmoothing)
                                        
                                        if (shouldCaptureVector) {
                                             // ... Vector Add & Preview ...
                                            val p = adjustPressure(event.getPressure(pointerIndex), sketchViewModel.currentSensitivity)
                                            if (isTechPen) state.vectorPoints.add(com.skecher.sketchercompanionv1.dto.StrokePoint(stabilizedPoint.x, stabilizedPoint.y, p))
                                            
                                            // Fill
                                            if (sketchViewModel.isFillModeEnabled || state.toolType == ToolType.FILL_SHAPE) {
                                                state.fillPath.lineTo(stabilizedPoint.x, stabilizedPoint.y)
                                                val pp = android.graphics.Path(state.fillPath)
                                                pp.close()
                                                val c = if(state.toolType == ToolType.FILL_SHAPE) state.color else sketchViewModel.fillModeColor
                                                val a = (state.opacity * 255).toInt()
                                                canvasView.updateCurrentFill(pp, androidx.core.graphics.ColorUtils.setAlphaComponent(c, a))
                                            }
                                            
                                            // Tech Pen Preview
                                            if (state.toolType == ToolType.TECHNICAL_PEN) {
                                                val (path, _, _) = com.skecher.sketchercompanionv1.utils.PathGenerator.generateStrokePath(state.vectorPoints, state.size, sketchViewModel.penMinSizeFactor)
                                                val alpha = (state.opacity * 255).toInt()
                                                val ca = androidx.core.graphics.ColorUtils.setAlphaComponent(state.color, alpha)
                                                canvasView.updateCurrentVectorPreview(path, state.vectorPoints.toList(), ca, state.size, sketchViewModel.penMinSizeFactor)
                                            }
                                        }
                                        
                                        if (isInkTool) {
                                             val snapScreenPts = floatArrayOf(stabilizedPoint.x, stabilizedPoint.y)
                                             cameraMatrix.mapPoints(snapScreenPts)
                                             // Synthesize Move
                                             val props = arrayOf(MotionEvent.PointerProperties())
                                             props[0] = MotionEvent.PointerProperties()
                                             event.getPointerProperties(pointerIndex, props[0])
                                             val coords = arrayOf(MotionEvent.PointerCoords())
                                             coords[0] = MotionEvent.PointerCoords()
                                             event.getPointerCoords(pointerIndex, coords[0])
                                             coords[0].x = snapScreenPts[0]
                                             coords[0].y = snapScreenPts[1]
                                              if (state.toolType == ToolType.PRESSURE_PEN) {
                                                 coords[0].pressure = adjustPressure(coords[0].pressure, sketchViewModel.currentSensitivity)
                                             }
                                             val ev = MotionEvent.obtain(event.downTime, event.eventTime, event.action, 1, props, coords, event.metaState, event.buttonState, event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags, event.source, event.flags)
                                             try {
                                                  strokeIdMap[activePointerId]?.let { wetView.addToStroke(ev, activePointerId, it, null) }
                                             } finally { ev.recycle() }
                                        }
                                    }
                                }
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                                if (activePointerId != -1 && event.getPointerId(event.actionIndex) == activePointerId) {
                                    // Finish
                                    val stabilizedPoint = stabilizer.update(lastInputX, lastInputY, sketchViewModel.currentSmoothing)
                                    
                                    if (shouldCaptureVector) {
                                         // Commit Vector/Fill
                                         if (isTechPen) state.vectorPoints.add(com.skecher.sketchercompanionv1.dto.StrokePoint(stabilizedPoint.x, stabilizedPoint.y, adjustPressure(lastInputPressure, sketchViewModel.currentSensitivity)))
                                         
                                         if (sketchViewModel.isFillModeEnabled || state.toolType == ToolType.FILL_SHAPE) {
                                              state.fillPath.lineTo(stabilizedPoint.x, stabilizedPoint.y)
                                              state.fillPath.close()
                                              val c = if(state.toolType == ToolType.FILL_SHAPE) state.color else sketchViewModel.fillModeColor
                                              val a = (state.opacity * 255).toInt()
                                              val fData = com.skecher.sketchercompanionv1.dto.FillData(android.graphics.Path(state.fillPath), androidx.core.graphics.ColorUtils.setAlphaComponent(c, a))
                                              sketchViewModel.addFill(fData)
                                              canvasView.updateCurrentFill(null, 0)
                                              canvasView.bakeFill(fData)
                                              canvasView.setLayers(sketchViewModel.layers)
                                         }
                                         
                                         if (state.toolType == ToolType.TECHNICAL_PEN) {
                                              val pts = state.vectorPoints 
                                              val (path, left, right) = com.skecher.sketchercompanionv1.utils.PathGenerator.generateStrokePath(pts, state.size, sketchViewModel.penMinSizeFactor)
                                              val a = (state.opacity * 255).toInt()
                                              val ca = androidx.core.graphics.ColorUtils.setAlphaComponent(state.color, a)
                                              val s = com.skecher.sketchercompanionv1.dto.VectorStroke(pts.toList(), ca, state.size, path, left, right)
                                              sketchViewModel.addVectorStroke(s)
                                              canvasView.updateCurrentVectorPreview(null, null, 0)
                                              canvasView.bakeStroke(s)
                                              canvasView.setLayers(sketchViewModel.layers)
                                         }
                                    }
                                    
                                    if (isInkTool) {
                                        val snapScreenPts = floatArrayOf(stabilizedPoint.x, stabilizedPoint.y)
                                        cameraMatrix.mapPoints(snapScreenPts)
                                        // Synthesize Up
                                         val pointerIndex = event.findPointerIndex(activePointerId)
                                         // Safety check
                                         if (pointerIndex != -1) {
                                             val props = arrayOf(MotionEvent.PointerProperties())
                                             props[0] = MotionEvent.PointerProperties()
                                             event.getPointerProperties(pointerIndex, props[0])
                                             val coords = arrayOf(MotionEvent.PointerCoords())
                                             coords[0] = MotionEvent.PointerCoords()
                                             event.getPointerCoords(pointerIndex, coords[0])
                                             coords[0].x = snapScreenPts[0]
                                             coords[0].y = snapScreenPts[1]
                                             val ev = MotionEvent.obtain(event.downTime, event.eventTime, MotionEvent.ACTION_UP, 1, props, coords, event.metaState, event.buttonState, event.xPrecision, event.yPrecision, event.deviceId, event.edgeFlags, event.source, event.flags)
                                             try {
                                                  strokeIdMap[activePointerId]?.let { 
                                                      wetView.finishStroke(ev, activePointerId, it)
                                                      strokeIdMap.remove(activePointerId)
                                                  }
                                             } finally { ev.recycle() }
                                         }
                                    }
                                    activePointerId = -1
                                }
                            }
                            MotionEvent.ACTION_CANCEL -> {
                                 if (activePointerId != -1) {
                                     strokeIdMap[activePointerId]?.let { wetView.cancelStroke(it, event); strokeIdMap.remove(activePointerId) }
                                     if (shouldCaptureVector) {
                                         state.vectorPoints.clear()
                                         canvasView.updateCurrentVectorPreview(null, null, 0)
                                         canvasView.updateCurrentFill(null, 0)
                                     }
                                     activePointerId = -1
                                 }
                            }
                        }
                    } else if (strokeIdMap.isNotEmpty()) {
                        // Cleanup
                        strokeIdMap.forEach { (_, sid) -> wetView.cancelStroke(sid, event) }
                        strokeIdMap.clear()
                        state.fillPath.reset()
                        canvasView.updateCurrentFill(null, 0)
                        activePointerId = -1
                    }
"""

final_content = lines[:start_idx] + [new_logic + "\n"] + lines[end_idx:]

with open(file_path, 'w', encoding='utf-8') as f:
    f.writelines(final_content)

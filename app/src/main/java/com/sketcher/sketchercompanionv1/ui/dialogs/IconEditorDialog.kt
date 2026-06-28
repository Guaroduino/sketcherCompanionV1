package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.components.ToolIcon
import com.sketcher.sketchercompanionv1.ui.components.VectorIconRenderer
import com.sketcher.sketchercompanionv1.ui.model.StudioTool
import com.sketcher.sketchercompanionv1.ui.model.ToolRegistry
import com.sketcher.sketchercompanionv1.ui.model.VectorIcon
import kotlin.math.roundToInt

enum class DrawMode { FREEHAND, LINE, ARC, ERASER, PAN }

class PathCommand(val type: Char, val args: List<Float>)

fun parsePathToAbsolute(pathStr: String): List<PathCommand> {
    val tokenRegex = """([MmLlHhVvCcSsQqTtAaZz])|(-?\d*\.?\d+(?:[eE][-+]?\d+)?)""".toRegex()
    val matches = tokenRegex.findAll(pathStr).toList()
    
    val commands = mutableListOf<PathCommand>()
    var i = 0
    
    var lastCommand = ' '
    
    var curX = 0f
    var curY = 0f
    var startX = 0f
    var startY = 0f
    
    while (i < matches.size) {
        val match = matches[i]
        val cmdMatch = match.groups[1]?.value
        
        var cmdChar = ' '
        if (cmdMatch != null) {
            cmdChar = cmdMatch[0]
            i++
        } else {
            if (lastCommand != ' ') {
                cmdChar = lastCommand
                if (lastCommand == 'M') cmdChar = 'L'
                if (lastCommand == 'm') cmdChar = 'l'
            } else {
                i++
                continue
            }
        }
        
        fun nextNumbers(n: Int): List<Float>? {
            val list = mutableListOf<Float>()
            for (k in 0 until n) {
                if (i >= matches.size) return null
                val num = matches[i].groups[2]?.value?.toFloatOrNull() ?: return null
                list.add(num)
                i++
            }
            return list
        }
        
        when (cmdChar) {
            'M' -> {
                val args = nextNumbers(2) ?: break
                curX = args[0]
                curY = args[1]
                startX = curX
                startY = curY
                commands.add(PathCommand('M', listOf(curX, curY)))
                lastCommand = 'M'
            }
            'm' -> {
                val args = nextNumbers(2) ?: break
                curX += args[0]
                curY += args[1]
                startX = curX
                startY = curY
                commands.add(PathCommand('M', listOf(curX, curY)))
                lastCommand = 'm'
            }
            'L' -> {
                val args = nextNumbers(2) ?: break
                curX = args[0]
                curY = args[1]
                commands.add(PathCommand('L', listOf(curX, curY)))
                lastCommand = 'L'
            }
            'l' -> {
                val args = nextNumbers(2) ?: break
                curX += args[0]
                curY += args[1]
                commands.add(PathCommand('L', listOf(curX, curY)))
                lastCommand = 'l'
            }
            'H' -> {
                val args = nextNumbers(1) ?: break
                curX = args[0]
                commands.add(PathCommand('L', listOf(curX, curY)))
                lastCommand = 'H'
            }
            'h' -> {
                val args = nextNumbers(1) ?: break
                curX += args[0]
                commands.add(PathCommand('L', listOf(curX, curY)))
                lastCommand = 'h'
            }
            'V' -> {
                val args = nextNumbers(1) ?: break
                curY = args[0]
                commands.add(PathCommand('L', listOf(curX, curY)))
                lastCommand = 'V'
            }
            'v' -> {
                val args = nextNumbers(1) ?: break
                curY += args[0]
                commands.add(PathCommand('L', listOf(curX, curY)))
                lastCommand = 'v'
            }
            'Q' -> {
                val args = nextNumbers(4) ?: break
                val cx = args[0]
                val cy = args[1]
                curX = args[2]
                curY = args[3]
                commands.add(PathCommand('Q', listOf(cx, cy, curX, curY)))
                lastCommand = 'Q'
            }
            'q' -> {
                val args = nextNumbers(4) ?: break
                val cx = curX + args[0]
                val cy = curY + args[1]
                curX += args[2]
                curY += args[3]
                commands.add(PathCommand('Q', listOf(cx, cy, curX, curY)))
                lastCommand = 'q'
            }
            'C' -> {
                val args = nextNumbers(6) ?: break
                val cx1 = args[0]
                val cy1 = args[1]
                val cx2 = args[2]
                val cy2 = args[3]
                curX = args[4]
                curY = args[5]
                commands.add(PathCommand('C', listOf(cx1, cy1, cx2, cy2, curX, curY)))
                lastCommand = 'C'
            }
            'c' -> {
                val args = nextNumbers(6) ?: break
                val cx1 = curX + args[0]
                val cy1 = curY + args[1]
                val cx2 = curX + args[2]
                val cy2 = curY + args[3]
                curX += args[4]
                curY += args[5]
                commands.add(PathCommand('C', listOf(cx1, cy1, cx2, cy2, curX, curY)))
                lastCommand = 'c'
            }
            'S' -> {
                val args = nextNumbers(4) ?: break
                val cx2 = args[0]
                val cy2 = args[1]
                curX = args[2]
                curY = args[3]
                commands.add(PathCommand('C', listOf(curX, curY, cx2, cy2, curX, curY)))
                lastCommand = 'S'
            }
            's' -> {
                val args = nextNumbers(4) ?: break
                val cx2 = curX + args[0]
                val cy2 = curY + args[1]
                curX += args[2]
                curY += args[3]
                commands.add(PathCommand('C', listOf(curX, curY, cx2, cy2, curX, curY)))
                lastCommand = 's'
            }
            'Z', 'z' -> {
                curX = startX
                curY = startY
                commands.add(PathCommand('Z', emptyList()))
                lastCommand = 'Z'
            }
            else -> {
                if (cmdChar == 'A') {
                    val args = nextNumbers(7) ?: break
                    curX = args[5]
                    curY = args[6]
                    commands.add(PathCommand('L', listOf(curX, curY)))
                } else if (cmdChar == 'a') {
                    val args = nextNumbers(7) ?: break
                    curX += args[5]
                    curY += args[6]
                    commands.add(PathCommand('L', listOf(curX, curY)))
                }
            }
        }
    }
    return commands
}

fun fitPathsToViewport(rawPathList: List<String>): List<String> {
    val parsedGroups = rawPathList.map { parsePathToAbsolute(it) }
    
    val xCoords = mutableListOf<Float>()
    val yCoords = mutableListOf<Float>()
    
    for (group in parsedGroups) {
        for (cmd in group) {
            var idx = 0
            while (idx < cmd.args.size) {
                xCoords.add(cmd.args[idx])
                yCoords.add(cmd.args[idx + 1])
                idx += 2
            }
        }
    }
    
    if (xCoords.isEmpty() || yCoords.isEmpty()) return rawPathList
    
    val minX = xCoords.minOrNull() ?: 0f
    val maxX = xCoords.maxOrNull() ?: 100f
    val minY = yCoords.minOrNull() ?: 0f
    val maxY = yCoords.maxOrNull() ?: 100f
    
    val width = maxX - minX
    val height = maxY - minY
    
    if (width == 0f && height == 0f) return rawPathList
    
    val targetSize = 70f
    val scale = targetSize / maxOf(width, height).coerceAtLeast(1f)
    
    val centerX = minX + width / 2f
    val centerY = minY + height / 2f
    
    val dx = 50f - centerX * scale
    val dy = 50f - centerY * scale
    
    return parsedGroups.map { group ->
        val sb = StringBuilder()
        for (cmd in group) {
            sb.append(cmd.type).append(" ")
            var idx = 0
            while (idx < cmd.args.size) {
                val nx = cmd.args[idx] * scale + dx
                val ny = cmd.args[idx + 1] * scale + dy
                sb.append(String.format(java.util.Locale.US, "%.2f", nx)).append(" ")
                sb.append(String.format(java.util.Locale.US, "%.2f", ny)).append(" ")
                idx += 2
            }
        }
        sb.toString().trim()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconEditorDialog(
    viewModel: SketcherViewModel,
    onDismiss: () -> Unit
) {
    val theme by viewModel.themeConfig.collectAsState()
    val scaler = LocalUiScaler.current
    
    // Local copy of custom icons map
    var customIconsState by remember { mutableStateOf(theme.customIcons) }
    
    // Tools list (excluding divider)
    val customizableTools = remember {
        ToolRegistry.allTools.filter { it.registryId != "divider" }
    }
    
    var selectedTool by remember { mutableStateOf(customizableTools.first()) }
    
    // Active paths being edited (represented in normalized 0..100 space)
    val activePaths = remember { mutableStateListOf<String>() }
    
    // Current stroke path coordinates
    val currentStroke = remember { mutableStateListOf<Offset>() }
    
    // History for Undo/Redo
    val history = remember { mutableStateListOf<List<String>>() }
    var historyIndex by remember { mutableStateOf(-1) }
    
    var gridSize by remember { mutableStateOf(0) } // 0 = Off, 24, 32, 64, 128
    var strokeWidth by remember { mutableStateOf(6f) } // relative to 100x100 viewport
    
    // Draw Mode configuration
    var drawMode by remember { mutableStateOf(DrawMode.FREEHAND) }
    var arcCurvature by remember { mutableFloatStateOf(20f) } // Bulge factor for arcs
    
    // Zoom and Pan states
    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }
    var showImportDialog by remember { mutableStateOf(false) }
    
    // Helper to load tool's icon
    fun loadIconForTool(tool: StudioTool) {
        activePaths.clear()
        val json = customIconsState[tool.registryId] ?: customIconsState[tool.id]
        if (json != null) {
            try {
                val type = object : TypeToken<List<String>>() {}.type
                val loaded: List<String> = Gson().fromJson(json, type)
                activePaths.addAll(loaded)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        
        // Reset history
        history.clear()
        history.add(activePaths.toList())
        historyIndex = 0
    }
    
    // Load initial tool
    LaunchedEffect(selectedTool) {
        loadIconForTool(selectedTool)
    }
    
    // Helper to push state to history
    fun pushHistory() {
        // Clear forward history if we are in the middle of undoing
        while (history.size > historyIndex + 1) {
            history.removeAt(history.size - 1)
        }
        history.add(activePaths.toList())
        historyIndex = history.size - 1
        
        // Auto-save changes to the local state map so switching tools preserves drawing
        val json = Gson().toJson(activePaths.toList())
        customIconsState = customIconsState.toMutableMap().apply {
            put(selectedTool.registryId, json)
        }
    }
    
    fun undo() {
        if (historyIndex > 0) {
            historyIndex--
            activePaths.clear()
            activePaths.addAll(history[historyIndex])
            
            val json = Gson().toJson(activePaths.toList())
            customIconsState = customIconsState.toMutableMap().apply {
                put(selectedTool.registryId, json)
            }
        }
    }
    
    fun redo() {
        if (historyIndex < history.size - 1) {
            historyIndex++
            activePaths.clear()
            activePaths.addAll(history[historyIndex])
            
            val json = Gson().toJson(activePaths.toList())
            customIconsState = customIconsState.toMutableMap().apply {
                put(selectedTool.registryId, json)
            }
        }
    }
    
    // Helper to snap a value to the active grid size
    fun snapValue(v: Float, G: Int): Float {
        if (G <= 0) return v.coerceIn(0f, 100f)
        return ((v / 100f * G).roundToInt().toFloat() / G * 100f).coerceIn(0f, 100f)
    }

    var hasErasedInCurrentDrag by remember { mutableStateOf(false) }

    fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
        val l2 = (b.x - a.x) * (b.x - a.x) + (b.y - a.y) * (b.y - a.y)
        if (l2 == 0f) return kotlin.math.sqrt((p.x - a.x) * (p.x - a.x) + (p.y - a.y) * (p.y - a.y))
        var t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2
        t = t.coerceIn(0f, 1f)
        val projX = a.x + t * (b.x - a.x)
        val projY = a.y + t * (b.y - a.y)
        val dx = p.x - projX
        val dy = p.y - projY
        return kotlin.math.sqrt(dx*dx + dy*dy)
    }

    fun getPathPoints(pathStr: String): List<Offset> {
        val parts = pathStr.split(" ")
        val points = mutableListOf<Offset>()
        var i = 0
        while (i < parts.size) {
            val part = parts[i]
            if (part == "M" || part == "L" || part == "Q") {
                val x = parts.getOrNull(i + 1)?.toFloatOrNull()
                val y = parts.getOrNull(i + 2)?.toFloatOrNull()
                if (x != null && y != null) {
                    points.add(Offset(x, y))
                }
                if (part == "Q") {
                    val ex = parts.getOrNull(i + 3)?.toFloatOrNull()
                    val ey = parts.getOrNull(i + 4)?.toFloatOrNull()
                    if (ex != null && ey != null) {
                        points.add(Offset(ex, ey))
                    }
                    i += 5
                    continue
                }
                i += 3
                continue
            }
            i++
        }
        return points
    }

    fun isStrokeNearPoint(pathStr: String, pt: Offset, threshold: Float): Boolean {
        val points = getPathPoints(pathStr)
        if (points.isEmpty()) return false
        if (points.size == 1) {
            val dx = points[0].x - pt.x
            val dy = points[0].y - pt.y
            return kotlin.math.sqrt(dx*dx + dy*dy) <= threshold
        }
        for (idx in 0 until points.size - 1) {
            val dist = distanceToSegment(pt, points[idx], points[idx + 1])
            if (dist <= threshold) return true
        }
        return false
    }
    
    // Helper to transform all paths using coordinate mapping (supporting M, L, and Q commands)
    fun applyTransformation(transform: (Float, Float) -> Pair<Float, Float>) {
        val transformed = activePaths.map { pathStr ->
            val parts = pathStr.split(" ")
            val newParts = mutableListOf<String>()
            var i = 0
            while (i < parts.size) {
                val part = parts[i]
                if (part == "M" || part == "L") {
                    newParts.add(part)
                    val x = parts.getOrNull(i + 1)?.toFloatOrNull()
                    val y = parts.getOrNull(i + 2)?.toFloatOrNull()
                    if (x != null && y != null) {
                        val (nx, ny) = transform(x, y)
                        newParts.add(nx.toString())
                        newParts.add(ny.toString())
                        i += 3
                        continue
                    }
                } else if (part == "Q") {
                    newParts.add(part)
                    val cx = parts.getOrNull(i + 1)?.toFloatOrNull()
                    val cy = parts.getOrNull(i + 2)?.toFloatOrNull()
                    val ex = parts.getOrNull(i + 3)?.toFloatOrNull()
                    val ey = parts.getOrNull(i + 4)?.toFloatOrNull()
                    if (cx != null && cy != null && ex != null && ey != null) {
                        val (ncx, ncy) = transform(cx, cy)
                        val (nex, ney) = transform(ex, ey)
                        newParts.add(ncx.toString())
                        newParts.add(ncy.toString())
                        newParts.add(nex.toString())
                        newParts.add(ney.toString())
                        i += 5
                        continue
                    }
                }
                if (part.isNotEmpty()) {
                    newParts.add(part)
                }
                i++
            }
            newParts.joinToString(" ")
        }
        activePaths.clear()
        activePaths.addAll(transformed)
        pushHistory()
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                contentColor = theme.iconColor
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .heightIn(max = 680.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Icon Editor",
                    style = MaterialTheme.typography.titleLarge,
                    color = theme.iconColor
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 1. Tool Selection Row
                Text(
                    text = "Select Tool to Customize",
                    fontSize = 11.sp,
                    color = theme.iconColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(customizableTools) { tool ->
                        val isSelected = tool.id == selectedTool.id
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) theme.highlightColor
                                    else theme.buttonColor.copy(alpha = 0.3f)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) theme.iconColor else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    selectedTool = tool
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Temporary mock config for the selector icon preview
                            val mockTheme = theme.copy(customIcons = customIconsState)
                            ToolIcon(
                                tool = tool,
                                theme = mockTheme,
                                tint = theme.iconColor,
                                iconSize = 24.dp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Editing Icon for: ${selectedTool.contentDescription}",
                    style = MaterialTheme.typography.titleMedium,
                    color = theme.iconColor,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                
                // 2. Grid Selector Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Grid Snap: " + (if (gridSize > 0) "${gridSize}x${gridSize}" else "Off"),
                        fontSize = 12.sp,
                        color = theme.iconColor
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(0, 24, 32, 64, 128).forEach { size ->
                            val isSel = gridSize == size
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSel) theme.iconColor
                                        else theme.buttonColor.copy(alpha = 0.3f)
                                    )
                                    .clickable { gridSize = size }
                                    .padding(horizontal = 6.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (size == 0) "Off" else "$size",
                                    fontSize = 10.sp,
                                    color = if (isSel) theme.barBackgroundColor else theme.iconColor,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                // 2a. Zoom and View Controls Row
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Zoom: ${"%.1f".format(zoomScale)}x",
                            fontSize = 12.sp,
                            color = theme.iconColor
                        )
                        if (zoomScale > 1f || panOffset != Offset.Zero) {
                            Text(
                                text = "Reset View",
                                fontSize = 10.sp,
                                color = theme.highlightColor,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier
                                    .clickable {
                                        zoomScale = 1f
                                        panOffset = Offset.Zero
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    
                    Slider(
                        value = zoomScale,
                        onValueChange = {
                            zoomScale = it
                            if (zoomScale == 1f) {
                                panOffset = Offset.Zero
                            }
                        },
                        valueRange = 1f..5f,
                        modifier = Modifier.width(150.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                
                // 2b. Draw Mode Selection Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Draw Mode:",
                        fontSize = 12.sp,
                        color = theme.iconColor
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        DrawMode.values().forEach { mode ->
                            val isSel = drawMode == mode
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (isSel) theme.highlightColor
                                        else theme.buttonColor.copy(alpha = 0.3f)
                                    )
                                    .clickable { drawMode = mode }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                    fontSize = 10.sp,
                                    color = if (isSel) theme.iconColor else theme.iconColor.copy(alpha = 0.8f),
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            }
                        }
                    }
                }
                
                if (drawMode == DrawMode.ARC) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Arc Curvature: ${arcCurvature.roundToInt()}%",
                            fontSize = 11.sp,
                            color = theme.iconColor
                        )
                        Slider(
                            value = arcCurvature,
                            onValueChange = { arcCurvature = it },
                            valueRange = -80f..80f,
                            modifier = Modifier.width(150.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 3. Canvas (Large drawing space)
                Box(
                    modifier = Modifier
                        .size(240.sdp) // Scales with UI scale factor
                        .clip(RoundedCornerShape(12.dp))
                        .background(theme.buttonColor.copy(alpha = 0.15f))
                        .border(1.dp, theme.iconColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val parsedPaths = remember(activePaths.toList()) {
                        VectorIcon(activePaths.toList()).toComposePaths()
                    }
                    
                    fun getNormalizedOffset(screenOffset: Offset, canvasW: Float, canvasH: Float): Offset {
                        val center = Offset(canvasW / 2f, canvasH / 2f)
                        val vx = (screenOffset.x - center.x - panOffset.x) / zoomScale + center.x
                        val vy = (screenOffset.y - center.y - panOffset.y) / zoomScale + center.y
                        val rx = vx / canvasW * 100f
                        val ry = vy / canvasH * 100f
                        return Offset(snapValue(rx, gridSize), snapValue(ry, gridSize))
                    }
                    
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(gridSize, drawMode, arcCurvature, strokeWidth, zoomScale, panOffset) {
                                awaitPointerEventScope {
                                    var drawing = false
                                    while (true) {
                                        val event = awaitPointerEvent()
                                        val changes = event.changes
                                        val canvasW = size.width.toFloat()
                                        val canvasH = size.height.toFloat()
                                        val threshold = (strokeWidth * 1.5f).coerceAtLeast(6f)
                                        
                                        if (changes.size > 1) {
                                            // 2-finger zoom and pan
                                            drawing = false
                                            currentStroke.clear()
                                            
                                            val zoomChange = event.calculateZoom()
                                            val panChange = event.calculatePan()
                                            
                                            zoomScale = (zoomScale * zoomChange).coerceIn(1f, 5f)
                                            if (zoomScale == 1f) {
                                                panOffset = Offset.Zero
                                            } else {
                                                val maxPanX = ((zoomScale - 1f) * canvasW / 2f).coerceAtLeast(0f)
                                                val maxPanY = ((zoomScale - 1f) * canvasH / 2f).coerceAtLeast(0f)
                                                panOffset = Offset(
                                                    (panOffset.x + panChange.x).coerceIn(-maxPanX, maxPanX),
                                                    (panOffset.y + panChange.y).coerceIn(-maxPanY, maxPanY)
                                                )
                                            }
                                            changes.forEach { it.consume() }
                                        } else if (changes.size == 1) {
                                            val change = changes[0]
                                            val norm = getNormalizedOffset(change.position, canvasW, canvasH)
                                            
                                            if (drawMode == DrawMode.PAN) {
                                                if (change.pressed) {
                                                    val dragAmount = change.position - change.previousPosition
                                                    val maxPanX = ((zoomScale - 1f) * canvasW / 2f).coerceAtLeast(0f)
                                                    val maxPanY = ((zoomScale - 1f) * canvasH / 2f).coerceAtLeast(0f)
                                                    panOffset = Offset(
                                                        (panOffset.x + dragAmount.x).coerceIn(-maxPanX, maxPanX),
                                                        (panOffset.y + dragAmount.y).coerceIn(-maxPanY, maxPanY)
                                                    )
                                                    change.consume()
                                                }
                                            } else if (drawMode == DrawMode.ERASER) {
                                                if (change.pressed) {
                                                    val toRemove = activePaths.filter { isStrokeNearPoint(it, norm, threshold) }
                                                    if (toRemove.isNotEmpty()) {
                                                        activePaths.removeAll(toRemove)
                                                        hasErasedInCurrentDrag = true
                                                    }
                                                    change.consume()
                                                }
                                                if (change.changedToUp()) {
                                                    if (hasErasedInCurrentDrag) {
                                                        pushHistory()
                                                        hasErasedInCurrentDrag = false
                                                    }
                                                }
                                            } else {
                                                // Drawing
                                                if (change.changedToDown()) {
                                                    drawing = true
                                                    currentStroke.clear()
                                                    currentStroke.add(norm)
                                                    change.consume()
                                                } else if (change.pressed && drawing) {
                                                    if (drawMode == DrawMode.FREEHAND) {
                                                        val last = currentStroke.lastOrNull()
                                                        if (last == null || norm != last) {
                                                            currentStroke.add(norm)
                                                        }
                                                    } else {
                                                        if (currentStroke.size > 0) {
                                                            val p1 = currentStroke[0]
                                                            currentStroke.clear()
                                                            currentStroke.add(p1)
                                                            currentStroke.add(norm)
                                                        }
                                                    }
                                                    change.consume()
                                                } else if (change.changedToUp() && drawing) {
                                                    drawing = false
                                                    if (currentStroke.isNotEmpty()) {
                                                        if (currentStroke.size == 1) {
                                                            val pt = currentStroke[0]
                                                            activePaths.add("M ${pt.x} ${pt.y} L ${pt.x + 0.1f} ${pt.y + 0.1f}")
                                                        } else {
                                                            val p1 = currentStroke[0]
                                                            val p2 = currentStroke[currentStroke.size - 1]
                                                            
                                                            when (drawMode) {
                                                                DrawMode.FREEHAND -> {
                                                                    val sb = StringBuilder()
                                                                    sb.append("M ${p1.x} ${p1.y}")
                                                                    for (i in 1 until currentStroke.size) {
                                                                        sb.append(" L ${currentStroke[i].x} ${currentStroke[i].y}")
                                                                    }
                                                                    activePaths.add(sb.toString())
                                                                }
                                                                DrawMode.LINE -> {
                                                                    activePaths.add("M ${p1.x} ${p1.y} L ${p2.x} ${p2.y}")
                                                                }
                                                                DrawMode.ARC -> {
                                                                    val midX = (p1.x + p2.x) / 2f
                                                                    val midY = (p1.y + p2.y) / 2f
                                                                    val dx = p2.x - p1.x
                                                                    val dy = p2.y - p1.y
                                                                    val px = -dy
                                                                    val py = dx
                                                                    val cpx = midX + px * (arcCurvature / 100f)
                                                                    val cpy = midY + py * (arcCurvature / 100f)
                                                                    activePaths.add("M ${p1.x} ${p1.y} Q $cpx $cpy ${p2.x} ${p2.y}")
                                                                }
                                                                else -> {}
                                                            }
                                                        }
                                                        pushHistory()
                                                    }
                                                    currentStroke.clear()
                                                    change.consume()
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                    ) {
                        val canvasW = size.width
                        val canvasH = size.height
                        
                        val center = Offset(canvasW / 2f, canvasH / 2f)
                        withTransform({
                            translate(panOffset.x, panOffset.y)
                            scale(zoomScale, zoomScale, pivot = center)
                        }) {
                            if (gridSize > 0) {
                                val linesColor = theme.iconColor.copy(alpha = 0.08f)
                                for (i in 1 until gridSize) {
                                    val frac = i.toFloat() / gridSize
                                    drawLine(
                                        color = linesColor,
                                        start = Offset(frac * canvasW, 0f),
                                        end = Offset(frac * canvasW, canvasH),
                                        strokeWidth = 1f
                                    )
                                    drawLine(
                                        color = linesColor,
                                        start = Offset(0f, frac * canvasH),
                                        end = Offset(canvasW, frac * canvasH),
                                        strokeWidth = 1f
                                    )
                                }
                            }
                            
                            val scaleX = canvasW / 100f
                            val scaleY = canvasH / 100f
                            
                            withTransform({
                                scale(scaleX, scaleY, pivot = Offset.Zero)
                            }) {
                                parsedPaths.forEach { path ->
                                    drawPath(
                                        path = path,
                                        color = theme.iconColor,
                                        style = Stroke(
                                            width = strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                                
                                if (currentStroke.size > 1) {
                                    val p1 = currentStroke[0]
                                    val p2 = currentStroke[currentStroke.size - 1]
                                    
                                    val strokePath = androidx.compose.ui.graphics.Path()
                                    when (drawMode) {
                                        DrawMode.FREEHAND -> {
                                            strokePath.moveTo(p1.x, p1.y)
                                            for (i in 1 until currentStroke.size) {
                                                strokePath.lineTo(currentStroke[i].x, currentStroke[i].y)
                                            }
                                        }
                                        DrawMode.LINE -> {
                                            strokePath.moveTo(p1.x, p1.y)
                                            strokePath.lineTo(p2.x, p2.y)
                                        }
                                        DrawMode.ARC -> {
                                            val midX = (p1.x + p2.x) / 2f
                                            val midY = (p1.y + p2.y) / 2f
                                            val dx = p2.x - p1.x
                                            val dy = p2.y - p1.y
                                            val cpx = midX + (-dy) * (arcCurvature / 100f)
                                            val cpy = midY + dx * (arcCurvature / 100f)
                                            
                                            strokePath.moveTo(p1.x, p1.y)
                                            strokePath.quadraticBezierTo(cpx, cpy, p2.x, p2.y)
                                        }
                                        else -> {}
                                    }
                                    drawPath(
                                        path = strokePath,
                                        color = theme.iconColor.copy(alpha = 0.5f),
                                        style = Stroke(
                                            width = strokeWidth,
                                            cap = StrokeCap.Round,
                                            join = StrokeJoin.Round
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // Floating canvas navigation controls overlay in bottom-right corner
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .background(theme.barBackgroundColor.copy(alpha = 0.85f), RoundedCornerShape(20.dp))
                            .border(0.5.dp, theme.iconColor.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                zoomScale = (zoomScale - 0.25f).coerceAtLeast(1f)
                                if (zoomScale == 1f) {
                                    panOffset = Offset.Zero
                                }
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Remove,
                                contentDescription = "Zoom Out",
                                tint = theme.iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Text(
                            text = "${"%.1f".format(zoomScale)}x",
                            fontSize = 10.sp,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = theme.iconColor,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        
                        IconButton(
                            onClick = {
                                zoomScale = (zoomScale + 0.25f).coerceIn(1f, 5f)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Zoom In",
                                tint = theme.iconColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(16.dp)
                                .background(theme.iconColor.copy(alpha = 0.2f))
                        )
                        
                        val isPanMode = drawMode == DrawMode.PAN
                        IconButton(
                            onClick = {
                                drawMode = if (isPanMode) DrawMode.FREEHAND else DrawMode.PAN
                            },
                            modifier = Modifier
                                .size(28.dp)
                                .background(
                                    if (isPanMode) theme.highlightColor else Color.Transparent,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Default.PanTool,
                                contentDescription = "Pan Mode",
                                tint = if (isPanMode) theme.barBackgroundColor else theme.iconColor,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                // 4. Utility Editing Controls
                Text(
                    text = "Edit Operations",
                    fontSize = 11.sp,
                    color = theme.iconColor.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.Start)
                )
                
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Shifters
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { applyTransformation { x, y -> Pair(x - 5f, y) } },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.ArrowBack, "Shift Left", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                        IconButton(
                            onClick = { applyTransformation { x, y -> Pair(x + 5f, y) } },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.ArrowForward, "Shift Right", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                        IconButton(
                            onClick = { applyTransformation { x, y -> Pair(x, y - 5f) } },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, "Shift Up", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                        IconButton(
                            onClick = { applyTransformation { x, y -> Pair(x, y + 5f) } },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, "Shift Down", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                    }
                    
                    // Transforms & Import
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { applyTransformation { x, y -> Pair(100f - x, y) } },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.Flip, "Mirror H", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                        IconButton(
                            onClick = { applyTransformation { x, y -> Pair(x, 100f - y) } },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.RotateLeft, "Mirror V", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                        IconButton(
                            onClick = { applyTransformation { x, y -> Pair(100f - x, 100f - y) } },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.Loop, "Invert", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                        IconButton(
                            onClick = { showImportDialog = true },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.ContentPaste, "Import SVG/Code", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                    }
                }
                
                // Undo, Redo, Clear, Width
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { undo() },
                            enabled = historyIndex > 0,
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(
                                Icons.Default.Undo,
                                "Undo",
                                tint = if (historyIndex > 0) theme.iconColor else theme.iconColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.sdp)
                            )
                        }
                        IconButton(
                            onClick = { redo() },
                            enabled = historyIndex < history.size - 1,
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(
                                Icons.Default.Redo,
                                "Redo",
                                tint = if (historyIndex < history.size - 1) theme.iconColor else theme.iconColor.copy(alpha = 0.3f),
                                modifier = Modifier.size(20.sdp)
                            )
                        }
                        IconButton(
                            onClick = {
                                activePaths.clear()
                                pushHistory()
                            },
                            modifier = Modifier.size(36.sdp)
                        ) {
                            Icon(Icons.Default.Delete, "Clear", tint = theme.iconColor, modifier = Modifier.size(20.sdp))
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Thickness:", fontSize = 10.sp, color = theme.iconColor)
                        Slider(
                            value = strokeWidth,
                            onValueChange = { strokeWidth = it },
                            valueRange = 2f..14f,
                            modifier = Modifier.width(90.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 5. Live Preview Row & Save/Cancel buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Preview simulated button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Button Preview", fontSize = 10.sp, color = theme.iconColor.copy(alpha = 0.6f))
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(theme.floatingShape())
                                .background(theme.buttonColor)
                                .border(1.dp, theme.iconColor.copy(alpha = 0.15f), theme.floatingShape()),
                            contentAlignment = Alignment.Center
                        ) {
                            VectorIconRenderer(
                                vectorIcon = VectorIcon(activePaths.toList()),
                                tint = theme.iconColor,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = strokeWidth
                            )
                        }
                    }
                    
                    // Footer Actions
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onDismiss,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.buttonColor.copy(alpha = 0.3f),
                                contentColor = theme.iconColor
                            )
                        ) {
                            Text("Cancel")
                        }
                        Button(
                            onClick = {
                                viewModel.updateTheme(theme.copy(customIcons = customIconsState))
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.iconColor,
                                contentColor = theme.barBackgroundColor
                            )
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }

    if (showImportDialog) {
        var importText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showImportDialog = false }) {
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                    contentColor = theme.iconColor
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.15f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Import SVG or Path Data",
                        style = MaterialTheme.typography.titleMedium,
                        color = theme.iconColor
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Paste standard SVG code or raw path data (e.g. M 10 10 L 20 20). The editor will automatically normalize, scale, and center it.",
                        fontSize = 11.sp,
                        color = theme.iconColor.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = importText,
                        onValueChange = { importText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        placeholder = { Text("<svg>...</svg> or M 0 0 ...", fontSize = 12.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = theme.iconColor,
                            unfocusedTextColor = theme.iconColor,
                            focusedBorderColor = theme.highlightColor,
                            unfocusedBorderColor = theme.iconColor.copy(alpha = 0.3f),
                            focusedContainerColor = theme.buttonColor.copy(alpha = 0.2f),
                            unfocusedContainerColor = theme.buttonColor.copy(alpha = 0.1f)
                        )
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showImportDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.buttonColor.copy(alpha = 0.3f),
                                contentColor = theme.iconColor
                            )
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val trimmed = importText.trim()
                                if (trimmed.isNotEmpty()) {
                                    val paths = mutableListOf<String>()
                                    val pathRegex = """d\s*=\s*["']([^"']+)["']""".toRegex(RegexOption.IGNORE_CASE)
                                    val matches = pathRegex.findAll(trimmed).map { it.groupValues[1] }.toList()
                                    
                                    if (matches.isNotEmpty()) {
                                        paths.addAll(matches)
                                    } else {
                                        paths.add(trimmed)
                                    }
                                    
                                    if (paths.isNotEmpty()) {
                                        val fitted = fitPathsToViewport(paths)
                                        activePaths.clear()
                                        activePaths.addAll(fitted)
                                        pushHistory()
                                    }
                                }
                                showImportDialog = false
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = theme.iconColor,
                                contentColor = theme.barBackgroundColor
                            )
                        ) {
                            Text("Import")
                        }
                    }
                }
            }
        }
    }
}

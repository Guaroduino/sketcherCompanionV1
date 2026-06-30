package com.sketcher.sketchercompanionv1.ui

import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.dto.FillStyle
import com.sketcher.sketchercompanionv1.dto.FillType
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import com.sketcher.sketchercompanionv1.ui.theme.ssp
import com.sketcher.sketchercompanionv1.utils.ImageTextureCache

@Composable
fun FillStylePickerDialog(
    initialStyle: FillStyle,
    onDismiss: () -> Unit,
    onStyleSelected: (FillStyle) -> Unit,
    onDisable: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(initialStyle.type) }

    // Solid Color State
    var solidColor by remember {
        mutableStateOf(
            if (initialStyle is FillStyle.Solid) initialStyle.color
            else AndroidColor.argb(255, 0, 122, 255)
        )
    }

    // SVG Pattern State
    val builtinSvgs = listOf(
        // Polka Dots
        "<svg width='40' height='40' viewBox='0 0 40 40' xmlns='http://www.w3.org/2000/svg'><circle cx='20' cy='20' r='6' fill='black'/></svg>",
        // Hexagons
        "<svg width='52' height='60' viewBox='0 0 52 60' xmlns='http://www.w3.org/2000/svg'><path d='M26 0 L52 15 L52 45 L26 60 L0 45 L0 15 Z M26 10 L43 20 L43 40 L26 50 L9 40 L9 20 Z' fill='none' stroke='black' stroke-width='2'/></svg>",
        // Wavy Lines
        "<svg width='60' height='60' viewBox='0 0 60 60' xmlns='http://www.w3.org/2000/svg'><path d='M0 10 Q 15 20, 30 10 T 60 10 M0 30 Q 15 40, 30 30 T 60 30 M0 50 Q 15 60, 30 50 T 60 50' fill='none' stroke='black' stroke-width='2'/></svg>",
        // Carbon Fiber
        "<svg width='40' height='40' viewBox='0 0 40 40' xmlns='http://www.w3.org/2000/svg'><path d='M0 0 L40 40 M40 0 L0 40' fill='none' stroke='black' stroke-width='2'/></svg>"
    )

    var svgContent by remember {
        mutableStateOf(
            if (initialStyle is FillStyle.SvgPattern) initialStyle.svgContent
            else builtinSvgs.first()
        )
    }
    var svgScaleX by remember { mutableFloatStateOf(if (initialStyle is FillStyle.SvgPattern) initialStyle.scaleX else 1f) }
    var svgScaleY by remember { mutableFloatStateOf(if (initialStyle is FillStyle.SvgPattern) initialStyle.scaleY else 1f) }
    var svgRotation by remember { mutableFloatStateOf(if (initialStyle is FillStyle.SvgPattern) initialStyle.rotation else 0f) }
    var svgOffsetX by remember { mutableFloatStateOf(if (initialStyle is FillStyle.SvgPattern) initialStyle.offsetX else 0f) }
    var svgOffsetY by remember { mutableFloatStateOf(if (initialStyle is FillStyle.SvgPattern) initialStyle.offsetY else 0f) }

    // Math Texture State
    var mathPatternName by remember {
        mutableStateOf(
            if (initialStyle is FillStyle.MathTexture) initialStyle.patternName
            else "GRID"
        )
    }
    var mathPrimaryColor by remember {
        mutableStateOf(
            if (initialStyle is FillStyle.MathTexture) initialStyle.primaryColor
            else AndroidColor.BLACK
        )
    }
    var mathSecondaryColor by remember {
        mutableStateOf(
            if (initialStyle is FillStyle.MathTexture) initialStyle.secondaryColor
            else AndroidColor.TRANSPARENT
        )
    }
    var mathSpacing by remember { mutableFloatStateOf(if (initialStyle is FillStyle.MathTexture) initialStyle.spacing else 20f) }
    var mathThickness by remember { mutableFloatStateOf(if (initialStyle is FillStyle.MathTexture) initialStyle.thickness else 2f) }
    var mathAngle by remember { mutableFloatStateOf(if (initialStyle is FillStyle.MathTexture) initialStyle.angle else 0f) }

    // Image Texture State
    var imagePath by remember {
        mutableStateOf(
            if (initialStyle is FillStyle.ImageTexture) initialStyle.imagePath
            else ""
        )
    }
    var imgScaleX by remember { mutableFloatStateOf(if (initialStyle is FillStyle.ImageTexture) initialStyle.scaleX else 1f) }
    var imgScaleY by remember { mutableFloatStateOf(if (initialStyle is FillStyle.ImageTexture) initialStyle.scaleY else 1f) }
    var imgRotation by remember { mutableFloatStateOf(if (initialStyle is FillStyle.ImageTexture) initialStyle.rotation else 0f) }
    var imgOffsetX by remember { mutableFloatStateOf(if (initialStyle is FillStyle.ImageTexture) initialStyle.offsetX else 0f) }
    var imgOffsetY by remember { mutableFloatStateOf(if (initialStyle is FillStyle.ImageTexture) initialStyle.offsetY else 0f) }
    var imgOpacity by remember { mutableFloatStateOf(if (initialStyle is FillStyle.ImageTexture) initialStyle.opacity else 1f) }

    // File launcher for SVGs
    val svgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val content = stream.bufferedReader().use { it.readText() }
                    if (content.contains("<svg", ignoreCase = true)) {
                        svgContent = content
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // File launcher for Image texture files
    val imageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val localPath = ImageTextureCache.saveTextureLocally(stream, context)
                    if (localPath != null) {
                        imagePath = localPath
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Color picker sub-dialog triggers
    var pickingMathColorTarget by remember { mutableStateOf<String?>(null) } // "PRIMARY" or "SECONDARY"

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .width(320.sdp)
                .heightIn(max = 500.sdp)
                .clip(RoundedCornerShape(16.sdp)),
            shape = RoundedCornerShape(16.sdp),
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.sdp),
                verticalArrangement = Arrangement.spacedBy(12.sdp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Tab System
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    modifier = Modifier.fillMaxWidth().height(40.sdp)
                ) {
                    FillType.entries.forEach { type ->
                        Tab(
                            selected = selectedTab == type,
                            onClick = { selectedTab = type },
                            text = { Text(type.name, fontSize = 10.ssp) }
                        )
                    }
                }

                // Selected Tab Panel Content
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (selectedTab) {
                        FillType.SOLID -> {
                            SolidColorSelector(
                                initialColor = solidColor,
                                onColorChanged = { solidColor = it }
                            )
                        }
                        FillType.SVG_PATTERN -> {
                            SvgPatternPanel(
                                svgContent = svgContent,
                                scaleX = svgScaleX,
                                scaleY = svgScaleY,
                                rotation = svgRotation,
                                offsetX = svgOffsetX,
                                offsetY = svgOffsetY,
                                builtinSvgs = builtinSvgs,
                                onSvgContentChanged = { svgContent = it },
                                onScaleXChanged = { svgScaleX = it },
                                onScaleYChanged = { svgScaleY = it },
                                onRotationChanged = { svgRotation = it },
                                onOffsetXChanged = { svgOffsetX = it },
                                onOffsetYChanged = { svgOffsetY = it },
                                onImportClick = { svgLauncher.launch("image/svg+xml") }
                            )
                        }
                        FillType.MATH_TEXTURE -> {
                            MathTexturePanel(
                                patternName = mathPatternName,
                                primaryColor = mathPrimaryColor,
                                secondaryColor = mathSecondaryColor,
                                spacing = mathSpacing,
                                thickness = mathThickness,
                                angle = mathAngle,
                                onPatternChanged = { mathPatternName = it },
                                onPrimaryColorClick = { pickingMathColorTarget = "PRIMARY" },
                                onSecondaryColorClick = { pickingMathColorTarget = "SECONDARY" },
                                onSpacingChanged = { mathSpacing = it },
                                onThicknessChanged = { mathThickness = it },
                                onAngleChanged = { mathAngle = it }
                            )
                        }
                        FillType.IMAGE_TEXTURE -> {
                            ImageTexturePanel(
                                imagePath = imagePath,
                                scaleX = imgScaleX,
                                scaleY = imgScaleY,
                                rotation = imgRotation,
                                offsetX = imgOffsetX,
                                offsetY = imgOffsetY,
                                opacity = imgOpacity,
                                onScaleXChanged = { imgScaleX = it },
                                onScaleYChanged = { imgScaleY = it },
                                onRotationChanged = { imgRotation = it },
                                onOffsetXChanged = { imgOffsetX = it },
                                onOffsetYChanged = { imgOffsetY = it },
                                onOpacityChanged = { imgOpacity = it },
                                onChooseImage = { imageLauncher.launch("image/*") }
                            )
                        }
                    }
                }

                // Actions Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.sdp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.sdp)) {
                        Text("Cancel", fontSize = 12.ssp)
                    }
                    if (onDisable != null) {
                        TextButton(onClick = { onDisable(); onDismiss() }, shape = RoundedCornerShape(8.sdp)) {
                            Text("Clear", fontSize = 12.ssp)
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            val styleResult = when (selectedTab) {
                                FillType.SOLID -> FillStyle.Solid(solidColor)
                                FillType.SVG_PATTERN -> FillStyle.SvgPattern(
                                    svgContent = svgContent,
                                    scaleX = svgScaleX,
                                    scaleY = svgScaleY,
                                    rotation = svgRotation,
                                    offsetX = svgOffsetX,
                                    offsetY = svgOffsetY
                                )
                                FillType.MATH_TEXTURE -> FillStyle.MathTexture(
                                    patternName = mathPatternName,
                                    primaryColor = mathPrimaryColor,
                                    secondaryColor = mathSecondaryColor,
                                    spacing = mathSpacing,
                                    thickness = mathThickness,
                                    angle = mathAngle
                                )
                                FillType.IMAGE_TEXTURE -> FillStyle.ImageTexture(
                                    imagePath = imagePath,
                                    scaleX = imgScaleX,
                                    scaleY = imgScaleY,
                                    rotation = imgRotation,
                                    offsetX = imgOffsetX,
                                    offsetY = imgOffsetY,
                                    opacity = imgOpacity
                                )
                            }
                            onStyleSelected(styleResult)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.sdp)
                    ) {
                        Text("Apply", fontSize = 12.ssp)
                    }
                }
            }
        }
    }

    // Sub color picker for Math Textures colors
    if (pickingMathColorTarget != null) {
        val initialColor = if (pickingMathColorTarget == "PRIMARY") mathPrimaryColor else mathSecondaryColor
        ColorPickerDialog(
            initialColor = initialColor,
            onDismiss = { pickingMathColorTarget = null },
            onColorSelected = { color ->
                if (pickingMathColorTarget == "PRIMARY") {
                    mathPrimaryColor = color
                } else {
                    mathSecondaryColor = color
                }
                pickingMathColorTarget = null
            },
            onDisable = if (pickingMathColorTarget == "SECONDARY") {
                { mathSecondaryColor = AndroidColor.TRANSPARENT }
            } else null
        )
    }
}

@Composable
fun SolidColorSelector(
    initialColor: Int,
    onColorChanged: (Int) -> Unit
) {
    // We import and leverage the Wheel and Sliders of our own app color picker
    var hue by remember { mutableFloatStateOf(0f) }
    var saturation by remember { mutableFloatStateOf(1f) }
    var value by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(initialColor) {
        val hsv = FloatArray(3)
        AndroidColor.colorToHSV(initialColor, hsv)
        hue = hsv[0]
        saturation = hsv[1]
        value = hsv[2]
    }

    val currentColor = remember(hue, saturation, value) {
        Color.hsv(hue, saturation, value)
    }

    LaunchedEffect(currentColor) {
        onColorChanged(currentColor.toArgb())
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.sdp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(45.sdp)
                .clip(CircleShape)
                .background(currentColor)
                .border(2.sdp, Color.Gray, CircleShape)
        )
        ColorWheel(
            hue = hue,
            saturation = saturation,
            onColorChange = { h, s -> hue = h; saturation = s }
        )
        ValueSlider(
            value = value,
            hue = hue,
            saturation = saturation,
            onValueChange = { value = it }
        )
    }
}

@Composable
fun SvgPatternPanel(
    svgContent: String,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    builtinSvgs: List<String>,
    onSvgContentChanged: (String) -> Unit,
    onScaleXChanged: (Float) -> Unit,
    onScaleYChanged: (Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onOffsetXChanged: (Float) -> Unit,
    onOffsetYChanged: (Float) -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.sdp)
    ) {
        Text("Builtin Patterns", fontSize = 11.ssp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.sdp)
        ) {
            builtinSvgs.forEachIndexed { index, svg ->
                val isSelected = svgContent == svg
                Box(
                    modifier = Modifier
                        .size(40.sdp)
                        .clip(RoundedCornerShape(8.sdp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .border(
                            width = if (isSelected) 2.sdp else 1.sdp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.LightGray,
                            shape = RoundedCornerShape(8.sdp)
                        )
                        .clickable { onSvgContentChanged(svg) }
                ) {
                    Text(
                        text = "#${index + 1}",
                        modifier = Modifier.align(Alignment.Center),
                        fontSize = 11.ssp
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onImportClick,
            modifier = Modifier.fillMaxWidth().height(32.sdp),
            shape = RoundedCornerShape(8.sdp)
        ) {
            Text("Import custom SVG file", fontSize = 11.ssp)
        }

        HorizontalDivider()

        Text("Transformations", fontSize = 11.ssp)
        SliderRow(label = "Scale X", value = scaleX, range = 0.2f..5.0f, onValueChange = onScaleXChanged)
        SliderRow(label = "Scale Y", value = scaleY, range = 0.2f..5.0f, onValueChange = onScaleYChanged)
        SliderRow(label = "Rotation", value = rotation, range = 0f..360f, onValueChange = onRotationChanged)
        SliderRow(label = "Offset X", value = offsetX, range = -100f..100f, onValueChange = onOffsetXChanged)
        SliderRow(label = "Offset Y", value = offsetY, range = -100f..100f, onValueChange = onOffsetYChanged)
    }
}

@Composable
fun MathTexturePanel(
    patternName: String,
    primaryColor: Int,
    secondaryColor: Int,
    spacing: Float,
    thickness: Float,
    angle: Float,
    onPatternChanged: (String) -> Unit,
    onPrimaryColorClick: () -> Unit,
    onSecondaryColorClick: () -> Unit,
    onSpacingChanged: (Float) -> Unit,
    onThicknessChanged: (Float) -> Unit,
    onAngleChanged: (Float) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val patterns = listOf("GRID", "CHECKERBOARD", "STRIPES", "DOTS")

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.sdp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.sdp)
            ) {
                Text("Pattern: $patternName", fontSize = 11.ssp)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                patterns.forEach { pattern ->
                    DropdownMenuItem(
                        text = { Text(pattern, fontSize = 11.ssp) },
                        onClick = {
                            onPatternChanged(pattern)
                            expanded = false
                        }
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.sdp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.sdp),
                modifier = Modifier.clickable { onPrimaryColorClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(20.sdp)
                        .clip(CircleShape)
                        .background(Color(primaryColor))
                        .border(1.sdp, Color.Gray, CircleShape)
                )
                Text("Color 1", fontSize = 11.ssp)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.sdp),
                modifier = Modifier.clickable { onSecondaryColorClick() }
            ) {
                Box(
                    modifier = Modifier
                        .size(20.sdp)
                        .clip(CircleShape)
                        .background(
                            if (secondaryColor == AndroidColor.TRANSPARENT) Color.Transparent
                            else Color(secondaryColor)
                        )
                        .border(1.sdp, Color.Gray, CircleShape)
                ) {
                    if (secondaryColor == AndroidColor.TRANSPARENT) {
                        // Diagonal line indicating transparent
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawLine(Color.Red, start = androidx.compose.ui.geometry.Offset(0f, size.height), end = androidx.compose.ui.geometry.Offset(size.width, 0f), strokeWidth = 2f)
                        }
                    }
                }
                Text("Color 2", fontSize = 11.ssp)
            }
        }

        HorizontalDivider()

        SliderRow(label = "Spacing", value = spacing, range = 8f..150f, onValueChange = onSpacingChanged)
        SliderRow(label = "Thickness", value = thickness, range = 1f..spacing / 2f, onValueChange = onThicknessChanged)
        SliderRow(label = "Angle", value = angle, range = 0f..360f, onValueChange = onAngleChanged)
    }
}

@Composable
fun ImageTexturePanel(
    imagePath: String,
    scaleX: Float,
    scaleY: Float,
    rotation: Float,
    offsetX: Float,
    offsetY: Float,
    opacity: Float,
    onScaleXChanged: (Float) -> Unit,
    onScaleYChanged: (Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onOffsetXChanged: (Float) -> Unit,
    onOffsetYChanged: (Float) -> Unit,
    onOpacityChanged: (Float) -> Unit,
    onChooseImage: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.sdp)
    ) {
        OutlinedButton(
            onClick = onChooseImage,
            modifier = Modifier.fillMaxWidth().height(36.sdp),
            shape = RoundedCornerShape(8.sdp)
        ) {
            Text("Load Image from Gallery", fontSize = 11.ssp)
        }

        if (imagePath.isNotEmpty()) {
            val file = java.io.File(imagePath)
            Text("Texture: ${file.name}", fontSize = 10.ssp, color = MaterialTheme.colorScheme.primary)
        } else {
            Text("No texture selected", fontSize = 10.ssp, color = Color.Gray)
        }

        HorizontalDivider()

        Text("Image Transformations", fontSize = 11.ssp)
        SliderRow(label = "Scale X", value = scaleX, range = 0.1f..4.0f, onValueChange = onScaleXChanged)
        SliderRow(label = "Scale Y", value = scaleY, range = 0.1f..4.0f, onValueChange = onScaleYChanged)
        SliderRow(label = "Rotation", value = rotation, range = 0f..360f, onValueChange = onRotationChanged)
        SliderRow(label = "Offset X", value = offsetX, range = -200f..200f, onValueChange = onOffsetXChanged)
        SliderRow(label = "Offset Y", value = offsetY, range = -200f..200f, onValueChange = onOffsetYChanged)
        SliderRow(label = "Opacity", value = opacity, range = 0f..1f, onValueChange = onOpacityChanged)
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.sdp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            modifier = Modifier.width(60.sdp),
            fontSize = 9.ssp
        )
        Slider(
            value = value,
            valueRange = range,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = String.format("%.1f", value),
            modifier = Modifier.width(30.sdp),
            fontSize = 9.ssp
        )
    }
}

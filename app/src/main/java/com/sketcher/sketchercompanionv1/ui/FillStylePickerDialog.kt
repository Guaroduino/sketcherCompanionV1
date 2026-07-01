package com.sketcher.sketchercompanionv1.ui

import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

import android.graphics.Color as AndroidColor

import android.net.Uri

import androidx.activity.compose.rememberLauncherForActivityResult

import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.Canvas

import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.ExperimentalFoundationApi

import androidx.compose.foundation.combinedClickable

import androidx.compose.foundation.layout.*

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size

import androidx.compose.foundation.lazy.grid.GridCells

import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.foundation.lazy.grid.items

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.material3.*

import androidx.compose.runtime.*

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.geometry.Offset

import androidx.compose.ui.graphics.drawscope.drawIntoCanvas

import androidx.compose.ui.graphics.drawscope.rotate

import androidx.compose.ui.graphics.nativeCanvas

import androidx.compose.material3.SliderDefaults

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

    theme: UiThemeConfig,

    presets: List<FillStyle>,

    onPresetOverwritten: (Int, FillStyle) -> Unit,

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
    var imgTintColor by remember { mutableIntStateOf(if (initialStyle is FillStyle.ImageTexture) initialStyle.tintColor else android.graphics.Color.TRANSPARENT) }
    var imgTintMix by remember { mutableFloatStateOf(if (initialStyle is FillStyle.ImageTexture) initialStyle.tintMix else 0f) }

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

    val currentPreviewStyle = remember(selectedTab, solidColor, svgContent, svgScaleX, svgScaleY, svgRotation, svgOffsetX, svgOffsetY, mathPatternName, mathPrimaryColor, mathSecondaryColor, mathSpacing, mathThickness, mathAngle, imagePath, imgScaleX, imgScaleY, imgRotation, imgOffsetX, imgOffsetY, imgOpacity, imgTintColor, imgTintMix) {
        when (selectedTab) {
            FillType.SOLID -> FillStyle.Solid(solidColor)
            FillType.SVG_PATTERN -> FillStyle.SvgPattern(svgContent, svgScaleX, svgScaleY, svgRotation, svgOffsetX, svgOffsetY)
            FillType.MATH_TEXTURE -> FillStyle.MathTexture(mathPatternName, mathPrimaryColor, mathSecondaryColor, mathSpacing, mathThickness, mathAngle)
            FillType.IMAGE_TEXTURE -> FillStyle.ImageTexture(imagePath, imgScaleX, imgScaleY, imgRotation, imgOffsetX, imgOffsetY, imgOpacity, imgTintColor, imgTintMix)
        }
    }

    Dialog(onDismissRequest = onDismiss) {

        Surface(

            modifier = Modifier
                .width(450.sdp)
                .heightIn(max = 700.sdp)
                .clip(RoundedCornerShape(16.sdp)),

            shape = RoundedCornerShape(16.sdp),

            color = theme.barBackgroundColor,

            contentColor = theme.iconColor

        ) {

            Column(

                modifier = Modifier

                    .fillMaxSize()

                    .padding(16.sdp),

                verticalArrangement = Arrangement.spacedBy(12.sdp),

                horizontalAlignment = Alignment.CenterHorizontally

            ) {

                // Large Live Preview Card

                LargeFillStylePreview(

                    style = currentPreviewStyle,

                    modifier = Modifier

                        .fillMaxWidth()

                        .height(80.sdp)

                )

                // Presets Title & Row
                Text("Presets (Long press to Save)", fontSize = 10.ssp, color = theme.iconColor.copy(alpha = 0.7f))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.sdp),
                    horizontalArrangement = Arrangement.spacedBy(12.sdp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    presets.forEachIndexed { index, preset ->
                        val isSelected = (currentPreviewStyle == preset)
                        CirclePresetPreview(
                            style = preset,
                            isSelected = isSelected,
                            onClick = {
                                selectedTab = preset.type
                                when (preset) {
                                    is FillStyle.Solid -> {
                                        solidColor = preset.color
                                    }
                                    is FillStyle.SvgPattern -> {
                                        svgContent = preset.svgContent
                                        svgScaleX = preset.scaleX
                                        svgScaleY = preset.scaleY
                                        svgRotation = preset.rotation
                                        svgOffsetX = preset.offsetX
                                        svgOffsetY = preset.offsetY
                                    }
                                    is FillStyle.MathTexture -> {
                                        mathPatternName = preset.patternName
                                        mathPrimaryColor = preset.primaryColor
                                        mathSecondaryColor = preset.secondaryColor
                                        mathSpacing = preset.spacing
                                        mathThickness = preset.thickness
                                        mathAngle = preset.angle
                                    }
                                    is FillStyle.ImageTexture -> {
                                        imagePath = preset.imagePath
                                        imgScaleX = preset.scaleX
                                        imgScaleY = preset.scaleY
                                        imgRotation = preset.rotation
                                        imgOffsetX = preset.offsetX
                                        imgOffsetY = preset.offsetY
                                        imgOpacity = preset.opacity
                                    }
                                }
                            },
                            onLongClick = {
                                onPresetOverwritten(index, currentPreviewStyle)
                            },
                            theme = theme
                        )
                    }
                }

                // Tab System

                TabRow(

                    selectedTabIndex = selectedTab.ordinal, containerColor = theme.barBackgroundColor, contentColor = theme.iconColor,

                    modifier = Modifier.fillMaxWidth().height(40.sdp)

                ) {

                    FillType.entries.forEach { type ->

                        val tabName = when (type) {
                            FillType.SOLID -> "Solid"
                            FillType.SVG_PATTERN -> "Pattern"
                            FillType.MATH_TEXTURE -> "Math Tex"
                            FillType.IMAGE_TEXTURE -> "Img Tex"
                        }

                        Tab(

                            selected = selectedTab == type,

                            onClick = { selectedTab = type },

                            text = { Text(tabName, fontSize = 10.ssp) }

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

                                onImportClick = { svgLauncher.launch("image/svg+xml") },
                                theme = theme

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

                                onAngleChanged = { mathAngle = it },
                                theme = theme

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
                                tintColor = imgTintColor,
                                tintMix = imgTintMix,
                                onTintColorClick = { pickingMathColorTarget = "TINT" },
                                onTintMixChanged = { imgTintMix = it },
                                onClearTintClick = { imgTintColor = android.graphics.Color.TRANSPARENT; imgTintMix = 0f },
                                onScaleXChanged = { imgScaleX = it },
                                onScaleYChanged = { imgScaleY = it },
                                onRotationChanged = { imgRotation = it },
                                onOffsetXChanged = { imgOffsetX = it },
                                onOffsetYChanged = { imgOffsetY = it },
                                onChooseImage = { imageLauncher.launch("image/*") },
                                theme = theme
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

                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(8.sdp), colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)) {

                        Text("Cancel", fontSize = 12.ssp)

                    }

                    if (onDisable != null) {

                        TextButton(onClick = { onDisable(); onDismiss() }, shape = RoundedCornerShape(8.sdp), colors = ButtonDefaults.textButtonColors(contentColor = theme.iconColor)) {

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
                                    opacity = imgOpacity,
                                    tintColor = imgTintColor,
                                    tintMix = imgTintMix
                                )

                            }

                            onStyleSelected(styleResult)

                            onDismiss()

                        },

                        shape = RoundedCornerShape(8.sdp),

                        colors = ButtonDefaults.buttonColors(containerColor = theme.buttonColor, contentColor = theme.iconColor)

                    ) {

                        Text("Apply", fontSize = 12.ssp)

                    }

                }

            }

        }

    }

    // Sub color picker for Math Textures colors

    if (pickingMathColorTarget != null) {
        val initialColor = when (pickingMathColorTarget) {
            "PRIMARY" -> mathPrimaryColor
            "SECONDARY" -> mathSecondaryColor
            else -> if (imgTintColor == android.graphics.Color.TRANSPARENT) android.graphics.Color.RED else imgTintColor
        }

        ColorPickerDialog(
            initialColor = initialColor,
            theme = theme,
            onDismiss = { pickingMathColorTarget = null },
            onColorSelected = { colorInt ->
                when (pickingMathColorTarget) {
                    "PRIMARY" -> mathPrimaryColor = colorInt
                    "SECONDARY" -> mathSecondaryColor = colorInt
                    "TINT" -> imgTintColor = colorInt
                }
                pickingMathColorTarget = null
            },
            onDisable = when (pickingMathColorTarget) {
                "SECONDARY" -> ({ mathSecondaryColor = android.graphics.Color.TRANSPARENT })
                "TINT" -> ({ imgTintColor = android.graphics.Color.TRANSPARENT })
                else -> null
            }
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

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).drawScrollbar(scrollState),
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

        Spacer(modifier = Modifier.height(4.sdp))
        Text("Saturation", fontSize = 10.ssp, modifier = Modifier.align(Alignment.Start))
        SaturationSlider(
            saturation = saturation,
            hue = hue,
            value = value,
            onSaturationChange = { saturation = it }
        )

        Spacer(modifier = Modifier.height(4.sdp))
        Text("Brightness", fontSize = 10.ssp, modifier = Modifier.align(Alignment.Start))
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

    onImportClick: () -> Unit,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

) {

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).drawScrollbar(scrollState),
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
                        .size(60.sdp)
                        .clip(RoundedCornerShape(8.sdp))
                        .background(if (isSelected) theme.highlightColor else theme.buttonColor.copy(alpha = 0.2f))
                        .border(
                            width = if (isSelected) 2.sdp else 1.sdp,
                            color = if (isSelected) theme.iconColor else Color.LightGray,
                            shape = RoundedCornerShape(8.sdp)
                        )
                        .clickable { onSvgContentChanged(svg) }
                        .padding(4.sdp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(32.sdp)) {
                            // Transparent checkerboard under SVG
                            val cellSize = 6.dp.toPx()
                            val cols = (size.width / cellSize).toInt() + 1
                            val rows = (size.height / cellSize).toInt() + 1
                            for (c in 0..cols) {
                                for (r in 0..rows) {
                                    val checkerColor = if ((c + r) % 2 == 0) Color(0xFFE5E5E5) else Color(0xFFF2F2F2)
                                    drawRect(checkerColor, topLeft = Offset(c * cellSize, r * cellSize), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                }
                            }
                            // Draw actual SVG using caverock engine
                            try {
                                val svgObj = com.caverock.androidsvg.SVG.getFromString(svg)
                                drawIntoCanvas { canvas ->
                                    val nativeCanvas = canvas.nativeCanvas
                                    nativeCanvas.save()
                                    svgObj.documentWidth = size.width
                                    svgObj.documentHeight = size.height
                                    svgObj.renderToCanvas(nativeCanvas)
                                    nativeCanvas.restore()
                                }
                            } catch (e: Exception) {
                                drawCircle(Color.Gray, radius = 4.dp.toPx())
                            }
                        }
                        Spacer(modifier = Modifier.height(2.sdp))
                        Text("Preset " + (index + 1), fontSize = 8.ssp, color = theme.iconColor)
                    }
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

        SliderRow(label = "Scale X", value = scaleX, range = 0.2f..5.0f, theme = theme, onValueChange = onScaleXChanged)

        SliderRow(label = "Scale Y", value = scaleY, range = 0.2f..5.0f, theme = theme, onValueChange = onScaleYChanged)

        SliderRow(label = "Rotation", value = rotation, range = 0f..360f, theme = theme, onValueChange = onRotationChanged)

        SliderRow(label = "Offset X", value = offsetX, range = -100f..100f, theme = theme, onValueChange = onOffsetXChanged)

        SliderRow(label = "Offset Y", value = offsetY, range = -100f..100f, theme = theme, onValueChange = onOffsetYChanged)

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

    onAngleChanged: (Float) -> Unit,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

) {

    var expanded by remember { mutableStateOf(false) }

    val patterns = listOf("GRID", "CHECKERBOARD", "STRIPES", "DOTS")

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).drawScrollbar(scrollState),
        verticalArrangement = Arrangement.spacedBy(8.sdp)
    ) {

                Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.sdp)
        ) {
            patterns.forEach { pattern ->
                val isSelected = patternName == pattern
                Box(
                    modifier = Modifier
                        .size(60.sdp)
                        .clip(RoundedCornerShape(8.sdp))
                        .background(if (isSelected) theme.highlightColor else theme.buttonColor.copy(alpha = 0.2f))
                        .border(
                            width = if (isSelected) 2.sdp else 1.sdp,
                            color = if (isSelected) theme.iconColor else Color.LightGray,
                            shape = RoundedCornerShape(8.sdp)
                        )
                        .clickable { onPatternChanged(pattern) }
                        .padding(4.sdp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Canvas(modifier = Modifier.size(32.sdp)) {
                            // 1. Transparency checkerboard background
                            val cellSize = 6.dp.toPx()
                            val cols = (size.width / cellSize).toInt() + 1
                            val rows = (size.height / cellSize).toInt() + 1
                            for (c in 0..cols) {
                                for (r in 0..rows) {
                                    val checkerColor = if ((c + r) % 2 == 0) Color(0xFFE5E5E5) else Color(0xFFF2F2F2)
                                    drawRect(checkerColor, topLeft = Offset(c * cellSize, r * cellSize), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
                                }
                            }
                            
                            val primary = Color(primaryColor)
                            val secondary = if (secondaryColor == AndroidColor.TRANSPARENT) Color.Transparent else Color(secondaryColor)
                            
                            // 2. Draw secondary background color on top of checkerboard (if not transparent)
                            drawRect(secondary)
                            
                            // 3. Draw pattern lines
                            when (pattern) {
                                "GRID" -> {
                                    val strokeWidth = 1.5.dp.toPx()
                                    drawLine(primary, start = Offset(size.width / 3f, 0f), end = Offset(size.width / 3f, size.height), strokeWidth = strokeWidth)
                                    drawLine(primary, start = Offset(2 * size.width / 3f, 0f), end = Offset(2 * size.width / 3f, size.height), strokeWidth = strokeWidth)
                                    drawLine(primary, start = Offset(0f, size.height / 3f), end = Offset(size.width, size.height / 3f), strokeWidth = strokeWidth)
                                    drawLine(primary, start = Offset(0f, 2 * size.height / 3f), end = Offset(size.width, 2 * size.height / 3f), strokeWidth = strokeWidth)
                                }
                                "CHECKERBOARD" -> {
                                    drawRect(primary, topLeft = Offset(0f, 0f), size = androidx.compose.ui.geometry.Size(size.width / 2f, size.height / 2f))
                                    drawRect(primary, topLeft = Offset(size.width / 2f, size.height / 2f), size = androidx.compose.ui.geometry.Size(size.width / 2f, size.height / 2f))
                                }
                                "STRIPES" -> {
                                    val strokeWidth = 2.dp.toPx()
                                    drawLine(primary, start = Offset(0f, size.height), end = Offset(size.width, 0f), strokeWidth = strokeWidth)
                                    drawLine(primary, start = Offset(0f, size.height / 2f), end = Offset(size.width / 2f, 0f), strokeWidth = strokeWidth)
                                    drawLine(primary, start = Offset(size.width / 2f, size.height), end = Offset(size.width, size.height / 2f), strokeWidth = strokeWidth)
                                }
                                "DOTS" -> {
                                    drawCircle(primary, radius = 2.5.dp.toPx(), center = Offset(size.width / 4f, size.height / 4f))
                                    drawCircle(primary, radius = 2.5.dp.toPx(), center = Offset(3 * size.width / 4f, size.height / 4f))
                                    drawCircle(primary, radius = 2.5.dp.toPx(), center = Offset(size.width / 4f, 3 * size.height / 4f))
                                    drawCircle(primary, radius = 2.5.dp.toPx(), center = Offset(3 * size.width / 4f, 3 * size.height / 4f))
                                    drawCircle(primary, radius = 2.5.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(2.sdp))
                        Text(pattern, fontSize = 8.ssp, color = theme.iconColor)
                    }
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

        SliderRow(label = "Spacing", value = spacing, range = 8f..150f, theme = theme, onValueChange = onSpacingChanged)

        SliderRow(label = "Thickness", value = thickness, range = 1f..spacing / 2f, theme = theme, onValueChange = onThicknessChanged)

        SliderRow(label = "Angle", value = angle, range = 0f..360f, theme = theme, onValueChange = onAngleChanged)

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
    tintColor: Int,
    tintMix: Float,
    onTintColorClick: () -> Unit,
    onTintMixChanged: (Float) -> Unit,
    onClearTintClick: () -> Unit,
    onScaleXChanged: (Float) -> Unit,
    onScaleYChanged: (Float) -> Unit,
    onRotationChanged: (Float) -> Unit,
    onOffsetXChanged: (Float) -> Unit,
    onOffsetYChanged: (Float) -> Unit,
    onChooseImage: () -> Unit,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(scrollState).drawScrollbar(scrollState),
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
        Text("Color Tint / Tone Mix", fontSize = 11.ssp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.sdp)
        ) {
            Box(
                modifier = Modifier
                    .size(24.sdp)
                    .clip(CircleShape)
                    .background(if (tintColor == android.graphics.Color.TRANSPARENT) Color.Transparent else Color(tintColor))
                    .border(1.sdp, Color.Gray, CircleShape)
                    .clickable { onTintColorClick() }
            ) {
                if (tintColor == android.graphics.Color.TRANSPARENT) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawLine(
                            color = Color.Red,
                            start = Offset(0f, 0f),
                            end = Offset(size.width, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                }
            }

            Button(
                onClick = onTintColorClick,
                modifier = Modifier.height(30.sdp),
                shape = RoundedCornerShape(8.sdp)
            ) {
                Text(if (tintColor == android.graphics.Color.TRANSPARENT) "Choose Tint Color" else "Change Tint Color", fontSize = 10.ssp)
            }

            if (tintColor != android.graphics.Color.TRANSPARENT) {
                OutlinedButton(
                    onClick = onClearTintClick,
                    modifier = Modifier.height(30.sdp),
                    shape = RoundedCornerShape(8.sdp)
                ) {
                    Text("Clear Tint", fontSize = 10.ssp)
                }
            }
        }

        if (tintColor != android.graphics.Color.TRANSPARENT) {
            SliderRow(
                label = "Tint Intensity",
                value = tintMix,
                range = 0f..1f,
                theme = theme,
                onValueChange = onTintMixChanged
            )
        }

        HorizontalDivider()
        Text("Image Transformations", fontSize = 11.ssp)
        SliderRow(label = "Scale X", value = scaleX, range = 0.1f..4.0f, theme = theme, onValueChange = onScaleXChanged)
        SliderRow(label = "Scale Y", value = scaleY, range = 0.1f..4.0f, theme = theme, onValueChange = onScaleYChanged)
        SliderRow(label = "Rotation", value = rotation, range = 0f..360f, theme = theme, onValueChange = onRotationChanged)
        SliderRow(label = "Offset X", value = offsetX, range = -200f..200f, theme = theme, onValueChange = onOffsetXChanged)
        SliderRow(label = "Offset Y", value = offsetY, range = -200f..200f, theme = theme, onValueChange = onOffsetYChanged)
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    theme: com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig,
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
            fontSize = 9.ssp,
            color = theme.iconColor
        )
        Slider(
            value = value,
            valueRange = range,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).height(24.sdp),
            colors = SliderDefaults.colors(
                thumbColor = theme.buttonColor,
                activeTrackColor = theme.buttonColor,
                inactiveTrackColor = theme.highlightColor.copy(alpha = 0.24f)
            )
        )
        Text(
            text = String.format("%.1f", value),
            modifier = Modifier.width(30.sdp),
            fontSize = 9.ssp,
            color = theme.iconColor
        )
    }
}

@Composable
fun LargeFillStylePreview(
    style: FillStyle,
    modifier: Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.sdp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.sdp, Color.LightGray, RoundedCornerShape(8.sdp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Helper to draw transparency background grid
            fun drawPreviewCheckerboard() {
                val cellSize = 8.dp.toPx()
                val cols = (size.width / cellSize).toInt() + 1
                val rows = (size.height / cellSize).toInt() + 1
                for (c in 0..cols) {
                    for (r in 0..rows) {
                        val checkerColor = if ((c + r) % 2 == 0) Color(0xFFE5E5E5) else Color(0xFFF2F2F2)
                        drawRect(checkerColor, topLeft = Offset(c * cellSize, r * cellSize), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
                    }
                }
            }

            when (style) {
                is FillStyle.Solid -> {
                    drawRect(Color(style.color))
                }
                is FillStyle.MathTexture -> {
                    val primary = Color(style.primaryColor)
                    val secondary = if (style.secondaryColor == AndroidColor.TRANSPARENT) Color.Transparent else Color(style.secondaryColor)
                    
                    // Draw checkerboard under math patterns
                    drawPreviewCheckerboard()
                    drawRect(secondary)
                    
                    val scale = 0.5f
                    val spacingPx = style.spacing * scale
                    val thicknessPx = style.thickness * scale
                    
                    rotate(degrees = style.angle) {
                        when (style.patternName.uppercase()) {
                            "GRID" -> {
                                val maxDim = kotlin.math.max(size.width, size.height) * 2f
                                val cols = (maxDim / spacingPx).toInt() + 1
                                val rows = (maxDim / spacingPx).toInt() + 1
                                for (i in -cols..cols) {
                                    val x = i * spacingPx
                                    drawLine(primary, start = Offset(x, -maxDim), end = Offset(x, maxDim), strokeWidth = thicknessPx)
                                }
                                for (j in -rows..rows) {
                                    val y = j * spacingPx
                                    drawLine(primary, start = Offset(-maxDim, y), end = Offset(maxDim, y), strokeWidth = thicknessPx)
                                }
                            }
                            "CHECKERBOARD" -> {
                                val maxDim = kotlin.math.max(size.width, size.height) * 2f
                                val cols = (maxDim / spacingPx).toInt() + 1
                                val rows = (maxDim / spacingPx).toInt() + 1
                                for (i in -cols..cols) {
                                    for (j in -rows..rows) {
                                        if ((i + j) % 2 == 0) {
                                            drawRect(
                                                color = primary,
                                                topLeft = Offset(i * spacingPx, j * spacingPx),
                                                size = androidx.compose.ui.geometry.Size(spacingPx, spacingPx)
                                            )
                                        }
                                    }
                                }
                            }
                            "STRIPES" -> {
                                val maxDim = kotlin.math.max(size.width, size.height) * 2f
                                val cols = (maxDim / spacingPx).toInt() + 1
                                val rows = (maxDim / spacingPx).toInt() + 1
                                for (i in -rows * 2..cols * 2) {
                                    drawLine(
                                        color = primary,
                                        start = Offset(i * spacingPx, -maxDim),
                                        end = Offset((i + rows * 2) * spacingPx, maxDim),
                                        strokeWidth = thicknessPx
                                    )
                                }
                            }
                            "DOTS" -> {
                                val maxDim = kotlin.math.max(size.width, size.height) * 2f
                                val cols = (maxDim / spacingPx).toInt() + 1
                                val rows = (maxDim / spacingPx).toInt() + 1
                                for (i in -cols..cols) {
                                    for (j in -rows..rows) {
                                        drawCircle(
                                            color = primary,
                                            radius = thicknessPx,
                                            center = Offset(i * spacingPx, j * spacingPx)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is FillStyle.SvgPattern -> {
                    // Draw checkerboard under SVG patterns
                    drawPreviewCheckerboard()
                    if (style.svgContent.isNotEmpty()) {
                        try {
                            val svgObj = com.caverock.androidsvg.SVG.getFromString(style.svgContent)
                            drawIntoCanvas { canvas ->
                                val nativeCanvas = canvas.nativeCanvas
                                nativeCanvas.save()
                                
                                // Apply transform parameters to preview
                                nativeCanvas.scale(style.scaleX, style.scaleY)
                                nativeCanvas.rotate(style.rotation)
                                nativeCanvas.translate(style.offsetX, style.offsetY)
                                
                                svgObj.documentWidth = size.width
                                svgObj.documentHeight = size.height
                                svgObj.renderToCanvas(nativeCanvas)
                                nativeCanvas.restore()
                            }
                        } catch (e: Exception) {
                            // Fallback outline
                            val cols = 6
                            val rows = 4
                            val cellWidth = size.width / cols
                            val cellHeight = size.height / rows
                            for (i in 0..cols) {
                                for (j in 0..rows) {
                                    drawCircle(Color.LightGray, radius = 3.dp.toPx(), center = Offset(i * cellWidth, j * cellHeight))
                                }
                            }
                        }
                    }
                }
                is FillStyle.ImageTexture -> {
                    drawPreviewCheckerboard()
                    // Draw image texture details
                    drawRect(Color(0x331E88E5))
                    val paintColor = Color(0xFF1E88E5)
                    drawCircle(paintColor.copy(alpha=0.3f), radius = size.minDimension / 3.5f, center = Offset(size.width / 2f, size.height / 2f))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CirclePresetPreview(
    style: FillStyle,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    theme: UiThemeConfig,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(36.sdp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (isSelected) 2.sdp else 1.sdp,
                color = if (isSelected) theme.iconColor else Color.LightGray,
                shape = CircleShape
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Helper to draw transparency background grid
            fun drawPreviewCheckerboard() {
                val cellSize = 5.dp.toPx()
                val cols = (size.width / cellSize).toInt() + 1
                val rows = (size.height / cellSize).toInt() + 1
                for (c in 0..cols) {
                    for (r in 0..rows) {
                        val checkerColor = if ((c + r) % 2 == 0) Color(0xFFE5E5E5) else Color(0xFFF2F2F2)
                        drawRect(checkerColor, topLeft = Offset(c * cellSize, r * cellSize), size = androidx.compose.ui.geometry.Size(cellSize, cellSize))
                    }
                }
            }

            when (style) {
                is FillStyle.Solid -> {
                    drawRect(Color(style.color))
                }
                is FillStyle.MathTexture -> {
                    val primary = Color(style.primaryColor)
                    val secondary = if (style.secondaryColor == AndroidColor.TRANSPARENT) Color.Transparent else Color(style.secondaryColor)
                    
                    drawPreviewCheckerboard()
                    drawRect(secondary)
                    
                    val scale = 0.2f
                    val spacingPx = style.spacing * scale
                    val thicknessPx = style.thickness * scale
                    
                    rotate(degrees = style.angle) {
                        when (style.patternName.uppercase()) {
                            "GRID" -> {
                                val maxDim = kotlin.math.max(size.width, size.height) * 2f
                                val cols = (maxDim / spacingPx).toInt() + 1
                                val rows = (maxDim / spacingPx).toInt() + 1
                                for (i in -cols..cols) {
                                    val x = i * spacingPx
                                    drawLine(primary, start = Offset(x, -maxDim), end = Offset(x, maxDim), strokeWidth = thicknessPx)
                                }
                                for (j in -rows..rows) {
                                    val y = j * spacingPx
                                    drawLine(primary, start = Offset(-maxDim, y), end = Offset(maxDim, y), strokeWidth = thicknessPx)
                                }
                            }
                            "CHECKERBOARD" -> {
                                val maxDim = kotlin.math.max(size.width, size.height) * 2f
                                val cols = (maxDim / spacingPx).toInt() + 1
                                val rows = (maxDim / spacingPx).toInt() + 1
                                for (i in -cols..cols) {
                                    for (j in -rows..rows) {
                                        if ((i + j) % 2 == 0) {
                                            drawRect(
                                                color = primary,
                                                topLeft = Offset(i * spacingPx, j * spacingPx),
                                                size = androidx.compose.ui.geometry.Size(spacingPx, spacingPx)
                                            )
                                        }
                                    }
                                }
                            }
                            "STRIPES" -> {
                                val maxDim = kotlin.math.max(size.width, size.height) * 2f
                                val cols = (maxDim / spacingPx).toInt() + 1
                                val rows = (maxDim / spacingPx).toInt() + 1
                                for (i in -rows * 2..cols * 2) {
                                    drawLine(
                                        color = primary,
                                        start = Offset(i * spacingPx, -maxDim),
                                        end = Offset((i + rows * 2) * spacingPx, maxDim),
                                        strokeWidth = thicknessPx
                                    )
                                }
                            }
                            "DOTS" -> {
                                val maxDim = kotlin.math.max(size.width, size.height) * 2f
                                val cols = (maxDim / spacingPx).toInt() + 1
                                val rows = (maxDim / spacingPx).toInt() + 1
                                for (i in -cols..cols) {
                                    for (j in -rows..rows) {
                                        drawCircle(
                                            color = primary,
                                            radius = thicknessPx,
                                            center = Offset(i * spacingPx, j * spacingPx)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                is FillStyle.SvgPattern -> {
                    drawPreviewCheckerboard()
                    if (style.svgContent.isNotEmpty()) {
                        try {
                            val svgObj = com.caverock.androidsvg.SVG.getFromString(style.svgContent)
                            drawIntoCanvas { canvas ->
                                val nativeCanvas = canvas.nativeCanvas
                                nativeCanvas.save()
                                
                                nativeCanvas.scale(style.scaleX * 0.35f, style.scaleY * 0.35f)
                                nativeCanvas.rotate(style.rotation)
                                nativeCanvas.translate(style.offsetX, style.offsetY)
                                
                                svgObj.documentWidth = size.width
                                svgObj.documentHeight = size.height
                                svgObj.renderToCanvas(nativeCanvas)
                                nativeCanvas.restore()
                            }
                        } catch (e: Exception) {
                            val cols = 4
                            val rows = 4
                            val cellWidth = size.width / cols
                            val cellHeight = size.height / rows
                            for (i in 0..cols) {
                                for (j in 0..rows) {
                                    drawCircle(Color.LightGray, radius = 2.dp.toPx(), center = Offset(i * cellWidth, j * cellHeight))
                                }
                            }
                        }
                    }
                }
                is FillStyle.ImageTexture -> {
                    drawPreviewCheckerboard()
                    drawRect(Color(0x331E88E5))
                    val paintColor = Color(0xFF1E88E5)
                    drawCircle(paintColor.copy(alpha=0.3f), radius = size.minDimension / 3.5f, center = Offset(size.width / 2f, size.height / 2f))
                }
            }
        }
    }
}

fun Modifier.drawScrollbar(
    state: ScrollState,
    color: Color = Color.Gray.copy(alpha = 0.4f)
): Modifier = composed {
    drawWithContent {
        drawContent()
        val viewportHeight = size.height
        val totalHeight = state.maxValue + viewportHeight
        if (totalHeight > viewportHeight) {
            val scrollFraction = state.value.toFloat() / state.maxValue
            val barHeight = (viewportHeight * (viewportHeight / totalHeight)).coerceAtLeast(30.dp.toPx())
            val maxScrollOffset = viewportHeight - barHeight
            val barOffset = scrollFraction * maxScrollOffset
            
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width - 4.dp.toPx(), barOffset),
                size = Size(3.dp.toPx(), barHeight),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        }
    }
}
package com.skecher.sketchercompanionv1.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skecher.sketchercompanionv1.dto.DistanceUnit
import com.skecher.sketchercompanionv1.dto.ScaleConfig
import com.skecher.sketchercompanionv1.utils.UnitUtils

@Composable
fun ScaleIndicator(
    scaleConfig: ScaleConfig,
    currentUnit: DistanceUnit,
    currentZoom: Float,
    modifier: Modifier = Modifier
) {
    // 1. Define Ideal Visual Width (in pixels)
    // We want the bar to be roughly 100 logical pixels wide on screen for readability
    val density = LocalDensity.current
    val targetWidthPx = with(density) { 100.dp.toPx() } // Using 100dp as "100 visual pixels" baseline
    
    // 2. Calculate what this width represents in "Project Units" (e.g. Meters)
    // Formula: Pixels on Screen -> Project Units
    // Note: We divide targetWidthPx by currentZoom to get "World Pixels"
    val rawUnits = UnitUtils.pixelsToProjectUnits(
        pixels = targetWidthPx / currentZoom,
        unit = currentUnit,
        basePxPerMm = scaleConfig.basePixelsPerMillimeter
    )
    
    // 3. Snap to a "Nice" Number (e.g., 0.93m -> 1.0m, or 0.42m -> 0.5m)
    val niceUnits = UnitUtils.getClosestNiceNumber(rawUnits)
    
    // 4. Calculate Exact Screen Pixels for this "Nice" Number
    val exactWidthPx = UnitUtils.projectUnitsToPixels(
        value = niceUnits,
        unit = currentUnit,
        basePxPerMm = scaleConfig.basePixelsPerMillimeter
    ) * currentZoom
    
    val barWidthDp = with(density) { exactWidthPx.toDp() }
    
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.Start
    ) {
        // Row 1: Info (Zoom)
        Text(
            text = "Zoom: ${(currentZoom * 100).toInt()}%",
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        
        // Row 2: Visual Bar
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
             // Label centered above bar
             Text(
                text = "${"%.2f".format(niceUnits).removeSuffix("0").removeSuffix("0").removeSuffix(".")} ${currentUnit.symbol}",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black, // Dark text for visibility
                style = MaterialTheme.typography.bodyMedium.copy(
                    shadow = Shadow(Color.White, Offset(1f, 1f), 2f)
                )
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            Canvas(modifier = Modifier
                .width(barWidthDp)
                .height(10.dp)
            ) {
                val w = size.width
                val h = size.height
                val strokeWidth = 2.dp.toPx()
                val color = Color.Black
                
                // Horizontal Line (Bottom aligned)
                val lineY = h - strokeWidth / 2
                drawLine(
                    color = color,
                    start = Offset(0f, lineY),
                    end = Offset(w, lineY),
                    strokeWidth = strokeWidth
                )
                
                // Left Tick
                drawLine(
                    color = color,
                    start = Offset(strokeWidth / 2, 0f),
                    end = Offset(strokeWidth / 2, h),
                    strokeWidth = strokeWidth
                )
                
                // Right Tick
                drawLine(
                    color = color,
                    start = Offset(w - strokeWidth / 2, 0f),
                    end = Offset(w - strokeWidth / 2, h),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}

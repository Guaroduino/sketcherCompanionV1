package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.VectorStroke
import com.sketcher.sketchercompanionv1.SketcherViewModel

@Composable
fun SmartPickerDialog(
    strokes: List<VectorStroke>,
    viewModel: SketcherViewModel
) {
    Dialog(onDismissRequest = { viewModel.cancelSmartPicker() }) {
        Surface(
            modifier = Modifier.padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Select Stroke", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(strokes) { stroke ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.applySampledStroke(stroke) }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val colorValue = stroke.strokeColor ?: 0xFF000000.toInt()
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(Color(colorValue), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stroke.brushType)
                        }
                    }
                }
            }
        }
    }
}

package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig

val PREDEFINED_SCALES = listOf(
    1f to "1:1",
    10f to "1:10",
    20f to "1:20",
    50f to "1:50",
    100f to "1:100",
    200f to "1:200",
    500f to "1:500",
    1000f to "1:1000"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalScaleDialog(
    viewModel: SketcherViewModel,
    onDismiss: () -> Unit
) {
    val theme by viewModel.themeConfig.collectAsState()
    val currentScaleRatio = viewModel.scaleConfig.globalScaleRatio
    
    var customScaleText by remember { mutableStateOf(if (PREDEFINED_SCALES.any { it.first == currentScaleRatio }) "" else currentScaleRatio.toString()) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor,
                contentColor = theme.iconColor
            ),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = "Escala Global del Proyecto",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = theme.iconColor,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    text = "Selecciona una escala predefinida (1 unidad real = X unidades de dibujo):",
                    fontSize = 14.sp,
                    color = theme.iconColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 200.dp)
                ) {
                    items(PREDEFINED_SCALES) { scale ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = (currentScaleRatio == scale.first),
                                onClick = {
                                    viewModel.updateGlobalScaleRatio(scale.first)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = theme.highlightColor,
                                    unselectedColor = theme.iconColor.copy(alpha = 0.5f)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = scale.second, color = theme.iconColor, fontSize = 16.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = theme.iconColor.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Escala personalizada:",
                    fontSize = 14.sp,
                    color = theme.iconColor.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = customScaleText,
                    onValueChange = { customScaleText = it },
                    label = { Text("Factor de escala (ej. 75)", color = theme.iconColor.copy(alpha = 0.5f)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = theme.iconColor,
                        unfocusedTextColor = theme.iconColor,
                        focusedBorderColor = theme.highlightColor,
                        unfocusedBorderColor = theme.iconColor.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar", color = theme.iconColor)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val ratio = customScaleText.toFloatOrNull()
                            if (ratio != null && ratio > 0) {
                                viewModel.updateGlobalScaleRatio(ratio)
                                onDismiss()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = theme.highlightColor,
                            contentColor = androidx.compose.ui.graphics.Color.White
                        )
                    ) {
                        Text("Aplicar")
                    }
                }
            }
        }
    }
}

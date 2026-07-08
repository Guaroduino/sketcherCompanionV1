package com.sketcher.sketchercompanionv1.ui.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.ui.theme.UiThemeConfig
import com.sketcher.sketchercompanionv1.ui.theme.sdp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Settings

@Composable
fun GridMenuDialog(
    theme: UiThemeConfig,
    onDismiss: () -> Unit,
    viewModel: SketcherViewModel,
    onEditGrid: () -> Unit
) {
    val scaler = com.sketcher.sketchercompanionv1.ui.theme.LocalUiScaler.current
    val gridConfig = viewModel.gridConfig
    val isSnap = viewModel.isSnapToGridEnabled
    val isVisible = gridConfig.isVisible

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .padding(16.sdp)
                .fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = theme.barBackgroundColor.copy(alpha = 0.98f),
                contentColor = theme.iconColor
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, theme.iconColor.copy(alpha = 0.1f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.sdp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.sdp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Configuración de Grid",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = theme.iconColor
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.sdp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = theme.iconColor)
                    }
                }

                HorizontalDivider(color = theme.iconColor.copy(alpha = 0.15f))

                // --- SNAP TO GRID ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.isSnapToGridEnabled = !isSnap
                        }
                        .padding(horizontal = 12.sdp, vertical = 8.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.sdp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FilterCenterFocus,
                            contentDescription = null,
                            tint = theme.iconColor,
                            modifier = Modifier.size(24.sdp)
                        )
                        Text(
                            text = "Snap to Grid",
                            fontSize = 14.sp,
                            color = theme.iconColor
                        )
                    }
                    Switch(
                        checked = isSnap,
                        onCheckedChange = { viewModel.isSnapToGridEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.highlightColor
                        )
                    )
                }

                // --- SHOW GRID ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            viewModel.gridConfig = gridConfig.copy(isVisible = !isVisible)
                        }
                        .padding(horizontal = 12.sdp, vertical = 8.sdp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.sdp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridOn,
                            contentDescription = null,
                            tint = theme.iconColor,
                            modifier = Modifier.size(24.sdp)
                        )
                        Text(
                            text = "Mostrar Grid",
                            fontSize = 14.sp,
                            color = theme.iconColor
                        )
                    }
                    Switch(
                        checked = isVisible,
                        onCheckedChange = { viewModel.gridConfig = gridConfig.copy(isVisible = it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = theme.highlightColor
                        )
                    )
                }

                // --- EDIT GRID BUTTON ---
                Button(
                    onClick = onEditGrid,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = theme.highlightColor,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(16.sdp)
                    )
                    Spacer(modifier = Modifier.width(6.sdp))
                    Text("Editar Grid...", fontSize = 14.sp)
                }
            }
        }
    }
}

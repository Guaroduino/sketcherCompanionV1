package com.sketcher.sketchercompanionv1.ui.dialogs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sketcher.sketchercompanionv1.SketcherViewModel
import com.sketcher.sketchercompanionv1.dto.ExportPngConfig
import com.sketcher.sketchercompanionv1.dto.ImageEditState
import com.sketcher.sketchercompanionv1.network.PollResult
import com.sketcher.sketchercompanionv1.network.RenderApiClient
import com.sketcher.sketchercompanionv1.ui.SettingSlider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RenderOptionsBottomSheet(
    viewModel: SketcherViewModel,
    currentUserUid: String?,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val apiClient = remember { RenderApiClient() }

    // SharedPreferences setup for Server URL persistence
    val prefs = remember { context.getSharedPreferences("sketcher_prefs", Context.MODE_PRIVATE) }
    var serverUrl by remember {
        mutableStateOf(prefs.getString("sketcher_render_server_url", "http://192.168.0.109:3002") ?: "http://192.168.0.109:3002")
    }

    // Input States
    var exportSelectionOnly by remember { mutableStateOf(!viewModel.selectionManager.selectedElements.isEmpty()) }
    var isMultiView by remember { mutableStateOf(false) }
    var viewCount by remember { mutableStateOf(3) } // 2 to 5

    // Single View references
    var refImage1 by remember { mutableStateOf<Uri?>(null) }
    var refImage2 by remember { mutableStateOf<Uri?>(null) }

    // Multi-View arrays
    var multiSketches by remember { mutableStateOf(List(5) { null as Uri? }) }
    var multiRefs by remember { mutableStateOf(List(5) { null as Uri? }) }
    var multiPrompts by remember { mutableStateOf(List(5) { "" }) }

    // Common fields
    var prompt by remember { mutableStateOf("") }
    var sceneType by remember { mutableStateOf("interior") }
    var spaceType by remember { mutableStateOf("living_room") }
    var denoiseValue by remember { mutableStateOf(0.85f) }

    // Presets
    var stylePreset by remember { mutableStateOf("default") }
    var lightingPreset by remember { mutableStateOf("default") }
    var colorPreset by remember { mutableStateOf("default") }

    // UI State
    var showAdvanced by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var progressVal by remember { mutableStateOf(0) }
    var progressMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Gallery Picker controller
    var activePickIndex by remember { mutableStateOf(-1) }
    var activePickType by remember { mutableStateOf("") } // "ref1", "ref2", "multi_sketch", "multi_ref"

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            when (activePickType) {
                "ref1" -> refImage1 = uri
                "ref2" -> refImage2 = uri
                "multi_sketch" -> {
                    if (activePickIndex in 0 until 5) {
                        multiSketches = multiSketches.toMutableList().apply { set(activePickIndex, uri) }
                    }
                }
                "multi_ref" -> {
                    if (activePickIndex in 0 until 5) {
                        multiRefs = multiRefs.toMutableList().apply { set(activePickIndex, uri) }
                    }
                }
            }
        }
        activePickIndex = -1
        activePickType = ""
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isLoading) onDismiss() },
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Generar Render Realista",
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = onDismiss, enabled = !isLoading) {
                    Icon(Icons.Default.Close, contentDescription = "Cerrar")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Server URL
            OutlinedTextField(
                value = serverUrl,
                onValueChange = {
                    serverUrl = it
                    prefs.edit().putString("sketcher_render_server_url", it).apply()
                },
                label = { Text("IP del Servidor (URL Base)") },
                placeholder = { Text("http://192.168.0.109:3002") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Canvas capture source selection
            Text("Origen de la imagen del lienzo", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !exportSelectionOnly,
                    onClick = { exportSelectionOnly = false },
                    enabled = !isLoading
                )
                Text("Todo el lienzo", modifier = Modifier.clickable { if (!isLoading) exportSelectionOnly = false })

                Spacer(modifier = Modifier.width(16.dp))

                val hasSelection = viewModel.selectionManager.selectedElements.isNotEmpty()
                RadioButton(
                    selected = exportSelectionOnly,
                    onClick = { exportSelectionOnly = true },
                    enabled = !isLoading && hasSelection
                )
                Text(
                    text = "Selección actual" + if (!hasSelection) " (Sin selección)" else "",
                    color = if (hasSelection) Color.Unspecified else Color.Gray,
                    modifier = Modifier.clickable(enabled = hasSelection) {
                        if (!isLoading) exportSelectionOnly = true
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Render Mode Selector
            Text("Modo de Renderizado", style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = !isMultiView,
                    onClick = { isMultiView = false },
                    enabled = !isLoading
                )
                Text("Vista Única", modifier = Modifier.clickable { if (!isLoading) isMultiView = false })

                Spacer(modifier = Modifier.width(24.dp))

                RadioButton(
                    selected = isMultiView,
                    onClick = { isMultiView = true },
                    enabled = !isLoading
                )
                Text("Múltiples Vistas", modifier = Modifier.clickable { if (!isLoading) isMultiView = true })
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Mode Configurations
            if (!isMultiView) {
                // SINGLE VIEW CONFIG
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt Principal de la Escena *") },
                    placeholder = { Text("ej: Un living moderno minimalista, render fotorealista, luz cálida de tarde") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !isLoading
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text("Imágenes de Referencia (Opcional, máx. 2)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        ReferenceImageSlot(
                            uri = refImage1,
                            label = "Añadir Ref 1",
                            onPick = {
                                activePickType = "ref1"
                                galleryLauncher.launch("image/*")
                            },
                            onDelete = { refImage1 = null },
                            enabled = !isLoading
                        )
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        ReferenceImageSlot(
                            uri = refImage2,
                            label = "Añadir Ref 2",
                            onPick = {
                                activePickType = "ref2"
                                galleryLauncher.launch("image/*")
                            },
                            onDelete = { refImage2 = null },
                            enabled = !isLoading
                        )
                    }
                }
            } else {
                // MULTI VIEW CONFIG
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Cantidad de Vistas: $viewCount", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        (2..5).forEach { num ->
                            ElevatedFilterChip(
                                selected = viewCount == num,
                                onClick = { viewCount = num },
                                label = { Text(num.toString()) },
                                enabled = !isLoading,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text("Configuración de Vistas (Bocetos desde la Galería)", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(6.dp))

                (0 until viewCount).forEach { index ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Vista ${index + 1}", style = MaterialTheme.typography.titleMedium)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ReferenceImageSlot(
                                        uri = multiSketches[index],
                                        label = "Boceto *",
                                        onPick = {
                                            activePickType = "multi_sketch"
                                            activePickIndex = index
                                            galleryLauncher.launch("image/*")
                                        },
                                        onDelete = {
                                            multiSketches = multiSketches.toMutableList().apply { set(index, null) }
                                        },
                                        enabled = !isLoading
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    ReferenceImageSlot(
                                        uri = multiRefs[index],
                                        label = "Referencia",
                                        onPick = {
                                            activePickType = "multi_ref"
                                            activePickIndex = index
                                            galleryLauncher.launch("image/*")
                                        },
                                        onDelete = {
                                            multiRefs = multiRefs.toMutableList().apply { set(index, null) }
                                        },
                                        enabled = !isLoading
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = multiPrompts[index],
                                onValueChange = { text ->
                                    multiPrompts = multiPrompts.toMutableList().apply { set(index, text) }
                                },
                                label = { Text("Prompt Individual (Opcional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isLoading
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Scene and Space selectors
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SimpleDropdown(
                        label = "Tipo Escena",
                        options = listOf("interior", "exterior"),
                        selected = sceneType,
                        onSelected = { sceneType = it },
                        enabled = !isLoading
                    )
                }
                Box(modifier = Modifier.weight(1f)) {
                    val spaces = if (sceneType == "interior") {
                        listOf("living_room", "bedroom", "kitchen", "bathroom", "office")
                    } else {
                        listOf("house", "villa", "facade", "garden", "pavilion")
                    }
                    if (spaceType !in spaces) spaceType = spaces.first()

                    SimpleDropdown(
                        label = "Espacio",
                        options = spaces,
                        selected = spaceType,
                        onSelected = { spaceType = it },
                        enabled = !isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Advanced settings toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!isLoading) showAdvanced = !showAdvanced }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Ajustes Avanzados", style = MaterialTheme.typography.titleMedium)
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Desplegar avanzados"
                )
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingSlider(
                        label = "Fuerza de redibujado (Denoise): ${String.format("%.2f", denoiseValue)}",
                        value = denoiseValue,
                        valueRange = 0.0f..1.0f,
                        onValueChange = { denoiseValue = it },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        showValueOnRight = false
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            SimpleDropdown(
                                label = "Estilo",
                                options = listOf("default", "minimalist", "industrial", "rustic", "modern"),
                                selected = stylePreset,
                                onSelected = { stylePreset = it },
                                enabled = !isLoading
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            SimpleDropdown(
                                label = "Iluminación",
                                options = listOf("default", "golden_hour", "sunlight", "cozy_warm", "studio"),
                                selected = lightingPreset,
                                onSelected = { lightingPreset = it },
                                enabled = !isLoading
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    SimpleDropdown(
                        label = "Preset de Color",
                        options = listOf("default", "editorial", "cinematic", "vintage", "bw"),
                        selected = colorPreset,
                        onSelected = { colorPreset = it },
                        enabled = !isLoading
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Progress Indicators
            if (isLoading) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    LinearProgressIndicator(
                        progress = { progressVal / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Progreso: $progressVal% - $progressMessage",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Submit Button
            Button(
                onClick = {
                    if (isMultiView) {
                        // Validate sketches count
                        val actualSketches = multiSketches.take(viewCount)
                        if (actualSketches.any { it == null }) {
                            errorMessage = "Debes cargar un boceto para cada vista obligatoriamente."
                            return@Button
                        }
                    } else {
                        if (prompt.isBlank()) {
                            errorMessage = "Debes introducir un prompt para la escena."
                            return@Button
                        }
                    }

                    isLoading = true
                    errorMessage = null
                    progressVal = 0
                    progressMessage = "Capturando lienzo..."

                    coroutineScope.launch {
                        try {
                            // 1. Capture Canvas Image
                            val canvasBitmap = if (exportSelectionOnly) {
                                viewModel.renderSelectionExportBitmap(transparent = false, maxDimension = 1024)
                            } else {
                                // Default to whole page rendering
                                viewModel.renderExportBitmap(
                                    config = ExportPngConfig(
                                        transparentBackground = false,
                                        useHomeView = false,
                                        width = 1024,
                                        height = 1024
                                    )
                                )
                            }

                            if (canvasBitmap == null) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    errorMessage = "Error al capturar el lienzo (verifica tu selección)."
                                }
                                return@launch
                            }

                            val stream = ByteArrayOutputStream()
                            canvasBitmap.compress(Bitmap.CompressFormat.PNG, 95, stream)
                            val canvasBytes = stream.toByteArray()
                            canvasBitmap.recycle()

                            progressMessage = "Subiendo imagen al servidor..."
                            progressVal = 10

                            // 2. Submit Render Request
                            val response = apiClient.sendRenderRequest(
                                context = context,
                                baseUrl = serverUrl,
                                canvasImageBytes = canvasBytes,
                                refImages = listOfNotNull(refImage1, refImage2),
                                renderMode = if (isMultiView) "three" else "single",
                                prompt = prompt,
                                sceneType = sceneType,
                                spaceType = spaceType,
                                sketchDenoise = denoiseValue.toString(),
                                userId = currentUserUid ?: "anonymous_user",
                                stylePreset = if (stylePreset == "default") null else stylePreset,
                                lightingPreset = if (lightingPreset == "default") null else lightingPreset,
                                colorPreset = if (colorPreset == "default") null else colorPreset,
                                multiViewSketches = multiSketches.take(viewCount).filterNotNull(),
                                multiViewReferences = multiRefs.take(viewCount),
                                multiViewPrompts = multiPrompts.take(viewCount)
                            )

                            if (!response.success || response.jobId == null) {
                                withContext(Dispatchers.Main) {
                                    isLoading = false
                                    errorMessage = "Servidor: ${response.error ?: "Error al crear el trabajo"}"
                                }
                                return@launch
                            }

                            val jobId = response.jobId
                            progressMessage = "Trabajo creado: $jobId. Esperando en cola..."
                            progressVal = 20

                            // 3. Polling for Status
                            val pollResult = apiClient.pollRenderStatus(
                                baseUrl = serverUrl,
                                jobId = jobId
                            ) { progress, message ->
                                progressVal = (20 + (progress * 0.6f)).toInt().coerceIn(20, 80)
                                progressMessage = message
                            }

                            when (pollResult) {
                                is PollResult.Error -> {
                                    withContext(Dispatchers.Main) {
                                        isLoading = false
                                        errorMessage = pollResult.message
                                    }
                                }
                                is PollResult.Success -> {
                                    progressMessage = "Descargando imagen renderizada..."
                                    progressVal = 85

                                    // Pick first completed image to import
                                    val imageUrl = if (isMultiView) {
                                        pollResult.imageUrls["image1"]
                                    } else {
                                        pollResult.imageUrls["image"]
                                    }

                                    if (imageUrl != null) {
                                        val renderedBitmap = apiClient.downloadImage(imageUrl)
                                        if (renderedBitmap != null) {
                                            withContext(Dispatchers.Main) {
                                                progressVal = 100
                                                isLoading = false
                                                Toast.makeText(context, "Render completado con éxito!", Toast.LENGTH_SHORT).show()
                                                viewModel.importRenderedBitmap(renderedBitmap, "render_${jobId}.png")
                                                onDismiss()
                                            }
                                        } else {
                                            withContext(Dispatchers.Main) {
                                                isLoading = false
                                                errorMessage = "Error al descargar la imagen renderizada final."
                                            }
                                        }
                                    } else {
                                        withContext(Dispatchers.Main) {
                                            isLoading = false
                                            errorMessage = "La respuesta del servidor no contiene una URL de imagen válida."
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                            withContext(Dispatchers.Main) {
                                isLoading = false
                                errorMessage = e.localizedMessage ?: "Ocurrió una excepción durante el proceso."
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp).padding(vertical = 4.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(24.dp))
                } else {
                    Text("Enviar a Renderizar", fontSize = 16.sp)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun ReferenceImageSlot(
    uri: Uri?,
    label: String,
    onPick: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean
) {
    val context = LocalContext.current
    val imageBitmap = rememberUriImage(context, uri)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled && uri == null) { onPick() },
        contentAlignment = Alignment.Center
    ) {
        if (uri == null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
            }

            if (enabled) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(28.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Borrar",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun rememberUriImage(context: Context, uri: Uri?): ImageBitmap? {
    if (uri == null) return null
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val raw = BitmapFactory.decodeStream(stream)
                    if (raw != null) {
                        val scaled = Bitmap.createScaledBitmap(raw, 256, (256f * (raw.height.toFloat() / raw.width.toFloat())).toInt(), true)
                        bitmap = scaled.asImageBitmap()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    return bitmap
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelected: (String) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier.menuAnchor().fillMaxWidth(),
            enabled = enabled
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

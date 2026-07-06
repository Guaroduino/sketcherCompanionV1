package com.sketcher.sketchercompanionv1.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

data class RenderJobResponse(
    val success: Boolean,
    val jobId: String?,
    val error: String?
)

data class PollResponse(
    val success: Boolean,
    val jobId: String,
    val estado: String, // 'pendiente', 'procesando', 'completado', 'error'
    val progreso: Int,
    val progresoMsg: String,
    val imagenesSalida: Map<String, String>?,
    val error: String?
)

sealed class PollResult {
    data class Success(val imageUrls: Map<String, String>) : PollResult()
    data class Error(val message: String) : PollResult()
}

class RenderApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private fun readUriBytes(context: Context, uri: Uri): ByteArray? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    suspend fun sendRenderRequest(
        context: Context,
        baseUrl: String,
        canvasImageBytes: ByteArray,
        refImages: List<Uri>,
        renderMode: String,
        prompt: String,
        sceneType: String,
        spaceType: String,
        sketchDenoise: String,
        userId: String,
        stylePreset: String?,
        lightingPreset: String?,
        colorPreset: String?,
        multiViewSketches: List<Uri> = emptyList(),
        multiViewReferences: List<Uri?> = emptyList(),
        multiViewPrompts: List<String> = emptyList()
    ): RenderJobResponse = withContext(Dispatchers.IO) {
        try {
            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("renderMode", renderMode)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("sceneType", sceneType)
                .addFormDataPart("spaceType", spaceType)
                .addFormDataPart("sketchDenoise", sketchDenoise)
                .addFormDataPart("userId", userId)

            if (!stylePreset.isNullOrBlank()) builder.addFormDataPart("stylePreset", stylePreset)
            if (!lightingPreset.isNullOrBlank()) builder.addFormDataPart("lightingPreset", lightingPreset)
            if (!colorPreset.isNullOrBlank()) builder.addFormDataPart("colorPreset", colorPreset)

            // 1. Single View Canvas Image (sent as "image")
            val imageBody = canvasImageBytes.toRequestBody("image/png".toMediaTypeOrNull())
            builder.addFormDataPart("image", "canvas_sketch.png", imageBody)

            // 2. Single View Reference Images
            refImages.forEachIndexed { index, uri ->
                val bytes = readUriBytes(context, uri)
                if (bytes != null) {
                    val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                    builder.addFormDataPart("refImage${index + 1}", "ref_${index + 1}.jpg", body)
                }
            }

            // 3. Multi-View Sketches and References
            if (renderMode != "single") {
                multiViewSketches.forEachIndexed { index, uri ->
                    val bytes = readUriBytes(context, uri)
                    if (bytes != null) {
                        val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                        builder.addFormDataPart("sketch${index + 1}", "sketch_${index + 1}.jpg", body)
                    }
                }

                multiViewReferences.forEachIndexed { index, uri ->
                    if (uri != null) {
                        val bytes = readUriBytes(context, uri)
                        if (bytes != null) {
                            val body = bytes.toRequestBody("image/jpeg".toMediaTypeOrNull())
                            builder.addFormDataPart("refImage${index + 1}", "ref_${index + 1}.jpg", body)
                        }
                    }
                }

                multiViewPrompts.forEachIndexed { index, p ->
                    if (p.isNotBlank()) {
                        builder.addFormDataPart("prompt${index + 1}", p)
                    }
                }
            }

            val requestBody = builder.build()
            val sanitizedBaseUrl = baseUrl.trim().removeSuffix("/")
            val request = Request.Builder()
                .url("$sanitizedBaseUrl/api/generate")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    gson.fromJson(bodyString, RenderJobResponse::class.java)
                } else {
                    val errString = response.body?.string() ?: "Error de red desconocido"
                    RenderJobResponse(success = false, jobId = null, error = "HTTP ${response.code}: $errString")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RenderJobResponse(success = false, jobId = null, error = e.localizedMessage ?: "Excepción de red")
        }
    }

    suspend fun pollRenderStatus(
        baseUrl: String,
        jobId: String,
        onProgress: (Int, String) -> Unit
    ): PollResult = withContext(Dispatchers.IO) {
        val sanitizedBaseUrl = baseUrl.trim().removeSuffix("/")
        val request = Request.Builder()
            .url("$sanitizedBaseUrl/api/check-job/$jobId")
            .get()
            .build()

        var attempts = 0
        val maxAttempts = 150 // 150 * 2s = 5 minutes max polling time

        while (attempts < maxAttempts) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val bodyString = response.body?.string() ?: ""
                        val pollData = gson.fromJson(bodyString, PollResponse::class.java)

                        if (pollData.success) {
                            onProgress(pollData.progreso, pollData.progresoMsg)
                            when (pollData.estado) {
                                "completado" -> {
                                    val images = pollData.imagenesSalida ?: emptyMap()
                                    return@withContext PollResult.Success(images)
                                }
                                "error" -> {
                                    return@withContext PollResult.Error(pollData.error ?: "Error reportado por el servidor.")
                                }
                                else -> {
                                    // Continúa en bucle para 'pendiente' o 'procesando'
                                }
                            }
                        } else {
                            return@withContext PollResult.Error(pollData.error ?: "Error al consultar estado.")
                        }
                    } else {
                        // Error de HTTP temporal
                    }
                }
            } catch (e: IOException) {
                // Posible desconexión transitoria
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext PollResult.Error(e.localizedMessage ?: "Error en el polling")
            }

            attempts++
            delay(2000)
        }

        PollResult.Error("Tiempo de espera agotado. El renderizado está tardando demasiado.")
    }

    suspend fun downloadImage(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(imageUrl).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.byteStream()?.use { inputStream ->
                        BitmapFactory.decodeStream(inputStream)
                    }
                } else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

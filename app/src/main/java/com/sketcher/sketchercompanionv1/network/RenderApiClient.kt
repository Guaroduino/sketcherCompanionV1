package com.sketcher.sketchercompanionv1.network

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class GenerateResponse(
    val success: Boolean,
    val jobId: String
)

data class RenderResult(
    val jobId: String?,
    val error: String?
)

data class ErrorResponse(
    val success: Boolean,
    val error: String?
)

data class JobStatusResponse(
    val success: Boolean,
    val jobId: String,
    val estado: String,                // "pendiente", "procesando", "completado", "error"
    val progreso: Int,
    val progresoMsg: String,
    val imagenesSalida: Map<String, String>?, // Contiene la URL del render final
    val error: String?
)

class RenderApiClient(private val baseUrl: String) {

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
        bitmap: Bitmap,
        refImages: List<Uri>,
        renderMode: String,
        prompt: String,
        sceneType: String,
        spaceType: String,
        denoise: Float,
        idToken: String,
        userId: String,
        stylePreset: String?,
        lightingPreset: String?,
        colorPreset: String?,
        multiViewSketches: List<Uri> = emptyList(),
        multiViewReferences: List<Uri?> = emptyList(),
        multiViewPrompts: List<String> = emptyList()
    ): RenderResult = withContext(Dispatchers.IO) {
        try {
            // Compress bitmap in memory to JPEG
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)
            val byteArray = stream.toByteArray()

            val builder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("renderMode", renderMode)
                .addFormDataPart("prompt", prompt)
                .addFormDataPart("sceneType", sceneType)
                .addFormDataPart("spaceType", spaceType)
                .addFormDataPart("sketchDenoise", denoise.toString())
                .addFormDataPart("userId", userId)

            if (!stylePreset.isNullOrBlank()) builder.addFormDataPart("stylePreset", stylePreset)
            if (!lightingPreset.isNullOrBlank()) builder.addFormDataPart("lightingPreset", lightingPreset)
            if (!colorPreset.isNullOrBlank()) builder.addFormDataPart("colorPreset", colorPreset)

            // 1. Single View Canvas Image (sent as "image")
            val imageBody = byteArray.toRequestBody("image/jpeg".toMediaTypeOrNull(), 0, byteArray.size)
            builder.addFormDataPart("image", "sketch.jpg", imageBody)

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
                .addHeader("Authorization", "Bearer $idToken")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                if (!response.isSuccessful) {
                    val errMsg = try {
                        val errObj = gson.fromJson(bodyString, ErrorResponse::class.java)
                        errObj.error ?: "Error de red: Código ${response.code}"
                    } catch (e: Exception) {
                        "Error de red: Código ${response.code}"
                    }
                    return@withContext RenderResult(null, errMsg)
                }
                if (bodyString == null) return@withContext RenderResult(null, "Respuesta vacía del servidor.")
                val result = gson.fromJson(bodyString, GenerateResponse::class.java)
                return@withContext if (result.success) {
                    RenderResult(result.jobId, null)
                } else {
                    RenderResult(null, "El servidor no devolvió un jobId exitoso.")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            RenderResult(null, e.localizedMessage ?: "Excepción de red al conectar al servidor.")
        }
    }

    suspend fun checkJobStatus(
        jobId: String,
        idToken: String
    ): JobStatusResponse? = withContext(Dispatchers.IO) {
        val sanitizedBaseUrl = baseUrl.trim().removeSuffix("/")
        val request = Request.Builder()
            .url("$sanitizedBaseUrl/api/check-job/$jobId")
            .addHeader("Authorization", "Bearer $idToken")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                return@withContext gson.fromJson(bodyString, JobStatusResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
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

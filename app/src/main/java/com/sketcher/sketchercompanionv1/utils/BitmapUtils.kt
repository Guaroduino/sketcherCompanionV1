package com.sketcher.sketchercompanionv1.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

object BitmapUtils {

    /**
     * Resolves the actual filename of a Uri (e.g. from ContentResolver openable columns).
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    /**
     * Loads a bitmap from a URI, scaling it down if necessary to fit within maxDimension.
     * Ensures ARGB_8888 config is used to preserve transparency.
     */
    fun loadScaledBitmap(context: Context, uri: Uri, maxDimension: Int = 2048): Bitmap? {
        return try {
            // Read orientation first
            var rotationDegrees = 0
            context.contentResolver.openInputStream(uri)?.use { stream ->
                try {
                    val exif = android.media.ExifInterface(stream)
                    val orientation = exif.getAttributeInt(
                        android.media.ExifInterface.TAG_ORIENTATION,
                        android.media.ExifInterface.ORIENTATION_NORMAL
                    )
                    rotationDegrees = when (orientation) {
                        android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                        android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                        android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                        else -> 0
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // 1. Decode bounds only
            var inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options()
            options.inJustDecodeBounds = true
            BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            // 2. Calculate inSampleSize
            options.inSampleSize = calculateInSampleSize(options, maxDimension, maxDimension)

            // 3. Decode with ARGB_8888
            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            
            // Re-open stream
            inputStream = context.contentResolver.openInputStream(uri)
            var bitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream?.close()

            if (bitmap != null && rotationDegrees != 0) {
                val matrix = android.graphics.Matrix()
                matrix.postRotate(rotationDegrees.toFloat())
                val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                if (rotated != bitmap) {
                    bitmap.recycle()
                    bitmap = rotated
                }
            }

            bitmap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.run { outHeight to outWidth }
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}


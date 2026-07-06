package com.trin.stonks.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun compressImageFromUri(context: Context, uri: Uri): ByteArray {
        // 1. Get Orientation from EXIF data
        val orientation = context.contentResolver.openInputStream(uri)?.use { inputStream ->
            ExifInterface(inputStream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
        } ?: ExifInterface.ORIENTATION_NORMAL

        // 2. Decode the Bitmap
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw Exception("Could not open the image file.")

        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        if (originalBitmap == null) throw Exception("Could not decode image format.")

        // 3. Apply Rotation based on EXIF
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        }

        val rotatedBitmap = Bitmap.createBitmap(
            originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
        )

        // 4. Scale and Compress
        val maxSize = 800
        val bitmapRatio = rotatedBitmap.width.toFloat() / rotatedBitmap.height.toFloat()
        val width: Int
        val height: Int

        if (bitmapRatio > 1) {
            width = maxSize
            height = (maxSize / bitmapRatio).toInt()
        } else {
            height = maxSize
            width = (maxSize * bitmapRatio).toInt()
        }

        val scaledBitmap = Bitmap.createScaledBitmap(rotatedBitmap, width, height, true)
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)

        // Cleanup
        originalBitmap.recycle()
        rotatedBitmap.recycle()
        scaledBitmap.recycle()

        return outputStream.toByteArray()
    }
}
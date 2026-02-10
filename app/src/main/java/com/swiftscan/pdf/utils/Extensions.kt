package com.swiftscan.pdf.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.widget.Toast
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun Context.toast(message: String, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, message, duration).show()
}

fun Long.toFormattedDate(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFormattedDateTime(): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    return sdf.format(Date(this))
}

fun Long.toFileSizeString(): String = when {
    this < 1024 -> "$this B"
    this < 1024 * 1024 -> "%.1f KB".format(this / 1024.0)
    this < 1024 * 1024 * 1024 -> "%.1f MB".format(this / (1024.0 * 1024.0))
    else -> "%.1f GB".format(this / (1024.0 * 1024.0 * 1024.0))
}

fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}

fun Context.decodeBitmapFromUri(uri: Uri, maxDimension: Int = 2048): Bitmap? {
    // Decode bounds
    val inputStream = contentResolver.openInputStream(uri) ?: return null
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeStream(inputStream, null, options)
    inputStream.close()

    val scale = maxOf(1, maxOf(options.outWidth, options.outHeight) / maxDimension)
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
    val stream = contentResolver.openInputStream(uri) ?: return null
    val bitmap = BitmapFactory.decodeStream(stream, null, decodeOptions) ?: run {
        stream.close()
        return null
    }
    stream.close()

    // Handle EXIF rotation
    val rotation = try {
        val exifStream = contentResolver.openInputStream(uri) ?: return bitmap
        val exif = ExifInterface(exifStream)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        exifStream.close()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } catch (_: Exception) {
        0f
    }

    return if (rotation != 0f) bitmap.rotate(rotation) else bitmap
}

fun File.toBitmap(maxDimension: Int = 2048): Bitmap? {
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(absolutePath, options)
    val scale = maxOf(1, maxOf(options.outWidth, options.outHeight) / maxDimension)
    val decodeOptions = BitmapFactory.Options().apply { inSampleSize = scale }
    return BitmapFactory.decodeFile(absolutePath, decodeOptions)
}

fun generateTimestampName(): String {
    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    return "Scan_${sdf.format(Date())}"
}

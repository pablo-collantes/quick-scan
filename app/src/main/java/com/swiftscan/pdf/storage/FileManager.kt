package com.swiftscan.pdf.storage

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FileManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scansDir: File
        get() = File(context.filesDir, "scans").also { it.mkdirs() }

    private val thumbnailsDir: File
        get() = File(context.cacheDir, "thumbnails").also { it.mkdirs() }

    private val tempDir: File
        get() = File(context.cacheDir, "temp").also { it.mkdirs() }

    suspend fun savePdf(name: String, pdfBytes: ByteArray): File = withContext(Dispatchers.IO) {
        val file = File(scansDir, "${sanitizeFilename(name)}.pdf")
        file.writeBytes(pdfBytes)
        file
    }

    suspend fun saveBitmap(bitmap: Bitmap, name: String, quality: Int = 90): File =
        withContext(Dispatchers.IO) {
            val file = File(scansDir, "${sanitizeFilename(name)}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            file
        }

    suspend fun saveThumbnail(bitmap: Bitmap, name: String): File = withContext(Dispatchers.IO) {
        val file = File(thumbnailsDir, "${sanitizeFilename(name)}_thumb.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
        }
        file
    }

    suspend fun saveTempBitmap(bitmap: Bitmap, prefix: String = "temp"): File =
        withContext(Dispatchers.IO) {
            val file = File.createTempFile(prefix, ".jpg", tempDir)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            file
        }

    suspend fun copyToExternalUri(sourceFile: File, destinationUri: Uri) =
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(destinationUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }
        }

    suspend fun readBytesFromUri(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Cannot read from URI: $uri")
    }

    fun scansDirectory(): File = scansDir

    fun getScanFile(filename: String): File = File(scansDir, filename)

    suspend fun deleteFile(file: File): Boolean = withContext(Dispatchers.IO) {
        file.delete()
    }

    suspend fun deleteFile(path: String): Boolean = deleteFile(File(path))

    fun clearTemp() {
        tempDir.listFiles()?.forEach { it.delete() }
    }

    private fun sanitizeFilename(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(200)
}

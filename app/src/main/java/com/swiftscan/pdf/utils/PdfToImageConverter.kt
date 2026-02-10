package com.swiftscan.pdf.utils

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfToImageConverter @Inject constructor() {

    suspend fun convertToImages(
        pdfFile: File,
        dpi: Int = 300,
    ): List<Bitmap> = withContext(Dispatchers.IO) {
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)
        val bitmaps = mutableListOf<Bitmap>()

        try {
            val scaleFactor = dpi / 72f
            for (i in 0 until renderer.pageCount) {
                val page = renderer.openPage(i)
                val width = (page.width * scaleFactor).toInt()
                val height = (page.height * scaleFactor).toInt()
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()
                bitmaps.add(bitmap)
            }
        } finally {
            renderer.close()
            fileDescriptor.close()
        }

        bitmaps
    }

    suspend fun convertPageToImage(
        pdfFile: File,
        pageIndex: Int,
        dpi: Int = 150,
    ): Bitmap? = withContext(Dispatchers.IO) {
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)

        try {
            if (pageIndex < 0 || pageIndex >= renderer.pageCount) return@withContext null
            val scaleFactor = dpi / 72f
            val page = renderer.openPage(pageIndex)
            val width = (page.width * scaleFactor).toInt()
            val height = (page.height * scaleFactor).toInt()
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            bitmap
        } finally {
            renderer.close()
            fileDescriptor.close()
        }
    }

    suspend fun getPageCount(pdfFile: File): Int = withContext(Dispatchers.IO) {
        val fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fileDescriptor)
        val count = renderer.pageCount
        renderer.close()
        fileDescriptor.close()
        count
    }

    suspend fun saveAsJpeg(bitmap: Bitmap, outputFile: File, quality: Int = 90) =
        withContext(Dispatchers.IO) {
            FileOutputStream(outputFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
        }
}

package com.swiftscan.pdf.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.pdf.PdfDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PdfGenerator @Inject constructor() {

    suspend fun generatePdf(pages: List<Bitmap>): ByteArray = withContext(Dispatchers.IO) {
        require(pages.isNotEmpty()) { "At least one page required" }

        val document = PdfDocument()
        try {
            for ((index, bitmap) in pages.withIndex()) {
                val pageInfo = PdfDocument.PageInfo.Builder(
                    bitmap.width, bitmap.height, index + 1
                ).create()
                val page = document.startPage(pageInfo)
                val canvas: Canvas = page.canvas
                canvas.drawBitmap(bitmap, 0f, 0f, null)
                document.finishPage(page)
            }

            val outputStream = ByteArrayOutputStream()
            document.writeTo(outputStream)
            outputStream.toByteArray()
        } finally {
            document.close()
        }
    }

    suspend fun generatePdfToFile(pages: List<Bitmap>, outputFile: File) =
        withContext(Dispatchers.IO) {
            require(pages.isNotEmpty()) { "At least one page required" }

            val document = PdfDocument()
            try {
                for ((index, bitmap) in pages.withIndex()) {
                    val pageInfo = PdfDocument.PageInfo.Builder(
                        bitmap.width, bitmap.height, index + 1
                    ).create()
                    val page = document.startPage(pageInfo)
                    page.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    document.finishPage(page)
                }

                FileOutputStream(outputFile).use { out ->
                    document.writeTo(out)
                }
            } finally {
                document.close()
            }
        }

    suspend fun mergePdfs(pdfFiles: List<File>, outputFile: File) =
        withContext(Dispatchers.IO) {
            val converter = PdfToImageConverter()
            val allPages = mutableListOf<Bitmap>()

            try {
                for (pdfFile in pdfFiles) {
                    val pages = converter.convertToImages(pdfFile)
                    allPages.addAll(pages)
                }
                generatePdfToFile(allPages, outputFile)
            } finally {
                allPages.forEach { it.recycle() }
            }
        }
}

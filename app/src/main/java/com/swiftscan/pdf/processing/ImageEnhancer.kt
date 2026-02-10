package com.swiftscan.pdf.processing

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton

enum class EnhanceMode {
    ORIGINAL,
    GRAYSCALE,
    BLACK_AND_WHITE,
    HIGH_CONTRAST,
}

@Singleton
class ImageEnhancer @Inject constructor() {

    suspend fun enhance(source: Bitmap, mode: EnhanceMode): Bitmap =
        withContext(Dispatchers.Default) {
            when (mode) {
                EnhanceMode.ORIGINAL -> source
                EnhanceMode.GRAYSCALE -> toGrayscale(source)
                EnhanceMode.BLACK_AND_WHITE -> toBlackAndWhite(source)
                EnhanceMode.HIGH_CONTRAST -> toHighContrast(source)
            }
        }

    private fun toGrayscale(source: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(source, srcMat)

        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        val outputMat = Mat()
        Imgproc.cvtColor(grayMat, outputMat, Imgproc.COLOR_GRAY2RGBA)

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, result)

        srcMat.release()
        grayMat.release()
        outputMat.release()
        return result
    }

    private fun toBlackAndWhite(source: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(source, srcMat)

        val grayMat = Mat()
        Imgproc.cvtColor(srcMat, grayMat, Imgproc.COLOR_RGBA2GRAY)

        val threshMat = Mat()
        Imgproc.adaptiveThreshold(
            grayMat, threshMat, 255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            21, 10.0,
        )

        val outputMat = Mat()
        Imgproc.cvtColor(threshMat, outputMat, Imgproc.COLOR_GRAY2RGBA)

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, result)

        srcMat.release()
        grayMat.release()
        threshMat.release()
        outputMat.release()
        return result
    }

    private fun toHighContrast(source: Bitmap): Bitmap {
        val srcMat = Mat()
        Utils.bitmapToMat(source, srcMat)

        val labMat = Mat()
        Imgproc.cvtColor(srcMat, labMat, Imgproc.COLOR_RGBA2RGB)
        val rgbMat = labMat.clone()
        Imgproc.cvtColor(rgbMat, labMat, Imgproc.COLOR_RGB2Lab)

        val channels = mutableListOf<Mat>()
        Core.split(labMat, channels)

        // Apply CLAHE to the L channel
        val clahe = Imgproc.createCLAHE(3.0, Size(8.0, 8.0))
        clahe.apply(channels[0], channels[0])

        Core.merge(channels, labMat)
        Imgproc.cvtColor(labMat, rgbMat, Imgproc.COLOR_Lab2RGB)

        val outputMat = Mat()
        Imgproc.cvtColor(rgbMat, outputMat, Imgproc.COLOR_RGB2RGBA)

        val result = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, result)

        srcMat.release()
        labMat.release()
        rgbMat.release()
        channels.forEach { it.release() }
        outputMat.release()
        return result
    }
}

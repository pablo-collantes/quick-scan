package com.swiftscan.pdf.processing

import android.graphics.Bitmap
import android.graphics.PointF
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class PerspectiveTransform @Inject constructor() {

    suspend fun warpDocument(source: Bitmap, corners: List<PointF>): Bitmap =
        withContext(Dispatchers.Default) {
            require(corners.size == 4) { "Exactly 4 corners required" }

            val srcMat = Mat()
            Utils.bitmapToMat(source, srcMat)

            // Order corners: top-left, top-right, bottom-right, bottom-left
            val ordered = orderCorners(corners)

            val srcPoints = Mat(4, 1, CvType.CV_32FC2)
            for (i in ordered.indices) {
                srcPoints.put(i, 0, ordered[i].x.toDouble(), ordered[i].y.toDouble())
            }

            // Calculate output dimensions from the max edge lengths
            val widthTop = distance(ordered[0], ordered[1])
            val widthBottom = distance(ordered[3], ordered[2])
            val outputWidth = maxOf(widthTop, widthBottom).toInt()

            val heightLeft = distance(ordered[0], ordered[3])
            val heightRight = distance(ordered[1], ordered[2])
            val outputHeight = maxOf(heightLeft, heightRight).toInt()

            val dstPoints = Mat(4, 1, CvType.CV_32FC2)
            dstPoints.put(0, 0, 0.0, 0.0)
            dstPoints.put(1, 0, outputWidth.toDouble(), 0.0)
            dstPoints.put(2, 0, outputWidth.toDouble(), outputHeight.toDouble())
            dstPoints.put(3, 0, 0.0, outputHeight.toDouble())

            val transform = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
            val output = Mat()
            Imgproc.warpPerspective(
                srcMat, output, transform,
                Size(outputWidth.toDouble(), outputHeight.toDouble())
            )

            val result = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(output, result)

            // Release mats
            srcMat.release()
            srcPoints.release()
            dstPoints.release()
            transform.release()
            output.release()

            result
        }

    private fun orderCorners(corners: List<PointF>): List<PointF> {
        // Sort by sum (x+y) to find TL and BR
        val sortedBySum = corners.sortedBy { it.x + it.y }
        val topLeft = sortedBySum.first()
        val bottomRight = sortedBySum.last()

        // Sort by diff (y-x) to find TR and BL
        val sortedByDiff = corners.sortedBy { it.y - it.x }
        val topRight = sortedByDiff.first()
        val bottomLeft = sortedByDiff.last()

        return listOf(topLeft, topRight, bottomRight, bottomLeft)
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt((dx * dx + dy * dy).toDouble()).toFloat()
    }
}

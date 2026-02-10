package com.swiftscan.pdf.camera

import android.content.Context
import android.graphics.PointF
import android.net.Uri
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val analysisExecutor = Executors.newSingleThreadExecutor()

    var isFlashEnabled: Boolean = false
        private set

    var onEdgesDetected: ((List<PointF>?) -> Unit)? = null

    fun toggleFlash(): Boolean {
        isFlashEnabled = !isFlashEnabled
        imageCapture?.flashMode = if (isFlashEnabled) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        return isFlashEnabled
    }

    @Suppress("DEPRECATION")
    suspend fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
    ) = suspendCancellableCoroutine { cont ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                val provider = cameraProviderFuture.get()
                cameraProvider = provider

                // Match aspect ratio across all use cases for consistent FOV
                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .build()
                    .also { it.surfaceProvider = previewView.surfaceProvider }

                val capture = ImageCapture.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(
                        if (isFlashEnabled) ImageCapture.FLASH_MODE_ON
                        else ImageCapture.FLASH_MODE_OFF
                    )
                    .build()
                imageCapture = capture

                val analysis = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { it.setAnalyzer(analysisExecutor, EdgeDetectionAnalyzer()) }

                // FIT_CENTER ensures preview shows exactly what will be captured
                previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                previewView.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    capture,
                    analysis,
                )
                cont.resume(Unit)
            } catch (e: Exception) {
                cont.resumeWithException(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    suspend fun captureImage(): Uri = suspendCancellableCoroutine { cont ->
        val capture = imageCapture
            ?: return@suspendCancellableCoroutine cont.resumeWithException(
                IllegalStateException("Camera not initialized")
            )

        val tempDir = File(context.cacheDir, "captures").also { it.mkdirs() }
        val photoFile = File.createTempFile("capture_", ".jpg", tempDir)
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    cont.resume(Uri.fromFile(photoFile))
                }

                override fun onError(exception: ImageCaptureException) {
                    cont.resumeWithException(exception)
                }
            },
        )
    }

    fun release() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        imageCapture = null
    }

    private inner class EdgeDetectionAnalyzer : ImageAnalysis.Analyzer {
        private var lastAnalyzedTimestamp = 0L

        override fun analyze(imageProxy: ImageProxy) {
            val now = System.currentTimeMillis()
            if (now - lastAnalyzedTimestamp < 150) {
                imageProxy.close()
                return
            }
            lastAnalyzedTimestamp = now

            try {
                val corners = detectDocumentEdges(imageProxy)
                onEdgesDetected?.invoke(corners)
            } catch (_: Exception) {
                onEdgesDetected?.invoke(null)
            } finally {
                imageProxy.close()
            }
        }

        private fun detectDocumentEdges(imageProxy: ImageProxy): List<PointF>? {
            // Extract Y plane as grayscale
            val yPlane = imageProxy.planes[0]
            val yBuffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val width = imageProxy.width
            val height = imageProxy.height

            val yData = ByteArray(height * rowStride)
            yBuffer.rewind()
            yBuffer.get(yData)

            val mat = Mat(height, width, CvType.CV_8UC1)
            // Copy row-by-row to handle row stride padding
            for (row in 0 until height) {
                mat.put(row, 0, yData, row * rowStride, width)
            }

            // Rotate to match display orientation
            val rotated = when (imageProxy.imageInfo.rotationDegrees) {
                90 -> {
                    val m = Mat()
                    Core.rotate(mat, m, Core.ROTATE_90_CLOCKWISE)
                    mat.release()
                    m
                }
                180 -> {
                    val m = Mat()
                    Core.rotate(mat, m, Core.ROTATE_180)
                    mat.release()
                    m
                }
                270 -> {
                    val m = Mat()
                    Core.rotate(mat, m, Core.ROTATE_90_COUNTERCLOCKWISE)
                    mat.release()
                    m
                }
                else -> mat
            }

            val result = findDocumentCorners(rotated)

            // Normalize corners to 0-1 range
            val normalized = result?.map { pt ->
                PointF(pt.x / rotated.cols(), pt.y / rotated.rows())
            }

            rotated.release()
            return normalized
        }

        private fun findDocumentCorners(gray: Mat): List<PointF>? {
            val blurred = Mat()
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)

            val edges = Mat()
            Imgproc.Canny(blurred, edges, 50.0, 150.0)

            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            val dilated = Mat()
            Imgproc.dilate(edges, dilated, kernel)

            val contours = mutableListOf<MatOfPoint>()
            val hierarchy = Mat()
            Imgproc.findContours(
                dilated, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )

            val imageArea = gray.rows().toDouble() * gray.cols().toDouble()
            val minArea = imageArea * 0.1

            var bestApprox: MatOfPoint2f? = null
            var bestArea = 0.0

            for (contour in contours) {
                val area = Imgproc.contourArea(contour)
                if (area < minArea) continue

                val contour2f = MatOfPoint2f(*contour.toArray())
                val peri = Imgproc.arcLength(contour2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)

                if (approx.toArray().size == 4 && area > bestArea) {
                    bestApprox?.release()
                    bestApprox = approx
                    bestArea = area
                } else {
                    approx.release()
                }
                contour2f.release()
            }

            blurred.release()
            edges.release()
            kernel.release()
            dilated.release()
            hierarchy.release()
            contours.forEach { it.release() }

            if (bestApprox == null) return null

            val points = bestApprox.toArray().map { PointF(it.x.toFloat(), it.y.toFloat()) }
            bestApprox.release()
            return points
        }
    }
}

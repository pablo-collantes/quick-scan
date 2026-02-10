package com.swiftscan.pdf.ui.crop

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.swiftscan.pdf.ui.theme.CropGreen

@Composable
fun MagnifierOverlay(
    bitmap: Bitmap?,
    touchPoint: Offset,
    imageOffset: Offset,
    imageScale: Float,
    modifier: Modifier = Modifier,
    magnification: Float = 2f,
    magnifierRadius: Float = 200f,
) {
    if (bitmap == null) return

    val imageBitmap = bitmap.asImageBitmap()

    Canvas(modifier = modifier.size((magnifierRadius * 2 / 3).dp)) {
        val centerX = magnifierRadius
        val centerY = magnifierRadius

        // Calculate source region in bitmap coordinates
        val bitmapX = ((touchPoint.x - imageOffset.x) / imageScale).toInt()
        val bitmapY = ((touchPoint.y - imageOffset.y) / imageScale).toInt()

        val sourceRadius = (magnifierRadius / magnification / imageScale).toInt()

        val srcLeft = (bitmapX - sourceRadius).coerceIn(0, bitmap.width - 1)
        val srcTop = (bitmapY - sourceRadius).coerceIn(0, bitmap.height - 1)
        val srcRight = (bitmapX + sourceRadius).coerceIn(1, bitmap.width)
        val srcBottom = (bitmapY + sourceRadius).coerceIn(1, bitmap.height)
        val srcWidth = (srcRight - srcLeft).coerceAtLeast(1)
        val srcHeight = (srcBottom - srcTop).coerceAtLeast(1)

        // Clip to circle
        val circlePath = Path().apply {
            addOval(
                androidx.compose.ui.geometry.Rect(
                    0f, 0f, magnifierRadius * 2, magnifierRadius * 2
                )
            )
        }

        clipPath(circlePath) {
            // Background
            drawCircle(
                color = Color.White,
                radius = magnifierRadius,
                center = Offset(centerX, centerY),
            )

            // Draw zoomed image
            drawImage(
                image = imageBitmap,
                srcOffset = IntOffset(srcLeft, srcTop),
                srcSize = IntSize(srcWidth, srcHeight),
                dstOffset = IntOffset(0, 0),
                dstSize = IntSize((magnifierRadius * 2).toInt(), (magnifierRadius * 2).toInt()),
            )
        }

        // Draw border
        drawCircle(
            color = CropGreen,
            radius = magnifierRadius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 3f),
        )

        // Draw crosshair
        val crossSize = 16f
        drawLine(
            color = CropGreen,
            start = Offset(centerX - crossSize, centerY),
            end = Offset(centerX + crossSize, centerY),
            strokeWidth = 1.5f,
        )
        drawLine(
            color = CropGreen,
            start = Offset(centerX, centerY - crossSize),
            end = Offset(centerX, centerY + crossSize),
            strokeWidth = 1.5f,
        )
    }
}

package com.swiftscan.pdf.ui.crop

import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.swiftscan.pdf.R
import com.swiftscan.pdf.processing.EnhanceMode
import com.swiftscan.pdf.ui.theme.CropGreen
import com.swiftscan.pdf.ui.theme.CropHandle
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CropScreen(
    imageUri: String,
    onNavigateToReview: () -> Unit,
    onRetake: () -> Unit,
    viewModel: CropViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(imageUri) {
        viewModel.loadImage(imageUri)
    }

    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var activeDragPoint by remember { mutableStateOf<Offset?>(null) }
    var draggedCornerIndex by remember { mutableIntStateOf(-1) }

    // Use rememberUpdatedState so the gesture handler always reads current values
    val currentCorners by rememberUpdatedState(uiState.corners)
    val currentBitmap by rememberUpdatedState(uiState.bitmap)

    // Compute scale and offset from canvas size and bitmap
    val bitmap = uiState.bitmap
    val scale = if (bitmap != null && canvasSize.width > 0 && canvasSize.height > 0) {
        minOf(
            canvasSize.width.toFloat() / bitmap.width,
            canvasSize.height.toFloat() / bitmap.height
        )
    } else 1f
    val offsetX = if (bitmap != null && canvasSize.width > 0) {
        (canvasSize.width - bitmap.width * scale) / 2f
    } else 0f
    val offsetY = if (bitmap != null && canvasSize.height > 0) {
        (canvasSize.height - bitmap.height * scale) / 2f
    } else 0f

    val currentScale by rememberUpdatedState(scale)
    val currentOffsetX by rememberUpdatedState(offsetX)
    val currentOffsetY by rememberUpdatedState(offsetY)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.crop_title)) },
                actions = {
                    IconButton(onClick = { viewModel.rotate90() }) {
                        @Suppress("DEPRECATION")
                        Icon(Icons.Default.RotateRight, contentDescription = stringResource(R.string.btn_rotate))
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onRetake) {
                        Icon(Icons.Default.Replay, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.btn_retake))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // Magnifier at top-left when dragging
            if (draggedCornerIndex >= 0 && activeDragPoint != null) {
                Box(modifier = Modifier.padding(16.dp)) {
                    MagnifierOverlay(
                        bitmap = uiState.bitmap,
                        touchPoint = activeDragPoint ?: Offset.Zero,
                        imageOffset = Offset(offsetX, offsetY),
                        imageScale = scale,
                    )
                }
            }

            // Image with crop overlay
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onSizeChanged { size -> canvasSize = size },
            ) {
                if (bitmap != null) {
                    val imageBitmap = bitmap.asImageBitmap()

                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val bmp = currentBitmap ?: return@detectDragGestures
                                        val idx = findNearestCorner(
                                            offset, currentCorners,
                                            currentScale, currentOffsetX, currentOffsetY,
                                        )
                                        if (idx >= 0) {
                                            draggedCornerIndex = idx
                                            viewModel.setActiveCorner(idx)
                                            activeDragPoint = offset
                                        }
                                    },
                                    onDrag = { change, _ ->
                                        val bmp = currentBitmap ?: return@detectDragGestures
                                        val idx = draggedCornerIndex
                                        if (idx >= 0) {
                                            val pos = change.position
                                            activeDragPoint = pos
                                            val bitmapX = ((pos.x - currentOffsetX) / currentScale)
                                                .coerceIn(0f, bmp.width.toFloat())
                                            val bitmapY = ((pos.y - currentOffsetY) / currentScale)
                                                .coerceIn(0f, bmp.height.toFloat())
                                            viewModel.updateCorner(idx, PointF(bitmapX, bitmapY))
                                        }
                                    },
                                    onDragEnd = {
                                        draggedCornerIndex = -1
                                        viewModel.clearActiveCorner()
                                        activeDragPoint = null
                                    },
                                    onDragCancel = {
                                        draggedCornerIndex = -1
                                        viewModel.clearActiveCorner()
                                        activeDragPoint = null
                                    },
                                )
                            },
                    ) {
                        // Draw image
                        drawImage(
                            image = imageBitmap,
                            srcOffset = IntOffset.Zero,
                            srcSize = IntSize(bitmap.width, bitmap.height),
                            dstOffset = IntOffset(offsetX.toInt(), offsetY.toInt()),
                            dstSize = IntSize(
                                (bitmap.width * scale).toInt(),
                                (bitmap.height * scale).toInt()
                            ),
                        )

                        // Draw crop overlay
                        if (uiState.corners.size == 4) {
                            val screenCorners = uiState.corners.map { pt ->
                                Offset(pt.x * scale + offsetX, pt.y * scale + offsetY)
                            }

                            // Draw filled region
                            val path = Path().apply {
                                moveTo(screenCorners[0].x, screenCorners[0].y)
                                for (i in 1..3) lineTo(screenCorners[i].x, screenCorners[i].y)
                                close()
                            }
                            drawPath(path, CropGreen.copy(alpha = 0.2f))
                            drawPath(path, CropGreen, style = Stroke(width = 3f))

                            // Draw corner handles — larger for easy touch
                            for ((i, corner) in screenCorners.withIndex()) {
                                val isActive = i == draggedCornerIndex
                                val handleRadius = if (isActive) 20f else 16f
                                drawCircle(
                                    if (isActive) CropGreen else CropHandle,
                                    radius = handleRadius,
                                    center = corner,
                                )
                                drawCircle(
                                    CropGreen,
                                    radius = handleRadius,
                                    center = corner,
                                    style = Stroke(width = 2.5f),
                                )
                            }
                        }
                    }
                } else if (uiState.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }

            // Enhancement mode chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EnhanceMode.entries.forEach { mode ->
                    FilterChip(
                        selected = uiState.enhanceMode == mode,
                        onClick = { viewModel.setEnhanceMode(mode) },
                        label = {
                            Text(
                                text = when (mode) {
                                    EnhanceMode.ORIGINAL -> "Original"
                                    EnhanceMode.GRAYSCALE -> "Gray"
                                    EnhanceMode.BLACK_AND_WHITE -> "B&W"
                                    EnhanceMode.HIGH_CONTRAST -> "Contrast"
                                },
                                style = MaterialTheme.typography.labelSmall,
                            )
                        },
                    )
                }
            }

            // Done button
            Button(
                onClick = {
                    scope.launch {
                        viewModel.cropAndSave()
                        onNavigateToReview()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !uiState.isProcessing && uiState.bitmap != null,
            ) {
                if (uiState.isProcessing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.btn_done))
                }
            }
        }
    }
}

private fun findNearestCorner(
    touch: Offset,
    corners: List<PointF>,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    threshold: Float = 100f,
): Int {
    var minDist = Float.MAX_VALUE
    var minIdx = -1
    for ((i, corner) in corners.withIndex()) {
        val screenX = corner.x * scale + offsetX
        val screenY = corner.y * scale + offsetY
        val dx = touch.x - screenX
        val dy = touch.y - screenY
        val dist = sqrt(dx * dx + dy * dy)
        if (dist < minDist && dist < threshold) {
            minDist = dist
            minIdx = i
        }
    }
    return minIdx
}

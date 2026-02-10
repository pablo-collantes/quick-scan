package com.swiftscan.pdf.ui.crop

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftscan.pdf.processing.EnhanceMode
import com.swiftscan.pdf.processing.ImageEnhancer
import com.swiftscan.pdf.processing.PerspectiveTransform
import com.swiftscan.pdf.storage.FileManager
import com.swiftscan.pdf.storage.ScanSessionManager
import com.swiftscan.pdf.storage.ScannedPage
import com.swiftscan.pdf.utils.decodeBitmapFromUri
import com.swiftscan.pdf.utils.rotate
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CropUiState(
    val bitmap: Bitmap? = null,
    val corners: List<PointF> = emptyList(),
    val activeCornerIndex: Int = -1,
    val isProcessing: Boolean = false,
    val rotation: Float = 0f,
    val enhanceMode: EnhanceMode = EnhanceMode.ORIGINAL,
    val error: String? = null,
)

@HiltViewModel
class CropViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val perspectiveTransform: PerspectiveTransform,
    private val imageEnhancer: ImageEnhancer,
    private val fileManager: FileManager,
    private val sessionManager: ScanSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CropUiState())
    val uiState: StateFlow<CropUiState> = _uiState.asStateFlow()

    fun loadImage(imageUri: String) {
        viewModelScope.launch {
            try {
                val uri = Uri.parse(imageUri)
                val bitmap = context.decodeBitmapFromUri(uri) ?: return@launch

                // Use detected corners from the scanner if available,
                // otherwise default to 10% margin
                val detectedCorners = sessionManager.consumeDetectedCorners()
                val corners = if (detectedCorners != null && detectedCorners.size == 4) {
                    // Map normalized (0-1) corners to bitmap pixel coordinates
                    detectedCorners.map { pt ->
                        PointF(
                            (pt.x * bitmap.width).coerceIn(0f, bitmap.width.toFloat()),
                            (pt.y * bitmap.height).coerceIn(0f, bitmap.height.toFloat()),
                        )
                    }
                } else {
                    getDefaultCorners(bitmap.width.toFloat(), bitmap.height.toFloat())
                }

                _uiState.value = _uiState.value.copy(
                    bitmap = bitmap,
                    corners = corners,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun updateCorner(index: Int, position: PointF) {
        val currentCorners = _uiState.value.corners.toMutableList()
        if (index in currentCorners.indices) {
            currentCorners[index] = position
            _uiState.value = _uiState.value.copy(corners = currentCorners)
        }
    }

    fun setActiveCorner(index: Int) {
        _uiState.value = _uiState.value.copy(activeCornerIndex = index)
    }

    fun clearActiveCorner() {
        _uiState.value = _uiState.value.copy(activeCornerIndex = -1)
    }

    fun rotate90() {
        val bitmap = _uiState.value.bitmap ?: return
        viewModelScope.launch {
            val newRotation = (_uiState.value.rotation + 90f) % 360f
            val rotated = bitmap.rotate(90f)
            val newCorners = getDefaultCorners(rotated.width.toFloat(), rotated.height.toFloat())
            _uiState.value = _uiState.value.copy(
                bitmap = rotated,
                corners = newCorners,
                rotation = newRotation,
            )
        }
    }

    fun setEnhanceMode(mode: EnhanceMode) {
        _uiState.value = _uiState.value.copy(enhanceMode = mode)
    }

    suspend fun cropAndSave() {
        val state = _uiState.value
        val bitmap = state.bitmap ?: return
        _uiState.value = state.copy(isProcessing = true)

        try {
            val warped = perspectiveTransform.warpDocument(bitmap, state.corners)
            val enhanced = imageEnhancer.enhance(warped, state.enhanceMode)
            val file = fileManager.saveTempBitmap(enhanced, "cropped")

            // Add page to session so ReviewScreen can see it
            sessionManager.addPage(ScannedPage(
                filePath = file.absolutePath,
                bitmap = enhanced,
            ))

            _uiState.value = _uiState.value.copy(isProcessing = false)
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                isProcessing = false,
                error = e.message,
            )
        }
    }

    private fun getDefaultCorners(width: Float, height: Float): List<PointF> {
        val margin = 0.1f
        return listOf(
            PointF(width * margin, height * margin),
            PointF(width * (1 - margin), height * margin),
            PointF(width * (1 - margin), height * (1 - margin)),
            PointF(width * margin, height * (1 - margin)),
        )
    }
}

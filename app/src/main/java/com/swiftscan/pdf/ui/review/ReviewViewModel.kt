package com.swiftscan.pdf.ui.review

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftscan.pdf.storage.FileManager
import com.swiftscan.pdf.storage.ScanSessionManager
import com.swiftscan.pdf.storage.ScannedPage
import com.swiftscan.pdf.storage.db.ScanDao
import com.swiftscan.pdf.storage.db.ScanDocument
import com.swiftscan.pdf.utils.PdfGenerator
import com.swiftscan.pdf.utils.generateTimestampName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ReviewUiState(
    val pages: List<ScannedPage> = emptyList(),
    val documentName: String = generateTimestampName(),
    val isSaving: Boolean = false,
    val pdfReady: Boolean = false,
    val saveSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionManager: ScanSessionManager,
    private val pdfGenerator: PdfGenerator,
    private val fileManager: FileManager,
    private val scanDao: ScanDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReviewUiState())
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var pendingPdfBytes: ByteArray? = null

    init {
        viewModelScope.launch {
            sessionManager.pages.collect { pages ->
                _uiState.value = _uiState.value.copy(pages = pages)
            }
        }
    }

    fun onDocumentNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(documentName = name)
    }

    fun removePage(index: Int) {
        sessionManager.removePage(index)
    }

    fun movePage(from: Int, to: Int) {
        sessionManager.movePage(from, to)
    }

    fun preparePdf() {
        val state = _uiState.value
        if (state.pages.isEmpty() || state.isSaving) return
        _uiState.value = state.copy(isSaving = true, error = null)

        viewModelScope.launch {
            try {
                val bitmaps = state.pages.map { it.bitmap }
                val pdfBytes = pdfGenerator.generatePdf(bitmaps)
                pendingPdfBytes = pdfBytes
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    pdfReady = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message,
                )
            }
        }
    }

    fun savePdfToUri(uri: Uri) {
        val pdfBytes = pendingPdfBytes ?: return
        val state = _uiState.value
        _uiState.value = state.copy(isSaving = true, pdfReady = false)

        viewModelScope.launch {
            try {
                // Write PDF to user-chosen location
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(pdfBytes)
                }

                // Also save internally for the library
                val bitmaps = state.pages.map { it.bitmap }
                val internalFile = fileManager.savePdf(state.documentName, pdfBytes)

                val thumbnailFile = bitmaps.firstOrNull()?.let { firstPage ->
                    val thumbWidth = 300
                    val thumbHeight = (firstPage.height * (thumbWidth.toFloat() / firstPage.width)).toInt()
                    val thumbnail = Bitmap.createScaledBitmap(firstPage, thumbWidth, thumbHeight, true)
                    fileManager.saveThumbnail(thumbnail, state.documentName)
                }

                val document = ScanDocument(
                    name = state.documentName,
                    filePath = internalFile.absolutePath,
                    thumbnailPath = thumbnailFile?.absolutePath,
                    pageCount = bitmaps.size,
                    fileSize = pdfBytes.size.toLong(),
                )
                scanDao.insert(document)

                sessionManager.clear()
                fileManager.clearTemp()
                pendingPdfBytes = null

                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    saveSuccess = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    error = e.message,
                )
            }
        }
    }

    fun clearPdfReady() {
        _uiState.value = _uiState.value.copy(pdfReady = false)
    }

    fun suggestedFileName(): String {
        return "${_uiState.value.documentName}.pdf"
    }
}

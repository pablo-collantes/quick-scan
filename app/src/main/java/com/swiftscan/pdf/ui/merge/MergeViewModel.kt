package com.swiftscan.pdf.ui.merge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftscan.pdf.storage.FileManager
import com.swiftscan.pdf.storage.db.ScanDao
import com.swiftscan.pdf.storage.db.ScanDocument
import com.swiftscan.pdf.utils.PdfGenerator
import com.swiftscan.pdf.utils.generateTimestampName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class MergeUiState(
    val selectedIds: Set<Long> = emptySet(),
    val isMerging: Boolean = false,
    val mergeSuccess: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MergeViewModel @Inject constructor(
    private val scanDao: ScanDao,
    private val pdfGenerator: PdfGenerator,
    private val fileManager: FileManager,
) : ViewModel() {

    val allScans: StateFlow<List<ScanDocument>> = scanDao.getAllScans()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow(MergeUiState())
    val uiState: StateFlow<MergeUiState> = _uiState.asStateFlow()

    fun toggleSelection(id: Long) {
        val current = _uiState.value.selectedIds.toMutableSet()
        if (current.contains(id)) current.remove(id) else current.add(id)
        _uiState.value = _uiState.value.copy(selectedIds = current)
    }

    fun mergePdfs() {
        val state = _uiState.value
        if (state.selectedIds.size < 2 || state.isMerging) return
        _uiState.value = state.copy(isMerging = true, error = null)

        viewModelScope.launch {
            try {
                val selectedScans = state.selectedIds.mapNotNull { id ->
                    scanDao.getById(id)
                }.filter { it.mimeType == "application/pdf" }

                if (selectedScans.size < 2) {
                    _uiState.value = _uiState.value.copy(
                        isMerging = false,
                        error = "Select at least 2 PDFs",
                    )
                    return@launch
                }

                val pdfFiles = selectedScans.map { File(it.filePath) }
                    .filter { it.exists() }

                val mergedName = "Merged_${generateTimestampName()}"
                val outputFile = File(fileManager.scansDirectory(), "$mergedName.pdf")
                pdfGenerator.mergePdfs(pdfFiles, outputFile)

                // Calculate total page count
                val totalPages = selectedScans.sumOf { it.pageCount }

                val document = ScanDocument(
                    name = mergedName,
                    filePath = outputFile.absolutePath,
                    thumbnailPath = selectedScans.firstOrNull()?.thumbnailPath,
                    pageCount = totalPages,
                    fileSize = outputFile.length(),
                )
                scanDao.insert(document)

                _uiState.value = _uiState.value.copy(
                    isMerging = false,
                    mergeSuccess = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isMerging = false,
                    error = e.message,
                )
            }
        }
    }
}

package com.swiftscan.pdf.ui.scanner

import android.app.Activity
import android.content.Context
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftscan.pdf.camera.DocumentDetector
import com.swiftscan.pdf.storage.ScanSessionManager
import com.swiftscan.pdf.storage.ScannedPage
import com.swiftscan.pdf.utils.decodeBitmapFromUri
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ScannerUiState(
    val isProcessing: Boolean = false,
    val scanComplete: Boolean = false,
    val cancelled: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentDetector: DocumentDetector,
    private val sessionManager: ScanSessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    fun launchScanner(
        activity: Activity,
        onLaunch: (IntentSenderRequest) -> Unit,
    ) {
        documentDetector.getStartScanIntent(
            activity = activity,
            onSuccess = onLaunch,
            onFailure = { e ->
                _uiState.value = _uiState.value.copy(
                    error = e.message,
                    cancelled = true,
                )
            },
        )
    }

    fun handleScanResult(result: ActivityResult) {
        val scanResult = documentDetector.handleScanResult(result)
        if (scanResult == null || scanResult.pageUris.isEmpty()) {
            _uiState.value = _uiState.value.copy(cancelled = true)
            return
        }

        _uiState.value = _uiState.value.copy(isProcessing = true)

        viewModelScope.launch {
            try {
                val pages = withContext(Dispatchers.IO) {
                    scanResult.pageUris.mapNotNull { uri ->
                        val bitmap = context.decodeBitmapFromUri(uri) ?: return@mapNotNull null
                        ScannedPage(
                            filePath = uri.toString(),
                            bitmap = bitmap,
                        )
                    }
                }

                for (page in pages) {
                    sessionManager.addPage(page)
                }

                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    scanComplete = true,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isProcessing = false,
                    error = e.message,
                    cancelled = true,
                )
            }
        }
    }

    fun clearScanComplete() {
        _uiState.value = _uiState.value.copy(scanComplete = false)
    }
}

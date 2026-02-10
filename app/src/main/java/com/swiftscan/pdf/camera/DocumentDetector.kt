package com.swiftscan.pdf.camera

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.mlkit.vision.documentscanner.GmsDocumentScanner
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentDetector @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scannerOptions = GmsDocumentScannerOptions.Builder()
        .setGalleryImportAllowed(true)
        .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
        .setScannerMode(SCANNER_MODE_FULL)
        .build()

    private val scanner: GmsDocumentScanner = GmsDocumentScanning.getClient(scannerOptions)

    fun getStartScanIntent(
        activity: Activity,
        onSuccess: (IntentSenderRequest) -> Unit,
        onFailure: (Exception) -> Unit,
    ) {
        scanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender: IntentSender ->
                val request = IntentSenderRequest.Builder(intentSender).build()
                onSuccess(request)
            }
            .addOnFailureListener { exception ->
                onFailure(exception)
            }
    }

    fun handleScanResult(result: ActivityResult): ScanResult? {
        if (result.resultCode != Activity.RESULT_OK) return null
        val scanningResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            ?: return null

        val pages = scanningResult.pages?.map { page ->
            page.imageUri
        } ?: emptyList()

        val pdfUri = scanningResult.pdf?.uri

        return ScanResult(
            pageUris = pages,
            pdfUri = pdfUri,
        )
    }
}

data class ScanResult(
    val pageUris: List<Uri>,
    val pdfUri: Uri?,
)

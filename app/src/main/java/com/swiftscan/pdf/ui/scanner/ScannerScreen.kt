package com.swiftscan.pdf.ui.scanner

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ScannerScreen(
    onNavigateToReview: () -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val activity = context as? Activity

    val scanLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.handleScanResult(result)
    }

    // Launch ML Kit scanner immediately on first composition
    LaunchedEffect(Unit) {
        if (activity != null) {
            viewModel.launchScanner(activity) { request ->
                scanLauncher.launch(request)
            }
        }
    }

    // Navigate to review when pages are ready
    LaunchedEffect(uiState.scanComplete) {
        if (uiState.scanComplete) {
            viewModel.clearScanComplete()
            onNavigateToReview()
        }
    }

    // Navigate back if user cancelled
    LaunchedEffect(uiState.cancelled) {
        if (uiState.cancelled) {
            onNavigateBack()
        }
    }

    // Simple loading screen while ML Kit is active
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (uiState.isProcessing) {
            CircularProgressIndicator(color = Color.White)
        }
    }
}

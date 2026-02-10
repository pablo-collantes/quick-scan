package com.swiftscan.pdf.storage

import android.graphics.Bitmap
import android.graphics.PointF
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class ScannedPage(
    val filePath: String,
    val bitmap: Bitmap,
)

@Singleton
class ScanSessionManager @Inject constructor() {

    private val _pages = MutableStateFlow<List<ScannedPage>>(emptyList())
    val pages: StateFlow<List<ScannedPage>> = _pages.asStateFlow()

    private val _detectedCorners = MutableStateFlow<List<PointF>?>(null)
    val detectedCorners: StateFlow<List<PointF>?> = _detectedCorners.asStateFlow()

    fun addPage(page: ScannedPage) {
        _pages.value = _pages.value + page
    }

    fun removePage(index: Int) {
        val current = _pages.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _pages.value = current
        }
    }

    fun movePage(from: Int, to: Int) {
        val current = _pages.value.toMutableList()
        if (from in current.indices && to in current.indices) {
            val item = current.removeAt(from)
            current.add(to, item)
            _pages.value = current
        }
    }

    fun setDetectedCorners(corners: List<PointF>?) {
        _detectedCorners.value = corners
    }

    fun consumeDetectedCorners(): List<PointF>? {
        val corners = _detectedCorners.value
        _detectedCorners.value = null
        return corners
    }

    fun clear() {
        _pages.value = emptyList()
        _detectedCorners.value = null
    }
}

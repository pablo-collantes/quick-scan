package com.swiftscan.pdf.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swiftscan.pdf.storage.FileManager
import com.swiftscan.pdf.storage.db.ScanDao
import com.swiftscan.pdf.storage.db.ScanDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val scanDao: ScanDao,
    private val fileManager: FileManager,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    @OptIn(ExperimentalCoroutinesApi::class)
    val scans: StateFlow<List<ScanDocument>> = _searchQuery
        .flatMapLatest { query ->
            if (query.isBlank()) scanDao.getAllScans()
            else scanDao.searchScans(query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun deleteScan(document: ScanDocument) {
        viewModelScope.launch {
            fileManager.deleteFile(document.filePath)
            document.thumbnailPath?.let { fileManager.deleteFile(it) }
            scanDao.delete(document)
        }
    }

    fun renameScan(document: ScanDocument, newName: String) {
        viewModelScope.launch {
            scanDao.update(document.copy(name = newName))
        }
    }
}

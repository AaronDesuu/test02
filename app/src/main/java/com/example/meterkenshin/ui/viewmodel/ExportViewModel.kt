package com.example.meterkenshin.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.meterkenshin.ui.manager.ExportManager
import com.example.meterkenshin.ui.manager.FileGroup
import com.example.meterkenshin.ui.manager.NotificationManager
import com.example.meterkenshin.ui.manager.AppPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.FileReader

/** Filter by file type */
enum class FileTypeFilter { ALL, LP, EL, BD }

/** Filter by retrieval mode */
enum class RetrievalModeFilter { ALL, ALL_DATA, BY_PERIOD }

/** Sort field — kept for potential future use */
enum class FileSortField { DATE, NAME, SIZE }

class ExportViewModel(application: Application) : AndroidViewModel(application) {
    private val exportManager = ExportManager(application)
    private val context = application

    private val _files = MutableStateFlow<List<File>>(emptyList())
    val files: StateFlow<List<File>> = _files.asStateFlow()

    private val _selectedFiles = MutableStateFlow<Set<String>>(emptySet())
    val selectedFiles: StateFlow<Set<String>> = _selectedFiles.asStateFlow()

    private val _isExporting = MutableStateFlow(false)
    val isExporting: StateFlow<Boolean> = _isExporting.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filter & sort state
    private val _typeFilter = MutableStateFlow(FileTypeFilter.ALL)
    val typeFilter: StateFlow<FileTypeFilter> = _typeFilter.asStateFlow()

    private val _modeFilter = MutableStateFlow(RetrievalModeFilter.ALL)
    val modeFilter: StateFlow<RetrievalModeFilter> = _modeFilter.asStateFlow()

    private val _sortAscending = MutableStateFlow(false) // default newest first
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    /** Flat filtered + sorted file list (replaces group-based display) */
    private val _displayFiles = MutableStateFlow<List<File>>(emptyList())
    val displayFiles: StateFlow<List<File>> = _displayFiles.asStateFlow()

    /** Maps filename → (firstTimestamp, lastTimestamp) extracted from CSV content */
    private val _fileDataPeriods = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val fileDataPeriods: StateFlow<Map<String, Pair<String, String>>> = _fileDataPeriods.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applyFilters()
    }

    fun setTypeFilter(filter: FileTypeFilter) {
        _typeFilter.value = filter
        applyFilters()
    }

    fun setModeFilter(filter: RetrievalModeFilter) {
        _modeFilter.value = filter
        applyFilters()
    }

    fun toggleSortOrder() {
        _sortAscending.value = !_sortAscending.value
        applyFilters()
    }

    private fun applyFilters() {
        var result = _files.value.toList()

        // Search filter
        val query = _searchQuery.value.lowercase()
        if (query.isNotEmpty()) {
            result = result.filter { it.name.lowercase().contains(query) }
        }

        // Type filter
        when (_typeFilter.value) {
            FileTypeFilter.LP -> result = result.filter { it.name.contains("_LP_") }
            FileTypeFilter.EL -> result = result.filter { it.name.contains("_EL_") }
            FileTypeFilter.BD -> result = result.filter { it.name.contains("_BD_") }
            FileTypeFilter.ALL -> { /* no filter */ }
        }

        // Mode filter
        when (_modeFilter.value) {
            RetrievalModeFilter.ALL_DATA -> result = result.filter { !it.name.contains("_from") }
            RetrievalModeFilter.BY_PERIOD -> result = result.filter { it.name.contains("_from") }
            RetrievalModeFilter.ALL -> { /* no filter */ }
        }

        // Sort by date
        result = if (_sortAscending.value) result.sortedBy { it.lastModified() }
                 else result.sortedByDescending { it.lastModified() }

        _displayFiles.value = result
    }

    fun loadFiles() {
        viewModelScope.launch {
            _files.value = exportManager.getAvailableFiles()
            applyFilters()
            extractFileDataPeriods(_files.value)
        }
    }

    /**
     * Extract first and last Clock timestamps from each CSV file.
     * Clock is always the first column; row 0 is the header.
     */
    private suspend fun extractFileDataPeriods(files: List<File>) = withContext(Dispatchers.IO) {
        val periods = mutableMapOf<String, Pair<String, String>>()
        for (file in files) {
            try {
                if (!file.name.endsWith(".csv")) continue
                BufferedReader(FileReader(file)).use { reader ->
                    reader.readLine() // skip header
                    val firstLine = reader.readLine() ?: return@use
                    val firstClock = firstLine.split(",").firstOrNull()?.trim() ?: return@use
                    var lastLine = firstLine
                    var line = reader.readLine()
                    while (line != null) {
                        if (line.isNotBlank()) lastLine = line
                        line = reader.readLine()
                    }
                    val lastClock = lastLine.split(",").firstOrNull()?.trim() ?: return@use
                    if (firstClock.isNotEmpty() && lastClock.isNotEmpty()) {
                        periods[file.name] = extractDateOnly(firstClock) to extractDateOnly(lastClock)
                    }
                }
            } catch (e: Exception) {
                Log.w("ExportViewModel", "Failed to extract period from ${file.name}: ${e.message}")
            }
        }
        _fileDataPeriods.value = periods
    }

    /** Extract date-only portion from clock strings like "2026/03/18 13:42:33" → "2026-03-18" */
    private fun extractDateOnly(clock: String): String {
        val datePart = clock.split(" ").firstOrNull() ?: clock
        return datePart.replace("/", "-")
    }

    fun toggleFileSelection(fileName: String, isSelected: Boolean) {
        val current = _selectedFiles.value.toMutableSet()
        if (isSelected) current.add(fileName) else current.remove(fileName)
        _selectedFiles.value = current
    }

    fun selectAll() {
        _selectedFiles.value = _displayFiles.value.map { it.name }.toSet()
    }

    fun selectNone() {
        _selectedFiles.value = emptySet()
    }

    fun exportSelectedFiles() {
        viewModelScope.launch {
            if (_selectedFiles.value.isEmpty()) {
                NotificationManager.showWarning("No files selected")
                return@launch
            }

            _isExporting.value = true
            try {
                val selectedFileList = _files.value.filter {
                    _selectedFiles.value.contains(it.name)
                }

                val result = exportManager.exportFiles(selectedFileList)

                if (result.success) {
                    NotificationManager.showSuccess(
                        "Successfully exported ${result.exportedCount} file(s) to Download/kenshinApp"
                    )
                    _selectedFiles.value = emptySet()

                    if (result.exportedFiles.isNotEmpty() &&
                        AppPreferences.isAutoShareExportEnabled(context)) {
                        exportManager.shareFiles(result.exportedFiles)
                    }
                } else {
                    NotificationManager.showError(
                        "Export failed: ${result.errorMessage}"
                    )
                }
            } catch (e: Exception) {
                NotificationManager.showError("Export failed: ${e.message}")
            } finally {
                _isExporting.value = false
            }
        }
    }

    fun deleteFile(fileName: String) {
        viewModelScope.launch {
            val result = exportManager.deleteFile(fileName)
            if (result.success) {
                NotificationManager.showSuccess("File deleted")
                loadFiles()
            } else {
                NotificationManager.showError("Delete failed: ${result.errorMessage}")
            }
        }
    }

    fun deleteSelectedFiles() {
        viewModelScope.launch {
            if (_selectedFiles.value.isEmpty()) {
                NotificationManager.showWarning("No files selected")
                return@launch
            }

            val result = exportManager.deleteFiles(_selectedFiles.value)
            if (result.success) {
                NotificationManager.showSuccess("Deleted ${result.exportedCount} file(s)")
                _selectedFiles.value = emptySet()
                loadFiles()
            } else {
                NotificationManager.showError("Delete failed: ${result.errorMessage}")
            }
        }
    }
}
